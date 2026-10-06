// SPDX-License-Identifier: GPL-3.0-only
// Derived from Im-Fran/MetalCraft, commit a2cc82780d01a51d00a297f75cf07c221d7c8700.
package dev.eviemod.metal.device;

import dev.eviemod.metal.mtl.Mtl;
import java.util.ArrayDeque;

/**
 * Staging memory for CPU -> GPU copies. Large shared MTLBuffers are sub-allocated linearly and recycled once the GPU
 * has finished the commands that read them, instead of allocating an MTLBuffer per upload. Render thread only.
 */
public final class UploadRing {
    private static final long CHUNK_SIZE = 4L << 20;
    private static final long ALIGNMENT = 16;

    /** Write {@code length} bytes at {@code address}, then encode a blit reading {@code buffer} at {@code offset}. */
    public record Slice(long buffer, long offset, long address) {}

    private static final class Chunk {
        final long handle, contents, capacity;
        long offset, lastUse;

        Chunk(long capacity) {
            this.capacity = capacity;
            this.handle = Mtl.newBuffer(capacity);
            if (handle == 0) throw new com.mojang.blaze3d.GpuOutOfMemoryException("Metal staging allocation failed");
            this.contents = Mtl.bufferContents(handle);
        }
    }

    private static final ArrayDeque<Chunk> RETIRED = new ArrayDeque<>();
    private static Chunk current;

    private UploadRing() {}

    public static void close() {
        if (current != null) Mtl.release(current.handle);
        current = null;
        RETIRED.forEach(c -> Mtl.release(c.handle));
        RETIRED.clear();
    }

    public static Slice reserve(long length) {
        Chunk chunk = chunkWithRoom(length);
        long offset = chunk.offset;
        chunk.offset = (offset + length + ALIGNMENT - 1) & -ALIGNMENT;
        chunk.lastUse = Mtl.fence();  // Reusable once the command buffer recording this upload completes.
        return new Slice(chunk.handle, offset, chunk.contents + offset);
    }

    // Bound queued staging allocations. A single larger upload is allowed, then reclaimed once complete.
    private static final long BUDGET = 64L << 20;

    static long retainedBytes() {
        long bytes = current == null ? 0 : current.capacity;
        for (Chunk chunk : RETIRED) bytes += chunk.capacity;
        return bytes;
    }

    /** Called after presentation as well as allocation, so a loading spike can shrink during quiet frames. */
    static void trimCompleted() {
        long completed = Mtl.completedFence();
        var it = RETIRED.iterator();
        while (it.hasNext()) {
            Chunk chunk = it.next();
            if (chunk.lastUse <= completed) { Mtl.release(chunk.handle); it.remove(); }
        }
        if (current != null && current.lastUse <= completed) {
            if (current.capacity > CHUNK_SIZE) { Mtl.release(current.handle); current = null; }
            else current.offset = 0;
        }
    }

    private static Chunk chunkWithRoom(long length) {
        if (length <= 0 || length > Long.MAX_VALUE - ALIGNMENT) throw new IllegalArgumentException("Invalid upload size");
        if (current != null && length <= current.capacity - current.offset) return current;
        // Reclaim every completed chunk, not just the oldest: a small oldest chunk used to prevent all reuse.
        trimCompleted();
        if (current != null && length <= current.capacity - current.offset) return current;
        long capacity = Math.max(CHUNK_SIZE, length);
        if (retainedBytes() > Math.max(0, BUDGET - capacity)) {
            // reserve() is used outside render passes. Submit and wait before creating more staging storage.
            if (!Mtl.fenceWait(Mtl.fence(), 5000)) {
                throw new IllegalStateException("Metal upload queue stalled; refusing unbounded staging allocation");
            }
            trimCompleted();
            if (current != null && length <= current.capacity - current.offset) return current;
        }
        // Allocate before changing ownership, so allocation failure leaves the old chunk tracked exactly once.
        Chunk next = new Chunk(capacity);
        if (current != null) RETIRED.addLast(current);
        current = next;
        return next;
    }
}

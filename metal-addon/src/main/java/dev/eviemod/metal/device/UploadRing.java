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

    private static Chunk chunkWithRoom(long length) {
        if (current != null && current.offset + length <= current.capacity) return current;
        if (current != null) RETIRED.addLast(current);
        Chunk oldest = RETIRED.peekFirst();
        if (oldest != null && oldest.capacity >= length && oldest.lastUse <= Mtl.completedFence()) {
            RETIRED.removeFirst();
            oldest.offset = 0;
            current = oldest;
        } else {
            // ponytail: chunks are never freed; steady state is ~3 frames of uploads. Trim RETIRED if memory matters.
            current = new Chunk(Math.max(CHUNK_SIZE, length));
        }
        return current;
    }
}

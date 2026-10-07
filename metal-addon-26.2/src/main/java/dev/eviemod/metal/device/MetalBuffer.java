// SPDX-License-Identifier: GPL-3.0-only
// Derived from Im-Fran/MetalCraft, commit a2cc82780d01a51d00a297f75cf07c221d7c8700.
package dev.eviemod.metal.device;

import dev.eviemod.metal.mtl.Mtl;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.GpuFormat;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/** A shared-storage MTLBuffer: CPU and GPU see the same memory, so mapping is a pointer, not a copy. */
public class MetalBuffer extends GpuBuffer {
    /** Buffers up to this size are rewritten by swapping in fresh storage (GL-style orphaning) instead of a GPU blit. */
    private static final long ORPHAN_LIMIT = 256 * 1024;

    long handle;
    private long storageGeneration;
    private long contents;
    private record TexelKey(GpuFormat format, long offset, long length) {}
    private final java.util.Map<TexelKey, Long> texelViews = new java.util.HashMap<>();
    private long lastGpuWrite;
    private boolean closed;

    MetalBuffer(int usage, long size) {
        super(usage, size);
        allocate();
    }

    private void allocate() {
        // MSL rounds uniform structs up to their 16-byte alignment, while std140 sizes from Blaze3D are exact.
        long paddedSize = size() > 0 && size() <= Integer.MAX_VALUE ? (size() + 15) & ~15L : 0;
        if (paddedSize == 0 || paddedSize > Mtl.maxBufferLength())
            throw new com.mojang.blaze3d.GpuOutOfMemoryException("Buffer exceeds Metal allocation limit: " + size());
        long next = Mtl.newBuffer(paddedSize);
        if (next == 0) throw new com.mojang.blaze3d.GpuOutOfMemoryException("Could not allocate buffer of " + size());
        handle = next;
        contents = Mtl.bufferContents(handle);
        storageGeneration++;
    }

    /** Records that queued GPU work writes into this buffer, so the CPU-side contents are stale until it completes. */
    void markGpuWrite() {
        lastGpuWrite = Mtl.fence();
    }

    /**
     * Writes {@code length} bytes at {@code offset} without a blit, when possible: the buffer gets new storage (commands
     * already recorded keep reading the old one) holding the old contents plus the new bytes. Returns false if the
     * buffer is too big to copy cheaply or the GPU may still be writing it, in which case the caller must blit.
     */
    boolean tryOrphanWrite(long offset, long source, long length) {
        if (size() > ORPHAN_LIMIT || lastGpuWrite > Mtl.completedFence()) return false;
        long oldHandle = handle, oldContents = contents;
        allocate();
        if (offset > 0 || length < size()) MemoryUtil.memCopy(oldContents, contents, size());
        MemoryUtil.memCopy(source, contents + offset, length);
        Mtl.release(oldHandle);
        texelViews.values().forEach(Mtl::release);
        texelViews.clear();
        return true;
    }

    ByteBuffer view(long offset, long length) {
        return MemoryUtil.memByteBuffer(contents + offset, Math.toIntExact(length));
    }

    long address() {
        return contents;
    }

    long storageGeneration() { return storageGeneration; }
    boolean hasPendingGpuWrite() { return lastGpuWrite > Mtl.completedFence(); }

    /** Texture view used when this buffer is bound as a samplerBuffer (texel buffer). */
    long texelView(GpuFormat format, long offset, long length) {
        if (closed) throw new IllegalStateException("Closed texel buffer");
        if (offset < 0 || length <= 0 || offset > size() - length || length % format.blockSize() != 0)
            throw new IllegalArgumentException("Invalid texel buffer slice");
        return texelViews.computeIfAbsent(new TexelKey(format, offset, length), key -> {
            long view = Mtl.newTextureBufferSlice(handle, MetalFormats.texture(format), offset, length, format.blockSize());
            if (view == 0) throw new IllegalStateException("Metal rejected texel buffer slice " + key);
            return view;
        });
    }

    @Override public com.mojang.blaze3d.buffers.GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
        if (closed) throw new IllegalStateException("Buffer already closed");
        if (!read && !write) throw new IllegalArgumentException("At least read or write must be true");
        if (offset < 0 || length < 0 || offset > size() - length) throw new IllegalArgumentException("Mapping outside buffer");
        if (lastGpuWrite > Mtl.completedFence() && Mtl.isRenderPassOpen())
            throw new UnsupportedOperationException("Mapping pending GPU writes requires a closed render pass");
        if (read && lastGpuWrite > Mtl.completedFence() && !Mtl.fenceWait(lastGpuWrite, 5000)) throw new IllegalStateException("Buffer read mapping fence timed out");
        var slice = slice(offset, length);
        if (!write) return new com.mojang.blaze3d.buffers.GpuBufferSlice.MappedView(slice, view(offset, length), () -> {});
        // Non-persistent writes use ordered staging: existing commands retain the previous storage.
        ByteBuffer staging = MemoryUtil.memAlloc(Math.toIntExact(length));
        long stagingAddress = MemoryUtil.memAddress(staging);
        // Write-only mappings may update only a subrange. Preserve the untouched bytes, including queued resize copies.
        if (lastGpuWrite > Mtl.completedFence() && !Mtl.fenceWait(lastGpuWrite, 5000)) {
            MemoryUtil.memFree(staging);
            throw new IllegalStateException("Buffer mapping fence timed out");
        }
        MemoryUtil.memCopy(contents + offset, stagingAddress, length);
        var mappedClosed = new java.util.concurrent.atomic.AtomicBoolean();
        return new com.mojang.blaze3d.buffers.GpuBufferSlice.MappedView(slice, staging, () -> {
            if (!mappedClosed.compareAndSet(false, true)) return;
            try {
                if (closed) throw new IllegalStateException("Buffer closed while mapped");
                if (length == 0) return;
                // Builders advance ByteBuffer.position(); mapping ownership still covers the original full range.
                if (!tryOrphanWrite(offset, stagingAddress, length)) {
                    if (Mtl.isRenderPassOpen()) throw new UnsupportedOperationException("Staged mapped writes require a closed render pass");
                    var upload = UploadRing.reserve(length);
                    MemoryUtil.memCopy(stagingAddress, upload.address(), length);
                    Mtl.copyBuffer(upload.buffer(), upload.offset(), handle, offset, length);
                    markGpuWrite();
                }
            } finally { MemoryUtil.memFree(staging); }
        });
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        // Command buffers retain what they reference, so releasing while the GPU still uses it is safe.
        texelViews.values().forEach(Mtl::release);
        texelViews.clear();
        Mtl.release(handle);
    }
}

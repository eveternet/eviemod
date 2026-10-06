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
        long next = Mtl.newBuffer((size() + 15) & ~15L);
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
        return MemoryUtil.memByteBuffer(contents + offset, (int) length);
    }

    long address() {
        return contents;
    }

    long storageGeneration() { return storageGeneration; }

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
        var slice = slice(offset, length);
        if (!write) return new com.mojang.blaze3d.buffers.GpuBufferSlice.MappedView(slice, view(offset, length), () -> {});
        // Non-persistent writes use ordered staging: existing commands retain the previous storage.
        ByteBuffer staging = MemoryUtil.memAlloc(Math.toIntExact(length));
        if (read) {
            if (!Mtl.fenceWait(Mtl.fence(), 5000)) throw new IllegalStateException("Buffer mapping fence timed out");
            MemoryUtil.memCopy(contents + offset, MemoryUtil.memAddress(staging), length);
        }
        return new com.mojang.blaze3d.buffers.GpuBufferSlice.MappedView(slice, staging, () -> {
            try {
                if (!tryOrphanWrite(offset, MemoryUtil.memAddress(staging), length)) {
                    var upload = UploadRing.reserve(length);
                    MemoryUtil.memCopy(MemoryUtil.memAddress(staging), upload.address(), length);
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

// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.device;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;

/** Validate Java API ranges before handing unchecked addresses and unsigned sizes to Metal. */
final class MetalRanges {
    private MetalRanges() {}

    static void buffer(GpuBuffer buffer, long offset, long length, int usage) {
        if (buffer.isClosed()) throw new IllegalStateException("Buffer already closed");
        if ((buffer.usage() & usage) != usage) throw new IllegalArgumentException("Buffer lacks required usage " + usage);
        if (offset < 0 || length < 0 || offset > buffer.size() || length > buffer.size() - offset)
            throw new IllegalArgumentException("Range outside buffer: offset=" + offset + ", length=" + length + ", size=" + buffer.size());
    }

    static void slice(GpuBufferSlice slice, int usage) {
        buffer(slice.buffer(), slice.offset(), slice.length(), usage);
    }

    static void draw(int count, int instances, int first, int firstInstance) {
        if (count < 0 || instances < 0 || first < 0 || firstInstance < 0)
            throw new IllegalArgumentException("Negative draw argument");
    }

    static void rectangle(int x, int y, int width, int height, int textureWidth, int textureHeight) {
        if (x < 0 || y < 0 || width < 0 || height < 0 || x > textureWidth || y > textureHeight
                || width > textureWidth - x || height > textureHeight - y)
            throw new IllegalArgumentException("Rectangle outside texture");
    }
}

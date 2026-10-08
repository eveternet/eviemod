// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.device;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetalRangesTest {
    private static final class Buffer extends GpuBuffer {
        private boolean closed;
        Buffer(long size, int usage) { super(usage, size); }
        @Override public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) { throw new UnsupportedOperationException(); }
        @Override public boolean isClosed() { return closed; }
        @Override public void close() { closed = true; }
    }

    @Test void directSliceRecordsCannotBypassBufferBoundsOrOverflow() {
        try (var buffer = new Buffer(64, GpuBuffer.USAGE_COPY_SRC)) {
            for (var slice : new GpuBufferSlice[]{
                    new GpuBufferSlice(buffer, -1, 1), new GpuBufferSlice(buffer, 0, -1),
                    new GpuBufferSlice(buffer, 63, 2), new GpuBufferSlice(buffer, 65, 0),
                    new GpuBufferSlice(buffer, Long.MAX_VALUE, Long.MAX_VALUE)}) {
                assertThrows(IllegalArgumentException.class, () -> MetalRanges.slice(slice, GpuBuffer.USAGE_COPY_SRC));
            }
            assertDoesNotThrow(() -> MetalRanges.slice(new GpuBufferSlice(buffer, 64, 0), GpuBuffer.USAGE_COPY_SRC));
            assertThrows(IllegalArgumentException.class, () -> MetalRanges.slice(buffer.slice(), GpuBuffer.USAGE_COPY_DST));
            buffer.close();
            assertThrows(IllegalStateException.class, () -> MetalRanges.slice(new GpuBufferSlice(buffer, 64, 0), 0));
        }
    }

    @Test void negativeDrawsAndOverflowingRectanglesAreRejectedBeforeNativeCalls() {
        assertThrows(IllegalArgumentException.class, () -> MetalRanges.draw(-1, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> MetalRanges.draw(1, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> MetalRanges.draw(1, 1, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> MetalRanges.draw(1, 1, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> MetalRanges.rectangle(Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 1, 4, 4));
        assertThrows(IllegalArgumentException.class, () -> MetalRanges.rectangle(0, -1, 1, 1, 4, 4));
        assertDoesNotThrow(() -> MetalRanges.rectangle(4, 4, 0, 0, 4, 4));
    }
}

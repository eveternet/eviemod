// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.device;
import com.mojang.blaze3d.systems.GpuQueryPool;
import dev.eviemod.metal.mtl.Mtl;
import java.util.OptionalLong;
final class MetalQueryPool implements GpuQueryPool {
    private final long handle;
    private final long[] fences;
    private boolean closed;
    MetalQueryPool(int size) {
        if (size < 1) throw new IllegalArgumentException("Timestamp pool size must be positive");
        fences = new long[size]; handle = Mtl.newTimestampPool(size);
        if (handle == 0) throw new IllegalStateException("Metal timestamp counter allocation failed");
    }
    private void check(int index) { if (closed) throw new IllegalStateException("Closed timestamp pool"); java.util.Objects.checkIndex(index, size()); }
    void write(int index) { check(index); Mtl.writeTimestamp(handle, index); fences[index] = Mtl.fence(); }
    @Override public int size() { return fences.length; }
    @Override public OptionalLong getValue(int index) {
        check(index);
        if (fences[index] == 0 || Mtl.completedFence() < fences[index]) return OptionalLong.empty();
        long value = Mtl.timestampValue(handle, index);
        return value < 0 ? OptionalLong.empty() : OptionalLong.of(value);
    }
    @Override public OptionalLong[] getValues(int first, int count) {
        java.util.Objects.checkFromIndexSize(first, count, size());
        var result = new OptionalLong[count]; for (int i = 0; i < count; i++) result[i] = getValue(first + i); return result;
    }
    @Override public void close() { if (closed) return; closed = true; Mtl.release(handle); }
}

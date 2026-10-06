// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.device;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.TransientMemory;
import com.mojang.blaze3d.util.TransientBlockAllocator;
import dev.eviemod.metal.mtl.Mtl;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.system.MemoryUtil;

/** Uses the game's allocator; rotations recycle storage only after submitted GPU work completes. */
final class MetalTransientMemory implements TransientMemory, AutoCloseable {
    private static final int ALL_USAGE = GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_UNIFORM
            | GpuBuffer.USAGE_COPY_SRC | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_MAP_READ;
    private final TransientBlockAllocator<MetalBuffer> gpu = new TransientBlockAllocator<>(524288, 65536,
            TransientBlockAllocator.Allocator.create(size -> new MetalBuffer(ALL_USAGE, size), MetalBuffer::close));
    private final TransientBlockAllocator<Long> cpu = new TransientBlockAllocator<>(524288, 16,
            TransientBlockAllocator.Allocator.create(MemoryUtil::nmemAlloc, MemoryUtil::nmemFree));
    private record Rotation(long fence, Runnable gpu, Runnable cpu) { void release() { gpu.run(); cpu.run(); } }
    private final ArrayDeque<Rotation> pending = new ArrayDeque<>();
    void rotate() {
        pending.addLast(new Rotation(Mtl.fence(), gpu.rotate(), cpu.rotate()));
        while (!pending.isEmpty() && pending.getFirst().fence <= Mtl.completedFence()) pending.removeFirst().release();
        // Bound submissions awaiting recycling, even for a minimized window without presentation.
        if (pending.size() > 3) {
            if (!Mtl.fenceWait(pending.getFirst().fence, 5000)) throw new IllegalStateException("Transient memory completion timed out");
            while (!pending.isEmpty() && pending.getFirst().fence <= Mtl.completedFence()) pending.removeFirst().release();
        }
    }
    @Override public ByteBuffer allocateCpu(long size, long alignment, long minimum, long elementSize) {
        var allocation = cpu.allocate(size, alignment, minimum, elementSize);
        return MemoryUtil.memByteBuffer(allocation.block() + allocation.offset(), Math.toIntExact(allocation.size()));
    }
    @Override public GpuBufferSlice allocateGpu(long size, long alignment, int usage, long minimum, long elementSize) {
        var allocation = gpu.allocate(size, alignment, minimum, elementSize);
        return allocation.block().slice(allocation.offset(), allocation.size());
    }
    @Override public GpuBufferSlice.MappedView allocateGpuMapped(long size, long alignment, int usage, long minimum, long elementSize) {
        var slice = allocateGpu(size, alignment, usage, minimum, elementSize);
        return new GpuBufferSlice.MappedView(slice, ((MetalBuffer) slice.buffer()).view(slice.offset(), slice.length()), () -> {});
    }
    @Override public GpuBufferSlice.MappedView allocateStaging(long size, long alignment, int usage, long minimum, long elementSize) {
        return allocateGpuMapped(size, alignment, usage, minimum, elementSize);
    }
    @Override public GpuBufferSlice uploadGpu(List<ByteBuffer> data, long alignment, int usage, long minimum, long elementSize) {
        long size = 0;
        for (ByteBuffer bytes : data) size = align(size + bytes.remaining(), alignment);
        var slice = allocateGpu(size, alignment, usage, minimum, elementSize);
        long offset = 0;
        for (ByteBuffer bytes : data) {
            if (offset + bytes.remaining() > slice.length()) throw new IllegalArgumentException("Partial transient allocation cannot fit upload");
            MemoryUtil.memCopy(MemoryUtil.memAddress(bytes), ((MetalBuffer) slice.buffer()).address() + slice.offset() + offset, bytes.remaining());
            offset = align(offset + bytes.remaining(), alignment);
        }
        return slice;
    }
    private static long align(long value, long alignment) { return Math.multiplyExact((Math.addExact(value, alignment - 1) / alignment), alignment); }
    @Override public GpuBufferSlice uploadStaging(List<ByteBuffer> data, long alignment, int usage, long minimum, long elementSize) { return uploadGpu(data, alignment, usage, minimum, elementSize); }
    @Override public List<GpuBufferSlice> multiUploadGpu(List<ByteBuffer> data, long alignment, int usage) {
        List<GpuBufferSlice> result = new ArrayList<>(data.size());
        for (ByteBuffer bytes : data) result.add(uploadGpu(List.of(bytes), alignment, usage));
        return result;
    }
    @Override public List<GpuBufferSlice> multiUploadStaging(List<ByteBuffer> data, long alignment, int usage) { return multiUploadGpu(data, alignment, usage); }
    @Override public void close() {
        if (!Mtl.fenceWait(Mtl.fence(), 5000)) throw new IllegalStateException("Transient memory shutdown timed out");
        while (!pending.isEmpty()) pending.removeFirst().release();
        gpu.close(); cpu.close();
    }
}

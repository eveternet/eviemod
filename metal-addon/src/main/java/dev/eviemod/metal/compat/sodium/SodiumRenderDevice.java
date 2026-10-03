// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.compat.sodium;

import java.nio.ByteBuffer;
import dev.eviemod.metal.mtl.Mtl;
import net.caffeinemc.mods.sodium.client.gl.array.GlVertexArray;
import net.caffeinemc.mods.sodium.client.gl.buffer.*;
import net.caffeinemc.mods.sodium.client.gl.device.*;
import net.caffeinemc.mods.sodium.client.gl.functions.DeviceFunctions;
import net.caffeinemc.mods.sodium.client.gl.sync.GlFence;
import net.caffeinemc.mods.sodium.client.gl.tessellation.*;
import net.caffeinemc.mods.sodium.client.gl.util.EnumBitField;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

/** Sodium arenas, staging, batching and sorting retained behind its supported command-list boundary. */
public final class SodiumRenderDevice implements RenderDevice {
    private boolean active;
    private final Commands commands = new Commands();
    @Override public CommandList createCommandList() {
        if (!active) throw new IllegalStateException("Unmanaged Sodium Metal command list");
        return commands;
    }
    @Override public void makeActive() { active = true; }
    @Override public void makeInactive() { active = false; }
    @Override public void invalidateBufferBinding(GlBufferTarget target) { /* explicit buffers, no global binding points */ }
    @Override public GLCapabilities getCapabilities() { throw new UnsupportedOperationException("Metal has no GL capabilities"); }
    @Override public DeviceFunctions getDeviceFunctions() { throw new UnsupportedOperationException("Use Metal command lists"); }
    @Override public int getSubTexelPrecisionBits() { return 8; }

    private static final class TerrainTessellation extends GlAbstractTessellation {
        TerrainTessellation(GlPrimitiveType primitive, TessellationBinding[] bindings) { super(primitive, bindings); }
        @Override public void bind(CommandList commands) {}
        @Override public void unbind(CommandList commands) {}
        @Override public void delete(CommandList commands) {} // owns references, never owns the arena buffers
        void draw(MultiDrawBatch batch, GlIndexType type) { SodiumMetal.multiDraw(primitiveType, bindings, batch, type); }
    }

    private static final class Commands implements CommandList, DrawCommandList {
        private TerrainTessellation tessellation;
        @Override public GlMutableBuffer createMutableBuffer() { return new GlMutableBuffer(); }
        @Override public GlImmutableBuffer createImmutableBuffer(long size, EnumBitField<GlBufferStorageFlags> flags) {
            GlImmutableBuffer b = new GlImmutableBuffer(flags);
            SodiumMetal.allocate(b.handle(), size, 0);
            return b;
        }
        @Override public GlTessellation createTessellation(GlPrimitiveType type, TessellationBinding[] bindings) { return new TerrainTessellation(type, bindings); }
        @Override public void bindVertexArray(GlVertexArray array) { throw new UnsupportedOperationException("Metal terrain has no vertex arrays"); }
        @Override public void unbindVertexArray() {}
        @Override public void deleteVertexArray(GlVertexArray array) { throw new UnsupportedOperationException("Metal terrain has no vertex arrays"); }
        @Override public void bindBuffer(GlBufferTarget target, GlBuffer buffer) {} // draw bindings passed explicitly
        @Override public void uploadData(GlMutableBuffer b, ByteBuffer data, GlBufferUsage usage) {
            SodiumMetal.allocate(b.handle(), data.remaining(), MemoryUtil.memAddress(data));
            b.setSize(data.remaining());
        }
        @Override public void uploadDataToOffset(GlMutableBuffer b, int offset, long pointer, int size) { SodiumMetal.subData(b.handle(), offset, pointer, size); }
        @Override public void copyBufferSubData(GlBuffer src, GlBuffer dst, long read, long write, long length) { SodiumMetal.copy(src.handle(), dst.handle(), read, write, length); }
        @Override public void allocateStorage(GlMutableBuffer b, long size, GlBufferUsage usage) { SodiumMetal.allocate(b.handle(), size, 0); b.setSize(size); }
        @Override public void deleteBuffer(GlBuffer b) {
            if (b.getActiveMapping() != null) unmap(b.getActiveMapping());
            int id = b.handle();
            b.invalidateHandle();
            SodiumMetal.deleteBuffer(id);
        }
        @Override public GlBufferMapping mapBuffer(GlBuffer b, long offset, long length, EnumBitField<GlBufferMapFlags> flags) {
            if (b.getActiveMapping() != null) throw new IllegalStateException("Already mapped Sodium buffer");
            if (flags.contains(GlBufferMapFlags.PERSISTENT) && (!(b instanceof GlImmutableBuffer immutable)
                    || !immutable.getFlags().contains(GlBufferStorageFlags.PERSISTENT))) throw new IllegalArgumentException("Invalid persistent mapping");
            GlBufferMapping mapping = new GlBufferMapping(b, SodiumMetal.map(b.handle(), offset, length));
            b.setActiveMapping(mapping);
            return mapping;
        }
        @Override public void unmap(GlBufferMapping mapping) {
            if (mapping.isDisposed()) throw new IllegalStateException("Disposed Sodium mapping");
            mapping.getBufferObject().setActiveMapping(null);
            mapping.dispose();
        }
        @Override public void flushMappedRange(GlBufferMapping mapping, int offset, int length) {
            if (mapping.isDisposed() || offset < 0 || length < 0 || (long) offset + length > mapping.getMemoryBuffer().capacity())
                throw new IllegalArgumentException("Invalid Sodium mapped flush");
            // Apple unified shared memory is coherent. Sodium fences each consumed staging range before reuse.
        }
        @Override public GlFence createFence() { return new TerrainFence(Mtl.fence()); }
        @Override public DrawCommandList beginTessellating(GlTessellation t) { tessellation = (TerrainTessellation) t; return this; }
        @Override public void deleteTessellation(GlTessellation t) { t.delete(this); }
        @Override public void multiDrawElementsBaseVertex(MultiDrawBatch batch, GlIndexType type) {
            if (tessellation == null) throw new IllegalStateException("Missing Sodium tessellation");
            tessellation.draw(batch, type);
        }
        @Override public void endTessellating() { tessellation = null; }
        @Override public void flush() { endTessellating(); }
        @Override public void close() { flush(); }
    }

    private static final class TerrainFence extends GlFence {
        private final long value;
        private boolean disposed;
        TerrainFence(long value) { super(0); this.value = value; }
        private void check() { if (disposed) throw new IllegalStateException("Disposed terrain fence"); }
        @Override public boolean isCompleted() { check(); return Mtl.completedFence() >= value; }
        @Override public void sync() { sync(Long.MAX_VALUE); }
        @Override public void sync(long nanos) {
            check();
            // A failed wait must never permit reuse of storage still read by the GPU.
            if (!Mtl.fenceWait(value, nanos == Long.MAX_VALUE ? 5000 : Math.max(1, nanos / 1_000_000)))
                throw new IllegalStateException("Metal Sodium staging fence timed out");
        }
        @Override public void delete() { check(); disposed = true; } // scalar shared-event value, no native allocation
    }
}

// SPDX-License-Identifier: GPL-3.0-only
// Derived from Im-Fran/MetalCraft, commit a2cc82780d01a51d00a297f75cf07c221d7c8700.
package dev.eviemod.metal.device;

import dev.eviemod.metal.mtl.Mtl;
import dev.eviemod.metal.shader.ShaderTranslator;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPass.Draw;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import java.nio.IntBuffer;
import org.lwjgl.PointerBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.MemoryStack;

/** GL-style render pass state (uniforms/samplers bound by name, persisting across pipeline changes) replayed onto a Metal render encoder. */
public class MetalRenderPass implements RenderPassBackend {
    private final MetalCommandEncoder encoder;
    private final int colorFormat, depthFormat, width, height;
    private @Nullable MetalPipeline pipeline, boundPipeline;
    private final Map<String, GpuBufferSlice> uniforms = new HashMap<>();
    private final Map<String, Binding> samplers = new HashMap<>();
    private final GpuBufferSlice[] vertexBuffers = new GpuBufferSlice[4];
    private final long[] vertexHandles = new long[4], vertexOffsets = new long[4];
    private @Nullable GpuBuffer indexBuffer;
    private IndexType indexType = IndexType.INT;
    private final RenderPass.RenderArea renderArea;
    private boolean scissor;
    private int scissorX, scissorY, scissorW, scissorH;
    private long boundScissor = -1;
    private int debugGroups;
    private boolean closed;

    private record Binding(MetalTextureView view, MetalSampler sampler) {}

    MetalRenderPass(MetalCommandEncoder encoder, GpuTextureView color, @Nullable GpuTextureView depth, @Nullable RenderPass.RenderArea area) {
        this.encoder = encoder;
        this.renderArea = area;
        this.colorFormat = MetalFormats.texture(color.texture().getFormat());
        this.depthFormat = depth != null ? MetalFormats.texture(depth.texture().getFormat()) : -1;
        this.width = color.getWidth(0);
        this.height = color.getHeight(0);
    }

    // ponytail: debug groups are counted but not forwarded; push them to the encoder when GPU captures need labels.
    @Override
    public void pushDebugGroup(Supplier<String> label) {
        debugGroups++;
    }

    @Override
    public void popDebugGroup() {
        if (debugGroups == 0) throw new IllegalStateException("Can't pop more debug groups than was pushed!");
        debugGroups--;
    }

    @Override
    public void setPipeline(RenderPipeline pipeline) {
        checkOpen();
        try {
            this.pipeline = encoder.device.getOrCompilePipeline(pipeline);
        } catch (RuntimeException | Error e) {
            abort(e);
            throw e;
        }
    }

    @Override
    public void bindTexture(String name, @Nullable GpuTextureView view, @Nullable GpuSampler sampler) {
        if (sampler == null || view == null) samplers.remove(name);
        else samplers.put(name, new Binding((MetalTextureView) view, (MetalSampler) sampler));
    }

    @Override
    public void setUniform(String name, GpuBuffer buffer) {
        uniforms.put(name, buffer.slice());
    }

    @Override
    public void setUniform(String name, GpuBufferSlice slice) {
        if (slice.offset() % encoder.device.getUniformOffsetAlignment() > 0) {
            throw new IllegalArgumentException("Uniform buffer offset must be aligned to " + encoder.device.getUniformOffsetAlignment());
        }
        uniforms.put(name, slice);
    }

    @Override
    public void enableScissor(int x, int y, int w, int h) {
        scissor = true;
        scissorX = x;
        scissorY = y;
        scissorW = w;
        scissorH = h;
    }

    @Override
    public void disableScissor() {
        scissor = false;
    }

    @Override public void setVertexBuffer(int slot, GpuBufferSlice slice) {
        java.util.Objects.checkIndex(slot, vertexBuffers.length); vertexBuffers[slot] = slice;
    }

    @Override
    public void setIndexBuffer(@Nullable GpuBuffer buffer, IndexType type) {
        indexBuffer = buffer;
        indexType = type;
    }

    @Override
    public void drawIndexed(int count, int instances, int firstIndex, int baseVertex, int firstInstance) {
        checkOpen();
        if (setup()) drawIndexed(indexBuffer, indexType, baseVertex, firstIndex, count, instances, firstInstance);
    }

    @Override
    public <T> void drawMultipleIndexed(Collection<Draw<T>> draws, @Nullable GpuBuffer defaultIndexBuffer, @Nullable IndexType defaultIndexType,
                                        Collection<String> dynamicUniforms, T context) {
        checkOpen();
        IndexType fallbackType = defaultIndexType != null ? defaultIndexType : IndexType.SHORT;
        for (Draw<T> draw : draws) {
            setVertexBuffer(draw.slot(), draw.vertexBuffer().slice());
            if (draw.uniformUploaderConsumer() != null) draw.uniformUploaderConsumer().accept(context, uniforms::put);
            if (!setup()) return;
            GpuBuffer ib = draw.indexBuffer() != null ? draw.indexBuffer() : defaultIndexBuffer;
            drawIndexed(ib, draw.indexType() != null ? draw.indexType() : fallbackType, draw.baseVertex(), draw.firstIndex(), draw.indexCount(), 1, 0);
        }
    }

    @Override
    public void draw(int count, int instances, int first, int firstInstance) {
        checkOpen();
        if (!setup()) return;
        if (pipeline.info.getPrimitiveTopology() == PrimitiveTopology.TRIANGLE_FAN) {
            if (firstInstance != 0) throw new UnsupportedOperationException("Nonzero first instance for triangle fans");
            if (count >= 3) Mtl.drawIndexed(Mtl.PRIMITIVE_TRIANGLE, (count - 2) * 3, true, sequentialFanIndices(count), 0, instances, first);
        } else {
            Mtl.drawInstanced(pipeline.primitive, first, count, instances, firstInstance);
        }
    }

    private void drawIndexed(@Nullable GpuBuffer ib, IndexType type, int baseVertex, int firstIndex, int count, int instances, int firstInstance) {
        if (ib == null) throw new IllegalStateException("Missing index buffer");
        if (pipeline.info.getPrimitiveTopology() == PrimitiveTopology.TRIANGLE_FAN) {
            long base = ((MetalBuffer) ib).address() + (long) firstIndex * type.bytes;
            if (firstInstance != 0) throw new UnsupportedOperationException("Nonzero first instance for triangle fans");
            drawFan(type == IndexType.SHORT ? i -> Short.toUnsignedInt(MemoryUtil.memGetShort(base + 2L * i)) : i -> MemoryUtil.memGetInt(base + 4L * i),
                    baseVertex, count, instances);
            return;
        }
        Mtl.drawIndexedInstanced(pipeline.primitive, count, type == IndexType.INT, ((MetalBuffer) ib).handle, (long) firstIndex * type.bytes, instances, baseVertex, firstInstance);
    }

    @Override public void multiDrawIndexed(PointerBuffer offsets, IntBuffer counts, IntBuffer baseVertices, int drawCount) {
        checkOpen();
        if (!setup()) return;
        if (indexBuffer == null || indexBuffer.isClosed()) throw new IllegalStateException("Missing or closed index buffer");
        if (pipeline.info.getPrimitiveTopology() == PrimitiveTopology.TRIANGLE_FAN) throw new UnsupportedOperationException("Triangle-fan multidraw");
        if (drawCount < 0 || drawCount > offsets.remaining() || drawCount > counts.remaining() || drawCount > baseVertices.remaining()) throw new IllegalArgumentException("Multidraw parameters exceed supplied arrays");
        Mtl.multiDrawIndexed(pipeline.primitive, indexType == IndexType.INT, ((MetalBuffer) indexBuffer).handle,
                MemoryUtil.memAddress(counts), MemoryUtil.memAddress(offsets), MemoryUtil.memAddress(baseVertices), drawCount);
    }
    @Override public void multiDraw(IntBuffer firstVertices, IntBuffer counts, int drawCount) {
        if (drawCount < 0 || drawCount > firstVertices.remaining() || drawCount > counts.remaining()) throw new IllegalArgumentException("Multidraw parameters exceed supplied arrays");
        for (int i = 0; i < drawCount; i++) draw(counts.get(counts.position() + i), 1, firstVertices.get(firstVertices.position() + i), 0);
    }
    @Override public void multiDrawIndexed(IntBuffer parameters, int instances, int firstInstance, int count) { throw new UnsupportedOperationException("Metal does not advertise interleaved direct multidraw"); }
    @Override public void multiDraw(IntBuffer parameters, int instances, int firstInstance, int count) { throw new UnsupportedOperationException("Metal does not advertise interleaved direct multidraw"); }
    @Override public void drawIndexedIndirect(GpuBufferSlice commands, int count) { throw new UnsupportedOperationException("Metal does not advertise indirect draws"); }
    @Override public void drawIndirect(GpuBufferSlice commands, int count) { throw new UnsupportedOperationException("Metal does not advertise indirect draws"); }
    @Override public void writeTimestamp(com.mojang.blaze3d.systems.GpuQueryPool pool, int index) { ((MetalQueryPool) pool).write(index); boundPipeline = null; }

    private final Map<String, Number[]> defaults = new HashMap<>();
    public void setDefaultUniform(String name, Number... values) { checkOpen(); defaults.put(name, values.clone()); }
    private void bindDefaults(ShaderTranslator.Result stage, boolean fragment) {
        if (stage.defaultsSize() == 0) return;
        try (var stack = MemoryStack.stackPush()) {
            var data = stack.calloc(stage.defaultsSize());
            for (var entry : defaults.entrySet()) {
                Integer offset = stage.defaults().get(entry.getKey());
                if (offset == null) continue;
                for (int i = 0; i < entry.getValue().length; i++) {
                    Number value = entry.getValue()[i];
                    if (value instanceof Float || value instanceof Double) data.putFloat(offset + i * 4, value.floatValue());
                    else data.putInt(offset + i * 4, value.intValue());
                }
            }
            Mtl.setBytes(fragment, stage.buffers().get(ShaderTranslator.DEFAULT_BLOCK), MemoryUtil.memAddress(data), data.remaining());
        }
    }

    private static long fanIndices;
    private static int fanCapacity;

    static void closeSharedBuffers() {
        Mtl.release(fanIndices);
        fanIndices = 0;
        fanCapacity = 0;
    }

    /**
     * Triangle-list indices for a fan of {@code count} consecutive vertices. The list for n vertices is a prefix of the
     * list for any larger n, so one shared buffer, grown on demand, serves every size (the sky draws fans each frame).
     */
    private static long sequentialFanIndices(int count) {
        if (count > fanCapacity) {
            int capacity = Math.max(count, 64);
            long buffer = Mtl.newBuffer((capacity - 2) * 12L);
            if (buffer == 0) throw new com.mojang.blaze3d.GpuOutOfMemoryException("Metal fan buffer allocation failed");
            long p = Mtl.bufferContents(buffer);
            for (int i = 0; i < capacity - 2; i++) {
                MemoryUtil.memPutInt(p + i * 12L, 0);
                MemoryUtil.memPutInt(p + i * 12L + 4, i + 1);
                MemoryUtil.memPutInt(p + i * 12L + 8, i + 2);
            }
            Mtl.release(fanIndices);  // Draws already recorded keep the old buffer alive.
            fanIndices = buffer;
            fanCapacity = capacity;
        }
        return fanIndices;
    }

    /** Metal has no triangle fans: expand (v0, vi, vi+1) into a triangle list. */
    // ponytail: indexed fans only come from debug renderers, so they're expanded on the CPU into a throwaway buffer.
    private void drawFan(java.util.function.IntUnaryOperator index, int baseVertex, int count, int instances) {
        if (count < 3) return;
        int triangles = count - 2;
        long buffer = Mtl.newBuffer(triangles * 12L);
        if (buffer == 0) throw new com.mojang.blaze3d.GpuOutOfMemoryException("Metal fan buffer allocation failed");
        try {
            long p = Mtl.bufferContents(buffer);
            for (int i = 0; i < triangles; i++) {
                MemoryUtil.memPutInt(p + i * 12L, index.applyAsInt(0));
                MemoryUtil.memPutInt(p + i * 12L + 4, index.applyAsInt(i + 1));
                MemoryUtil.memPutInt(p + i * 12L + 8, index.applyAsInt(i + 2));
            }
            Mtl.drawIndexed(Mtl.PRIMITIVE_TRIANGLE, triangles * 3, true, buffer, 0, instances, baseVertex);
        } finally { Mtl.release(buffer); }
    }

    /** Binds pipeline state and every resource the pipeline's shaders reference. Returns false (skip the draw) for invalid pipelines, like GL does. */
    private boolean setup() {
        if (pipeline == null || !pipeline.isValid()) return false;
        if (pipeline != boundPipeline) {
            long pso;
            try {
                pso = pipeline.state(colorFormat, depthFormat);
            } catch (RuntimeException | Error e) {
                abort(e);
                throw e;
            }
            if (pso == 0) return false;
            RenderPipeline info = pipeline.info;
            Mtl.setPipelineState(pso, depthFormat >= 0 ? pipeline.depthState : encoder.noDepthState(), pipeline.cull,
                    info.getPolygonMode() == PolygonMode.WIREFRAME, (info.getDepthStencilState() == null ? 0 : info.getDepthStencilState().depthBiasConstant()), (info.getDepthStencilState() == null ? 0 : info.getDepthStencilState().depthBiasScaleFactor()));
            if (pipeline.hasMissingAttributes) {
                // setBytes copies these zeros into the encoder before the stack allocation expires.
                try (var stack = MemoryStack.stackPush()) {
                    Mtl.setBytes(false, 26,
                            MemoryUtil.memAddress(stack.calloc(16)), 16);
                }
            }
            boundPipeline = pipeline;
        }

        for (int slot = 0; slot < vertexBuffers.length; slot++) {
            var slice = vertexBuffers[slot];
            if (slice == null) continue;
            var buffer = (MetalBuffer) slice.buffer();
            if (buffer.isClosed()) throw new IllegalStateException("Closed vertex buffer at slot " + slot);
            if (vertexHandles[slot] != buffer.handle || vertexOffsets[slot] != slice.offset()) {
                Mtl.setBuffer(false, ShaderTranslator.VERTEX_BUFFER_INDEX - slot, buffer.handle, slice.offset());
                vertexHandles[slot] = buffer.handle; vertexOffsets[slot] = slice.offset();
            }
        }
        bindDefaults(pipeline.vertex, false);
        bindDefaults(pipeline.fragment, true);

        bindStage(pipeline.vertex, false);
        bindStage(pipeline.fragment, true);

        int x = renderArea == null ? 0 : renderArea.x(), y = renderArea == null ? 0 : renderArea.y();
        int w = renderArea == null ? width : renderArea.width(), h = renderArea == null ? height : renderArea.height();
        if (scissor) {
            // Metal rejects scissor rects outside the attachment; GL just clips.
            int right = x + w, top = y + h;
            x = Math.clamp(scissorX, x, right);
            y = Math.clamp(scissorY, y, top);
            w = Math.clamp(scissorX + scissorW, x, right) - x;
            h = Math.clamp(scissorY + scissorH, y, top) - y;
        }
        long key = ((long) x << 48) | ((long) y << 32) | ((long) w << 16) | h;
        if (key != boundScissor) {
            Mtl.setScissor(x, y, w, h);
            boundScissor = key;
        }
        return true;
    }

    private void bindStage(ShaderTranslator.Result stage, boolean fragment) {
        for (var e : stage.buffers().entrySet()) {
            GpuBufferSlice slice = uniforms.get(e.getKey());
            // Stale bindings to buffers Minecraft has since closed are harmless in GL but dangling pointers in Metal.
            if (slice != null && !slice.buffer().isClosed()) bindBuffer(fragment, e.getValue(), ((MetalBuffer) slice.buffer()).handle, slice.offset());
        }
        for (var e : stage.textures().entrySet()) {
            String name = e.getKey();
            Binding binding = samplers.get(name);
            if (binding != null && !binding.view.isClosed() && !binding.sampler.isClosed()) {
                bindTexture(fragment, e.getValue(), binding.view.handle, stage.samplers().getOrDefault(name, -1), binding.sampler.handle);
                continue;
            }
            GpuBufferSlice texel = uniforms.get(name);
            GpuFormat format = texel != null ? pipeline.texelFormat(name) : null;
            if (format != null && !texel.buffer().isClosed()) bindTexture(fragment, e.getValue(), ((MetalBuffer) texel.buffer()).texelView(format, texel.offset(), texel.length()), -1, 0);
        }
    }

    // What this pass has bound per [stage][index], so unchanged resources skip the JNI call. A merged encoder may
    // still hold older bindings, which is fine: starting empty only means rebinding once.
    private final long[][] boundBuffers = new long[2][32], boundOffsets = new long[2][32];
    private final long[][] boundTextures = new long[2][32], boundSamplers = new long[2][32];

    private void bindBuffer(boolean fragment, int index, long buffer, long offset) {
        int s = fragment ? 1 : 0;
        if (boundBuffers[s][index] == buffer && boundOffsets[s][index] == offset) return;
        boundBuffers[s][index] = buffer;
        boundOffsets[s][index] = offset;
        Mtl.setBuffer(fragment, index, buffer, offset);
    }

    private void bindTexture(boolean fragment, int index, long texture, int samplerIndex, long sampler) {
        int s = fragment ? 1 : 0;
        if (boundTextures[s][index] == texture && (samplerIndex < 0 || boundSamplers[s][samplerIndex] == sampler)) return;
        boundTextures[s][index] = texture;
        if (samplerIndex >= 0) boundSamplers[s][samplerIndex] = sampler;
        Mtl.setTexture(fragment, index, texture, samplerIndex, sampler);
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("Can't use a closed render pass");
    }

    public boolean isClosed() { return closed; }

    /** A caller may catch a shader error without closing its pass. Release backend ownership immediately. */
    private void abort(Throwable failure) {
        debugGroups = 0;
        try { close(); }
        catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
    }

    public void close() {
        if (closed) return;
        closed = true;
        encoder.finishRenderPass();
        if (debugGroups > 0) throw new IllegalStateException("Render pass had debug groups left open!");
    }
}

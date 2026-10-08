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
public class MetalRenderPass implements RenderPassBackend, AutoCloseable {
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

    MetalRenderPass(MetalCommandEncoder encoder, GpuTextureView color, @Nullable GpuTextureView depth, RenderPass.@Nullable RenderArea area) {
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
        resourceRevision++;
    }

    @Override
    public void setUniform(String name, GpuBuffer buffer) {
        setUniform(name, buffer.slice());
    }

    @Override
    public void setUniform(String name, GpuBufferSlice slice) {
        checkOpen();
        MetalRanges.slice(slice, 0);
        if (slice.offset() % encoder.device.getUniformOffsetAlignment() > 0) {
            throw new IllegalArgumentException("Uniform buffer offset must be aligned to " + encoder.device.getUniformOffsetAlignment());
        }
        uniforms.put(name, slice);
        resourceRevision++;
    }

    @Override
    public void enableScissor(int x, int y, int w, int h) {
        checkOpen();
        if (w < 0 || h < 0) throw new IllegalArgumentException("Negative scissor extent");
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
        checkOpen();
        java.util.Objects.checkIndex(slot, vertexBuffers.length);
        MetalRanges.slice(slice, GpuBuffer.USAGE_VERTEX);
        vertexBuffers[slot] = slice;
    }

    @Override
    public void setIndexBuffer(@Nullable GpuBuffer buffer, IndexType type) {
        checkOpen();
        if (buffer != null) MetalRanges.buffer(buffer, 0, buffer.size(), GpuBuffer.USAGE_INDEX);
        indexBuffer = buffer;
        indexType = java.util.Objects.requireNonNull(type);
    }

    @Override
    public void drawIndexed(int count, int instances, int firstIndex, int baseVertex, int firstInstance) {
        checkOpen();
        MetalRanges.draw(count, instances, firstIndex, firstInstance);
        if (setup()) drawIndexed(indexBuffer, indexType, baseVertex, firstIndex, count, instances, firstInstance);
    }

    @Override
    public <T> void drawMultipleIndexed(Collection<Draw<T>> draws, @Nullable GpuBuffer defaultIndexBuffer, @Nullable IndexType defaultIndexType,
                                        Collection<String> dynamicUniforms, T context) {
        checkOpen();
        IndexType fallbackType = defaultIndexType != null ? defaultIndexType : IndexType.SHORT;
        for (Draw<T> draw : draws) {
            setVertexBuffer(draw.slot(), draw.vertexBuffer().slice());
            if (draw.uniformUploaderConsumer() != null) draw.uniformUploaderConsumer().accept(context, this::setUniform);
            if (!setup()) return;
            GpuBuffer ib = draw.indexBuffer() != null ? draw.indexBuffer() : defaultIndexBuffer;
            drawIndexed(ib, draw.indexType() != null ? draw.indexType() : fallbackType, draw.baseVertex(), draw.firstIndex(), draw.indexCount(), 1, 0);
        }
    }

    @Override
    public void draw(int count, int instances, int first, int firstInstance) {
        checkOpen();
        MetalRanges.draw(count, instances, first, firstInstance);
        if (count == 0 || instances == 0 || !setup()) return;
        validateVertexRanges(count, instances, first, firstInstance, false);
        if (pipeline.info.getPrimitiveTopology() == PrimitiveTopology.TRIANGLE_FAN) {
            if (firstInstance != 0) throw new UnsupportedOperationException("Nonzero first instance for triangle fans");
            if (count >= 3) Mtl.drawIndexed(Mtl.PRIMITIVE_TRIANGLE, Math.multiplyExact(count - 2, 3), true, sequentialFanIndices(count), 0, instances, first);
        } else {
            Mtl.drawInstanced(pipeline.primitive, first, count, instances, firstInstance);
        }
    }

    private void drawIndexed(@Nullable GpuBuffer ib, IndexType type, int baseVertex, int firstIndex, int count, int instances, int firstInstance) {
        MetalRanges.draw(count, instances, firstIndex, firstInstance);
        if (ib == null) throw new IllegalStateException("Missing index buffer");
        MetalRanges.buffer(ib, (long) firstIndex * type.bytes, (long) count * type.bytes, GpuBuffer.USAGE_INDEX);
        if (count == 0 || instances == 0) return;
        validateVertexRanges(count, instances, 0, firstInstance, true);
        if (pipeline.info.getPrimitiveTopology() == PrimitiveTopology.TRIANGLE_FAN) {
            if (((MetalBuffer) ib).hasPendingGpuWrite())
                throw new UnsupportedOperationException("CPU-expanded triangle fans require completed index-buffer writes");
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
        for (int i = 0; i < drawCount; i++) {
            long offset = offsets.get(offsets.position() + i);
            int count = counts.get(counts.position() + i);
            if (count < 0 || offset % indexType.bytes != 0) throw new IllegalArgumentException("Invalid multidraw index range");
            MetalRanges.buffer(indexBuffer, offset, (long) count * indexType.bytes, GpuBuffer.USAGE_INDEX);
        }
        if (drawCount == 0) return;
        validateVertexRanges(0, 1, 0, 0, true);
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
    @Override public void writeTimestamp(com.mojang.blaze3d.systems.GpuQueryPool pool, int index) { ((MetalQueryPool) pool).write(index); boundPipeline = null; boundScissor = -1;
        java.util.Arrays.fill(vertexHandles, 0);
        for (int stage = 0; stage < 2; stage++) { java.util.Arrays.fill(boundBuffers[stage], 0); java.util.Arrays.fill(boundTextures[stage], 0); java.util.Arrays.fill(boundSamplers[stage], 0); } }

    // Indexed vertex contents remain GPU-owned; only their byte ranges and per-instance fetches can be checked here.
    private void validateVertexRanges(int count, int instances, int first, int firstInstance, boolean indexed) {
        var formats = pipeline.info.getVertexFormatBindings();
        for (int slot = 0; slot < vertexBuffers.length && slot < formats.length; slot++) {
            var format = formats[slot];
            var slice = vertexBuffers[slot];
            if (!pipeline.requiredVertexSlots[slot] || format == null || slice == null) continue;
            int rate = format.getStepRate();
            if (indexed && rate == 0) continue;
            long last = rate == 0 ? (long) first + count - 1 : ((long) firstInstance + instances - 1) / rate;
            if (last >= 0 && last * format.getVertexSize() + pipeline.requiredVertexBytes[slot] > slice.length())
                throw new IllegalArgumentException("Draw exceeds vertex slice at slot " + slot);
        }
    }

    private final Map<String, int[]> defaults = new HashMap<>();
    private long resourceRevision, defaultRevision;
    private final Map<MetalPipeline, ResolvedStage[]> bindingPlans = new java.util.IdentityHashMap<>();
    private ResolvedStage[] resolved;
    public void setDefaultFloat3(String name, float x, float y, float z) {
        setDefault(name, Float.floatToRawIntBits(x), Float.floatToRawIntBits(y), Float.floatToRawIntBits(z), 3);
    }
    public void setDefaultInt(String name, int value) { setDefault(name, value, 0, 0, 1); }
    private void setDefault(String name, int x, int y, int z, int words) {
        checkOpen();
        var bits = defaults.computeIfAbsent(name, ignored -> new int[4]);
        if (bits[0] == x && bits[1] == y && bits[2] == z && bits[3] == words) return;
        bits[0] = x; bits[1] = y; bits[2] = z; bits[3] = words; defaultRevision++;
    }
    private final class ResolvedStage {
        final MetalPipeline.BindingPlan plan;
        final boolean fragment;
        final GpuBufferSlice[] buffers;
        final Binding[] textures;
        final GpuBufferSlice[] texels;
        final long[] texelHandles, texelGenerations;
        final int[][] defaultBits;
        long resources = -1, values = -1;
        ResolvedStage(MetalPipeline.BindingPlan plan, boolean fragment) {
            this.plan = plan; this.fragment = fragment;
            buffers = new GpuBufferSlice[plan.buffers().length];
            textures = new Binding[plan.textures().length]; texels = new GpuBufferSlice[textures.length];
            texelHandles = new long[textures.length]; texelGenerations = new long[textures.length];
            defaultBits = new int[plan.defaults().length][];
            for (int i = 0; i < defaultBits.length; i++) defaultBits[i] = defaults.computeIfAbsent(plan.defaults()[i].name(), ignored -> new int[4]);
        }
        void bind(boolean force) {
            if (resources != resourceRevision) {
                for (int i=0; i<buffers.length; i++) buffers[i] = uniforms.get(plan.buffers()[i].name());
                for (int i=0; i<textures.length; i++) {
                    var slot = plan.textures()[i];
                    textures[i] = samplers.get(slot.name()); texels[i] = uniforms.get(slot.name());
                    texelHandles[i] = 0;
                }
                resources = resourceRevision;
            }
            for (int i=0; i<buffers.length; i++) {
                var slice = buffers[i];
                if (slice == null || slice.buffer().isClosed()) throw new IllegalStateException("Missing/closed Metal uniform " + plan.buffers()[i].name());
                MetalRanges.slice(slice, GpuBuffer.USAGE_UNIFORM);
                bindBuffer(fragment, plan.buffers()[i].index(), ((MetalBuffer)slice.buffer()).handle, slice.offset());
            }
            for (int i=0; i<textures.length; i++) {
                var slot = plan.textures()[i]; var binding = textures[i];
                if (binding != null && !binding.view.isClosed() && !binding.sampler.isClosed()) {
                    bindTexture(fragment, slot.index(), binding.view.handle, slot.samplerIndex(), binding.sampler.handle);
                } else if (slot.texelFormat() != null && texels[i] != null && !texels[i].buffer().isClosed()) {
                    var slice = texels[i]; var buffer = (MetalBuffer)slice.buffer();
                    MetalRanges.slice(slice, GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER);
                    if (texelHandles[i] == 0 || texelGenerations[i] != buffer.storageGeneration()) {
                        texelHandles[i] = buffer.texelView(slot.texelFormat(), slice.offset(), slice.length());
                        texelGenerations[i] = buffer.storageGeneration();
                    }
                    bindTexture(fragment, slot.index(), texelHandles[i], -1, 0);
                } else throw new IllegalStateException("Missing/closed Metal texture " + slot.name());
            }
            if (plan.defaultSize() > 0 && (force || values != defaultRevision)) {
                try (var stack = MemoryStack.stackPush()) {
                    var bytes = stack.calloc(plan.defaultSize());
                    for (int i=0;i<defaultBits.length;i++) {
                        int offset = plan.defaults()[i].offset();
                        // Typed updates record their width; scalar and vector values never depend on variable names.
                        int words = defaultBits[i][3];
                        for (int word=0;word<words;word++) bytes.putInt(offset+word*4,defaultBits[i][word]);
                    }
                    Mtl.setBytes(fragment, plan.defaultIndex(), MemoryUtil.memAddress(bytes), bytes.remaining());
                }
                values = defaultRevision;
            }
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
        int indexCount = Math.multiplyExact(triangles, 3);
        long buffer = Mtl.newBuffer(triangles * 12L);
        if (buffer == 0) throw new com.mojang.blaze3d.GpuOutOfMemoryException("Metal fan buffer allocation failed");
        try {
            long p = Mtl.bufferContents(buffer);
            for (int i = 0; i < triangles; i++) {
                MemoryUtil.memPutInt(p + i * 12L, index.applyAsInt(0));
                MemoryUtil.memPutInt(p + i * 12L + 4, index.applyAsInt(i + 1));
                MemoryUtil.memPutInt(p + i * 12L + 8, index.applyAsInt(i + 2));
            }
            Mtl.drawIndexed(Mtl.PRIMITIVE_TRIANGLE, indexCount, true, buffer, 0, instances, baseVertex);
        } finally { Mtl.release(buffer); }
    }

    /** Binds pipeline state and every resource the pipeline's shaders reference. Returns false (skip the draw) for invalid pipelines, like GL does. */
    private boolean setup() {
        if (pipeline == null || !pipeline.isValid()) return false;
        boolean force = pipeline != boundPipeline;
        if (force) {
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
            resolved = bindingPlans.computeIfAbsent(pipeline, p -> new ResolvedStage[]{new ResolvedStage(p.vertexPlan, false), new ResolvedStage(p.fragmentPlan, true)});
            boundPipeline = pipeline;
        }

        for (int slot = 0; slot < vertexBuffers.length; slot++) {
            var slice = vertexBuffers[slot];
            if (slice == null) {
                if (pipeline.requiredVertexSlots[slot]) throw new IllegalStateException("Missing vertex buffer at slot " + slot);
                continue;
            }
            MetalRanges.slice(slice, GpuBuffer.USAGE_VERTEX);
            var buffer = (MetalBuffer) slice.buffer();
            if (buffer.isClosed()) throw new IllegalStateException("Closed vertex buffer at slot " + slot);
            if (vertexHandles[slot] != buffer.handle || vertexOffsets[slot] != slice.offset()) {
                Mtl.setBuffer(false, ShaderTranslator.VERTEX_BUFFER_INDEX - slot, buffer.handle, slice.offset());
                vertexHandles[slot] = buffer.handle; vertexOffsets[slot] = slice.offset();
            }
        }
        resolved[0].bind(force);
        resolved[1].bind(force);

        int x = renderArea == null ? 0 : renderArea.x(), y = renderArea == null ? 0 : renderArea.y();
        int w = renderArea == null ? width : renderArea.width(), h = renderArea == null ? height : renderArea.height();
        if (scissor) {
            // Metal rejects scissor rects outside the attachment; GL just clips.
            int right = x + w, top = y + h;
            x = Math.clamp(scissorX, x, right);
            y = Math.clamp(scissorY, y, top);
            w = (int) Math.clamp((long) scissorX + scissorW, (long) x, (long) right) - x;
            h = (int) Math.clamp((long) scissorY + scissorH, (long) y, (long) top) - y;
        }
        long key = ((long) x << 48) | ((long) y << 32) | ((long) w << 16) | h;
        if (key != boundScissor) {
            Mtl.setScissor(x, y, w, h);
            boundScissor = key;
        }
        return true;
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

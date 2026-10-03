// SPDX-License-Identifier: GPL-3.0-only
// Terrain/program adaptation derived from MetalCraft a2cc827; resources use current Eviemetal.
package dev.eviemod.metal.compat.sodium;

import dev.eviemod.metal.EvieMetal;
import dev.eviemod.metal.device.MetalPipeline;
import dev.eviemod.metal.device.MetalSampler;
import dev.eviemod.metal.device.MetalTextureView;
import dev.eviemod.metal.device.MetalTerrainResources;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.eviemod.metal.mtl.Mtl;
import dev.eviemod.metal.shader.ShaderTranslator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;

import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.caffeinemc.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferTarget;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlIndexType;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import net.caffeinemc.mods.sodium.client.gl.tessellation.TessellationBinding;
import org.jspecify.annotations.Nullable;

import org.lwjgl.system.MemoryUtil;

/**
 * Runs Sodium's OpenGL object model on Metal. Sodium keeps its GL-shaped code; mixins route each GL call here.
 * Handles stay plain ints, as Sodium expects. Render thread only.
 */
public final class SodiumMetal {
    private static final int GL_VERTEX_SHADER = 0x8B31;
    private static int nextHandle = 1;

    private SodiumMetal() {}

    public static int newHandle() {
        return nextHandle++;
    }

    // ---- Buffers ---------------------------------------------------------------------------------

    // IDs are Sodium object identities, never native GL objects. Each entry owns one current MetalBuffer.
    private static final Int2ObjectOpenHashMap<GpuBuffer> BUFFERS = new Int2ObjectOpenHashMap<>();
    private static final java.util.Set<Integer> BUFFER_IDS = new java.util.HashSet<>();
    private static final int USAGE = GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_UNIFORM
            | GpuBuffer.USAGE_COPY_SRC | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_MAP_WRITE;

    public static int genBuffer() {
        int id = newHandle();
        BUFFER_IDS.add(id);
        return id;
    }

    private static final Int2ObjectOpenHashMap<GpuBuffer> BORROWED = new Int2ObjectOpenHashMap<>();
    public static int borrowBuffer(GpuBuffer buffer) {
        int id = newHandle(); BORROWED.put(id, buffer); return id;
    }
    public static void forgetBorrowedBuffer(int id) { BORROWED.remove(id); }

    public static GpuBuffer buffer(int id) {
        GpuBuffer b = BUFFERS.get(id);
        if (b == null) b = BORROWED.get(id);
        if (b == null || b.isClosed()) throw new IllegalStateException("Unallocated Sodium buffer " + id);
        return b;
    }

    public static void deleteBuffer(int id) {
        BUFFER_IDS.remove(id);
        GpuBuffer b = BUFFERS.remove(id);
        if (b != null) b.close();
    }

    public static void allocate(int id, long size, long data) {
        if (!BUFFER_IDS.contains(id) || size < 0) throw new IllegalArgumentException("Invalid Sodium buffer allocation");
        // Allocate before releasing the previous storage. Already encoded commands retain it until completion.
        GpuBuffer next = size == 0 ? null : data == 0
                ? RenderSystem.getDevice().createBuffer(() -> "Sodium terrain", USAGE, size)
                : RenderSystem.getDevice().createBuffer(() -> "Sodium terrain", USAGE, MemoryUtil.memByteBuffer(data, Math.toIntExact(size)));
        GpuBuffer old = BUFFERS.remove(id);
        if (next != null) BUFFERS.put(id, next);
        if (old != null) old.close();
    }

    public static void subData(int id, long offset, long data, long length) {
        requireNoPass("update terrain buffer");
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer(id).slice(offset, length),
                MemoryUtil.memByteBuffer(data, Math.toIntExact(length)));
    }

    public static void copy(int src, int dst, long readOffset, long writeOffset, long length) {
        requireNoPass("copy terrain buffer");
        RenderSystem.getDevice().createCommandEncoder().copyToBuffer(buffer(src).slice(readOffset, length), buffer(dst).slice(writeOffset, length));
    }

    public static ByteBuffer map(int id, long offset, long length) {
        suspendPass();
        return RenderSystem.getDevice().createCommandEncoder().mapBuffer(buffer(id).slice(offset, length), false, true).data();
    }

    // ---- Shaders & programs ----------------------------------------------------------------------

    private static final Int2ObjectOpenHashMap<String> SHADER_SOURCES = new Int2ObjectOpenHashMap<>();
    private static final Int2IntOpenHashMap SHADER_TYPES = new Int2IntOpenHashMap();

    public static int createShader(int type) {
        int handle = newHandle();
        SHADER_TYPES.put(handle, type);
        return handle;
    }

    public static void shaderSource(int shader, CharSequence source) {
        SHADER_SOURCES.put(shader, source.toString());
    }

    public static void deleteShader(int shader) {
        SHADER_SOURCES.remove(shader);
        SHADER_TYPES.remove(shader);
    }

    private record Attribute(int index, int format, int offset, int stride) {}
    private record PsoKey(RenderPipeline pipeline, int colorFormat, int depthFormat, List<Attribute> attributes) {}

    private static final class Program {
        final IntArrayList shaders = new IntArrayList();
        final Map<String, Integer> attributes = new HashMap<>();
        final List<String> uniformNames = new ArrayList<>();  // GL uniform location -> name
        final List<String> blockNames = new ArrayList<>();    // GL block index -> name
        final Object2IntOpenHashMap<String> blockBindings = new Object2IntOpenHashMap<>();
        final Object2IntOpenHashMap<String> samplerUnits = new Object2IntOpenHashMap<>();
        final Map<PsoKey, Long> pipelines = new HashMap<>();
        final Map<RenderPipeline, Long> depths = new HashMap<>();
        ShaderTranslator.Result vs, fs;
        long vsFn, fsFn, vsDefaults, fsDefaults;
        String log = "";
    }

    private static final Int2ObjectOpenHashMap<Program> PROGRAMS = new Int2ObjectOpenHashMap<>();
    private static @Nullable Program current;

    public static int createProgram() {
        int handle = newHandle();
        PROGRAMS.put(handle, new Program());
        return handle;
    }

    public static void attachShader(int program, int shader) {
        PROGRAMS.get(program).shaders.add(shader);
    }

    public static void bindAttribLocation(int program, int index, CharSequence name) {
        PROGRAMS.get(program).attributes.put(name.toString(), index);
    }

    public static void link(int handle) {
        Program p = PROGRAMS.get(handle);
        String vsh = null, fsh = null;
        for (int shader : p.shaders) {
            if (SHADER_TYPES.get(shader) == GL_VERTEX_SHADER) vsh = SHADER_SOURCES.get(shader);
            else fsh = SHADER_SOURCES.get(shader);
        }
        try {
            // Attribute locations follow glBindAttribLocation, so Sodium's attribute indices are also Metal's.
            p.vs = ShaderTranslator.translate("sodium.vsh", vsh, ShaderTranslator.Stage.VERTEX, p.attributes);
            p.fs = ShaderTranslator.translate("sodium.fsh", fsh, ShaderTranslator.Stage.FRAGMENT, p.vs.outputs());
            p.vsFn = function(p.vs);
            p.fsFn = function(p.fs);
            p.vsDefaults = MemoryUtil.nmemCalloc(1, Math.max(16, p.vs.defaultsSize()));
            p.fsDefaults = MemoryUtil.nmemCalloc(1, Math.max(16, p.fs.defaultsSize()));
        } catch (ShaderTranslator.TranslationException | IllegalStateException e) {
            deleteProgram(handle);
            throw new IllegalStateException("Sodium terrain shader translation failed", e);
        }
    }

    private static long function(ShaderTranslator.Result r) {
        long library = Mtl.newLibrary(r.msl());
        try { return Mtl.newFunction(library, r.entryPoint()); }
        finally { Mtl.release(library); }
    }

    public static int linkStatus(int program) {
        return PROGRAMS.get(program).vsFn != 0 && PROGRAMS.get(program).fsFn != 0 ? 1 : 0;
    }

    public static String programLog(int program) {
        return PROGRAMS.get(program).log;
    }

    public static void useProgram(int program) {
        current = program == 0 ? null : PROGRAMS.get(program);
    }

    public static void deleteProgram(int handle) {
        Program p = PROGRAMS.remove(handle);
        if (p == null) return;
        if (current == p) current = null;
        p.pipelines.values().forEach(Mtl::release);
        p.depths.values().forEach(Mtl::release);
        Mtl.release(p.vsFn);
        Mtl.release(p.fsFn);
        MemoryUtil.nmemFree(p.vsDefaults);
        MemoryUtil.nmemFree(p.fsDefaults);
    }

    /** Locations index a per-program name table; -1 for names neither stage uses (GL semantics). */
    public static int uniformLocation(int program, CharSequence nameSeq) {
        Program p = PROGRAMS.get(program);
        String name = nameSeq.toString();
        if (p.vs == null || !(uses(p.vs, name) || uses(p.fs, name))) return -1;
        int location = p.uniformNames.indexOf(name);
        if (location < 0) {
            p.uniformNames.add(name);
            location = p.uniformNames.size() - 1;
        }
        return location;
    }

    private static boolean uses(ShaderTranslator.Result stage, String name) {
        return stage.defaults().containsKey(name) || stage.textures().containsKey(name);
    }

    public static int uniformBlockIndex(int program, CharSequence nameSeq) {
        Program p = PROGRAMS.get(program);
        String name = nameSeq.toString();
        if (p.vs == null || !(p.vs.buffers().containsKey(name) || p.fs.buffers().containsKey(name))) return -1;
        if (!p.blockNames.contains(name)) p.blockNames.add(name);
        return p.blockNames.indexOf(name);
    }

    public static void uniformBlockBinding(int program, int blockIndex, int binding) {
        Program p = PROGRAMS.get(program);
        p.blockBindings.put(p.blockNames.get(blockIndex), binding);
    }

    // ---- Uniform values (apply to the current program, like glUniform*) ------------------------

    public static void uniformInt(int location, int value) {
        Program p = current;
        if (p == null || location < 0) return;
        String name = p.uniformNames.get(location);
        if (p.vs.textures().containsKey(name) || p.fs.textures().containsKey(name)) {
            p.samplerUnits.put(name, value);  // Sampler uniforms hold texture units.
            return;
        }
        write(p, name, addr -> MemoryUtil.memPutInt(addr, value));
    }

    public static void uniformFloats(int location, float... values) {
        Program p = current;
        if (p == null || location < 0) return;
        write(p, p.uniformNames.get(location), addr -> {
            for (int i = 0; i < values.length; i++) MemoryUtil.memPutFloat(addr + 4L * i, values[i]);
        });
    }

    public static void uniformMatrix4(int location, FloatBuffer matrix) {
        Program p = current;
        if (p == null || location < 0) return;
        write(p, p.uniformNames.get(location), addr -> MemoryUtil.memCopy(MemoryUtil.memAddress(matrix), addr, 64));
    }

    private static void write(Program p, String name, java.util.function.LongConsumer writer) {
        Integer vsOffset = p.vs.defaults().get(name);
        if (vsOffset != null) writer.accept(p.vsDefaults + vsOffset);
        Integer fsOffset = p.fs.defaults().get(name);
        if (fsOffset != null) writer.accept(p.fsDefaults + fsOffset);
    }

    // ---- Global binding points ---------------------------------------------------------------

    private static final GpuBufferSlice[] UNIFORM_BUFFERS = new GpuBufferSlice[16];
    private static final long[] UNIT_VIEWS = new long[16];
    private static final long[] UNIT_SAMPLERS = new long[16];

    public static void bindUniformBuffer(int binding, int buffer) {
        UNIFORM_BUFFERS[binding] = buffer(buffer).slice();
    }

    public static void bindUniformRange(int binding, GpuBufferSlice slice) {
        UNIFORM_BUFFERS[binding] = slice;
    }

    private static final class TimeView {
        final GpuBuffer source;
        long storage, view;
        TimeView(GpuBuffer source) { this.source = source; refresh(); }
        void refresh() {
            long nextStorage = MetalTerrainResources.handle(source);
            if (nextStorage == storage) return;
            long nextView = MetalTerrainResources.sectionTimesView(source);
            if (nextView == 0) throw new IllegalStateException("Sodium section time texture allocation failed");
            Mtl.release(view);
            view = nextView; storage = nextStorage;
        }
    }
    private static final Int2ObjectOpenHashMap<TimeView> TIME_VIEWS = new Int2ObjectOpenHashMap<>();
    public static int createTimeView(GpuBuffer buffer) {
        TimeView view = new TimeView(buffer);
        int id = newHandle();
        TIME_VIEWS.put(id, view);
        return id;
    }
    public static void deleteTimeView(int id) {
        TimeView view = TIME_VIEWS.remove(id);
        if (view != null) Mtl.release(view.view);
    }
    public static void bindTimeView(int unit, int id) {
        TimeView view = TIME_VIEWS.get(id);
        if (view == null) throw new IllegalStateException("Unknown Sodium section time view");
        // Small ordered writes can orphan MetalBuffer storage. The independently retained texture view must follow it.
        view.refresh();
        UNIT_VIEWS[unit] = view.view;
        UNIT_SAMPLERS[unit] = 0;
    }

    public static void bindTexture(int unit, GpuTextureView view, GpuSampler sampler) {
        UNIT_VIEWS[unit] = ((MetalTextureView) view).handle();
        UNIT_SAMPLERS[unit] = ((MetalSampler) sampler).handle();
    }

    // ---- Render pass & draws -------------------------------------------------------------------

    private static @Nullable RenderPass pass;
    private static @Nullable RenderPipeline pipeline;
    private static @Nullable RenderTarget target;
    private static int colorFormat, depthFormat;
    private static long boundPso;

    private static long noDepthState;

    /** Replaces Sodium binding the target's GL framebuffer and applying the pipeline's GL state. */
    public static void beginPass(RenderTarget renderTarget, RenderPipeline renderPipeline) {
        if (pipeline != null) throw new IllegalStateException("Nested Sodium terrain pass");
        target = renderTarget;
        pipeline = renderPipeline;
        colorFormat = target.getColorTextureView().texture().getFormat().ordinal();
        var depth = target.getDepthTextureView();
        depthFormat = depth != null ? depth.texture().getFormat().ordinal() : -1;
        boundPso = 0;
    }

    private static void resumePass() {
        if (pass != null) return;
        if (target == null || pipeline == null) throw new IllegalStateException("No Sodium terrain target");
        var color = target.getColorTextureView();
        pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Sodium terrain " + pipeline.getLocation(),
                color, OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty());
        // Native encoders can merge across ordinary Blaze3D passes; reset inherited scissors explicitly.
        Mtl.setScissor(0, 0, color.getWidth(0), color.getHeight(0));
        boundPso = 0;
    }

    private static void suspendPass() {
        // Sodium can grow/map its shared index buffer between region draws. Preserve command ordering by ending
        // the current encoder, doing the upload/map, then reopening the same attachments with load actions.
        if (pass != null) { pass.close(); pass = null; }
        boundPso = 0;
    }

    public static void endPass() {
        try { suspendPass(); }
        finally {
            target = null; pipeline = null; current = null;
            java.util.Arrays.fill(UNIFORM_BUFFERS, null);
            java.util.Arrays.fill(UNIT_VIEWS, 0);
            java.util.Arrays.fill(UNIT_SAMPLERS, 0);
        }
    }

    private static void requireNoPass(String what) { suspendPass(); }

    public static void multiDraw(GlPrimitiveType primitiveType, TessellationBinding[] bindings, MultiDrawBatch batch, GlIndexType indexType) {
        Program p = current;
        if (batch.size == 0) return;
        if (p == null || p.vsFn == 0 || pipeline == null) throw new IllegalStateException("Missing Sodium Metal terrain pass/program");
        if (indexType == GlIndexType.UNSIGNED_BYTE) throw new UnsupportedOperationException("Metal has no 8-bit index buffers");

        GpuBuffer vertices = null, indices = null;
        GlVertexAttributeBinding[] attributes = null;
        for (TessellationBinding binding : bindings) {
            if (binding.target() == GlBufferTarget.ELEMENT_BUFFER) {
                indices = buffer(binding.buffer().handle());
            } else {
                vertices = buffer(binding.buffer().handle());
                attributes = binding.attributeBindings();
            }
        }
        if (vertices == null || indices == null) throw new IllegalStateException("Missing Sodium terrain geometry");

        resumePass();
        long pso = p.pipelines.computeIfAbsent(new PsoKey(pipeline, colorFormat, depthFormat, java.util.Arrays.stream(attributes).map(a -> new Attribute(a.getIndex(), vertexFormat(a), a.getPointer(), a.getStride())).toList()), SodiumMetal::buildPipeline);
        if (pso == 0) return;
        if (pso != boundPso) {
            Mtl.setPipelineState(pso, depthState(), pipeline.isCull() ? Mtl.CULL_BACK : Mtl.CULL_NONE,
                    pipeline.getPolygonMode() == PolygonMode.WIREFRAME, (pipeline.getDepthStencilState() == null ? 0 : pipeline.getDepthStencilState().depthBiasConstant()), (pipeline.getDepthStencilState() == null ? 0 : pipeline.getDepthStencilState().depthBiasScaleFactor()));
            boundPso = pso;
        }
        Mtl.setBuffer(false, ShaderTranslator.VERTEX_BUFFER_INDEX, MetalTerrainResources.handle(vertices), 0);
        bindStage(p, p.vs, p.vsDefaults, false);
        bindStage(p, p.fs, p.fsDefaults, true);
        Mtl.multiDrawIndexed(primitive(primitiveType), indexType == GlIndexType.UNSIGNED_INT, MetalTerrainResources.handle(indices),
                batch.pElementCount, batch.pElementPointer, batch.pBaseVertex, batch.size);
    }

    private static void bindStage(Program p, ShaderTranslator.Result stage, long defaults, boolean fragment) {
        for (var e : stage.buffers().entrySet()) {
            if (e.getKey().equals(ShaderTranslator.DEFAULT_BLOCK)) {
                Mtl.setBytes(fragment, e.getValue(), defaults, stage.defaultsSize());
            } else if (p.blockBindings.containsKey(e.getKey())) {
                GpuBufferSlice b = UNIFORM_BUFFERS[p.blockBindings.getInt(e.getKey())];
                if (b == null) throw new IllegalStateException("Missing terrain uniform " + e.getKey());
                Mtl.setBuffer(fragment, e.getValue(), MetalTerrainResources.handle(b.buffer()), b.offset());
            }
        }
        for (var e : stage.textures().entrySet()) {
            if (!p.samplerUnits.containsKey(e.getKey())) continue;
            int unit = p.samplerUnits.getInt(e.getKey());
            Mtl.setTexture(fragment, e.getValue(), UNIT_VIEWS[unit], stage.samplers().getOrDefault(e.getKey(), -1), UNIT_SAMPLERS[unit]);
        }
    }

    private static long depthState() {
        if (depthFormat < 0) {
            if (noDepthState == 0) noDepthState = Mtl.newDepthStencilState(Mtl.COMPARE_ALWAYS, false);
            return noDepthState;
        }
        return current.depths.computeIfAbsent(pipeline, rp -> {
            var d = rp.getDepthStencilState();
            return Mtl.newDepthStencilState(d == null ? Mtl.COMPARE_ALWAYS : MetalPipeline.compare(d.depthTest()), d != null && d.writeDepth());
        });
    }

    private static long buildPipeline(PsoKey key) {
        Program p = current;
        IntArrayList attribs = new IntArrayList();
        int stride = 0;
        for (Attribute a : key.attributes()) {
            if (!p.vs.inputs().containsValue(a.index())) continue;
            attribs.add(a.index());
            attribs.add(a.format());
            attribs.add(a.offset());
            stride = a.stride();
        }
        var color = key.pipeline().getColorTargetState();
        BlendFunction blend = color.blendFunction().orElse(null);
        int writeMask = (color.writeRed() ? 8 : 0) | (color.writeGreen() ? 4 : 0) | (color.writeBlue() ? 2 : 0) | (color.writeAlpha() ? 1 : 0);
        try {
            return Mtl.newRenderPipeline(p.vsFn, p.fsFn, key.colorFormat(), key.depthFormat(), blend != null,
                    blend != null ? MetalPipeline.blendFactor(blend.sourceColor().name()) : 0, blend != null ? MetalPipeline.blendFactor(blend.destColor().name()) : 0,
                    blend != null ? MetalPipeline.blendFactor(blend.sourceAlpha().name()) : 0, blend != null ? MetalPipeline.blendFactor(blend.destAlpha().name()) : 0,
                    writeMask, attribs.toIntArray(), new int[0], stride, ShaderTranslator.VERTEX_BUFFER_INDEX, "sodium:" + key.pipeline().getLocation());
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Sodium Metal terrain pipeline failed: " + key.pipeline().getLocation(), e);
        }
    }

    public static void close() {
        endPass();
        for (int id : PROGRAMS.keySet().toIntArray()) deleteProgram(id);
        for (int id : new java.util.ArrayList<>(BUFFER_IDS)) deleteBuffer(id);
        for (int id : TIME_VIEWS.keySet().toIntArray()) deleteTimeView(id);
        SHADER_SOURCES.clear(); SHADER_TYPES.clear(); BORROWED.clear();
        Mtl.release(noDepthState); noDepthState = 0;
    }

    private static int primitive(GlPrimitiveType type) {
        return switch (type) {
            case POINTS -> Mtl.PRIMITIVE_POINT;
            case LINES -> Mtl.PRIMITIVE_LINE;
            case TRIANGLES -> Mtl.PRIMITIVE_TRIANGLE;
            case PATCHES -> throw new UnsupportedOperationException("Tessellation patches aren't supported on Metal");
        };
    }

    /** GL vertex attribute (type, count, normalized, integer) -> MTLVertexFormat. */
    private static int vertexFormat(GlVertexAttributeBinding a) {
        int n = a.getCount() - 1;
        boolean integer = a.isIntType(), normalized = a.isNormalized();
        return switch (a.getFormat()) {
            case 0x1406 -> 28 + n;  // FLOAT
            case 0x1401 -> integer ? new int[]{45, 1, 2, 3}[n] : normalized ? new int[]{47, 7, 8, 9}[n] : unsupported(a);    // UNSIGNED_BYTE
            case 0x1400 -> integer ? new int[]{46, 4, 5, 6}[n] : normalized ? new int[]{48, 10, 11, 12}[n] : unsupported(a); // BYTE
            case 0x1403 -> integer ? new int[]{49, 13, 14, 15}[n] : normalized ? new int[]{51, 19, 20, 21}[n] : unsupported(a); // UNSIGNED_SHORT
            case 0x1402 -> integer ? new int[]{50, 16, 17, 18}[n] : normalized ? new int[]{52, 22, 23, 24}[n] : unsupported(a); // SHORT
            case 0x1405 -> integer ? 36 + n : unsupported(a);  // UNSIGNED_INT
            case 0x1404 -> integer ? 32 + n : unsupported(a);  // INT
            default -> unsupported(a);
        };
    }

    /** Integer data read as unnormalized float has no Metal vertex format. */
    private static int unsupported(GlVertexAttributeBinding a) {
        throw new UnsupportedOperationException("Unsupported vertex attribute format 0x" + Integer.toHexString(a.getFormat()) + " x" + a.getCount());
    }
}

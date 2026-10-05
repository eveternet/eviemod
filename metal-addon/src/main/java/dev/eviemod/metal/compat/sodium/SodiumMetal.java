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
import java.nio.ByteBuffer;
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
 * Adapts Sodium terrain programs and explicit resources to Eviemetal. CPU arenas, meshing and batches stay in Sodium.
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
        suspendPass();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer(id).slice(offset, length),
                MemoryUtil.memByteBuffer(data, Math.toIntExact(length)));
    }

    public static void copy(int src, int dst, long readOffset, long writeOffset, long length) {
        suspendPass();
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
        if (type != GL_VERTEX_SHADER && type != 0x8B30) throw new UnsupportedOperationException("Only Sodium terrain vertex/fragment shaders are supported");
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

    private record PassKey(RenderPipeline pipeline, int colorFormat, int depthFormat) {}
    private static final TerrainLayout.Pool LAYOUTS = new TerrainLayout.Pool();
    private static final boolean PROFILE = Boolean.getBoolean("eviemod-metal.terrainProfile");
    private static final TerrainBindingCache BINDINGS = new TerrainBindingCache(TerrainBindingCache.NATIVE, PROFILE);
    private static long layoutSnapshots, batches, pipelineMisses;

    static TerrainLayout acquireLayout(TessellationBinding[] bindings) {
        // Match the existing last vertex binding selection; geometry/storage still resolves at each draw.
        GlVertexAttributeBinding[] attributes = null;
        for (var binding : bindings)
            if (binding.target() != GlBufferTarget.ELEMENT_BUFFER) attributes = binding.attributeBindings();
        if (attributes == null) throw new IllegalStateException("Missing Sodium terrain layout");
        if (PROFILE) layoutSnapshots++;
        return LAYOUTS.acquire(attributes);
    }
    static void releaseLayout(TerrainLayout layout) { LAYOUTS.release(layout); }

    private static final class Program {
        final IntArrayList shaders = new IntArrayList();
        final Map<String, Integer> attributes = new HashMap<>();
        final List<String> uniformNames = new ArrayList<>();  // GL uniform location -> name
        final List<String> blockNames = new ArrayList<>();    // GL block index -> name
        final Map<PassKey, Map<TerrainLayout, Long>> pipelines = new HashMap<>();
        final Map<RenderPipeline, Long> depths = new HashMap<>();
        ShaderTranslator.Result vs, fs;
        TerrainBindingPlan vsPlan, fsPlan;
        long vsGeneration, fsGeneration;
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
            p.vsPlan = new TerrainBindingPlan(p.vs);
            p.fsPlan = new TerrainBindingPlan(p.fs);
        } catch (ShaderTranslator.TranslationException | RuntimeException | Error e) {
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
        Program next = program == 0 ? null : PROGRAMS.get(program);
        if (next != current) { BINDINGS.invalidate(); boundPso = 0; }
        current = next;
    }

    public static void deleteProgram(int handle) {
        Program p = PROGRAMS.remove(handle);
        if (p == null) return;
        if (current == p) { current = null; BINDINGS.invalidate(); boundPso = 0; }
        p.pipelines.values().forEach(variants -> {
            variants.values().forEach(Mtl::release);
            variants.keySet().forEach(LAYOUTS::release);
        });
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
        String name = p.blockNames.get(blockIndex);
        p.vsPlan.block(name, binding);
        p.fsPlan.block(name, binding);
    }

    // ---- Uniform values (apply to the current program, like glUniform*) ------------------------

    public static void uniformInt(int location, int value) {
        Program p = current;
        if (p == null || location < 0) return;
        String name = p.uniformNames.get(location);
        if (p.vs.textures().containsKey(name) || p.fs.textures().containsKey(name)) {
            p.vsPlan.sampler(name, value); // Sampler uniforms hold texture units.
            p.fsPlan.sampler(name, value);
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

    private static void write(Program p, String name, java.util.function.LongConsumer writer) {
        Integer vsOffset = p.vs.defaults().get(name);
        if (vsOffset != null) { writer.accept(p.vsDefaults + vsOffset); p.vsGeneration++; }
        Integer fsOffset = p.fs.defaults().get(name);
        if (fsOffset != null) { writer.accept(p.fsDefaults + fsOffset); p.fsGeneration++; }
    }

    // ---- Global binding points ---------------------------------------------------------------

    private static final GpuBufferSlice[] UNIFORM_BUFFERS = new GpuBufferSlice[16];
    private static final long[] UNIT_VIEWS = new long[16];
    private static final long[] UNIT_SAMPLERS = new long[16];
    private static final long[] UNIT_GENERATIONS = new long[16];
    private static final MetalTextureView[] UNIT_TEXTURES = new MetalTextureView[16];
    private static final MetalSampler[] UNIT_SAMPLER_OBJECTS = new MetalSampler[16];
    private static final TimeView[] UNIT_TIMES = new TimeView[16];
    private static final long[] UNIT_TIME_GENERATIONS = new long[16];
    private static long nextUnitGeneration;

    public static void bindUniformRange(int binding, GpuBufferSlice slice) {
        UNIFORM_BUFFERS[binding] = slice;
    }

    private static final class TimeView {
        final GpuBuffer source;
        long storage, storageGeneration, view;
        TimeView(GpuBuffer source) { this.source = source; refresh(); }
        void refresh() {
            long nextStorage = MetalTerrainResources.handle(source);
            long generation = MetalTerrainResources.storageGeneration(source);
            if (nextStorage == storage && generation == storageGeneration) return;
            long nextView = MetalTerrainResources.sectionTimesView(source);
            if (nextView == 0) throw new IllegalStateException("Sodium section time texture allocation failed");
            Mtl.release(view);
            view = nextView; storage = nextStorage; storageGeneration = generation;
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
        for (int unit = 0; unit < UNIT_TIMES.length; unit++)
            if (view != null && UNIT_TIMES[unit] == view) clearUnit(unit);
    }
    public static long bindTimeView(int unit, int id) {
        TimeView view = TIME_VIEWS.get(id);
        if (view == null) throw new IllegalStateException("Unknown Sodium section time view");
        // Small ordered writes can orphan MetalBuffer storage. The independently retained texture view must follow it.
        view.refresh();
        if (UNIT_TIMES[unit] != view || UNIT_TIME_GENERATIONS[unit] != view.storageGeneration)
            UNIT_GENERATIONS[unit] = ++nextUnitGeneration;
        UNIT_TIME_GENERATIONS[unit] = view.storageGeneration;
        UNIT_TIMES[unit] = view; UNIT_TEXTURES[unit] = null; UNIT_SAMPLER_OBJECTS[unit] = null;
        UNIT_VIEWS[unit] = view.view;
        UNIT_SAMPLERS[unit] = 0;
        return view.view; // borrowed native view; ownership stays in TIME_VIEWS
    }

    public static void bindTexture(int unit, GpuTextureView view, GpuSampler sampler) {
        if (UNIT_TEXTURES[unit] != view || UNIT_SAMPLER_OBJECTS[unit] != sampler)
            UNIT_GENERATIONS[unit] = ++nextUnitGeneration;
        UNIT_TEXTURES[unit] = (MetalTextureView) view;
        UNIT_SAMPLER_OBJECTS[unit] = (MetalSampler) sampler;
        UNIT_TIMES[unit] = null;
        UNIT_VIEWS[unit] = ((MetalTextureView) view).handle();
        UNIT_SAMPLERS[unit] = ((MetalSampler) sampler).handle();
    }

    private static void clearUnit(int unit) {
        UNIT_TEXTURES[unit] = null; UNIT_SAMPLER_OBJECTS[unit] = null; UNIT_TIMES[unit] = null;
        UNIT_VIEWS[unit] = 0; UNIT_SAMPLERS[unit] = 0; UNIT_TIME_GENERATIONS[unit] = 0;
        UNIT_GENERATIONS[unit] = ++nextUnitGeneration;
    }

    private static void refreshUnit(int unit) {
        TimeView times = UNIT_TIMES[unit];
        if (times != null) {
            times.refresh();
            if (UNIT_TIME_GENERATIONS[unit] != times.storageGeneration) {
                UNIT_TIME_GENERATIONS[unit] = times.storageGeneration;
                UNIT_VIEWS[unit] = times.view;
                UNIT_GENERATIONS[unit] = ++nextUnitGeneration;
            }
        } else if (UNIT_TEXTURES[unit] != null && (UNIT_TEXTURES[unit].isClosed() || UNIT_SAMPLER_OBJECTS[unit].isClosed())) {
            throw new IllegalStateException("Closed terrain texture/sampler");
        }
    }

    // ---- Render pass & draws -------------------------------------------------------------------

    private static @Nullable RenderPass pass;
    private static @Nullable RenderPipeline pipeline;
    private static @Nullable RenderTarget target;
    private static int colorFormat, depthFormat;
    private static @Nullable PassKey passKey;
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
        passKey = new PassKey(pipeline, colorFormat, depthFormat);
        boundPso = 0;
        BINDINGS.invalidate();
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
        BINDINGS.invalidate(); // Ordinary passes may have changed a merged native encoder's slots.
    }

    private static void suspendPass() {
        // Sodium can grow/map its shared index buffer between region draws. Preserve command ordering by ending
        // the current encoder, doing the upload/map, then reopening the same attachments with load actions.
        if (pass != null) { pass.close(); pass = null; }
        boundPso = 0;
        BINDINGS.invalidate();
    }

    public static void endPass() {
        try { suspendPass(); }
        finally {
            target = null; pipeline = null; passKey = null; current = null;
            java.util.Arrays.fill(UNIFORM_BUFFERS, null);
            for (int unit = 0; unit < UNIT_VIEWS.length; unit++) clearUnit(unit);
        }
    }

    static void multiDraw(GlPrimitiveType primitiveType, TessellationBinding[] bindings, TerrainLayout layout, MultiDrawBatch batch, GlIndexType indexType) {
        Program p = current;
        if (batch.size == 0) return;
        if (p == null || p.vsFn == 0 || pipeline == null) throw new IllegalStateException("Missing Sodium Metal terrain pass/program");
        if (indexType == GlIndexType.UNSIGNED_BYTE) throw new UnsupportedOperationException("Metal has no 8-bit index buffers");

        GpuBuffer vertices = null, indices = null;
        for (TessellationBinding binding : bindings) {
            if (binding.target() == GlBufferTarget.ELEMENT_BUFFER) {
                indices = buffer(binding.buffer().handle());
            } else {
                vertices = buffer(binding.buffer().handle());
            }
        }
        if (vertices == null || indices == null) throw new IllegalStateException("Missing Sodium terrain geometry");

        try {
            resumePass();
            if (PROFILE) batches++;
            if (BINDINGS.context(p, Mtl.renderEncoderGeneration())) boundPso = 0;
            var variants = p.pipelines.get(passKey);
            if (variants == null) { variants = new HashMap<>(); p.pipelines.put(passKey, variants); }
            Long cached = variants.get(layout);
            long pso;
            if (cached == null) {
                if (PROFILE) pipelineMisses++;
                pso = buildPipeline(p, passKey, layout);
                variants.put(layout, pso);
                LAYOUTS.retain(layout);
            } else pso = cached;
            if (pso == 0) return;
            if (pso != boundPso) {
                Mtl.setPipelineState(pso, depthState(), pipeline.isCull() ? Mtl.CULL_BACK : Mtl.CULL_NONE,
                        pipeline.getPolygonMode() == PolygonMode.WIREFRAME, (pipeline.getDepthStencilState() == null ? 0 : pipeline.getDepthStencilState().depthBiasConstant()), (pipeline.getDepthStencilState() == null ? 0 : pipeline.getDepthStencilState().depthBiasScaleFactor()));
                boundPso = pso;
            }
            BINDINGS.buffer(false, ShaderTranslator.VERTEX_BUFFER_INDEX, vertices, MetalTerrainResources.handle(vertices), 0,
                    MetalTerrainResources.storageGeneration(vertices));
            bindStage(p, p.vsPlan, p.vsDefaults, p.vsGeneration, false);
            bindStage(p, p.fsPlan, p.fsDefaults, p.fsGeneration, true);
            Mtl.multiDrawIndexed(primitive(primitiveType), indexType == GlIndexType.UNSIGNED_INT, MetalTerrainResources.handle(indices),
                    batch.pElementCount, batch.pElementPointer, batch.pBaseVertex, batch.size);
        } catch (RuntimeException | Error failure) {
            // Binding validation/compilation may fail after taking logical ownership.
            // Keep the target configured so a caller can resume after correcting the resource.
            try { suspendPass(); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void bindStage(Program p, TerrainBindingPlan plan, long defaults, long generation, boolean fragment) {
        if (plan.defaultsIndex >= 0) BINDINGS.bytes(fragment, plan.defaultsIndex, p, defaults, plan.defaultsSize, generation);
        for (var binding : plan.buffers) {
            if (binding.binding < 0) continue;
            GpuBufferSlice b = UNIFORM_BUFFERS[binding.binding];
            if (b == null) throw new IllegalStateException("Missing terrain uniform " + binding.name);
            BINDINGS.buffer(fragment, binding.index, b.buffer(), MetalTerrainResources.handle(b.buffer()), b.offset(),
                    MetalTerrainResources.storageGeneration(b.buffer()));
        }
        for (var binding : plan.textures) {
            if (binding.unit < 0) continue;
            int unit = binding.unit;
            refreshUnit(unit);
            BINDINGS.texture(fragment, binding.index, UNIT_VIEWS[unit], binding.samplerIndex, UNIT_SAMPLERS[unit], UNIT_GENERATIONS[unit]);
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

    private static long buildPipeline(Program p, PassKey key, TerrainLayout layout) {
        IntArrayList attribs = new IntArrayList();
        int stride = 0;
        for (TerrainLayout.Attribute a : layout.attributes) {
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

    /** Development assertions, no per-frame logging or retained history in the distributed addon. */
    public static String resourceSummary() {
        long bytes = 0;
        for (GpuBuffer buffer : BUFFERS.values()) bytes += buffer.size();
        int variants = 0;
        for (Program program : PROGRAMS.values())
            for (var layouts : program.pipelines.values()) variants += layouts.size();
        return "buffers=" + BUFFER_IDS.size() + " bytes=" + bytes + " borrowed=" + BORROWED.size()
                + " programs=" + PROGRAMS.size() + " pipelines=" + variants + " timeViews=" + TIME_VIEWS.size() + " layouts=" + LAYOUTS.size();
    }

    public record PerformanceCounters(long layoutSnapshots, long batches, long pipelineMisses,
                                      long bindingAttempts, long bindingCalls, long copiedDefaultBytes) {}
    public static PerformanceCounters performanceCounters() {
        return new PerformanceCounters(layoutSnapshots, batches, pipelineMisses, BINDINGS.attempted, BINDINGS.applied, BINDINGS.copiedBytes);
    }

    /** Aggregate counters only; opt in with -Deviemod-metal.terrainProfile=true. No per-draw history or logging. */
    public static String performanceSummary() {
        return "enabled=" + PROFILE + " layoutSnapshots=" + layoutSnapshots + " batches=" + batches + " pipelineMisses=" + pipelineMisses
                + " bindingAttempts=" + BINDINGS.attempted + " bindingCalls=" + BINDINGS.applied
                + " bindingSkips=" + (BINDINGS.attempted - BINDINGS.applied) + " copiedDefaultBytes=" + BINDINGS.copiedBytes;
    }

    public static void assertWorldReleased() {
        if (!BUFFER_IDS.isEmpty() || !BUFFERS.isEmpty() || !BORROWED.isEmpty() || !PROGRAMS.isEmpty() || !TIME_VIEWS.isEmpty()
                || !SHADER_SOURCES.isEmpty() || !SHADER_TYPES.isEmpty() || LAYOUTS.size() != 0 || pass != null || pipeline != null)
            throw new IllegalStateException("Sodium world retained GPU owners: " + resourceSummary());
    }

    public static void close() {
        endPass();
        for (int id : PROGRAMS.keySet().toIntArray()) deleteProgram(id);
        for (int id : new java.util.ArrayList<>(BUFFER_IDS)) deleteBuffer(id);
        for (int id : TIME_VIEWS.keySet().toIntArray()) deleteTimeView(id);
        SHADER_SOURCES.clear(); SHADER_TYPES.clear(); BORROWED.clear();
        LAYOUTS.clear();
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
    static int vertexFormat(GlVertexAttributeBinding a) {
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

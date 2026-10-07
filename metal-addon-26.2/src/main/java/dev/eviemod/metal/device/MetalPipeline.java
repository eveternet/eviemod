// SPDX-License-Identifier: GPL-3.0-only
// Derived from Im-Fran/MetalCraft, commit a2cc82780d01a51d00a297f75cf07c221d7c8700.
package dev.eviemod.metal.device;

import dev.eviemod.metal.EvieMetal;
import dev.eviemod.metal.mtl.Mtl;
import dev.eviemod.metal.shader.ShaderTranslator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** A RenderPipeline compiled to Metal. The MTLRenderPipelineState depends on the attachment formats, so variants are built lazily. */
public class MetalPipeline implements CompiledRenderPipeline {
    /** Only source/translation/library failures may enter the optional resource-pack fallback. */
    public static final class CompilationException extends IllegalStateException {
        private CompilationException(String message, Throwable cause) { super(message, cause); }
    }
    final RenderPipeline info;
    final ShaderTranslator.@Nullable Result vertex;
    final ShaderTranslator.@Nullable Result fragment;
    private final long vertexFn, fragmentFn;
    private boolean closed;
    final long depthState;
    final int primitive;
    final int cull;
    boolean hasMissingAttributes;
    final boolean[] requiredVertexSlots = new boolean[4];
    final int[] requiredVertexBytes = new int[4];
    record BufferSlot(String name, int index) {}
    record TextureSlot(String name, int index, int samplerIndex, GpuFormat texelFormat) {}
    record DefaultSlot(String name, int offset) {}
    record BindingPlan(BufferSlot[] buffers, TextureSlot[] textures, DefaultSlot[] defaults, int defaultSize, int defaultIndex) {}
    final BindingPlan vertexPlan, fragmentPlan;
    private BindingPlan bindingPlan(ShaderTranslator.Result stage) {
        var buffers = stage.buffers().entrySet().stream().filter(e -> !e.getKey().equals(ShaderTranslator.DEFAULT_BLOCK))
                .map(e -> new BufferSlot(e.getKey(), e.getValue())).toArray(BufferSlot[]::new);
        var textures = stage.textures().entrySet().stream().map(e -> new TextureSlot(e.getKey(), e.getValue(),
                stage.samplers().getOrDefault(e.getKey(), -1), texelFormat(e.getKey()))).toArray(TextureSlot[]::new);
        var defaults = stage.defaults().entrySet().stream().map(e -> new DefaultSlot(e.getKey(), e.getValue())).toArray(DefaultSlot[]::new);
        return new BindingPlan(buffers, textures, defaults, stage.defaultsSize(), stage.buffers().getOrDefault(ShaderTranslator.DEFAULT_BLOCK, -1));
    }
    private record Attachments(int color, int depth) {}
    private final java.util.Map<Attachments, Long> variants = new java.util.HashMap<>();

    private MetalPipeline(RenderPipeline info, ShaderTranslator.@Nullable Result vertex, ShaderTranslator.@Nullable Result fragment, long vertexFn, long fragmentFn) {
        this.info = info;
        this.vertex = vertex;
        this.fragment = fragment;
        BindGroupLayout.ensureCompatible(info.getBindGroupLayouts());
        var formats = info.getVertexFormatBindings();
        for (int slot = 0; slot < formats.length; slot++) {
            var format = formats[slot];
            if (format == null) continue;
            if (slot >= requiredVertexSlots.length) throw new UnsupportedOperationException("Metal supports vertex binding slots 0–3; requested " + slot);
            if (format.getVertexSize() < 0) throw new IllegalArgumentException("Negative vertex stride");
            if (format.getStepRate() < 0) throw new IllegalArgumentException("Negative vertex step rate");
            var names = new java.util.HashSet<String>();
            for (var element : format.getElements()) {
                if (!names.add(element.name())) throw new UnsupportedOperationException("Metal matrix vertex attributes are not supported");
                if (element.offset() < 0) throw new IllegalArgumentException("Negative vertex attribute offset");
                if (vertex.inputs().containsKey(element.name())) {
                    requiredVertexSlots[slot] = true;
                    requiredVertexBytes[slot] = Math.max(requiredVertexBytes[slot], Math.addExact(element.offset(), element.format().blockSize()));
                }
            }
        }
        this.vertexPlan = bindingPlan(vertex);
        this.fragmentPlan = bindingPlan(fragment);
        this.vertexFn = vertexFn;
        this.fragmentFn = fragmentFn;
        this.primitive = primitive(info.getPrimitiveTopology());
        this.cull = info.isCull() ? Mtl.CULL_BACK : Mtl.CULL_NONE;
        var depth = info.getDepthStencilState();
        this.depthState = Mtl.newDepthStencilState(depth == null ? Mtl.COMPARE_ALWAYS : compare(depth.depthTest()),
                depth != null && depth.writeDepth());
        if (depthState == 0) throw new IllegalStateException("Metal depth state allocation failed");
    }

    static MetalPipeline compile(RenderPipeline info, ShaderSource source) {
        String name = info.getLocation().toString();
        try {
            String vsh = source.get(info.getVertexShader(), ShaderType.VERTEX);
            String fsh = source.get(info.getFragmentShader(), ShaderType.FRAGMENT);
            if (vsh == null) throw new IllegalStateException("Missing vertex shader " + info.getVertexShader() + ".vsh");
            if (fsh == null) throw new IllegalStateException("Missing fragment shader " + info.getFragmentShader() + ".fsh");
            var vs = ShaderTranslator.translate(info.getVertexShader() + ".vsh", GlslPreprocessor.injectDefines(vsh, info.getShaderDefines()),
                    ShaderTranslator.Stage.VERTEX, Map.of());
            var fs = ShaderTranslator.translate(info.getFragmentShader() + ".fsh", GlslPreprocessor.injectDefines(fsh, info.getShaderDefines()),
                    ShaderTranslator.Stage.FRAGMENT, vs.outputs());
            long vertexFn = function(vs, info.getVertexShader() + ".vsh", "vertex");
            long fragmentFn = 0;
            try {
                fragmentFn = function(fs, info.getFragmentShader() + ".fsh", "fragment");
                return new MetalPipeline(info, vs, fs, vertexFn, fragmentFn);
            } catch (RuntimeException | Error e) {
                Mtl.release(vertexFn);
                Mtl.release(fragmentFn);
                throw e;
            }
        } catch (ShaderTranslator.TranslationException | IllegalStateException e) {
            EvieMetal.LOGGER.error("Couldn't compile pipeline {}: {}", name, e.getMessage());
            throw new CompilationException("Metal shader compilation failed for pipeline " + name + ": " + e.getMessage()
                    + "; set -Deviemod.metal=false and restart", e);
        }
    }

    // ponytail: one MTLLibrary per stage; a disk cache of compiled libraries (MTLBinaryArchive) is the upgrade if startup time matters.
    private static long function(ShaderTranslator.Result r, String shader, String stage) {
        try {
            long library = Mtl.newLibrary(r.msl());
            try {
                long function = Mtl.newFunction(library, r.entryPoint());
                if (function == 0) throw new IllegalStateException("Missing MSL entry point " + r.entryPoint());
                return function;
            } finally { Mtl.release(library); }
        } catch (IllegalStateException e) {
            throw new IllegalStateException(shader + " [" + stage + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isValid() {
        return !closed && vertexFn != 0;
    }

    /** Pipeline state for a pass with the given explicit native color and depth formats (-1 = no depth). */
    long state(int colorFormat, int depthFormat) {
        var key = new Attachments(colorFormat, depthFormat);
        if (variants.containsKey(key)) return variants.get(key);
        long pso;
        try {
            pso = build(colorFormat, depthFormat);
        } catch (IllegalStateException e) {
            EvieMetal.LOGGER.error("Couldn't create Metal pipeline state for {}: {}", info.getLocation(), e.getMessage());
            throw new IllegalStateException("Metal pipeline state failed for " + info.getLocation() + "; set -Deviemod.metal=false and restart", e);
        }
        variants.put(key, pso);
        return pso;
    }

    /** Format of a texel-buffer uniform (samplerBuffer), or null if the name isn't one. */
    @Nullable GpuFormat texelFormat(String name) {
        for (BindGroupLayout.UniformDescription u : BindGroupLayout.flattenUniforms(info.getBindGroupLayouts())) {
            if (u.name().equals(name) && u.type() == UniformType.TEXEL_BUFFER) return u.gpuFormat();
        }
        return null;
    }

    private long build(int colorFormat, int depthFormat) {
        BindGroupLayout.ensureCompatible(info.getBindGroupLayouts());
        if (info.getColorTargetStates().length != 1) throw new UnsupportedOperationException("Metal requires one pipeline color target");
        IntArrayList attribs = new IntArrayList(), layouts = new IntArrayList(), missing = new IntArrayList();
        var found = new java.util.HashSet<String>();
        var bindings = info.getVertexFormatBindings();
        for (int slot = 0; slot < bindings.length; slot++) {
            VertexFormat format = bindings[slot];
            if (format == null) continue;
            if (slot >= 4) throw new UnsupportedOperationException("Metal supports vertex binding slots 0–3; requested " + slot);
            int nativeSlot = ShaderTranslator.VERTEX_BUFFER_INDEX - slot;
            layouts.add(nativeSlot); layouts.add(format.getVertexSize()); layouts.add(format.getStepRate());
            for (var e : format.getElements()) {
                Integer location = vertex.inputs().get(e.name());
                if (location == null) continue;
                found.add(e.name());
                attribs.add(location); attribs.add(MetalFormats.vertex(e.format())); attribs.add(e.offset()); attribs.add(nativeSlot);
            }
        }
        for (var input : vertex.inputs().entrySet()) if (!found.contains(input.getKey())) missing.add(input.getValue().intValue());
        hasMissingAttributes = !missing.isEmpty();

        var target = info.getColorTargetState();
        BlendFunction blend = target.blendFunction().orElse(null);
        int writeMask = (target.writeRed() ? 8 : 0) | (target.writeGreen() ? 4 : 0)
                | (target.writeBlue() ? 2 : 0) | (target.writeAlpha() ? 1 : 0);
        return Mtl.newRenderPipelineBindings(vertexFn, fragmentFn, colorFormat, depthFormat,
                blend != null,
                blend == null ? 0 : blendFactor(blend.color().sourceFactor().name()), blend == null ? 0 : blendFactor(blend.color().destFactor().name()),
                blend == null ? 0 : blendFactor(blend.alpha().sourceFactor().name()), blend == null ? 0 : blendFactor(blend.alpha().destFactor().name()),
                blend == null ? 0 : blendOp(blend.color().op()), blend == null ? 0 : blendOp(blend.alpha().op()),
                writeMask, attribs.toIntArray(), missing.toIntArray(), layouts.toIntArray(), 26, info.getLocation().toString());
    }
    private static int blendOp(com.mojang.blaze3d.platform.BlendOp operation) {
        return switch (operation) { case ADD -> 0; case SUBTRACT -> 1; case REVERSE_SUBTRACT -> 2; case MIN -> 3; case MAX -> 4; };
    }

    void close() {
        if (closed) return;
        closed = true;
        for (long pso : variants.values()) Mtl.release(pso);
        variants.clear();
        Mtl.release(vertexFn);
        Mtl.release(fragmentFn);
        Mtl.release(depthState);
    }

    public static int compare(CompareOp f) {
        return switch (f) {
            case NEVER_PASS -> 0;
            case LESS_THAN -> 1;
            case EQUAL -> 2;
            case LESS_THAN_OR_EQUAL -> 3;
            case GREATER_THAN -> 4;
            case NOT_EQUAL -> 5;
            case GREATER_THAN_OR_EQUAL -> 6;
            case ALWAYS_PASS -> 7;
        };
    }

    private static int primitive(PrimitiveTopology mode) {
        return switch (mode) {
            // Minecraft expands LINES and QUADS into triangles itself (shader-side widening / quad index buffer).
            case LINES, TRIANGLES, QUADS, TRIANGLE_FAN -> Mtl.PRIMITIVE_TRIANGLE;
            case DEBUG_LINES -> Mtl.PRIMITIVE_LINE;
            case DEBUG_LINE_STRIP -> Mtl.PRIMITIVE_LINE_STRIP;
            case POINTS -> Mtl.PRIMITIVE_POINT;
            case TRIANGLE_STRIP -> Mtl.PRIMITIVE_TRIANGLE_STRIP;
        };
    }

    /** SourceFactor and DestFactor share constant names; map both to MTLBlendFactor. */
    public static int blendFactor(String name) {
        return switch (name) {
            case "ZERO" -> 0;
            case "ONE" -> 1;
            case "SRC_COLOR" -> 2;
            case "ONE_MINUS_SRC_COLOR" -> 3;
            case "SRC_ALPHA" -> 4;
            case "ONE_MINUS_SRC_ALPHA" -> 5;
            case "DST_COLOR" -> 6;
            case "ONE_MINUS_DST_COLOR" -> 7;
            case "DST_ALPHA" -> 8;
            case "ONE_MINUS_DST_ALPHA" -> 9;
            case "SRC_ALPHA_SATURATE" -> 10;
            case "CONSTANT_COLOR" -> 11;
            case "ONE_MINUS_CONSTANT_COLOR" -> 12;
            case "CONSTANT_ALPHA" -> 13;
            case "ONE_MINUS_CONSTANT_ALPHA" -> 14;
            default -> throw new IllegalArgumentException(name);
        };
    }

    /** Matches Blaze3D's GL attribute setup: COLOR/NORMAL normalized, integer UVs stay integer, everything else float. */
}

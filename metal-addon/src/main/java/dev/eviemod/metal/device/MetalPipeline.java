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
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** A RenderPipeline compiled to Metal. The MTLRenderPipelineState depends on the attachment formats, so variants are built lazily. */
public class MetalPipeline implements CompiledRenderPipeline {
    final RenderPipeline info;
    final ShaderTranslator.@Nullable Result vertex;
    final ShaderTranslator.@Nullable Result fragment;
    private final long vertexFn, fragmentFn;
    final long depthState;
    final int primitive;
    final int cull;
    private final Int2LongOpenHashMap variants = new Int2LongOpenHashMap();

    private MetalPipeline(RenderPipeline info, ShaderTranslator.@Nullable Result vertex, ShaderTranslator.@Nullable Result fragment, long vertexFn, long fragmentFn) {
        this.info = info;
        this.vertex = vertex;
        this.fragment = fragment;
        this.vertexFn = vertexFn;
        this.fragmentFn = fragmentFn;
        var depth = info.getDepthStencilState();
        this.depthState = Mtl.newDepthStencilState(depth == null ? Mtl.COMPARE_ALWAYS : compare(depth.depthTest()),
                depth != null && depth.writeDepth());
        this.primitive = primitive(info.getVertexFormatMode());
        this.cull = info.isCull() ? Mtl.CULL_BACK : Mtl.CULL_NONE;
    }

    static MetalPipeline compile(RenderPipeline info, ShaderSource source) {
        String name = info.getLocation().toString();
        try {
            String vsh = source.get(info.getVertexShader(), ShaderType.VERTEX);
            String fsh = source.get(info.getFragmentShader(), ShaderType.FRAGMENT);
            if (vsh == null || fsh == null) {
                EvieMetal.LOGGER.error("Couldn't find shader source for pipeline {}", name);
                throw new IllegalStateException("Missing Metal pipeline shader: " + name);
            }
            var vs = ShaderTranslator.translate(info.getVertexShader() + ".vsh", GlslPreprocessor.injectDefines(vsh, info.getShaderDefines()),
                    ShaderTranslator.Stage.VERTEX, Map.of());
            var fs = ShaderTranslator.translate(info.getFragmentShader() + ".fsh", GlslPreprocessor.injectDefines(fsh, info.getShaderDefines()),
                    ShaderTranslator.Stage.FRAGMENT, vs.outputs());
            long vertexFn = function(vs);
            long fragmentFn = 0;
            try {
                fragmentFn = function(fs);
                return new MetalPipeline(info, vs, fs, vertexFn, fragmentFn);
            } catch (RuntimeException e) {
                Mtl.release(vertexFn);
                Mtl.release(fragmentFn);
                throw e;
            }
        } catch (ShaderTranslator.TranslationException | IllegalStateException e) {
            EvieMetal.LOGGER.error("Couldn't compile pipeline {}: {}", name, e.getMessage());
            throw new IllegalStateException("Metal shader compilation failed for " + name + "; disable -Deviemod.metal and restart", e);
        }
    }

    // ponytail: one MTLLibrary per stage; a disk cache of compiled libraries (MTLBinaryArchive) is the upgrade if startup time matters.
    private static long function(ShaderTranslator.Result r) {
        long library = Mtl.newLibrary(r.msl());
        try { return Mtl.newFunction(library, r.entryPoint()); }
        finally { Mtl.release(library); }
    }

    @Override
    public boolean isValid() {
        return vertexFn != 0;
    }

    /** Pipeline state for a pass with the given color format ordinal and depth format ordinal (-1 = no depth). */
    long state(int colorFormat, int depthFormat) {
        int key = colorFormat * 8 + depthFormat + 1;
        if (variants.containsKey(key)) return variants.get(key);
        long pso;
        try {
            pso = build(colorFormat, depthFormat);
        } catch (IllegalStateException e) {
            EvieMetal.LOGGER.error("Couldn't create Metal pipeline state for {}: {}", info.getLocation(), e.getMessage());
            throw new IllegalStateException("Metal pipeline state failed for " + info.getLocation() + "; disable -Deviemod.metal and restart", e);
        }
        variants.put(key, pso);
        return pso;
    }

    /** Format of a texel-buffer uniform (samplerBuffer), or null if the name isn't one. */
    @Nullable TextureFormat texelFormat(String name) {
        for (RenderPipeline.UniformDescription u : info.getUniforms()) {
            if (u.name().equals(name) && u.type() == UniformType.TEXEL_BUFFER) return u.textureFormat();
        }
        return null;
    }

    private long build(int colorFormat, int depthFormat) {
        VertexFormat format = info.getVertexFormat();
        IntArrayList attribs = new IntArrayList();
        IntArrayList missing = new IntArrayList();
        Map<String, Integer> inputs = vertex.inputs();
        var names = format.getElementAttributeNames();
        var elements = format.getElements();
        for (int i = 0; i < elements.size(); i++) {
            Integer location = inputs.get(names.get(i));
            if (location == null) continue;
            VertexFormatElement e = elements.get(i);
            attribs.add(location);
            attribs.add(vertexFormat(e));
            attribs.add(format.getOffset(e));
        }
        for (var input : inputs.entrySet()) {
            if (!names.contains(input.getKey())) missing.add(input.getValue().intValue());
        }

        var target = info.getColorTargetState();
        BlendFunction blend = target.blendFunction().orElse(null);
        int writeMask = (target.writeRed() ? 8 : 0) | (target.writeGreen() ? 4 : 0)
                | (target.writeBlue() ? 2 : 0) | (target.writeAlpha() ? 1 : 0);
        return Mtl.newRenderPipeline(vertexFn, fragmentFn, colorFormat, depthFormat,
                blend != null,
                blend != null ? blendFactor(blend.sourceColor().name()) : 0, blend != null ? blendFactor(blend.destColor().name()) : 0,
                blend != null ? blendFactor(blend.sourceAlpha().name()) : 0, blend != null ? blendFactor(blend.destAlpha().name()) : 0,
                writeMask, attribs.toIntArray(), missing.toIntArray(), format.getVertexSize(),
                ShaderTranslator.VERTEX_BUFFER_INDEX, info.getLocation().toString());
    }

    void close() {
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

    private static int primitive(VertexFormat.Mode mode) {
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
    private static int vertexFormat(VertexFormatElement e) {
        int n = e.count() - 1;
        boolean normalized = e.normalized();
        return switch (e.type()) {
            case FLOAT -> 28 + n;
            case UBYTE -> (normalized ? new int[]{47, 7, 8, 9} : new int[]{45, 1, 2, 3})[n];
            case BYTE -> (normalized ? new int[]{48, 10, 11, 12} : new int[]{46, 4, 5, 6})[n];
            case USHORT -> (normalized ? new int[]{51, 19, 20, 21} : new int[]{49, 13, 14, 15})[n];
            case SHORT -> (normalized ? new int[]{52, 22, 23, 24} : new int[]{50, 16, 17, 18})[n];
            case UINT -> 36 + n;
            case INT -> 32 + n;
        };
    }
}

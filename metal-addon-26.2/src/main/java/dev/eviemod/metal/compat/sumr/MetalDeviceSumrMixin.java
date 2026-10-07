// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.compat.sumr;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import dev.eviemod.metal.device.MetalDevice;
import dev.eviemod.metal.device.MetalPipeline;
import games.enchanted.eg_stop_unloading_my_shaders.common.ModConstants;
import games.enchanted.eg_stop_unloading_my_shaders.common.ShaderReloadManager;
import games.enchanted.eg_stop_unloading_my_shaders.common.duck.GpuDeviceAdditions;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Independent Metal adapter for the installed SUMR duck interface; no upstream implementation is bundled. */
@Mixin(value = MetalDevice.class, remap = false)
abstract class MetalDeviceSumrMixin implements GpuDeviceAdditions {
    @Unique private boolean eviemetal$bypassCache;
    @Unique private boolean eviemetal$recovering;

    @Override public void eg_sumr$setBypassPipelineCache(boolean bypass) { eviemetal$bypassCache = bypass; }

    @WrapMethod(method = "precompilePipeline")
    private CompiledRenderPipeline eviemetal$precompile(RenderPipeline pipeline, @Nullable ShaderSource source,
                                                       Operation<CompiledRenderPipeline> original) {
        if (eviemetal$bypassCache) ((MetalDevice) (Object) this).invalidatePipeline(pipeline);
        try { return original.call(pipeline, source); }
        catch (MetalPipeline.CompilationException error) { return eviemetal$recover(pipeline, error); }
    }

    @WrapMethod(method = "getOrCompilePipeline")
    private MetalPipeline eviemetal$compile(RenderPipeline pipeline, Operation<MetalPipeline> original) {
        if (eviemetal$bypassCache) ((MetalDevice) (Object) this).invalidatePipeline(pipeline);
        try { return original.call(pipeline); }
        catch (MetalPipeline.CompilationException error) { return eviemetal$recover(pipeline, error); }
    }

    @Unique private MetalPipeline eviemetal$recover(RenderPipeline pipeline, MetalPipeline.CompilationException error) {
        if (eviemetal$recovering) throw error;
        // SUMR measures its text immediately, which can upload glyphs. Always queue diagnostics
        // so a lazy compilation failure inside setPipeline cannot upload during an open pass.
        Minecraft.getInstance().schedule(() -> ShaderReloadManager.showShaderErrorMessage(
                Component.literal("Metal shader error: " + pipeline.getLocation()),
                Component.literal(error.getCause().getMessage())));
        boolean previousBypass = eviemetal$bypassCache;
        eviemetal$recovering = true;
        eviemetal$bypassCache = true;
        var device = (MetalDevice) (Object) this;
        try {
            ShaderSource vanilla = ModConstants.getFallbackShaderSource();
            // A mod-owned stage has no vanilla replacement. Use a complete no-output pair instead of
            // falling through to remembered resource-pack sources or retaining a broken native program.
            if (vanilla.get(pipeline.getVertexShader(), ShaderType.VERTEX) != null
                    && vanilla.get(pipeline.getFragmentShader(), ShaderType.FRAGMENT) != null) {
                try { return (MetalPipeline) device.precompilePipeline(pipeline, vanilla); }
                catch (MetalPipeline.CompilationException fallbackError) { error.addSuppressed(fallbackError); }
            }
            try {
                return (MetalPipeline) device.precompilePipeline(pipeline, (id, type) -> type == ShaderType.VERTEX
                        ? "#version 330\nvoid main(){gl_Position=vec4(0.0,0.0,0.0,1.0);}"
                        : "#version 330\nout vec4 color;void main(){discard;}");
            } catch (RuntimeException | Error fallbackError) {
                error.addSuppressed(fallbackError);
                throw error;
            }
        } finally {
            eviemetal$bypassCache = previousBypass;
            eviemetal$recovering = false;
        }
    }
}

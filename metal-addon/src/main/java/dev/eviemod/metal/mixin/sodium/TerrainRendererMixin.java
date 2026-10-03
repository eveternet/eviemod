// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin.sodium;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuSampler;
import dev.eviemod.metal.MetalBootstrap;
import dev.eviemod.metal.compat.sodium.SodiumMetal;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlTexelBuffer;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.*;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShaderChunkRenderer.class, remap = false)
abstract class TerrainRendererMixin {
    @Shadow @Final protected ChunkVertexType vertexType;
    @Shadow protected GlProgram<ChunkShaderInterface> activeProgram;
    @Shadow protected abstract GlProgram<ChunkShaderInterface> compileProgram(ChunkShaderOptions options);

    @Inject(method = "begin", at = @At("HEAD"), cancellable = true)
    private void begin(TerrainRenderPass pass, FogParameters fog, GpuSampler sampler, GpuBufferSlice globals, GlTexelBuffer times, CallbackInfo ci) {
        if (!MetalBootstrap.isActive()) return;
        activeProgram = compileProgram(new ChunkShaderOptions(ChunkFogMode.SMOOTH, pass, vertexType));
        SodiumMetal.beginPass(pass.getTarget(), pass.getPipeline());
        try {
            activeProgram.bind();
            activeProgram.getInterface().setupState(pass, fog, sampler, globals, times);
        } catch (RuntimeException | Error failure) {
            SodiumMetal.endPass();
            throw failure;
        }
        ci.cancel();
    }

    @Inject(method = "end", at = @At("TAIL"))
    private void end(TerrainRenderPass pass, CallbackInfo ci) {
        if (MetalBootstrap.isActive()) SodiumMetal.endPass();
    }
}

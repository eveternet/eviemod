// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin.sodium;

import java.util.Map;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import dev.eviemod.metal.MetalBootstrap;
import dev.eviemod.metal.compat.sodium.SodiumMetal;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlTexelBuffer;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformBlock;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformInt;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.*;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultShaderInterface.class, remap = false)
abstract class TerrainShaderInterfaceMixin {
    @Shadow @Final private Map<ChunkShaderTextureSlot, GlUniformInt> uniformTextures;
    @Shadow @Final private GlUniformBlock uniformGlobals;
    @Inject(method = "setupState", at = @At("HEAD"), cancellable = true)
    private void setup(TerrainRenderPass pass, FogParameters fog, GpuSampler sampler, GpuBufferSlice globals, GlTexelBuffer times, CallbackInfo ci) {
        if (!MetalBootstrap.isActive()) return;
        SodiumMetal.bindTexture(ChunkShaderTextureSlot.BLOCK.ordinal(), pass.getAtlas(), sampler);
        SodiumMetal.bindTexture(ChunkShaderTextureSlot.LIGHT.ordinal(), Minecraft.getInstance().gameRenderer.lightmap(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        SodiumMetal.bindTimeView(ChunkShaderTextureSlot.SECTION.ordinal(), times.handle());
        for (var entry : uniformTextures.entrySet()) entry.getValue().setInt(entry.getKey().ordinal());
        uniformGlobals.bindBufferRange(globals);
        ci.cancel();
    }
}

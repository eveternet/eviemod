// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin.sodium;

import dev.eviemod.metal.MetalBootstrap;
import dev.eviemod.metal.compat.sodium.SodiumMetal;
import net.caffeinemc.mods.sodium.client.gl.GlObject;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlTexelBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GlTexelBuffer.class, remap = false)
abstract class TerrainTimeViewMixin extends GlObject {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_genTexture()I"), cancellable = true)
    private void create(int buffer, int format, CallbackInfo ci) {
        if (!MetalBootstrap.isActive()) return;
        if (format != org.lwjgl.opengl.GL46C.GL_R32I) throw new IllegalArgumentException("Unsupported Sodium terrain texel format");
        setHandle(SodiumMetal.createTimeView(SodiumMetal.buffer(buffer)));
        ci.cancel();
    }
    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    private void destroy(CallbackInfo ci) {
        if (!MetalBootstrap.isActive()) return;
        SodiumMetal.deleteTimeView(handle());
        invalidateHandle();
        ci.cancel();
    }
}

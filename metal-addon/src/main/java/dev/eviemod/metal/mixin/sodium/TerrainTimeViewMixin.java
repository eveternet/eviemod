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
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_genTexture()I"))
    private int create(int buffer, int format) {
        if (!MetalBootstrap.isActive()) return com.mojang.blaze3d.opengl.GlStateManager._genTexture();
        if (format != org.lwjgl.opengl.GL46C.GL_R32I) throw new IllegalArgumentException("Unsupported Sodium terrain texel format");
        return SodiumMetal.createTimeView(SodiumMetal.buffer(buffer));
    }
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL46C;glBindTexture(II)V"))
    private void bind(int target, int texture) {
        if (!MetalBootstrap.isActive()) org.lwjgl.opengl.GL46C.glBindTexture(target, texture);
        // Metal texture views bind explicitly at draw submission.
    }
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL46C;glTexBuffer(III)V"))
    private void attach(int target, int format, int buffer) {
        if (!MetalBootstrap.isActive()) org.lwjgl.opengl.GL46C.glTexBuffer(target, format, buffer);
        // create() already created the independently retained view over this buffer.
    }
    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    private void destroy(CallbackInfo ci) {
        if (!MetalBootstrap.isActive()) return;
        SodiumMetal.deleteTimeView(handle());
        invalidateHandle();
        ci.cancel();
    }
}

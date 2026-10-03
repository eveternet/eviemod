// SPDX-License-Identifier: GPL-3.0-only
// Adapted from MetalCraft a2cc827 shader/object boundary.
package dev.eviemod.metal.mixin.sodium;

import dev.eviemod.metal.compat.sodium.SodiumMetal;
import dev.eviemod.metal.MetalBootstrap;
import net.caffeinemc.mods.sodium.client.gl.shader.GlShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Shaders only record their source; translation to MSL happens at link time, when both stages are known. */
@Mixin(value = GlShader.class, remap = false)
abstract class GlShaderMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20C;glCreateShader(I)I"))
    private int eviemetal$create(int type) {
        if (!MetalBootstrap.isActive()) { return org.lwjgl.opengl.GL20C.glCreateShader(type); }
        return SodiumMetal.createShader(type);
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderWorkarounds;safeShaderSource(ILjava/lang/CharSequence;)V"))
    private void eviemetal$source(int shader, CharSequence source) {
        if (!MetalBootstrap.isActive()) { org.lwjgl.opengl.GL20C.glShaderSource(shader, source); return; }
        SodiumMetal.shaderSource(shader, source);
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20C;glCompileShader(I)V"))
    private void eviemetal$compile(int shader) {
        if (!MetalBootstrap.isActive()) { org.lwjgl.opengl.GL20C.glCompileShader(shader); return; }}

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20C;glGetShaderInfoLog(I)Ljava/lang/String;"))
    private String eviemetal$log(int shader) {
        if (!MetalBootstrap.isActive()) { return org.lwjgl.opengl.GL20C.glGetShaderInfoLog(shader); }
        return "";
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;glGetShaderi(II)I"))
    private int eviemetal$status(int shader, int pname) {
        if (!MetalBootstrap.isActive()) { return com.mojang.blaze3d.opengl.GlStateManager.glGetShaderi(shader, pname); }
        return 1;
    }

    @Redirect(method = "delete", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20C;glDeleteShader(I)V"))
    private void eviemetal$delete(int shader) {
        if (!MetalBootstrap.isActive()) { org.lwjgl.opengl.GL20C.glDeleteShader(shader); return; }
        SodiumMetal.deleteShader(shader);
    }
}

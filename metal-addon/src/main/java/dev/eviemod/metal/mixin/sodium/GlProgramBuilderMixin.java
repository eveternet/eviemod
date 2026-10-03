// SPDX-License-Identifier: GPL-3.0-only
// Adapted from MetalCraft a2cc827 shader/object boundary.
package dev.eviemod.metal.mixin.sodium;

import dev.eviemod.metal.compat.sodium.SodiumMetal;
import dev.eviemod.metal.MetalBootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.GlProgram$Builder", remap = false)
abstract class GlProgramBuilderMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20C;glCreateProgram()I"))
    private int eviemetal$create() {
        if (!MetalBootstrap.isActive()) { return org.lwjgl.opengl.GL20C.glCreateProgram(); }
        return SodiumMetal.createProgram();
    }

    @Redirect(method = "attachShader", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20C;glAttachShader(II)V"))
    private void eviemetal$attach(int program, int shader) {
        if (!MetalBootstrap.isActive()) { org.lwjgl.opengl.GL20C.glAttachShader(program, shader); return; }
        SodiumMetal.attachShader(program, shader);
    }

    @Redirect(method = "bindAttribute", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20C;glBindAttribLocation(IILjava/lang/CharSequence;)V"))
    private void eviemetal$bindAttribute(int program, int index, CharSequence name) {
        if (!MetalBootstrap.isActive()) { org.lwjgl.opengl.GL20C.glBindAttribLocation(program, index, name); return; }
        SodiumMetal.bindAttribLocation(program, index, name);
    }

    @Redirect(method = "bindFragmentData", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL30C;glBindFragDataLocation(IILjava/lang/CharSequence;)V"))
    private void eviemetal$bindFragData(int program, int index, CharSequence name) {
        if (!MetalBootstrap.isActive()) { org.lwjgl.opengl.GL30C.glBindFragDataLocation(program, index, name); return; }}

    @Redirect(method = "link", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20C;glLinkProgram(I)V"))
    private void eviemetal$link(int program) {
        if (!MetalBootstrap.isActive()) { org.lwjgl.opengl.GL20C.glLinkProgram(program); return; }
        SodiumMetal.link(program);
    }

    @Redirect(method = "link", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20C;glGetProgramInfoLog(I)Ljava/lang/String;"))
    private String eviemetal$log(int program) {
        if (!MetalBootstrap.isActive()) { return org.lwjgl.opengl.GL20C.glGetProgramInfoLog(program); }
        return SodiumMetal.programLog(program);
    }

    @Redirect(method = "link", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;glGetProgrami(II)I"))
    private int eviemetal$status(int program, int pname) {
        if (!MetalBootstrap.isActive()) { return com.mojang.blaze3d.opengl.GlStateManager.glGetProgrami(program, pname); }
        return SodiumMetal.linkStatus(program);
    }
}

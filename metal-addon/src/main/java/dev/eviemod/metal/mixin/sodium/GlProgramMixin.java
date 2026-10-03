// SPDX-License-Identifier: GPL-3.0-only
// Adapted from MetalCraft a2cc827 shader/object boundary.
package dev.eviemod.metal.mixin.sodium;

import dev.eviemod.metal.compat.sodium.SodiumMetal;
import dev.eviemod.metal.MetalBootstrap;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GlProgram.class, remap = false)
abstract class GlProgramMixin {
    @Redirect(method = {"bind", "unbind"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20C;glUseProgram(I)V"))
    private void eviemetal$use(int program) {
        if (!MetalBootstrap.isActive()) { org.lwjgl.opengl.GL20C.glUseProgram(program); return; }
        SodiumMetal.useProgram(program);
    }

    @Redirect(method = "delete", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20C;glDeleteProgram(I)V"))
    private void eviemetal$delete(int program) {
        if (!MetalBootstrap.isActive()) { org.lwjgl.opengl.GL20C.glDeleteProgram(program); return; }
        SodiumMetal.deleteProgram(program);
    }

    @Redirect(method = {"bindUniform", "bindUniformOptional"},
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20C;glGetUniformLocation(ILjava/lang/CharSequence;)I"))
    private int eviemetal$uniformLocation(int program, CharSequence name) {
        if (!MetalBootstrap.isActive()) { return org.lwjgl.opengl.GL20C.glGetUniformLocation(program, name); }
        return SodiumMetal.uniformLocation(program, name);
    }

    @Redirect(method = {"bindUniformBlock", "bindUniformBlockOptional"},
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32C;glGetUniformBlockIndex(ILjava/lang/CharSequence;)I"))
    private int eviemetal$blockIndex(int program, CharSequence name) {
        if (!MetalBootstrap.isActive()) { return org.lwjgl.opengl.GL32C.glGetUniformBlockIndex(program, name); }
        return SodiumMetal.uniformBlockIndex(program, name);
    }

    @Redirect(method = {"bindUniformBlock", "bindUniformBlockOptional"},
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32C;glUniformBlockBinding(III)V"))
    private void eviemetal$blockBinding(int program, int index, int binding) {
        if (!MetalBootstrap.isActive()) { org.lwjgl.opengl.GL32C.glUniformBlockBinding(program, index, binding); return; }
        SodiumMetal.uniformBlockBinding(program, index, binding);
    }
}

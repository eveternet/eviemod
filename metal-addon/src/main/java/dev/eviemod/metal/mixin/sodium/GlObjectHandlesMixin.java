// SPDX-License-Identifier: GPL-3.0-only
// Adapted from MetalCraft a2cc827 shader/object boundary.
package dev.eviemod.metal.mixin.sodium;

import dev.eviemod.metal.compat.sodium.SodiumMetal;
import dev.eviemod.metal.MetalBootstrap;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GlBuffer.class, remap = false)
abstract class GlObjectHandlesMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20C;glGenBuffers()I"))
    private int eviemetal$genBuffer() {
        if (!MetalBootstrap.isActive()) { return org.lwjgl.opengl.GL20C.glGenBuffers(); }
        return SodiumMetal.genBuffer();
    }
}

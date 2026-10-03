// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin.sodium;
import dev.eviemod.metal.MetalBootstrap;
import dev.eviemod.metal.compat.sodium.SodiumMetal;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

final class TerrainUniformsMixin {
    @Mixin(value = GlUniformInt.class, remap = false)
    abstract static class Int {
        @Redirect(method="setInt", at=@At(value="INVOKE", target="Lorg/lwjgl/opengl/GL30C;glUniform1i(II)V"))
        private void set(int index, int value) {
            if (MetalBootstrap.isActive()) SodiumMetal.uniformInt(index, value);
            else org.lwjgl.opengl.GL30C.glUniform1i(index, value);
        }
    }
    @Mixin(value = GlUniformUnsignedInt.class, remap = false)
    abstract static class UInt {
        @Redirect(method="setInt", at=@At(value="INVOKE", target="Lorg/lwjgl/opengl/GL30C;glUniform1ui(II)V"))
        private void set(int index, int value) {
            if (MetalBootstrap.isActive()) SodiumMetal.uniformInt(index, value);
            else org.lwjgl.opengl.GL30C.glUniform1ui(index, value);
        }
    }
    @Mixin(value = GlUniformFloat3v.class, remap = false)
    abstract static class Float3 {
        @Redirect(method="set", at=@At(value="INVOKE", target="Lorg/lwjgl/opengl/GL30C;glUniform3f(IFFF)V"))
        private void set(int index, float x, float y, float z) {
            if (MetalBootstrap.isActive()) SodiumMetal.uniformFloats(index, x, y, z);
            else org.lwjgl.opengl.GL30C.glUniform3f(index, x, y, z);
        }
        @Redirect(method="set", at=@At(value="INVOKE", target="Lorg/lwjgl/opengl/GL30C;glUniform3fv(I[F)V"))
        private void set(int index, float[] values) {
            if (MetalBootstrap.isActive()) SodiumMetal.uniformFloats(index, values);
            else org.lwjgl.opengl.GL30C.glUniform3fv(index, values);
        }
    }
    @Mixin(value = GlUniformBlock.class, remap = false)
    abstract static class Block {
        @Shadow @Final private int binding;
        @Inject(method="bindBufferRange", at=@At("HEAD"), cancellable=true)
        private void bind(GpuBufferSlice slice, CallbackInfo ci) {
            if (!MetalBootstrap.isActive()) return;
            SodiumMetal.bindUniformRange(binding, slice);
            ci.cancel();
        }
    }
}

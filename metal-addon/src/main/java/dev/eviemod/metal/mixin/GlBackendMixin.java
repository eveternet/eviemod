// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin;

import com.mojang.blaze3d.opengl.GlBackend;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.GpuDevice;
import dev.eviemod.metal.MetalBootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Version boundary: Minecraft 26.1.2 device creation, before RenderSystem installs resources. */
@Mixin(GlBackend.class)
abstract class GlBackendMixin {
    @Inject(method = "createDevice", at = @At("HEAD"), cancellable = true)
    private void eviemod$metal(long window, ShaderSource shaders, GpuDebugOptions debug,
                              CallbackInfoReturnable<GpuDevice> cir) {
        GpuDevice device = MetalBootstrap.tryCreate(window, shaders);
        if (device != null) cir.setReturnValue(device);
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin.sodium;

import dev.eviemod.metal.MetalBootstrap;
import dev.eviemod.metal.compat.sodium.SodiumRenderDevice;
import net.caffeinemc.mods.sodium.client.gl.device.*;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.MappedStagingBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

final class TerrainDeviceMixin {
    @Mixin(value = RenderDevice.class, remap = false)
    interface Device {
        @Redirect(method = "<clinit>", at = @At(value = "NEW", target = "net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice"))
        private static RenderDevice create() {
            return MetalBootstrap.isActive() ? new SodiumRenderDevice() : new GLRenderDevice();
        }
    }
    @Mixin(value = MappedStagingBuffer.class, remap = false)
    abstract static class Staging {
        @Inject(method = "isSupported", at = @At("HEAD"), cancellable = true)
        private static void supported(RenderDevice device, CallbackInfoReturnable<Boolean> ci) {
            if (device instanceof SodiumRenderDevice) ci.setReturnValue(true);
        }
    }
}

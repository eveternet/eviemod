// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin.sodium;

import dev.eviemod.metal.compat.sodium.SodiumMetal;
import dev.eviemod.metal.device.MetalDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MetalDevice.class, remap = false)
abstract class MetalDeviceCloseMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void closeTerrain(CallbackInfo ci) { SodiumMetal.close(); }
}

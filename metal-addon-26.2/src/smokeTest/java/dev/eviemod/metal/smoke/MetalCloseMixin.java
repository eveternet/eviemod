// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.smoke;

import dev.eviemod.metal.device.MetalDevice;
import dev.eviemod.metal.mtl.Mtl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fixture-only proof that a successfully rendered client reaches native device teardown. */
@Mixin(MetalDevice.class)
abstract class MetalCloseMixin {
    @Inject(method = "close", at = @At("RETURN"))
    private void verifyNativeShutdown(CallbackInfo ci) {
        if (Mtl.allocatedBytes() != 0 || Mtl.completedFence() != 0 || Mtl.hasLiveContext())
            throw new AssertionError("Metal native state survived device close");
        System.out.println("EVIEMETAL_DEVICE_CLOSED_OK");
    }
}

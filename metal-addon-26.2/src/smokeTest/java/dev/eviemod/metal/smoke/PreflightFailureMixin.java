// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.smoke;
import dev.eviemod.metal.device.MetalDevice;
import dev.eviemod.metal.fixture.PreflightProbe;
import dev.eviemod.metal.mtl.Mtl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Fault injection after shader/native preflight and before Cocoa attachment. Never shipped. */
@Mixin(MetalDevice.class)
abstract class PreflightFailureMixin {
    @Inject(method="prepareSurface",at=@At("HEAD"))
    private void fail(long window,CallbackInfo ci) {
        if (!Boolean.getBoolean("eviemod.metal.fixturePreflightFailure")) return;
        if (Mtl.deviceName().equals("none")) throw new AssertionError("Preflight did not initialize Metal");
        PreflightProbe.failed=true;
        throw new IllegalStateException("Intentional fixture preflight failure before Cocoa attachment");
    }
    @Inject(method="close",at=@At("RETURN"))
    private void closed(CallbackInfo ci) {
        if (!PreflightProbe.failed && !Boolean.getBoolean("eviemod.metal.fixturePreflightFailure")) return;
        if (Mtl.allocatedBytes() != 0 || Mtl.completedFence() != 0) throw new AssertionError("Native device survived failed startup cleanup");
        PreflightProbe.cleaned=true;
        System.out.println("EVIEMETAL_PREFLIGHT_CLEANUP_OK injected=" + PreflightProbe.failed);
    }
}

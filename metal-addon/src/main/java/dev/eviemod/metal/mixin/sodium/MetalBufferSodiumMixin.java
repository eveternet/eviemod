// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin.sodium;

import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.eviemod.metal.compat.sodium.SodiumMetal;
import dev.eviemod.metal.device.MetalBuffer;
import net.caffeinemc.mods.sodium.mixin.core.GlBufferAccessor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only Sodium's section-time constructor uses this borrowed identity; it never owns the MetalBuffer. */
@Mixin(value = MetalBuffer.class, remap = false)
abstract class MetalBufferSodiumMixin implements GlBufferAccessor {
    @Unique private int eviemetal$borrowed;
    @Override public int sodium$getHandle() {
        if (eviemetal$borrowed == 0) eviemetal$borrowed = SodiumMetal.borrowBuffer((GpuBuffer) (Object) this);
        return eviemetal$borrowed;
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void forget(CallbackInfo ci) {
        if (eviemetal$borrowed != 0) SodiumMetal.forgetBorrowedBuffer(eviemetal$borrowed);
        eviemetal$borrowed = 0;
    }
}

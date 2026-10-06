// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.smoke;

import dev.eviemod.metal.fixture.SodiumStress;

import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.eviemod.metal.device.MetalDevice;
import java.nio.ByteBuffer;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-only ownership probe; labels checked against the pinned Sodium and DynamicUniformStorage sources. */
@Mixin(MetalDevice.class)
abstract class SodiumBufferOwnershipMixin {
    @Inject(method = "createBuffer(Ljava/util/function/Supplier;IJ)Lcom/mojang/blaze3d/buffers/GpuBuffer;", at = @At("RETURN"))
    private void allocation(Supplier<String> label, int usage, long size, CallbackInfoReturnable<GpuBuffer> ci) {
        SodiumStress.track(label, ci.getReturnValue());
    }
    @Inject(method = "createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;", at = @At("RETURN"))
    private void upload(Supplier<String> label, int usage, ByteBuffer data, CallbackInfoReturnable<GpuBuffer> ci) {
        SodiumStress.track(label, ci.getReturnValue());
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin.sodium;
import dev.eviemod.metal.MetalBootstrap;
import dev.eviemod.metal.compat.sodium.MetalDrawContext;
import net.caffeinemc.mods.sodium.client.gpu.device.context.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Replace the exact factory that otherwise selects GLDrawContext for every non-Vulkan device. */
@Mixin(value = DrawContext.class, remap = false)
abstract class DrawContextMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static void createMetalContext(CallbackInfoReturnable<DrawContext> ci) {
        if (MetalBootstrap.isActive()) ci.setReturnValue(new MetalDrawContext());
    }
}

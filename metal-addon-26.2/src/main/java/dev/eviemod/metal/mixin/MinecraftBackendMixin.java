// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.systems.GpuBackend;
import dev.eviemod.metal.MetalBootstrap;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Keep the exact candidate order produced by 26.2's selected graphics preference. */
@Mixin(Minecraft.class)
abstract class MinecraftBackendMixin {
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/PreferredGraphicsApi;getBackendsToTry()[Lcom/mojang/blaze3d/systems/GpuBackend;"))
    private GpuBackend[] eviemod$backends(GpuBackend[] defaults) { return MetalBootstrap.selectBackends(defaults); }
}

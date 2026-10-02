package dev.eviemod.metal.smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class FallbackLaunchMixin {
    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void verifyFallback(CallbackInfo ci) {
        if (!RenderSystem.getDevice().getBackendName().equals("OpenGL")) {
            throw new AssertionError("Disabled or unsupported Metal addon must preserve OpenGL");
        }
        System.out.println("EVIEMOD_METAL_FALLBACK_OK");
        ci.cancel();
    }
}

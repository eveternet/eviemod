package dev.eviemod.paintbrush.mixin;

import dev.eviemod.paintbrush.HelmetSkins;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class HelmetSkinRenderMixin {
    // Only change the detached render state. PROFILE and equipment on the entity stay untouched.
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void paintbrush$skin(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        HelmetSkins.applyWorn(entity, state);
    }
}

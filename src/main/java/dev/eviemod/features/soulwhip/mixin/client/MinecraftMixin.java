package dev.eviemod.features.soulwhip.mixin.client;

import dev.eviemod.features.soulwhip.EquipAnimationAccess;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Unique private boolean eviemod$emptyAirAttack;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void eviemod$beginAttack(CallbackInfoReturnable<Boolean> cir) {
        eviemod$emptyAirAttack = false;
    }

    // This call exists only in vanilla's MISS / now-air block branch, before the normal swing.
    @Inject(method = "startAttack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V"))
    private void eviemod$markEmptyAirAttack(CallbackInfoReturnable<Boolean> cir) {
        eviemod$emptyAirAttack = true;
    }

    @Inject(method = "startAttack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void eviemod$recordAttack(CallbackInfoReturnable<Boolean> cir) {
        var client = (Minecraft) (Object) this;
        dev.eviemod.compat.ClientUi.equipAnimations(client).eviemod$attack(eviemod$emptyAirAttack);
    }
}

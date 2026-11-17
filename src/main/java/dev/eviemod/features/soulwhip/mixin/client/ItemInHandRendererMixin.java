package dev.eviemod.features.soulwhip.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.eviemod.features.skyblock.FeatureSettings;
import dev.eviemod.features.soulwhip.EquipAnimationAccess;
import dev.eviemod.features.soulwhip.EquipAnimationPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.ItemInHandRenderer")
abstract class ItemInHandRendererMixin implements EquipAnimationAccess {
   @Shadow
   private ItemStack mainHandItem;
   @Shadow
   @Final
   private Minecraft minecraft;
   @Unique
   private int soulwhipfix$selectedSlot = -1;
   @Unique
   private boolean soulwhipfix$slotChangeInProgress;
   @Unique
   private final EquipAnimationPolicy eviemod$equipPolicy = new EquipAnimationPolicy();

   @Override
   public void eviemod$attack(boolean emptyAir) {
      var player = this.minecraft.player;
      if (player == null) this.eviemod$equipPolicy.clear();
      else this.eviemod$equipPolicy.attack(FeatureSettings.areUnintendedEquipAnimationsDisabled(), emptyAir,
         player, player.getInventory().getSelectedSlot(), player.getMainHandItem());
   }

   @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
   private float eviemod$preventEmptyAirEquipAnimation(float original) {
      var player = this.minecraft.player;
      if (player == null) return original;
      return this.eviemod$equipPolicy.itemSwapScale(original, FeatureSettings.areUnintendedEquipAnimationsDisabled(),
         player, player.getInventory().getSelectedSlot(), player.getMainHandItem(), this.mainHandItem,
         this.soulwhipfix$slotChangeInProgress);
   }

   @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
   private void soulwhipfix$preventUseEquipAnimation(InteractionHand hand, CallbackInfo ci) {
      if (FeatureSettings.areUnintendedEquipAnimationsDisabled() && hand == InteractionHand.MAIN_HAND) {
         ci.cancel();
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void soulwhipfix$preventRepeatedEquipAnimation(CallbackInfo ci) {
      if (!FeatureSettings.areUnintendedEquipAnimationsDisabled() || this.minecraft.player == null) {
         this.soulwhipfix$selectedSlot = -1;
         this.soulwhipfix$slotChangeInProgress = false;
         this.eviemod$equipPolicy.clear();
      } else {
         int currentSlot = this.minecraft.player.getInventory().getSelectedSlot();
         if (currentSlot != this.soulwhipfix$selectedSlot) {
            this.soulwhipfix$selectedSlot = currentSlot;
            this.soulwhipfix$slotChangeInProgress = true;
         } else if (!this.soulwhipfix$slotChangeInProgress) {
            ItemStack heldItem = this.minecraft.player.getMainHandItem();
            if (EquipAnimationPolicy.sameHeldItem(this.mainHandItem, heldItem)) {
               this.mainHandItem = heldItem;
            }
         }
      }
   }

   @Inject(method = "tick", at = @At("TAIL"))
   private void soulwhipfix$finishRealSlotChange(CallbackInfo ci) {
      if (this.soulwhipfix$slotChangeInProgress && this.minecraft.player != null && this.mainHandItem == this.minecraft.player.getMainHandItem()) {
         this.soulwhipfix$slotChangeInProgress = false;
      }
   }
}

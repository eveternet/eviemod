package dev.eviemod.features.soulwhip.mixin.client;

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
abstract class ItemInHandRendererMixin {
   @Shadow
   private ItemStack mainHandItem;
   @Shadow
   @Final
   private Minecraft minecraft;
   @Unique
   private int soulwhipfix$selectedSlot = -1;
   @Unique
   private boolean soulwhipfix$slotChangeInProgress;

   @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
   private void soulwhipfix$preventUseEquipAnimation(InteractionHand hand, CallbackInfo ci) {
      if (dev.eviemod.features.skyblock.FeatureSettings.isSoulWhipFixEnabled() && hand == InteractionHand.MAIN_HAND) {
         ci.cancel();
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void soulwhipfix$preventRepeatedEquipAnimation(CallbackInfo ci) {
      if (!dev.eviemod.features.skyblock.FeatureSettings.isSoulWhipFixEnabled() || this.minecraft.player == null) {
         this.soulwhipfix$selectedSlot = -1;
         this.soulwhipfix$slotChangeInProgress = false;
      } else {
         int currentSlot = this.minecraft.player.getInventory().getSelectedSlot();
         if (currentSlot != this.soulwhipfix$selectedSlot) {
            this.soulwhipfix$selectedSlot = currentSlot;
            this.soulwhipfix$slotChangeInProgress = true;
         } else if (!this.soulwhipfix$slotChangeInProgress) {
            ItemStack heldItem = this.minecraft.player.getMainHandItem();
            if (this.mainHandItem.getItem() == heldItem.getItem()) {
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

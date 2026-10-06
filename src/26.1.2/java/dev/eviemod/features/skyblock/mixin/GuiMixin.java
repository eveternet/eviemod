package dev.eviemod.features.skyblock.mixin;

import dev.eviemod.features.skyblock.VisualHealthController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
   @Shadow
   private int lastHealth;
   @Shadow
   private int displayHealth;

   @Inject(method = "extractPlayerHealth", at = @At("HEAD"))
   private void skyblockvisuals$clampCachedHealthBlink(GuiGraphicsExtractor extractor, CallbackInfo ci) {
      Player player = Minecraft.getInstance().player;
      if (player != null) {
         float currentHealth = player.getHealth();
         this.lastHealth = VisualHealthController.clampHudHealthCache(this.lastHealth, currentHealth);
         this.displayHealth = VisualHealthController.clampHudHealthCache(this.displayHealth, currentHealth);
      }
   }

   @Redirect(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getHealth()F"))
   private float skyblockvisuals$getVisualHealthForHud(Player player) {
      return VisualHealthController.getHudHealth(player.getHealth());
   }

   @Redirect(
      method = "extractPlayerHealth",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D")
   )
   private double skyblockvisuals$getVisualMaxHealthForHud(Player player, Holder<Attribute> attribute) {
      double value = player.getAttributeValue(attribute);
      return attribute.is(Attributes.MAX_HEALTH) ? VisualHealthController.getHudMaxHealth(value) : value;
   }
}

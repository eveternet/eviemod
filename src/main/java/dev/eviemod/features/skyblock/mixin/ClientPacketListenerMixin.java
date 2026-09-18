package dev.eviemod.features.skyblock.mixin;

import dev.eviemod.features.skyblock.PartyCommandController;
import dev.eviemod.features.skyblock.VisualHealthController;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
   @ModifyArg(method = "handleSetHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hurtTo(F)V"), index = 0)
   private float skyblockvisuals$scaleHealthForVisualBar(float health) {
      return VisualHealthController.toVisualHealth(health);
   }

   @Inject(method = "handleUpdateAttributes", at = @At("TAIL"))
   private void skyblockvisuals$rememberAndNormalizeMaxHealth(ClientboundUpdateAttributesPacket packet, CallbackInfo ci) {
      VisualHealthController.onAttributesApplied(packet);
   }

   @Inject(method = "handleSetTime", at = @At("TAIL"))
   private void skyblockvisuals$sampleServerTps(ClientboundSetTimePacket packet, CallbackInfo ci) {
      PartyCommandController.onSetTimePacket();
   }

   @Inject(method = "handlePongResponse", at = @At("TAIL"))
   private void skyblockvisuals$sampleServerPing(ClientboundPongResponsePacket packet, CallbackInfo ci) {
      PartyCommandController.onPongResponse(packet.time());
   }
}

package dev.eviemod.features.skyblock.mixin;

import dev.eviemod.features.compass.WishingCompassClient;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe only manual uses that reach vanilla's send boundary after Fabric callbacks. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class CompassUsePacketMixin {
    @Inject(method = "send", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void eviemod$observeSentCompassUse(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ServerboundUseItemPacket use) WishingCompassClient.sentUse(use.getHand());
        else if (packet instanceof ServerboundUseItemOnPacket use) WishingCompassClient.sentUse(use.getHand());
    }
}

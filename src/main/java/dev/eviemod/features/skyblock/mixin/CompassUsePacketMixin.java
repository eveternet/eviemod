package dev.eviemod.features.skyblock.mixin;

import dev.eviemod.features.compass.WishingCompassClient;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe only manual uses that reach vanilla's send boundary after Fabric callbacks. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class CompassUsePacketMixin {
    @Inject(method = "send", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;)V", shift = At.Shift.AFTER))
    private void eviemod$observeSentCompassUse(Packet<?> packet, CallbackInfo ci) {
        WishingCompassClient.sentUse(packet);
    }
}

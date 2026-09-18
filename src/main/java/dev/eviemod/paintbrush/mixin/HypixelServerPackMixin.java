package dev.eviemod.paintbrush.mixin;

import dev.eviemod.paintbrush.TexturePackBypasser;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.1.2 adapter, shared by configuration and play listeners. Vanilla keeps all unknown packs. */
@Mixin(ClientCommonPacketListenerImpl.class)
abstract class HypixelServerPackMixin {
    @Shadow @Final protected ServerData serverData;
    @Shadow @Final protected Connection connection;

    @Inject(method = "handleResourcePackPush", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
        shift = At.Shift.AFTER), cancellable = true)
    private void eviemod$localHypixelPack(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        if (!TexturePackBypasser.shouldBypass(serverData == null ? null : serverData.ip, packet.url(), packet.hash())) return;
        connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED));
        connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.DOWNLOADED));
        connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
        ci.cancel();
    }
}

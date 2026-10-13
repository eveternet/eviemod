package dev.eviemod.paintbrush.mixin;

import dev.eviemod.features.experiments.ExperimentClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ExperimentPacketMixin {
    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void eviemod$experimentSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) { ExperimentClient.observeServer(packet.getContainerId()); }
    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void eviemod$experimentContents(ClientboundContainerSetContentPacket packet, CallbackInfo ci) { ExperimentClient.observeServer(packet.containerId()); }
}

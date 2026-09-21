package dev.eviemod.features.dungeons;

import dev.eviemod.features.skyblock.PartyCommandController;
import dev.eviemod.paintbrush.EviemodSettings;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Observes unsigned system chat without cancelling or rewriting the original message. */
public final class AnnounceCritClient {
    public static void initialize() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            var client = Minecraft.getInstance();
            if (client.player == null) return;
            var settings = EviemodSettings.features().dungeons;
            AnnounceCrit.receive(message.getString(), overlay, settings.announceCrit, settings.announceCritTemplate,
                settings.announceCritPartyChat,
                text -> client.gui.getChat().addClientSystemMessage(Component.literal(text)),
                PartyCommandController::sendCommand);
        });
    }

    private AnnounceCritClient() {}
}

package dev.eviemod.features.dungeons;

import dev.eviemod.features.skyblock.SkyblockContext;
import dev.eviemod.paintbrush.EviemodSettings;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Observes system chat without cancelling or rewriting the original message. */
public final class PartyFinderAlertClient {
    public static void initialize() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            var client = Minecraft.getInstance();
            if (client.player == null || !SkyblockContext.isHypixel()) return;
            var settings = EviemodSettings.features().dungeons;
            PartyFinderAlert.receive(message.getString(), overlay, settings.partyFinderAlert,
                settings.partyFinderSubtitle, text -> {
                    client.gui.setTimes(0, 60, 10);
                    client.gui.setSubtitle(Component.literal(text));
                    // Vanilla only starts the title/subtitle timer when a title is set.
                    client.gui.setTitle(Component.empty());
                });
        });
    }

    private PartyFinderAlertClient() {}
}

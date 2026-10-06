package dev.eviemod.features.dungeons;

import dev.eviemod.compat.ClientUi;
import dev.eviemod.features.skyblock.SkyblockContext;
import dev.eviemod.paintbrush.EviemodSettings;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;

/** Observes system chat without cancelling or rewriting the original message. */
public final class PartyFinderAlertClient {
    public static void initialize() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            var client = Minecraft.getInstance();
            if (client.player == null || !SkyblockContext.isHypixel()) return;
            var settings = EviemodSettings.features().dungeons;
            PartyFinderAlert.receive(message.getString(), overlay, settings.partyFinderAlert,
                settings.partyFinderSubtitle, settings.partyFinderSound, settings.partyFinderSoundId,
                settings.partyFinderSoundVolume, settings.partyFinderSoundPitch, text -> {
                    ClientUi.setTimes(client, 0, 60, 10);
                    ClientUi.setSubtitle(client, Component.literal(text));
                    // Vanilla only starts the title/subtitle timer when a title is set.
                    ClientUi.setTitle(client, Component.empty());
                }, sound -> {
                    var id = Identifier.tryParse(sound.id());
                    if (id == null) return;
                    BuiltInRegistries.SOUND_EVENT.getOptional(id).ifPresent(event ->
                        client.getSoundManager().play(SimpleSoundInstance.forUI(event, sound.pitch(), sound.volume())));
                });
        });
    }

    private PartyFinderAlertClient() {}
}

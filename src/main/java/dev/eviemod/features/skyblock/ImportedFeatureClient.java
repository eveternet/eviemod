package dev.eviemod.features.skyblock;

import dev.eviemod.features.garden.GardenToolsClient;
import dev.eviemod.paintbrush.EviemodSettings;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class ImportedFeatureClient {
    private static boolean openSettings;
    public static void initialize() {
        dev.eviemod.features.dungeons.AnnounceCritClient.initialize();
        GardenToolsClient.initialize();
        CustomCommandHotkeys.initialize();
        PartyCommandController.register();
        dev.eviemod.features.scoresync.ScoreSyncClient.initialize();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
            dispatcher.register(ClientCommands.literal("kabeewie")
                .executes(context -> openSettings())
                .then(ClientCommands.literal("settings").executes(context -> openSettings()))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openSettings) { openSettings = false; client.setScreen(EviemodSettings.screen(null)); }
            SkyblockContext.tick(client);
            VisualHealthController.tick(client);
            CustomCommandHotkeys.tick(client);
        });
    }
    private static int openSettings() { openSettings = true; return 1; }
}

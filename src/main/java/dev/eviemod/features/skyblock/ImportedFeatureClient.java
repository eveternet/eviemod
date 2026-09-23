package dev.eviemod.features.skyblock;

import dev.eviemod.features.garden.GardenToolsClient;
import dev.eviemod.paintbrush.PaintBrushClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class ImportedFeatureClient {
    public static void initialize() {
        dev.eviemod.features.dungeons.AnnounceCritClient.initialize();
        GardenToolsClient.initialize();
        CustomCommandHotkeys.initialize();
        PartyCommandController.register();
        dev.eviemod.features.scoresync.ScoreSyncClient.initialize();
        // Legacy aliases remain for macros; /eviemod is the canonical settings command.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
            dispatcher.register(ClientCommands.literal("kabeewie")
                .executes(context -> deprecatedSettings(context.getSource(), "/eviemod"))
                .then(ClientCommands.literal("settings").executes(context -> deprecatedSettings(context.getSource(), "/eviemod settings")))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SkyblockContext.tick(client);
            VisualHealthController.tick(client);
            CustomCommandHotkeys.tick(client);
        });
    }
    private static int deprecatedSettings(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, String replacement) {
        source.sendFeedback(net.minecraft.network.chat.Component.literal("/kabeewie is deprecated. Use " + replacement + " instead."));
        PaintBrushClient.requestSettings();
        return 1;
    }
}

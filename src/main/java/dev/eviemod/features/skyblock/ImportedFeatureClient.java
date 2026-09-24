package dev.eviemod.features.skyblock;

import dev.eviemod.features.garden.GardenToolsClient;
import dev.eviemod.paintbrush.PaintBrushClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

public final class ImportedFeatureClient {
    public static void initialize() {
        dev.eviemod.features.dungeons.AnnounceCritClient.initialize();
        GardenToolsClient.initialize();
        CustomCommandHotkeys.initialize();
        PartyCommandController.register();
        dev.eviemod.features.scoresync.ScoreSyncClient.initialize();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
            dispatcher.register(ClientCommands.literal("kabeewie")
                .executes(context -> legacySettings(context.getSource()))
                .then(ClientCommands.literal("settings").executes(context -> legacySettings(context.getSource())))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SkyblockContext.tick(client);
            VisualHealthController.tick(client);
            CustomCommandHotkeys.tick(client);
        });
    }
    private static int legacySettings(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("Use /eviemod settings instead; /kabeewie is deprecated."));
        return PaintBrushClient.openSettings();
    }
}

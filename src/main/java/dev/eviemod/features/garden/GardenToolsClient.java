package dev.eviemod.features.garden;

import dev.eviemod.paintbrush.EviemodSettings;
import dev.eviemod.paintbrush.PaintBrushClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class GardenToolsClient {
   public static final String MOD_ID = "gardentools";
   private static boolean mouseLocked;
   private static boolean inGarden;

   public static void initialize() {
      GardenKeyMappings.initialize();
      ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
         dispatcher.register(ClientCommands.literal("gardentools").executes(context -> {
            context.getSource().sendFeedback(Component.literal("Use /eviemod settings instead; /gardentools is deprecated."));
            return PaintBrushClient.openSettings();
         })));
      ClientTickEvents.START_CLIENT_TICK.register(client -> {
         inGarden = GardenDetector.isInGarden(client);
         GardenCommandKeys.tick(client, inGarden);
      });
      ClientTickEvents.END_CLIENT_TICK.register(client -> mouseLocked = shouldLockMouse(client));
   }

   private static boolean shouldLockMouse(Minecraft client) {
      return EviemodSettings.features().garden.mouseLock
          && client.screen == null
          && client.player != null
          && client.player.onGround()
          && inGarden
          && FarmingToolDetector.isFarmingTool(client.player.getMainHandItem());
   }

   public static boolean isMouseLocked() {
      return mouseLocked;
   }
}

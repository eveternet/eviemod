package dev.eviemod.features.garden;

import dev.eviemod.paintbrush.EviemodSettings;
import dev.eviemod.paintbrush.PaintBrushClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class GardenToolsClient {
   public static final String MOD_ID = "gardentools";
   private static boolean mouseLocked;

   public static void initialize() {
      GardenKeyMappings.initialize();
      // Legacy alias remains for macros; /eviemod settings owns the screen.
      ClientCommandRegistrationCallback.EVENT
         .register(
            (dispatcher, registryAccess) -> dispatcher.register(
               ClientCommands.literal("gardentools").executes(context -> {
                  context.getSource().sendFeedback(Component.literal("/gardentools is deprecated. Use /eviemod settings instead."));
                  PaintBrushClient.requestSettings();
                  return 1;
               })
            )
         );
      ClientTickEvents.START_CLIENT_TICK.register((StartTick)client -> {
         GardenCommandKeys.tick(client);
      });
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> mouseLocked = shouldLockMouse(client));
   }

   private static boolean shouldLockMouse(Minecraft client) {
      return EviemodSettings.features().garden.mouseLock
         && GardenDetector.isInGarden(client)
         && client.screen == null
         && client.player != null
         && client.player.onGround()
         && FarmingToolDetector.isFarmingTool(client.player.getMainHandItem());
   }

   public static boolean isMouseLocked() {
      return mouseLocked;
   }
}

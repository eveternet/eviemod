package dev.eviemod.features.garden;

import dev.eviemod.paintbrush.EviemodSettings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class GardenCommandKeys {
   private GardenCommandKeys() {
   }

   public static void tick(Minecraft client) {
      boolean active = GardenDetector.isInGarden(client) && client.player != null && client.screen == null;
      if (!active) {
         drain(GardenKeyMappings.TELEPORT_TO_PLOT);
         drain(GardenKeyMappings.SET_SPAWN);
         drain(GardenKeyMappings.WARP_GARDEN);
      } else {
         while (GardenKeyMappings.TELEPORT_TO_PLOT.consumeClick()) {
            send(client, "tptoplot " + EviemodSettings.features().garden.teleportPlot);
         }

         while (GardenKeyMappings.SET_SPAWN.consumeClick()) {
            send(client, "setspawn");
         }

         while (GardenKeyMappings.WARP_GARDEN.consumeClick()) {
            send(client, "warp garden");
         }
      }
   }

   static void send(Minecraft client, String command) {
      if (client.getConnection() != null) {
         client.getConnection().sendCommand(command);
      }
   }

   static void drain(KeyMapping mapping) {
      while (mapping.consumeClick()) {
      }
   }
}

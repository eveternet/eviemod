package dev.eviemod.features.garden;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

public final class GardenDetector {
   private GardenDetector() {
   }

   public static boolean isInGarden(Minecraft client) {
      return client.level != null && client.getConnection() != null && client.getCurrentServer() != null
         ? client.getConnection()
            .getOnlinePlayers()
            .stream()
            .<Component>map(PlayerInfo::getTabListDisplayName)
            .filter(name -> name != null)
            .map(name -> name.getString())
            .map(GardenDetector::normalize)
            .anyMatch(line -> line.equals("area: garden"))
         : false;
   }

   private static String normalize(String value) {
      return value.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim().toLowerCase(Locale.ROOT);
   }
}

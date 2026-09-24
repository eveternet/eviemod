package dev.eviemod.features.garden;

import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

public final class GardenDetector {
   private static final Pattern FORMATTING = Pattern.compile("§[0-9A-FK-ORa-fk-or]");
   private GardenDetector() {
   }

   public static boolean isInGarden(Minecraft client) {
      if (client.level == null || client.getConnection() == null || client.getCurrentServer() == null) return false;
      for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
         Component name = info.getTabListDisplayName();
         if (name != null && normalize(name.getString()).equals("area: garden")) return true;
      }
      return false;
   }

   private static String normalize(String value) {
      return FORMATTING.matcher(value).replaceAll("").trim().toLowerCase(Locale.ROOT);
   }
}

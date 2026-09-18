package dev.eviemod.features.skyblock;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public final class SkyblockContext {
   private static final int SCOREBOARD_SCAN_INTERVAL_TICKS = 20;
   private static boolean hypixel;
   private static boolean skyblock;
   private static boolean rift;
   private static int ticksUntilScoreboardScan;

   private SkyblockContext() {
   }

   public static void tick(Minecraft client) {
      hypixel = isHypixelServer(client);
      if (hypixel && client.level != null) {
         if (ticksUntilScoreboardScan-- <= 0) {
            refreshScoreboardState(client);
            ticksUntilScoreboardScan = 20;
         }
      } else {
         skyblock = false;
         rift = false;
         ticksUntilScoreboardScan = 0;
      }
   }

   public static void refreshNow(Minecraft client) {
      hypixel = isHypixelServer(client);
      if (hypixel && client.level != null) {
         refreshScoreboardState(client);
         ticksUntilScoreboardScan = 20;
      } else {
         skyblock = false;
         rift = false;
         ticksUntilScoreboardScan = 0;
      }
   }

   public static boolean isActiveSkyblock() {
      return hypixel && skyblock;
   }

   public static boolean shouldNormalizeHealth() {
      return FeatureSettings.isMaxTenHeartsEnabled() && isActiveSkyblock() && !rift;
   }

   public static boolean shouldIgnoreWorldBorderRestrictions() {
      return FeatureSettings.isNoBarrierEffectsEnabled() && isActiveSkyblock();
   }

   public static boolean isHypixel() {
      return hypixel;
   }

   private static boolean isHypixelServer(Minecraft client) {
      ServerData server = client.getCurrentServer();
      if (server != null && server.ip != null) {
         String host = server.ip.trim().toLowerCase(Locale.ROOT);
         int portStart = host.indexOf(58);
         if (portStart >= 0) {
            host = host.substring(0, portStart);
         }

         while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
         }

         return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
      } else {
         return false;
      }
   }

   private static void refreshScoreboardState(Minecraft client) {
      Scoreboard scoreboard = client.level.getScoreboard();
      Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
      boolean foundSkyblock = false;
      boolean foundRift = false;
      if (sidebar != null) {
         foundSkyblock |= containsNormalized(sidebar.getDisplayName(), "SKYBLOCK");
         foundRift |= isRiftLine(sidebar.getDisplayName());

         for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            SkyblockContext.LineFlags flags = readScoreboardEntry(scoreboard, entry);
            foundSkyblock |= flags.skyblock;
            foundRift |= flags.rift;
         }
      }

      for (PlayerTeam team : scoreboard.getPlayerTeams()) {
         foundSkyblock |= containsNormalized(team.getDisplayName(), "SKYBLOCK")
            || containsNormalized(team.getPlayerPrefix(), "SKYBLOCK")
            || containsNormalized(team.getPlayerSuffix(), "SKYBLOCK");
         foundRift |= isRiftLine(team.getDisplayName()) || isRiftLine(team.getPlayerPrefix()) || isRiftLine(team.getPlayerSuffix());
      }

      skyblock = foundSkyblock;
      rift = foundRift;
   }

   private static SkyblockContext.LineFlags readScoreboardEntry(Scoreboard scoreboard, PlayerScoreEntry entry) {
      if (entry.display() != null) {
         return SkyblockContext.LineFlags.of(entry.display());
      }

      String owner = entry.owner();
      PlayerTeam team = scoreboard.getPlayersTeam(owner);
      boolean foundSkyblock = containsNormalized(owner, "SKYBLOCK");
      boolean foundRift = isRiftLine(owner);
      if (team != null) {
         foundSkyblock |= containsNormalized(team.getPlayerPrefix(), "SKYBLOCK") || containsNormalized(team.getPlayerSuffix(), "SKYBLOCK");
         foundRift |= isRiftLine(team.getPlayerPrefix()) || isRiftLine(team.getPlayerSuffix());
      }

      return new SkyblockContext.LineFlags(foundSkyblock, foundRift);
   }

   private static boolean containsNormalized(Component component, String needle) {
      return component != null && containsNormalized(component.getString(), needle);
   }

   private static boolean containsNormalized(String value, String needle) {
      return value != null && !value.isEmpty() ? stripFormatting(value).contains(needle) : false;
   }

   private static boolean isRiftLine(Component component) {
      return component != null && isRiftLine(component.getString());
   }

   private static boolean isRiftLine(String value) {
      String line = stripFormatting(value);
      return line.contains("THE RIFT")
         || line.contains("RIFT TIME")
         || line.contains("MOTES")
         || line.contains("WYLD WOODS")
         || line.contains("LAGOON")
         || line.contains("DREADFARM")
         || line.contains("STILLGORE")
         || line.contains("MIRRORVERSE")
         || line.contains("LIVING CAVE")
         || line.contains("WITHER CAGE");
   }

   private static String stripFormatting(String value) {
      StringBuilder builder = new StringBuilder(value.length());
      boolean formattingCode = false;

      for (int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);
         if (formattingCode) {
            formattingCode = false;
         } else if (c == 167) {
            formattingCode = true;
         } else {
            builder.append(Character.toUpperCase(c));
         }
      }

      return builder.toString();
   }

   private record LineFlags(boolean skyblock, boolean rift) {
      static SkyblockContext.LineFlags of(Component component) {
         return new SkyblockContext.LineFlags(SkyblockContext.containsNormalized(component, "SKYBLOCK"), SkyblockContext.isRiftLine(component));
      }
   }
}

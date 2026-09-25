package dev.eviemod.features.skyblock;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public final class SkyblockContext {
   private static boolean hypixel;
   private static boolean skyblock;
   private static boolean rift;
   private static int ticksUntilScoreboardScan;
   private static final Pattern HYPIXEL_HOST = Pattern.compile("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*hypixel\\.net\\.?");
   private static String checkedAddress;
   private static boolean checkedHypixel;

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

   /** Check the current connection, not a server-provided brand or location packet. */
   public static boolean isHypixelServer(Minecraft client) {
      if (client.getConnection() == null || client.isLocalServer()) return false;
      ServerData server = client.getCurrentServer();
      String address = server == null ? null : server.ip;
      // This is also used during rendering; normalize only when the address changes.
      if (!Objects.equals(address, checkedAddress)) {
         checkedAddress = address;
         checkedHypixel = isHypixelAddress(address);
      }
      return checkedHypixel;
   }

   public static boolean isHypixelAddress(String address) {
      if (address == null) return false;
      String host = address.trim().toLowerCase(Locale.ROOT);
      if (host.length() > 260) return false; // DNS name, optional root dot and port.
      int colon = host.indexOf(':');
      if (colon >= 0) {
         String port = host.substring(colon + 1);
         if (!port.matches("[0-9]{1,5}")) return false;
         int number = Integer.parseInt(port);
         if (number < 1 || number > 65535) return false;
         host = host.substring(0, colon);
      }
      return host.length() <= (host.endsWith(".") ? 254 : 253) && HYPIXEL_HOST.matcher(host).matches();
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

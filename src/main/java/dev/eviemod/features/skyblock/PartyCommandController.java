package dev.eviemod.features.skyblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.Game;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class PartyCommandController {
   private static final Pattern CHAT_PATTERN = Pattern.compile(
      "^(?:Party > (?:\\[[^]]*?])? ?(\\w{1,16})(?: [ቾ⚒])?: ?(.+)$|Guild > (?:\\[[^]]*?])? ?(\\w{1,16})(?: \\[[^]]*?])?: ?(.+)$|Co-op > (?:\\[[^]]*?])? ?(\\w{1,16}): ?(.+)$)"
   );
   private static final Pattern JOINED_SELF = Pattern.compile("^You have joined ((?:\\[[^]]*?])? ?)?(\\w{1,16})'s? party!$");
   private static final Pattern JOINED_OTHER = Pattern.compile("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) joined the party\\.$");
   private static final Pattern LEFT_PARTY = Pattern.compile("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) has left the party\\.$");
   private static final Pattern KICKED_PARTY = Pattern.compile("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) has been removed from the party\\.$");
   private static final Pattern KICKED_OFFLINE = Pattern.compile("^Kicked ((?:\\[[^]]*?])? ?)?(\\w{1,16}) because they were offline\\.$");
   private static final Pattern KICKED_DISCONNECTED = Pattern.compile(
      "^((?:\\[[^]]*?])? ?)?(\\w{1,16}) was removed from your party because they disconnected\\.$"
   );
   private static final Pattern TRANSFER_LEAVE = Pattern.compile(
      "^The party was transferred to ((?:\\[[^]]*?])? ?)?(\\w{1,16}) because ((?:\\[[^]]*?])? ?)?(\\w{1,16}) left$"
   );
   private static final Pattern TRANSFER_BY = Pattern.compile(
      "^The party was transferred to ((?:\\[[^]]*?])? ?)?(\\w{1,16}) by ((?:\\[[^]]*?])? ?)?(\\w{1,16})$"
   );
   private static final Pattern PARTY_CHAT = Pattern.compile("^Party > ((?:\\[[^]]*?])? ?)?(\\w{1,16}): (.+)$");
   private static final Pattern PARTY_INVITE = Pattern.compile(
      "^((?:\\[[^]]*?])? ?)?(\\w{1,16}) invited ((?:\\[[^]]*?])? ?)?(\\w{1,16}) to the party! They have 60 seconds to accept\\.$"
   );
   private static final Pattern LEADER_DISCONNECTED = Pattern.compile(
      "^The party leader, ((?:\\[[^]]*?])? ?)?(\\w{1,16}) has disconnected, they have 5 minutes to rejoin before the party is disbanded\\.$"
   );
   private static final Pattern LEADER_REJOINED = Pattern.compile("^The party leader ((?:\\[[^]]*?])? ?)?(\\w{1,16}) has rejoined\\.$");
   private static final Pattern MEMBERS_LIST = Pattern.compile("^Party (Leader|Moderators|Members): (.+)$");
   private static final Pattern MEMBER_FORMAT = Pattern.compile("^((?:\\[[^]]*?])? ?)?(\\w{1,16})$");
   private static final Pattern PARTY_WITH = Pattern.compile("^You'll be partying with: (.+)$");
   private static final Pattern KUUDRA_JOIN = Pattern.compile("^Party Finder > ((?:\\[[^]]*?])? ?)?(\\w{1,16}) joined the group! \\(Combat Level (\\d+)\\)$");
   private static final Pattern[] DISBAND_PATTERNS = new Pattern[]{
      Pattern.compile("^((?:\\[[^]]*?])? ?)?(\\w{1,16}) has disbanded the party!$"),
      Pattern.compile("^You have been kicked from the party by ((?:\\[[^]]*?])? ?)?(\\w{1,16})$"),
      Pattern.compile("^The party was disbanded because all invites expired and the party was empty\\.$"),
      Pattern.compile("^The party was disbanded because the party leader disconnected\\.$"),
      Pattern.compile("^You left the party\\.$"),
      Pattern.compile("^You are not currently in a party\\.$")
   };
   public static final PartyCommandController.CommandFeature[] FEATURES = new PartyCommandController.CommandFeature[]{
      new PartyCommandController.CommandFeature("warp", "Warp", "!w / !warp -> /p warp", true),
      new PartyCommandController.CommandFeature("coords", "Coordinates", "!coords -> sends your coordinates", false),
      new PartyCommandController.CommandFeature("invite", "Invite", "!inv [ign] -> /p invite [ign]", false),
      new PartyCommandController.CommandFeature("kick", "Kick", "!k / !kick [ign] -> /p kick [ign], sender when blank", true),
      new PartyCommandController.CommandFeature("transfer", "Party Transfer", "!pt [ign] -> /party transfer [ign], sender when blank", true),
      new PartyCommandController.CommandFeature("ping", "Ping", "!ping -> sends your current ping", false),
      new PartyCommandController.CommandFeature("tps", "TPS", "!tps -> sends estimated server TPS", false),
      new PartyCommandController.CommandFeature("fps", "FPS", "!fps -> sends your current FPS", false)
   };
   private static final List<String> members = new ArrayList<>();
   private static String partyLeader;
   private static long previousTimePacketMillis;
   private static float averageTps = 20.0F;
   private static int currentPing = -1;

   private PartyCommandController() {
   }

   public static void register() {
      ClientReceiveMessageEvents.GAME.register((Game)(message, overlay) -> handleChatLine(message));
   }

   public static void onSetTimePacket() {
      long now = System.currentTimeMillis();
      if (previousTimePacketMillis != 0L) {
         averageTps = clamp(20000.0F / (float)(now - previousTimePacketMillis + 1L), 0.0F, 20.0F);
      }

      previousTimePacketMillis = now;
   }

   public static void onPongResponse(long sentTime) {
      currentPing = Math.max(0, (int)(Util.getMillis() - sentTime));
   }

   public static int currentPingMs() {
      return currentPing();
   }

   public static String findPartyMember(String partialName) {
      for (String member : members) {
         if (member.toLowerCase(Locale.ROOT).contains(partialName.toLowerCase(Locale.ROOT))) {
            return member;
         }
      }

      return partialName;
   }

   private static void handleChatLine(Component component) {
      String line = stripControlCodes(component.getString());
      updatePartyState(line);
      Matcher matcher = CHAT_PATTERN.matcher(line);
      if (matcher.matches()) {
         PartyCommandController.CommandChannel channel;
         String sender;
         String message;
         if (matcher.group(1) != null) {
            channel = PartyCommandController.CommandChannel.PARTY;
            sender = matcher.group(1);
            message = matcher.group(2);
         } else if (matcher.group(3) != null) {
            channel = PartyCommandController.CommandChannel.GUILD;
            sender = matcher.group(3);
            message = matcher.group(4);
         } else {
            channel = PartyCommandController.CommandChannel.COOP;
            sender = matcher.group(5);
            message = matcher.group(6);
         }

         if (message.startsWith("!")) {
            handleCommand(message.substring(1), sender, channel);
         }
      }
   }

   private static void handleCommand(String rawCommand, String sender, PartyCommandController.CommandChannel channel) {
      String[] parts = rawCommand.trim().split("\\s+");
      if (parts.length != 0 && !parts[0].isEmpty()) {
         String command = parts[0].toLowerCase(Locale.ROOT);
         switch (command) {
            case "w":
            case "warp":
               executeLeaderCommand("warp", channel, "party warp");
               break;
            case "coords":
            case "coord":
            case "co":
               if (isEnabled("coords", channel)) {
                  channelMessage(channel, sender, coordinates());
               }
               break;
            case "inv":
            case "invite":
               if (isEnabled("invite", channel)) {
                  String target = parts.length >= 2 ? parts[1] : sender;
                  sendCommand("party invite " + target);
               }
               break;
            case "k":
            case "kick":
               if (isEnabled("kick", channel) && isPartyLeader()) {
                  String target = parts.length >= 2 ? findPartyMember(parts[1]) : sender;
                  sendCommand("party kick " + target);
               }
               break;
            case "pt":
            case "transfer":
               if (isEnabled("transfer", channel) && isPartyLeader()) {
                  String target = parts.length >= 2 ? findPartyMember(parts[1]) : sender;
                  sendCommand("party transfer " + target);
               }
               break;
            case "ping":
               if (isEnabled("ping", channel)) {
                  channelMessage(channel, sender, "Current Ping: " + currentPing() + "ms");
               }
               break;
            case "tps":
               if (isEnabled("tps", channel)) {
                  channelMessage(channel, sender, String.format(Locale.ROOT, "Current TPS: %.1f", averageTps));
               }
               break;
            case "fps":
               if (isEnabled("fps", channel)) {
                  channelMessage(channel, sender, "Current FPS: " + Minecraft.getInstance().getFps());
               }
         }
      }
   }

   private static void executeLeaderCommand(String key, PartyCommandController.CommandChannel channel, String command) {
      if (isEnabled(key, channel) && isPartyLeader()) {
         sendCommand(command);
      }
   }

   private static boolean isEnabled(String key, PartyCommandController.CommandChannel channel) {
      return FeatureSettings.isPartyCommandEnabled(key, channel);
   }

   private static boolean isPartyLeader() {
      LocalPlayer player = Minecraft.getInstance().player;
      return player != null && partyLeader != null && partyLeader.equalsIgnoreCase(player.getGameProfile().name());
   }

   private static String coordinates() {
      LocalPlayer player = Minecraft.getInstance().player;
      return player == null ? "Coordinates unavailable." : "x: " + player.getBlockX() + ", y: " + player.getBlockY() + ", z: " + player.getBlockZ();
   }

   private static int currentPing() {
      if (currentPing >= 0) {
         return currentPing;
      } else {
         Minecraft client = Minecraft.getInstance();
         ClientPacketListener connection = client.getConnection();
         if (connection != null && client.player != null) {
            PlayerInfo info = connection.getPlayerInfo(client.player.getUUID());
            return info == null ? 0 : Math.max(0, info.getLatency());
         } else {
            return 0;
         }
      }
   }

   private static void channelMessage(PartyCommandController.CommandChannel channel, String sender, String message) {
      switch (channel) {
         case PARTY:
            sendCommand("pc " + message);
            break;
         case GUILD:
            sendCommand("gc " + message);
            break;
         case COOP:
            sendCommand("cc " + message);
      }
   }

   public static void sendCommand(String command) {
      Minecraft client = Minecraft.getInstance();
      if (client.getConnection() != null) {
         client.getConnection().sendCommand(command);
      }
   }

   private static void updatePartyState(String line) {
      Matcher matcher;
      if ((matcher = JOINED_OTHER.matcher(line)).matches()) {
         addMember(matcher.group(2));
      } else if ((matcher = JOINED_SELF.matcher(line)).matches()) {
         addMember(matcher.group(2));
         partyLeader = matcher.group(2);
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            addMember(player.getGameProfile().name());
         }
      } else if ((matcher = LEFT_PARTY.matcher(line)).matches()
         || (matcher = KICKED_PARTY.matcher(line)).matches()
         || (matcher = KICKED_OFFLINE.matcher(line)).matches()
         || (matcher = KICKED_DISCONNECTED.matcher(line)).matches()) {
         removeMember(matcher.group(2));
      } else if ((matcher = TRANSFER_BY.matcher(line)).matches()) {
         addMember(matcher.group(2));
         addMember(matcher.group(4));
         partyLeader = matcher.group(2);
      } else if ((matcher = TRANSFER_LEAVE.matcher(line)).matches()) {
         addMember(matcher.group(2));
         partyLeader = matcher.group(2);
         removeMember(matcher.group(4));
      } else if ((matcher = LEADER_DISCONNECTED.matcher(line)).matches() || (matcher = LEADER_REJOINED.matcher(line)).matches()) {
         partyLeader = matcher.group(2);
      } else if ((matcher = PARTY_CHAT.matcher(line)).matches()) {
         addMember(matcher.group(2));
      } else if ((matcher = PARTY_INVITE.matcher(line)).matches()) {
         addMember(matcher.group(2));
         if (partyLeader == null) {
            partyLeader = matcher.group(2);
         }
      } else if ((matcher = KUUDRA_JOIN.matcher(line)).matches()) {
         addMember(matcher.group(2));

      } else {
         for (Pattern pattern : DISBAND_PATTERNS) {
            if (pattern.matcher(line).find()) {
               disband();
               return;
            }
         }

         if ((matcher = MEMBERS_LIST.matcher(line)).matches()) {
            String type = matcher.group(1);

            for (String segment : matcher.group(2).split(" ●")) {
               Matcher member = MEMBER_FORMAT.matcher(segment.trim());
               if (member.matches()) {
                  addMember(member.group(2));
                  if ("Leader".equals(type)) {
                     partyLeader = member.group(2);
                  }
               }
            }
         } else {
            if ((matcher = PARTY_WITH.matcher(line)).matches()) {
               for (String playerName : matcher.group(1).split(", ")) {
                  Matcher member = MEMBER_FORMAT.matcher(playerName.trim());
                  if (member.matches()) {
                     addMember(member.group(2));
                  }
               }
            }
         }
      }
   }

   private static void addMember(String playerName) {
      for (String member : members) {
         if (member.equalsIgnoreCase(playerName)) {
            return;
         }
      }

      members.add(playerName);
   }

   private static void removeMember(String playerName) {
      members.removeIf(member -> member.equalsIgnoreCase(playerName));
      if (members.isEmpty()) {
         disband();
      }
   }

   private static void disband() {
      members.clear();
      partyLeader = null;
   }

   private static String stripControlCodes(String value) {
      StringBuilder builder = new StringBuilder(value.length());
      boolean controlCode = false;

      for (int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);
         if (controlCode) {
            controlCode = false;
         } else if (c == 167) {
            controlCode = true;
         } else {
            builder.append(c);
         }
      }

      return builder.toString();
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }

   public enum CommandChannel {
      PARTY("Party"),
      GUILD("Guild"),
      COOP("Co-op");

      public final String displayName;

      CommandChannel(String displayName) {
         this.displayName = displayName;
      }
   }

   public record CommandFeature(String key, String name, String description, boolean leaderOnly) {
   }
}

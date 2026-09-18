package dev.eviemod.features.garden;

import dev.eviemod.paintbrush.EviemodSettings;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.Chat;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.Game;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

public final class PestWorkflow {
   private static final Pattern PEST_SPAWN = Pattern.compile("YUCK!.*PESTS?.*SPAWNED IN PLOT", 2);
   private static final Pattern COOLDOWN = Pattern.compile("COOLDOWN:\\s*(?:READY|(?:(\\d+)M\\s*)?(\\d+)S)", 2);
   private static final long NORMAL_COOLDOWN_NANOS = 135000000000L;
   private static final long FINNEGAN_COOLDOWN_NANOS = 75000000000L;
   private static final long WARNING_NANOS = 5000000000L;
   private static PestWorkflow.State state = PestWorkflow.State.WAITING_FOR_PEST;
   private static long cooldownDeadline;
   private static boolean cooldownLoadoutUsed;
   private static boolean showingPersistentWarning;

   private PestWorkflow() {
   }

   public static void initialize() {
      ClientReceiveMessageEvents.GAME.register((Game)(message, overlay) -> onGameMessage(message));
      ClientReceiveMessageEvents.CHAT.register((Chat)(message, signedMessage, sender, parameters, receivedAt) -> onGameMessage(message));
   }

   private static void onGameMessage(Component message) {
      Minecraft client = Minecraft.getInstance();
      if (GardenDetector.isInGarden(client)) {
         if (PEST_SPAWN.matcher(message.getString()).find()) {
            long duration = EviemodSettings.features().garden.forceFinnegan ? 75000000000L : 135000000000L;
            cooldownDeadline = System.nanoTime() + duration;
            state = PestWorkflow.State.PEST_LOADOUT_ARMED;
            cooldownLoadoutUsed = false;
            showingPersistentWarning = false;
            showSubtitle(client, Component.literal("Pest spawned").withStyle(ChatFormatting.YELLOW));
         }
      }
   }

   public static void tick(Minecraft client) {
      if (GardenDetector.isInGarden(client) && client.player != null) {
         if ((state == PestWorkflow.State.PEST_LOADOUT_ARMED || state == PestWorkflow.State.COOLDOWN) && System.nanoTime() >= cooldownDeadline - 5000000000L) {
            state = PestWorkflow.State.COOLDOWN_LOADOUT_ARMED;
            showingPersistentWarning = true;
         }

         boolean armed = state == PestWorkflow.State.PEST_LOADOUT_ARMED || state == PestWorkflow.State.COOLDOWN_LOADOUT_ARMED;
         if (armed) {
            suppressConflictingMappings(client);
         }

         boolean pressed = GardenKeyMappings.LOADOUTS.consumeClick();
         if (!armed) {
            GardenCommandKeys.drain(GardenKeyMappings.LOADOUTS);
         } else if (pressed && client.screen == null) {
            GardenCommandKeys.send(client, "loadouts");
            if (state == PestWorkflow.State.PEST_LOADOUT_ARMED) {
               state = PestWorkflow.State.COOLDOWN;
            } else {
               cooldownLoadoutUsed = true;
            }
         }

         if (state == PestWorkflow.State.COOLDOWN_LOADOUT_ARMED) {
            showSubtitle(client, Component.literal("pest cooldown over soon").withStyle(ChatFormatting.RED));
            showingPersistentWarning = true;
            if (cooldownLoadoutUsed && tabCooldownAtMostFiveSeconds(client)) {
               state = PestWorkflow.State.WAITING_FOR_PEST;
               showingPersistentWarning = false;
               client.gui.clearTitles();
            }
         }
      } else {
         reset(client);
      }
   }

   private static void suppressConflictingMappings(Minecraft client) {
      for (KeyMapping mapping : client.options.keyMappings) {
         if (mapping != GardenKeyMappings.LOADOUTS && mapping.same(GardenKeyMappings.LOADOUTS)) {
            mapping.setDown(false);
            GardenCommandKeys.drain(mapping);
         }
      }
   }

   private static boolean tabCooldownAtMostFiveSeconds(Minecraft client) {
      if (client.getConnection() == null) {
         return false;
      }

      for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
         Component displayName = info.getTabListDisplayName();
         if (displayName != null) {
            String line = displayName.getString().trim().toUpperCase(Locale.ROOT);
            Matcher matcher = COOLDOWN.matcher(line);
            if (matcher.find()) {
               if (matcher.group().contains("READY")) {
                  return true;
               }

               int minutes = matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1));
               int seconds = Integer.parseInt(matcher.group(2));
               return minutes == 0 && seconds <= 5;
            }
         }
      }

      return false;
   }

   private static void showSubtitle(Minecraft client, Component subtitle) {
      client.gui.setTitle(Component.empty());
      client.gui.setSubtitle(subtitle);
   }

   private static void reset(Minecraft client) {
      state = PestWorkflow.State.WAITING_FOR_PEST;
      cooldownLoadoutUsed = false;
      if (showingPersistentWarning) {
         client.gui.clearTitles();
      }

      showingPersistentWarning = false;
      GardenCommandKeys.drain(GardenKeyMappings.LOADOUTS);
   }

   private enum State {
      WAITING_FOR_PEST,
      PEST_LOADOUT_ARMED,
      COOLDOWN,
      COOLDOWN_LOADOUT_ARMED;
   }
}

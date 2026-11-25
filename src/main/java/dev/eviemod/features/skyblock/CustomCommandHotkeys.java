package dev.eviemod.features.skyblock;

import dev.eviemod.compat.ClientUi;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import dev.eviemod.compat.ClientInput;

public final class CustomCommandHotkeys {
   public static final int UNBOUND_KEY = -1;
   private static final List<HotkeyPressState> pressedStates = new ArrayList<>();

   private CustomCommandHotkeys() {
   }

   public static void initialize() {
      ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
         sampleKeys(client, false);
         // Capture keys typed after the last screen tick, before gameplay resumes.
         ScreenEvents.remove(screen).register(closed -> sampleKeys(client, false));
      });
   }

   public static void tick(Minecraft client) {
      sampleKeys(client, FeatureSettings.isCommandHotkeysEnabled() && ClientUi.screen(client) == null && client.getConnection() != null);
   }

   private static void sampleKeys(Minecraft client, boolean active) {
      syncPressedStateSize();
      for (int slot = 0; slot < FeatureSettings.getCommandHotkeyCount(); slot++) {
         int key = FeatureSettings.getCommandHotkeyKey(slot);
         boolean down = key != UNBOUND_KEY && ClientInput.isDown(client, key);
         if (pressedStates.get(slot).sample(key, down, active && FeatureSettings.isCommandHotkeyEnabled(slot))) execute(slot, client);
      }
   }

   private static void execute(int slot, Minecraft client) {
      String command = FeatureSettings.getCommandHotkeyCommand(slot).trim();
      if (!command.isEmpty()) {
         if (command.startsWith("/")) {
            command = command.substring(1);
         }

         if (!command.isEmpty()) {
            client.getConnection().sendCommand(command);
         }
      }
   }

   private static void syncPressedStateSize() {
      int count = FeatureSettings.getCommandHotkeyCount();

      while (pressedStates.size() < count) {
         pressedStates.add(new HotkeyPressState());
      }

      while (pressedStates.size() > count) {
         pressedStates.remove(pressedStates.size() - 1);
      }
   }
}

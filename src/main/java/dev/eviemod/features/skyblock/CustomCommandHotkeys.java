package dev.eviemod.features.skyblock;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import org.lwjgl.glfw.GLFW;

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
      sampleKeys(client, FeatureSettings.isCommandHotkeysEnabled() && client.screen == null && client.getConnection() != null);
   }

   private static void sampleKeys(Minecraft client, boolean active) {
      syncPressedStateSize();
      for (int slot = 0; slot < FeatureSettings.getCommandHotkeyCount(); slot++) {
         int key = FeatureSettings.getCommandHotkeyKey(slot);
         boolean down = key != UNBOUND_KEY && isDown(client, key);
         if (pressedStates.get(slot).sample(key, down, active)) execute(slot, client);
      }
   }

   public static String keyName(int key) {
      if (key == -1) {
         return "Unbound";
      }

      if (isMouseButton(key)) {
         return switch (key) {
            case 0 -> "Mouse 1";
            case 1 -> "Mouse 2";
            case 2 -> "Mouse 3";
            default -> "Mouse " + (key + 1);
         };
      } else {
         String name = GLFW.glfwGetKeyName(key, 0);
         if (name != null && !name.isBlank()) {
            return name.toUpperCase();
         }

         return switch (key) {
            case 32 -> "Space";
            case 257 -> "Enter";
            case 258 -> "Tab";
            case 259 -> "Backspace";
            case 261 -> "Delete";
            case 262 -> "Right";
            case 263 -> "Left";
            case 264 -> "Down";
            case 265 -> "Up";
            case 340 -> "LShift";
            case 341 -> "LCtrl";
            case 342 -> "LAlt";
            case 344 -> "RShift";
            case 345 -> "RCtrl";
            case 346 -> "RAlt";
            default -> key >= 290 && key <= 314 ? "F" + (key - 290 + 1) : "Key " + key;
         };
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

   private static boolean isDown(Minecraft client, int key) {
      return isMouseButton(key) ? GLFW.glfwGetMouseButton(client.getWindow().handle(), key) == 1 : InputConstants.isKeyDown(client.getWindow(), key);
   }

   private static boolean isMouseButton(int key) {
      return key >= 0 && key <= 7;
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

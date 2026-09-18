package dev.eviemod.features.garden;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;

public final class GardenKeyMappings {
   private static final Category CATEGORY = Category.register(Identifier.fromNamespaceAndPath("gardentools", "commands"));
   public static final KeyMapping TELEPORT_TO_PLOT = register("tptoplot");
   public static final KeyMapping SET_SPAWN = register("setspawn");
   public static final KeyMapping WARP_GARDEN = register("warp_garden");
   public static final KeyMapping LOADOUTS = register("loadouts");

   private GardenKeyMappings() {
   }

   public static void initialize() {
   }

   private static KeyMapping register(String name) {
      return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.gardentools." + name, Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
   }
}

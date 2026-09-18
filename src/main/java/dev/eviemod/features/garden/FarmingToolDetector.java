package dev.eviemod.features.garden;

import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class FarmingToolDetector {
   private static final Pattern THEORETICAL_HOE = Pattern.compile("(?:THEORETICAL|THEORATICAL)_HOE_[A-Z0-9_]+_[1-3]");
   private static final Pattern COCOA_CHOPPER = Pattern.compile("COCOA_CHOPPER(?:_[1-3])?");

   private FarmingToolDetector() {
   }

   public static boolean isFarmingTool(ItemStack stack) {
      String id = getSkyBlockId(stack).toUpperCase(Locale.ROOT);
      int namespaceSeparator = id.indexOf(58);
      if (namespaceSeparator >= 0) {
         id = id.substring(namespaceSeparator + 1);
      }

      return THEORETICAL_HOE.matcher(id).matches() || COCOA_CHOPPER.matcher(id).matches();
   }

   static String getSkyBlockId(ItemStack stack) {
      CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
      if (customData == null) {
         return "";
      }

      CompoundTag root = customData.copyTag();
      String directId = root.getStringOr("id", "");
      if (!directId.isEmpty()) {
         return directId;
      }

      CompoundTag extraAttributes = root.getCompoundOrEmpty("ExtraAttributes");
      return extraAttributes.getStringOr("id", "");
   }
}

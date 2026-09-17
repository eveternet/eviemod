package dev.eviemod.paintbrush;

import com.google.gson.JsonElement;
import net.minecraft.resources.Identifier;

public final class ItemAppearance {
    private ItemAppearance() {}
    public static boolean supportsCustomTexture(net.minecraft.world.item.ItemStack stack) {
        var equipped = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        return !stack.isEmpty() && !HelmetSkins.supports(stack) && (equipped == null || !equipped.slot().isArmor());
    }
    public static Identifier previewModel(Identifier original, String override) {
        // Identifier.tryParse("") accepts an empty path; it is not an absent override.
        if (override == null || override.isBlank()) return original;
        Identifier parsed = Identifier.tryParse(override);
        return parsed == null ? original : parsed;
    }
    /** Model IDs only imply equipment when they match a registered item's default model and slot. */
    public static net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> equipmentAsset(
            net.minecraft.world.item.ItemStack stack, Identifier model,
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> original) {
        if (model == null) return original;
        var source = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        if (source == null || !source.slot().isArmor()) return original;
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(model);
        if (item == null) return original;
        var target = item.getDefaultInstance();
        if (!model.equals(target.get(net.minecraft.core.component.DataComponents.ITEM_MODEL))) return original;
        var equipment = target.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        if (equipment == null || equipment.slot() != source.slot()) return original;
        return equipment.assetId().orElse(original);
    }

    public static boolean hasDyeTint(Identifier model) {
        var resource = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(
            Identifier.fromNamespaceAndPath(model.getNamespace(), "items/" + model.getPath() + ".json"));
        if (resource.isEmpty()) return false;
        try (var reader = resource.get().openAsReader()) {
            return hasDyeTint(com.google.gson.JsonParser.parseReader(reader));
        } catch (Exception e) { return false; }
    }

    public static boolean hasDyeTint(JsonElement element) {
        if (element.isJsonObject()) {
            var object = element.getAsJsonObject();
            var type = object.get("type");
            if (type != null && type.isJsonPrimitive() && type.getAsJsonPrimitive().isString()
                && (type.getAsString().equals("minecraft:dye") || type.getAsString().equals("dye"))) return true;
            return object.entrySet().stream().anyMatch(entry -> hasDyeTint(entry.getValue()));
        }
        if (element.isJsonArray()) for (var child : element.getAsJsonArray()) if (hasDyeTint(child)) return true;
        return false;
    }
}

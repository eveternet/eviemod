package dev.eviemod.paintbrush;

import java.util.EnumMap;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Remembers local armor identity across UUID-less updates without changing item data. */
final class EquipmentColorContinuity {
    private record Known(Item item, String skyblockId, UUID uuid) {}
    private final EnumMap<EquipmentSlot, Known> known = new EnumMap<>(EquipmentSlot.class);

    void clear() { known.clear(); }

    UUID observe(EquipmentSlot slot, ItemStack stack) {
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        if (stack.isEmpty() || !ColorOverrides.isLeatherArmor(stack) || custom == null || stack.getCount() != 1) {
            known.remove(slot); return null;
        }
        var tag = custom.copyTag();
        String id = tag.getStringOr("id", "");
        String rawUuid = tag.getStringOr("uuid", "");
        UUID uuid = SkyBlockUuid.read(stack);
        if (uuid != null) {
            known.put(slot, new Known(stack.getItem(), id, uuid));
            return uuid;
        }
        Known previous = known.get(slot);
        // Missing identity persists until positive evidence invalidates it. Malformed UUIDs are not missing.
        if (!rawUuid.isEmpty() || id.isBlank() || previous == null || previous.item() != stack.getItem()
            || !previous.skyblockId().equals(id)) {
            known.remove(slot); return null;
        }
        return previous.uuid();
    }

    int resolve(EquipmentSlot slot, ItemStack equipped, ItemStack rendered, ColorOverrides colors, int original) {
        UUID uuid = observe(slot, equipped);
        // Never infer ownership from an item's type alone, including detached render copies.
        if (rendered != equipped || SkyBlockUuid.read(rendered) != null) return original;
        Integer color = colors.get(uuid);
        return color == null ? original : 0xff000000 | color;
    }

    // Caller must establish local-player and slot ownership through the armor render state.
    int resolveOwnedCopy(EquipmentSlot slot, ItemStack equipped, ItemStack rendered, ColorOverrides colors, int original) {
        UUID uuid = observe(slot, equipped);
        if (uuid == null || rendered.isEmpty() || rendered.getItem() != equipped.getItem() || rendered.getCount() != 1
            || SkyBlockUuid.read(rendered) != null) return original;
        var data = rendered.get(DataComponents.CUSTOM_DATA);
        if (data == null) return original;
        var tag = data.copyTag();
        Known current = known.get(slot);
        if (!tag.getStringOr("uuid", "").isEmpty() || current == null
            || !current.skyblockId().equals(tag.getStringOr("id", ""))) return original;
        Integer color = colors.get(uuid);
        return color == null ? original : 0xff000000 | color;
    }
}

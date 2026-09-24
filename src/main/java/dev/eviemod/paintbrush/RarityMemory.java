package dev.eviemod.paintbrush;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Session-only identity memory. Never infer rarity globally from a SkyBlock ID. */
final class RarityMemory {
    private record Known(Item item, String id, UUID uuid, ItemRarity rarity) {
        boolean matches(ItemStack stack, String currentId) { return item == stack.getItem() && id.equals(currentId); }
    }
    private final Map<Integer, Known> slots = new HashMap<>();
    private final Map<UUID, Known> identities = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, Known> entry) { return size() > 2048; }
    };
    void clear() { slots.clear(); identities.clear(); }
    private static CompoundTag tag(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }
    private static String id(CompoundTag tag) {
        return tag == null ? "" : tag.getStringOr("id", "");
    }
    private static UUID uuid(CompoundTag tag) {
        return id(tag).isBlank() ? null : SkyBlockUuid.parse(tag.getStringOr("uuid", ""));
    }
    ItemRarity resolve(ItemStack stack) {
        var tag = tag(stack);
        return resolve(stack, ItemRarity.read(stack, tag), tag);
    }
    ItemRarity resolve(ItemStack stack, ItemRarity fresh) {
        return resolve(stack, fresh, tag(stack));
    }
    private ItemRarity resolve(ItemStack stack, ItemRarity fresh, CompoundTag tag) {
        UUID uuid = uuid(tag);
        String id = id(tag);
        if (uuid != null) {
            if (fresh != null) identities.put(uuid, new Known(stack.getItem(), id, uuid, fresh));
            else if (ItemRarity.missingMetadata(stack, tag)) {
                Known known = identities.get(uuid);
                if (known != null && known.matches(stack, id)) return known.rarity();
            } else identities.remove(uuid);
        }
        return fresh;
    }
    ItemRarity observe(int slot, ItemStack stack) {
        var tag = tag(stack);
        return observe(slot, stack, ItemRarity.read(stack, tag), tag);
    }
    ItemRarity observe(int slot, ItemStack stack, ItemRarity fresh) {
        return observe(slot, stack, fresh, tag(stack));
    }
    ItemRarity observe(int slot, ItemStack stack, ItemRarity fresh, CompoundTag tag) {
        ItemRarity rarity = resolve(stack, fresh, tag);
        UUID uuid = uuid(tag);
        String id = id(tag);
        Known previous = slots.get(slot);
        if (stack.isEmpty() || stack.getCount() != 1 || id.isBlank() || tag != null && tag.contains("uuid") && uuid == null) {
            slots.remove(slot); return rarity;
        }
        if (uuid != null) {
            if (rarity != null) slots.put(slot, new Known(stack.getItem(), id, uuid, rarity));
            else slots.remove(slot);
            return rarity;
        }
        if (previous != null && previous.matches(stack, id) && ItemRarity.missingMetadata(stack, tag)) return previous.rarity();
        slots.remove(slot);
        return rarity;
    }
    void refreshKnown(int slot) {
        Known known = slots.get(slot);
        if (known != null && known.uuid() != null) identities.put(known.uuid(), known);
    }
}

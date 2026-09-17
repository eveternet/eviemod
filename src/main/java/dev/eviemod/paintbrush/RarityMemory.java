package dev.eviemod.paintbrush;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Session-only identity memory. Never infer rarity globally from a SkyBlock ID. */
final class RarityMemory {
    private record Known(Item item, String id, UUID uuid, ItemRarity rarity) {
        boolean matches(ItemStack stack) { return item == stack.getItem() && id.equals(RarityMemory.id(stack)); }
    }
    private final Map<Integer, Known> slots = new HashMap<>();
    private final Map<UUID, Known> identities = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, Known> entry) { return size() > 2048; }
    };
    void clear() { slots.clear(); identities.clear(); }
    private static String id(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr("id", "");
    }
    private static boolean malformed(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains("uuid") && SkyBlockUuid.read(stack) == null;
    }
    ItemRarity resolve(ItemStack stack) {
        ItemRarity fresh = ItemRarity.read(stack);
        UUID uuid = SkyBlockUuid.read(stack);
        if (uuid != null) {
            if (fresh != null) identities.put(uuid, new Known(stack.getItem(), id(stack), uuid, fresh));
            else if (ItemRarity.missingMetadata(stack)) {
                Known known = identities.get(uuid);
                if (known != null && known.matches(stack)) return known.rarity();
            } else identities.remove(uuid);
        }
        return fresh;
    }
    ItemRarity observe(int slot, ItemStack stack) {
        ItemRarity rarity = resolve(stack);
        UUID uuid = SkyBlockUuid.read(stack);
        Known previous = slots.get(slot);
        if (stack.isEmpty() || stack.getCount() != 1 || id(stack).isBlank() || malformed(stack)) {
            slots.remove(slot); return rarity;
        }
        if (uuid != null) {
            if (rarity != null) slots.put(slot, new Known(stack.getItem(), id(stack), uuid, rarity));
            else slots.remove(slot);
            return rarity;
        }
        if (previous != null && previous.matches(stack) && ItemRarity.missingMetadata(stack)) return previous.rarity();
        slots.remove(slot);
        return rarity;
    }
}

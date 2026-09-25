package dev.eviemod.paintbrush;

import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/** Client-thread parsing cache, independent of the optional remembered-rarity feature. */
final class RarityCache {
    static final int MAX_ENTRIES = 256;
    record Entry(CustomData data, ItemLore lore, Identifier style, boolean empty,
                 CompoundTag tag, ItemRarity rarity) {}
    private final Reference2ObjectLinkedOpenHashMap<ItemStack, Entry> entries = new Reference2ObjectLinkedOpenHashMap<>();

    Entry get(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        var lore = stack.get(DataComponents.LORE);
        var style = stack.get(DataComponents.TOOLTIP_STYLE);
        boolean empty = stack.isEmpty();
        var previous = entries.getAndMoveToFirst(stack);
        // Minecraft component values are immutable: supported updates replace the value.
        // Compare references, never hash/serialize/deep-compare the NBT or petInfo text.
        if (previous != null && previous.data() == data && previous.lore() == lore
            && previous.style() == style && previous.empty() == empty) return previous;
        var tag = data == null ? null : data.copyTag();
        var entry = new Entry(data, lore, style, empty, tag, ItemRarity.read(stack, tag));
        // Keep null/invalid results too; they must not be reparsed every frame.
        entries.putAndMoveToFirst(stack, entry);
        if (entries.size() > MAX_ENTRIES) entries.removeLast();
        return entry;
    }

    void clear() { entries.clear(); }
}

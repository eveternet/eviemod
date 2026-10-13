package dev.eviemod.features.experiments;

import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Server packet copies only; the live menu also contains unacknowledged client predictions. */
final class ExperimentInventory {
    private final ItemStack[] slots = new ItemStack[54];
    ExperimentInventory() { clear(); }
    void clear() { Arrays.fill(slots, ItemStack.EMPTY); }
    ItemStack get(int slot) { return slots[slot]; }
    boolean setSlot(int slot, ItemStack item) {
        if (slot < 0 || slot >= slots.length) return false;
        slots[slot] = item.copy(); return true;
    }
    boolean setContents(List<ItemStack> items) {
        if (items.size() < slots.length) { clear(); return false; }
        for (int i = 0; i < slots.length; i++) slots[i] = items.get(i).copy();
        return true;
    }
}

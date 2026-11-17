package dev.eviemod.features.soulwhip;

import dev.eviemod.paintbrush.SkyBlockUuid;
import net.minecraft.world.item.ItemStack;

/** Render-only state for the cooldown dip belonging to an actual empty-air attack. */
public final class EquipAnimationPolicy {
    private Object attackPlayer;
    private int attackSlot = -1;
    private ItemStack attackItem = ItemStack.EMPTY;

    public void attack(boolean enabled, boolean emptyAir, Object player, int slot, ItemStack item) {
        clear();
        if (enabled && emptyAir && player != null) {
            attackPlayer = player;
            attackSlot = slot;
            attackItem = item;
        }
    }

    public void clear() {
        attackPlayer = null;
        attackSlot = -1;
        attackItem = ItemStack.EMPTY;
    }

    public float itemSwapScale(float vanilla, boolean enabled, Object player, int slot,
                               ItemStack held, ItemStack rendered, boolean changingSlot) {
        if (!enabled || player == null || player != attackPlayer || slot != attackSlot
            || changingSlot || !sameHeldItem(attackItem, held) || vanilla >= 1.0F) {
            clear();
            return vanilla;
        }
        return rendered == held ? 1.0F : vanilla;
    }

    /** UUID continuity allows Soul Whip metadata updates, never a different physical item. */
    public static boolean sameHeldItem(ItemStack before, ItemStack after) {
        if (before == after) return true;
        if (before.isEmpty() || after.isEmpty() || before.getItem() != after.getItem()) return false;
        var beforeId = SkyBlockUuid.read(before);
        var afterId = SkyBlockUuid.read(after);
        if (beforeId != null || afterId != null) return beforeId != null && beforeId.equals(afterId);
        return ItemStack.isSameItemSameComponents(before, after);
    }
}

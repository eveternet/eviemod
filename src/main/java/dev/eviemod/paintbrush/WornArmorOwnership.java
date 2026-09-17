package dev.eviemod.paintbrush;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Resolves ownership from the render state, never from a matching item type alone. */
public final class WornArmorOwnership {
    private WornArmorOwnership() {}
    public static EquipmentSlot localSlot(Object state, ItemStack stack, int localPlayerId) {
        if (!(state instanceof AvatarRenderState avatar) || avatar.id != localPlayerId || stack.isEmpty()) return null;
        if (stack == avatar.feetEquipment) return EquipmentSlot.FEET;
        if (stack == avatar.legsEquipment) return EquipmentSlot.LEGS;
        if (stack == avatar.chestEquipment) return EquipmentSlot.CHEST;
        if (stack == avatar.headEquipment) return EquipmentSlot.HEAD;
        return null;
    }
}

package dev.eviemod.paintbrush;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WornArmorOwnershipTest {
    @BeforeAll static void bootstrap() { ModelOverridesTest.bootstrap(); }
    @Test void acceptsOnlyCopiesInTheLocalPlayersActualRenderState() {
        var state = new AvatarRenderState(); state.id = 123;
        state.feetEquipment = new ItemStack(Items.LEATHER_BOOTS);
        assertEquals(EquipmentSlot.FEET, WornArmorOwnership.localSlot(state, state.feetEquipment, 123));
        assertNull(WornArmorOwnership.localSlot(state, state.feetEquipment, 456));
        assertNull(WornArmorOwnership.localSlot(state, state.feetEquipment.copy(), 123));
        assertNull(WornArmorOwnership.localSlot(new HumanoidRenderState(), state.feetEquipment, 123));
        assertNull(WornArmorOwnership.localSlot(state, ItemStack.EMPTY, 123));
        state.chestEquipment = new ItemStack(Items.LEATHER_CHESTPLATE);
        assertEquals(EquipmentSlot.CHEST, WornArmorOwnership.localSlot(state, state.chestEquipment, 123));
    }
}

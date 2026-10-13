package dev.eviemod.features.experiments;

import java.util.ArrayList;
import java.util.Collections;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentInventoryTest {
    @BeforeAll static void bootstrap() { ExperimentMenuTest.bootstrap(); }
    @Test void packetCopiesCannotObserveLiveMenuPredictionOrLaterPayloadMutation() {
        var inventory = new ExperimentInventory(); var payload = new ItemStack(Items.DIAMOND);
        payload.set(DataComponents.CUSTOM_NAME, Component.literal("revealed reward"));
        inventory.setSlot(10, payload);
        // A live menu can move, rename or remove this original stack during click prediction.
        payload.setCount(7); payload.set(DataComponents.CUSTOM_NAME, Component.literal("client prediction"));
        inventory.setSlot(49, new ItemStack(Items.CLOCK));
        assertEquals(1, inventory.get(10).getCount());
        assertEquals("revealed reward", ExperimentMenu.name(inventory.get(10)));
        assertTrue(inventory.get(11).isEmpty());
    }
    @Test void completePayloadIsCopiedAndPlayerInventorySlotsCannotAlterBoard() {
        var inventory = new ExperimentInventory();
        var packet = new ArrayList<>(Collections.nCopies(90, ItemStack.EMPTY));
        var card = new ItemStack(Items.ENCHANTED_BOOK); packet.set(10, card); packet.set(60, new ItemStack(Items.DIAMOND));
        assertTrue(inventory.setContents(packet)); card.setCount(3); packet.set(10, ItemStack.EMPTY);
        assertEquals(1, inventory.get(10).getCount());
        assertFalse(inventory.setSlot(-1, new ItemStack(Items.DIAMOND)));
        assertFalse(inventory.setSlot(60, new ItemStack(Items.DIAMOND)));
        assertTrue(inventory.get(10).is(Items.ENCHANTED_BOOK));
    }
    @Test void incompleteFullContentsAndCloseDiscardStaleObservations() {
        var inventory = new ExperimentInventory(); inventory.setSlot(10, new ItemStack(Items.DIAMOND));
        assertFalse(inventory.setContents(Collections.nCopies(27, ItemStack.EMPTY)));
        assertTrue(inventory.get(10).isEmpty());
        inventory.setSlot(10, new ItemStack(Items.DIAMOND)); inventory.clear();
        assertTrue(inventory.get(10).isEmpty());
    }
}

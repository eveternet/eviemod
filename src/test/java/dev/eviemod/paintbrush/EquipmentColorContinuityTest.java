package dev.eviemod.paintbrush;

import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentColorContinuityTest {
    @TempDir Path directory;
    private static final UUID ID = UUID.fromString("f1aa0d73-40e3-4950-ba29-e3b66745d2bd");
    @BeforeAll static void bootstrap() { ModelOverridesTest.bootstrap(); }
    private ItemStack boots(String uuid, String id) {
        ItemStack stack = new ItemStack(Items.LEATHER_BOOTS);
        CompoundTag tag = new CompoundTag(); tag.putString("id", id);
        if (uuid != null) tag.putString("uuid", uuid);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); return stack;
    }
    private ItemStack boots(String uuid) { return boots(uuid, "SPEED_WITHER_BOOTS"); }

    @Test void bridgesCapturedUuidGapWithoutChangingItemData() throws Exception {
        var colors = new ColorOverrides(directory.resolve("colors.json")); colors.set(ID, 0xffff55);
        var bridge = new EquipmentColorContinuity();
        bridge.observe(EquipmentSlot.FEET, boots(ID.toString()));
        ItemStack transientStack = boots(null); var before = transientStack.copy();
        assertEquals(0xffffff55, bridge.resolve(EquipmentSlot.FEET, transientStack, transientStack, colors, 0xff9e1adf));
        assertEquals(0xffffff55, bridge.resolve(EquipmentSlot.FEET, transientStack, transientStack, colors, 0xffcf14ef));
        assertTrue(ItemStack.isSameItemSameComponents(before, transientStack));
        assertNull(SkyBlockUuid.read(transientStack));
        assertEquals(ID, bridge.observe(EquipmentSlot.FEET, boots(ID.toString())));
        colors.set(ID, null);
        assertEquals(42, bridge.resolve(EquipmentSlot.FEET, transientStack, transientStack, colors, 42));
    }
    @Test void identityPersistsAcrossProlongedMovementAndOtherSlotsCannotBorrowIt() throws Exception {
        var colors = new ColorOverrides(directory.resolve("colors.json")); colors.set(ID, 0xffffff);
        var bridge = new EquipmentColorContinuity();
        bridge.observe(EquipmentSlot.FEET, boots(ID.toString()));
        // More than an hour of distinct, UUID-less tick updates; no wall-clock expiration.
        for (int tick = 0; tick < 72001; tick++) {
            var replacement = boots(null);
            assertEquals(0xffffffff, bridge.resolve(EquipmentSlot.FEET, replacement, replacement, colors, 0xff620dc5));
        }
        assertNull(bridge.observe(EquipmentSlot.CHEST, boots(null)));
        assertNull(new EquipmentColorContinuity().observe(EquipmentSlot.FEET, boots(null)));
        assertEquals(ID, bridge.observe(EquipmentSlot.FEET, boots(ID.toString())));
    }

    @Test void animationsAndSavedEditsStayLiveWhileUuidIsMissing() throws Exception {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var colors = new ColorOverrides(directory.resolve("colors.json"), clock::get);
        colors.setValue(ID, "Rose Dye");
        var bridge = new EquipmentColorContinuity(); bridge.observe(EquipmentSlot.FEET, boots(ID.toString()));
        var missing = boots(null); var preset = DyePresets.find("Rose Dye");
        int first = bridge.resolve(EquipmentSlot.FEET, missing, missing, colors, 42);
        int changedFrame = 0;
        while (preset.frames().get(changedFrame).equals(preset.frames().getFirst())) changedFrame++;
        clock.set(3_600_000L + changedFrame * 100L);
        int animated = bridge.resolve(EquipmentSlot.FEET, missing, missing, colors, 42);
        assertEquals(0xff000000 | preset.colorAt(clock.get()), animated);
        // Check the first distinct frame without relying on cycle divisibility.
        clock.set(changedFrame * 100L);
        assertNotEquals(first, bridge.resolve(EquipmentSlot.FEET, missing, missing, colors, 42));
        colors.set(ID, 0x123456);
        assertEquals(0xff123456, bridge.resolve(EquipmentSlot.FEET, missing, missing, colors, 42));
        colors.set(ID, null);
        assertEquals(42, bridge.resolve(EquipmentSlot.FEET, missing, missing, colors, 42));
    }

    @Test void replacementUuidWinsAndInvalidTransitionsClearHistory() {
        var bridge = new EquipmentColorContinuity();
        var otherId = UUID.randomUUID();
        bridge.observe(EquipmentSlot.FEET, boots(ID.toString()));
        assertEquals(otherId, bridge.observe(EquipmentSlot.FEET, boots(otherId.toString())));
        assertEquals(otherId, bridge.observe(EquipmentSlot.FEET, boots(null)));
        for (ItemStack invalid : new ItemStack[] {ItemStack.EMPTY, boots(null, "OTHER_BOOTS"), boots("bad"), boots(null, ""), new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)}) {
            bridge.observe(EquipmentSlot.FEET, boots(ID.toString()));
            assertNull(bridge.observe(EquipmentSlot.FEET, invalid));
            assertNull(bridge.observe(EquipmentSlot.FEET, boots(null)));
        }
        bridge.observe(EquipmentSlot.FEET, boots(ID.toString())); bridge.clear();
        assertNull(bridge.observe(EquipmentSlot.FEET, boots(null)));
    }
    @Test void detachedOrForeignCopiesNeverReceiveTheFallback() throws Exception {
        var colors = new ColorOverrides(directory.resolve("colors.json")); colors.set(ID, 0xffff55);
        var bridge = new EquipmentColorContinuity(); bridge.observe(EquipmentSlot.FEET, boots(ID.toString()));
        var equipped = boots(null);
        assertEquals(42, bridge.resolve(EquipmentSlot.FEET, equipped, equipped.copy(), colors, 42));
        assertEquals(42, bridge.resolve(EquipmentSlot.FEET, equipped, boots(UUID.randomUUID().toString()), colors, 42));
    }

    @Test void verifiedWornCopyRetainsColorWithoutMutatingEitherStack() throws Exception {
        var colors = new ColorOverrides(directory.resolve("colors.json")); colors.set(ID, 0xffffff);
        var bridge = new EquipmentColorContinuity(); bridge.observe(EquipmentSlot.FEET, boots(ID.toString()));
        var equipped = boots(null); var rendered = equipped.copy(); var before = rendered.copy();
        assertNotSame(equipped, rendered);
        assertEquals(42, bridge.resolve(EquipmentSlot.FEET, equipped, rendered, colors, 42));
        assertEquals(0xffffffff, bridge.resolveOwnedCopy(EquipmentSlot.FEET, equipped, rendered, colors, 42));
        assertTrue(ItemStack.isSameItemSameComponents(before, rendered));
        assertTrue(ItemStack.isSameItemSameComponents(before, equipped));
        colors.set(ID, 0xff43a4);
        assertEquals(0xffff43a4, bridge.resolveOwnedCopy(EquipmentSlot.FEET, equipped, rendered, colors, 42));
        colors.set(ID, null);
        assertEquals(42, bridge.resolveOwnedCopy(EquipmentSlot.FEET, equipped, rendered, colors, 42));
    }

    @Test void verifiedCopyStillRejectsDifferentItemsMalformedIdentityAndClearedSlots() throws Exception {
        var colors = new ColorOverrides(directory.resolve("colors.json")); colors.set(ID, 0xffffff);
        var bridge = new EquipmentColorContinuity(); bridge.observe(EquipmentSlot.FEET, boots(ID.toString()));
        var equipped = boots(null);
        for (var copy : new ItemStack[] {boots(null, "OTHER_BOOTS"), boots("bad"), boots(UUID.randomUUID().toString()), new ItemStack(Items.LEATHER_BOOTS), ItemStack.EMPTY})
            assertEquals(42, bridge.resolveOwnedCopy(EquipmentSlot.FEET, equipped, copy, colors, 42));
        assertEquals(42, bridge.resolveOwnedCopy(EquipmentSlot.FEET, ItemStack.EMPTY, equipped.copy(), colors, 42));
        assertEquals(42, bridge.resolveOwnedCopy(EquipmentSlot.FEET, equipped, equipped.copy(), colors, 42));
    }
}

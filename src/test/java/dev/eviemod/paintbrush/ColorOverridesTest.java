package dev.eviemod.paintbrush;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ColorOverridesTest {
    private static final UUID KEY = UUID.fromString("d320530e-052d-48fa-bf56-09c2e1a4a12d");
    @TempDir Path directory;

    @BeforeAll static void bootstrap() { ModelOverridesTest.bootstrap(); }

    private ItemStack armor() {
        var stack = new ItemStack(Items.LEATHER_CHESTPLATE);
        var tag = new CompoundTag();
        tag.putString("id", "TEST_CHESTPLATE");
        tag.putString("uuid", KEY.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0x123456));
        return stack;
    }

    @Test void replacementModelCanDyeGoldWithoutChangingTheRealItem() throws Exception {
        var store = new ColorOverrides(directory.resolve("colors.json"));
        var stack = new ItemStack(Items.GOLDEN_CHESTPLATE);
        stack.set(DataComponents.CUSTOM_DATA, armor().get(DataComponents.CUSTOM_DATA));
        var before = stack.copy();
        store.set(KEY, 0xff88cc);
        assertEquals(42, store.resolve(stack, 42));
        assertEquals(0xffff88cc, store.resolve(stack, 42, true));
        assertTrue(ItemStack.isSameItemSameComponents(before, stack));
        store.set(KEY, null);
        assertEquals(42, store.resolve(stack, 42, true));
        store.set(KEY, 0xff88cc);
        stack.remove(DataComponents.CUSTOM_DATA);
        assertEquals(42, store.resolve(stack, 42, true));
    }

    @Test void validatesExactRgbAndAllowsBlack() {
        assertEquals(0, ColorOverrides.parseHex("#000000"));
        assertEquals(0xffffff, ColorOverrides.parseHex("FFFFFF"));
        assertEquals(0xff88cc, ColorOverrides.parseHex("#ff88Cc"));
        for (String bad : new String[] {null, "", "#FFF", "#FFFFFFFF", "0x123456", "GG0000", "-12345", "123456 "}) {
            assertNull(ColorOverrides.parseHex(bad));
        }
        assertEquals("#000001", ColorOverrides.format(1));
    }

    @Test void supportsOtherDyeableEquipment() throws Exception {
        var store = new ColorOverrides(directory.resolve("colors.json"));
        store.set(KEY, 0x123456);
        for (var item : new net.minecraft.world.item.Item[] {Items.LEATHER_HORSE_ARMOR, Items.WOLF_ARMOR}) {
            var stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_DATA, armor().get(DataComponents.CUSTOM_DATA));
            assertTrue(ColorOverrides.isDyeable(stack));
            assertEquals(0xff123456, store.resolve(stack, 0));
        }
    }

    @Test void rendersOpaqueColorWithoutMutatingStack() throws Exception {
        var store = new ColorOverrides(directory.resolve("colors.json"));
        var stack = armor();
        var before = stack.copy();
        store.set(KEY, 0);
        assertEquals(0xff000000, store.resolve(stack, 0xff123456));
        store.set(KEY, 0xff88cc);
        assertEquals(0xffff88cc, store.resolve(stack, 0xff123456));
        assertTrue(ItemStack.isSameItemSameComponents(before, stack));
        assertEquals(before.getComponentsPatch(), stack.getComponentsPatch());
        assertEquals(0x123456, stack.get(DataComponents.DYED_COLOR).rgb());
        stack.remove(DataComponents.DYED_COLOR);
        assertEquals(0xffff88cc, store.resolve(stack, 0));
        assertNull(stack.get(DataComponents.DYED_COLOR));
    }

    @Test void ignoresNonLeatherMissingAndOtherUuids() throws Exception {
        var store = new ColorOverrides(directory.resolve("colors.json"));
        store.set(KEY, 0xff88cc);
        var plain = new ItemStack(Items.LEATHER_HELMET);
        assertEquals(42, store.resolve(plain, 42));
        assertEquals(42, store.resolve(ItemStack.EMPTY, 42));
        var other = armor();
        var tag = other.get(DataComponents.CUSTOM_DATA).copyTag();
        tag.putString("uuid", UUID.randomUUID().toString());
        other.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        assertEquals(42, store.resolve(other, 42));
        tag.putString("uuid", "bad");
        other.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        assertEquals(42, store.resolve(other, 42));
        var diamond = new ItemStack(Items.DIAMOND_CHESTPLATE);
        diamond.set(DataComponents.CUSTOM_DATA, armor().get(DataComponents.CUSTOM_DATA));
        assertEquals(42, store.resolve(diamond, 42));
        for (var item : new net.minecraft.world.item.Item[] {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS}) {
            var leather = new ItemStack(item);
            leather.set(DataComponents.CUSTOM_DATA, armor().get(DataComponents.CUSTOM_DATA));
            assertEquals(0xffff88cc, store.resolve(leather, 42));
        }
    }

    @Test void replacementStacksResolveImmediatelyWithoutSlotOrUuidLeakage() throws Exception {
        var store = new ColorOverrides(directory.resolve("colors.json"));
        store.setValue(KEY, "Pure Black Dye");
        for (var item : new net.minecraft.world.item.Item[] {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS}) {
            var refreshed = new ItemStack(item);
            refreshed.set(DataComponents.CUSTOM_DATA, armor().get(DataComponents.CUSTOM_DATA));
            refreshed.set(DataComponents.DYED_COLOR, new DyedItemColor(0xffffff));
            var before = refreshed.copy();
            assertEquals(0xff000000, store.resolve(refreshed, 0xffffffff));
            assertTrue(ItemStack.isSameItemSameComponents(before, refreshed));
            refreshed.remove(DataComponents.CUSTOM_DATA);
            assertEquals(0xffffffff, store.resolve(refreshed, 0xffffffff));
            var data = armor().get(DataComponents.CUSTOM_DATA).copyTag();
            data.putString("uuid", UUID.randomUUID().toString());
            refreshed.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            assertEquals(0xffffffff, store.resolve(refreshed, 0xffffffff));
            refreshed.set(DataComponents.CUSTOM_DATA, armor().get(DataComponents.CUSTOM_DATA));
            assertEquals(0xff000000, store.resolve(refreshed, 0xffffffff));
        }
    }


    @Test void persistsClearsAndPreservesMalformedConfig() throws Exception {
        Path file = directory.resolve("colors.json");
        var store = new ColorOverrides(file);
        store.set(KEY, 0xff88cc);
        var loaded = new ColorOverrides(file);
        loaded.load();
        assertEquals(0xff88cc, loaded.get(KEY));
        Files.writeString(file, "{\"" + KEY + "\":\"#GGGGGG\"}");
        assertThrows(java.io.IOException.class, loaded::load);
        assertTrue(Files.readString(file).contains("#GGGGGG"));
        assertEquals(0xff88cc, loaded.get(KEY));
        assertThrows(IllegalArgumentException.class, () -> loaded.set(KEY, 0x1000000));
        assertThrows(IllegalArgumentException.class, () -> loaded.set(KEY, -1));
        loaded.set(KEY, null);
        store.load();
        assertNull(store.get(KEY));
        assertEquals(42, store.resolve(armor(), 42));
    }
}

package dev.eviemod.paintbrush;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NameOverridesTest {
    private static final UUID KEY = UUID.fromString("d320530e-052d-48fa-bf56-09c2e1a4a12d");
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { ModelOverridesTest.bootstrap(); }

    private ItemStack stack(String uuid) {
        var stack = new ItemStack(Items.BOW);
        var tag = new CompoundTag();
        tag.putString("id", "JUJU_SHORTBOW");
        if (uuid != null) tag.putString("uuid", uuid);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Original Juju").withStyle(ChatFormatting.GOLD));
        return stack;
    }

    @Test void changesDisplayOnlyAndReturnsFreshText() throws Exception {
        var store = new NameOverrides(directory.resolve("names.json"));
        store.set(KEY, "My favourite bow ✨");
        var stack = stack(KEY.toString());
        var before = stack.copy();
        var original = stack.getHoverName();
        var result = store.resolve(stack, original);
        assertEquals("My favourite bow ✨", result.getString());
        assertEquals(original.getStyle().getColor(), result.getStyle().getColor());
        assertFalse(result.getStyle().isItalic());
        assertNotSame(result, store.resolve(stack, original));
        assertTrue(ItemStack.isSameItemSameComponents(before, stack));
        assertEquals(before.getComponentsPatch(), stack.getComponentsPatch());
        assertEquals("Original Juju", stack.get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test void missingMalformedAndUnconfiguredUuidsKeepOriginal() throws Exception {
        var store = new NameOverrides(directory.resolve("names.json"));
        var original = Component.literal("unchanged");
        assertSame(original, store.resolve(stack(KEY.toString()), original));
        store.set(KEY, "New name");
        for (String uuid : new String[] {null, "", "bad", UUID.randomUUID().toString()}) {
            assertSame(original, store.resolve(stack(uuid), original));
        }
        assertSame(original, store.resolve(ItemStack.EMPTY, original));
    }

    @Test void persistsAndClearsNamesWhileProtectingBadConfig() throws Exception {
        Path file = directory.resolve("names.json");
        var store = new NameOverrides(file);
        store.set(KEY, "Bow with spaces & punctuation!");
        var loaded = new NameOverrides(file);
        loaded.load();
        assertEquals(store.get(KEY), loaded.get(KEY));
        Files.writeString(file, "{\"" + KEY + "\":42}");
        assertThrows(java.io.IOException.class, loaded::load);
        assertEquals(store.get(KEY), loaded.get(KEY));
        assertTrue(Files.readString(file).endsWith("42}"));
        for (String bad : new String[] {"", "   ", "line\nbreak", "§cRed", "x".repeat(257)}) {
            assertThrows(IllegalArgumentException.class, () -> loaded.set(KEY, bad));
        }
        loaded.set(KEY, null);
        store.load();
        assertNull(store.get(KEY));
        var original = Component.literal("original");
        assertSame(original, store.resolve(stack(KEY.toString()), original));
    }
}

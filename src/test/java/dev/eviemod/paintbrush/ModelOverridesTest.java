package dev.eviemod.paintbrush;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ModelOverridesTest {
    private static final String SAMPLE_UUID = "d320530e-052d-48fa-bf56-09c2e1a4a12d";
    private static final UUID UUID_KEY = UUID.fromString(SAMPLE_UUID);
    private static final Identifier ORIGINAL = Identifier.parse("hypixel_skyblock:item/slayer/enderman/weapons/juju_shortbow");
    private static final Identifier CUSTOM = Identifier.parse("minecraft:diamond_sword");
    @TempDir Path directory;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
            .build(net.minecraft.data.registries.VanillaRegistries.createLookup())
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    }

    private ItemStack stack(String uuid) {
        ItemStack stack = new ItemStack(Items.BOW);
        CompoundTag data = new CompoundTag();
        data.putString("id", "JUJU_SHORTBOW");
        if (uuid != null) data.putString("uuid", uuid);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.ITEM_MODEL, ORIGINAL);
        return stack;
    }

    @Test void resolvesSampleWithoutChangingAnyComponents() throws Exception {
        var store = new ModelOverrides(directory.resolve("models.json"));
        store.set(UUID_KEY, CUSTOM);
        ItemStack stack = stack(SAMPLE_UUID);
        ItemStack before = stack.copy();
        var patch = stack.getComponentsPatch();
        assertEquals(UUID_KEY, SkyBlockUuid.read(stack));
        assertEquals(CUSTOM, store.resolve(stack, ORIGINAL));
        assertEquals(ORIGINAL, stack.get(DataComponents.ITEM_MODEL));
        assertEquals(patch, stack.getComponentsPatch());
        assertTrue(ItemStack.isSameItemSameComponents(before, stack));
        assertEquals(before.getCount(), stack.getCount());
        assertEquals(CUSTOM, store.resolve(stack.copy(), ORIGINAL));
    }

    @Test void leavesMissingMalformedAndUnconfiguredUuidsAlone() throws Exception {
        var store = new ModelOverrides(directory.resolve("models.json"));
        store.set(UUID_KEY, CUSTOM);
        for (String uuid : new String[] {null, "", "not-a-uuid", "1-1-1-1-1", UUID.randomUUID().toString()}) {
            assertSame(ORIGINAL, store.resolve(stack(uuid), ORIGINAL));
        }
        assertSame(ORIGINAL, store.resolve(new ItemStack(Items.BOW), ORIGINAL));
        assertNull(store.resolve(ItemStack.EMPTY, null));
        ItemStack wrongType = stack(null);
        var data = wrongType.get(DataComponents.CUSTOM_DATA).copyTag();
        data.putInt("uuid", 42);
        wrongType.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        assertSame(ORIGINAL, store.resolve(wrongType, ORIGINAL));
    }

    @Test void rereadsChangedStackDataAndNormalizesUuidCase() throws Exception {
        var store = new ModelOverrides(directory.resolve("models.json"));
        store.set(UUID_KEY, CUSTOM);
        ItemStack stack = stack(SAMPLE_UUID.toUpperCase());
        assertEquals(CUSTOM, store.resolve(stack, ORIGINAL));
        stack.set(DataComponents.CUSTOM_DATA, stack(UUID.randomUUID().toString()).get(DataComponents.CUSTOM_DATA));
        assertSame(ORIGINAL, store.resolve(stack, ORIGINAL));
    }

    @Test void persistsAndRemovesOverrides() throws Exception {
        Path file = directory.resolve("models.json");
        var first = new ModelOverrides(file);
        first.set(UUID_KEY, CUSTOM);
        var reloaded = new ModelOverrides(file);
        reloaded.load();
        assertEquals(CUSTOM, reloaded.get(UUID_KEY));
        reloaded.set(UUID_KEY, null);
        first.load();
        assertNull(first.get(UUID_KEY));
        assertSame(ORIGINAL, first.resolve(stack(SAMPLE_UUID), ORIGINAL));
    }

    @Test void badConfigPreservesFileAndLastGoodMappings() throws Exception {
        Path file = directory.resolve("models.json");
        var store = new ModelOverrides(file);
        store.set(UUID_KEY, CUSTOM);
        for (String invalid : new String[] {"{", "null", "[]", "{\"\":\"minecraft:bow\"}",
                "{\"" + SAMPLE_UUID + "\":\"Bad Model!\"}"}) {
            Files.writeString(file, invalid);
            assertThrows(java.io.IOException.class, store::load);
            assertEquals(invalid, Files.readString(file));
            assertEquals(CUSTOM, store.get(UUID_KEY));
        }
    }
}

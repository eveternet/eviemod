package dev.eviemod.paintbrush;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RarityMemoryTest {
    private static final String UUID = "f1aa0d73-40e3-4950-ba29-e3b66745d2bd";
    private static final String OTHER = "f1aa0d73-40e3-4950-ba29-e3b66745d2be";
    @BeforeAll static void bootstrap() { ModelOverridesTest.bootstrap(); }
    private static ItemStack item(String uuid, String rarity) {
        var stack = new ItemStack(Items.LEATHER_BOOTS);
        var tag = new CompoundTag(); tag.putString("id", "SPEED_WITHER_BOOTS");
        if (uuid != null) tag.putString("uuid", uuid);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        if (rarity != null) stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(rarity))));
        return stack;
    }
    @Test void skyblockRarityIsIndependentOfVanillaRarityAndLocalName() {
        var stack = item(UUID, "§d§lMYTHIC DUNGEON BOOTS");
        assertEquals(ItemRarity.MYTHIC, ItemRarity.read(stack));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("LEGENDARY"));
        stack.set(DataComponents.RARITY, net.minecraft.world.item.Rarity.EPIC);
        stack.remove(DataComponents.LORE);
        assertNull(ItemRarity.read(stack));
        stack.set(DataComponents.TOOLTIP_STYLE, Identifier.parse("hypixel_skyblock:supreme"));
        assertNull(ItemRarity.read(stack)); // supplied Skyblocker does not recognize the supreme style alias
        stack.set(DataComponents.TOOLTIP_STYLE, Identifier.parse("hypixel_skyblock:divine"));
        assertEquals(ItemRarity.DIVINE, ItemRarity.read(stack));
        stack.set(DataComponents.TOOLTIP_STYLE, Identifier.parse("minecraft:epic"));
        assertNull(ItemRarity.read(stack));
    }
    @Test void matchesSkyblockerLoreSubstringRulesWithoutRequiringCustomData() {
        for (var rarity : ItemRarity.values()) {
            assertEquals(rarity, ItemRarity.read(item(null, rarity.name().replace('_',' ') + " SWORD")));
        }
        assertEquals(ItemRarity.MYTHIC, ItemRarity.read(item(UUID, "a SHINY MYTHIC DUNGEON BOOTS a")));
        assertEquals(ItemRarity.LEGENDARY, ItemRarity.read(item(UUID, "Find a LEGENDARY item")));
        var vanilla = item(UUID, "RARE SWORD"); vanilla.remove(DataComponents.CUSTOM_DATA);
        assertEquals(ItemRarity.RARE, ItemRarity.read(vanilla));
    }
    @Test void bridgesLongSlotGapsWithoutMutatingDataOrLeakingToCopies() {
        var memory = new RarityMemory();
        assertEquals(ItemRarity.MYTHIC, memory.observe(38, item(UUID, "MYTHIC BOOTS")));
        var gap = item(null, null); var original = gap.copy();
        for (int i = 0; i < 72001; i++) assertEquals(ItemRarity.MYTHIC, memory.observe(38, gap));
        assertNull(memory.resolve(gap.copy()));
        assertNull(memory.observe(39, gap.copy()));
        assertTrue(ItemStack.isSameItemSameComponents(original, gap));
        memory.clear(); assertNull(memory.observe(38, gap));
    }
    @Test void confirmedIdentityTravelsWithItemButNewUuidNeverBorrowsRarity() {
        var memory = new RarityMemory(); memory.observe(1, item(UUID, "EPIC BOOTS"));
        assertEquals(ItemRarity.EPIC, memory.resolve(item(UUID, null)));
        assertNull(memory.observe(1, item(OTHER, null)));
        assertNull(memory.observe(1, item(null, null)));
        assertEquals(ItemRarity.LEGENDARY, memory.observe(1, item(OTHER, "LEGENDARY BOOTS")));
        assertEquals(ItemRarity.LEGENDARY, memory.observe(1, item(null, null)));
    }
    @Test void explicitChangesClearSlotMemory() {
        for (int scenario = 0; scenario < 6; scenario++) {
            var memory = new RarityMemory(); memory.observe(1, item(UUID, "MYTHIC BOOTS"));
            var changed = item(null, null);
            switch (scenario) {
                case 0 -> changed = ItemStack.EMPTY;
                case 1 -> changed.remove(DataComponents.CUSTOM_DATA);
                case 2 -> changed = item("malformed", null);
                case 3 -> changed.setCount(2);
                case 4 -> { var data = new CompoundTag(); data.putString("id", "OTHER_BOOTS"); changed.set(DataComponents.CUSTOM_DATA, CustomData.of(data)); }
                case 5 -> changed.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Unknown new item lore"))));
            }
            assertNull(memory.observe(1, changed));
            assertNull(memory.observe(1, item(null, null)));
        }
    }
    @Test void freshRarityReplacesCachedRarityAndUnknownMetadataInvalidatesIt() {
        var memory = new RarityMemory(); memory.resolve(item(UUID, "RARE BOOTS"));
        assertEquals(ItemRarity.MYTHIC, memory.resolve(item(UUID, "MYTHIC BOOTS")));
        assertEquals(ItemRarity.MYTHIC, memory.resolve(item(UUID, null)));
        assertNull(memory.resolve(item(UUID, "New unrecognized lore")));
        assertNull(memory.resolve(item(UUID, null)));
    }
}

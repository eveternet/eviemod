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

class RarityCacheTest {
    @BeforeAll static void bootstrap() { ModelOverridesTest.bootstrap(); }
    private static void pet(ItemStack stack, String json) {
        var tag = new CompoundTag(); tag.putString("id", "PET"); tag.putString("petInfo", json);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    private static void lore(ItemStack stack, String text) {
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(text))));
    }

    @Test void unchangedPetReusesParseAcrossFramesAndUnrelatedChanges() {
        var cache = new RarityCache(); var stack = new ItemStack(Items.PLAYER_HEAD);
        pet(stack, "{\"type\":\"SHEEP\",\"tier\":\"RARE\"}");
        var parsed = cache.get(stack);
        assertEquals(ItemRarity.RARE, parsed.rarity());
        for (int frame = 0; frame < 100; frame++) assertSame(parsed, cache.get(stack));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("MYTHIC"));
        stack.setCount(2);
        assertSame(parsed, cache.get(stack));
        pet(stack, "{\"type\":\"SHEEP\",\"tier\":\"RARE\",\"heldItem\":\"PET_ITEM_TIER_BOOST\"}");
        assertNotSame(parsed, cache.get(stack));
        assertEquals(ItemRarity.EPIC, cache.get(stack).rarity());
    }

    @Test void invalidAndMissingResultsAreCachedAndReplacedWhenComponentsChange() {
        var cache = new RarityCache(); var stack = new ItemStack(Items.PLAYER_HEAD);
        for (String invalid : List.of("{", "{}", "x".repeat(ItemRarity.MAX_PET_INFO_LENGTH + 1))) {
            pet(stack, invalid);
            var parsed = cache.get(stack);
            assertNull(parsed.rarity());
            assertSame(parsed, cache.get(stack));
        }
        pet(stack, "{\"type\":\"SHEEP\",\"tier\":\"LEGENDARY\"}");
        assertEquals(ItemRarity.LEGENDARY, cache.get(stack).rarity());
        stack.remove(DataComponents.CUSTOM_DATA);
        var absent = cache.get(stack);
        assertNull(absent.rarity());
        assertSame(absent, cache.get(stack));
        lore(stack, "RARE SWORD");
        assertEquals(ItemRarity.RARE, cache.get(stack).rarity());
        lore(stack, "MYTHIC SWORD");
        assertEquals(ItemRarity.MYTHIC, cache.get(stack).rarity());
        stack.remove(DataComponents.LORE);
        stack.set(DataComponents.TOOLTIP_STYLE, Identifier.parse("hypixel_skyblock:divine"));
        assertEquals(ItemRarity.DIVINE, cache.get(stack).rarity());
        stack.set(DataComponents.TOOLTIP_STYLE, Identifier.parse("hypixel_skyblock:epic"));
        assertEquals(ItemRarity.EPIC, cache.get(stack).rarity());
        stack.remove(DataComponents.TOOLTIP_STYLE);
        assertNull(cache.get(stack).rarity());
    }

    @Test void emptyTransitionsAndNewStacksDoNotReuseAnInapplicableParse() {
        var cache = new RarityCache(); var stack = new ItemStack(Items.DIAMOND);
        lore(stack, "RARE");
        var parsed = cache.get(stack);
        assertNotSame(parsed, cache.get(stack.copy()));
        stack.setCount(0);
        assertNull(cache.get(stack).rarity());
        stack.setCount(1);
        assertEquals(ItemRarity.RARE, cache.get(stack).rarity());
    }

    @Test void cacheIsBoundedAndClearDropsSessionResults() {
        var cache = new RarityCache(); var first = new ItemStack(Items.DIAMOND);
        var parsed = cache.get(first);
        for (int i = 0; i < RarityCache.MAX_ENTRIES; i++) cache.get(new ItemStack(Items.DIAMOND));
        assertNotSame(parsed, cache.get(first));
        var current = cache.get(first);
        cache.clear();
        assertNotSame(current, cache.get(first));
    }
}

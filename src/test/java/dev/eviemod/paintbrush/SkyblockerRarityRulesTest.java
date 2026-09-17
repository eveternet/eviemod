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

class SkyblockerRarityRulesTest {
    @BeforeAll static void bootstrap() { ModelOverridesTest.bootstrap(); }
    private static ItemStack stack(String... lines) {
        var stack = new ItemStack(Items.DIAMOND);
        stack.set(DataComponents.LORE, new ItemLore(java.util.Arrays.stream(lines).map(Component::literal).map(c -> (Component)c).toList()));
        return stack;
    }
    private static ItemStack pet(String info) {
        var stack = stack("MYTHIC PET");
        stack.set(DataComponents.TOOLTIP_STYLE, Identifier.parse("hypixel_skyblock:divine"));
        var data = new CompoundTag(); data.putString("id", "PET"); data.putString("petInfo", info);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data)); return stack;
    }
    @Test void lorePrecedesStyleAndSearchesAboveTrailingMenuInstructions() {
        var stack = stack("EPIC SWORD", "LEGENDARY SWORD", "Click to inspect!", "");
        stack.set(DataComponents.TOOLTIP_STYLE, Identifier.parse("hypixel_skyblock:mythic"));
        assertEquals(ItemRarity.LEGENDARY, ItemRarity.read(stack));
        assertEquals(ItemRarity.UNCOMMON, ItemRarity.read(stack("UNCOMMON ACCESSORY")));
        assertEquals(ItemRarity.VERY_SPECIAL, ItemRarity.read(stack("VERY SPECIAL MEMENTO")));
        assertNull(ItemRarity.read(stack("RARE", "UNKNOWN")));
    }
    @Test void arbitraryNamesAndVanillaRarityDoNotCreateBackgrounds() {
        var stack = stack("Click to go back!");
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("LEGENDARY MENU"));
        stack.set(DataComponents.RARITY, net.minecraft.world.item.Rarity.EPIC);
        assertNull(ItemRarity.read(stack));
        stack.set(DataComponents.TOOLTIP_STYLE, Identifier.parse("minecraft:legendary"));
        assertNull(ItemRarity.read(stack));
    }
    @Test void validPetsOverrideLoreAndApplyTierBoost() {
        assertEquals(ItemRarity.RARE, ItemRarity.read(pet("{\"type\":\"SHEEP\",\"tier\":\"RARE\"}")));
        assertEquals(ItemRarity.EPIC, ItemRarity.read(pet("{\"type\":\"SHEEP\",\"tier\":\"RARE\",\"heldItem\":\"PET_ITEM_TIER_BOOST\"}")));
    }
    @Test void invalidPetsDoNotFallBackToLoreStyleOrMemory() {
        for (String value : List.of("", "{}", "null", "{", "{\"tier\":\"RARE\"}", "{\"type\":\"SHEEP\",\"tier\":\"BAD\"}")) {
            var stack = pet(value);
            assertNull(ItemRarity.read(stack));
            assertFalse(ItemRarity.missingMetadata(stack));
        }
    }
    @Test void releaseScopeExcludesLobbiesOtherGamesSingleplayerAndNoWorld() {
        assertTrue(SkyBlockSession.allowed(true, false, false));
        assertFalse(SkyBlockSession.allowed(false, false, false));
        assertFalse(SkyBlockSession.allowed(false, false, true));
        assertFalse(SkyBlockSession.allowed(false, true, false));
        assertTrue(SkyBlockSession.allowed(false, true, true));
    }
}

package dev.eviemod.features.experiments;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.eviemod.features.experiments.ExperimentSession.*;

class ExperimentMenuTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(net.minecraft.data.registries.VanillaRegistries.createLookup())
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    }
    private static ItemStack item(String id, String name, int count) {
        var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)), count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack;
    }
    @Test void onlyActualExperimentPlayTitlesAreRecognized() {
        for (String tier : new String[]{"High", "Grand", "Supreme", "Transcendent", "Metaphysical"}) {
            assertEquals(Game.CHRONOMATRON, ExperimentMenu.game("§aChronomatron (" + tier + ")"));
            assertEquals(Game.ULTRASEQUENCER, ExperimentMenu.game("Ultrasequencer (" + tier + ")"));
            assertEquals(Game.SUPERPAIRS, ExperimentMenu.game("Superpairs (" + tier + ")"));
        }
        for (String title : new String[]{"Experimentation Table", "Chronomatron", "Chronomatron ➜ Stakes", "Superpairs Rewards",
            "Superpairs (Future)", "Experiment Over", "Other Superpairs (High)"}) assertNull(ExperimentMenu.game(title));
    }
    @Test void phaseRequiresBothExpectedServerItemAndInstruction() {
        assertEquals(Phase.REMEMBER, ExperimentMenu.phase(Game.CHRONOMATRON, item("minecraft:glowstone", "§aRemember the pattern!", 1)));
        assertEquals(Phase.REPLAY, ExperimentMenu.phase(Game.CHRONOMATRON, item("minecraft:clock", "§aTimer: §f12s", 1)));
        assertEquals(Phase.REPLAY, ExperimentMenu.phase(Game.ULTRASEQUENCER, item("minecraft:clock", "Timer: 9", 1)));
        for (String name : new String[]{"Timer: ?", "Timer: -1", "Rewards", "Remember the pattern!"})
            assertEquals(Phase.UNKNOWN, ExperimentMenu.phase(Game.CHRONOMATRON, item("minecraft:clock", name, 1)));
        assertEquals(Phase.UNKNOWN, ExperimentMenu.phase(Game.CHRONOMATRON, item("minecraft:diamond", "Timer: 12s", 1)));
    }
    @Test void roundAndNumberRejectMissingMalformedAndConflictingData() {
        assertEquals(15, ExperimentMenu.round(item("minecraft:book", "Round: 15", 15)));
        for (String name : new String[]{"Round: 0", "Round: 50", "Round: 999999999999", "Round", "15"})
            assertEquals(0, ExperimentMenu.round(item("minecraft:book", name, 1)));
        assertEquals(36, ExperimentMenu.number(item("minecraft:blue_dye", "36", 36)));
        assertEquals(0, ExperimentMenu.number(item("minecraft:blue_dye", "2", 1)));
        assertEquals(0, ExperimentMenu.number(item("minecraft:blue_dye", "37", 37)));
    }
    @Test void terracottaAndGlassColorsMatchOnBothVersions() {
        assertEquals("red", ExperimentMenu.note(item("minecraft:red_terracotta", "note", 1)));
        assertEquals("red", ExperimentMenu.note(item("minecraft:red_stained_glass", "note", 1)));
        assertEquals("light_blue", ExperimentMenu.note(item("minecraft:light_blue_terracotta", "note", 1)));
        assertEquals("", ExperimentMenu.note(new ItemStack(Items.DIAMOND)));
    }
    @Test void hiddenPlaceholdersAndPowerupsAreKeptSeparateFromRewards() {
        assertTrue(ExperimentMenu.hidden(item("minecraft:cyan_stained_glass", "Click any button!", 1)));
        assertTrue(ExperimentMenu.hidden(item("minecraft:cyan_stained_glass", "Click a second button!", 1)));
        assertFalse(ExperimentMenu.hidden(item("minecraft:cyan_stained_glass", "Unsupported instruction", 1)));
        assertFalse(ExperimentMenu.hidden(item("minecraft:diamond", "Click a second button!", 1)));
        assertFalse(ExperimentMenu.reward(item("minecraft:black_stained_glass_pane", "", 1)));
        assertFalse(ExperimentMenu.reward(ItemStack.EMPTY));
        var reward = item("minecraft:enchanted_book", "Protection VI", 1);
        assertTrue(ExperimentMenu.reward(reward)); assertFalse(ExperimentMenu.powerup(reward));
        for (String text : new String[]{"§aPowerup", "Power Up"}) {
            reward.set(DataComponents.LORE, new ItemLore(java.util.List.of(Component.literal(text))));
            assertTrue(ExperimentMenu.powerup(reward));
        }
    }
    @Test void cardEqualityIncludesCountAndEnchantmentMetadataAndCopiesRemainIndependent() {
        var first = item("minecraft:enchanted_book", "Protection VI", 1); var copy = first.copy();
        assertTrue(ItemStack.matches(first, copy));
        copy.set(DataComponents.CUSTOM_NAME, Component.literal("Protection VII")); assertFalse(ItemStack.matches(first, copy));
        assertEquals("Protection VI", ExperimentMenu.name(first));
        copy = first.copy(); copy.setCount(2); assertFalse(ItemStack.matches(first, copy));
        copy = first.copy(); var tag = new net.minecraft.nbt.CompoundTag(); tag.putString("enchantment", "protection_7");
        copy.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        assertFalse(ItemStack.matches(first, copy));
    }
}

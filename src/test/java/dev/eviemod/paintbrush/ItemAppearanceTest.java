package dev.eviemod.paintbrush;

import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemAppearanceTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() { ModelOverridesTest.bootstrap(); }

    @Test void goldArmorUsesMatchingLeatherEquipmentWithoutChangingComponents() {
        var gold = new net.minecraft.world.item.Item[] {net.minecraft.world.item.Items.GOLDEN_HELMET,
            net.minecraft.world.item.Items.GOLDEN_CHESTPLATE, net.minecraft.world.item.Items.GOLDEN_LEGGINGS,
            net.minecraft.world.item.Items.GOLDEN_BOOTS};
        var leather = new net.minecraft.world.item.Item[] {net.minecraft.world.item.Items.LEATHER_HELMET,
            net.minecraft.world.item.Items.LEATHER_CHESTPLATE, net.minecraft.world.item.Items.LEATHER_LEGGINGS,
            net.minecraft.world.item.Items.LEATHER_BOOTS};
        for (int i = 0; i < gold.length; i++) {
            var stack = gold[i].getDefaultInstance();
            var before = stack.copy();
            var target = leather[i].getDefaultInstance();
            var original = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE).assetId().orElseThrow();
            var expected = target.get(net.minecraft.core.component.DataComponents.EQUIPPABLE).assetId().orElseThrow();
            assertEquals(expected, ItemAppearance.equipmentAsset(stack,
                target.get(net.minecraft.core.component.DataComponents.ITEM_MODEL), original));
            for (var model : new String[] {"minecraft:diamond_sword", "unknown:leather_boots", "minecraft:air"})
                assertEquals(original, ItemAppearance.equipmentAsset(stack, Identifier.parse(model), original));
            assertEquals(original, ItemAppearance.equipmentAsset(stack, null, original));
            assertEquals(original, ItemAppearance.equipmentAsset(stack,
                leather[(i + 1) % leather.length].getDefaultInstance().get(net.minecraft.core.component.DataComponents.ITEM_MODEL), original));
            assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameComponents(before, stack));
        }
    }

    @Test void absentOrInvalidPreviewOverridePreservesOriginal() {
        var original = Identifier.parse("hypixel_skyblock:crimson_chestplate");
        for (String value : new String[] {null, "", "  ", "Bad Model!"}) assertSame(original, ItemAppearance.previewModel(original, value));
        assertNull(ItemAppearance.previewModel(null, ""));
        assertEquals(Identifier.parse("minecraft:netherite_chestplate"), ItemAppearance.previewModel(original, "minecraft:netherite_chestplate"));
    }
    @Test void dyeTintIsRequiredOnReplacementModel() {
        assertFalse(ItemAppearance.hasDyeTint(JsonParser.parseString("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/netherite_chestplate\"}}")));
        assertTrue(ItemAppearance.hasDyeTint(JsonParser.parseString("{\"model\":{\"type\":\"minecraft:model\",\"tints\":[{\"type\":\"minecraft:dye\",\"default\":10511680}]}}")));
        assertTrue(ItemAppearance.hasDyeTint(JsonParser.parseString("{\"model\":{\"models\":[{\"tints\":[{\"type\":\"dye\"}]}]}}")));
        assertFalse(ItemAppearance.hasDyeTint(JsonParser.parseString("{\"tints\":[{\"type\":\"minecraft:constant\",\"value\":123}]}")));
    }
}

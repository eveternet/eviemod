package dev.eviemod.paintbrush;

import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemAppearanceTest {
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

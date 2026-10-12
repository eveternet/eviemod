package dev.eviemod.paintbrush;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuickNavigationIconsTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
            .build(net.minecraft.data.registries.VanillaRegistries.createLookup())
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    }
    @Test void defaultsAndComponentsProduceIndependentPreviewItems() {
        for (var tab : QuickNavigationSettings.defaults()) assertFalse(QuickNavigationIcons.parse(tab.icon).isEmpty());
        var stack = QuickNavigationIcons.parse("minecraft:leather_chestplate[minecraft:dyed_color=8991416]");
        assertEquals(8991416, stack.get(net.minecraft.core.component.DataComponents.DYED_COLOR).rgb());
        assertNull(QuickNavigationIcons.parse("minecraft:leather_chestplate").get(net.minecraft.core.component.DataComponents.DYED_COLOR));
    }
    @Test void unsupportedIconsFailWithoutGuessingAnItem() {
        for (String input : new String[]{"minecraft:does_not_exist", "minecraft:air", "minecraft:apple garbage", "minecraft:apple[bad=3]"}) {
            assertThrows(IllegalArgumentException.class, () -> QuickNavigationIcons.parse(input));
            assertTrue(QuickNavigationIcons.preview(input).is(net.minecraft.world.item.Items.BARRIER));
        }
    }
}

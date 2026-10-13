package dev.eviemod.paintbrush;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class QuickNavigationIconsTest {
    @TempDir java.nio.file.Path directory;
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
            .build(net.minecraft.data.registries.VanillaRegistries.createLookup())
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    }
    @Test void defaultsAndComponentsProduceIndependentPreviewItems() {
        for (var tab : QuickNavigationSettings.defaults()) assertTrue(QuickNavigationIcons.parse(tab).isEmpty());
        var stack = QuickNavigationIcons.parse("minecraft:leather_chestplate[minecraft:dyed_color=8991416]");
        assertEquals(8991416, stack.get(net.minecraft.core.component.DataComponents.DYED_COLOR).rgb());
        assertNull(QuickNavigationIcons.parse("minecraft:leather_chestplate").get(net.minecraft.core.component.DataComponents.DYED_COLOR));
    }
    @Test void heldSkyblockItemRetainsIdentityHeadTextureAndComponentsAcrossPersistence() throws Exception {
        var held = QuickNavigationIcons.parse("minecraft:player_head[minecraft:custom_data={id:\"WISHING_COMPASS\",uuid:\"00000000-0000-0000-0000-000000000001\"},minecraft:custom_name=\"Wishing Compass\"]");
        var profile = net.minecraft.world.item.component.ResolvableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
            com.google.gson.JsonParser.parseString("{\"properties\":[{\"name\":\"textures\",\"value\":\"eyJ0ZXh0dXJlcyI6e319\"}]}"))
            .getOrThrow();
        held.set(net.minecraft.core.component.DataComponents.PROFILE, profile);
        held.setCount(64);
        var next = new ModSettings.Values(); var tab = next.quickNavigation.tabs.get(3);
        QuickNavigationIcons.capture(tab, held); tab.command = "/skills";
        var store = new ModSettings(directory.resolve("settings.json")); store.save(next); store.load();
        var saved = store.values().quickNavigation.tabs.get(3);
        var first = QuickNavigationIcons.parse(saved); var second = QuickNavigationIcons.parse(saved);
        assertNotSame(held, first); assertNotSame(first, second); assertEquals(1, first.getCount());
        assertEquals(64, held.getCount()); assertEquals("Wishing Compass", first.getHoverName().getString());
        assertEquals("WISHING_COMPASS", first.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getStringOr("id", ""));
        assertEquals(profile.partialProfile().properties(), first.get(net.minecraft.core.component.DataComponents.PROFILE).partialProfile().properties());
        first.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        assertNotNull(second.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
        assertNotNull(held.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
        saved.editIcon("minecraft:player_head"); assertFalse(saved.capturedIcon.isEmpty());
        saved.editIcon("minecraft:apple"); assertTrue(saved.capturedIcon.isEmpty());
        assertTrue(QuickNavigationIcons.parse(saved).is(net.minecraft.world.item.Items.APPLE));
    }
    @Test void capturedDyedItemsRetainColorAndMalformedCapturesHaveNoFallback() {
        var held = QuickNavigationIcons.parse("minecraft:leather_chestplate[minecraft:dyed_color=8991416,minecraft:custom_data={id:\"SPEEDSTER_CHESTPLATE\"}]");
        var tab = new QuickNavigationSettings.Tab(); QuickNavigationIcons.capture(tab, held);
        assertEquals(8991416, QuickNavigationIcons.parse(tab).get(net.minecraft.core.component.DataComponents.DYED_COLOR).rgb());
        tab.icon = "minecraft:apple";
        assertThrows(IllegalArgumentException.class, () -> QuickNavigationIcons.parse(tab));
        tab.icon = "minecraft:leather_chestplate"; tab.capturedIcon = "{bad}";
        assertThrows(IllegalArgumentException.class, () -> QuickNavigationIcons.parse(tab));
        assertTrue(QuickNavigationIcons.preview(tab).is(net.minecraft.world.item.Items.BARRIER));
        var blank = new QuickNavigationSettings.Tab();
        assertThrows(IllegalArgumentException.class, () -> QuickNavigationIcons.capture(blank, net.minecraft.world.item.ItemStack.EMPTY));
        assertTrue(blank.empty());
    }
    @Test void unsupportedIconsFailWithoutGuessingAnItem() {
        for (String input : new String[]{"minecraft:does_not_exist", "minecraft:air", "minecraft:apple garbage", "minecraft:apple[bad=3]"}) {
            assertThrows(IllegalArgumentException.class, () -> QuickNavigationIcons.parse(input));
            assertTrue(QuickNavigationIcons.preview(input).is(net.minecraft.world.item.Items.BARRIER));
        }
    }
}

package dev.eviemod.paintbrush;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class QuickNavigationTest {
    @TempDir Path directory;
    @Test void defaultsStayOffAndExistingChoicesSurvive() throws Exception {
        var path = directory.resolve("settings.json"); var store = new ModSettings(path);
        for (String json : new String[]{"{}", "{\"quickNavigation\":{}}", "{\"rarityBackgrounds\":true}"}) {
            Files.writeString(path, json); store.load();
            assertFalse(store.values().quickNavigation.enabled);
            assertEquals(14, store.values().quickNavigation.tabs.size());
            store.save(store.values().copy()); store.load(); assertFalse(store.values().quickNavigation.enabled);
        }
        var draft = store.values().copy();
        draft.quickNavigation.enabled = true;
        var tab = draft.quickNavigation.tabs.get(2);
        tab.icon = "minecraft:apple"; tab.command = "/warp garden"; tab.tooltip = "My garden";
        tab.title = "Garden.*"; tab.hidden = true; tab.activation = QuickNavigationSettings.Activation.DOUBLE_CLICK;
        assertFalse(store.values().quickNavigation.enabled);
        store.save(draft); store.load();
        assertTrue(store.values().quickNavigation.enabled); assertTrue(store.values().rarityBackgrounds);
        var saved = store.values().quickNavigation.tabs.get(2);
        assertEquals("minecraft:apple", saved.icon); assertEquals("/warp garden", saved.command);
        assertEquals("My garden", saved.tooltip); assertEquals("Garden.*", saved.title); assertTrue(saved.hidden);
        assertEquals(QuickNavigationSettings.Activation.DOUBLE_CLICK, saved.activation);
        store.save(new ModSettings.Values()); store.load(); assertFalse(store.values().quickNavigation.enabled);
    }
    @Test void malformedSettingsArePreserved() throws Exception {
        var path = directory.resolve("settings.json");
        for (String json : new String[]{"null", "[]", "{\"enabled\":\"true\"}", "{\"enabled\":null}",
                "{\"tabs\":null}", "{\"tabs\":[]}", "{\"tabs\":[null]}"}) {
            String original = "{\"quickNavigation\":" + json + "}";
            Files.writeString(path, original); var store = new ModSettings(path);
            assertThrows(java.io.IOException.class, store::load);
            assertThrows(java.io.IOException.class, () -> store.save(new ModSettings.Values()));
            assertEquals(original, Files.readString(path));
        }
        for (String field : new String[]{"\"icon\":9", "\"hidden\":\"true\"", "\"title\":\"[\"", "\"command\":null", "\"activation\":\"UNKNOWN\""}) {
            var object = new com.google.gson.Gson().toJsonTree(new ModSettings.Values()).getAsJsonObject();
            object.getAsJsonObject("quickNavigation").getAsJsonArray("tabs").set(0,
                com.google.gson.JsonParser.parseString("{" + field + "}"));
            String original = object.toString(); Files.writeString(path, original);
            var store = new ModSettings(path); assertThrows(java.io.IOException.class, store::load);
            assertEquals(original, Files.readString(path));
        }
    }
    @Test void commandsAndPatternsAreValidated() {
        var tab = new QuickNavigationSettings.Tab(); tab.icon = "minecraft:apple";
        assertNull(tab.error());
        tab.command = "/"; assertNotNull(tab.error());
        tab.command = "/skills\n/hub"; assertNotNull(tab.error());
        tab.command = " /warp garden "; assertNull(tab.error());
        assertEquals("warp garden", QuickNavigationSettings.normalizedCommand(tab.command));
        tab.title = "["; assertNotNull(tab.error());
        tab.title = "Pets(?: \\(\\d+/\\d+\\))?"; assertNull(tab.error());
        new QuickNavigationSettings().validate();
    }
    @Test void layoutKeepsHiddenSlotsStableAndExcludesInventoryPixels() {
        var top = QuickNavigationLayout.tab(0, 100, 80, 176, 166);
        assertEquals(new QuickNavigationLayout.Bounds(100, 52, 26, 32), top);
        assertEquals(250, QuickNavigationLayout.tab(6, 100, 80, 176, 166).x());
        var bottom = QuickNavigationLayout.tab(7, 100, 80, 176, 166);
        assertEquals(242, bottom.y());
        assertFalse(QuickNavigationLayout.exposed(0, top).contains(110, 80));
        assertFalse(QuickNavigationLayout.exposed(7, bottom).contains(110, 245));
        assertTrue(QuickNavigationLayout.exposed(7, bottom).contains(110, 246));
        assertEquals(108, QuickNavigationLayout.tab(7, 100, 80, 176, 274).y() - bottom.y());
        assertThrows(IllegalArgumentException.class, () -> QuickNavigationLayout.tab(14, 0, 0, 176, 166));
        assertTrue(QuickNavigationLayout.allowed(true, true, true, false));
        assertFalse(QuickNavigationLayout.allowed(false, true, true, false));
        assertFalse(QuickNavigationLayout.allowed(true, false, true, false));
        assertFalse(QuickNavigationLayout.allowed(true, true, false, false));
        assertFalse(QuickNavigationLayout.allowed(true, true, true, true));
    }
    @Test void confirmationExpiresAndCooldownSurvivesScreenChanges() {
        var clicks = new QuickNavigationClicks();
        var doubleClick = QuickNavigationSettings.Activation.DOUBLE_CLICK;
        assertFalse(clicks.activate(7, doubleClick, 100)); assertTrue(clicks.armed(7, 999));
        assertFalse(clicks.activate(8, doubleClick, 1000)); assertFalse(clicks.armed(7, 1000));
        assertFalse(clicks.activate(8, doubleClick, 2000)); // Expired arm, re-arm.
        assertTrue(clicks.activate(8, doubleClick, 2100)); assertFalse(clicks.armed(8, 2100));
        clicks.disarm(); assertFalse(clicks.activate(0, QuickNavigationSettings.Activation.CLICK, 3099));
        assertTrue(clicks.activate(0, QuickNavigationSettings.Activation.CLICK, 3100));
        assertFalse(clicks.activate(7, doubleClick, 3200)); assertFalse(clicks.armed(7, 3200));
        assertFalse(clicks.activate(7, doubleClick, 4100)); clicks.disarm();
        assertFalse(clicks.activate(7, doubleClick, 4200)); assertTrue(clicks.activate(7, doubleClick, 4201));
    }
}

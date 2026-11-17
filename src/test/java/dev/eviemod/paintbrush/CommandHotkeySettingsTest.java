package dev.eviemod.paintbrush;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CommandHotkeySettingsTest {
    @TempDir Path directory;

    @Test void editsPersistImmediatelyWithoutApplyingOtherDraftSettingsOrSharingRows() throws Exception {
        var path = directory.resolve("settings.json");
        var store = new ModSettings(path); store.load();
        var initial = store.values().copy();
        initial.features.skyblock.commandHotkeysEnabled = true;
        initial.features.soulWhip.enabled = true;
        initial.migrations.skyblock = true;
        store.save(initial);
        var draft = store.values().copy();
        draft.features.skyblock.commandHotkeysEnabled = false;
        draft.features.soulWhip.enabled = false;
        var hotkey = new ImportedFeatures.Hotkey();
        hotkey.key = 2; hotkey.command = "/warp garden";
        draft.features.skyblock.commandHotkeys.add(hotkey);

        store.saveCommandHotkeys(draft.features.skyblock.commandHotkeys);
        assertEquals("/warp garden", store.values().features.skyblock.commandHotkeys.getFirst().command);
        var restarted = new ModSettings(path); restarted.load();
        assertEquals(2, restarted.values().features.skyblock.commandHotkeys.getFirst().key);
        assertTrue(restarted.values().features.skyblock.commandHotkeysEnabled);
        assertTrue(restarted.values().features.soulWhip.enabled);
        assertTrue(restarted.values().migrations.skyblock);
        hotkey.command = "hub";
        assertEquals("/warp garden", store.values().features.skyblock.commandHotkeys.getFirst().command);
        store.saveCommandHotkeys(draft.features.skyblock.commandHotkeys);
        restarted.load();
        assertEquals("hub", restarted.values().features.skyblock.commandHotkeys.getFirst().command);
        store.save(draft); restarted.load();
        assertEquals("hub", restarted.values().features.skyblock.commandHotkeys.getFirst().command);
        assertFalse(restarted.values().features.skyblock.commandHotkeysEnabled);
        assertFalse(restarted.values().features.soulWhip.enabled);
    }

    @Test void addingAndRemovingBindingsNeverEnablesHotkeysAndPersistsAnExplicitEmptyList() throws Exception {
        var path = directory.resolve("settings.json");
        var store = new ModSettings(path); store.load();
        var hotkeys = new ArrayList<ImportedFeatures.Hotkey>();
        hotkeys.add(new ImportedFeatures.Hotkey());
        store.saveCommandHotkeys(hotkeys); store.load();
        assertFalse(store.values().features.skyblock.commandHotkeysEnabled);
        assertEquals(1, store.values().features.skyblock.commandHotkeys.size());
        hotkeys.clear(); store.saveCommandHotkeys(hotkeys); store.load();
        assertFalse(store.values().features.skyblock.commandHotkeysEnabled);
        assertTrue(store.values().features.skyblock.commandHotkeys.isEmpty());
        assertEquals(0, store.present().getAsJsonObject("features").getAsJsonObject("skyblock")
            .getAsJsonArray("commandHotkeys").size());
    }

    @Test void failedWriteKeepsLiveBindingsIntact() throws Exception {
        var path = directory.resolve("settings.json");
        var store = new ModSettings(path); store.load();
        var hotkey = new ImportedFeatures.Hotkey(); hotkey.command = "hub";
        store.saveCommandHotkeys(List.of(hotkey));
        Files.delete(path); Files.createDirectory(path);
        Files.writeString(path.resolve("blocker"), "keep");
        hotkey.command = "warp garden";
        assertThrows(IOException.class, () -> store.saveCommandHotkeys(List.of(hotkey)));
        assertEquals("hub", store.values().features.skyblock.commandHotkeys.getFirst().command);
        assertEquals("keep", Files.readString(path.resolve("blocker")));
    }

    @Test void malformedConfigCannotBeOverwrittenByAnImmediateEdit() throws Exception {
        var path = directory.resolve("settings.json");
        Files.writeString(path, "{broken");
        var store = new ModSettings(path);
        assertThrows(IOException.class, store::load);
        assertThrows(IOException.class, () -> store.saveCommandHotkeys(List.of(new ImportedFeatures.Hotkey())));
        assertEquals("{broken", Files.readString(path));
    }

    @Test void disablingAndEnablingPreservesTheBindingAndMasterSwitch() throws Exception {
        var path = directory.resolve("settings.json");
        var store = new ModSettings(path); store.load();
        var initial = store.values().copy(); initial.features.skyblock.commandHotkeysEnabled = true; store.save(initial);
        var hotkey = new ImportedFeatures.Hotkey(); hotkey.key = 80; hotkey.command = "/warp garden";
        for (boolean disabled : new boolean[]{true, false}) {
            hotkey.disabled = disabled;
            store.saveCommandHotkeys(List.of(hotkey)); store.load();
            var saved = store.values().features.skyblock;
            assertTrue(saved.commandHotkeysEnabled);
            assertEquals(disabled, saved.commandHotkeys.getFirst().disabled);
            assertEquals(80, saved.commandHotkeys.getFirst().key);
            assertEquals("/warp garden", saved.commandHotkeys.getFirst().command);
        }
    }

    @Test void oldBindingsKeepTheirBehaviorWithoutEnablingTheMasterSwitch() throws Exception {
        var path = directory.resolve("settings.json");
        for (boolean enabled : new boolean[]{false, true}) {
            Files.writeString(path, "{\"features\":{\"skyblock\":{\"commandHotkeysEnabled\":" + enabled
                + ",\"commandHotkeys\":[{\"key\":80,\"command\":\"hub\"}]}}}");
            var store = new ModSettings(path); store.load();
            assertFalse(store.values().features.skyblock.commandHotkeys.getFirst().disabled);
            store.save(store.values().copy()); store.load();
            assertEquals(enabled, store.values().features.skyblock.commandHotkeysEnabled);
            assertFalse(store.values().features.skyblock.commandHotkeys.getFirst().disabled);
        }
    }

    @Test void malformedBindingStatePreservesTheOriginalFile() throws Exception {
        var path = directory.resolve("settings.json");
        for (String value : new String[]{"null", "0", "\"false\""}) {
            String original = "{\"features\":{\"skyblock\":{\"commandHotkeys\":[{\"disabled\":" + value + "}]}}}";
            Files.writeString(path, original);
            var store = new ModSettings(path);
            assertThrows(IOException.class, store::load);
            assertThrows(IOException.class, () -> store.saveCommandHotkeys(List.of(new ImportedFeatures.Hotkey())));
            assertEquals(original, Files.readString(path));
        }
    }
}

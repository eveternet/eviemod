package dev.eviemod.paintbrush;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ModSettingsTest {
    @TempDir Path directory;
    @Test void skinsAndTexturesAreOptInAcrossFreshLegacyAndResetConfigurations() throws Exception {
        var file = directory.resolve("eviemod.json"); var store = new ModSettings(file); store.load();
        assertFalse(store.values().helmetSkins); assertFalse(store.values().customTextures);
        Files.writeString(file, "{\"rarityBackgrounds\":false}"); store.load();
        assertFalse(store.values().helmetSkins); assertFalse(store.values().customTextures);
        var draft = store.values().copy(); draft.helmetSkins = true; draft.customTextures = true; store.save(draft);
        store.load(); assertTrue(store.values().helmetSkins); assertTrue(store.values().customTextures);
        store.save(new ModSettings.Values()); store.load();
        assertFalse(store.values().helmetSkins); assertFalse(store.values().customTextures);
    }
    @Test void persistsSettingsAndIsolatesDrafts() throws Exception {
        var file = directory.resolve("eviemod.json"); var store = new ModSettings(file); store.load();
        var draft = store.values().copy(); draft.opacity = 80; draft.shape = ModSettings.Shape.CIRCLE;
        assertEquals(45, store.values().opacity);
        store.save(draft); draft.opacity = 5;
        assertEquals(80, store.values().opacity);
        var reloaded = new ModSettings(file); reloaded.load();
        assertEquals(80, reloaded.values().opacity); assertEquals(ModSettings.Shape.CIRCLE, reloaded.values().shape);
    }
    @Test void malformedConfigCannotBeOverwrittenAndRecoversAfterReload() throws Exception {
        var file = directory.resolve("eviemod.json"); var store = new ModSettings(file);
        for (String invalid : new String[]{"null", "[]", "{", "{\"opacity\":101}", "{\"shape\":\"BAD\"}", "{\"rarityBackgrounds\":\"yes\"}", "{\"opacity\":20.5}", "{\"helmetSkins\":null}", "{\"customTextures\":\"true\"}"}) {
            Files.writeString(file, invalid);
            assertThrows(java.io.IOException.class, store::load);
            assertThrows(java.io.IOException.class, () -> store.save(new ModSettings.Values()));
            assertEquals(invalid, Files.readString(file));
        }
        Files.writeString(file, "{}"); store.load(); store.save(new ModSettings.Values()); assertNull(store.error());
    }
    @Test void legacyPaintbrushFilesAreCopiedOnceWithoutOverwritingEitherFile() throws Exception {
        var old = directory.resolve("skyshitter-colors.json"); Files.writeString(old, "legacy");
        var path = ConfigMigration.migrate(directory, "eviemod-colors.json");
        assertEquals("legacy", Files.readString(path)); assertEquals("legacy", Files.readString(old));
        Files.writeString(path, "new"); ConfigMigration.migrate(directory, "eviemod-colors.json");
        assertEquals("new", Files.readString(path)); assertEquals("legacy", Files.readString(old));
    }
}

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
    @Test void allFeatureDefaultsStayOffAcrossMissingFieldsAndReset() throws Exception {
        var file = directory.resolve("defaults.json"); var store = new ModSettings(file); store.load();
        assertAllTogglesOff(store.values());
        for (String json : new String[]{"{}", "{\"opacity\":60}",
                "{\"features\":{\"skyblock\":{\"partyCommands\":{\"warp\":{}}},\"soulWhip\":{}}}"}) {
            Files.writeString(file, json); store.load();
            assertAllTogglesOff(store.values());
            store.save(store.values().copy()); store.load();
            assertAllTogglesOff(store.values());
        }
        var draft = store.values().copy();
        draft.rarityBackgrounds = true; draft.rememberRarity = true;
        draft.features.soulWhip.enabled = true;
        draft.features.skyblock.noBarrierEffects = true;
        draft.features.skyblock.maxTenHearts = true;
        draft.features.skyblock.commandHotkeysEnabled = true;
        draft.features.skyblock.channels("warp").party = true;
        store.save(draft); store.load();
        assertTrue(store.values().rarityBackgrounds); assertTrue(store.values().rememberRarity);
        assertTrue(store.values().features.soulWhip.enabled);
        assertTrue(store.values().features.skyblock.noBarrierEffects);
        assertTrue(store.values().features.skyblock.maxTenHearts);
        assertTrue(store.values().features.skyblock.commandHotkeysEnabled);
        assertTrue(store.values().features.skyblock.channels("warp").party);
        store.save(new ModSettings.Values()); store.load();
        assertAllTogglesOff(store.values());
    }
    private static void assertAllTogglesOff(ModSettings.Values values) {
        values.features.skyblock.channels("warp");
        assertBooleansOff(new com.google.gson.Gson().toJsonTree(values), "settings");
    }
    private static void assertBooleansOff(com.google.gson.JsonElement value, String path) {
        if (value.isJsonObject()) {
            value.getAsJsonObject().entrySet().forEach(entry -> assertBooleansOff(entry.getValue(), path + "." + entry.getKey()));
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            assertFalse(value.getAsBoolean(), path);
        }
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
    @Test void texturePackBypasserDefaultsOffAndPreservesOptIn() throws Exception {
        assertFalse(new ModSettings.Values().texturePackBypasser);
        Path path = directory.resolve("bypasser.json");
        Files.writeString(path, "{}");
        var store = new ModSettings(path); store.load();
        assertFalse(store.values().texturePackBypasser);
        var draft = store.values().copy(); draft.texturePackBypasser = true; store.save(draft);
        var restarted = new ModSettings(path); restarted.load(); assertTrue(restarted.values().texturePackBypasser);
        Files.writeString(path, "{\"texturePackBypasser\":\"true\"}");
        assertThrows(java.io.IOException.class, restarted::load);
    }
}

package dev.eviemod.paintbrush;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LavaToWaterSettingsTest {
    @TempDir Path directory;

    @Test void freshExistingAndResetConfigurationsStayOff() throws Exception {
        var path = directory.resolve("settings.json");
        var store = new ModSettings(path);
        store.load();
        assertFalse(store.values().features.lavaToWater.enabled);
        assertFalse(store.values().features.lavaToWater.colorTint);
        assertFalse(store.values().features.lavaToWater.hideFog);
        assertEquals(0x3F76E4, store.values().features.lavaToWater.tintColor);
        Files.writeString(path, "{\"features\":{\"skyblock\":{\"maxTenHearts\":true}}}");
        store.load();
        assertFalse(store.values().features.lavaToWater.enabled);
        var next = store.values().copy();
        next.features.lavaToWater.enabled = true;
        store.save(next);
        store.save(new ModSettings.Values());
        store.load();
        assertFalse(store.values().features.lavaToWater.enabled);
    }

    @Test void explicitSettingsPersistWithoutChangingOtherFeatures() throws Exception {
        var store = new ModSettings(directory.resolve("settings.json"));
        store.load();
        var next = store.values().copy();
        next.features.skyblock.maxTenHearts = true;
        next.features.lavaToWater.enabled = true;
        next.features.lavaToWater.colorTint = true;
        next.features.lavaToWater.tintColor = 0x123456;
        next.features.lavaToWater.hideFog = true;
        store.save(next);
        store.load();
        var lava = store.values().features.lavaToWater;
        assertTrue(lava.enabled);
        assertTrue(lava.colorTint);
        assertEquals(0x123456, lava.tintColor);
        assertTrue(lava.hideFog);
        assertTrue(store.values().features.skyblock.maxTenHearts);
        next = store.values().copy();
        next.features.lavaToWater.enabled = false;
        store.save(next);
        store.load();
        assertFalse(store.values().features.lavaToWater.enabled);
        assertEquals(0x123456, store.values().features.lavaToWater.tintColor);
    }

    @Test void malformedSettingsArePreservedAndCannotBeOverwritten() throws Exception {
        for (String field : new String[]{"enabled", "colorTint", "hideFog"})
            for (String value : new String[]{"null", "\"true\"", "1", "[]", "{}"})
                reject("{\"" + field + "\":" + value + "}");
        for (String value : new String[]{"null", "\"123\"", "true", "-1", "16777216", "1.5", "4294967296", "[]", "{}"})
            reject("{\"tintColor\":" + value + "}");
        for (String value : new String[]{"null", "false", "[]"}) reject(value);
    }

    private void reject(String lava) throws Exception {
        var path = directory.resolve("settings.json");
        String original = "{\"features\":{\"lavaToWater\":" + lava + "}}";
        Files.writeString(path, original);
        var store = new ModSettings(path);
        assertThrows(IOException.class, store::load, original);
        assertThrows(IOException.class, () -> store.save(new ModSettings.Values()), original);
        assertEquals(original, Files.readString(path));
    }

    @Test void legacyMigrationDoesNotEnableLavaToWater() throws Exception {
        Files.createDirectories(directory.resolve("kabeewie"));
        Files.writeString(directory.resolve("kabeewie/config.json"), "{\"noBarrierEffects\":true}");
        var store = new ModSettings(directory.resolve("eviemod/settings.json"));
        store.load();
        ImportedFeatureMigration.run(store, directory);
        store.load();
        assertTrue(store.values().features.skyblock.noBarrierEffects);
        assertFalse(store.values().features.lavaToWater.enabled);
    }
}

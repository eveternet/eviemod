package dev.eviemod.paintbrush;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentSettingsTest {
    @TempDir Path directory;
    @Test void freshMissingAndResetSettingsRemainOffAndChoicesPersist() throws Exception {
        var path = directory.resolve("settings.json"); var store = new ModSettings(path);
        store.load(); defaults(store.values());
        Files.writeString(path, "{\"features\":{\"skyblock\":{\"maxTenHearts\":true}}}");
        store.load(); defaults(store.values()); assertTrue(store.values().features.skyblock.maxTenHearts);
        var next = store.values().copy();
        next.features.experiments.chronomatron = next.features.experiments.ultrasequencer = true;
        next.features.experiments.superpairs = next.features.experiments.protectIncorrectClicks = true;
        store.save(next); store.load();
        assertTrue(store.values().features.experiments.chronomatron); assertTrue(store.values().features.experiments.ultrasequencer);
        assertTrue(store.values().features.experiments.superpairs); assertTrue(store.values().features.experiments.protectIncorrectClicks);
        next = store.values().copy(); next.features.experiments = new ImportedFeatures.Experiments();
        store.save(next); store.load(); defaults(store.values());
        assertTrue(store.values().features.skyblock.maxTenHearts);
    }
    @Test void malformedSettingsPreserveFileAndPreventOverwrite() throws Exception {
        for (String value : new String[]{"null", "[]", "true", "{\"chronomatron\":null}", "{\"ultrasequencer\":1}",
            "{\"superpairs\":\"yes\"}", "{\"protectIncorrectClicks\":null}"}) {
            var path = directory.resolve("settings.json"); String source = "{\"features\":{\"experiments\":" + value + "}}";
            Files.writeString(path, source); var store = new ModSettings(path);
            assertThrows(java.io.IOException.class, store::load);
            assertThrows(java.io.IOException.class, () -> store.save(new ModSettings.Values()));
            assertEquals(source, Files.readString(path));
        }
    }
    private static void defaults(ModSettings.Values values) {
        assertFalse(values.features.experiments.chronomatron); assertFalse(values.features.experiments.ultrasequencer);
        assertFalse(values.features.experiments.superpairs); assertFalse(values.features.experiments.protectIncorrectClicks);
    }
}

package dev.eviemod.paintbrush;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CellsAlignmentSettingsTest {
    @TempDir Path directory;

    @Test void defaultMissingAndResetStayOffAndExplicitChoicePersists() throws Exception {
        var path = directory.resolve("settings.json");
        var store = new ModSettings(path);
        store.load();
        assertFalse(store.values().features.dungeons.cellsAlignmentTimer);
        Files.writeString(path, "{\"features\":{\"dungeons\":{\"partyFinderAlert\":true,\"partyFinderSubtitle\":\"Full!\"}}}");
        store.load();
        assertFalse(store.values().features.dungeons.cellsAlignmentTimer);
        var next = store.values().copy();
        next.features.dungeons.cellsAlignmentTimer = true;
        store.save(next);
        store.load();
        assertTrue(store.values().features.dungeons.cellsAlignmentTimer);
        assertTrue(store.values().features.dungeons.partyFinderAlert);
        assertEquals("Full!", store.values().features.dungeons.partyFinderSubtitle);
        next = store.values().copy();
        next.features.dungeons.cellsAlignmentTimer = false;
        store.save(next);
        store.load();
        assertFalse(store.values().features.dungeons.cellsAlignmentTimer);
        assertFalse(new ImportedFeatures.Dungeons().cellsAlignmentTimer);
    }

    @Test void malformedSettingNeverOverwritesUserData() throws Exception {
        for (String value : new String[]{"null", "\"true\"", "1", "[]", "{}"}) {
            var path = directory.resolve("settings.json");
            String original = "{\"features\":{\"dungeons\":{\"cellsAlignmentTimer\":" + value + "}}}";
            Files.writeString(path, original);
            var store = new ModSettings(path);
            assertThrows(IOException.class, store::load);
            assertThrows(IOException.class, () -> store.save(new ModSettings.Values()));
            assertEquals(original, Files.readString(path));
        }
    }
}

package dev.eviemod.paintbrush;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PartyFinderSettingsTest {
    @TempDir Path directory;

    @Test void freshExistingAndResetSettingsRemainOptIn() throws Exception {
        var path = directory.resolve("settings.json");
        var store = new ModSettings(path);
        store.load();
        assertDefaults(store.values());
        Files.writeString(path, "{\"features\":{\"dungeons\":{\"announceCrit\":true}}}");
        store.load();
        assertDefaults(store.values());
        assertTrue(store.values().features.dungeons.announceCrit);
        var next = store.values().copy();
        next.features.dungeons.partyFinderAlert = true;
        next.features.dungeons.partyFinderSubtitle = "Let's go!";
        store.save(next);
        store.load();
        assertTrue(store.values().features.dungeons.partyFinderAlert);
        assertEquals("Let's go!", store.values().features.dungeons.partyFinderSubtitle);
        assertTrue(store.values().features.dungeons.announceCrit);
        next = store.values().copy();
        next.features.dungeons = new ImportedFeatures.Dungeons();
        store.save(next);
        store.load();
        assertDefaults(store.values());
    }

    @Test void invalidSettingsPreserveTheOriginalFile() throws Exception {
        for (String fields : new String[]{"\"partyFinderAlert\":\"yes\"", "\"partyFinderAlert\":null",
            "\"partyFinderSubtitle\":null", "\"partyFinderSubtitle\":123"}) {
            var path = directory.resolve("settings.json");
            String original = "{\"features\":{\"dungeons\":{" + fields + "}}}";
            Files.writeString(path, original);
            var store = new ModSettings(path);
            assertThrows(IOException.class, store::load);
            assertThrows(IOException.class, () -> store.save(new ModSettings.Values()));
            assertEquals(original, Files.readString(path));
        }
    }

    private static void assertDefaults(ModSettings.Values values) {
        assertFalse(values.features.dungeons.partyFinderAlert);
        assertEquals("Party is full!", values.features.dungeons.partyFinderSubtitle);
    }
}

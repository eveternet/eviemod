package dev.eviemod.paintbrush;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DungeonsSettingsTest {
    @TempDir Path directory;
    @Test void defaultsMigrationAndRoundTrip() throws Exception {
        var path = directory.resolve("settings.json"); var store = new ModSettings(path);
        store.load(); assertFalse(store.values().features.dungeons.announceCrit);
        assertFalse(store.values().features.dungeons.announceCritPartyChat);
        Files.writeString(path, "{\"features\":{\"skyblock\":{\"noammScoreSync\":true}}}");
        store.load(); assertFalse(store.values().features.dungeons.announceCrit);
        assertTrue(store.values().features.skyblock.noammScoreSync);
        var next = store.values().copy(); next.features.dungeons.announceCrit = true;
        next.features.dungeons.announceCritPartyChat = true; next.features.dungeons.announceCritTemplate = "Crit: {damage}";
        store.save(next); store.load();
        assertTrue(store.values().features.dungeons.announceCrit);
        assertTrue(store.values().features.dungeons.announceCritPartyChat);
        assertEquals("Crit: {damage}", store.values().features.dungeons.announceCritTemplate);
        assertTrue(store.values().features.skyblock.noammScoreSync);
        assertFalse(new ModSettings.Values().features.dungeons.announceCrit);
    }
    @Test void malformedConfigurationIsPreserved() throws Exception {
        for (String invalid : new String[]{"null", "{\"announceCrit\":\"yes\"}", "{\"announceCritPartyChat\":1}", "{\"announceCritTemplate\":null}"}) {
            var path = directory.resolve("settings.json");
            String original = "{\"features\":{\"dungeons\":" + invalid + "}}";
            Files.writeString(path, original); var store = new ModSettings(path);
            assertThrows(java.io.IOException.class, store::load);
            assertThrows(java.io.IOException.class, () -> store.save(new ModSettings.Values()));
            assertEquals(original, Files.readString(path));
        }
    }
}

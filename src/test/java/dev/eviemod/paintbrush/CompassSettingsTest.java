package dev.eviemod.paintbrush;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CompassSettingsTest {
    @TempDir Path directory;
    @Test void freshExistingAndResetSettingsRemainOffAndExplicitChoicesPersist() throws Exception {
        Path path = directory.resolve("settings.json");
        var store = new ModSettings(path); store.load();
        assertFalse(store.values().features.skyblock.wishingCompass);
        Files.writeString(path, "{\"features\":{\"skyblock\":{\"maxTenHearts\":true}}}"); store.load();
        assertFalse(store.values().features.skyblock.wishingCompass);
        var draft = store.values().copy(); draft.features.skyblock.wishingCompass = true;
        store.save(draft); store.load(); assertTrue(store.values().features.skyblock.wishingCompass);
        assertTrue(store.values().features.skyblock.maxTenHearts);
        draft = store.values().copy(); draft.features.skyblock.wishingCompass = false;
        draft.choose("skyblock.wishingCompass"); store.save(draft); store.load();
        assertFalse(store.values().features.skyblock.wishingCompass);
        assertTrue(store.values().features.skyblock.maxTenHearts);
    }
    @Test void malformedOptInIsRejectedAndOriginalFilePreserved() throws Exception {
        Path path = directory.resolve("settings.json");
        for (String value : new String[]{"null", "\"true\"", "1", "{}"}) {
            String original = "{\"features\":{\"skyblock\":{\"wishingCompass\":" + value + "}}}";
            Files.writeString(path, original);
            var store = new ModSettings(path); assertThrows(IOException.class, store::load);
            assertThrows(IOException.class, () -> store.save(new ModSettings.Values()));
            assertEquals(original, Files.readString(path));
        }
    }
}

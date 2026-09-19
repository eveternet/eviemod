package dev.eviemod.paintbrush;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class NoammScoreSettingsTest {
    @TempDir Path directory;
    @Test void missingResetAndPersistedExplicitChoice() throws Exception {
        Path path = directory.resolve("settings.json"); var settings = new ModSettings(path);
        settings.load(); assertFalse(settings.values().features.skyblock.noammScoreSync);
        Files.writeString(path, "{\"features\":{\"skyblock\":{\"noBarrierEffects\":false}}}");
        settings.load(); assertFalse(settings.values().features.skyblock.noammScoreSync);
        var next = settings.values().copy(); next.features.skyblock.noammScoreSync = true;
        next.choose("skyblock.noammScoreSync"); settings.save(next); settings.load();
        assertTrue(settings.values().features.skyblock.noammScoreSync);
        assertFalse(new ModSettings.Values().features.skyblock.noammScoreSync);
    }
    @Test void malformedValueIsNotOverwritten() throws Exception {
        Path path = directory.resolve("settings.json"); String invalid = "{\"features\":{\"skyblock\":{\"noammScoreSync\":\"yes\"}}}";
        Files.writeString(path, invalid); var settings = new ModSettings(path);
        assertThrows(java.io.IOException.class, settings::load); assertEquals(invalid, Files.readString(path));
    }
}

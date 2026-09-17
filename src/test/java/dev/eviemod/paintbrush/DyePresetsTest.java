package dev.eviemod.paintbrush;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DyePresetsTest {
    @TempDir Path directory;

    @Test void catalogResolvesNamesIdsAndAllFrames() {
        assertEquals(66, DyePresets.all().size());
        assertEquals(24, DyePresets.all().stream().filter(DyePresets.Preset::animated).count());
        for (var preset : DyePresets.all()) {
            assertSame(preset, DyePresets.find(preset.id()));
            assertSame(preset, DyePresets.find(preset.name()));
            assertEquals(preset.id(), DyePresets.normalize(preset.name()));
            assertFalse(preset.frames().isEmpty());
            for (int frame : preset.frames()) assertTrue(frame >= 0 && frame <= 0xffffff);
        }
        assertEquals("DYE_PURE_BLACK", DyePresets.normalize("pure black"));
        assertEquals("TENTACLE_DYE", DyePresets.normalize("Tentacle Dye"));
        assertEquals(0, DyePresets.colorAt("DYE_PURE_BLACK", 10));
        assertEquals("#FF88CC", DyePresets.normalize("ff88cc"));
        assertThrows(IllegalArgumentException.class, () -> DyePresets.normalize("Imaginary Dye"));
    }

    @Test void animationUsesSharedClockAndLoops() {
        var preset = DyePresets.find("Rose");
        assertTrue(preset.animated());
        for (int i = 0; i < preset.frames().size(); i++) {
            assertEquals(preset.frames().get(i), preset.colorAt(i * 100L));
            assertEquals(preset.colorAt(i * 100L), preset.colorAt(i * 100L + 99));
        }
        assertEquals(preset.colorAt(0), preset.colorAt(preset.frames().size() * 100L));
        assertTrue(preset.frames().stream().distinct().count() > 1);
    }

    @Test void savesNamedAnimationsAlongsideLegacyHexAndRejectsUnknownDyes() throws Exception {
        var id = java.util.UUID.randomUUID();
        var old = java.util.UUID.randomUUID();
        Path path = directory.resolve("colors.json");
        Files.writeString(path, "{\"" + old + "\":\"#000000\"}");
        var store = new ColorOverrides(path); store.load();
        store.setValue(id, "Rose Dye");
        var reloaded = new ColorOverrides(path); reloaded.load();
        assertEquals("DYE_ROSE", reloaded.getValue(id));
        assertEquals(0, reloaded.get(old));
        String saved = Files.readString(path);
        assertThrows(IllegalArgumentException.class, () -> store.setValue(id, "unknown"));
        assertEquals(saved, Files.readString(path));
        Files.writeString(path, "{\"" + id + "\":\"DYE_UNKNOWN\"}");
        assertThrows(java.io.IOException.class, reloaded::load);
        assertEquals("DYE_ROSE", reloaded.getValue(id));
        reloaded.setValue(id, null); store.load();
        assertNull(store.getValue(id));
        assertEquals(0, store.get(old));
    }
}

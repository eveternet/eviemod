package dev.eviemod.paintbrush;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EquipAnimationSettingsTest {
    @TempDir Path directory;

    @Test void existingSoulWhipChoicesRemainTheSingleSettingAcrossSaveAndReload() throws Exception {
        var path = directory.resolve("settings.json");
        for (boolean enabled : new boolean[]{false, true}) {
            Files.writeString(path, "{\"features\":{\"soulWhip\":{\"enabled\":" + enabled + "}}}");
            var store = new ModSettings(path);
            store.load();
            assertEquals(enabled, store.values().features.soulWhip.enabled);
            var next = store.values().copy();
            next.features.soulWhip.enabled = !enabled;
            next.choose("soulWhip.enabled");
            store.save(next);
            store.load();
            assertEquals(!enabled, store.values().features.soulWhip.enabled);
            assertEquals(java.util.Set.of("soulWhip"), store.present().getAsJsonObject("features").keySet());
        }
    }

    @Test void missingSettingsAndResetStayOff() throws Exception {
        var path = directory.resolve("settings.json");
        var store = new ModSettings(path);
        store.load();
        assertFalse(store.values().features.soulWhip.enabled);
        Files.writeString(path, "{\"features\":{\"skyblock\":{\"maxTenHearts\":true}}}");
        store.load();
        assertFalse(store.values().features.soulWhip.enabled);
        var next = store.values().copy();
        next.features.soulWhip.enabled = true;
        store.save(next);
        store.save(new ModSettings.Values());
        store.load();
        assertFalse(store.values().features.soulWhip.enabled);
    }

    @Test void legacyKabeewieTrueAndFalseMigrateWithoutOverridingLaterChoices() throws Exception {
        Files.createDirectories(directory.resolve("kabeewie"));
        for (boolean enabled : new boolean[]{false, true}) {
            Files.writeString(directory.resolve("kabeewie/config.json"), "{\"soulWhipFix\":" + enabled + "}");
            var store = new ModSettings(directory.resolve(enabled + "-settings.json"));
            store.load();
            ImportedFeatureMigration.run(store, directory);
            assertEquals(enabled, store.values().features.soulWhip.enabled);
            var next = store.values().copy();
            next.features.soulWhip.enabled = !enabled;
            store.save(next);
            store.load();
            ImportedFeatureMigration.run(store, directory);
            assertEquals(!enabled, store.values().features.soulWhip.enabled);
        }
    }
}

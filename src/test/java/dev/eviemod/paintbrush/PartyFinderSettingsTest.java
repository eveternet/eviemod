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
        next.features.dungeons.partyFinderSound = true;
        next.features.dungeons.partyFinderSoundId = "minecraft:entity.experience_orb.pickup";
        next.features.dungeons.partyFinderSoundPitch = 1.5F;
        next.features.dungeons.partyFinderSoundVolume = 0.35F;
        store.save(next);
        store.load();
        assertTrue(store.values().features.dungeons.partyFinderAlert);
        assertEquals("Let's go!", store.values().features.dungeons.partyFinderSubtitle);
        assertTrue(store.values().features.dungeons.partyFinderSound);
        assertEquals("minecraft:entity.experience_orb.pickup", store.values().features.dungeons.partyFinderSoundId);
        assertEquals(1.5F, store.values().features.dungeons.partyFinderSoundPitch);
        assertEquals(0.35F, store.values().features.dungeons.partyFinderSoundVolume);
        assertTrue(store.values().features.dungeons.announceCrit);
        next = store.values().copy();
        next.features.dungeons = new ImportedFeatures.Dungeons();
        store.save(next);
        store.load();
        assertDefaults(store.values());
    }

    @Test void invalidSettingsPreserveTheOriginalFile() throws Exception {
        for (String fields : new String[]{"\"partyFinderAlert\":\"yes\"", "\"partyFinderAlert\":null",
            "\"partyFinderSubtitle\":null", "\"partyFinderSubtitle\":123",
            "\"partyFinderSound\":null", "\"partyFinderSound\":\"yes\"",
            "\"partyFinderSoundId\":null", "\"partyFinderSoundId\":123",
            "\"partyFinderSoundPitch\":null", "\"partyFinderSoundPitch\":\"1\"",
            "\"partyFinderSoundPitch\":0.4", "\"partyFinderSoundPitch\":2.1",
            "\"partyFinderSoundPitch\":1e999", "\"partyFinderSoundVolume\":null",
            "\"partyFinderSoundVolume\":\"1\"", "\"partyFinderSoundVolume\":true",
            "\"partyFinderSoundVolume\":-0.1", "\"partyFinderSoundVolume\":1.1",
            "\"partyFinderSoundVolume\":1e999"}) {
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
        assertSoundDefaults(values);
    }

    private static void assertSoundDefaults(ModSettings.Values values) {
        assertFalse(values.features.dungeons.partyFinderSound);
        assertEquals("minecraft:block.note_block.pling", values.features.dungeons.partyFinderSoundId);
        assertEquals(1.0F, values.features.dungeons.partyFinderSoundPitch);
        assertEquals(1.0F, values.features.dungeons.partyFinderSoundVolume);
    }

    @Test void existingSubtitleUsersDoNotSilentlyGainSound() throws Exception {
        var path = directory.resolve("settings.json");
        Files.writeString(path, "{\"features\":{\"dungeons\":{\"partyFinderAlert\":true,\"partyFinderSubtitle\":\"Full!\"}}}");
        var store = new ModSettings(path);
        store.load();
        assertTrue(store.values().features.dungeons.partyFinderAlert);
        assertEquals("Full!", store.values().features.dungeons.partyFinderSubtitle);
        assertSoundDefaults(store.values());
        store.save(store.values().copy());
        store.load();
        assertSoundDefaults(store.values());
    }

    @Test void unknownSoundNamesAndBoundaryLevelsRemainEditable() throws Exception {
        var store = new ModSettings(directory.resolve("settings.json"));
        store.load();
        for (String id : new String[]{"", "not a sound", "minecraft:noteblock"}) {
            var next = store.values().copy();
            next.features.dungeons.partyFinderSoundId = id;
            next.features.dungeons.partyFinderSoundVolume = 0.0F;
            next.features.dungeons.partyFinderSoundPitch = 0.5F;
            store.save(next);
            store.load();
            assertEquals(id, store.values().features.dungeons.partyFinderSoundId);
            assertEquals(0.0F, store.values().features.dungeons.partyFinderSoundVolume);
            assertEquals(0.5F, store.values().features.dungeons.partyFinderSoundPitch);
        }
    }
}

package dev.eviemod.paintbrush;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ImportedFeatureMigrationTest {
    private static boolean retiredPestValue(ImportedFeatures.Garden garden) {
        return new com.google.gson.Gson().toJsonTree(garden).getAsJsonObject().get("forceFinnegan").getAsBoolean();
    }
    @TempDir Path config;
    private Path target() { return config.resolve("eviemod/settings.json"); }
    private ModSettings store() throws IOException { var store = new ModSettings(target()); store.load(); return store; }
    private Path legacy(String json) throws IOException {
        Path file = config.resolve("kabeewie/config.json"); Files.createDirectories(file.getParent()); Files.writeString(file, json); return file;
    }
    private static final String ALL = """
        {"soulWhipFix":false,"noBarrierEffects":false,"maxTenHearts":false,"commandHotkeysEnabled":false,
         "commandHotkeys":[{"key":0,"command":"/warp garden"},{"key":-1,"command":""},{"key":290,"command":"p warp"}],
         "partyCommands":{"warp":{"party":false,"guild":true,"coop":true}},
         "garden":{"mouseLock":false,"forceFinnegan":false,"teleportPlot":24}}
        """;

    @Test void freshInstallDefaultsOffWithoutInventingMigrations() throws Exception {
        var store = store(); ImportedFeatureMigration.run(store, config);
        assertFalse(store.values().features.soulWhip.enabled);
        assertFalse(store.values().features.skyblock.maxTenHearts);
        assertFalse(store.values().features.skyblock.noBarrierEffects);
        assertFalse(store.values().features.skyblock.commandHotkeysEnabled);
        assertFalse(new ImportedFeatures.Channels().party);
        assertFalse(new ImportedFeatures.Channels().guild);
        assertFalse(store.values().features.garden.mouseLock);
        assertFalse(retiredPestValue(store.values().features.garden));
        assertEquals(1, store.values().features.garden.teleportPlot);
        assertFalse(store.values().migrations.skyblock); assertFalse(store.values().migrations.garden); assertFalse(store.values().migrations.soulWhip);
        assertFalse(Files.exists(target()));
        store.save(store.values().copy());
        assertTrue(store.present().getAsJsonObject("features").isEmpty());
    }
    @Test void allThreeGroupsMigrateAndPreserveEverySettingAndLegacyBytes() throws Exception {
        Path file = legacy(ALL); byte[] original = Files.readAllBytes(file); var modified = Files.getLastModifiedTime(file);
        var store = store(); ImportedFeatureMigration.run(store, config); store = store();
        assertTrue(store.values().migrations.skyblock); assertTrue(store.values().migrations.garden); assertTrue(store.values().migrations.soulWhip);
        var values = store.values().features;
        assertFalse(values.soulWhip.enabled); assertFalse(values.skyblock.noBarrierEffects); assertFalse(values.skyblock.maxTenHearts);
        assertFalse(values.skyblock.commandHotkeysEnabled); assertFalse(values.garden.mouseLock); assertFalse(retiredPestValue(values.garden));
        assertEquals(24, values.garden.teleportPlot);
        assertEquals(3, values.skyblock.commandHotkeys.size()); assertEquals(0, values.skyblock.commandHotkeys.get(0).key);
        assertEquals("/warp garden", values.skyblock.commandHotkeys.get(0).command); assertEquals(-1, values.skyblock.commandHotkeys.get(1).key);
        assertEquals(290, values.skyblock.commandHotkeys.get(2).key); assertEquals("p warp", values.skyblock.commandHotkeys.get(2).command);
        var channels = values.skyblock.channels("warp"); assertFalse(channels.party); assertTrue(channels.guild); assertTrue(channels.coop);
        assertArrayEquals(original, Files.readAllBytes(file)); assertEquals(modified, Files.getLastModifiedTime(file));
    }
    @Test void absentGroupsRemainEligibleAfterAnotherGroupMigratesAndRestart() throws Exception {
        legacy("{\"soulWhipFix\":false}"); var store = store(); ImportedFeatureMigration.run(store, config);
        assertTrue(store.values().migrations.soulWhip); assertFalse(store.values().migrations.skyblock); assertFalse(store.values().migrations.garden);
        assertFalse(store.present().getAsJsonObject("features").has("skyblock"));
        legacy(ALL); store = store(); ImportedFeatureMigration.run(store, config); store = store();
        assertFalse(store.values().features.skyblock.maxTenHearts); assertEquals(24, store.values().features.garden.teleportPlot);
        assertTrue(store.values().migrations.skyblock); assertTrue(store.values().migrations.garden);
    }
    @Test void twoGroupsMigrateWithoutMarkingTheThird() throws Exception {
        legacy("{\"noBarrierEffects\":false,\"garden\":{\"mouseLock\":true}}");
        var store = store(); ImportedFeatureMigration.run(store, config);
        assertTrue(store.values().migrations.skyblock); assertTrue(store.values().migrations.garden); assertFalse(store.values().migrations.soulWhip);
        assertTrue(store.values().features.garden.mouseLock);
    }
    @Test void standaloneGardenIsSupportedAndNeverModified() throws Exception {
        Path old = config.resolve("garden-tools.json"); String original = "{\"mouseLock\":true,\"forceFinnegan\":true,\"teleportPlot\":100}";
        Files.writeString(old, original); var store = store(); ImportedFeatureMigration.run(store, config);
        assertTrue(store.values().features.garden.mouseLock); assertTrue(retiredPestValue(store.values().features.garden));
        assertEquals(24, store.values().features.garden.teleportPlot); assertTrue(store.values().migrations.garden);
        assertFalse(store.values().migrations.skyblock); assertEquals(original, Files.readString(old));
    }
    @Test void combinedGardenReplacesStandaloneIncludingMissingFieldDefaults() throws Exception {
        Files.writeString(config.resolve("garden-tools.json"), "{\"mouseLock\":true,\"forceFinnegan\":true,\"teleportPlot\":15}");
        legacy("{\"garden\":{\"teleportPlot\":2}}"); var store = store(); ImportedFeatureMigration.run(store, config);
        assertFalse(store.values().features.garden.mouseLock); assertFalse(retiredPestValue(store.values().features.garden)); assertEquals(2, store.values().features.garden.teleportPlot);
    }
    @Test void existingValuesWinIndividuallyIncludingFalseDefaultAndEmptyList() throws Exception {
        Files.createDirectories(target().getParent()); Files.writeString(target(), """
            {"opacity":70,"rarityBackgrounds":false,"features":{
             "skyblock":{"maxTenHearts":true,"commandHotkeys":[],"partyCommands":{"warp":{"guild":false}}},
             "garden":{"mouseLock":false,"teleportPlot":1},"soulWhip":{"enabled":true}}}
            """);
        legacy(ALL); var store = store(); ImportedFeatureMigration.run(store, config); store = store();
        assertEquals(70, store.values().opacity); assertFalse(store.values().rarityBackgrounds);
        assertTrue(store.values().features.skyblock.maxTenHearts); assertTrue(store.values().features.soulWhip.enabled);
        assertTrue(store.values().features.skyblock.commandHotkeys.isEmpty()); assertFalse(store.values().features.skyblock.noBarrierEffects);
        assertEquals(1, store.values().features.garden.teleportPlot); assertFalse(store.values().features.garden.mouseLock);
        var channels = store.values().features.skyblock.channels("warp"); assertFalse(channels.guild); assertFalse(channels.party); assertTrue(channels.coop);
    }
    @Test void completedGroupsNeverReadChangedOrMalformedLegacyAgain() throws Exception {
        legacy(ALL); var store = store(); ImportedFeatureMigration.run(store, config);
        var next = store.values().copy(); next.features.skyblock.maxTenHearts = true; next.features.garden.teleportPlot = 7; next.features.soulWhip.enabled = true; store.save(next);
        legacy("{broken"); byte[] saved = Files.readAllBytes(target());
        store = store(); ImportedFeatureMigration.run(store, config);
        assertArrayEquals(saved, Files.readAllBytes(target())); assertTrue(store.values().features.skyblock.maxTenHearts);
        assertEquals(7, store.values().features.garden.teleportPlot); assertTrue(store.values().features.soulWhip.enabled);
    }
    @Test void malformedGroupDoesNotPreventOtherGroupsMigrating() throws Exception {
        legacy("{\"soulWhipFix\":false,\"maxTenHearts\":\"false\",\"garden\":{\"forceFinnegan\":true}}");
        var store = store(); ImportedFeatureMigration.run(store, config);
        assertTrue(store.values().migrations.garden); assertTrue(store.values().migrations.soulWhip); assertFalse(store.values().migrations.skyblock);
        assertFalse(store.values().features.skyblock.maxTenHearts); assertTrue(retiredPestValue(store.values().features.garden));
        legacy(ALL); store = store(); ImportedFeatureMigration.run(store, config); assertFalse(store.values().features.skyblock.maxTenHearts);
    }
    @Test void malformedInputsDoNotChangeLastValidDestinationOrSources() throws Exception {
        var store = store(); var next = store.values().copy(); next.opacity = 66; store.save(next);
        for (String invalid : new String[]{"{", "null", "[]", "{\"soulWhipFix\":null}", "{\"garden\":[]}",
                "{\"garden\":{\"teleportPlot\":1.5}}", "{\"commandHotkeys\":{}}", "{\"commandHotkeys\":[null]}",
                "{\"commandHotkeys\":[{\"key\":2147483648}]}", "{\"commandHotkeys\":[{\"command\":null}]}",
                "{\"partyCommands\":{\"warp\":null}}"}) {
            Path file = legacy(invalid); byte[] before = Files.readAllBytes(target());
            ImportedFeatureMigration.run(store, config);
            assertArrayEquals(before, Files.readAllBytes(target()), invalid); assertEquals(invalid, Files.readString(file));
            assertEquals(66, store.values().opacity); assertFalse(store.values().migrations.skyblock); assertFalse(store.values().migrations.garden); assertFalse(store.values().migrations.soulWhip);
        }
    }
    @Test void malformedDestinationCannotBeOverwrittenByMigration() throws Exception {
        Files.createDirectories(target().getParent()); Files.writeString(target(), "{\"features\":null}");
        var store = new ModSettings(target()); assertThrows(IOException.class, store::load); legacy(ALL);
        ImportedFeatureMigration.run(store, config); assertEquals("{\"features\":null}", Files.readString(target()));
        assertFalse(store.values().migrations.skyblock);
    }
    @Test void incompleteOlderEntriesDefaultOff() throws Exception {
        legacy("{\"commandHotkeys\":[{}, {\"command\":\"warp garden\"}],\"partyCommands\":{\"warp\":{\"guild\":true}},\"garden\":{}}");
        var store = store(); ImportedFeatureMigration.run(store, config);
        assertEquals(-1, store.values().features.skyblock.commandHotkeys.get(0).key); assertEquals("", store.values().features.skyblock.commandHotkeys.get(0).command);
        assertFalse(store.values().features.skyblock.channels("warp").party); assertTrue(store.values().features.skyblock.channels("warp").guild);
        assertFalse(store.values().features.skyblock.channels("warp").coop); assertEquals(1, store.values().features.garden.teleportPlot);
    }
    @Test void failedDataSaveKeepsMemoryFileAndMarkerUnchanged() throws Exception {
        var initial = store(); var next = initial.values().copy(); next.opacity = 73; initial.save(next); byte[] before = Files.readAllBytes(target());
        var failing = new ModSettings(target()) {
            @Override void save(Values values, String group) throws IOException { throw new IOException("fixture write failure"); }
        };
        failing.load(); legacy(ALL); ImportedFeatureMigration.run(failing, config);
        assertArrayEquals(before, Files.readAllBytes(target())); assertEquals(73, failing.values().opacity);
        assertFalse(failing.values().features.skyblock.maxTenHearts); assertFalse(failing.values().migrations.skyblock);
    }
    @Test void failedMarkerSaveLeavesValidValuesAndRetriesWithoutOverwritingThem() throws Exception {
        legacy("{\"soulWhipFix\":false}");
        var failing = new ModSettings(target()) {
            @Override void save(Values values, String group) throws IOException {
                if (group == null) throw new IOException("fixture marker failure");
                super.save(values, group);
            }
        };
        failing.load(); ImportedFeatureMigration.run(failing, config);
        var restarted = store(); assertFalse(restarted.values().features.soulWhip.enabled); assertFalse(restarted.values().migrations.soulWhip);
        legacy("{\"soulWhipFix\":true}"); ImportedFeatureMigration.run(restarted, config);
        assertFalse(restarted.values().features.soulWhip.enabled); assertTrue(restarted.values().migrations.soulWhip);
    }
    @Test void realFilesystemFailureDoesNotPublishCandidateOrMarker() throws Exception {
        Files.writeString(config.resolve("eviemod"), "blocked directory"); legacy(ALL);
        var store = store(); ImportedFeatureMigration.run(store, config);
        assertFalse(store.values().migrations.skyblock); assertTrue(store.values().features.skyblock.maxTenHearts);
        assertEquals("blocked directory", Files.readString(config.resolve("eviemod")));
    }
    @Test void existingEviemodFilesAreCopiedIntoDirectoryOnlyOnce() throws Exception {
        for (String name : new String[]{"eviemod.json", "eviemod-colors.json", "eviemod-names.json", "eviemod-paintbrush.json", "eviemod-helmet-skins.json", "eviemod-hypixel-pack.json"}) {
            Path old = config.resolve(name); Files.writeString(old, "old-" + name);
            Path migrated = ConfigMigration.migrate(config, name);
            assertEquals(config.resolve("eviemod"), migrated.getParent()); assertEquals("old-" + name, Files.readString(migrated));
            Files.writeString(migrated, "new"); ConfigMigration.migrate(config, name);
            assertEquals("new", Files.readString(migrated)); assertEquals("old-" + name, Files.readString(old));
        }
    }
    @Test void nativeGardenKeysAreInTheSameTransactionAndOptionsRemainUnchanged() throws Exception {
        Path instanceConfig = config.resolve("instance/config"); Files.createDirectories(instanceConfig);
        Path options = instanceConfig.resolveSibling("options.txt");
        String original = "version:4790\nkey_key.gardentools.tptoplot:key.keyboard.o\nkey_key.gardentools.loadouts:key.mouse.middle\n";
        Files.writeString(options, original);
        var store = new ModSettings(instanceConfig.resolve("eviemod/settings.json")); store.load();
        ImportedFeatureMigration.run(store, instanceConfig); store.load();
        assertTrue(store.values().migrations.garden); assertFalse(store.values().migrations.skyblock);
        assertEquals("key.keyboard.o", store.values().features.garden.keys.get("tptoplot"));
        assertEquals("key.mouse.middle", store.values().features.garden.keys.get("loadouts"));
        assertEquals("key.keyboard.unknown", store.values().features.garden.keys.get("setspawn"));
        assertEquals(original, Files.readString(options));
        Files.writeString(options, "key_key.gardentools.tptoplot:key.keyboard.x\n");
        ImportedFeatureMigration.run(store, instanceConfig);
        assertEquals("key.keyboard.o", store.values().features.garden.keys.get("tptoplot"));
    }
    @Test void malformedNativeGardenKeyDoesNotCompleteGardenOrChangeEitherFile() throws Exception {
        Path instanceConfig = config.resolve("instance/config"); Files.createDirectories(instanceConfig.resolve("kabeewie"));
        Files.writeString(instanceConfig.resolve("kabeewie/config.json"), "{\"garden\":{\"teleportPlot\":10},\"soulWhipFix\":false}");
        Path options = instanceConfig.resolveSibling("options.txt"); Files.writeString(options, "key_key.gardentools.tptoplot:???\n");
        var store = new ModSettings(instanceConfig.resolve("eviemod/settings.json")); store.load();
        ImportedFeatureMigration.run(store, instanceConfig);
        assertFalse(store.values().migrations.garden); assertTrue(store.values().migrations.soulWhip);
        assertEquals(1, store.values().features.garden.teleportPlot);
        assertEquals("key_key.gardentools.tptoplot:???\n", Files.readString(options));
    }
    @Test void explicitDefaultValuedUiChoiceWinsOverLaterLegacyValue() throws Exception {
        var store = store(); var draft = store.values().copy();
        draft.features.soulWhip.enabled = false; draft.choose("soulWhip.enabled");
        draft.features.skyblock.commandHotkeys.clear(); draft.choose("skyblock.commandHotkeys");
        store.save(draft); legacy("{\"soulWhipFix\":true}"); store = store(); ImportedFeatureMigration.run(store, config);
        assertFalse(store.values().features.soulWhip.enabled); assertTrue(store.values().features.skyblock.commandHotkeys.isEmpty());
    }

    @Test void retiredPestValuesAreOpaqueAndSurviveUnrelatedSettingsSaves() throws Exception {
        Files.createDirectories(target().getParent());
        Files.writeString(target(), "{\"features\":{\"garden\":{\"forceFinnegan\":true,\"keys\":{\"loadouts\":\"key.keyboard.l\"}}},\"migrations\":{\"garden\":true}}");
        var store = store(); var draft = store.values().copy(); draft.opacity = 60; store.save(draft); store.load();
        var garden = store.present().getAsJsonObject("features").getAsJsonObject("garden");
        assertTrue(garden.get("forceFinnegan").getAsBoolean());
        assertEquals("key.keyboard.l", garden.getAsJsonObject("keys").get("loadouts").getAsString());
        assertTrue(store.values().migrations.garden);
        assertThrows(NoSuchFieldException.class, () -> ImportedFeatures.Garden.class.getField("forceFinnegan"));
    }

}

package dev.eviemod.paintbrush;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Cross-feature persistence boundaries shared by the three independently developed PRs. */
class CombinedFeatureSettingsTest {
    @TempDir Path directory;

    @Test void legacyMigrationAndUnrelatedSaveKeepEveryNewFeatureOff() throws Exception {
        var config = directory.resolve("config");
        Files.createDirectories(config.resolve("kabeewie"));
        Files.writeString(config.resolve("kabeewie/config.json"), "{\"maxTenHearts\":true}");
        var store = new ModSettings(config.resolve("eviemod/settings.json"));
        store.load();
        ImportedFeatureMigration.run(store, config);
        var draft = store.values().copy(); draft.rarityBackgrounds = true;
        store.save(draft); store.load();
        assertTrue(store.values().features.skyblock.maxTenHearts);
        assertTrue(store.values().rarityBackgrounds);
        assertFalse(store.values().quickNavigation.enabled);
        assertFalse(store.values().features.skyblock.wishingCompass);
        defaults(store.values().features.experiments);
    }

    @Test void navigationEditorSaveAndParentSavePreserveBothOtherFeatureChoices() throws Exception {
        var store = new ModSettings(directory.resolve("settings.json")); store.load();
        var parentDraft = store.values().copy();
        parentDraft.features.skyblock.wishingCompass = true;
        parentDraft.choose("skyblock.wishingCompass");
        parentDraft.features.experiments.chronomatron = true;
        parentDraft.features.experiments.superpairs = true;
        parentDraft.features.experiments.protectIncorrectClicks = true;
        parentDraft.choose("experiments.chronomatron");
        parentDraft.choose("experiments.superpairs");
        parentDraft.choose("experiments.protectIncorrectClicks");
        parentDraft.features.skyblock.maxTenHearts = true;
        // Opening the tab editor commits the parent draft; Done saves its copy.
        store.save(parentDraft);
        var editorDraft = parentDraft.copy();
        editorDraft.quickNavigation.enabled = true;
        editorDraft.quickNavigation.tabs.get(0).command = "/skills";
        editorDraft.quickNavigation.tabs.get(0).tooltip = "Custom skills";
        store.save(editorDraft);
        parentDraft.quickNavigation = store.values().copy().quickNavigation;
        store.save(parentDraft); store.load();
        var actual = store.values();
        assertTrue(actual.quickNavigation.enabled);
        assertEquals("Custom skills", actual.quickNavigation.tabs.get(0).tooltip);
        assertTrue(actual.features.skyblock.wishingCompass);
        assertTrue(actual.features.experiments.chronomatron);
        assertTrue(actual.features.experiments.superpairs);
        assertTrue(actual.features.experiments.protectIncorrectClicks);
        assertFalse(actual.features.experiments.ultrasequencer);
        assertTrue(actual.features.skyblock.maxTenHearts);
    }

    @Test void resettingOneFeaturePreservesOthersAndFullResetRemainsOff() throws Exception {
        var store = new ModSettings(directory.resolve("settings.json")); store.load();
        var draft = store.values().copy();
        draft.quickNavigation.enabled = true;
        draft.features.skyblock.wishingCompass = true;
        draft.features.experiments.chronomatron = true;
        draft.features.experiments.ultrasequencer = true;
        draft.features.experiments.superpairs = true;
        draft.features.experiments.protectIncorrectClicks = true;
        store.save(draft);
        draft = store.values().copy(); draft.features.experiments = new ImportedFeatures.Experiments();
        store.save(draft); store.load();
        assertTrue(store.values().quickNavigation.enabled);
        assertTrue(store.values().features.skyblock.wishingCompass);
        defaults(store.values().features.experiments);
        store.save(new ModSettings.Values()); store.load();
        assertFalse(store.values().quickNavigation.enabled);
        assertFalse(store.values().features.skyblock.wishingCompass);
        defaults(store.values().features.experiments);
    }

    private static void defaults(ImportedFeatures.Experiments values) {
        assertFalse(values.chronomatron); assertFalse(values.ultrasequencer);
        assertFalse(values.superpairs); assertFalse(values.protectIncorrectClicks);
    }
}

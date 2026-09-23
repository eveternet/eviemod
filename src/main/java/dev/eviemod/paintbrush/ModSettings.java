package dev.eviemod.paintbrush;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.*;

class ModSettings {
    enum Shape { SQUARE, CIRCLE }
    static final class Values {
        ImportedFeatures features = new ImportedFeatures();
        ImportedFeatures.Migrations migrations = new ImportedFeatures.Migrations();
        boolean rarityBackgrounds = false;
        int opacity = 45;
        Shape shape = Shape.SQUARE;
        boolean rememberRarity = false;
        boolean texturePackBypasser = false;
        // Legacy keys retained for persistence compatibility; per-item choices are the opt-in now.
        boolean helmetSkins = false;
        boolean customTextures = false;
        transient java.util.Set<String> featureChoices = new java.util.HashSet<>();
        void choose(String path) { featureChoices.add(path); }
        Values copy() { return GSON.fromJson(GSON.toJson(this), Values.class); }
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private Values values = new Values();
    private String error;
    private com.google.gson.JsonObject present = new com.google.gson.JsonObject();
    com.google.gson.JsonObject present() { return present.deepCopy(); }
    ModSettings(Path path) { this.path = path; }
    Values values() { return values; }
    String error() { return error; }
    void load() throws IOException {
        try {
            ConfigMigration.prepare(path);
            if (!Files.exists(path)) { values = new Values(); present = new com.google.gson.JsonObject(); error = null; return; }
            var json = JsonParser.parseString(Files.readString(path));
            if (!json.isJsonObject()) throw new IllegalArgumentException("Expected a settings object");
            var object = json.getAsJsonObject();
            removeRetiredGardenFields(object);
            for (String key : new String[]{"rarityBackgrounds", "rememberRarity", "helmetSkins", "customTextures", "texturePackBypasser"}) {
                if (object.has(key) && (!object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isBoolean()))
                    throw new IllegalArgumentException("Invalid " + key);
            }
            if (object.has("opacity") && (!object.get("opacity").isJsonPrimitive()
                || !object.getAsJsonPrimitive("opacity").isNumber()
                || object.get("opacity").getAsDouble() != object.get("opacity").getAsInt()))
                throw new IllegalArgumentException("Invalid opacity");
            Values loaded = GSON.fromJson(json, Values.class);
            ImportedFeatureMigration.validate(object);
            validate(loaded); present = object.deepCopy(); values = loaded; error = null;
        } catch (RuntimeException | IOException e) {
            error = "Could not load eviemod/settings.json. Original file preserved. Fix it, then use /eviemod reload.";
            throw new IOException(error, e);
        }
    }
    private static void validate(Values value) {
        if (value == null || value.features == null || value.features.skyblock == null || value.features.garden == null
            || value.features.dungeons == null || value.features.dungeons.announceCritTemplate == null
            || value.features.soulWhip == null || value.features.skyblock.commandHotkeys == null
            || value.features.skyblock.partyCommands == null || value.features.garden.keys == null || value.migrations == null || value.shape == null || value.opacity < 0 || value.opacity > 100)
            throw new IllegalArgumentException("Invalid rarity background settings");
    }
    private static void removeRetiredGardenFields(com.google.gson.JsonObject root) {
        if (!root.has("features") || !root.get("features").isJsonObject()) return;
        var features = root.getAsJsonObject("features");
        if (!features.has("garden") || !features.get("garden").isJsonObject()) return;
        var garden = features.getAsJsonObject("garden");
        garden.remove("forceFinnegan");
        if (garden.has("keys") && garden.get("keys").isJsonObject())
            garden.getAsJsonObject("keys").remove("loadouts");
    }
    void save(Values next) throws IOException { save(next, null); }
    void save(Values next, String importedGroup) throws IOException {
        if (error != null) throw new IOException(error);
        ConfigMigration.prepare(path);
        validate(next);
        var json = GSON.toJsonTree(next).getAsJsonObject();
        ImportedFeatureMigration.validate(json);
        var prior = GSON.toJsonTree(values).getAsJsonObject();
        var explicit = present.has("features") ? present.getAsJsonObject("features") : new com.google.gson.JsonObject();
        var sparse = ImportedFeatureMigration.changed(explicit, prior.getAsJsonObject("features"), json.getAsJsonObject("features"));
        if (importedGroup != null) sparse.add(importedGroup, json.getAsJsonObject("features").get(importedGroup).deepCopy());
        for (String choice : next.featureChoices) {
            String[] parts = choice.split("\\.");
            var source = json.getAsJsonObject("features"); var destination = sparse;
            for (int i = 0; i < parts.length - 1; i++) {
                source = source.getAsJsonObject(parts[i]);
                if (!destination.has(parts[i])) destination.add(parts[i], new com.google.gson.JsonObject());
                destination = destination.getAsJsonObject(parts[i]);
            }
            destination.add(parts[parts.length - 1], source.get(parts[parts.length - 1]).deepCopy());
        }
        json.add("features", sparse);
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "eviemod-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(json) + "\n");
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            values = next.copy(); present = json.deepCopy();
        } finally { Files.deleteIfExists(temporary); }
    }
}

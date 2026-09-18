package dev.eviemod.paintbrush;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.*;

final class ModSettings {
    enum Shape { SQUARE, CIRCLE }
    static final class Values {
        ImportedFeatures features = new ImportedFeatures();
        ImportedFeatures.Migrations migrations = new ImportedFeatures.Migrations();
        boolean rarityBackgrounds = true;
        int opacity = 45;
        Shape shape = Shape.SQUARE;
        boolean rememberRarity = true;
        boolean texturePackBypasser = false;
        // Legacy keys retained for persistence compatibility; per-item choices are the opt-in now.
        boolean helmetSkins = false;
        boolean customTextures = false;
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
            if (!Files.exists(path)) { values = new Values(); error = null; return; }
            var json = JsonParser.parseString(Files.readString(path));
            if (!json.isJsonObject()) throw new IllegalArgumentException("Expected a settings object");
            var object = json.getAsJsonObject();
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
            error = "Could not load eviemod.json. Original file preserved. Fix it, then use /eviemod reload.";
            throw new IOException(error, e);
        }
    }
    private static void validate(Values value) {
        if (value == null || value.features == null || value.migrations == null || value.shape == null || value.opacity < 0 || value.opacity > 100)
            throw new IllegalArgumentException("Invalid rarity background settings");
    }
    void save(Values next) throws IOException { save(next, null); }
    void save(Values next, String importedGroup) throws IOException {
        if (error != null) throw new IOException(error);
        validate(next);
        var json = GSON.toJsonTree(next).getAsJsonObject();
        ImportedFeatureMigration.validate(json);
        var prior = GSON.toJsonTree(values).getAsJsonObject();
        var explicit = present.has("features") ? present.getAsJsonObject("features") : new com.google.gson.JsonObject();
        var sparse = ImportedFeatureMigration.changed(explicit, prior.getAsJsonObject("features"), json.getAsJsonObject("features"));
        if (importedGroup != null) sparse.add(importedGroup, json.getAsJsonObject("features").get(importedGroup).deepCopy());
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

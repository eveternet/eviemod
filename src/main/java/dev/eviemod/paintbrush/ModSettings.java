package dev.eviemod.paintbrush;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.*;

final class ModSettings {
    enum Shape { SQUARE, CIRCLE }
    static final class Values {
        boolean rarityBackgrounds = true;
        int opacity = 45;
        Shape shape = Shape.SQUARE;
        boolean rememberRarity = true;
        Values copy() { return GSON.fromJson(GSON.toJson(this), Values.class); }
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private Values values = new Values();
    private String error;
    ModSettings(Path path) { this.path = path; }
    Values values() { return values; }
    String error() { return error; }
    void load() throws IOException {
        try {
            if (!Files.exists(path)) { values = new Values(); error = null; return; }
            var json = JsonParser.parseString(Files.readString(path));
            if (!json.isJsonObject()) throw new IllegalArgumentException("Expected a settings object");
            var object = json.getAsJsonObject();
            for (String key : new String[]{"rarityBackgrounds", "rememberRarity"}) {
                if (object.has(key) && (!object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isBoolean()))
                    throw new IllegalArgumentException("Invalid " + key);
            }
            if (object.has("opacity") && (!object.get("opacity").isJsonPrimitive()
                || !object.getAsJsonPrimitive("opacity").isNumber()
                || object.get("opacity").getAsDouble() != object.get("opacity").getAsInt()))
                throw new IllegalArgumentException("Invalid opacity");
            Values loaded = GSON.fromJson(json, Values.class);
            validate(loaded); values = loaded; error = null;
        } catch (RuntimeException | IOException e) {
            error = "Could not load eviemod.json. Original file preserved. Fix it, then use /eviemod reload.";
            throw new IOException(error, e);
        }
    }
    private static void validate(Values value) {
        if (value == null || value.shape == null || value.opacity < 0 || value.opacity > 100)
            throw new IllegalArgumentException("Invalid rarity background settings");
    }
    void save(Values next) throws IOException {
        if (error != null) throw new IOException(error);
        validate(next);
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "eviemod-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(next) + "\n");
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            values = next.copy();
        } finally { Files.deleteIfExists(temporary); }
    }
}

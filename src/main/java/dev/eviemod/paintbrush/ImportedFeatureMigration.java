package dev.eviemod.paintbrush;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.LoggerFactory;

/** The only reader of Kabeewie's files. Each group commits its values before its marker. */
final class ImportedFeatureMigration {
    private static final Gson GSON = new Gson();
    static void run(ModSettings store, Path configDirectory) {
        for (String group : List.of("skyblock", "garden", "soulWhip")) {
            if (GSON.toJsonTree(store.values().migrations).getAsJsonObject().get(group).getAsBoolean()) continue;
            try {
                JsonObject legacy = readGroup(configDirectory, group);
                if (legacy == null) continue; // Absent groups remain eligible for a later migration.
                var raw = store.present();
                JsonObject features = objectOrEmpty(raw, "features");
                JsonObject existing = objectOrEmpty(features, group);
                JsonObject merged = mergeMissing(existing, legacy);
                var candidate = GSON.toJsonTree(store.values()).getAsJsonObject();
                candidate.getAsJsonObject("features").add(group, merged);
                validate(candidate);
                ModSettings.Values next = GSON.fromJson(candidate, ModSettings.Values.class);
                store.save(next, group);
                // A failure here leaves the imported values valid and their keys authoritative on retry.
                next = store.values().copy();
                var markers = GSON.toJsonTree(next.migrations).getAsJsonObject();
                markers.addProperty(group, true);
                next.migrations = GSON.fromJson(markers, ImportedFeatures.Migrations.class);
                store.save(next);
                LoggerFactory.getLogger("eviemod").info("Migrated legacy {} settings; source files preserved", group);
            } catch (IOException | RuntimeException e) {
                LoggerFactory.getLogger("eviemod").warn("Could not migrate legacy {} settings; will retry, source files preserved", group, e);
            }
        }
    }

    private static JsonObject readGroup(Path directory, String group) throws IOException {
        Path combinedPath = directory.resolve("kabeewie/config.json");
        JsonObject combined = Files.exists(combinedPath) ? read(combinedPath) : new JsonObject();
        JsonObject result = new JsonObject();
        if (group.equals("soulWhip")) {
            if (!combined.has("soulWhipFix")) return null;
            result.add("enabled", combined.get("soulWhipFix").deepCopy());
        } else if (group.equals("skyblock")) {
            for (String key : List.of("noBarrierEffects", "maxTenHearts", "commandHotkeysEnabled", "commandHotkeys", "partyCommands"))
                if (combined.has(key)) result.add(key, combined.get(key).deepCopy());
            if (result.isEmpty()) return null;
        } else {
            Path garden = directory.resolve("garden-tools.json");
            // The combined mod loads garden-tools.json, then replaces that entire object when garden exists.
            if (combined.has("garden")) result = object(combined.get("garden"), "garden").deepCopy();
            else if (Files.exists(garden)) result = read(garden);
            else if (!hasGardenKeys(directory)) return null;
            // Native key choices are part of the Garden migration transaction, before its marker.
            result.add("keys", gardenKeys(directory));
            if (result.has("teleportPlot")) {
                integer(result.get("teleportPlot"), "teleportPlot");
                result.addProperty("teleportPlot", Math.clamp(result.get("teleportPlot").getAsInt(), 1, 24));
            }
        }
        var wrapper = new JsonObject();
        var features = new JsonObject(); features.add(group, result); wrapper.add("features", features);
        validate(wrapper); // Validate source even when an existing destination value would win.
        return result;
    }

    private static final List<String> GARDEN_KEYS = List.of("tptoplot", "setspawn", "warp_garden", "loadouts");
    private static boolean hasGardenKeys(Path directory) throws IOException {
        Path options = directory.resolveSibling("options.txt");
        if (!Files.exists(options)) return false;
        return Files.readAllLines(options).stream().anyMatch(line -> GARDEN_KEYS.stream()
            .anyMatch(key -> line.startsWith("key_key.gardentools." + key + ":")));
    }
    private static JsonObject gardenKeys(Path directory) throws IOException {
        var keys = new JsonObject();
        for (String key : GARDEN_KEYS) keys.addProperty(key, "key.keyboard.unknown");
        Path options = directory.resolveSibling("options.txt");
        if (Files.exists(options)) for (String line : Files.readAllLines(options)) for (String key : GARDEN_KEYS) {
            String prefix = "key_key.gardentools." + key + ":";
            if (line.startsWith(prefix)) {
                String value = line.substring(prefix.length());
                if (!value.matches("key\\.(keyboard|mouse)\\.[a-z0-9._-]+") && !value.matches("scancode\\.[0-9]+"))
                    throw new IllegalArgumentException("Unsupported legacy Garden key binding: " + key);
                keys.addProperty(key, value);
            }
        }
        return keys;
    }

    static JsonObject read(Path path) throws IOException {
        return object(JsonParser.parseString(Files.readString(path)), path.toString());
    }
    private static JsonObject object(JsonElement value, String name) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Expected object: " + name);
        return value.getAsJsonObject();
    }
    private static JsonObject objectOrEmpty(JsonObject parent, String key) {
        return parent.has(key) ? object(parent.get(key), key) : new JsonObject();
    }
    static JsonObject mergeMissing(JsonObject existing, JsonObject legacy) {
        JsonObject result = existing.deepCopy();
        for (var entry : legacy.entrySet()) {
            String key = entry.getKey();
            if (!result.has(key)) result.add(key, entry.getValue().deepCopy());
            else if (result.get(key).isJsonObject() && entry.getValue().isJsonObject())
                result.add(key, mergeMissing(result.getAsJsonObject(key), entry.getValue().getAsJsonObject()));
        }
        return result;
    }
    /** Keep absent groups sparse so saving another group cannot masquerade as an explicit choice. */
    static JsonObject changed(JsonObject explicit, JsonObject before, JsonObject after) {
        JsonObject result = explicit.deepCopy();
        for (var entry : after.entrySet()) {
            String key = entry.getKey(); JsonElement value = entry.getValue();
            if (value.isJsonObject() && before.has(key) && before.get(key).isJsonObject()) {
                JsonObject child = changed(objectOrEmpty(explicit, key), before.getAsJsonObject(key), value.getAsJsonObject());
                if (!child.isEmpty() || explicit.has(key)) result.add(key, child);
            } else if (explicit.has(key) || !value.equals(before.get(key))) result.add(key, value.deepCopy());
        }
        return result;
    }
    static void validate(JsonObject root) {
        if (root.has("migrations")) {
            var markers = object(root.get("migrations"), "migrations");
            booleans(markers, "skyblock", "garden", "soulWhip");
        }
        if (!root.has("features")) return;
        JsonObject features = object(root.get("features"), "features");
        if (features.has("soulWhip")) booleans(object(features.get("soulWhip"), "soulWhip"), "enabled");
        if (features.has("garden")) {
            var garden = object(features.get("garden"), "garden");
            booleans(garden, "mouseLock", "forceFinnegan");
            if (garden.has("teleportPlot")) {
                integer(garden.get("teleportPlot"), "teleportPlot");
                int plot = garden.get("teleportPlot").getAsInt();
                if (plot < 1 || plot > 24) throw new IllegalArgumentException("Invalid teleportPlot");
            }
            if (garden.has("keys")) for (var entry : object(garden.get("keys"), "keys").entrySet()) string(entry.getValue(), "key binding");
        }
        if (features.has("skyblock")) {
            var skyblock = object(features.get("skyblock"), "skyblock");
            booleans(skyblock, "noBarrierEffects", "maxTenHearts", "commandHotkeysEnabled", "noammScoreSync");
            if (skyblock.has("commandHotkeys")) {
                if (!skyblock.get("commandHotkeys").isJsonArray()) throw new IllegalArgumentException("Invalid commandHotkeys");
                for (var entry : skyblock.getAsJsonArray("commandHotkeys")) {
                    var hotkey = object(entry, "hotkey");
                    if (hotkey.has("key")) integer(hotkey.get("key"), "hotkey.key");
                    if (hotkey.has("command")) string(hotkey.get("command"), "hotkey.command");
                }
            }
            if (skyblock.has("partyCommands")) for (var entry : object(skyblock.get("partyCommands"), "partyCommands").entrySet())
                booleans(object(entry.getValue(), "party channel"), "party", "guild", "coop");
        }
    }
    private static void booleans(JsonObject object, String... keys) {
        for (String key : keys) if (object.has(key) && (!object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isBoolean()))
            throw new IllegalArgumentException("Invalid boolean: " + key);
    }
    private static void integer(JsonElement value, String name) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Invalid integer: " + name);
        try { value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Invalid integer: " + name, e); }
    }
    private static void string(JsonElement value, String name) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid string: " + name);
    }
}

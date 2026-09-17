package dev.eviemod.paintbrush;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class NameOverrides {
    private final Path file;
    private volatile Map<UUID, StyledName> overrides = Map.of();

    public NameOverrides(Path file) { this.file = file; }

    public static void validate(String name) {
        if (name == null || name.isBlank() || name.length() > 256
                || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x00a7 || c == 0x2028 || c == 0x2029)) {
            throw new IllegalArgumentException("Use a plain-text name of 1–256 characters, without line breaks or formatting codes.");
        }
    }

    public StyledName getStyle(UUID uuid) { return uuid == null ? null : overrides.get(uuid); }

    public String get(UUID uuid) { var value = getStyle(uuid); return value == null ? null : value.text(); }

    public Component resolve(ItemStack stack, Component original) {
        if (overrides.isEmpty()) return original;
        StyledName custom = getStyle(SkyBlockUuid.read(stack));
        return custom == null ? original : custom.render(original.getStyle());
    }

    public synchronized void load() throws IOException {
        if (!Files.exists(file)) { overrides = Map.of(); return; }
        Map<UUID, StyledName> loaded = new HashMap<>();
        try (var reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : root.entrySet()) {
                UUID uuid = SkyBlockUuid.parse(entry.getKey());
                if (uuid == null) throw new IllegalArgumentException("Invalid item UUID");
                StyledName name;
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                    name = StyledName.plain(entry.getValue().getAsString());
                } else if (entry.getValue().isJsonObject()) {
                    name = StyledName.fromJson(entry.getValue().getAsJsonObject());
                } else throw new IllegalArgumentException("Invalid name entry");
                validate(name.text());
                loaded.put(uuid, name);
            }
        } catch (RuntimeException e) {
            throw new IOException("Invalid Paint Brush name config; original file preserved", e);
        }
        overrides = Map.copyOf(loaded);
    }

    public synchronized void set(UUID uuid, String name) throws IOException {
        setStyle(uuid, name == null ? null : StyledName.plain(name));
    }

    public synchronized void setStyle(UUID uuid, StyledName name) throws IOException {
        if (uuid == null) throw new IllegalArgumentException("An item UUID is required");
        Map<UUID, StyledName> next = new HashMap<>(overrides);
        if (name == null) next.remove(uuid); else { validate(name.text()); next.put(uuid, name); }
        // Commit to memory only after the new configuration is safely saved.
        Map<String, JsonObject> json = new TreeMap<>();
        next.forEach((key, value) -> json.put(key.toString(), value.toJson()));
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "paintbrush-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            overrides = Map.copyOf(next);
        } finally { Files.deleteIfExists(temporary); }
    }
}

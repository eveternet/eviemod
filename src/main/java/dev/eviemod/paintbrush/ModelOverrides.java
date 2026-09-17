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
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class ModelOverrides {
    private final Path file;
    private volatile Map<UUID, Identifier> overrides = Map.of();

    public ModelOverrides(Path file) { this.file = file; }

    public Identifier get(UUID uuid) { return uuid == null ? null : overrides.get(uuid); }

    public Identifier resolve(ItemStack stack, Identifier original) {
        if (overrides.isEmpty()) return original;
        Identifier custom = get(SkyBlockUuid.read(stack));
        return custom == null ? original : custom;
    }

    public synchronized void load() throws IOException {
        if (!Files.exists(file)) { overrides = Map.of(); return; }
        Map<UUID, Identifier> loaded = new HashMap<>();
        try (var reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : root.entrySet()) {
                UUID uuid = SkyBlockUuid.parse(entry.getKey());
                if (uuid == null || !entry.getValue().isJsonPrimitive()
                        || !entry.getValue().getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Invalid UUID or model entry: " + entry.getKey());
                }
                Identifier model = Identifier.tryParse(entry.getValue().getAsString());
                if (model == null) throw new IllegalArgumentException("Invalid model: " + entry.getValue());
                loaded.put(uuid, model);
            }
        } catch (RuntimeException e) {
            throw new IOException("Invalid Paint Brush config; original file preserved", e);
        }
        overrides = Map.copyOf(loaded);
    }

    public synchronized void set(UUID uuid, Identifier model) throws IOException {
        if (uuid == null) throw new IllegalArgumentException("An item UUID is required");
        Map<UUID, Identifier> next = new HashMap<>(overrides);
        if (model == null) next.remove(uuid); else next.put(uuid, model);
        // Commit to memory only after the new configuration is safely saved.
        Map<String, String> json = new TreeMap<>();
        next.forEach((key, value) -> json.put(key.toString(), value.toString()));
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

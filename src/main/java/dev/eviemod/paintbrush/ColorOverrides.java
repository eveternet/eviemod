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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

public final class ColorOverrides {
    private final Path file;
    private final java.util.function.LongSupplier clock;
    private volatile Map<UUID, String> overrides = Map.of();

    public ColorOverrides(Path file) { this(file, System::currentTimeMillis); }
    ColorOverrides(Path file, java.util.function.LongSupplier clock) { this.file = file; this.clock = clock; }

    public static boolean isLeatherArmor(ItemStack stack) {
        return stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE)
            || stack.is(Items.LEATHER_LEGGINGS) || stack.is(Items.LEATHER_BOOTS);
    }

    public static boolean isDyeable(ItemStack stack) {
        return isLeatherArmor(stack) || stack.is(Items.LEATHER_HORSE_ARMOR) || stack.is(Items.WOLF_ARMOR)
            || stack.is(net.minecraft.tags.ItemTags.CAULDRON_CAN_REMOVE_DYE);
    }

    public static Integer parseHex(String value) {
        if (value == null || !value.matches("#?[0-9a-fA-F]{6}")) return null;
        return Integer.parseInt(value.startsWith("#") ? value.substring(1) : value, 16);
    }

    public static String format(int rgb) { return String.format("#%06X", rgb); }

    public String getValue(UUID uuid) { return uuid == null ? null : overrides.get(uuid); }

    public Integer get(UUID uuid) {
        String value = getValue(uuid);
        return value == null ? null : DyePresets.colorAt(value, clock.getAsLong());
    }

    public int resolve(ItemStack stack, int original) {
        return resolve(stack, original, false);
    }

    // Called only by dye rendering: a selected replacement model supplies its own dye tint.
    public int resolve(ItemStack stack, int original, boolean hasModelOverride) {
        if (overrides.isEmpty() || (!hasModelOverride && !isDyeable(stack))) return original;
        Integer custom = get(SkyBlockUuid.read(stack));
        return custom == null ? original : 0xff000000 | custom;
    }

    public synchronized void load() throws IOException {
        ConfigMigration.prepare(file);
        if (!Files.exists(file)) { overrides = Map.of(); return; }
        Map<UUID, String> loaded = new HashMap<>();
        try (var reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : root.entrySet()) {
                UUID uuid = SkyBlockUuid.parse(entry.getKey());
                if (uuid == null || !entry.getValue().isJsonPrimitive()
                        || !entry.getValue().getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Invalid UUID or color entry: " + entry.getKey());
                }
                String rgb = DyePresets.normalize(entry.getValue().getAsString());
                if (rgb == null) throw new IllegalArgumentException("Invalid color: " + entry.getValue());
                loaded.put(uuid, rgb);
            }
        } catch (RuntimeException e) {
            throw new IOException("Invalid Paint Brush color config; original file preserved", e);
        }
        overrides = Map.copyOf(loaded);
    }

    public synchronized void set(UUID uuid, Integer rgb) throws IOException {
        if (uuid == null) throw new IllegalArgumentException("An item UUID is required");
        if (rgb != null && (rgb < 0 || rgb > 0xffffff)) throw new IllegalArgumentException("Color must be a 24-bit RGB value");
        setValue(uuid, rgb == null ? null : format(rgb));
    }

    public synchronized void setValue(UUID uuid, String value) throws IOException {
        ConfigMigration.prepare(file);
        if (uuid == null) throw new IllegalArgumentException("An item UUID is required");
        String rgb = DyePresets.normalize(value);
        Map<UUID, String> next = new HashMap<>(overrides);
        if (rgb == null) next.remove(uuid); else next.put(uuid, rgb);
        // Commit to memory only after the new configuration is safely saved.
        Map<String, String> json = new TreeMap<>();
        next.forEach((key, color) -> json.put(key.toString(), color));
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

package dev.eviemod.paintbrush;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bundled NEU dye palette snapshot; no runtime network requests. */
public final class DyePresets {
    public record Preset(String id, String name, List<Integer> frames) {
        public Preset { frames = List.copyOf(frames); }
        public boolean animated() { return frames.size() > 1; }
        public int colorAt(long millis) {
            // Play the sampled palette at two ticks per frame; one clock keeps armor in sync.
            return frames.get((int) Math.floorMod(millis / 100L, (long) frames.size()));
        }
    }
    private static final List<Preset> ALL = load();
    private static final Map<String, Preset> INDEX = index();
    private DyePresets() {}
    public static List<Preset> all() { return ALL; }
    public static List<String> names() { return ALL.stream().map(Preset::name).toList(); }
    public static Preset find(String input) {
        if (input == null) return null;
        return INDEX.get(input.strip().toLowerCase(Locale.ROOT));
    }
    private static Map<String, Preset> index() {
        Map<String, Preset> result = new HashMap<>();
        for (Preset p : ALL) {
            result.put(p.id().toLowerCase(Locale.ROOT), p);
            result.put(p.name().toLowerCase(Locale.ROOT), p);
            result.put(p.name().replaceFirst(" Dye$", "").toLowerCase(Locale.ROOT), p);
        }
        return Map.copyOf(result);
    }
    public static String normalize(String input) {
        if (input == null || input.isBlank()) return null;
        Integer rgb = ColorOverrides.parseHex(input);
        if (rgb != null) return ColorOverrides.format(rgb);
        Preset preset = find(input);
        if (preset == null) throw new IllegalArgumentException("Choose a Hypixel dye or enter six hex digits.");
        return preset.id();
    }
    public static int colorAt(String value, long millis) {
        Integer rgb = ColorOverrides.parseHex(value);
        if (rgb != null) return rgb;
        Preset preset = find(value);
        if (preset == null) throw new IllegalArgumentException("Unknown dye: " + value);
        return preset.colorAt(millis);
    }
    public static String display(String value) {
        Preset preset = find(value);
        return preset == null ? value : preset.name();
    }
    private static List<Preset> load() {
        try (var stream = Objects.requireNonNull(DyePresets.class.getResourceAsStream("/assets/eviemod/dyes.json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            List<Preset> result = new ArrayList<>();
            for (var entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                List<Integer> frames = new ArrayList<>();
                if (entry.getValue().isJsonArray()) {
                    for (var frame : entry.getValue().getAsJsonArray()) frames.add(Objects.requireNonNull(ColorOverrides.parseHex(frame.getAsString())));
                } else frames.add(Objects.requireNonNull(ColorOverrides.parseHex(entry.getValue().getAsString())));
                if (frames.isEmpty()) throw new IllegalArgumentException("Empty dye palette");
                String base = entry.getKey().replaceFirst("^DYE_", "").replaceFirst("_DYE$", "").toLowerCase(Locale.ROOT);
                String name = Arrays.stream(base.split("_")).map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                    .collect(java.util.stream.Collectors.joining(" ")) + " Dye";
                result.add(new Preset(entry.getKey(), name, frames));
            }
            return result.stream().sorted(Comparator.comparing(Preset::name)).toList();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
}

package dev.eviemod.paintbrush;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** A literal name with optional RGB endpoints; never parses commands or click events. */
public record StyledName(String text, Integer start, Integer end, boolean bold, boolean italic,
                         boolean obfuscated, boolean underlined, boolean strikethrough, java.util.List<StyledName> segments) {
    public StyledName {
        validateSegmentText(text);
        segments = java.util.List.copyOf(segments);
        if (!segments.isEmpty() && (segments.stream().anyMatch(n -> !n.segments().isEmpty())
                || !segments.stream().map(StyledName::text).collect(java.util.stream.Collectors.joining()).equals(text)))
            throw new IllegalArgumentException("Invalid styled name segments");
        if (start != null && (start < 0 || start > 0xffffff)
                || end != null && (end < 0 || end > 0xffffff) || start == null && end != null)
            throw new IllegalArgumentException("Choose valid six-digit gradient colors.");
    }

    public StyledName(String text, Integer start, Integer end, boolean bold, boolean italic,
                      boolean obfuscated, boolean underlined, boolean strikethrough) {
        this(text, start, end, bold, italic, obfuscated, underlined, strikethrough, java.util.List.of());
    }

    public static StyledName plain(String text) {
        NameOverrides.validate(text);
        return new StyledName(text, null, null, false, false, false, false, false);
    }

    public Component render(Style original) {
        if (!segments.isEmpty()) {
            var result = Component.empty();
            segments.forEach(part -> result.append(part.render(original)));
            return result;
        }
        Style style = Style.EMPTY.withColor(original.getColor()).withBold(bold).withItalic(italic)
            .withObfuscated(obfuscated).withUnderlined(underlined).withStrikethrough(strikethrough);
        if (start == null) return Component.literal(text).withStyle(style);
        if (end == null) return Component.literal(text).withStyle(style.withColor(start));
        int[] points = text.codePoints().toArray();
        var result = Component.empty();
        for (int i = 0; i < points.length; i++) {
            float t = points.length <= 1 ? 0 : (float) i / (points.length - 1);
            int rgb = interpolate(start, end, t);
            result.append(Component.literal(new String(Character.toChars(points[i]))).withStyle(style.withColor(rgb)));
        }
        return result;
    }

    private static int interpolate(int start, int end, float t) {
        int rgb = 0;
        for (int shift : new int[] {16, 8, 0}) {
            int a = start >> shift & 255, b = end >> shift & 255;
            rgb |= Math.round(a + (b - a) * t) << shift;
        }
        return rgb;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("text", text);
        if (start != null) json.addProperty("start", ColorOverrides.format(start));
        if (end != null) json.addProperty("end", ColorOverrides.format(end));
        json.addProperty("bold", bold); json.addProperty("italic", italic);
        json.addProperty("obfuscated", obfuscated); json.addProperty("underlined", underlined);
        json.addProperty("strikethrough", strikethrough);
        if (!segments.isEmpty()) {
            var array = new com.google.gson.JsonArray(); segments.forEach(part -> array.add(part.toJson()));
            json.add("segments", array);
        }
        return json;
    }

    public static StyledName fromJson(JsonObject json) {
        if (!json.has("text") || !json.get("text").isJsonPrimitive() || !json.getAsJsonPrimitive("text").isString())
            throw new IllegalArgumentException("Name text is required");
        java.util.List<StyledName> segments = new java.util.ArrayList<>();
        if (json.has("segments")) {
            for (var part : json.getAsJsonArray("segments")) {
                if (!part.isJsonObject() || part.getAsJsonObject().has("segments"))
                    throw new IllegalArgumentException("Invalid name segment");
                segments.add(fromJson(part.getAsJsonObject()));
            }
        }
        return new StyledName(json.get("text").getAsString(), color(json, "start"), color(json, "end"),
            flag(json, "bold"), flag(json, "italic"), flag(json, "obfuscated"), flag(json, "underlined"), flag(json, "strikethrough"), segments);
    }

    private static void validateSegmentText(String text) {
        if (text == null || text.isEmpty() || text.length() > 256
            || text.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x00a7 || c == 0x2028 || c == 0x2029))
            throw new IllegalArgumentException("Invalid name text");
    }
    private static Integer color(JsonObject json, String key) {
        if (!json.has(key)) return null;
        if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString()) throw new IllegalArgumentException("Invalid " + key);
        Integer rgb = ColorOverrides.parseHex(json.get(key).getAsString());
        if (rgb == null) throw new IllegalArgumentException("Invalid " + key);
        return rgb;
    }
    private static boolean flag(JsonObject json, String key) {
        if (!json.has(key)) return false;
        if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isBoolean()) throw new IllegalArgumentException("Invalid " + key);
        return json.get(key).getAsBoolean();
    }
}

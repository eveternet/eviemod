package dev.eviemod.paintbrush;

import com.google.gson.JsonElement;
import net.minecraft.resources.Identifier;

public final class ItemAppearance {
    private ItemAppearance() {}
    public static Identifier previewModel(Identifier original, String override) {
        // Identifier.tryParse("") accepts an empty path; it is not an absent override.
        if (override == null || override.isBlank()) return original;
        Identifier parsed = Identifier.tryParse(override);
        return parsed == null ? original : parsed;
    }
    public static boolean hasDyeTint(JsonElement element) {
        if (element.isJsonObject()) {
            var object = element.getAsJsonObject();
            var type = object.get("type");
            if (type != null && type.isJsonPrimitive() && type.getAsJsonPrimitive().isString()
                && (type.getAsString().equals("minecraft:dye") || type.getAsString().equals("dye"))) return true;
            return object.entrySet().stream().anyMatch(entry -> hasDyeTint(entry.getValue()));
        }
        if (element.isJsonArray()) for (var child : element.getAsJsonArray()) if (hasDyeTint(child)) return true;
        return false;
    }
}

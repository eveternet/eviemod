package dev.eviemod.paintbrush;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Persistence and defaults, independent of the game screen and item parser. */
final class QuickNavigationSettings {
    enum Activation { CLICK, DOUBLE_CLICK }
    boolean enabled = false;
    List<Tab> tabs = defaults();

    static final class Tab {
        boolean hidden = false;
        Activation activation = Activation.CLICK;
        String icon = "";
        /** Registry-aware ItemStack JSON for a copied held item; empty for typed vanilla icons. */
        String capturedIcon = "";
        String command = "";
        String title = "";
        String tooltip = "";
        Tab() {}
        boolean empty() { return icon.isBlank() && capturedIcon.isBlank(); }
        void editIcon(String value) {
            if (!value.equals(icon)) { icon = value; capturedIcon = ""; }
        }
        String error() {
            if (activation == null) return "Choose a click action.";
            if (!text(icon, 8192) || !text(capturedIcon, 65536)) return "Enter a valid icon item.";
            if (!text(command, 256) || (!command.isBlank() && normalizedCommand(command).isEmpty())) return "Enter a single command, up to 256 characters.";
            if (!text(tooltip, 256)) return "Keep the tooltip on one line, up to 256 characters.";
            if (!text(title, 256)) return "Keep the menu title pattern under 256 characters.";
            if (empty() && (!command.isBlank() || !title.isBlank() || !tooltip.isBlank())) return "Choose an icon for this tab.";
            try { Pattern.compile(title); }
            catch (PatternSyntaxException e) { return "Menu title pattern: " + e.getDescription(); }
            return null;
        }
    }
    static String normalizedCommand(String command) {
        String value = command.strip();
        return value.startsWith("/") ? value.substring(1).strip() : value;
    }
    private static boolean text(String value, int max) {
        return value != null && value.length() <= max && value.chars().noneMatch(Character::isISOControl);
    }
    void validate() {
        if (tabs == null || tabs.size() != 14) throw new IllegalArgumentException("Quick navigation requires fourteen tabs");
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i) == null) throw new IllegalArgumentException("Missing quick navigation tab " + (i + 1));
            String error = tabs.get(i).error();
            if (error != null) throw new IllegalArgumentException("Quick navigation tab " + (i + 1) + ": " + error);
        }
    }
    static void validateJson(JsonObject root) {
        if (!root.has("quickNavigation")) return;
        if (!root.get("quickNavigation").isJsonObject()) throw new IllegalArgumentException("Invalid quick navigation settings");
        var object = root.getAsJsonObject("quickNavigation");
        booleanField(object, "enabled");
        if (!object.has("tabs")) return;
        if (!object.get("tabs").isJsonArray()) throw new IllegalArgumentException("Invalid quick navigation tabs");
        for (var value : object.getAsJsonArray("tabs")) {
            if (!value.isJsonObject()) throw new IllegalArgumentException("Invalid quick navigation tab");
            var tab = value.getAsJsonObject();
            booleanField(tab, "hidden");
            for (String key : new String[]{"icon", "capturedIcon", "command", "title", "tooltip", "activation"}) {
                if (tab.has(key) && (!tab.get(key).isJsonPrimitive() || !tab.getAsJsonPrimitive(key).isString()))
                    throw new IllegalArgumentException("Invalid quick navigation " + key);
            }
        }
    }
    private static void booleanField(JsonObject object, String key) {
        if (object.has(key) && (!object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isBoolean()))
            throw new IllegalArgumentException("Invalid quick navigation " + key);
    }
    static List<Tab> defaults() {
        var result = new ArrayList<Tab>();
        // Positions remain stable in the editor, but fresh/reset settings have no buttons.
        for (int i = 0; i < 14; i++) result.add(new Tab());
        return result;
    }
}

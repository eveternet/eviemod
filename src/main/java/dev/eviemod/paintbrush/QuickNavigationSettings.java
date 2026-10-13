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
        String icon = "minecraft:barrier";
        String command = "";
        String title = "";
        String tooltip = "";
        Tab() {}
        Tab(String icon, String command, String title, String tooltip, Activation activation) {
            this.icon = "minecraft:" + icon; this.command = command; this.title = title;
            this.tooltip = tooltip; this.activation = activation;
        }
        String error() {
            if (activation == null) return "Choose a click action.";
            if (!text(icon, 8192) || icon.isBlank()) return "Enter an item for the icon.";
            if (!text(command, 256) || (!command.isBlank() && normalizedCommand(command).isEmpty())) return "Enter a single command, up to 256 characters.";
            if (!text(tooltip, 256)) return "Keep the tooltip on one line, up to 256 characters.";
            if (!text(title, 256)) return "Keep the menu title pattern under 256 characters.";
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
            for (String key : new String[]{"icon", "command", "title", "tooltip", "activation"}) {
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
        result.add(new Tab("diamond_sword", "/skills", "Your Skills", "Skills", Activation.CLICK));
        result.add(new Tab("painting", "/collection", "Collections", "Collections", Activation.CLICK));
        result.add(new Tab("bone", "/pets", "(?:\\(\\d+/\\d+\\) )?Pets", "Pets", Activation.CLICK));
        result.add(new Tab("leather_chestplate", "/armor", "\\(\\d+/\\d+\\) Armor Sets", "Armor Sets", Activation.CLICK));
        result.add(new Tab("bundle", "/sacks", "Sack of Sacks", "Sacks", Activation.CLICK));
        result.add(new Tab("poppy", "/accessories", "Accessory Bag(?: \\(\\d+/\\d+\\))?", "Accessories", Activation.CLICK));
        result.add(new Tab("ender_chest", "/storage", "(?:Rift )?Storage(?: \\(\\d+/\\d+\\))?", "Storage", Activation.CLICK));
        result.add(new Tab("grass_block", "/is", "", "Home", Activation.DOUBLE_CLICK));
        result.add(new Tab("wheat", "/warp garden", "", "Garden", Activation.DOUBLE_CLICK));
        result.add(new Tab("oak_sign", "/hub", "", "SkyBlock Hub", Activation.DOUBLE_CLICK));
        result.add(new Tab("wither_skeleton_skull", "/warp dungeon_hub", "", "Dungeons Hub", Activation.DOUBLE_CLICK));
        result.add(new Tab("gold_block", "/ah", "(?:Auctions Browser|Co-op Auction House|Auction House|Manage Auctions|Your Bids|BIN Auction View|Auction View|Auctions:.*)", "Auction House", Activation.CLICK));
        result.add(new Tab("golden_carrot", "/bz", "(?:Co-op )?Bazaar .*", "Bazaar", Activation.CLICK));
        result.add(new Tab("crafting_table", "/craft", "Craft Item", "Crafting Table", Activation.CLICK));
        return result;
    }
}

package dev.eviemod.features.experiments;

import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;

/** Hypixel's English GUI protocol, kept separate from the state machines. See docs/experiments.md. */
public final class ExperimentMenu {
    private static final Pattern TITLE = Pattern.compile("(Chronomatron|Ultrasequencer|Superpairs) \\((?:Beginner|High|Grand|Supreme|Transcendent|Metaphysical)\\)");
    private static final Pattern ROUND = Pattern.compile("Round: ([1-9][0-9]?)");
    private static final Pattern TIMER = Pattern.compile("Timer: [0-9]+(?:\\.[0-9]+)?s?");
    private ExperimentMenu() {}
    public static ExperimentSession.Game game(String title) {
        var match = TITLE.matcher(clean(title));
        if (!match.matches()) return null;
        return switch (match.group(1)) {
            case "Chronomatron" -> ExperimentSession.Game.CHRONOMATRON;
            case "Ultrasequencer" -> ExperimentSession.Game.ULTRASEQUENCER;
            default -> ExperimentSession.Game.SUPERPAIRS;
        };
    }
    public static String clean(String text) { return text.replaceAll("§.", "").strip(); }
    public static String name(ItemStack stack) {
        // Read server components rather than another cosmetic mod's resolved hover name.
        var custom = stack.get(DataComponents.CUSTOM_NAME);
        return clean(custom == null ? stack.getHoverName().getString() : custom.getString());
    }
    public static int round(ItemStack stack) {
        var match = ROUND.matcher(name(stack));
        if (!match.matches()) return 0;
        int value = Integer.parseInt(match.group(1));
        return value <= 49 ? value : 0;
    }
    public static ExperimentSession.Phase phase(ExperimentSession.Game game, ItemStack instruction) {
        if (game == ExperimentSession.Game.SUPERPAIRS) return ExperimentSession.Phase.PAIRS;
        String name = name(instruction);
        if (instruction.is(Items.GLOWSTONE) && name.equals("Remember the pattern!")) return ExperimentSession.Phase.REMEMBER;
        if (instruction.is(Items.CLOCK) && TIMER.matcher(name).matches()) return ExperimentSession.Phase.REPLAY;
        return ExperimentSession.Phase.UNKNOWN;
    }
    public static String note(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals("minecraft")) return "";
        String path = id.getPath();
        for (String color : new String[]{"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"})
            if (path.equals(color + "_terracotta") || path.equals(color + "_stained_glass")) return color;
        return "";
    }
    public static int number(ItemStack stack) {
        String name = name(stack);
        if (!name.matches("[1-9][0-9]?")) return 0;
        int value = Integer.parseInt(name);
        // Both the displayed number and stack count must agree, not a guessed position.
        return value <= 36 && stack.getCount() == value ? value : 0;
    }
    public static boolean hidden(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("minecraft:cyan_stained_glass") && switch (name(stack)) {
            case "Click any button!", "Click a second button!" -> true;
            default -> false;
        };
    }
    public static boolean powerup(ItemStack stack) {
        var lore = stack.get(DataComponents.LORE);
        return lore != null && lore.lines().stream().anyMatch(line -> clean(line.getString()).toLowerCase(java.util.Locale.ROOT).matches(".*power ?up.*"));
    }
    public static boolean reward(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return !stack.isEmpty() && !path.endsWith("stained_glass") && !path.endsWith("stained_glass_pane");
    }
    /** SkyHanni rewrites a manual experiment click to button-2 CLONE before vanilla receives it. */
    public static boolean manualClick(ContainerInput input, int button) {
        return input == ContainerInput.PICKUP && (button == 0 || button == 1)
            || input == ContainerInput.CLONE && button == 2;
    }
}

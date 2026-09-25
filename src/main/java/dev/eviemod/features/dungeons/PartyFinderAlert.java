package dev.eviemod.features.dungeons;

import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Matches the supplied Hypixel system message, independently of component styling. */
public final class PartyFinderAlert {
    public static final String DEFAULT_SUBTITLE = "Party is full!";
    private static final String FULL_MESSAGE =
        "Party Finder > Your dungeon group is full! Click here to warp to the dungeon!";
    private static final Pattern FORMATTING = Pattern.compile("§[0-9A-FK-OR]", Pattern.CASE_INSENSITIVE);

    public static void receive(String text, boolean overlay, boolean enabled, String subtitle,
                               Consumer<String> display) {
        if (!enabled || overlay || text == null || subtitle == null || subtitle.isBlank()) return;
        // Flatten components at the client boundary; strip any embedded legacy formatting here.
        // Match the entire line so player chat quoting the notification does not trigger it.
        if (FULL_MESSAGE.equals(FORMATTING.matcher(text).replaceAll("").strip())) display.accept(subtitle);
    }

    private PartyFinderAlert() {}
}

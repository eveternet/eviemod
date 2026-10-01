package dev.eviemod.features.dungeons;

import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Matches the supplied Hypixel system message, independently of component styling. */
public final class PartyFinderAlert {
    public static final String DEFAULT_SUBTITLE = "Party is full!";
    public static final String DEFAULT_SOUND = "minecraft:block.note_block.pling";
    public static final float DEFAULT_VOLUME = 1.0F;
    public static final float DEFAULT_PITCH = 1.0F;
    public record Sound(String id, float volume, float pitch) {}
    private static final String FULL_MESSAGE =
        "Party Finder > Your dungeon group is full! Click here to warp to the dungeon!";
    private static final Pattern FORMATTING = Pattern.compile("§[0-9A-FK-OR]", Pattern.CASE_INSENSITIVE);

    public static void receive(String text, boolean overlay, boolean enabled, String subtitle,
                               Consumer<String> display) {
        receive(text, overlay, enabled, subtitle, false, DEFAULT_SOUND, DEFAULT_VOLUME, DEFAULT_PITCH,
            display, sound -> {});
    }

    public static void receive(String text, boolean overlay, boolean subtitleEnabled, String subtitle,
                               boolean soundEnabled, String soundId, float volume, float pitch,
                               Consumer<String> display, Consumer<Sound> play) {
        if (overlay || text == null || (!subtitleEnabled && !soundEnabled)) return;
        // Flatten components at the client boundary; strip any embedded legacy formatting here.
        // Match the entire line so player chat quoting the notification does not trigger it.
        if (!FULL_MESSAGE.equals(FORMATTING.matcher(text).replaceAll("").strip())) return;
        if (subtitleEnabled && subtitle != null && !subtitle.isBlank()) display.accept(subtitle);
        if (soundEnabled && soundId != null && !soundId.isBlank() && validSoundLevels(volume, pitch))
            play.accept(new Sound(soundId.strip(), volume, pitch));
    }

    public static boolean validSoundLevels(float volume, float pitch) {
        return Float.isFinite(volume) && volume >= 0.0F && volume <= 1.0F
            && Float.isFinite(pitch) && pitch >= 0.5F && pitch <= 2.0F;
    }

    private PartyFinderAlert() {}
}

package dev.eviemod.features.dungeons;

import java.util.regex.Pattern;

/** Pure tick timeline. The adapter supplies elapsed ticks from its TPS estimator. */
public final class CellsAlignmentTimer {
    private static final Pattern FORMATTING = Pattern.compile("§[0-9A-FK-ORX]", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAST = Pattern.compile("[A-Za-z0-9_]{1,16} cast Cells Alignment on you!");
    private double elapsedTicks;
    private boolean active;

    public record Frame(String text, int color) {}
    private static final Frame[] FRAMES = {
        new Frame("Cells Aligned Casted!", 0x55ff55),
        new Frame("4", 0x55ff55), new Frame("3", 0x55ff55),
        new Frame("2", 0xffaa00), new Frame("1", 0xff5555),
        new Frame("Esoward now", 0xff5555)
    };

    static String plain(String text) {
        return text == null ? "" : FORMATTING.matcher(text).replaceAll("").strip();
    }

    public static boolean matches(String message) {
        return CAST.matcher(plain(message)).matches();
    }

    public boolean receive(String message, boolean overlay, boolean eligible) {
        if (!eligible) reset();
        if (!eligible || overlay || !matches(message)) return false;
        elapsedTicks = 0;
        active = true;
        return true;
    }

    public void advance(double serverTicks) {
        if (!active || !Double.isFinite(serverTicks) || serverTicks <= 0) return;
        if (serverTicks >= 160 - elapsedTicks) reset();
        else elapsedTicks += serverTicks;
    }

    public Frame frame() {
        return active ? FRAMES[Math.min((int) (elapsedTicks / 20), 5)] : null;
    }

    public void reset() {
        active = false;
        elapsedTicks = 0;
    }
}

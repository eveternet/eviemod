package dev.eviemod.features.scoresync;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/** Narrow observable Hypixel messages; evidence and attribution limits are in docs/noamm-score-sync.md. */
final class ChatCooldown {
    private static final Pattern DURATION = Pattern.compile(
        "^You can only chat once every ([0-9]+(?:\\.[0-9]+)?) seconds!(?: Ranked users (?:can )?bypass this restriction!)?$");

    static Long delay(String plain) {
        if (plain.equals("You are sending commands too fast! Please slow down.")
            || plain.equals("You are sending commands too fast!Please slow down.")) return 1000L;
        var match = DURATION.matcher(plain);
        if (!match.matches()) return null;
        try {
            return new BigDecimal(match.group(1)).multiply(BigDecimal.valueOf(1000))
                .setScale(0, RoundingMode.CEILING).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) { return null; }
    }
    private ChatCooldown() {}
}

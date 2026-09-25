package dev.eviemod.features.dungeons;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Pure parsing and output selection, independent of Minecraft and configuration storage. */
public final class AnnounceCrit {
    public static final String DEFAULT_TEMPLATE = "Explosive Shot did {damage} damage to enemies.";
    // 128 decimal digits is vastly beyond gameplay values, without imposing a
    // numeric damage cap. The whole-line limit also bounds regex work beforehand.
    static final int MAX_NUMBER_DIGITS = 128;
    private static final int MAX_MESSAGE_LENGTH = 512;
    private static final Pattern SUMMARY = Pattern.compile(
        "^Your Explosive Shot hit (\\d+) (?:enemy|enemies) for ([\\d,.]+) damage\\.$");
    private static final Pattern DAMAGE = Pattern.compile("(?:\\d+|\\d{1,3}(?:,\\d{3})+)(?:\\.\\d+)?");

    public static String render(String text, String template) {
        if (text.length() > MAX_MESSAGE_LENGTH) return null;
        var match = SUMMARY.matcher(text);
        if (!match.matches() || match.group(1).length() > MAX_NUMBER_DIGITS
            || match.group(2).chars().filter(Character::isDigit).count() > MAX_NUMBER_DIGITS
            || !DAMAGE.matcher(match.group(2)).matches()) return null;
        try {
            var enemies = new BigInteger(match.group(1));
            if (enemies.signum() <= 0) return null;
            var total = new BigDecimal(match.group(2).replace(",", ""));
            var average = total.divide(new BigDecimal(enemies), 0, RoundingMode.HALF_UP);
            return template.replace("{damage}", NumberFormat.getIntegerInstance(Locale.US).format(average));
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    public static void receive(String text, boolean overlay, boolean enabled, String template,
                               boolean partyChat, Consumer<String> local, Consumer<String> command) {
        if (overlay || !enabled) return;
        String rendered = render(text, template);
        if (rendered == null) return;
        if (partyChat) command.accept("pc " + rendered);
        else local.accept(rendered);
    }

    private AnnounceCrit() {}
}

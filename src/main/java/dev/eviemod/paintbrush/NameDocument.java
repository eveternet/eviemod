package dev.eviemod.paintbrush;

import java.util.*;
import net.minecraft.network.chat.Style;

/** Editing state indexed by Unicode code point; widget selections arrive as UTF-16 offsets. */
public final class NameDocument {
    private String text = "";
    private final List<Style> styles = new ArrayList<>();
    public NameDocument(StyledName name) {
        if (name != null) {
            text = name.text();
            name.render(Style.EMPTY).visit((style, value) -> {
                value.codePoints().forEach(cp -> styles.add(style)); return Optional.empty();
            }, Style.EMPTY);
        }
    }
    public String text() { return text; }
    public void edit(String value) {
        int[] old = text.codePoints().toArray(), next = value.codePoints().toArray();
        int prefix = 0, suffix = 0;
        while (prefix < old.length && prefix < next.length && old[prefix] == next[prefix]) prefix++;
        while (suffix < old.length - prefix && suffix < next.length - prefix
            && old[old.length - suffix - 1] == next[next.length - suffix - 1]) suffix++;
        Style inherited = prefix > 0 ? styles.get(prefix - 1) : prefix < styles.size() ? styles.get(prefix) : Style.EMPTY;
        var result = new ArrayList<>(styles.subList(0, prefix));
        for (int i = prefix; i < next.length - suffix; i++) result.add(inherited);
        result.addAll(styles.subList(old.length - suffix, old.length));
        text = value; styles.clear(); styles.addAll(result);
    }
    private int[] range(int anchor, int cursor) {
        int a = Math.clamp(Math.min(anchor, cursor), 0, text.length());
        int b = Math.clamp(Math.max(anchor, cursor), 0, text.length());
        if (a > 0 && a < text.length() && Character.isLowSurrogate(text.charAt(a))) a--;
        if (b > 0 && b < text.length() && Character.isLowSurrogate(text.charAt(b))) b++;
        return new int[] {text.codePointCount(0, a), text.codePointCount(0, b)};
    }
    public boolean enabled(int a, int b, int flag) {
        var r = range(a, b);
        return r[0] < r[1] && styles.subList(r[0], r[1]).stream().allMatch(s -> flag(s, flag));
    }
    public void toggle(int a, int b, int flag) {
        var r = range(a, b); boolean value = !enabled(a, b, flag);
        for (int i = r[0]; i < r[1]; i++) styles.set(i, switch (flag) {
            case 0 -> styles.get(i).withBold(value); case 1 -> styles.get(i).withItalic(value);
            case 2 -> styles.get(i).withObfuscated(value); case 3 -> styles.get(i).withUnderlined(value);
            default -> styles.get(i).withStrikethrough(value);
        });
    }
    private static boolean flag(Style s, int f) {
        return switch (f) { case 0 -> s.isBold(); case 1 -> s.isItalic(); case 2 -> s.isObfuscated(); case 3 -> s.isUnderlined(); default -> s.isStrikethrough(); };
    }
    public void color(int a, int b, int start, Integer end) {
        var r = range(a, b);
        for (int i = r[0]; i < r[1]; i++) {
            double t = r[1] - r[0] <= 1 ? 0 : (double) (i - r[0]) / (r[1] - r[0] - 1);
            int rgb = 0;
            for (int shift : new int[] {16, 8, 0}) {
                int from = start >> shift & 255, to = (end == null ? start : end) >> shift & 255;
                rgb |= (int) Math.round(from + (to - from) * t) << shift;
            }
            styles.set(i, styles.get(i).withColor(rgb));
        }
    }
    public StyledName styled() {
        if (text.isBlank()) return null;
        var segments = new ArrayList<StyledName>();
        int[] points = text.codePoints().toArray();
        for (int first = 0; first < points.length;) {
            int last = first + 1;
            while (last < points.length && styles.get(first).equals(styles.get(last))) last++;
            Style s = styles.get(first);
            segments.add(new StyledName(new String(points, first, last - first), s.getColor() == null ? null : s.getColor().getValue(), null,
                s.isBold(), s.isItalic(), s.isObfuscated(), s.isUnderlined(), s.isStrikethrough()));
            first = last;
        }
        return new StyledName(text, null, null, false, false, false, false, false, segments);
    }
}

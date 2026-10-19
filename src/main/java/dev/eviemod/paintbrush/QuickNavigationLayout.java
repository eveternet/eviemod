package dev.eviemod.paintbrush;

/** The fourteen creative-style tabs, anchored to the actual container bounds. */
final class QuickNavigationLayout {
    record Bounds(int x, int y, int width, int height) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + width && my >= y && my < y + height; }
    }
    static Bounds tab(int index, int left, int top, int width, int height) {
        if (index < 0 || index >= 14) throw new IllegalArgumentException("Invalid navigation tab");
        return new Bounds(left + width / 2 - 88 + index % 7 * 25, index < 7 ? top - 28 : top + height - 4, 26, 32);
    }
    static Bounds exposed(int index, Bounds tab) {
        return new Bounds(tab.x(), tab.y() + (index < 7 ? 0 : 4), 26, 28);
    }
    static boolean allowed(boolean enabled, boolean skyblock, boolean player, boolean creative) {
        return enabled && skyblock && player && !creative;
    }
}

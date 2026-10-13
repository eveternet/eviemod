package dev.eviemod.paintbrush;

/** One-second command cooldown across screens, with a separate confirmation arm. */
final class QuickNavigationClicks {
    private long sentAt;
    private boolean sent;
    private int armed = -1;
    private long armedAt;
    boolean activate(int tab, QuickNavigationSettings.Activation action, long now) {
        if (sent && now - sentAt < 1000) return false;
        if (action == QuickNavigationSettings.Activation.DOUBLE_CLICK && !armed(tab, now)) {
            armed = tab; armedAt = now; return false;
        }
        sent = true; sentAt = now; disarm(); return true;
    }
    boolean armed(int tab, long now) { return armed == tab && now - armedAt < 1000; }
    boolean coolingDown(long now) { return sent && now - sentAt < 1000; }
    void disarm() { armed = -1; }
}

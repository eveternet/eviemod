package dev.eviemod.features.skyblock;

/** Tracks physical state even while a screen owns input; never queues a press for later. */
final class HotkeyPressState {
    private int key = -1;
    private boolean wasDown;

    boolean sample(int currentKey, boolean down, boolean gameplayActive) {
        boolean pressed = gameplayActive && currentKey == key && down && !wasDown;
        key = currentKey;
        wasDown = down;
        return pressed;
    }
}

package dev.eviemod.features.skyblock;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HotkeyPressStateTest {
    private static final int X = 88;

    @Test void typingHexThenClosingChatWhileHoldingXRequiresRelease() {
        var state = new HotkeyPressState();
        assertFalse(state.sample(X, false, true));
        assertFalse(state.sample(X, true, false)); // X typed in chat
        assertFalse(state.sample(X, true, false)); // screen removal
        assertFalse(state.sample(X, true, true)); // Enter closed chat
        assertFalse(state.sample(X, true, true)); // still held
        assertFalse(state.sample(X, false, true));
        assertTrue(state.sample(X, true, true));
        assertFalse(state.sample(X, true, true));
    }

    @Test void screenRemovalCapturesTypingBetweenTicks() {
        var state = new HotkeyPressState();
        state.sample(X, false, false); // last chat tick before typing X
        assertFalse(state.sample(X, true, false)); // removal samples the late press
        assertFalse(state.sample(X, true, true));
        state.sample(X, false, true);
        assertTrue(state.sample(X, true, true));
    }

    @Test void aFreshPressImmediatelyAfterClosingTheScreenStillWorks() {
        var state = new HotkeyPressState();
        state.sample(X, true, false);
        state.sample(X, false, false); // released before closing
        assertTrue(state.sample(X, true, true));
    }

    @Test void holdingGameplayKeyAcrossScreenDoesNotRetrigger() {
        var state = new HotkeyPressState();
        state.sample(X, false, true);
        assertTrue(state.sample(X, true, true));
        assertFalse(state.sample(X, true, false));
        assertFalse(state.sample(X, true, true));
    }

    @Test void disabledOrDisconnectedInputDoesNotQueueCommands() {
        var state = new HotkeyPressState();
        state.sample(X, false, false);
        assertFalse(state.sample(X, true, false));
        assertFalse(state.sample(X, true, true));
        state.sample(X, false, true);
        assertTrue(state.sample(X, true, true));
    }

    @Test void newOrChangedBindingMustObserveReleaseBeforeFiring() {
        var state = new HotkeyPressState();
        assertFalse(state.sample(X, true, true));
        state.sample(X, false, true);
        assertFalse(state.sample(89, true, true));
        assertFalse(state.sample(89, true, true));
        state.sample(89, false, true);
        assertTrue(state.sample(89, true, true));
    }

    @Test void mouseBindingsUseTheSameReleaseGuardIndependently() {
        var mouse = new HotkeyPressState(); var keyboard = new HotkeyPressState();
        mouse.sample(0, true, false); keyboard.sample(X, false, false);
        assertFalse(mouse.sample(0, true, true));
        assertTrue(keyboard.sample(X, true, true));
        mouse.sample(0, false, true);
        assertTrue(mouse.sample(0, true, true));
    }
}

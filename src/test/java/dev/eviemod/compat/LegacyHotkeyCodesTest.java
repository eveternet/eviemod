package dev.eviemod.compat;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyHotkeyCodesTest {
    @Test void existingKeyboardBindingsKeepTheirMeaning() {
        assertBinding(65, "key.keyboard.a", InputConstants.KEY_A);
        assertBinding(80, "key.keyboard.p", InputConstants.KEY_P);
        assertBinding(257, "key.keyboard.enter", InputConstants.KEY_RETURN);
        assertBinding(258, "key.keyboard.tab", InputConstants.KEY_TAB);
        assertBinding(290, "key.keyboard.f1", InputConstants.KEY_F1);
        assertBinding(335, "key.keyboard.keypad.enter", InputConstants.KEY_NUMPADENTER);
    }
    @Test void mouseButtonsRemainDistinctFromKeyboardScancodes() {
        assertBinding(0, "key.mouse.left", InputConstants.MOUSE_BUTTON_LEFT);
        assertBinding(1, "key.mouse.right", InputConstants.MOUSE_BUTTON_RIGHT);
        assertBinding(2, "key.mouse.middle", InputConstants.MOUSE_BUTTON_MIDDLE);
        for (int i = 3; i <= 7; i++) {
            var key = LegacyHotkeyCodes.resolve(i);
            assertEquals(InputConstants.Type.MOUSE, key.getType());
            assertEquals(i, LegacyHotkeyCodes.store(key));
        }
        assertEquals(65, LegacyHotkeyCodes.store(ClientInput.keyboard().getOrCreate(InputConstants.KEY_A)));
    }
    @Test void unsupportedValuesAreUnboundWithoutReinterpretingThem() {
        for (int value : new int[]{-1, -2, 8, 400, Integer.MAX_VALUE})
            assertEquals(InputConstants.UNKNOWN, LegacyHotkeyCodes.resolve(value));
        assertEquals(-1, LegacyHotkeyCodes.store(InputConstants.UNKNOWN));
    }
    private static void assertBinding(int saved, String name, int nativeCode) {
        var key = LegacyHotkeyCodes.resolve(saved);
        assertEquals(name, key.getName());
        assertEquals(nativeCode, key.getValue());
        assertEquals(saved, LegacyHotkeyCodes.store(key));
    }
}

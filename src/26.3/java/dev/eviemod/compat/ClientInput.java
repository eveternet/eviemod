package dev.eviemod.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/** Native input boundary; persisted command bindings remain in the legacy format. */
public final class ClientInput {
    private ClientInput() {}
    public static InputConstants.Type keyboard() { return InputConstants.Type.KEYBOARD; }
    public static boolean isDown(Minecraft client, int stored) {
        var key = LegacyHotkeyCodes.resolve(stored);
        if (key.equals(InputConstants.UNKNOWN)) return false;
        if (key.getType() == InputConstants.Type.MOUSE)
            return (org.lwjgl.sdl.SDLMouse.nSDL_GetMouseState(0, 0) & (1 << (key.getValue() - 1))) != 0;
        return InputConstants.isKeyDown(key.getValue());
    }
}

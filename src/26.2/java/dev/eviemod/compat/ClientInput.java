package dev.eviemod.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/** Native input boundary; persisted command bindings remain in the legacy format. */
public final class ClientInput {
    private ClientInput() {}
    public static InputConstants.Type keyboard() { return InputConstants.Type.KEYSYM; }
    public static boolean isDown(Minecraft client, int stored) {
        var key = LegacyHotkeyCodes.resolve(stored);
        if (key.equals(InputConstants.UNKNOWN)) return false;
        if (key.getType() == InputConstants.Type.MOUSE)
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.getWindow().handle(), key.getValue()) == InputConstants.PRESS;
        return InputConstants.isKeyDown(client.getWindow(), key.getValue());
    }
}

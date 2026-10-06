// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.fixture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/** Optional test-only singleton Screen selected by class name, with no dependency on a GUI mod. */
public final class OptionalGuiProbe {
    private static Screen screen;
    private static int frames, closes;

    private OptionalGuiProbe() {}

    public static boolean prepare(Minecraft mc) {
        String name = System.getProperty("eviemod.metal.guiSmokeScreen");
        if (name == null) return mc.gui.screen() instanceof TitleScreen;
        if (screen == null) {
            if (!(mc.gui.screen() instanceof TitleScreen)) return false;
            try { screen = (Screen) Class.forName(name).getField("INSTANCE").get(null); }
            catch (ReflectiveOperationException e) { throw new AssertionError("Cannot load fixture screen " + name, e); }
        }
        if (mc.gui.screen() != screen) {
            mc.gui.setScreen(screen);
            frames = 0;
            return false;
        }
        if (closes < 20) {
            if (++frames >= 12) {
                mc.gui.setScreen(new TitleScreen());
                closes++;
                if (closes == 20) System.out.println("EVIEMETAL_MOD_GUI_CYCLES_OK: " + name + " opened/closed 20 times");
            }
            return false;
        }
        // Optional time for manual fixture interaction before the ordinary screenshot/readback exits.
        return ++frames >= Integer.getInteger("eviemod.metal.guiSmokeHoldFrames", 12);
    }
}

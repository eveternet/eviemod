package dev.eviemod.paintbrush;

import net.azureaaron.dandelion.deps.moulconfig.platform.MoulConfigRenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Reuse the settings backend's panel renderer; keep the platform dependency in one place. */
final class EditorTheme {
    private EditorTheme() {}
    static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        new MoulConfigRenderContext(graphics).drawDarkRect(x, y, width, height, false);
    }
    static void inset(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xff36363e);
        graphics.fill(x, y, x + width - 1, y + height - 1, 0xff101016);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xff16161e);
    }
}

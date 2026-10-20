package dev.eviemod.features.dungeons;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Draw only the main title, using vanilla's title position, scale and backdrop. */
public final class CellsAlignmentTitle {
    public static void extract(GuiGraphicsExtractor graphics, Font font, CellsAlignmentTimer.Frame frame) {
        if (frame == null) return;
        var text = Component.literal(frame.text()).withColor(frame.color());
        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate(graphics.guiWidth() / 2, graphics.guiHeight() / 2);
        graphics.pose().scale(4.0F, 4.0F);
        int width = font.width(text);
        graphics.textWithBackdrop(font, text, -width / 2, -10, width, 0xffffffff);
        graphics.pose().popMatrix();
    }

    private CellsAlignmentTitle() {}
}

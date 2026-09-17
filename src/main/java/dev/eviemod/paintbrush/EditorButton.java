package dev.eviemod.paintbrush;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class EditorButton extends AbstractWidget {
    private final Runnable action;
    private final BooleanSupplier selected;
    EditorButton(String label, int x, int y, int width, Runnable action, BooleanSupplier selected) {
        super(x, y, width, 20, Component.literal(label)); this.action = action; this.selected = selected;
    }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        boolean on = selected.getAsBoolean();
        EditorTheme.panel(g, getX(), getY(), getWidth(), getHeight());
        if (active && isHoveredOrFocused()) g.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, 0xff36363e);
        var font = Minecraft.getInstance().font;
        g.text(font, getMessage(), getX() + (getWidth() - font.width(getMessage())) / 2, getY() + 6, !active ? 0xff6d7280 : on ? 0xff55ffff : 0xffcccccc);
        if (on) {
            int labelWidth = font.width(getMessage());
            int labelX = getX() + (getWidth() - labelWidth) / 2;
            g.fill(labelX, getY() + 16, labelX + labelWidth, getY() + 17, 0xff55ffff);
        }
    }
    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { action.run(); }
    @Override public boolean keyPressed(KeyEvent event) {
        if (active && isFocused() && (event.key() == 257 || event.key() == 32 || event.key() == 335)) {
            action.run(); return true;
        }
        return false;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
}

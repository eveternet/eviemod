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
        int background = !active ? 0xff29292e : on ? 0xff36595b : isHoveredOrFocused() ? 0xff41414b : 0xff303038;
        g.fill(getX(), getY(), getRight(), getBottom(), background);
        if (on) g.fill(getX(), getBottom() - 2, getRight(), getBottom(), 0xff55ffff);
        var font = Minecraft.getInstance().font;
        g.text(font, getMessage(), getX() + (getWidth() - font.width(getMessage())) / 2, getY() + 6, active ? -1 : 0xff6d7280);
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

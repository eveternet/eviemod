package dev.eviemod.paintbrush;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Local rendering/input transform; never changes Minecraft's global GUI scale. */
abstract class CompactScreen extends Screen {
    protected int viewWidth, viewHeight;
    private EditorViewport viewport = new EditorViewport(1, 1, 1);
    protected CompactScreen(Component title) { super(title); }
    protected void updateViewport() {
        viewport = EditorViewport.fit(width, height, minecraft.getWindow().getGuiScale());
        viewWidth = viewport.width(); viewHeight = viewport.height();
    }
    protected abstract void renderContents(GuiGraphicsExtractor g, int mx, int my, float delta);
    protected void renderWidgets(GuiGraphicsExtractor g, int mx, int my, float delta) { super.extractRenderState(g, mx, my, delta); }

    @Override public final void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int vx = (int) viewport.input(mx), vy = (int) viewport.input(my);
        g.pose().pushMatrix();
        g.pose().scale(viewport.scale(), viewport.scale());
        try {
            renderContents(g, vx, vy, delta);
        } finally { g.pose().popMatrix(); }
    }
    private MouseButtonEvent convert(MouseButtonEvent event) {
        return new MouseButtonEvent(viewport.input(event.x()), viewport.input(event.y()), event.buttonInfo());
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        var local = convert(event);
        // Vanilla containers keep keyboard focus on blank-space clicks. Dismiss editor suggestions explicitly.
        for (var child : children()) if (child instanceof ModelField field && !field.isMouseOver(local.x(), local.y())) {
            field.dismiss(); field.setFocused(false);
            if (getFocused() == field) setFocused(null);
        }
        return super.mouseClicked(local, doubleClick);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return super.mouseReleased(convert(event)); }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return super.mouseDragged(convert(event), viewport.input(dx), viewport.input(dy));
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return super.mouseScrolled(viewport.input(x), viewport.input(y), dx, dy);
    }
    @Override public void mouseMoved(double x, double y) { super.mouseMoved(viewport.input(x), viewport.input(y)); }
}

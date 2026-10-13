package dev.eviemod.paintbrush;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Native input/narration with vanilla creative-tab sprites, shared by runtime and editor. */
final class QuickNavigationTab extends AbstractWidget {
    private final int index;
    private final Supplier<QuickNavigationSettings.Tab> config;
    private final Supplier<QuickNavigationLayout.Bounds> bounds;
    private final BooleanSupplier selected;
    private final Runnable action;
    private String parsedIcon, tooltip;
    private ItemStack icon;
    private QuickNavigationLayout.Bounds full;

    QuickNavigationTab(int index, Supplier<QuickNavigationSettings.Tab> config,
            Supplier<QuickNavigationLayout.Bounds> bounds, BooleanSupplier selected, Runnable action) {
        super(0, 0, 26, 28, Component.empty());
        this.index = index; this.config = config; this.bounds = bounds;
        this.selected = selected; this.action = action;
        setTooltipDelay(Duration.ofMillis(100)); updateBounds();
    }
    void updateBounds() {
        full = bounds.get();
        var hit = QuickNavigationLayout.exposed(index, full);
        setPosition(hit.x(), hit.y());
    }
    int index() { return index; }
    QuickNavigationSettings.Tab config() { return config.get(); }
    @Override public boolean isMouseOver(double x, double y) { updateBounds(); return super.isMouseOver(x, y); }
    void tooltip(String value) {
        if (!value.equals(tooltip)) {
            tooltip = value; setMessage(Component.literal(value)); setTooltip(Tooltip.create(Component.literal(value)));
        }
    }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        updateBounds();
        var tab = config.get();
        if (!tab.icon.equals(parsedIcon)) { parsedIcon = tab.icon; icon = QuickNavigationIcons.preview(tab.icon); }
        boolean on = selected.getAsBoolean() || isHoveredOrFocused();
        String sprite = "container/creative_inventory/tab_" + (index < 7 ? "top" : "bottom")
            + (on ? "_selected_" : "_unselected_") + (index % 7 + 1);
        // Inactive tabs have four pixels under the inventory border. Clip that seam
        // rather than hooking Screen's background/subtitle internals like Skyblocker.
        if (!on) g.enableScissor(getX(), getY(), getRight(), getBottom());
        g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace(sprite), full.x(), full.y(), 26, 32);
        g.item(icon, full.x() + 5, full.y() + (index < 7 ? 9 : 7));
        if (tab.hidden) g.fill(getX(), getY(), getRight(), getBottom(), 0x99000000);
        if (!on) g.disableScissor();
    }
    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { action.run(); }
    @Override public boolean keyPressed(KeyEvent event) {
        if (active && isFocused() && (event.key() == 257 || event.key() == 32 || event.key() == 335)) {
            action.run(); return true;
        }
        return false;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}

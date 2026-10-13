package dev.eviemod.paintbrush;

import dev.eviemod.compat.ClientUi;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** SkyOcean's select-a-tab editing flow in the same panel/native controls as Paint Brush. */
final class QuickNavigationScreen extends CompactScreen {
    private final Screen parent;
    private final ModSettings.Values parentDraft;
    private final QuickNavigationSettings draft;
    private int selected = 0;
    private int x, y;
    private String error;
    private ModelField icon;
    private EditorButton done, hide, confirm;
    private List<String> itemIds;
    private static final Identifier INVENTORY = Identifier.withDefaultNamespace("textures/gui/container/inventory.png");

    QuickNavigationScreen(Screen parent, ModSettings.Values parentDraft) {
        super(Component.literal("Quick Navigation"));
        this.parent = parent; this.parentDraft = parentDraft;
        this.draft = parentDraft.copy().quickNavigation;
    }
    @Override protected void init() {
        updateViewport(); x = (viewWidth - 540) / 2; y = (viewHeight - 300) / 2;
        if (itemIds == null) itemIds = BuiltInRegistries.ITEM.keySet().stream().map(Object::toString).sorted().toList();
        var tab = draft.tabs.get(selected);
        int left = x + 16, width = 290;
        icon = addRenderableWidget(new ModelField(font, left, y + 60, width - 106, itemIds, "",
            value -> { tab.editIcon(value); updateIconTooltip(); validateDraft(); }));
        icon.setMaxLength(8192); icon.setValue(tab.icon);
        updateIconTooltip();
        var useHeld = button("Use held item", left + width - 100, y + 60, 100, () -> captureHeld(
            minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem()));
        useHeld.active = minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty();
        useHeld.setTooltip(Tooltip.create(Component.literal("Copies the held item's appearance, including SkyBlock data and head textures.")));
        field("Command", left, y + 100, width, tab.command, 256, value -> tab.command = value);
        var title = field("Menu title pattern", left, y + 140, width, tab.title, 256, value -> tab.title = value);
        title.setTooltip(Tooltip.create(Component.literal("Highlights this tab when the whole menu title matches. Leave blank for no highlight.")));
        field("Tooltip", left, y + 180, width, tab.tooltip, 256, value -> {
            tab.tooltip = value;
            for (var child : children()) if (child instanceof QuickNavigationTab preview && preview.index() == selected)
                preview.tooltip(tabLabel(selected));
        });
        hide = button(tab.hidden ? "Show tab" : "Hide tab", left, y + 218, 85, () -> {
            tab.hidden = !tab.hidden; hide.setMessage(Component.literal(tab.hidden ? "Show tab" : "Hide tab"));
        });
        confirm = button(actionLabel(tab), left + 91, y + 218, 112, () -> {
            tab.activation = tab.activation == QuickNavigationSettings.Activation.CLICK
                ? QuickNavigationSettings.Activation.DOUBLE_CLICK : QuickNavigationSettings.Activation.CLICK;
            confirm.setMessage(Component.literal(actionLabel(tab)));
        });
        confirm.setTooltip(Tooltip.create(Component.literal("Double click asks for a second click within one second.")));
        button("Reset tab", left + 209, y + 218, 81, () -> {
            draft.tabs.set(selected, QuickNavigationSettings.defaults().get(selected)); rebuildWidgets();
        });
        done = button("Done", x + 462, y + 10, 62, this::onClose);
        button("Cancel", x + 394, y + 10, 62, () -> ClientUi.setScreen(minecraft, parent));
        for (int i = 0; i < 14; i++) {
            int index = i;
            var widget = new QuickNavigationTab(index, () -> draft.tabs.get(index),
                () -> QuickNavigationLayout.tab(index, x + 344, y + 64, 176, 166), () -> selected == index,
                () -> { selected = index; rebuildWidgets(); });
            widget.tooltip(tabLabel(index));
            addRenderableWidget(widget);
        }
        validateDraft();
    }
    private void updateIconTooltip() {
        if (icon == null) return;
        var tab = draft.tabs.get(selected);
        String label = "Item ID with optional Minecraft components. Leave an unused tab blank.";
        if (!tab.capturedIcon.isBlank()) {
            label = "Captured: " + QuickNavigationIcons.preview(tab).getHoverName().getString()
                + ". Changing this field replaces the captured item.";
        }
        icon.setTooltip(Tooltip.create(Component.literal(label)));
    }
    /** The UI and fixture use the same snapshot path; no inventory item is changed. */
    void captureHeld(ItemStack held) {
        try {
            var tab = draft.tabs.get(selected);
            QuickNavigationIcons.capture(tab, held);
            icon.setValue(tab.icon); updateIconTooltip(); validateDraft();
        } catch (RuntimeException e) {
            error = e.getMessage() == null ? "Unable to copy this item's icon." : e.getMessage();
        }
    }
    private static String actionLabel(QuickNavigationSettings.Tab tab) {
        return tab.activation == QuickNavigationSettings.Activation.CLICK ? "Single click" : "Double click";
    }
    private String tabLabel(int index) { return "Tab " + (index + 1) + " · " + draft.tabs.get(index).tooltip; }
    private EditorButton button(String label, int bx, int by, int width, Runnable action) {
        return addRenderableWidget(new EditorButton(label, bx, by, width, action, () -> false));
    }
    private EditBox field(String label, int bx, int by, int width, String value, int max, java.util.function.Consumer<String> change) {
        var box = new EditBox(font, bx, by, width, 20, Component.literal(label));
        box.setMaxLength(max); box.setValue(value); box.setHint(Component.literal(label));
        box.setResponder(text -> { change.accept(text); validateDraft(); });
        return addRenderableWidget(box);
    }
    private void validateDraft() {
        error = null;
        for (int i = 0; i < draft.tabs.size(); i++) {
            var tab = draft.tabs.get(i);
            String problem = tab.error();
            if (problem == null) {
                try { QuickNavigationIcons.parse(tab); }
                catch (RuntimeException e) { problem = e.getMessage() == null ? "Invalid icon item." : e.getMessage(); }
            }
            if (problem != null) { error = "Tab " + (i + 1) + ": " + problem; break; }
        }
        if (done != null) done.active = error == null;
    }
    @Override protected void renderContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        EditorTheme.panel(g, x, y, 540, 300);
        g.text(font, "Quick Navigation", x + 16, y + 17, -1);
        g.text(font, "Tab " + (selected + 1) + " · " + (selected < 7 ? "Top" : "Bottom"), x + 16, y + 35, 0xff55ffff);
        String[] labels = {"Icon", "Command", "Menu title pattern", "Tooltip"};
        for (int i = 0; i < labels.length; i++) g.text(font, labels[i], x + 16, y + 48 + 40 * i, 0xffaaaebd);
        g.blit(RenderPipelines.GUI_TEXTURED, INVENTORY, x + 344, y + 64, 0, 0, 176, 166, 256, 256);
        g.text(font, "Inventory", x + 352, y + 138, 0xff404040);
        renderWidgets(g, mx, my, delta);
        icon.drawSuggestions(g);
        if (error != null) {
            g.text(font, font.plainSubstrByWidth(error, 500), x + 16, y + 278, 0xffff8585);
        } else {
            g.text(font, draft.enabled ? "Changes save on Done." : "Quick Navigation is off. Enable it in settings.", x + 16, y + 278, 0xffaaaebd);
        }
    }
    @Override public void onClose() {
        validateDraft();
        if (error != null) return;
        var next = parentDraft.copy(); next.quickNavigation = draft;
        try {
            EviemodSettings.STORE.save(next);
            parentDraft.quickNavigation = EviemodSettings.STORE.values().copy().quickNavigation;
            ClientUi.setScreen(minecraft, parent);
        } catch (IOException e) { error = e.getMessage(); }
    }
    @Override public boolean isPauseScreen() { return false; }
}

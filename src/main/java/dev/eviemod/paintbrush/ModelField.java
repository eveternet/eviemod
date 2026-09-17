package dev.eviemod.paintbrush;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class ModelField extends EditBox {
    private final List<String> models;
    private final Font font;
    private final boolean dyes;
    private final boolean names;
    private List<String> matches = List.of();
    private int choice;
    private boolean open;
    ModelField(Font font, int x, int y, int width, List<String> models, String initial, java.util.function.Consumer<String> change) {
        this(font, x, y, width, models, initial, change, false);
    }
    ModelField(Font font, int x, int y, int width, List<String> models, String initial, java.util.function.Consumer<String> change, boolean dyes) {
        this(font, x, y, width, models, initial, change, dyes, dyes,
            dyes ? "Dye preset or hex" : "Item model", dyes ? "Search Hypixel dyes or enter #RRGGBB" : "minecraft:diamond_sword");
    }
    ModelField(Font font, int x, int y, int width, List<String> models, String initial,
            java.util.function.Consumer<String> change, boolean dyes, boolean names, String label, String hint) {
        super(font, x, y, width, 20, Component.literal(label));
        this.dyes = dyes; this.names = names;
        this.font = font; this.models = models; setMaxLength(256); setValue(initial);
        setHint(Component.literal(hint));
        setResponder(value -> { change.accept(value); update(); });
    }
    private void update() { matches = names ? models.stream().filter(s -> s.toLowerCase(java.util.Locale.ROOT).contains(getValue().toLowerCase(java.util.Locale.ROOT))).toList() : ModelCompletion.matches(models, getValue()); choice = 0; open = !matches.isEmpty(); }
    private void accept() { if (open && !matches.isEmpty()) { setValue(matches.get(choice)); moveCursorToEnd(false); open = false; } }
    private int suggestionAt(double x, double y) {
        if (!visible || !active || !open || !isFocused() || x < getX() || x >= getRight() || y < getBottom()) return -1;
        int first = choice - choice % 5;
        int row = (int) ((y - getBottom()) / 16);
        return row < Math.min(5, matches.size() - first) ? first + row : -1;
    }
    @Override public boolean isMouseOver(double x, double y) {
        // ContainerEventHandler checks this before dispatching mouseClicked.
        return super.isMouseOver(x, y) || suggestionAt(x, y) >= 0;
    }
    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { super.onClick(event, doubleClick); update(); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int suggestion = suggestionAt(event.x(), event.y());
        if (event.button() == 0 && suggestion >= 0) {
            choice = suggestion;
            accept(); return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (isFocused()) {
            if ((event.key() == 258 || event.key() == 264) && !open) { update(); return true; }
            if (open) {
                if (event.key() == 256) { open = false; return true; }
                if (event.key() == 264 || event.key() == 265) { choice = Math.floorMod(choice + (event.key() == 264 ? 1 : -1), matches.size()); return true; }
                if (event.key() == 258 || event.key() == 257 || event.key() == 335) { accept(); return true; }
            }
        }
        return super.keyPressed(event);
    }
    void drawSuggestions(GuiGraphicsExtractor g) {
        if (!open || !isFocused()) return;
        int first = choice - choice % 5;
        for (int i = first; i < Math.min(first + 5, matches.size()); i++) {
            int top = getBottom() + (i - first) * 16;
            g.fill(getX(), top, getRight(), top + 16, i == choice ? 0xff36595b : 0xff17171f);
            var preset = dyes ? DyePresets.find(matches.get(i)) : null;
            if (preset != null) {
                g.fill(getX() + 5, top + 4, getX() + 13, top + 12, 0xff000000 | preset.colorAt(System.currentTimeMillis()));
                String label = preset.name() + (preset.animated() ? " · animated" : "");
                g.text(font, font.plainSubstrByWidth(label, getWidth() - 24), getX() + 19, top + 4, -1);
            } else g.text(font, font.plainSubstrByWidth(matches.get(i), getWidth() - 10), getX() + 5, top + 4, -1);
        }
    }
}

package dev.eviemod.paintbrush;

import com.mojang.blaze3d.platform.InputConstants;
import dev.eviemod.compat.ClientUi;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Persists edits immediately and keeps the parent draft in sync without replacing either screen. */
final class CommandHotkeysScreen extends CompactScreen {
    private static final int WIDTH = 540, HEIGHT = 260, KEY_WIDTH = 100;
    private final Screen parent;
    private final ModSettings.Values draft;
    private HotkeyList list;
    private EditorButton addButton;
    private Row capturing;
    private Runnable afterInput;
    private int x, y;

    CommandHotkeysScreen(Screen parent, ModSettings.Values draft) {
        super(Component.literal("Command Hotkeys"));
        this.parent = parent;
        this.draft = draft;
    }

    @Override protected void init() {
        updateViewport();
        x = (viewWidth - WIDTH) / 2; y = (viewHeight - HEIGHT) / 2;
        addRenderableWidget(new EditorButton("Back", x + WIDTH - 62, y + 8, 50, this::onClose, () -> false));
        if (list == null) {
            list = new HotkeyList();
            draft.features.skyblock.commandHotkeys.forEach(hotkey -> list.add(hotkey));
        }
        list.updateSizeAndPosition(WIDTH - 24, 148, x + 12, y + 72);
        addRenderableWidget(list);
        addButton = addRenderableWidget(new EditorButton("Add hotkey", x + 16, y + 228, 100, this::addHotkey, () -> false));
    }

    private void changed() { EviemodSettings.saveCommandHotkeys(draft); }

    private void addHotkey() {
        var hotkey = new ImportedFeatures.Hotkey();
        hotkey.disabled = true;
        draft.features.skyblock.commandHotkeys.add(hotkey);
        Row row = list.add(hotkey);
        changed();
        afterInput = () -> focus(row, row.command);
    }

    private void removeHotkey(Row row) {
        int index = list.children().indexOf(row);
        draft.features.skyblock.commandHotkeys.remove(row.hotkey);
        list.remove(row);
        changed();
        // Native input dispatch assigns focus after invoking a button. Repair it afterwards.
        afterInput = () -> {
            if (list.children().isEmpty()) { list.setFocused(null); setFocused(addButton); }
            else {
                Row next = list.children().get(Math.min(index, list.children().size() - 1));
                focus(next, next.remove);
            }
        };
    }

    private void focus(Row row, GuiEventListener widget) {
        setFocused(list);
        list.setFocused(row);
        row.setFocused(widget);
    }

    private boolean finishInput(boolean handled) {
        if (afterInput != null) {
            Runnable action = afterInput; afterInput = null;
            action.run();
        }
        return handled;
    }

    private void bind(int key) {
        capturing.hotkey.key = key;
        capturing.updateKeyLabel();
        capturing = null;
        changed();
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (capturing != null) {
            bind(event.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : event.key());
            return true;
        }
        return finishInput(super.keyPressed(event));
    }

    @Override public boolean charTyped(CharacterEvent event) {
        return capturing != null || super.charTyped(event);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (capturing != null) { bind(event.button()); return true; }
        return finishInput(super.mouseClicked(event, doubleClick));
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return capturing != null || super.mouseScrolled(mx, my, dx, dy);
    }

    @Override public void onClose() { ClientUi.setScreen(minecraft, parent); }

    @Override protected void renderContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, viewWidth, viewHeight, 0x99000000);
        EditorTheme.panel(g, x, y, WIDTH, HEIGHT);
        g.text(font, getTitle(), x + 16, y + 14, 0xffcccccc);
        g.text(font, "Use commands with or without a leading slash.", x + 16, y + 38, 0xffaaaaaa);
        g.text(font, "Key", list.getRowLeft() + 2, y + 58, 0xffcccccc);
        g.text(font, "Command", list.getRowLeft() + KEY_WIDTH + 8, y + 58, 0xffcccccc);
        renderWidgets(g, mx, my, delta);
        if (list.children().isEmpty()) g.text(font, "No hotkeys. Add one below.", x + 30, y + 88, 0xffaaaaaa);
        if (capturing != null) g.text(font, "Press a key or mouse button; Esc clears.", x + 130, y + 234, 0xffaaaaaa);
    }

    private final class HotkeyList extends ContainerObjectSelectionList<Row> {
        HotkeyList() { super(CommandHotkeysScreen.this.minecraft, WIDTH - 24, 148, y + 72, 28); }
        Row add(ImportedFeatures.Hotkey hotkey) { var row = new Row(hotkey); addEntry(row); return row; }
        void remove(Row row) { removeEntry(row); refreshScrollAmount(); }
        @Override public int getRowWidth() { return getWidth() - 28; }
        @Override protected void extractListBackground(GuiGraphicsExtractor g) {
            EditorTheme.inset(g, getX(), getY(), getWidth(), getHeight());
        }
        @Override protected void extractListSeparators(GuiGraphicsExtractor g) {}
        @Override protected void enableScissor(GuiGraphicsExtractor g) {
            // CompactScreen's logical bounds can exceed the unscaled GUI dimensions.
            g.enableScissor(getX(), getY(), getRight(), getBottom());
        }
    }

    private final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final ImportedFeatures.Hotkey hotkey;
        private final Button key;
        private final EditBox command;
        private final EditorButton enabled;
        private final EditorButton remove;
        private final List<AbstractWidget> widgets;

        Row(ImportedFeatures.Hotkey hotkey) {
            this.hotkey = hotkey;
            key = Button.builder(Component.empty(), button -> {
                capturing = this; button.setMessage(Component.literal("> … <"));
            }).bounds(0, 0, KEY_WIDTH, 20).build();
            key.setTooltip(Tooltip.create(Component.literal("Press a key or mouse button. Esc clears the binding.")));
            updateKeyLabel();
            command = new EditBox(font, 0, 0, 200, 20, Component.literal("Command"));
            // Preserve existing commands even when longer than the normal chat limit.
            command.setMaxLength(Math.max(256, hotkey.command.length()));
            command.setValue(hotkey.command);
            command.setHint(Component.literal("/command"));
            command.setResponder(value -> { hotkey.command = value; changed(); });
            enabled = new EditorButton("", 0, 0, 72, () -> {
                hotkey.disabled = !hotkey.disabled;
                updateEnabledLabel();
                changed();
            }, () -> !hotkey.disabled);
            updateEnabledLabel();
            remove = new EditorButton("Remove", 0, 0, 60, () -> removeHotkey(this), () -> false);
            widgets = List.of(key, command, enabled, remove);
        }

        void updateKeyLabel() {
            key.setMessage(hotkey.key == -1 ? Component.literal("Unbound")
                : (hotkey.key >= 0 && hotkey.key <= 7 ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM)
                    .getOrCreate(hotkey.key).getDisplayName());
        }

        private void updateEnabledLabel() {
            enabled.setMessage(Component.literal(hotkey.disabled ? "Disabled" : "Enabled"));
            enabled.setTooltip(Tooltip.create(Component.literal(hotkey.disabled ? "Enable this hotkey" : "Disable this hotkey")));
        }

        private void layout() {
            key.setPosition(getContentX(), getContentY());
            command.setPosition(getContentX() + KEY_WIDTH + 6, getContentY());
            command.setWidth(Math.max(1, getContentWidth() - KEY_WIDTH - 150));
            enabled.setPosition(getContentRight() - 138, getContentY());
            remove.setPosition(getContentRight() - 60, getContentY());
        }
        @Override public void setX(int x) { super.setX(x); layout(); }
        @Override public void setY(int y) { super.setY(y); layout(); }
        @Override public void setWidth(int width) { super.setWidth(width); layout(); }
        @Override public List<? extends GuiEventListener> children() { return widgets; }
        @Override public List<? extends NarratableEntry> narratables() { return widgets; }
        @Override public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hovered, float delta) {
            widgets.forEach(widget -> widget.extractRenderState(g, mx, my, delta));
        }
    }
}

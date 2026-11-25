package dev.eviemod.paintbrush;

import com.mojang.blaze3d.platform.InputConstants;

import dev.eviemod.compat.ClientUi;
import dev.eviemod.features.skyblock.FeatureSettings;
import java.io.IOException;
import java.util.List;
import net.azureaaron.dandelion.deps.moulconfig.gui.GuiElementComponent;
import net.azureaaron.dandelion.deps.moulconfig.gui.MoulConfigEditor;
import net.azureaaron.dandelion.deps.moulconfig.platform.MoulConfigScreenComponent;
import net.azureaaron.dandelion.deps.moulconfig.processor.ProcessedOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Real input and shared-settings navigation, run on both game targets by VersionSmokeTest. */
final class CommandHotkeysSmokeTest {
    private final ModSettings.Values original = EviemodSettings.STORE.values().copy();
    private final Screen parent;
    private final MoulConfigScreenComponent settings;
    private final CommandHotkeysScreen screen;

    CommandHotkeysSmokeTest(Screen parent) {
        this.parent = parent;
        var seed = new ModSettings.Values();
        var hotkey = new ImportedFeatures.Hotkey();
        hotkey.key = 80; hotkey.command = "/warp garden";
        seed.features.skyblock.commandHotkeys.add(hotkey);
        save(seed);
        settings = (MoulConfigScreenComponent) EviemodSettings.screen(parent, "Command Hotkeys", null);
        ClientUi.setScreen(Minecraft.getInstance(), settings);
        check(option(settings, "eviemod:visuals/hearts").set(true), "Unrelated setting draft");
        openEditor(settings);
        screen = (CommandHotkeysScreen) ClientUi.screen(Minecraft.getInstance());
    }

    void exercise() {
        try {
            var list = list(screen);
            var first = row(list, 0);
            var command = (EditBox) first.get(1);
            check(command.getValue().equals("/warp garden"), "Existing binding retained");
            checkEnabled(0, true);
            click(screen, first.get(2));
            checkEnabled(0, false);
            check(first.get(2).getMessage().getString().equals("Disabled"), "Disabled state is visible");
            checkSaved(0, 80, "/warp garden");
            key(screen, InputConstants.KEY_RETURN); // Enter toggles the focused state control.
            checkEnabled(0, true);
            check(first.get(2).getMessage().getString().equals("Enabled"), "Enabled state is visible");
            click(screen, command);
            key(screen, InputConstants.KEY_END); // End
            type(screen, " test");
            checkSaved(0, 80, "/warp garden test");
            click(screen, button(screen, "Add hotkey"));
            checkSaved(1, -1, "");
            checkEnabled(1, false);
            check(ClientUi.screen(Minecraft.getInstance()) == screen && list(screen) == list, "Add keeps screen and list instances");
            check(row(list, 0).get(1) == command && command.getValue().equals("/warp garden test"), "Add preserves edited widget");
            check(((EditBox) row(list, 1).get(1)).isFocused(), "New command receives keyboard focus");
            type(screen, "hub");
            checkSaved(1, -1, "hub");
            click(screen, row(list, 1).getFirst()); key(screen, InputConstants.KEY_K);
            checkSaved(1, 75, "hub");
            check(row(list, 1).getFirst().getMessage().getString().equalsIgnoreCase("K"), "Keyboard capture");
            click(screen, row(list, 1).getFirst()); key(screen, InputConstants.KEY_ESCAPE);
            checkSaved(1, -1, "hub");
            check(ClientUi.screen(Minecraft.getInstance()) == screen, "Esc clears binding without closing");
            check(row(list, 1).getFirst().getMessage().getString().equals("Unbound"), "Esc unbinds");
            click(screen, row(list, 1).getFirst());
            mouse(screen, InputConstants.MOUSE_BUTTON_MIDDLE, 0, 0);
            checkSaved(1, 2, "hub");
            checkEnabled(1, false);
            click(screen, row(list, 1).get(2));
            checkEnabled(1, true);
            click(screen, row(list, 1).get(2));
            checkEnabled(1, false);
            check(row(list, 1).get(1).isActive(), "Disabled command remains editable");
            // Removing a preceding row must retain the edited command and its mouse binding.
            var survivor = row(list, 1);
            click(screen, first.get(3));
            checkSaved(0, 2, "hub");
            checkEnabled(0, false);
            check(EviemodSettings.features().skyblock.commandHotkeys.size() == 1, "Remove persists immediately");
            check(list.children().size() == 1 && row(list, 0).get(1) == survivor.get(1), "Remove only the requested row");
            check(ClientUi.screen(Minecraft.getInstance()) == screen && list(screen) == list, "Remove keeps editor instances");
            check(list.getFocused() == list.children().getFirst(), "Removed row loses keyboard focus");
            for (int i = 0; i < 9; i++) click(screen, button(screen, "Add hotkey"));
            check(list.scrollAmount() > 0, "New rows scroll into view");
            var last = row(list, list.children().size() - 1);
            click(screen, last.get(1)); type(screen, "last command");
            double scroll = list.scrollAmount();
            screen.resize(screen.width, screen.height);
            check(list(screen) == list && list.scrollAmount() == scroll, "Resize retains list and scroll position");
            check(((EditBox) last.get(1)).getValue().equals("last command"), "Resize preserves text");
            for (int i = 9; i > 0; i--) {
                list.setScrollAmount(list.maxScrollAmount());
                click(screen, row(list, i).get(3));
            }
            check(list.children().size() == 1 && list.scrollAmount() == 0, "Remove clamps scroll");
            check(!EviemodSettings.features().skyblock.commandHotkeysEnabled, "Editing never enables hotkeys");
            check(!EviemodSettings.features().skyblock.maxTenHearts, "Hotkey edits do not apply unrelated settings drafts");
            click(screen, button(screen, "Back"));
            check(ClientUi.screen(Minecraft.getInstance()) == settings, "Back returns to original settings instance");
            check((Boolean) option(settings, "eviemod:visuals/hearts").get(), "Parent draft survives round trip");
            openEditor(settings);
            var reopened = (CommandHotkeysScreen) ClientUi.screen(Minecraft.getInstance());
            check(((EditBox) row(list(reopened), 0).get(1)).getValue().equals("hub"), "Reopening retains immediate edits");
            check(row(list(reopened), 0).get(2).getMessage().getString().equals("Disabled"), "Reopening retains disabled state");
            key(reopened, InputConstants.KEY_ESCAPE);
            check(ClientUi.screen(Minecraft.getInstance()) == settings, "Esc returns to original settings");
            settings.onClose();
            check(ClientUi.screen(Minecraft.getInstance()) == parent, "Settings returns to original parent");
            reload();
            var saved = EviemodSettings.features().skyblock;
            check(saved.commandHotkeys.size() == 1 && saved.commandHotkeys.getFirst().key == 2
                && saved.commandHotkeys.getFirst().command.equals("hub") && saved.commandHotkeys.getFirst().disabled,
                "Mouse binding, command and disabled state persisted");
            check(!saved.commandHotkeysEnabled && saved.maxTenHearts, "Enable state and unrelated setting persisted");
            // Deleting the final binding must persist an explicitly empty list.
            var emptySettings = (MoulConfigScreenComponent) EviemodSettings.screen(parent);
            ClientUi.setScreen(Minecraft.getInstance(), emptySettings); openEditor(emptySettings);
            var emptyEditor = (CommandHotkeysScreen) ClientUi.screen(Minecraft.getInstance());
            click(emptyEditor, row(list(emptyEditor), 0).get(3));
            check(emptyEditor.getFocused() == button(emptyEditor, "Add hotkey"), "Last removal focuses Add");
            reload();
            check(EviemodSettings.features().skyblock.commandHotkeys.isEmpty(), "Empty list persists before leaving editor");
            click(emptyEditor, button(emptyEditor, "Back")); emptySettings.onClose(); reload();
            check(EviemodSettings.features().skyblock.commandHotkeys.isEmpty(), "Empty list persists");
        } finally { save(original); }
    }

    static void openEditor(MoulConfigScreenComponent settings) { ((Runnable) option(settings, "eviemod:hotkeys/editor").get()).run(); }
    private static ProcessedOption option(MoulConfigScreenComponent settings, String path) {
        var editor = (MoulConfigEditor<?>) ((GuiElementComponent) settings.getGuiContext().getRoot()).getElement();
        return editor.getAllOptions().stream().filter(option -> {
            try { return path.equals(option.getDebugDeclarationLocation()); }
            catch (UnsupportedOperationException ignored) { return false; }
        }).findFirst().orElseThrow();
    }
    private static ContainerObjectSelectionList<?> list(Screen screen) {
        return screen.children().stream().filter(ContainerObjectSelectionList.class::isInstance)
            .map(child -> (ContainerObjectSelectionList<?>) child).findFirst().orElseThrow();
    }
    private static List<AbstractWidget> row(ContainerObjectSelectionList<?> list, int index) {
        return ((ContainerEventHandler) list.children().get(index)).children().stream().map(AbstractWidget.class::cast).toList();
    }
    private static AbstractWidget button(Screen screen, String text) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().equals(text)).findFirst().orElseThrow();
    }
    private static void click(Screen screen, AbstractWidget widget) {
        var viewport = EditorViewport.fit(screen.width, screen.height, Minecraft.getInstance().getWindow().getGuiScale());
        mouse(screen, InputConstants.MOUSE_BUTTON_LEFT, (widget.getX() + widget.getWidth() / 2.0) * viewport.scale(), (widget.getY() + 10) * viewport.scale());
    }
    private static void mouse(Screen screen, int button, double x, double y) {
        var event = new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
        check(screen.mouseClicked(event, false), "Mouse input handled"); screen.mouseReleased(event);
    }
    private static void key(Screen screen, int key) { screen.keyPressed(dev.eviemod.compat.VersionFixtures.keyEvent(key)); }
    private static void type(Screen screen, String text) { text.codePoints().forEach(cp -> screen.charTyped(new CharacterEvent(cp))); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void checkEnabled(int index, boolean enabled) {
        check(FeatureSettings.isCommandHotkeyEnabled(index) == enabled, "Runtime sees binding state immediately");
        reload();
        check(FeatureSettings.isCommandHotkeyEnabled(index) == enabled, "Binding state persists immediately");
    }
    private static void checkSaved(int index, int key, String command) {
        var live = EviemodSettings.features().skyblock.commandHotkeys.get(index);
        check(live.key == key && live.command.equals(command), "Edit takes effect before leaving editor");
        reload();
        var saved = EviemodSettings.features().skyblock.commandHotkeys.get(index);
        check(saved.key == key && saved.command.equals(command), "Edit persists before leaving editor");
    }
    private static void save(ModSettings.Values values) {
        try { EviemodSettings.STORE.save(values); } catch (IOException e) { throw new AssertionError(e); }
    }
    private static void reload() {
        try { EviemodSettings.STORE.load(); } catch (IOException e) { throw new AssertionError(e); }
    }
}

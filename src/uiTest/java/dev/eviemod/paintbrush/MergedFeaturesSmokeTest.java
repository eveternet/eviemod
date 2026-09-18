package dev.eviemod.paintbrush;

import com.mojang.blaze3d.platform.InputConstants;
import dev.eviemod.features.skyblock.FeatureSettings;
import net.azureaaron.dandelion.deps.moulconfig.gui.*;
import net.azureaaron.dandelion.deps.moulconfig.platform.MoulConfigScreenComponent;
import net.azureaaron.dandelion.deps.moulconfig.processor.ProcessedOption;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;


/** Offline fixture: exercises the actual generated MoulConfig controls and transformed mixin targets. */
final class MergedFeaturesSmokeTest {
    private int ticks, startupTicks;
    private Screen parent;
    void start() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (ticks == 0) {
                if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null) return;
                if (++startupTicks < 60) return;
                parent = client.screen;
                for (String type : new String[]{"net.minecraft.client.multiplayer.ClientPacketListener", "net.minecraft.client.gui.Gui",
                        "net.minecraft.world.level.border.WorldBorder", "net.minecraft.client.renderer.ItemInHandRenderer", "net.minecraft.client.MouseHandler"}) {
                    try { Class.forName(type); } catch (ClassNotFoundException e) { throw new AssertionError(e); }
                }
                client.setScreen(EviemodSettings.screen(parent));
                check(editor().getAllCategories().size() == 7, "All shared/imported categories must exist");
                check(!FeatureSettings.isSoulWhipFixEnabled(), "Fixture Soul Whip disabled migration");
                check(!FeatureSettings.isMaxTenHeartsEnabled(), "Fixture hearts disabled migration");
                check(EviemodSettings.features().garden.teleportPlot == 19, "Fixture Garden migration");
            }
            ticks++;
            switch (ticks) {
                case 10 -> select("eviemod:skyblock_visuals");
                case 25 -> capture(client, "merged-visuals.png");
                case 30 -> {
                    check(option("eviemod:soul_whip/enabled").set(true), "Soul Whip UI binding");
                    check(option("eviemod:visuals/hearts").set(true), "Health UI binding");
                    client.screen.onClose();
                    check(FeatureSettings.isSoulWhipFixEnabled(), "Soul Whip runtime sees saved UI choice");
                    check(FeatureSettings.isMaxTenHeartsEnabled(), "Health runtime sees saved UI choice");
                    check(client.screen == parent, "Shared UI returns to original parent");
                    client.setScreen(EviemodSettings.screen(parent)); select("eviemod:garden");
                }
                case 45 -> capture(client, "merged-garden.png");
                case 50 -> {
                    check(option("eviemod:garden/plot").set(7F), "Plot UI binding");
                    check(option("eviemod:garden/mouse_lock").set(true), "Mouse lock UI binding");
                    captureKey("eviemod:garden/key/tptoplot", 80, false);
                    client.screen.onClose(); client.setScreen(EviemodSettings.screen(parent)); select("eviemod:party_commands");
                    check(EviemodSettings.features().garden.teleportPlot == 7, "Plot persisted");
                    check(EviemodSettings.features().garden.mouseLock, "Mouse lock persisted");
                    check(EviemodSettings.features().garden.keys.get("tptoplot").equals("key.keyboard.p"), "Garden key persisted");
                }
                case 65 -> capture(client, "merged-party.png");
                case 70 -> {
                    check(option("eviemod:commands/warp/party").set(false), "Party channel UI binding");
                    check(option("eviemod:commands/warp/guild").set(true), "Guild channel UI binding");
                    client.screen.onClose(); client.setScreen(EviemodSettings.screen(parent)); select("eviemod:command_hotkeys");
                }
                case 85 -> capture(client, "merged-hotkeys.png");
                case 90 -> {
                    check(option("eviemod:hotkeys/0/command").set("/warp garden"), "Command text UI binding");
                    captureKey("eviemod:hotkeys/0/key", 2, true);
                    action("eviemod:hotkeys/add");
                    check(EviemodSettings.features().skyblock.commandHotkeys.size() == 2, "Add control persists and rebuilds");
                    check(EviemodSettings.features().skyblock.commandHotkeys.get(0).key == 2, "Mouse binding retained");
                    check(EviemodSettings.features().skyblock.commandHotkeys.get(0).command.equals("/warp garden"), "Command retained across Add");
                }
                case 100 -> {
                    action("eviemod:hotkeys/1/remove");
                    check(EviemodSettings.features().skyblock.commandHotkeys.size() == 1, "Remove control persists and rebuilds");
                }
                case 110 -> {
                    client.screen.onClose(); check(client.screen == parent, "Add/remove preserve settings parent");
                    EviemodSettings.load();
                    check(EviemodSettings.features().garden.teleportPlot == 7, "Reload never reapplies legacy plot");
                    check(FeatureSettings.isSoulWhipFixEnabled(), "Reload never reapplies legacy Soul Whip choice");
                    check(!FeatureSettings.isPartyCommandEnabled("warp", dev.eviemod.features.skyblock.PartyCommandController.CommandChannel.PARTY), "Party disabled state");
                    check(FeatureSettings.isPartyCommandEnabled("warp", dev.eviemod.features.skyblock.PartyCommandController.CommandChannel.GUILD), "Guild enabled state");
                    org.slf4j.LoggerFactory.getLogger("eviemod-fixture").info("MERGED_FEATURES_SMOKE_PASS");
                    client.stop();
                }
            }
        });
    }
    private static MoulConfigEditor<?> editor() {
        var screen = (MoulConfigScreenComponent)Minecraft.getInstance().screen;
        return (MoulConfigEditor<?>)((GuiElementComponent)screen.getGuiContext().getRoot()).getElement();
    }
    private static void select(String category) {
        var editor = editor();
        editor.setSelectedCategory(editor.getAllCategories().values().stream().filter(value -> value.getIdentifier().equals(category)).findFirst().orElseThrow());
    }
    private static ProcessedOption option(String path) {
        return editor().getAllOptions().stream().filter(option -> option.getDebugDeclarationLocation().equals(path)).findFirst()
            .orElseThrow(() -> new AssertionError("Missing option " + path + " in " + editor().getAllOptions().stream().map(ProcessedOption::getDebugDeclarationLocation).toList()));
    }
    private static void action(String path) { ((Runnable)option(path).get()).run(); }
    private static void captureKey(String path, int code, boolean mouse) {
        var control = (net.azureaaron.dandelion.deps.moulconfig.gui.editors.ComponentEditor)option(path).getEditor();
        GuiComponent key = control.getDelegate().foldRecursive((GuiComponent)null,
            (component, found) -> component.getClass().getSimpleName().equals("KeyMappingComponent") ? component : found);
        check(key != null, "Standard key capture control");
        var screen = (MoulConfigScreenComponent)Minecraft.getInstance().screen;
        var context = new GuiImmediateContext(screen.createContext().getRenderContext(), 0, 0, 0, 30, 0, 15, 0, 15, 0F, 15F);
        check(key.mouseEvent(new MouseEvent.Click(0, true), context), "Enter key capture");
        key.mouseEvent(new MouseEvent.Click(0, false), context);
        if (mouse) check(key.mouseEvent(new MouseEvent.Click(code, true), context), "Capture mouse button");
        else {
            key.keyboardEvent(new KeyboardEvent.KeyPressed(code, 0, true), context);
            check(key.keyboardEvent(new KeyboardEvent.KeyPressed(code, 0, false), context), "Capture keyboard key");
        }
    }
    private static void capture(Minecraft client, String name) {
        net.minecraft.client.Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), 1, message -> {});
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

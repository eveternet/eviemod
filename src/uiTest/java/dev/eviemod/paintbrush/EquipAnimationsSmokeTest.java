package dev.eviemod.paintbrush;

import dev.eviemod.compat.ClientUi;
import dev.eviemod.features.skyblock.FeatureSettings;
import dev.eviemod.features.soulwhip.EquipAnimationAccess;
import net.azureaaron.dandelion.deps.moulconfig.gui.GuiElementComponent;
import net.azureaaron.dandelion.deps.moulconfig.gui.MoulConfigEditor;
import net.azureaaron.dandelion.deps.moulconfig.platform.MoulConfigScreenComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;

/** Real shared UI binding and transformed itemUsed hook, without a server or player. */
final class EquipAnimationsSmokeTest {
    static void exercise(Screen parent) {
        var client = Minecraft.getInstance();
        var renderer = client.gameRenderer.itemInHandRenderer;
        check((Object) renderer instanceof EquipAnimationAccess, "Attack bridge applied");
        var original = EviemodSettings.STORE.values().copy();
        try {
            var main = renderer.getClass().getDeclaredField("mainHandHeight");
            var off = renderer.getClass().getDeclaredField("offHandHeight");
            main.setAccessible(true);
            off.setAccessible(true);
            float originalMain = main.getFloat(renderer), originalOff = off.getFloat(renderer);
            try {
                for (boolean enabled : new boolean[]{true, false}) {
                    var screen = (MoulConfigScreenComponent) EviemodSettings.screen(parent);
                    ClientUi.setScreen(client, screen);
                    var editor = (MoulConfigEditor<?>) ((GuiElementComponent) screen.getGuiContext().getRoot()).getElement();
                    var options = editor.getAllOptions().stream()
                        .filter(option -> option.getName().equals("Disable Unintended Equip Animations")).toList();
                    check(options.size() == 1, "Exactly one consolidated toggle");
                    check(editor.getAllOptions().stream().noneMatch(option -> option.getName().equals("Soul Whip Fix")),
                        "Old toggle removed");
                    check(options.getFirst().set(enabled), "Consolidated UI binding");
                    screen.onClose();
                    check(ClientUi.screen(client) == parent, "Settings navigation preserved");
                    EviemodSettings.load();
                    check(FeatureSettings.areUnintendedEquipAnimationsDisabled() == enabled, "Saved choice controls runtime");
                    main.setFloat(renderer, 0.75F);
                    off.setFloat(renderer, 0.75F);
                    renderer.itemUsed(InteractionHand.MAIN_HAND);
                    check(main.getFloat(renderer) == (enabled ? 0.75F : 0.0F), "Soul Whip main-hand use dip");
                    renderer.itemUsed(InteractionHand.OFF_HAND);
                    check(off.getFloat(renderer) == 0.0F, "Offhand use remains vanilla");
                }
            } finally {
                main.setFloat(renderer, originalMain);
                off.setFloat(renderer, originalOff);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Equip renderer fixture", e);
        } finally {
            try { EviemodSettings.STORE.save(original); }
            catch (java.io.IOException e) { throw new AssertionError(e); }
        }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

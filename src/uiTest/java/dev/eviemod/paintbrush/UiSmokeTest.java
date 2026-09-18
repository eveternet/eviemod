package dev.eviemod.paintbrush;

import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class UiSmokeTest implements ClientModInitializer {
    private boolean opened;
    private int captureTicks;
    @Override public void onInitializeClient() {
        if (Boolean.getBoolean("eviemod.mergedCapture")) { new MergedFeaturesSmokeTest().start(); return; }
        if (Boolean.getBoolean("eviemod.packCapture")) { new PackBypassSmokeTest().start(); return; }
        if (Boolean.getBoolean("eviemod.textureCapture")) { new TextureSmokeTest().start(); return; }
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen) {
                net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(
                    net.minecraft.client.gui.components.Button.builder(Component.literal("eviemod test"), b -> client.setScreen(new RarityPreviewScreen()))
                        .bounds(120, 5, 110, 20).build());
                net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(
                    net.minecraft.client.gui.components.Button.builder(Component.literal("Paint Brush test"), b -> openEditor(client))
                        .bounds(5, 5, 110, 20).build());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (opened && Boolean.getBoolean("eviemod.capture")) {
                captureTicks++;
                if (captureTicks == 30 || captureTicks == 80 || captureTicks == 140 || captureTicks == 185 || captureTicks == 200) {
                    String name = captureTicks == 30 ? "rarity-square.png" : captureTicks == 80 ? "settings.png" : captureTicks == 140 ? "paintbrush-editor.png" : captureTicks == 185 ? "paintbrush-restored.png" : "paintbrush-close.png";
                    net.minecraft.client.Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), 1,
                        message -> org.slf4j.LoggerFactory.getLogger("eviemod-fixture").info(message.getString()));
                }
                if (captureTicks == 40) client.setScreen(EviemodSettings.screen(client.screen));
                if (captureTicks == 100) {
                    openEditor(client);
                }
                if (captureTicks == 108) { editorClick(client, 30, 115); type(client, "minecraft:leather_chestplate"); }
                if (captureTicks == 110) key(client, 258);
                if (captureTicks == 112) {
                    var dye = client.screen.children().stream()
                        .filter(child -> child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                            && widget.getMessage().getString().equals("Dye"))
                        .map(child -> (net.minecraft.client.gui.components.AbstractWidget) child).findFirst().orElseThrow();
                    if (!dye.active) throw new AssertionError("Gold armor with a leather model must enable Dye");
                    editorClick(client, 200, 82);
                    editorClick(client, 30, 115);
                    type(client, "#FF88CC");
                    // Force loading the equipment renderer to validate the armor mixin at runtime.
                    try { Class.forName("net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer"); }
                    catch (ClassNotFoundException e) { throw new AssertionError(e); }
                }
                if (captureTicks == 115) editorClick(client, 260, 82);
                if (captureTicks == 120) { editorClick(client, 30, 115); type(client, "Fixture name"); }
                if (captureTicks == 148) editorClick(client, 50, 82);
                if (captureTicks == 155) editorClick(client, 260, 82);
                if (captureTicks == 172) editorClick(client, 40, 50);
                if (captureTicks == 178) {
                    if (client.screen instanceof PaintBrushScreen) throw new AssertionError("Choose item must open the picker");
                    client.screen.onClose();
                }
                if (captureTicks == 192) client.screen.onClose();
                if (captureTicks == 205) {
                    if (!(client.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen)) throw new AssertionError("Unapplied Paint Brush drafts must prompt before closing");
                    var discard = client.screen.children().stream()
                        .filter(child -> child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals("Discard edits"))
                        .map(child -> (net.minecraft.client.gui.components.Button) child).findFirst().orElseThrow();
                    click(client, discard.getX() + 3, discard.getY() + 3);
                    if (!(client.screen instanceof net.azureaaron.dandelion.deps.moulconfig.platform.MoulConfigScreenComponent))
                        throw new AssertionError("Editor must return to its settings parent");
                    client.stop();
                }
            }
            if (!opened && client.screen instanceof TitleScreen && client.getOverlay() == null) {
                opened = true;
                net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
                    .build(net.minecraft.data.registries.VanillaRegistries.createLookup())
                    .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
                client.setScreen(new RarityPreviewScreen());
            }
        });
    }
    private static void openEditor(net.minecraft.client.Minecraft client) {
        client.setScreen(new PaintBrushScreen(EviemodSettings.screen(null), List.of(
                    item(Items.GOLDEN_CHESTPLATE, "Gold to leather fixture", "eb11aa00-052d-48fa-bf56-09c2e1a4a12d"),
                    item(Items.LEATHER_CHESTPLATE, "Crimson Chestplate", "eb11aa00-052d-48fa-bf56-09c2e1a4a12e"),
                    item(Items.DIAMOND_SWORD, "Aspect of the Dragons", "eb11aa00-052d-48fa-bf56-09c2e1a4a12f"),
                    item(Items.APPLE, "No UUID", null))));
    }

    private static void editorClick(net.minecraft.client.Minecraft client, int x, int y) {
        var viewport = EditorViewport.fit(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), client.getWindow().getGuiScale());
        int px = (viewport.width() - 440) / 2, py = (viewport.height() - 260) / 2;
        click(client, Math.round((px + x) * viewport.scale()), Math.round((py + y) * viewport.scale()));
    }

    private static void click(net.minecraft.client.Minecraft client, int x, int y) {
        if (client.screen instanceof net.azureaaron.dandelion.deps.moulconfig.platform.MoulConfigScreenComponent screen) {
            var component = (net.azureaaron.dandelion.deps.moulconfig.gui.GuiElementComponent) screen.getGuiContext().getRoot();
            var editor = (net.azureaaron.dandelion.deps.moulconfig.gui.MoulConfigEditor<?>) component.getElement();
            editor.mouseInput(x, y, new net.azureaaron.dandelion.deps.moulconfig.gui.MouseEvent.Click(0, true));
            editor.mouseInput(x, y, new net.azureaaron.dandelion.deps.moulconfig.gui.MouseEvent.Click(0, false));
        } else {
            var event = new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0));
            client.screen.mouseClicked(event, false); client.screen.mouseReleased(event);
        }
    }
    private static void key(net.minecraft.client.Minecraft client, int key) {
        client.screen.keyPressed(new net.minecraft.client.input.KeyEvent(key, 0, 0));
    }
    private static void type(net.minecraft.client.Minecraft client, String text) {
        text.codePoints().forEach(cp -> client.screen.charTyped(new net.minecraft.client.input.CharacterEvent(cp)));
    }

    private static ItemStack item(Item item, String name, String uuid) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withColor(0xffaa00));
        CompoundTag tag = new CompoundTag(); tag.putString("id", "TEST_ITEM");
        if (uuid != null) tag.putString("uuid", uuid);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }
}

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
                    String name = captureTicks == 30 ? "rarity-square.png" : captureTicks == 80 ? "settings.png" : captureTicks == 140 ? "paintbrush-embedded.png" : captureTicks == 185 ? "paintbrush-restored.png" : "paintbrush-close.png";
                    net.minecraft.client.Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), 1,
                        message -> org.slf4j.LoggerFactory.getLogger("eviemod-fixture").info(message.getString()));
                }
                if (captureTicks == 40) client.setScreen(EviemodSettings.screen(client.screen));
                if (captureTicks == 100) {
                    openEditor(client);
                }
                if (captureTicks == 108) { click(client, 230, 115); type(client, "minecraft:diamond"); }
                if (captureTicks == 110) { key(client, 264); key(client, 258); }
                if (captureTicks == 115) click(client, 347, 99);
                if (captureTicks == 120) { click(client, 230, 115); type(client, "Fixture name"); }
                if (captureTicks == 148) { click(client, 345, 55); key(client, 269); for (int i=0; i<20; i++) key(client, 259); }
                if (captureTicks == 155) click(client, 80, 82);
                if (captureTicks == 172) click(client, 80, 97);
                if (captureTicks == 178) click(client, 203, 83);
                if (captureTicks == 180) {
                    if (client.screen instanceof net.azureaaron.dandelion.deps.moulconfig.platform.MoulConfigScreenComponent)
                        throw new AssertionError("Choose item must open the picker");
                    client.screen.onClose();
                }
                if (captureTicks == 192) client.screen.onClose();
                if (captureTicks == 205) {
                    if (!(client.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen)) throw new AssertionError("Unapplied Paint Brush drafts must prompt before closing");
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
        client.setScreen(EviemodSettings.screen(null, "paint brush", List.of(
                    item(Items.BOW, "Precise Juju Shortbow", "eb11aa00-052d-48fa-bf56-09c2e1a4a12d"),
                    item(Items.LEATHER_CHESTPLATE, "Crimson Chestplate", "eb11aa00-052d-48fa-bf56-09c2e1a4a12e"),
                    item(Items.DIAMOND_SWORD, "Aspect of the Dragons", "eb11aa00-052d-48fa-bf56-09c2e1a4a12f"),
                    item(Items.APPLE, "No UUID", null))));
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

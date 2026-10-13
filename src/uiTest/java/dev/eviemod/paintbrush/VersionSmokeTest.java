package dev.eviemod.paintbrush;

import dev.eviemod.compat.ClientUi;
import net.azureaaron.dandelion.deps.moulconfig.platform.MoulConfigScreenComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.LoggerFactory;

/** Offline version-port fixture: apply every main-mod mixin and exercise real settings screens. */
final class VersionSmokeTest {
    private int ticks;
    private Screen parent;

    void start() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            // Fresh CI game directories otherwise wait for the onboarding button.
            client.options.onboardAccessibility = false;
            ClientUi.setScreen(client, new TitleScreen());
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (parent == null) {
                if (!(ClientUi.screen(client) instanceof TitleScreen) || ClientUi.overlay(client) != null) return;
                parent = ClientUi.screen(client);
                // Class loading runs the required mixin transforms, including targets unused on the title screen.
                for (String target : new String[] {
                    "net.minecraft.client.gui.Gui",
                    "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen",
                    "net.minecraft.client.gui.components.EditBox",
                    "net.minecraft.client.MouseHandler",
                    "net.minecraft.client.multiplayer.ClientPacketListener",
                    "net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl",
                    "net.minecraft.world.level.border.WorldBorder",
                    "net.minecraft.client.renderer.ItemInHandRenderer",
                    "net.minecraft.client.renderer.item.ItemModelResolver",
                    "net.minecraft.world.item.ItemStack",
                    "net.minecraft.world.item.component.DyedItemColor",
                    "net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer",
                    "net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer",
                    "net.minecraft.client.renderer.entity.LivingEntityRenderer"
                }) {
                    try { Class.forName(target); }
                    catch (ClassNotFoundException e) { throw new AssertionError(target, e); }
                }
                ClientUi.setScreen(client, EviemodSettings.screen(parent));
                return;
            }
            ticks++;
            if (ticks == 5) {
                var settings = (MoulConfigScreenComponent) ClientUi.screen(client);
                var editor = (net.azureaaron.dandelion.deps.moulconfig.gui.MoulConfigEditor<?>)
                    ((net.azureaaron.dandelion.deps.moulconfig.gui.GuiElementComponent) settings.getGuiContext().getRoot()).getElement();
                if (!editor.getAllCategories().values().stream().map(category -> category.getDisplayName().getText()).toList()
                    .equals(java.util.List.of("Appearance", "Hypixel Pack", "Quick Navigation", "Mining", "Garden", "Dungeons", "Experiments", "Chat Commands", "Command Hotkeys")))
                    throw new AssertionError("Combined feature categories differ from the expected nine-category order");
                var mining = editor.getAllCategories().values().stream()
                    .filter(value -> value.getIdentifier().equals("eviemod:compass/category")).findFirst().orElseThrow();
                editor.setSelectedCategory(mining);
                var enabled = editor.getAllOptions().stream().filter(value -> compassOption(value, "enabled")).findFirst().orElseThrow();
                if ((Boolean) enabled.get()) throw new AssertionError("Compass defaults on in actual settings UI");
                enabled.set(true);
                if (!(Boolean) enabled.get()) throw new AssertionError("Compass toggle did not update its draft");
                var clear = editor.getAllOptions().stream().filter(value -> compassOption(value, "clear")).findFirst().orElseThrow();
                ((Runnable) clear.get()).run();
                if (EviemodSettings.features().skyblock.wishingCompass) throw new AssertionError("Clearing observations saved an unfinished settings draft");
                enabled.set(false);
                org.slf4j.LoggerFactory.getLogger("eviemod-fixture").info("EVIEMOD_COMPASS_SETTINGS_SMOKE_OK");
            }
            if (ticks == 10) {
                var settings = (MoulConfigScreenComponent) ClientUi.screen(client);
                var editor = (net.azureaaron.dandelion.deps.moulconfig.gui.MoulConfigEditor<?>)
                    ((net.azureaaron.dandelion.deps.moulconfig.gui.GuiElementComponent) settings.getGuiContext().getRoot()).getElement();
                var experiments = editor.getAllCategories().values().stream()
                    .filter(value -> value.getIdentifier().equals("eviemod:experiments")).findFirst().orElseThrow();
                editor.setSelectedCategory(experiments);
                for (String name : new String[]{"chronomatron", "ultrasequencer", "superpairs", "protection"}) {
                    var option = editor.getAllOptions().stream().filter(value -> {
                        try { return value.getDebugDeclarationLocation().equals("eviemod:experiments/" + name); }
                        catch (UnsupportedOperationException e) { return false; }
                    }).findFirst().orElseThrow();
                    if ((Boolean) option.get()) throw new AssertionError("Experiment helper defaults on: " + name);
                    option.set(true);
                    if (!(Boolean) option.get()) throw new AssertionError("Experiment toggle did not update: " + name);
                    option.set(false);
                }
                if (EviemodSettings.features().experiments.chronomatron
                    || EviemodSettings.features().skyblock.wishingCompass)
                    throw new AssertionError("Unfinished shared settings draft reached live feature state");
                org.slf4j.LoggerFactory.getLogger("eviemod-fixture").info("EVIEMOD_EXPERIMENT_SETTINGS_SMOKE_OK");
            }
            if (ticks == 20) {
                if (!(ClientUi.screen(client) instanceof MoulConfigScreenComponent))
                    throw new AssertionError("Shared settings screen did not open");
                ClientUi.screen(client).onClose();
                if (ClientUi.screen(client) != parent) throw new AssertionError("Settings did not return to parent");
                // Without joining a server, vanilla has not bound item defaults
                // from the registry lookup yet. Use the same offline binding as
                // the item/model unit fixtures before constructing a test stack.
                net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
                    .build(net.minecraft.data.registries.VanillaRegistries.createLookup())
                    .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
                var item = net.minecraft.world.item.Items.GOLDEN_CHESTPLATE.getDefaultInstance();
                var tag = new net.minecraft.nbt.CompoundTag();
                tag.putString("id", "TEST_ITEM");
                tag.putString("uuid", "eb11aa00-052d-48fa-bf56-09c2e1a4a12d");
                item.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag));
                ClientUi.setScreen(client, new PaintBrushScreen(parent, java.util.List.of(item)));
            }
            if (ticks == 40) {
                if (!(ClientUi.screen(client) instanceof PaintBrushScreen))
                    throw new AssertionError("Paint Brush screen did not open");
                ClientUi.screen(client).onClose();
                if (ClientUi.screen(client) != parent) throw new AssertionError("Paint Brush did not return to parent");
                new QuickNavigationSmokeTest().start(parent);
            }
        });
    }
    private static boolean compassOption(net.azureaaron.dandelion.deps.moulconfig.processor.ProcessedOption option, String name) {
        try { return option.getDebugDeclarationLocation().equals("eviemod:compass/" + name); }
        catch (UnsupportedOperationException e) { return false; }
    }
}

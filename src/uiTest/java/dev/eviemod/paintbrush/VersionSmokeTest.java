package dev.eviemod.paintbrush;

import dev.eviemod.compat.ClientUi;
import net.azureaaron.dandelion.deps.moulconfig.platform.MoulConfigScreenComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.LoggerFactory;

/** Offline version-port fixture: apply every main-mod mixin and exercise real settings screens. */
final class VersionSmokeTest {
    private int ticks;
    private Screen parent;

    void start() {
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
            if (ticks == 20) {
                if (!(ClientUi.screen(client) instanceof MoulConfigScreenComponent))
                    throw new AssertionError("Shared settings screen did not open");
                ClientUi.screen(client).onClose();
                if (ClientUi.screen(client) != parent) throw new AssertionError("Settings did not return to parent");
                var item = net.minecraft.world.item.Items.GOLDEN_CHESTPLATE.getDefaultInstance();
                var tag = new net.minecraft.nbt.CompoundTag();
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
                LoggerFactory.getLogger("eviemod-fixture").info("EVIEMOD_VERSION_SMOKE_OK");
                client.stop();
            }
        });
    }
}

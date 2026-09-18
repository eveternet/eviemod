package dev.eviemod.paintbrush;

import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.TitleScreen;

/** Offline runtime check: exercises the actual settings renderer and applies the packet mixin. */
final class PackBypassSmokeTest {
    private int ticks;
    private boolean opened;
    void start() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!opened && client.screen instanceof TitleScreen && client.getOverlay() == null) {
                try { Class.forName("net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl"); }
                catch (ClassNotFoundException e) { throw new AssertionError(e); }
                if (new ModSettings.Values().texturePackBypasser) throw new AssertionError("Bypass must default off");
                var category = SettingsCategories.create(new ModSettings.Values(), List.of()).stream()
                    .filter(value -> value.name().getString().equals("Hypixel Pack")).findFirst().orElseThrow();
                if (category.rootGroup().options().size() != 2) throw new AssertionError("Expected one toggle and one action");
                client.setScreen(EviemodSettings.screen(null, "Hypixel Pack", null)); opened = true;
            }
            if (opened && ++ticks == 40) {
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "texture-pack-bypasser.png", client.getMainRenderTarget(), 1,
                    message -> org.slf4j.LoggerFactory.getLogger("eviemod-fixture").info(message.getString()));
            }
            if (opened && ticks == 60) {
                org.slf4j.LoggerFactory.getLogger("eviemod-fixture").info("Hypixel Pack settings and mixin smoke test passed");
                client.stop();
            }
        });
    }
}

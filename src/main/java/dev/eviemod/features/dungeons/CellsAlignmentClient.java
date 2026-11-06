package dev.eviemod.features.dungeons;

import dev.eviemod.paintbrush.EviemodSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

/** Client-thread adapter. Polling frequency never determines countdown speed. */
public final class CellsAlignmentClient {
    private static final CellsAlignmentSession SESSION = new CellsAlignmentSession();

    public static void initialize() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || !CellsAlignmentTimer.matches(message.getString())) return;
            var client = Minecraft.getInstance();
            updateContext(client);
            SESSION.receive(message.getString(), false, System.nanoTime());
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (SESSION.active()) {
                updateContext(client);
                SESSION.frame(System.nanoTime());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SESSION.reset());
    }

    private static void updateContext(Minecraft client) {
        boolean enabled = EviemodSettings.features().dungeons.cellsAlignmentTimer;
        SESSION.context(client.level, client.getConnection(), enabled && DungeonTankDetector.eligible(client));
        if (!enabled) SESSION.reset();
    }

    /** Invoked after vanilla has handled a server world-age packet, on the client thread. */
    public static void onServerTime(long serverTick) {
        var client = Minecraft.getInstance();
        updateContext(client);
        if (EviemodSettings.features().dungeons.cellsAlignmentTimer && client.level != null && client.getConnection() != null)
            SESSION.sample(serverTick, System.nanoTime());
    }

    public static CellsAlignmentTimer.Frame frame() {
        if (!SESSION.active()) return null;
        updateContext(Minecraft.getInstance());
        return SESSION.frame(System.nanoTime());
    }

    private CellsAlignmentClient() {}
}

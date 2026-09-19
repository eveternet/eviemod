package dev.eviemod.features.scoresync;

import dev.eviemod.features.skyblock.SkyblockContext;
import dev.eviemod.paintbrush.EviemodSettings;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.LoggerFactory;

/** Fabric, configuration, timing and normal chat transport adapter. All relay mutation runs on the client thread. */
public final class ScoreSyncClient {
    // TEMPORARY: log callback input before the existing relay eligibility filters.
    private static final org.slf4j.Logger DEBUG = LoggerFactory.getLogger("eviemod-score-sync-debug");
    private static final NoammScoreHook HOOK = new NoammScoreHook();
    private static boolean available, sending;
    private static ScheduledExecutorService timer;
    private static Object level;
    private static long session;
    private static final ScoreRelay RELAY = new ScoreRelay(() -> System.nanoTime() / 1_000_000,
        ScoreSyncClient::after, command -> {
            var client = Minecraft.getInstance();
            if (!active(client)) throw new IllegalStateException("Relay unavailable");
            sending = true;
            try { client.getConnection().sendCommand(command); }
            finally { sending = false; }
        });

    public static boolean available() { return available; }

    public static void initialize() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (!FabricLoader.getInstance().isModLoaded("noammaddons")) return;
            try {
                available = HOOK.register(true, ScoreSyncClient.class.getClassLoader(), raw -> {
                    if (active(client)) RELAY.incoming(raw);
                }, ScoreSyncClient::failed);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                failed();
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            available = false; reset(); HOOK.close();
            if (timer != null) timer.shutdownNow();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!available) return;
            if (level != client.level || !active(client)) { reset(); level = client.level; }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        // GAME is Hypixel's unsigned system chat; CHAT covers signed chat without treating it as an error response.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            DEBUG.info("chat callback=GAME input={} overlay={}", message.getString(), overlay);
            var client = Minecraft.getInstance();
            if (overlay || !active(client)) return;
            observe(message.getString(), client);
        });
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> {
            DEBUG.info("chat callback=CHAT input={}", message.getString());
            var client = Minecraft.getInstance();
            if (active(client) && ScoreRelay.stripFormatting(message.getString()).startsWith("Party > "))
                observe(message.getString(), client);
        });
        ClientSendMessageEvents.COMMAND.register(command -> { if (available && !sending) RELAY.foreignSend(); });
        ClientSendMessageEvents.CHAT.register(message -> { if (available && !sending) RELAY.foreignSend(); });
    }

    private static void observe(String text, Minecraft client) {
        int floor = DungeonChatContext.floor(client);
        String ownPattern = "^Party > (?:\\[[^]]*\\] )?" + Pattern.quote(client.player.getGameProfile().name()) + ": .+$";
        boolean own = ScoreRelay.stripFormatting(text).matches(ownPattern);
        RELAY.chat(text, floor >= 0, floor, own);
    }

    private static boolean active(Minecraft client) {
        return available && EviemodSettings.features().skyblock.noammScoreSync
            && client.player != null && client.getConnection() != null && SkyblockContext.isHypixel();
    }

    private static void after(long millis, Runnable task) {
        if (timer == null) timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "eviemod-score-relay"); thread.setDaemon(true); return thread;
        });
        var client = Minecraft.getInstance();
        var scheduledLevel = client.level;
        long scheduledSession = session;
        timer.schedule(() -> client.execute(() -> {
            if (scheduledSession == session && scheduledLevel == client.level && active(client)) task.run();
        }), millis, TimeUnit.MILLISECONDS);
    }

    private static void reset() { session++; RELAY.clear(); }
    private static void failed() {
        available = false; reset(); HOOK.close();
        LoggerFactory.getLogger("eviemod").warn("Noamm score event API unavailable; score relay disabled for this session");
    }
    private ScoreSyncClient() {}
}

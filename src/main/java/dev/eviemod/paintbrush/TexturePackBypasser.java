package dev.eviemod.paintbrush;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import org.slf4j.LoggerFactory;

/** Client-thread orchestration; all HTTP, hashing and persistence run on one worker. */
public final class TexturePackBypasser {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "eviemod-hypixel-pack"); thread.setDaemon(true); return thread;
    });
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId();
    private static HypixelPackStore store; // worker only
    private static Snapshot installed; // client thread only
    private static boolean busy, initialized, reloading;
    private static long nextCheck;
    private record Snapshot(HypixelPackStore.State state, Path file, long size, java.nio.file.attribute.FileTime modified, Object key) {}
    private TexturePackBypasser() {}
    private static boolean enabled() { return EviemodSettings.STORE.error() == null && EviemodSettings.STORE.values().texturePackBypasser; }
    private static int format() { return SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).major(); }

    static void tick(Minecraft client) {
        if (enabled() && !busy && (!initialized || System.currentTimeMillis() >= nextCheck)) check(false);
    }
    static void manualCheck() {
        if (!enabled()) { notifyUser("Enable Texture Pack Bypasser first."); return; }
        if (busy) { notifyUser("Checking for updates…"); return; }
        check(true);
    }
    private static void check(boolean manual) {
        busy = true;
        var client = Minecraft.getInstance();
        int format = format();
        Path directory = client.getResourcePackDirectory();
        WORKER.execute(() -> {
            boolean changed = false; Snapshot snapshot = null; String failure = null;
            long next = System.currentTimeMillis() + HypixelPackStore.DAY;
            try {
                if (store == null) store = new HypixelPackStore(directory,
                    FabricLoader.getInstance().getConfigDir().resolve("eviemod-hypixel-pack.json"), HypixelPackStore.http());
                boolean hadPack = store.state().pack() != null;
                if (manual || store.due(System.currentTimeMillis())) changed = store.check(format, System.currentTimeMillis());
                if (store.validInstalled(format)) snapshot = snapshot();
                next = Math.max(System.currentTimeMillis() + 1000, store.state().checkedAt() + HypixelPackStore.DAY);
                if (changed && !hadPack) client.execute(() -> notifyUser("Hypixel pack installed. Select it in Resource Packs."));
            } catch (Exception e) {
                failure = "Could not update the Hypixel pack. Existing packs were kept.";
                LoggerFactory.getLogger("eviemod").warn("Hypixel pack check failed", e);
                // An API outage must not disable an already verified, still intact local pack.
                try { if (store != null && store.validInstalled(format)) snapshot = snapshot(); }
                catch (Exception ignored) { /* Fail open to vanilla. */ }
            }
            Snapshot result = snapshot; String error = failure; boolean updated = changed; long deadline = next;
            client.execute(() -> {
                installed = result; initialized = true; nextCheck = deadline; busy = false;
                if (manual) {
                    if (error != null) notifyUser(error);
                    else {
                        notifyUser(updated ? "Hypixel pack updated." : "Hypixel pack is up to date.");
                        reloadPending();
                    }
                }
            });
        });
    }
    private static Snapshot snapshot() throws java.io.IOException {
        var attributes = Files.readAttributes(store.file(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return new Snapshot(store.state(), store.file(), attributes.size(), attributes.lastModifiedTime(), attributes.fileKey());
    }
    /** Called for each verified HM API SkyBlock location event, including island/server switches. */
    static void enteredSkyBlock() { Minecraft.getInstance().execute(TexturePackBypasser::reloadPending); }
    private static void reloadPending() {
        if (!enabled() || installed == null || !installed.state().pendingReload() || reloading || busy) return;
        var client = Minecraft.getInstance();
        Snapshot current = installed;
        if (!intact(current)) return;
        String packId = "file/" + current.state().file();
        boolean selected = client.getResourcePackRepository().getSelectedIds().contains(packId);
        // Refresh discovery only. Never add to selection, re-enable or reorder the pack.
        client.getResourcePackRepository().reload();
        reloading = true;
        CompletableFuture<Void> reload = selected ? client.reloadResourcePacks() : CompletableFuture.completedFuture(null);
        reload.whenComplete((unused, error) -> client.execute(() -> {
            if (error != null) {
                reloading = false;
                LoggerFactory.getLogger("eviemod").warn("Hypixel local pack reload failed; will retry next SkyBlock entry", error);
                return;
            }
            WORKER.execute(() -> {
                Snapshot refreshed = null;
                try { store.applied(current.state().pack().hash()); refreshed = snapshot(); }
                catch (Exception e) { LoggerFactory.getLogger("eviemod").warn("Could not record Hypixel pack reload", e); }
                Snapshot result = refreshed;
                client.execute(() -> { if (result != null) installed = result; reloading = false; });
            });
        }));
    }
    private static boolean intact(Snapshot snapshot) {
        try {
            var attributes = Files.readAttributes(snapshot.file(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attributes.isRegularFile() && attributes.size() == snapshot.size()
                && attributes.lastModifiedTime().equals(snapshot.modified()) && java.util.Objects.equals(attributes.fileKey(), snapshot.key());
        } catch (java.io.IOException e) { return false; }
    }
    /** Narrow fail-open packet boundary: known Hypixel server, exact API URL/hash, verified local file. */
    public static boolean shouldBypass(String serverAddress, String url, String hash) {
        if (!enabled() || installed == null || !matches(serverAddress, url, hash, installed.state().pack())) return false;
        return intact(installed);
    }
    static boolean matches(String serverAddress, String url, String hash, HypixelPackStore.Pack pack) {
        if (serverAddress == null || pack == null) return false;
        String host = serverAddress.toLowerCase(java.util.Locale.ROOT).split(":", 2)[0];
        return (host.equals("hypixel.net") || host.endsWith(".hypixel.net"))
            && pack.url().equals(url) && pack.hash().equalsIgnoreCase(hash);
    }
    private static void notifyUser(String message) {
        SystemToast.addOrUpdate(Minecraft.getInstance().getToastManager(), TOAST,
            Component.literal("Texture Pack Bypasser"), Component.literal(message));
    }
}

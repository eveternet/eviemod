package dev.eviemod.paintbrush;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HypixelPackStoreTest {
    @TempDir Path root;
    private String api(String hash) {
        return "{\"success\":true,\"packs\":[{\"id\":\"SkyBlock\",\"deployId\":\"deployment\",\"versions\":[{\"packFormat\":84,\"hash\":\""
            + hash + "\",\"url\":\"https://resourcepacks.hypixel.net/SkyBlock/deployment/84.zip\"}]}]}";
    }
    private Path zip(String description) throws IOException {
        Path zip = Files.createTempFile(root, "fixture", ".zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write(("{\"pack\":{\"pack_format\":84,\"description\":\"" + description + "\"}}").getBytes());
            out.closeEntry();
        }
        return zip;
    }
    private final class Remote implements HypixelPackStore.Fetch {
        Path payload; String json; int requests, downloads; boolean offline, brokenDownload;
        Remote(Path zip) throws IOException { update(zip); }
        void update(Path zip) throws IOException { payload = zip; json = api(HypixelPackStore.hash(zip)); }
        public void get(java.net.URI uri, Path destination, long limit) throws IOException {
            requests++;
            if (offline) throw new IOException("offline");
            if (uri.equals(HypixelPackStore.API)) Files.writeString(destination, json);
            else { downloads++; Files.copy(payload, destination, StandardCopyOption.REPLACE_EXISTING);
                if (brokenDownload) { Files.writeString(destination, "partial"); throw new IOException("interrupted"); } }
        }
    }
    private HypixelPackStore store(Remote remote) throws IOException {
        return new HypixelPackStore(root.resolve("resourcepacks"), root.resolve("config/state.json"), remote);
    }
    @Test void selectsOnlyExactSkyblockFormatAndOfficialUrl() throws Exception {
        String json = api("a".repeat(40));
        assertEquals(84, HypixelPackStore.select(json, 84).format());
        assertThrows(IOException.class, () -> HypixelPackStore.select(json.replace("84,", "84.5,"), 84));
        assertThrows(IOException.class, () -> HypixelPackStore.select(json.replace("84,", "\"84\","), 84));
        assertThrows(IOException.class, () -> HypixelPackStore.select(json, 88));
        assertThrows(IOException.class, () -> HypixelPackStore.select(json.replace("SkyBlock\"", "Other\""), 84));
        assertThrows(IOException.class, () -> HypixelPackStore.select(json.replace("resourcepacks.hypixel.net", "evil.example"), 84));
        assertThrows(IOException.class, () -> HypixelPackStore.select(json.replace("https:", "http:"), 84));
        assertThrows(IOException.class, () -> HypixelPackStore.select("{}", 84));
        assertThrows(IOException.class, () -> HypixelPackStore.select(json.replace("true", "false"), 84));
        assertThrows(IOException.class, () -> HypixelPackStore.select(json.replace("a".repeat(40), "bad"), 84));
    }
    @Test void installsPersistsSkipsUnchangedAndKeepsStableFilename() throws Exception {
        var remote = new Remote(zip("one")); var store = store(remote);
        assertTrue(store.check(84, 1000)); Path file = store.file();
        assertTrue(store.validInstalled(84)); assertTrue(store.state().pendingReload());
        assertFalse(store.check(84, 2000)); assertEquals(1, remote.downloads);
        assertEquals(store.state(), store(remote).state());
        remote.update(zip("two")); assertTrue(store.check(84, 3000));
        assertEquals(file, store.file()); assertEquals(2, remote.downloads);
        assertEquals(HypixelPackStore.hash(remote.payload), store.state().pack().hash());
        store.applied("wrong hash"); assertTrue(store.state().pendingReload());
        store.applied(store.state().pack().hash()); assertFalse(store(remote).state().pendingReload());
    }
    @Test void attemptsAreThrottledAcrossRestartEvenOnFailure() throws Exception {
        var remote = new Remote(zip("one")); var store = store(remote); remote.offline = true;
        assertThrows(IOException.class, () -> store.check(84, 1000));
        var restarted = store(remote);
        assertFalse(restarted.due(1001)); assertFalse(restarted.due(1000 + HypixelPackStore.DAY - 1));
        assertTrue(restarted.due(1000 + HypixelPackStore.DAY));
    }
    @Test void failedDownloadAndWrongHashPreserveInstalledAndUnrelatedPacks() throws Exception {
        var remote = new Remote(zip("one")); var store = store(remote); store.check(84, 1000);
        byte[] original = Files.readAllBytes(store.file());
        Path unrelated = root.resolve("resourcepacks/personal.zip"); Files.writeString(unrelated, "mine");
        remote.update(zip("two")); remote.brokenDownload = true;
        assertThrows(IOException.class, () -> store.check(84, 2000));
        assertArrayEquals(original, Files.readAllBytes(store.file()));
        remote.brokenDownload = false; remote.json = api("0".repeat(40));
        assertThrows(IOException.class, () -> store.check(84, 3000));
        assertArrayEquals(original, Files.readAllBytes(store.file())); assertEquals("mine", Files.readString(unrelated));
        try (var files = Files.list(root.resolve("resourcepacks"))) { assertEquals(2, files.count()); }
    }
    @Test void refusesExternallyModifiedPackAndSymlink() throws Exception {
        var remote = new Remote(zip("one")); var store = store(remote); store.check(84, 1000);
        remote.update(zip("two")); Files.writeString(store.file(), "player data");
        assertThrows(IOException.class, () -> store.check(84, 2000)); assertEquals("player data", Files.readString(store.file()));
        Files.delete(store.file()); Files.createSymbolicLink(store.file(), remote.payload);
        assertThrows(IOException.class, () -> store.check(84, 3000)); assertTrue(Files.isSymbolicLink(store.file()));
    }
    @Test void deletedManagedPackCanBeReinstalledAndCollisionIsPreserved() throws Exception {
        var remote = new Remote(zip("one")); var store = store(remote);
        Files.createDirectories(store.file().getParent()); Files.writeString(store.file(), "collision");
        assertThrows(IOException.class, () -> store.check(84, 1000)); assertEquals("collision", Files.readString(store.file()));
        Files.delete(store.file()); assertTrue(store.check(84, 2000));
        Files.delete(store.file()); assertTrue(store.check(84, 3000));
    }
    @Test void malformedStateIsNeverOverwritten() throws Exception {
        var remote = new Remote(zip("one")); Path state = root.resolve("config/state.json");
        Files.createDirectories(state.getParent()); Files.writeString(state, "broken");
        assertThrows(IOException.class, () -> store(remote)); assertEquals("broken", Files.readString(state));
        Files.writeString(state, "{\"file\":\"../../unrelated.zip\"}");
        assertThrows(IOException.class, () -> store(remote));
    }
    @Test void rejectsNonPackZipEvenWithCorrectHash() throws Exception {
        Path path = root.resolve("bad.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(path))) { out.putNextEntry(new ZipEntry("other")); out.closeEntry(); }
        assertThrows(IOException.class, () -> HypixelPackStore.validateZip(path, HypixelPackStore.hash(path)));
    }
}

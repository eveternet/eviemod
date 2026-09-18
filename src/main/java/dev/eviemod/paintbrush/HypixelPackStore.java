package dev.eviemod.paintbrush;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipFile;

/** Single-worker storage/HTTP boundary. Never selects packs or touches Minecraft state. */
final class HypixelPackStore {
    static final URI API = URI.create("https://api.hypixel.net/v2/resources/packs");
    static final long DAY = Duration.ofDays(1).toMillis();
    private static final long MAX_PACK = 256L * 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    record Pack(int format, String hash, String url, String version) {}
    record State(String file, Pack pack, long checkedAt, boolean pendingReload) {}
    interface Fetch { void get(URI uri, Path destination, long limit) throws IOException; }
    private final Path directory, statePath;
    private final Fetch fetch;
    private State state;

    HypixelPackStore(Path directory, Path statePath, Fetch fetch) throws IOException {
        this.directory = directory; this.statePath = statePath; this.fetch = fetch;
        if (Files.exists(statePath)) {
            try {
                state = GSON.fromJson(Files.readString(statePath), State.class);
                if (state == null || state.file() == null || !state.file().matches("eviemod-hypixel-[0-9a-f-]{36}\\.zip")
                    || state.checkedAt() < 0) throw new IllegalArgumentException("Invalid pack state");
                if (state.pack() != null) validate(state.pack());
            } catch (RuntimeException e) { throw new IOException("Invalid Hypixel pack state; original file preserved", e); }
        } else state = new State("eviemod-hypixel-" + UUID.randomUUID() + ".zip", null, 0, false);
    }
    State state() { return state; }
    Path file() { return directory.resolve(state.file()); }
    boolean due(long now) { return state.checkedAt() == 0 || now - state.checkedAt() >= DAY; }
    boolean validInstalled(int format) throws IOException {
        return state.pack() != null && state.pack().format() == format && Files.isRegularFile(file(), LinkOption.NOFOLLOW_LINKS)
            && hash(file()).equals(state.pack().hash());
    }
    void applied(String hash) throws IOException {
        if (state.pack() != null && state.pack().hash().equals(hash))
            save(new State(state.file(), state.pack(), state.checkedAt(), false));
    }
    /** Records attempts, including failures, so restarting does not hammer the API. */
    boolean check(int format, long now) throws IOException {
        save(new State(state.file(), state.pack(), now, state.pendingReload()));
        Files.createDirectories(directory);
        Path api = Files.createTempFile(statePath.toAbsolutePath().getParent(), "hypixel-api-", ".tmp");
        Path download = null;
        try {
            fetch.get(API, api, 1024 * 1024);
            Pack next = select(Files.readString(api), format);
            if (next.equals(state.pack()) && validInstalled(format)) return false;
            // A hash, not deployment metadata, determines whether the actual pack changed.
            if (state.pack() != null && next.hash().equals(state.pack().hash()) && validInstalled(format)) {
                save(new State(state.file(), next, now, state.pendingReload())); return false;
            }
            // Refuse to overwrite files replaced by the player, symlinks, or unowned collisions.
            if (Files.exists(file(), LinkOption.NOFOLLOW_LINKS)
                && (state.pack() == null || !Files.isRegularFile(file(), LinkOption.NOFOLLOW_LINKS)
                    || !hash(file()).equals(state.pack().hash())))
                throw new IOException("Managed pack was changed outside eviemod; file preserved");
            download = Files.createTempFile(directory, ".eviemod-hypixel-", ".tmp");
            fetch.get(URI.create(next.url()), download, MAX_PACK);
            validateZip(download, next.hash());
            // Recheck ownership after downloading, before the only replacement operation.
            if (Files.exists(file(), LinkOption.NOFOLLOW_LINKS)
                && (state.pack() == null || !Files.isRegularFile(file(), LinkOption.NOFOLLOW_LINKS)
                    || !hash(file()).equals(state.pack().hash()))) throw new IOException("Managed pack changed during download");
            atomicMove(download, file());
            save(new State(state.file(), next, now, true));
            return true;
        } catch (RuntimeException e) { throw new IOException("Invalid Hypixel pack response", e); }
        finally { Files.deleteIfExists(api); if (download != null) Files.deleteIfExists(download); }
    }
    private void save(State next) throws IOException {
        Files.createDirectories(statePath.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(statePath.toAbsolutePath().getParent(), "hypixel-state-", ".tmp");
        try { Files.writeString(temporary, GSON.toJson(next) + "\n"); atomicMove(temporary, statePath); state = next; }
        finally { Files.deleteIfExists(temporary); }
    }
    private static void atomicMove(Path source, Path target) throws IOException {
        // No non-atomic fallback: unsupported filesystems retain the working pack.
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    static Pack select(String json, int format) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.get("success").getAsBoolean()) throw new IOException("Hypixel API was unsuccessful");
            Pack found = null;
            for (var element : root.getAsJsonArray("packs")) {
                var pack = element.getAsJsonObject();
                if (!"SkyBlock".equals(pack.get("id").getAsString())) continue;
                for (var entry : pack.getAsJsonArray("versions")) {
                    var version = entry.getAsJsonObject();
                    if (version.get("packFormat").getAsInt() != format) continue;
                    Pack candidate = new Pack(format, version.get("hash").getAsString().toLowerCase(Locale.ROOT),
                        version.get("url").getAsString(), pack.get("deployId").getAsString());
                    validate(candidate);
                    if (found != null) throw new IOException("Ambiguous compatible Hypixel pack");
                    found = candidate;
                }
            }
            if (found == null) throw new IOException("No Hypixel pack for this Minecraft resource format");
            return found;
        } catch (RuntimeException e) { throw new IOException("Malformed Hypixel pack response", e); }
    }
    private static void validate(Pack pack) {
        URI uri = URI.create(pack.url());
        if (pack.format() <= 0 || !pack.hash().matches("[0-9a-f]{40}") || pack.version() == null
            || !"https".equals(uri.getScheme()) || !"resourcepacks.hypixel.net".equals(uri.getHost())
            || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getFragment() != null
            || !uri.getPath().startsWith("/SkyBlock/")) throw new IllegalArgumentException("Invalid official pack metadata");
    }
    static String hash(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-1");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    static void validateZip(Path path, String expected) throws IOException {
        if (!hash(path).equals(expected)) throw new IOException("Hypixel pack hash mismatch");
        try (var zip = new ZipFile(path.toFile())) {
            var metadata = zip.getEntry("pack.mcmeta");
            if (metadata == null || metadata.isDirectory()) throw new IOException("Missing pack.mcmeta");
            try (var input = zip.getInputStream(metadata)) {
                byte[] bytes = input.readNBytes(65537);
                if (bytes.length > 65536 || !JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().get("pack").isJsonObject()) throw new IOException("Invalid pack.mcmeta");
            } catch (RuntimeException e) { throw new IOException("Invalid pack metadata", e); }
        }
    }
    static Fetch http() {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
        return (uri, destination, limit) -> {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(2)).header("User-Agent", "eviemod").GET().build();
            // A bounded subscriber keeps the request deadline active through the entire body.
            try {
                var response = client.send(request, info -> new LimitedBody(destination, limit));
                if (response.statusCode() != 200) throw new IOException("Hypixel HTTP " + response.statusCode());
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Download interrupted", e); }
        };
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<Path> {
        private final java.util.concurrent.CompletableFuture<Path> result = new java.util.concurrent.CompletableFuture<>();
        private final Path path; private final long limit; private long size;
        private OutputStream output; private java.util.concurrent.Flow.Subscription subscription;
        LimitedBody(Path path, long limit) { this.path = path; this.limit = limit; }
        public java.util.concurrent.CompletionStage<Path> getBody() { return result; }
        public void onSubscribe(java.util.concurrent.Flow.Subscription value) {
            subscription = value;
            try { output = Files.newOutputStream(path); value.request(1); } catch (IOException e) { fail(e); }
        }
        public void onNext(List<java.nio.ByteBuffer> buffers) {
            try {
                for (var buffer : buffers) {
                    size += buffer.remaining(); if (size > limit) throw new IOException("Download exceeds size limit");
                    byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); output.write(bytes);
                }
                subscription.request(1);
            } catch (IOException e) { fail(e); }
        }
        private void fail(Throwable error) {
            if (subscription != null) subscription.cancel();
            try { if (output != null) output.close(); } catch (IOException close) { error.addSuppressed(close); }
            result.completeExceptionally(error);
        }
        public void onError(Throwable error) { fail(error); }
        public void onComplete() {
            try { output.close(); result.complete(path); } catch (IOException e) { fail(e); }
        }
    }
}

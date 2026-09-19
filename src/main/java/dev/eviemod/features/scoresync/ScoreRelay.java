package dev.eviemod.features.scoresync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.Strictness;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.EnumMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Client-thread, per-occurrence relay. No dungeon score or per-run suppression state. */
final class ScoreRelay {
    enum Kind {
        MIMIC("Mimic Killed!"), PRINCE("Prince Killed!");
        final String body;
        Kind(String body) { this.body = body; }
    }
    interface Timer { void after(long millis, Runnable task); }
    private static final Pattern PARTY = Pattern.compile("^Party > .*?: (.+)$");
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final LongSupplier clock;
    private final Timer timer;
    private final Consumer<String> send;
    private final ArrayList<Pending> windows = new ArrayList<>();
    private final ArrayDeque<Response> responses = new ArrayDeque<>();
    private final EnumMap<Kind, Long> lastAnnouncements = new EnumMap<>(Kind.class);
    private long generation;

    ScoreRelay(LongSupplier clock, Timer timer, Consumer<String> send) {
        this.clock = clock; this.timer = timer; this.send = send;
    }

    void incoming(String json) {
        Kind kind = packetKind(json);
        if (kind == null) return;
        long now = clock.getAsLong();
        Long lastAnnouncement = lastAnnouncements.get(kind);
        if (lastAnnouncement != null && now - lastAnnouncement >= 0 && now - lastAnnouncement <= 1000) return;
        Pending pending = new Pending(kind, now + 1000, generation);
        windows.add(pending);
        timer.after(1000, () -> {
            if (pending.generation != generation || !windows.remove(pending)) return;
            transmit(pending);
        });
    }

    void chat(String text, boolean dungeon, int floor, boolean ownMessage) {
        String plain = stripFormatting(text);
        var party = PARTY.matcher(plain);
        if (party.matches()) {
            Kind kind = bodyKind(party.group(1));
            if (kind != null && dungeon && (kind != Kind.MIMIC || floor == 6 || floor == 7)) {
                long now = clock.getAsLong();
                lastAnnouncements.put(kind, now);
                windows.removeIf(p -> p.kind == kind && now < p.deadline);
            }
            if (ownMessage && kind != null) {
                // The chat transport has no request IDs; acknowledge the oldest corresponding send.
                for (var iterator = responses.iterator(); iterator.hasNext();) {
                    if (iterator.next().pending.kind == kind) { iterator.remove(); break; }
                }
            }
            return;
        }
        // Full-line cooldown matching cannot classify prefixed player chat as a retryable error.
        Long cooldown = ChatCooldown.delay(plain);
        if (cooldown != null) {
            Response response = responses.pollFirst();
            Pending pending = response == null ? null : response.pending;
            if (pending != null && !pending.retried) {
                pending.retried = true;
                timer.after(cooldown, () -> {
                    if (pending.generation == generation) transmit(pending);
                });
            }
        } else {
            // No public failure API: any unclassified system response abandons response tracking.
            // This covers mute/generic failures without inventing retry categories.
            responses.clear();
        }
    }

    void foreignSend() { responses.clear(); }
    void clear() { generation++; windows.clear(); responses.clear(); lastAnnouncements.clear(); }

    private void transmit(Pending pending) {
        Response response = new Response(pending);
        responses.addLast(response);
        try { send.accept("pc " + pending.kind.body); }
        catch (RuntimeException e) { responses.remove(response); return; }
        // Bound ambiguous response attribution; no response is never grounds for retrying.
        timer.after(5000, () -> responses.remove(response));
    }

    static Kind packetKind(String json) {
        try {
            var value = JSON.fromJson(json, JsonElement.class);
            if (value == null || !value.isJsonObject()) return null;
            var type = value.getAsJsonObject().get("type");
            if (type == null || !type.isJsonPrimitive() || !type.getAsJsonPrimitive().isString()) return null;
            return switch (type.getAsString()) {
                case "dungeonmimic" -> Kind.MIMIC;
                case "dungeonprince" -> Kind.PRINCE;
                default -> null;
            };
        } catch (RuntimeException e) { return null; }
    }

    static Kind bodyKind(String body) {
        return switch (body.toLowerCase(Locale.ROOT)) {
            case "mimic killed", "mimic slain", "mimic killed!", "mimic dead", "mimic dead!" -> Kind.MIMIC;
            case "prince killed", "prince slain", "prince killed!", "prince dead", "prince dead!" -> Kind.PRINCE;
            default -> null;
        };
    }

    static String stripFormatting(String text) { return text.replaceAll("§[\\s\\S]", ""); }
    private static final class Response {
        final Pending pending;
        Response(Pending pending) { this.pending = pending; }
    }
    private static final class Pending {
        final Kind kind;
        final long deadline, generation;
        boolean retried;
        Pending(Kind kind, long deadline, long generation) {
            this.kind = kind; this.deadline = deadline; this.generation = generation;
        }
    }
}

package dev.eviemod.features.scoresync;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoreRelayTest {
    private static final String COOLDOWN = "You are sending commands too fast! Please slow down.";
    private static final class Fixture {
        long now, sequence;
        record Task(long due, long sequence, Runnable run) {}
        final PriorityQueue<Task> tasks = new PriorityQueue<>(Comparator.comparingLong(Task::due).thenComparingLong(Task::sequence));
        final ArrayList<String> sent = new ArrayList<>();
        final ScoreRelay relay = new ScoreRelay(() -> now,
            (delay, run) -> tasks.add(new Task(now + delay, sequence++, run)), sent::add);
        void event(String kind) { relay.incoming("{\"type\":\"dungeon" + kind + "\"}"); }
        void at(long time) {
            while (!tasks.isEmpty() && tasks.peek().due <= time) {
                Task task = tasks.remove(); now = task.due; task.run.run();
            }
            now = time;
        }
        void chat(String body) { relay.chat("Party > [MVP+] Player: " + body, false); }
    }

    @Test void bothKindsSendAtExactlyOneSecond() {
        var f = new Fixture(); f.event("mimic"); f.event("prince");
        f.at(999); assertTrue(f.sent.isEmpty());
        f.at(1000); assertEquals(java.util.List.of("pc Mimic Killed!", "pc Prince Killed!"), f.sent);
        f.at(100000); assertEquals(2, f.sent.size());
    }
    @Test void allAcceptedBodiesCancelOnlyTheirOwnKind() {
        for (String kind : java.util.List.of("mimic", "prince")) {
            for (String suffix : java.util.List.of(" killed", " slain", " killed!", " dead", " dead!")) {
                var f = new Fixture(); f.event("mimic"); f.event("prince"); f.at(999);
                f.relay.chat("§aParty > §b[VIP] Player: §r" + (kind + suffix).toUpperCase(java.util.Locale.ROOT), false);
                f.at(1000); assertEquals(1, f.sent.size());
                assertEquals(kind.equals("mimic") ? "pc Prince Killed!" : "pc Mimic Killed!", f.sent.getFirst());
            }
        }
    }
    @Test void exactOdinStructureAndWhitespace() {
        for (String line : java.util.List.of("party > Player: Mimic Killed!", " Party > Player: Mimic Killed!",
            "Party > Player:Mimic Killed!", "Party > Player:  Mimic Killed!", "Party > Player: Mimic Killed! ",
            "Party > Player: Mimic  Killed!", "Party > Player: Mimic Killed!!", "Guild > Player: Mimic Killed!",
            "Party > Player: someone: Mimic Killed!", "Mimic Killed!", "Party > Player: hi")) {
            var f = new Fixture(); f.event("mimic"); f.relay.chat(line, false); f.at(1000);
            assertEquals(1, f.sent.size(), line);
        }
    }
    @Test void websocketFirstCancellation() {
        var f = new Fixture(); f.event("mimic"); f.at(500); f.chat("Mimic Killed!"); f.at(1000);
        assertTrue(f.sent.isEmpty());
        var p = new Fixture(); p.event("prince"); p.at(500); p.chat("Prince Killed!"); p.at(1000);
        assertTrue(p.sent.isEmpty());
    }
    @Test void repeatedEventsAreIndependentAndOverlappingWindowsCancelTogether() {
        var f = new Fixture(); f.event("mimic"); f.at(500); f.event("mimic");
        f.at(999); f.chat("Mimic Dead!"); f.at(1500); assertTrue(f.sent.isEmpty());
        f.at(2000); f.event("mimic"); f.at(3000); assertEquals(1, f.sent.size());
        f.event("mimic"); f.event("mimic"); f.at(4000); assertEquals(3, f.sent.size());
    }
    @Test void messagesOutsideWindowDoNotSuppress() {
        var f = new Fixture(); f.event("mimic");
        // Simulate a delayed timer dispatch: a message at the deadline is outside [start, deadline).
        f.now = 1000; f.chat("Mimic Killed!"); f.at(1000); assertEquals(1, f.sent.size());
    }
    @Test void recentChatSuppressesImmediatelyForBothKindsAndAllAcceptedBodies() {
        for (String kind : java.util.List.of("mimic", "prince")) {
            for (String suffix : java.util.List.of(" killed", " slain", " killed!", " dead", " dead!")) {
                for (long age : new long[]{0, 500, 999, 1000}) {
                    var f = new Fixture();
                    f.chat((kind + suffix).toUpperCase(java.util.Locale.ROOT));
                    f.at(age); f.event(kind);
                    assertTrue(f.tasks.isEmpty(), "Suppression must not schedule a relay");
                    f.at(10000); assertTrue(f.sent.isEmpty());
                }
            }
        }
    }
    @Test void chatOlderThanOneSecondDoesNotSuppressEitherKind() {
        for (String kind : java.util.List.of("mimic", "prince")) {
            var f = new Fixture(); f.chat(kind + " killed!");
            f.at(1001); f.event(kind); f.at(2000); assertTrue(f.sent.isEmpty());
            f.at(2001); assertEquals(1, f.sent.size());
        }
    }
    @Test void recentChatDoesNotSuppressOtherKind() {
        for (String kind : java.util.List.of("mimic", "prince")) {
            var f = new Fixture(); f.chat(kind + " killed!"); f.at(500);
            f.event("mimic"); f.event("prince"); f.at(1500);
            assertEquals(java.util.List.of(kind.equals("mimic") ? "pc Prince Killed!" : "pc Mimic Killed!"), f.sent);
        }
    }
    @Test void unrelatedOrInvalidEarlierChatDoesNotSuppress() {
        for (String line : java.util.List.of("Party > Player: hello", "Guild > Player: Mimic Killed!",
            "party > Player: Mimic Killed!", "Party > Player: Mimic  Killed!", "Party > Player: Prince Killed!!",
            "Party > Player: Prince Killed! ")) {
            var f = new Fixture(); f.relay.chat(line, false); f.at(500);
            f.event("mimic"); f.event("prince"); f.at(1500); assertEquals(2, f.sent.size(), line);
        }
    }
    @Test void chatFirstSuppression() {
        var f = new Fixture(); f.chat("Mimic Dead!"); f.chat("Prince Slain");
        f.at(500); f.event("mimic"); f.event("prince");
        assertTrue(f.tasks.isEmpty(), "Recorded chat must suppress immediately");
        f.at(1500); assertTrue(f.sent.isEmpty());
    }
    @Test void newestMatchingAnnouncementRefreshesLookbackButUnrelatedChatDoesNot() {
        var f = new Fixture(); f.chat("Mimic Killed!"); f.at(900); f.chat("Mimic Slain");
        f.at(1500); f.event("mimic"); assertTrue(f.tasks.isEmpty());
        f.at(1800); f.chat("hello"); f.at(1901); f.event("mimic");
        f.at(2901); assertEquals(1, f.sent.size());
    }
    @Test void resetClearsRecentAnnouncementsForBothKinds() {
        var f = new Fixture(); f.chat("Mimic Killed!"); f.chat("Prince Killed!");
        f.relay.clear(); f.event("mimic"); f.event("prince"); f.at(1000); assertEquals(2, f.sent.size());
    }
    @Test void onlyExactStringPacketTypesAreSupportedAndJsonMustBeValid() {
        for (String raw : java.util.List.of("{}", "null", "[]", "bad", "{\"type\":3}", "{\"type\":null}",
            "{\"type\":\"dungeonbat\"}", "{\"type\":\"Dungeonmimic\"}", "{\"Type\":\"dungeonmimic\"}",
            "{type:'dungeonmimic'}", "{\"type\":\"dungeonmimic\"} trailing", "{\"type\":\"dungeonmimic\",}")) {
            assertNull(ScoreRelay.packetKind(raw), raw);
        }
    }
    @Test void cooldownRetriesOnceWithDefaultDelay() {
        var f = new Fixture(); f.event("mimic"); f.at(1000);
        f.relay.chat(COOLDOWN, false); f.at(1999); assertEquals(1, f.sent.size());
        f.at(2000); assertEquals(2, f.sent.size()); f.relay.chat(COOLDOWN, false);
        f.at(100000); assertEquals(2, f.sent.size());
    }
    @Test void cooldownUsesExposedDuration() {
        var f = new Fixture(); f.event("prince"); f.at(1000);
        f.relay.chat("You can only chat once every 3 seconds! Ranked users bypass this restriction!", false);
        f.at(3999); assertEquals(1, f.sent.size()); f.at(4000); assertEquals(2, f.sent.size());
    }
    @Test void overlappingOccurrencesEachHaveOnlyOneRetry() {
        var f = new Fixture(); f.event("mimic"); f.event("prince"); f.at(1000);
        f.relay.chat(COOLDOWN, false); f.relay.chat(COOLDOWN, false);
        f.at(2000);
        assertEquals(java.util.List.of("pc Mimic Killed!", "pc Prince Killed!", "pc Mimic Killed!", "pc Prince Killed!"), f.sent);
        f.relay.chat(COOLDOWN, false); f.relay.chat(COOLDOWN, false);
        f.at(100000); assertEquals(4, f.sent.size());
    }
    @Test void muteGenericAndUnknownFailuresNeverRetryEvenIfFollowedByCooldown() {
        for (String failure : java.util.List.of("You are currently muted!", "You are not currently in a party.",
            "An unknown error occurred!", "Error: chat is unavailable", "Mute reason: test", "You cannot say the same message twice!")) {
            var f = new Fixture(); f.event("prince"); f.at(1000); f.relay.chat(failure, false);
            f.relay.chat(COOLDOWN, false); f.at(100000); assertEquals(1, f.sent.size());
        }
    }
    @Test void resetCancelsWindowsAndScheduledRetry() {
        var f = new Fixture(); f.event("mimic"); f.relay.clear(); f.at(1000); assertTrue(f.sent.isEmpty());
        f.event("prince"); f.at(2000); f.relay.chat(COOLDOWN, false); f.relay.clear();
        f.at(100000); assertEquals(1, f.sent.size());
    }
    @Test void foreignSendsOwnEchoAndExpiredResponsesPreventMisattributedRetry() {
        for (int action = 0; action < 3; action++) {
            var f = new Fixture(); f.event("mimic"); f.at(1000);
            switch (action) {
                case 0 -> f.relay.foreignSend();
                case 1 -> f.relay.chat("Party > Me: Mimic Killed!", true);
                case 2 -> f.at(6000);
            }
            f.relay.chat(COOLDOWN, false); f.at(100000); assertEquals(1, f.sent.size());
        }
    }
    @Test void playerCooldownTextIsNotAnErrorAndUnrelatedChatDoesNotCancel() {
        var f = new Fixture(); f.event("mimic"); f.at(1000);
        f.relay.chat("Party > Player: " + COOLDOWN, false);
        f.relay.chat("[VIP] Player: " + COOLDOWN, false);
        f.at(10000); assertEquals(1, f.sent.size());
    }
    @Test void transportExceptionsGiveUp() {
        int[] sends = {0}; var f = new Fixture();
        var relay = new ScoreRelay(() -> f.now, (delay, task) -> f.tasks.add(new Fixture.Task(f.now + delay, f.sequence++, task)),
            command -> { sends[0]++; throw new IllegalStateException(); });
        relay.incoming("{\"type\":\"dungeonmimic\"}"); f.at(100000); assertEquals(1, sends[0]);
    }
}

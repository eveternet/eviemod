package dev.eviemod.features.experiments;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.eviemod.features.experiments.ExperimentSession.*;

class ExperimentSessionTest {
    private static Cell note(String color, boolean lit) { return new Cell(Kind.NOTE, color, 0, lit, false); }
    private static Cell number(int n) { return new Cell(Kind.NUMBER, "", n, false, false); }
    private static Cell hidden(boolean second) { return new Cell(Kind.HIDDEN, "", 0, false, second); }
    private static Cell reward(String identity) { return new Cell(Kind.REWARD, identity, 0, false, false); }
    private static List<Cell> cells() { return new ArrayList<>(java.util.Collections.nCopies(54, Cell.OTHER)); }
    private static Board chrono(Phase phase, int round, int lit) {
        var cells = cells(); cells.set(20, note("red", lit == 20)); cells.set(22, note("blue", lit == 22));
        return new Board(phase, round, cells);
    }
    private static void pulse(ExperimentSession session, int round, int slot) {
        session.observe(chrono(Phase.REMEMBER, round, slot));
        session.observe(chrono(Phase.REMEMBER, round, -1));
    }
    @Test void chronomatronRecordsRepeatedNotesAndAdvancesOnlyManualCorrectClicks() {
        var s = new ExperimentSession(Game.CHRONOMATRON);
        pulse(s, 3, 20); pulse(s, 3, 20); pulse(s, 3, 22);
        s.observe(chrono(Phase.REPLAY, 3, -1));
        assertTrue(s.next(20)); assertFalse(s.next(22));
        assertTrue(s.click(22, true)); assertTrue(s.next(20));
        assertFalse(s.click(20, true)); assertTrue(s.next(20));
        assertFalse(s.click(20, true)); assertTrue(s.next(22));
        assertFalse(s.click(22, true)); assertFalse(s.next(22));
        assertFalse(s.click(20, true));
    }
    @Test void duplicateSnapshotsDoNotDuplicateNotes() {
        var s = new ExperimentSession(Game.CHRONOMATRON);
        for (int i = 0; i < 10; i++) s.observe(chrono(Phase.REMEMBER, 1, 20));
        s.observe(chrono(Phase.REPLAY, 1, -1)); assertTrue(s.next(20));
    }
    @Test void missedPulsesMissingRoundAndReopeningMidReplayFailOpen() {
        for (int round : new int[]{0, 2}) {
            var s = new ExperimentSession(Game.CHRONOMATRON);
            pulse(s, round, 20); s.observe(chrono(Phase.REPLAY, round, -1));
            assertFalse(s.next(20)); assertFalse(s.click(22, true));
        }
        var s = new ExperimentSession(Game.CHRONOMATRON);
        pulse(s, 1, 20); s.observe(chrono(Phase.REPLAY, 1, -1)); s.clear();
        s.observe(chrono(Phase.REPLAY, 1, -1)); assertFalse(s.next(20)); assertFalse(s.click(22, true));
    }
    @Test void roundTransitionAndAllowedMistakeDiscardOldGuidance() {
        var s = new ExperimentSession(Game.CHRONOMATRON);
        pulse(s, 1, 20); s.observe(chrono(Phase.REPLAY, 1, -1));
        assertFalse(s.click(22, false)); assertFalse(s.next(20)); assertFalse(s.click(22, true));
        pulse(s, 2, 22); pulse(s, 2, 20); s.observe(chrono(Phase.REPLAY, 2, -1));
        assertTrue(s.next(22));
        s.observe(chrono(Phase.REPLAY, 3, -1)); assertFalse(s.next(22));
    }
    @Test void ambiguousSimultaneousPulsesAndOverflowFailOpen() {
        var s = new ExperimentSession(Game.CHRONOMATRON);
        var cells = cells(); cells.set(20, note("red", true)); cells.set(22, note("blue", true));
        s.observe(new Board(Phase.REMEMBER, 2, cells)); s.observe(chrono(Phase.REPLAY, 2, -1));
        assertFalse(s.next(20)); assertFalse(s.click(22, true));
        s.clear(); pulse(s, 1, 20); pulse(s, 1, 22); s.observe(chrono(Phase.REPLAY, 1, -1));
        assertFalse(s.next(20));
    }
    private static Board ultra(Phase phase, int round, Cell a, Cell b, Cell c) {
        var cells = cells(); cells.set(10, a); cells.set(15, b); cells.set(30, c);
        return new Board(phase, round, cells);
    }
    @Test void ultrasequencerWaitsForCompleteNumberedPositionsAndResetsEveryRound() {
        var s = new ExperimentSession(Game.ULTRASEQUENCER);
        s.observe(ultra(Phase.REMEMBER, 3, number(2), Cell.OTHER, Cell.OTHER));
        s.observe(ultra(Phase.REMEMBER, 3, number(2), number(3), number(1)));
        s.observe(ultra(Phase.REPLAY, 3, hidden(false), hidden(false), hidden(false)));
        assertEquals(1, s.number(30)); assertTrue(s.next(30)); assertTrue(s.click(10, true));
        s.click(30, true); assertTrue(s.next(10)); s.click(10, true); assertTrue(s.next(15));
        s.click(15, true); assertFalse(s.next(15)); assertFalse(s.click(10, true));
        s.observe(ultra(Phase.REMEMBER, 1, number(1), Cell.OTHER, Cell.OTHER));
        s.observe(ultra(Phase.REPLAY, 1, hidden(false), hidden(false), hidden(false)));
        assertTrue(s.next(10)); assertEquals(0, s.number(30));
    }
    @Test void missingDuplicateAndOutOfRangeNumbersCannotProtectClicks() {
        for (Board reveal : List.of(ultra(Phase.REMEMBER, 3, number(1), number(2), Cell.OTHER),
            ultra(Phase.REMEMBER, 3, number(1), number(1), number(3)),
            ultra(Phase.REMEMBER, 3, number(1), number(2), number(4)))) {
            var s = new ExperimentSession(Game.ULTRASEQUENCER); s.observe(reveal);
            s.observe(ultra(Phase.REPLAY, 3, hidden(false), hidden(false), hidden(false)));
            assertFalse(s.next(10)); assertFalse(s.click(15, true));
        }
    }
    private static Board pairs(Cell a, Cell b, Cell c, Cell d) {
        var cells = cells(); cells.set(10, a); cells.set(11, b); cells.set(12, c); cells.set(13, d);
        return new Board(Phase.PAIRS, 0, cells);
    }
    private static ExperimentSession knownPairs() {
        var s = new ExperimentSession(Game.SUPERPAIRS);
        s.observe(pairs(reward("book-VI"), reward("book-VII"), hidden(false), hidden(false)));
        s.observe(pairs(hidden(false), hidden(false), reward("book-VI"), hidden(false)));
        s.observe(pairs(hidden(false), hidden(false), hidden(false), hidden(false)));
        return s;
    }
    @Test void superpairsRemembersItemsAndKnownMatchesWithoutBlockingExploration() {
        var s = knownPairs();
        assertEquals("book-VI", s.remembered(10).identity()); assertTrue(s.knownMatch(10)); assertTrue(s.knownMatch(12));
        assertFalse(s.knownMatch(11)); assertNull(s.remembered(13));
        assertFalse(s.click(13, true));
        s.observe(pairs(reward("book-VI"), hidden(true), hidden(true), hidden(true)));
        assertFalse(s.next(12)); // This is not the server reveal for the selected unknown slot 13.
        s = knownPairs();
        s.observe(pairs(reward("book-VI"), hidden(true), hidden(true), hidden(true)));
        assertTrue(s.next(12)); assertTrue(s.click(11, true));
        assertTrue(s.next(12)); assertFalse(s.click(13, true));
    }
    @Test void superpairsNeverBlocksWithoutAnUnambiguousServerConfirmedFirstCard() {
        var s = knownPairs();
        s.observe(pairs(reward("book-VI"), hidden(false), hidden(false), hidden(false)));
        assertFalse(s.click(11, true));
        s.observe(pairs(reward("book-VI"), hidden(true), hidden(true), hidden(true)));
        assertFalse(s.click(12, true));
        // Duplicate observations cannot clear the pending second-click state.
        s.observe(pairs(reward("book-VI"), hidden(true), hidden(true), hidden(true)));
        assertFalse(s.click(11, true));
        s.observe(pairs(reward("book-VI"), reward("book-VII"), hidden(true), hidden(true)));
        assertFalse(s.click(11, true));
    }
    @Test void superpairsSpecialCardsAndUnknownSlotsRemainClickable() {
        var s = knownPairs();
        var power = new Cell(Kind.POWERUP, "instant", 0, false, false);
        s.observe(pairs(power, hidden(true), hidden(true), hidden(true)));
        assertFalse(s.next(11)); assertFalse(s.click(11, true));
        s.observe(pairs(hidden(false), power, hidden(false), hidden(false)));
        s.observe(pairs(reward("book-VI"), hidden(true), hidden(true), hidden(true)));
        assertFalse(s.click(11, true)); assertFalse(s.click(13, true));
    }
    @Test void unrelatedUpdatesCannotRearmProtectionWhileSecondRevealIsPending() {
        var s = knownPairs();
        s.observe(pairs(reward("book-VI"), hidden(true), hidden(true), hidden(true)));
        assertFalse(s.click(12, true));
        var update = pairs(reward("book-VI"), hidden(true), hidden(true), hidden(true));
        var cells = new ArrayList<>(update.cells()); cells.set(49, reward("timer-update"));
        s.observe(new Board(Phase.PAIRS, 0, cells));
        assertFalse(s.next(12)); assertFalse(s.click(11, true));
    }
    @Test void changedBoardRemovedCardsAndCloseInvalidateSuperpairsMemory() {
        var s = knownPairs();
        s.observe(pairs(reward("different-board"), hidden(true), hidden(true), hidden(true)));
        assertNull(s.remembered(11)); assertNull(s.remembered(12)); assertFalse(s.click(11, true));
        s = knownPairs(); s.observe(pairs(Cell.OTHER, hidden(false), hidden(false), hidden(false)));
        assertFalse(s.knownMatch(12));
        s.clear(); s.observe(pairs(hidden(true), hidden(true), hidden(true), hidden(true)));
        assertNull(s.remembered(12)); assertFalse(s.click(11, true));
    }
    @Test void unsupportedSnapshotsAndNonBoardClicksAlwaysPassThrough() {
        var s = knownPairs();
        for (int id : new int[]{-999, -1, 4, 49, 54, 89}) assertFalse(s.click(id, true));
        s.observe(new Board(Phase.UNKNOWN, 0, cells())); assertNull(s.remembered(10));
        s.observe(new Board(Phase.PAIRS, 0, List.of())); assertFalse(s.click(10, true));
    }
}

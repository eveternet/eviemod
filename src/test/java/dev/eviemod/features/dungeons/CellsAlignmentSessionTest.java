package dev.eviemod.features.dungeons;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CellsAlignmentSessionTest {
    private static final String CAST = CellsAlignmentTimerTest.CAST;
    private final Object world = new Object();
    private final Object connection = new Object();
    private final CellsAlignmentSession session = new CellsAlignmentSession();
    private static long seconds(double value) { return (long) (value * 1_000_000_000L); }
    private void ready() { session.context(world, connection, true); }
    private String text(double time) {
        var frame = session.frame(seconds(time));
        return frame == null ? null : frame.text();
    }

    @Test void nominalFallbackAnchorsAtCastAndExpiresAtEightSeconds() {
        ready();
        assertTrue(session.receive(CAST, false, seconds(0.25)));
        assertEquals("Cells Aligned Casted!", text(1.249));
        assertEquals("4", text(1.25));
        assertEquals("3", text(2.25));
        assertEquals("2", text(3.25));
        assertEquals("1", text(4.25));
        assertEquals("Esoward now", text(5.25));
        assertEquals("Esoward now", text(8.249));
        assertNull(text(8.25));
    }

    @Test void tenTpsDoublesEachStageAndFinalHold() {
        ready();
        session.sample(100, seconds(0));
        session.sample(120, seconds(2));
        session.receive(CAST, false, seconds(2));
        assertEquals("Cells Aligned Casted!", text(3.999));
        assertEquals("4", text(4));
        assertEquals("3", text(6));
        assertEquals("2", text(8));
        assertEquals("1", text(10));
        assertEquals("Esoward now", text(12));
        assertEquals("Esoward now", text(17.999));
        assertNull(text(18));
    }

    @Test void clientTickAndFrameCountsDoNotDetermineProgress() {
        ready();
        session.sample(0, 0);
        session.sample(20, seconds(2));
        session.receive(CAST, false, seconds(2));
        for (int i = 0; i < 10000; i++) {
            session.context(world, connection, true);
            assertEquals("Cells Aligned Casted!", text(2));
        }
        assertEquals("Cells Aligned Casted!", text(3.9));
        assertEquals("4", text(4));
    }

    @Test void changingRateAffectsOnlyFutureElapsedTime() {
        ready();
        session.sample(0, 0);
        session.sample(20, seconds(1));
        session.receive(CAST, false, seconds(1));
        session.sample(40, seconds(3)); // Last two seconds estimated at 20 TPS; next interval is 10 TPS.
        assertEquals("3", text(3));
        assertEquals("3", text(4.999));
        assertEquals("2", text(5));
        session.sample(60, seconds(5));
        assertEquals("1", text(7));
    }

    @Test void suddenServerStallKeepsExtrapolatingRatherThanPretendingExactTicks() {
        ready();
        session.sample(0, 0);
        session.sample(20, seconds(2));
        session.receive(CAST, false, seconds(2));
        // No further packets: approved estimation continues at the last measured 10 TPS.
        assertEquals("Esoward now", text(12));
        assertNull(text(18));
    }

    @Test void duplicateSamplesAreIgnoredAndFastCatchupCannotExceedTwentyTps() {
        ready();
        session.sample(0, 0);
        session.sample(20, seconds(2));
        session.sample(20, seconds(2.5));
        session.receive(CAST, false, seconds(2.5));
        assertEquals("Cells Aligned Casted!", text(4.499));
        assertEquals("4", text(4.5));
        session.sample(1000, seconds(4.5));
        session.receive(CAST, false, seconds(4.5));
        assertEquals("Cells Aligned Casted!", text(5.499));
        assertEquals("4", text(5.5));
    }

    @Test void recastRestartsImmediatelyAndRetainsMeasuredTps() {
        ready();
        session.sample(0, 0);
        session.sample(20, seconds(2));
        session.receive(CAST, false, seconds(2));
        assertEquals("Esoward now", text(12));
        assertFalse(session.receive("Player: " + CAST, false, seconds(12.5)));
        assertTrue(session.receive(CAST, false, seconds(12.5)));
        assertEquals("Cells Aligned Casted!", text(14.499));
        assertEquals("4", text(14.5));
    }

    @Test void leavingDungeonDisablingChangingWorldDisconnectAndUnknownClassCancel() {
        for (int change = 0; change < 5; change++) {
            ready();
            session.receive(CAST, false, 0);
            assertTrue(session.active());
            switch (change) {
                case 0 -> session.context(world, connection, false); // Includes disabled/unknown/non-Tank.
                case 1 -> session.context(new Object(), connection, true);
                case 2 -> session.context(world, new Object(), true);
                case 3 -> session.context(null, null, false);
                case 4 -> session.reset();
            }
            assertNull(text(0.5));
            ready();
            assertNull(text(1));
            session.reset();
        }
    }

    @Test void worldChangeClearsRateHistoryAndServerClockRewindCancels() {
        ready();
        session.sample(100, 0);
        session.sample(120, seconds(2));
        session.receive(CAST, false, seconds(2));
        session.sample(5, seconds(2.5));
        assertNull(text(2.5));
        session.context(new Object(), connection, true);
        session.receive(CAST, false, seconds(3));
        assertEquals("4", text(4)); // Back to nominal 20 TPS until new sample pair.
    }

    @Test void noWorldOverlayOrFailedGateCannotStartTimer() {
        assertFalse(session.receive(CAST, false, 0));
        ready();
        assertFalse(session.receive(CAST, true, 0));
        session.context(world, connection, false);
        assertFalse(session.receive(CAST, false, 0));
        assertNull(text(100));
    }
}

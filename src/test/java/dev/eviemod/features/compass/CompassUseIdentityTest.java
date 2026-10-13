package dev.eviemod.features.compass;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompassUseIdentityTest {
    private final Object world = new Object(), connection = new Object(), player = new Object();
    private final CompassUseIdentity identities = new CompassUseIdentity();
    private CompassUseIdentity.Snapshot snapshot(boolean main, boolean off) {
        return new CompassUseIdentity.Snapshot(world, connection, player, "mini1", main, off);
    }
    private boolean send(Object packet, boolean offHand) {
        return identities.consume(packet, offHand, world, connection, player, "mini1");
    }
    @Test void identitySurvivesPredictionConsumingTheHeldItemAndIsConsumedOnlyAtSend() {
        boolean heldCompass = true;
        var before = snapshot(heldCompass, false);
        heldCompass = false; // Local block placement consumed its one-count held stack.
        var packet = new Object(); identities.bind(packet, false, before);
        assertFalse(heldCompass);
        assertEquals(1, identities.pendingCount());
        assertTrue(send(packet, false));
        assertFalse(send(packet, false));
        assertEquals(0, identities.pendingCount());
    }
    @Test void equalityDoesNotAuthorizeAnotherPacketsIdentity() {
        Object predicted = new String("same payload"), other = new String("same payload");
        assertEquals(predicted, other);
        var before = snapshot(true, false); identities.bind(predicted, false, before);
        assertFalse(send(other, false)); assertTrue(send(predicted, false));
    }
    @Test void anotherHandCannotBorrowTheCapturedCompassIdentity() {
        var before = snapshot(true, false); var packet = new Object();
        identities.bind(packet, true, before); assertFalse(send(packet, true));
        identities.bind(packet, false, before); assertFalse(send(packet, true));
        assertFalse(send(packet, false)); // The mismatching send consumed the binding.
    }
    @Test void offHandIdentityIsIndependent() {
        var before = snapshot(false, true); var packet = new Object();
        identities.bind(packet, true, before); assertTrue(send(packet, true));
        identities.bind(packet, false, before); assertFalse(send(packet, false));
    }
    @Test void canceledAndExceptionalPredictionsLeaveNoIdentityForAFutureSend() {
        var before = snapshot(true, false); var packet = new Object();
        try { identities.bind(packet, false, before); }
        finally { identities.discard(before); }
        assertFalse(send(packet, false)); assertEquals(0, identities.pendingCount());
        var next = snapshot(true, false);
        assertThrows(IllegalStateException.class, () -> {
            try { identities.bind(packet, false, next); throw new IllegalStateException("send failed"); }
            finally { identities.discard(next); }
        });
        assertFalse(send(packet, false)); assertEquals(0, identities.pendingCount());
    }
    @Test void nestedPredictionCleanupDoesNotDiscardTheOuterPacketsIdentity() {
        var outer = snapshot(true, false); var inner = snapshot(true, false);
        var outerPacket = new Object(); var innerPacket = new Object();
        identities.bind(outerPacket, false, outer); identities.bind(innerPacket, false, inner);
        identities.discard(inner);
        assertFalse(send(innerPacket, false)); assertTrue(send(outerPacket, false));
    }
    @Test void changedWorldConnectionPlayerOrLobbyRejectsAndConsumesTheBinding() {
        var before = snapshot(true, false); var packet = new Object();
        identities.bind(packet, false, before);
        assertFalse(identities.consume(packet, false, new Object(), connection, player, "mini1"));
        identities.bind(packet, false, before);
        assertFalse(identities.consume(packet, false, world, new Object(), player, "mini1"));
        identities.bind(packet, false, before);
        assertFalse(identities.consume(packet, false, world, connection, new Object(), "mini1"));
        identities.bind(packet, false, before);
        assertFalse(identities.consume(packet, false, world, connection, player, "mini2"));
        assertFalse(send(packet, false));
    }
    @Test void clearAndOverflowFailClosed() {
        var before = snapshot(true, false);
        for (int i = 0; i < 20; i++) identities.bind(new Object(), false, before);
        assertEquals(16, identities.pendingCount());
        var overflowPacket = new Object(); identities.bind(overflowPacket, false, before);
        assertFalse(send(overflowPacket, false));
        identities.clear(); assertEquals(0, identities.pendingCount());
    }
}

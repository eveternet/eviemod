package dev.eviemod.features.compass;

import java.util.IdentityHashMap;
import java.util.Objects;

/** Transient pre-prediction identity, usable only at the matching packet's send boundary. */
public final class CompassUseIdentity {
    public record Snapshot(Object world, Object connection, Object player, String session, boolean main, boolean off) {}
    private record Binding(Snapshot snapshot, boolean offHand) {}
    private final IdentityHashMap<Object, Binding> pending = new IdentityHashMap<>();

    void bind(Object packet, boolean offHand, Snapshot snapshot) {
        if (packet != null && snapshot != null && pending.size() < 16) pending.put(packet, new Binding(snapshot, offHand));
    }
    boolean consume(Object packet, boolean offHand, Object world, Object connection, Object player, String session) {
        Binding binding = pending.remove(packet);
        if (binding == null || binding.offHand != offHand) return false;
        Snapshot snapshot = binding.snapshot;
        return world != null && connection != null && player != null && session != null
            && snapshot.world == world && snapshot.connection == connection && snapshot.player == player
            && Objects.equals(snapshot.session, session) && (offHand ? snapshot.off : snapshot.main);
    }
    void discard(Snapshot snapshot) { pending.values().removeIf(binding -> binding.snapshot == snapshot); }
    void clear() { pending.clear(); }
    int pendingCount() { return pending.size(); }
}

package dev.eviemod.features.dungeons;

/** Testable lifecycle and timing boundary; all inputs are supplied by the client adapter. */
final class CellsAlignmentSession {
    private final CellsAlignmentTimer timer = new CellsAlignmentTimer();
    private final ServerTickEstimator clock = new ServerTickEstimator();
    private Object world;
    private Object connection;
    private boolean eligible;

    void context(Object nextWorld, Object nextConnection, boolean allowed) {
        if (world != nextWorld || connection != nextConnection) {
            reset();
            world = nextWorld;
            connection = nextConnection;
        }
        eligible = allowed && world != null && connection != null;
        if (!eligible) timer.reset();
    }

    boolean receive(String message, boolean overlay, long nowNanos) {
        advance(nowNanos);
        return timer.receive(message, overlay, eligible);
    }

    void sample(long serverTick, long nowNanos) {
        advance(nowNanos);
        if (!clock.sample(serverTick, nowNanos)) timer.reset();
    }

    CellsAlignmentTimer.Frame frame(long nowNanos) {
        advance(nowNanos);
        return timer.frame();
    }

    boolean active() { return timer.frame() != null; }

    private void advance(long nowNanos) { timer.advance(clock.advance(nowNanos)); }

    void reset() {
        timer.reset();
        clock.reset();
        eligible = false;
        world = null;
        connection = null;
    }
}

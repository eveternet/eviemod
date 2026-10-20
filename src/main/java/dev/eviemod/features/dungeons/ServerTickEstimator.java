package dev.eviemod.features.dungeons;

/** Monotonic elapsed time scaled by the most recent positive server world-age sample interval. */
final class ServerTickEstimator {
    private double ticksPerSecond = 20.0;
    private boolean advancing;
    private long advancedAt;
    private boolean sampled;
    private long sampledAt;
    private long serverTick;

    double advance(long nowNanos) {
        if (!advancing) {
            advancing = true;
            advancedAt = nowNanos;
            return 0;
        }
        long elapsed = nowNanos - advancedAt;
        if (elapsed <= 0) return 0;
        advancedAt = nowNanos;
        return elapsed / 1_000_000_000.0 * ticksPerSecond;
    }

    /** False denotes a server-clock rewind, requiring cancellation of the current countdown. */
    boolean sample(long tick, long nowNanos) {
        if (sampled) {
            if (tick < serverTick) {
                reset();
                sampled = true;
                serverTick = tick;
                sampledAt = nowNanos;
                return false;
            }
            // Duplicate time updates can be sent outside the periodic broadcast.
            // Do not interpret them as a stopped server or discard the prior positive interval.
            if (tick == serverTick || nowNanos - sampledAt <= 0) return true;
            ticksPerSecond = Math.min(20.0, (tick - serverTick) * 1_000_000_000.0 / (nowNanos - sampledAt));
        }
        sampled = true;
        serverTick = tick;
        sampledAt = nowNanos;
        return true;
    }

    void reset() {
        ticksPerSecond = 20.0;
        advancing = false;
        sampled = false;
    }
}

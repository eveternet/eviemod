// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.compat.sodium;

import dev.eviemod.metal.MetalBootstrap;
import dev.eviemod.metal.mtl.Mtl;
import org.lwjgl.opengl.GL32C;

/** Direct adaptation of Sodium's frame-ahead queue to the existing Metal shared-event timeline. */
public final class SodiumFrameSync {
    private SodiumFrameSync() {}
    public static long fence(int condition, int flags) {
        if (!MetalBootstrap.isActive()) return GL32C.glFenceSync(condition, flags);
        if (condition != GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE || flags != 0) throw new IllegalArgumentException("Unsupported Sodium frame fence");
        return Mtl.fence();
    }
    public static int waitFor(long value, int flags, long nanos) {
        if (!MetalBootstrap.isActive()) return GL32C.glClientWaitSync(value, flags, nanos);
        if (Mtl.completedFence() >= value) return GL32C.GL_ALREADY_SIGNALED;
        if (!Mtl.fenceWait(value, nanos == Long.MAX_VALUE ? 5000 : Math.max(1, nanos / 1_000_000)))
            throw new IllegalStateException("Sodium Metal frame fence timed out; refusing unsafe storage reuse");
        return GL32C.GL_CONDITION_SATISFIED;
    }
    public static void delete(long value) {
        if (!MetalBootstrap.isActive()) GL32C.glDeleteSync(value);
        // Metal fences are scalar event values; no separately owned native sync object.
    }
}

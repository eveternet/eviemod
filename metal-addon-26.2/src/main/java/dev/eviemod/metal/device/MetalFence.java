// SPDX-License-Identifier: GPL-3.0-only
// Derived from Im-Fran/MetalCraft, commit a2cc82780d01a51d00a297f75cf07c221d7c8700.
package dev.eviemod.metal.device;

import dev.eviemod.metal.mtl.Mtl;
import com.mojang.blaze3d.buffers.GpuFence;
import java.util.concurrent.TimeUnit;

/** Completion of everything submitted before it, tracked with an MTLSharedEvent value. */
record MetalFence(long value) implements GpuFence {
    @Override
    public boolean awaitCompletion(long timeoutNanos) {
        return Mtl.fenceWait(value, timeoutNanos == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUnit.NANOSECONDS.toMillis(timeoutNanos));
    }

    @Override
    public void close() {}
}

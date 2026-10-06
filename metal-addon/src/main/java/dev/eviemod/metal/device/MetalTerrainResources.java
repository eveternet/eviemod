// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.device;

import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.eviemod.metal.mtl.Mtl;

/** Narrow native access for Sodium terrain submission; ownership stays with MetalBuffer. */
public final class MetalTerrainResources {
    private MetalTerrainResources() {}

    public static long handle(GpuBuffer buffer) {
        if (buffer.isClosed()) throw new IllegalStateException("Closed terrain buffer");
        return ((MetalBuffer) buffer).handle;
    }

    public static long storageGeneration(GpuBuffer buffer) {
        if (buffer.isClosed()) throw new IllegalStateException("Closed terrain buffer");
        return ((MetalBuffer) buffer).storageGeneration();
    }

    /** A separately owned R32Sint view for Sodium's signed section timestamps. */
    public static long sectionTimesView(GpuBuffer buffer) {
        return Mtl.newTerrainTimeView(handle(buffer), buffer.size());
    }
}

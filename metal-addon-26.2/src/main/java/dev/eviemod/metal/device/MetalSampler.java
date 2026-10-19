// SPDX-License-Identifier: GPL-3.0-only
// Derived from Im-Fran/MetalCraft, commit a2cc82780d01a51d00a297f75cf07c221d7c8700.
package dev.eviemod.metal.device;

import dev.eviemod.metal.mtl.Mtl;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.util.OptionalDouble;

public class MetalSampler extends GpuSampler {
    final long handle;
    private final AddressMode addressModeU, addressModeV;
    private final FilterMode minFilter, magFilter;
    private final int maxAnisotropy;
    private final OptionalDouble maxLod;
    private boolean closed;

    MetalSampler(AddressMode u, AddressMode v, FilterMode min, FilterMode mag, int maxAnisotropy, OptionalDouble maxLod) {
        this.addressModeU = u;
        this.addressModeV = v;
        this.minFilter = min;
        this.magFilter = mag;
        this.maxAnisotropy = maxAnisotropy;
        this.maxLod = maxLod;
        this.handle = Mtl.newSampler(u == AddressMode.REPEAT, v == AddressMode.REPEAT, min == FilterMode.LINEAR, mag == FilterMode.LINEAR,
                maxAnisotropy, maxLod.isPresent() ? (float) maxLod.getAsDouble() : -1f);
    }

    /** The retained Metal object, for code outside the device package that encodes directly (compat shims). */
    public long handle() {
        return handle;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override public AddressMode getAddressModeU() { return addressModeU; }
    @Override public AddressMode getAddressModeV() { return addressModeV; }
    @Override public FilterMode getMinFilter() { return minFilter; }
    @Override public FilterMode getMagFilter() { return magFilter; }
    @Override public int getMaxAnisotropy() { return maxAnisotropy; }
    @Override public OptionalDouble getMaxLod() { return maxLod; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        Mtl.release(handle);
    }
}

// SPDX-License-Identifier: GPL-3.0-only
// Derived from Im-Fran/MetalCraft, commit a2cc82780d01a51d00a297f75cf07c221d7c8700.
package dev.eviemod.metal.device;

import dev.eviemod.metal.mtl.Mtl;
import com.mojang.blaze3d.textures.GpuTextureView;

public class MetalTextureView extends GpuTextureView {
    final long handle;
    private boolean closed;

    MetalTextureView(MetalTexture texture, int baseMip, int mipLevels) {
        super(texture, baseMip, mipLevels);
        int base = Math.min(baseMip, texture.metalMips - 1);
        this.handle = Mtl.newTextureView(texture.handle, base, Math.max(1, Math.min(mipLevels, texture.metalMips - base)));
    }

    /** The retained Metal object, for code outside the device package that encodes directly (compat shims). */
    public long handle() {
        return handle;
    }

    @Override
    public MetalTexture texture() {
        return (MetalTexture) super.texture();
    }

    @Override
    public boolean isClosed() {
        return closed || texture().isClosed();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        Mtl.release(handle);
    }
}

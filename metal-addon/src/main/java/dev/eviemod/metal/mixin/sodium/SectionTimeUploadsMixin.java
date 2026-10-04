// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin.sodium;

import dev.eviemod.metal.MetalBootstrap;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.MappedStagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Mutable timestamps use ordered Eviemetal uploads, unlike Sodium's fenced persistent staging ring. */
@Mixin(value = UniformBufferManager.class, remap = false)
abstract class SectionTimeUploadsMixin {
    @Redirect(method = {"<init>", "resizeIfNeeded"}, at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/gl/arena/staging/MappedStagingBuffer;isSupported(Lnet/caffeinemc/mods/sodium/client/gl/device/RenderDevice;)Z"))
    private boolean useOrderedWrites(RenderDevice device) {
        return !MetalBootstrap.isActive() && MappedStagingBuffer.isSupported(device);
    }
}

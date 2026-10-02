// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin;

import com.mojang.blaze3d.systems.GpuBackend;
import dev.eviemod.metal.MetalBootstrap;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Version boundary: prepend Metal to 26.1.2's ordered backend candidates before any window is created. */
@Mixin(Minecraft.class)
abstract class MinecraftBackendMixin {
    @ModifyVariable(method = "<init>", at = @At("STORE"), ordinal = 0)
    private GpuBackend[] eviemod$backends(GpuBackend[] defaults) {
        return MetalBootstrap.selectBackends(defaults);
    }
}

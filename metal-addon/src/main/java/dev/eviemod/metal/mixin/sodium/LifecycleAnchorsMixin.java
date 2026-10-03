// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin.sodium;

import org.spongepowered.asm.mixin.Mixin;

/** See the exact post-merge adaptations in SodiumMixinPlugin. */
final class LifecycleAnchorsMixin {
    @Mixin(targets = "net.minecraft.client.Minecraft", priority = 500)
    abstract static class Minecraft {}
    @Mixin(targets = "com.mojang.blaze3d.systems.RenderSystem", priority = 500)
    abstract static class RenderSystem {}
    @Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.SodiumConfigBuilder", priority = 500, remap = false)
    abstract static class Options {}
}

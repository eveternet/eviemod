package dev.eviemod.features.visual;

import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** Rendering-only adapter for NoammAddons' LavaToWater. See docs/lava-to-water.md. */
public final class LavaToWaterRenderer {
    public record Options(boolean enabled, boolean colorTint, int tintColor, boolean hideFog) {
        public int opaqueTint() { return 0xFF000000 | tintColor; }
    }
    private record TintedModel(FluidModel water, int color, FluidModel model) {}
    // Chunk meshes are built on worker threads; publish complete immutable snapshots.
    private static volatile Options options = new Options(false, false, 0x3F76E4, true);
    private static volatile TintedModel cachedTint;

    private LavaToWaterRenderer() {}
    public static Options options() { return options; }

    /** Called on the client thread. Returns whether existing chunk meshes need rebuilding. */
    public static boolean configure(boolean enabled, boolean colorTint, int tintColor, boolean hideFog) {
        var before = options;
        if (before.enabled == enabled && before.colorTint == colorTint
            && before.tintColor == tintColor && before.hideFog == hideFog) return false;
        options = new Options(enabled, colorTint, tintColor, hideFog);
        cachedTint = null;
        return before.enabled || enabled;
    }

    public static FluidModel model(FluidStateModelSet models, FluidState state, FluidModel original) {
        var current = options;
        if (!current.enabled || (state.getType() != Fluids.LAVA && state.getType() != Fluids.FLOWING_LAVA)) return original;
        // Re-entering get() for WATER exits above, so this cannot recurse indefinitely.
        var water = models.get(Fluids.WATER.defaultFluidState());
        if (!current.colorTint) return water;
        var cached = cachedTint;
        int color = current.opaqueTint();
        // Identity also invalidates this cache when a resource reload bakes new materials.
        if (cached != null && cached.water == water && cached.color == color) return cached.model;
        var tinted = new FluidModel(water.layer(), water.stillMaterial(), water.flowingMaterial(), water.overlayMaterial(),
            BlockTintSources.constant(color, color));
        cachedTint = new TintedModel(water, color, tinted);
        return tinted;
    }

    /** Only invoked by LavaFogEnvironment; water, atmospheric and status-effect fog keep their own paths. */
    public static boolean hideFog(FogData fog, float renderDistance) {
        var current = options;
        if (!current.enabled || !current.hideFog) return false;
        fog.color.w = 0F;
        fog.environmentalStart = renderDistance;
        fog.environmentalEnd = renderDistance;
        fog.skyEnd = renderDistance;
        fog.cloudEnd = renderDistance;
        return true;
    }
}

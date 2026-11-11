package dev.eviemod.features.visual;

import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static org.junit.jupiter.api.Assertions.*;

class LavaToWaterRendererTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach @AfterEach void reset() { LavaToWaterRenderer.configure(false, false, 0x3F76E4, false); }

    private static FluidModel water() {
        return new FluidModel(ChunkSectionLayer.TRANSLUCENT, new Material.Baked(null, true),
            new Material.Baked(null, true), new Material.Baked(null, true), BlockTintSources.water());
    }
    private static final class Models extends FluidStateModelSet {
        // Exercise the same nested get(WATER) path as the mixin, without a game launch.
        Models(FluidModel water, FluidModel lava) {
            super(Map.of(Fluids.WATER, water, Fluids.FLOWING_WATER, water,
                Fluids.LAVA, lava, Fluids.FLOWING_LAVA, lava), lava);
        }
        @Override public FluidModel get(FluidState state) {
            return LavaToWaterRenderer.model(this, state, super.get(state));
        }
    }

    @Test void onlyEnabledSourceAndFlowingLavaUseTheWaterModel() {
        var water = water();
        var lava = new FluidModel(ChunkSectionLayer.SOLID, null, null, null, null);
        var models = new Models(water, lava);
        assertSame(lava, models.get(Fluids.LAVA.defaultFluidState()));
        assertSame(lava, models.get(Fluids.FLOWING_LAVA.defaultFluidState()));
        LavaToWaterRenderer.configure(true, false, 0x3F76E4, true);
        for (var fluid : new net.minecraft.world.level.material.Fluid[]{Fluids.LAVA, Fluids.FLOWING_LAVA}) {
            var state = fluid.defaultFluidState();
            var block = state.createLegacyBlock();
            assertSame(water, models.get(state));
            assertSame(fluid, state.getType());
            assertSame(block, state.createLegacyBlock());
        }
        assertSame(water, models.get(Fluids.WATER.defaultFluidState()));
        assertSame(water, models.get(Fluids.FLOWING_WATER.defaultFluidState()));
        assertSame(lava, models.get(Fluids.EMPTY.defaultFluidState()));
        LavaToWaterRenderer.configure(false, false, 0x3F76E4, true);
        assertSame(lava, models.get(Fluids.LAVA.defaultFluidState()));
    }

    @Test void tintKeepsWaterMaterialsAndRefreshesAfterColorOrResourceChanges() {
        var water = water();
        var lava = new FluidModel(ChunkSectionLayer.SOLID, null, null, null, null);
        var models = new Models(water, lava);
        var state = Fluids.LAVA.defaultFluidState();
        LavaToWaterRenderer.configure(true, true, 0x123456, true);
        var tinted = models.get(state);
        assertSame(water.layer(), tinted.layer());
        assertSame(water.stillMaterial(), tinted.stillMaterial());
        assertSame(water.flowingMaterial(), tinted.flowingMaterial());
        assertSame(water.overlayMaterial(), tinted.overlayMaterial());
        assertEquals(0xFF123456, tinted.tintSource().color(null));
        assertEquals(0xFF123456, tinted.tintSource().colorInWorld(null, null, null));
        assertSame(tinted, models.get(state));
        assertSame(water, models.get(Fluids.WATER.defaultFluidState()));
        LavaToWaterRenderer.configure(true, true, 0xABCDEF, true);
        assertEquals(0xFFABCDEF, models.get(state).tintSource().color(null));
        var reloaded = water();
        var afterReload = new Models(reloaded, lava).get(state);
        assertSame(reloaded.stillMaterial(), afterReload.stillMaterial());
        assertNotSame(tinted, afterReload);
        LavaToWaterRenderer.configure(true, false, 0xABCDEF, true);
        assertSame(water, models.get(state));
    }

    @Test void configurationRequestsRebuildsOnlyForChangesWhileActiveOrToggling() {
        assertFalse(LavaToWaterRenderer.configure(false, true, 0x123456, false));
        assertTrue(LavaToWaterRenderer.configure(true, true, 0x123456, false));
        assertFalse(LavaToWaterRenderer.configure(true, true, 0x123456, false));
        assertTrue(LavaToWaterRenderer.configure(true, true, 0xABCDEF, false));
        assertTrue(LavaToWaterRenderer.configure(true, false, 0xABCDEF, false));
        assertTrue(LavaToWaterRenderer.configure(true, false, 0xABCDEF, true));
        assertTrue(LavaToWaterRenderer.configure(false, false, 0xABCDEF, true));
        assertFalse(LavaToWaterRenderer.configure(false, false, 0xABCDEF, true));
    }

    @Test void fogRemovalRequiresBothFlagsAndPreservesColorAndDistanceFog() {
        var fog = new FogData();
        fog.color.set(0.2F, 0.4F, 0.6F, 1F);
        fog.environmentalStart = 0.25F;
        fog.environmentalEnd = 1F;
        fog.renderDistanceStart = 100F;
        fog.renderDistanceEnd = 128F;
        assertFalse(LavaToWaterRenderer.hideFog(fog, 128F));
        LavaToWaterRenderer.configure(true, false, 0x3F76E4, false);
        assertFalse(LavaToWaterRenderer.hideFog(fog, 128F));
        assertEquals(1F, fog.color.w);
        assertEquals(0.25F, fog.environmentalStart);
        assertEquals(1F, fog.environmentalEnd);
        LavaToWaterRenderer.configure(true, false, 0x3F76E4, true);
        assertTrue(LavaToWaterRenderer.hideFog(fog, 128F));
        assertEquals(new org.joml.Vector4f(0.2F, 0.4F, 0.6F, 0F), fog.color);
        assertEquals(128F, fog.environmentalStart);
        assertEquals(128F, fog.environmentalEnd);
        assertEquals(128F, fog.skyEnd);
        assertEquals(128F, fog.cloudEnd);
        assertEquals(100F, fog.renderDistanceStart);
        assertEquals(128F, fog.renderDistanceEnd);
    }

    @Test void fogMixinPreservesVanillaWhenDisabledAndUsesOpaqueCustomColorWhenEnabled() throws Exception {
        var type = dev.eviemod.paintbrush.mixin.LavaFogMixin.class;
        var method = java.util.Arrays.stream(type.getDeclaredMethods())
            .filter(m -> m.getName().equals("eviemod$lavaFogColor")).findFirst().orElseThrow();
        method.setAccessible(true);
        var harness = new FogHarness();
        var callback = new CallbackInfoReturnable<Integer>("getBaseColor", true, 0xFFFF6600);
        method.invoke(harness, null, null, 128, 0F, callback);
        assertFalse(callback.isCancelled());
        assertEquals(0xFFFF6600, callback.getReturnValue());
        LavaToWaterRenderer.configure(true, true, 0x123456, false);
        method.invoke(harness, null, null, 128, 0F, callback);
        assertTrue(callback.isCancelled());
        assertEquals(0xFF123456, callback.getReturnValue());
    }
    private static final class FogHarness extends dev.eviemod.paintbrush.mixin.LavaFogMixin {}
}

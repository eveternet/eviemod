// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.compat.sodium;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import dev.eviemod.metal.device.MetalRenderPass;
import net.caffeinemc.mods.sodium.client.gpu.device.context.DrawContext;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.mixin.core.RenderPassAccessor;
/** Preserve Sodium's batch/arena machinery; only loose per-region uniforms need a Metal endpoint. */
public final class MetalDrawContext extends DrawContext {
    private MetalRenderPass metal;
    @Override public void setContext(RenderPass pass, RenderPipeline pipeline) {
        this.pass = pass;
        var backend = ((RenderPassAccessor) pass).getBackend();
        if (!(backend instanceof MetalRenderPass target)) throw new IllegalStateException("Sodium Metal terrain received " + backend.getClass().getName());
        metal = target;
    }
    @Override public void updateData(RenderRegion region, CameraTransform camera) {
        if (metal == null) throw new IllegalStateException("Sodium Metal terrain has no active pass");
        metal.setDefaultFloat3("u_RegionOffset", getCameraTranslation(region.getOriginX(), camera.intX, camera.fracX),
                getCameraTranslation(region.getOriginY(), camera.intY, camera.fracY), getCameraTranslation(region.getOriginZ(), camera.intZ, camera.fracZ));
        metal.setDefaultInt("u_CurrentTime", Math.toIntExact(System.currentTimeMillis() - region.getCreationTime()));
        metal.setDefaultInt("u_RegionID", region.getId());
    }
    @Override public void rotate() {}
    @Override public void delete() { metal = null; pass = null; }
    @Override public void endDraw() { metal = null; pass = null; }
}

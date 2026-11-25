package dev.eviemod.paintbrush.mixin;

import dev.eviemod.features.visual.LavaToWaterRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LavaFogEnvironment.class)
public abstract class LavaFogMixin {
    @Unique private static final WaterFogEnvironment EVIEMOD_WATER_FOG = new WaterFogEnvironment();

    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private void eviemod$hideLavaFog(FogData fog, Camera camera, ClientLevel level, float renderDistance,
                                   DeltaTracker deltaTracker, CallbackInfo ci) {
        if (LavaToWaterRenderer.hideFog(fog, renderDistance)) ci.cancel();
    }

    @Inject(method = "getBaseColor", at = @At("HEAD"), cancellable = true)
    private void eviemod$lavaFogColor(ClientLevel level, Camera camera, int renderDistance, float partialTicks,
                                     CallbackInfoReturnable<Integer> cir) {
        var options = LavaToWaterRenderer.options();
        if (!options.enabled()) return;
        cir.setReturnValue(options.colorTint() ? options.opaqueTint()
            : EVIEMOD_WATER_FOG.getBaseColor(level, camera, renderDistance, partialTicks));
    }
}

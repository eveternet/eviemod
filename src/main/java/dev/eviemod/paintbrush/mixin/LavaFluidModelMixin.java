package dev.eviemod.paintbrush.mixin;

import dev.eviemod.features.visual.LavaToWaterRenderer;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidStateModelSet.class)
public abstract class LavaFluidModelMixin {
    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private void eviemod$lavaModel(FluidState state, CallbackInfoReturnable<FluidModel> cir) {
        var original = cir.getReturnValue();
        var model = LavaToWaterRenderer.model((FluidStateModelSet) (Object) this, state, original);
        if (model != original) cir.setReturnValue(model);
    }
}

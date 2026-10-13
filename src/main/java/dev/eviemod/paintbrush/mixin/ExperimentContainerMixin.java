package dev.eviemod.paintbrush.mixin;

import dev.eviemod.features.experiments.ExperimentClient;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ExperimentContainerMixin {
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void eviemod$manualExperimentClick(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (ExperimentClient.cancelClick((AbstractContainerScreen<?>)(Object)this, slot, slotId, button, input)) ci.cancel();
    }
}

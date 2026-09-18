package dev.eviemod.features.garden.mixin.client;

import dev.eviemod.features.garden.GardenToolsClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
   @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
   private void gardenTools$lockFarmingView(double movementTime, CallbackInfo ci) {
      if (GardenToolsClient.isMouseLocked()) {
         ci.cancel();
      }
   }
}

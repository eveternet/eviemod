package dev.eviemod.features.skyblock.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.eviemod.features.compass.WishingCompassClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import org.spongepowered.asm.mixin.Mixin;

/** Identity is captured before item prediction, but only the real send may arm a reading. */
@Mixin(MultiPlayerGameMode.class)
public abstract class CompassPredictionMixin {
    @WrapMethod(method = "startPrediction")
    private void eviemod$preserveCompassIdentity(ClientLevel level, PredictiveAction action, Operation<Void> original) {
        var snapshot = WishingCompassClient.predictionSnapshot();
        if (snapshot == null) {
            original.call(level, action);
            return;
        }
        try {
            PredictiveAction observed = sequence -> {
                var packet = action.predict(sequence);
                WishingCompassClient.bindPredictionPacket(packet, snapshot);
                return packet;
            };
            original.call(level, observed);
        } finally {
            // Includes another mod cancelling a send, prediction/send exceptions and nesting.
            WishingCompassClient.finishPrediction(snapshot);
        }
    }
}

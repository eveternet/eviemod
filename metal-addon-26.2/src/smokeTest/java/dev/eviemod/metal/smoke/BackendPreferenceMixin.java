// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.smoke;
import com.mojang.blaze3d.systems.GpuBackend;
import dev.eviemod.metal.fixture.PreflightProbe;
import net.minecraft.client.PreferredGraphicsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PreferredGraphicsApi.class)
abstract class BackendPreferenceMixin {
    @Inject(method="getBackendsToTry",at=@At("RETURN"))
    private void order(CallbackInfoReturnable<GpuBackend[]> ci) {
        String expected=System.getProperty("eviemod.metal.expectedPreference");
        if(expected==null)return;
        String actual=((PreferredGraphicsApi)(Object)this).getSerializedName();
        if(!actual.equals(expected))throw new AssertionError("Vanilla graphics preference changed: "+actual);
        PreflightProbe.vanillaCandidates=java.util.Arrays.stream(ci.getReturnValue()).map(GpuBackend::getName).toArray(String[]::new);
        System.out.println("EVIEMETAL_VANILLA_ORDER_OK preference="+actual+" candidates="+String.join(",",PreflightProbe.vanillaCandidates));
    }
}

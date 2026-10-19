package dev.eviemod.metal.smoke;

import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
abstract class SmokeWatchdogMixin {
    /** Start before Minecraft construction so native hangs and blocking startup dialogs also fail CI. */
    @Inject(method = "main", at = @At("HEAD"))
    private static void startWatchdog(String[] args, CallbackInfo ci) {
        Thread.ofPlatform().daemon().name("metal-smoke-watchdog").start(() -> {
            try { Thread.sleep(Boolean.getBoolean("eviemod.metal.worldSmoke") ? 900_000 : 180_000); }
            catch (InterruptedException e) { return; }
            System.err.println("Addon launch/readback watchdog timed out");
            Thread.getAllStackTraces().forEach((thread, trace) -> {
                System.err.println(thread);
                for (var frame : trace) System.err.println("    at " + frame);
            });
            Runtime.getRuntime().halt(1);
        });
    }
}

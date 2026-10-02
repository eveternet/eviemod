package dev.eviemod.metal.smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import java.nio.file.Path;
import java.io.IOException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class FallbackLaunchMixin {
    private int readyFrames;
    private boolean requestedScreenshot;
    private final long smokeStart = System.nanoTime();

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void verifyFallback(CallbackInfo ci) {
        if (Boolean.getBoolean("eviemod.metal.smokeExpected")) {
            if (!RenderSystem.getDevice().getBackendName().equals("Metal")) throw new AssertionError("Expected active Metal backend");
            return;
        }
        if (!RenderSystem.getDevice().getBackendName().equals("OpenGL")) {
            throw new AssertionError("Disabled or unsupported Metal addon must preserve OpenGL");
        }
        System.out.println("EVIEMOD_METAL_FALLBACK_OK");
        ci.cancel();
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void verifyMetalFrame(boolean renderLevel, CallbackInfo ci) {
        if (!Boolean.getBoolean("eviemod.metal.smokeExpected")) return;
        var mc = (Minecraft) (Object) this;
        if (System.nanoTime() - smokeStart > 180_000_000_000L) throw new AssertionError("Metal launch/readback timed out");
        // CI has a fresh options file; choose the screen under test after the initial resource load.
        if (mc.getOverlay() == null && mc.screen instanceof AccessibilityOnboardingScreen) {
            mc.setScreen(new TitleScreen());
            return;
        }
        if (requestedScreenshot || mc.getOverlay() != null || !(mc.screen instanceof TitleScreen) || ++readyFrames < 10) return;
        requestedScreenshot = true;
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
            try (image) {
                int first = image.getPixel(0, 0);
                boolean varied = false;
                for (int y = 0; y < image.getHeight(); y += 20) {
                    for (int x = 0; x < image.getWidth(); x += 20) varied |= image.getPixel(x, y) != first;
                }
                if (!varied) throw new AssertionError("Metal rendered a blank frame");
                image.writeToFile(Path.of("metal-smoke.png"));
                System.out.println("EVIEMOD_METAL_FRAME_OK");
                mc.stop();
            } catch (IOException e) { throw new AssertionError("Could not save Metal smoke frame", e); }
        });
    }
}

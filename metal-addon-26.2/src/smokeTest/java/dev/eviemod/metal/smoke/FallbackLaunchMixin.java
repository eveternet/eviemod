package dev.eviemod.metal.smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.eviemod.metal.fixture.OptionalGuiProbe;
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
    private long readySince;
    private boolean requestedScreenshot;
    private final long smokeStart = System.nanoTime();

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void verifyFallback(CallbackInfo ci) {

        if (Boolean.getBoolean("eviemod.metal.distributionExpected")) {
            String source = dev.eviemod.metal.EvieMetal.class.getProtectionDomain().getCodeSource().getLocation().toString();
            if (!source.endsWith(".jar")) throw new AssertionError("Expected packaged addon JAR, loaded " + source);
            System.out.println("EVIEMETAL_DISTRIBUTION_SOURCE " + source);
            for (Class<?> tool : new Class<?>[]{org.lwjgl.util.shaderc.Shaderc.class, org.lwjgl.util.spvc.Spvc.class}) {
                String packaged = tool.getProtectionDomain().getCodeSource().getLocation().toString();
                if (packaged.contains("modules-2/files-2.1")) throw new AssertionError("Shader tool loaded from loose development dependency: " + packaged);
                System.out.println("EVIEMETAL_SHADER_TOOL_SOURCE " + packaged);
            }
        }
        if (Boolean.getBoolean("eviemod.metal.sodiumExpected")) {
            var sodium = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("sodium").orElseThrow(
                    () -> new AssertionError("Expected installed pinned Sodium in packaged fixture"));
            String version = sodium.getMetadata().getVersion().getFriendlyString();
            if (!version.equals("0.9.2+mc26.2")) throw new AssertionError("Unexpected fixture Sodium: " + version);
            System.out.println("EVIEMETAL_PINNED_SODIUM_OK version=" + version);
        }
        if (Boolean.getBoolean("eviemod.metal.smokeExpected")) {
            if (!RenderSystem.getDevice().getDeviceInfo().backendName().equals("Metal")) throw new AssertionError("Expected active Metal backend");
            return;
        }
        String selected = RenderSystem.getDevice().getDeviceInfo().backendName();
        if (Boolean.getBoolean("eviemod.metal.fixturePreflightFailure") && !dev.eviemod.metal.fixture.PreflightProbe.cleaned)
            throw new AssertionError("Native preflight failure did not clean up before vanilla retry");
        if (System.getProperty("eviemod.metal.expectedPreference") != null && (dev.eviemod.metal.fixture.PreflightProbe.vanillaCandidates == null
                || java.util.Arrays.stream(dev.eviemod.metal.fixture.PreflightProbe.vanillaCandidates).noneMatch(selected::equals)))
            throw new AssertionError("Selected backend outside vanilla candidate list: " + selected);
        if (selected.equals("Metal")) throw new AssertionError("Expected Minecraft's preferred backend with Metal disabled/unsupported");
        System.out.println("EVIEMOD_METAL_FALLBACK_OK backend=" + selected);
        if (System.getProperty("eviemod.metal.guiSmokeScreen") != null) return;
        ci.cancel();
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void verifyMetalFrame(boolean renderLevel, CallbackInfo ci) {
        if (!Boolean.getBoolean("eviemod.metal.smokeExpected") && System.getProperty("eviemod.metal.guiSmokeScreen") == null) return;
        if (Boolean.getBoolean("eviemod.metal.worldSmoke")) return;
        var mc = (Minecraft) (Object) this;
        if (System.nanoTime() - smokeStart > 180_000_000_000L) throw new AssertionError("Metal launch/readback timed out");
        // CI has a fresh options file; choose the screen under test after the initial resource load.
        if (mc.gui.overlay() == null && mc.gui.screen() instanceof AccessibilityOnboardingScreen) {
            mc.gui.setScreen(new TitleScreen());
            return;
        }
        if (requestedScreenshot || mc.gui.overlay() != null || !OptionalGuiProbe.prepare(mc)) return;
        if (readySince == 0) readySince = System.nanoTime();
        // The 26.2 title transition can still be entirely black immediately after loading ends.
        if (++readyFrames < 10 || System.nanoTime() - readySince < 5_000_000_000L) return;
        requestedScreenshot = true;
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                int first = image.getPixel(0, 0);
                boolean varied = false;
                for (int y = 0; y < image.getHeight(); y += 20) {
                    for (int x = 0; x < image.getWidth(); x += 20) varied |= image.getPixel(x, y) != first;
                }
                image.writeToFile(Path.of("metal-smoke.png"));
                if (!varied) throw new AssertionError("Metal rendered a blank frame; saved metal-smoke.png");
                System.out.println("EVIEMOD_METAL_FRAME_OK");
                mc.stop();
            } catch (IOException e) { throw new AssertionError("Could not save Metal smoke frame", e); }
        });
    }
}

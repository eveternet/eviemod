package dev.eviemod.metal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import dev.eviemod.metal.mtl.Mtl;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Explicit development fixture only, never included in the distributed addon. */
@Mixin(Minecraft.class)
abstract class SodiumWorldSmokeMixin {
    private boolean opened;
    private int frames;
    private int worldFrames;

    @Inject(method = "runTick", at = @At("TAIL"))
    private void world(boolean tick, CallbackInfo ci) {
        if (!Boolean.getBoolean("eviemod.metal.worldSmoke")) return;
        Minecraft mc = (Minecraft) (Object) this;
        if (!opened && mc.screen instanceof TitleScreen && mc.getOverlay() == null && ++frames > 20) {
            opened = true;
            mc.options.renderDistance().set(6);
            mc.options.simulationDistance().set(5);
            mc.options.pauseOnLostFocus = false;
            String name = "sodium-metal-fixture";
            if (java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(name).resolve("level.dat"))) {
                mc.createWorldOpenFlows().openWorld(name, () -> mc.setScreen(new TitleScreen()));
            } else {
                mc.createWorldOpenFlows().createFreshLevel(name,
                        new LevelSettings("Sodium Metal fixture", GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(424242L, true, false),
                        registry -> registry.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(),
                        new TitleScreen());
            }
        }
        if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
        worldFrames++;
        if (worldFrames % 300 == 0) {
            System.out.println("EVIEMETAL_SODIUM_WORLD frame=" + worldFrames + " metalBytes=" + Mtl.allocatedBytes()
                    + " position=" + mc.player.position());
            Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
                try (image) { image.writeToFile(Path.of("sodium-world-" + worldFrames + ".png")); }
                catch (java.io.IOException e) { throw new AssertionError(e); }
            });
        }
    }
}

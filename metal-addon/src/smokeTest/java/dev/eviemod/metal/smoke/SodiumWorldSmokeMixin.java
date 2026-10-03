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
    private long worldStart;
    private int phase;
    private boolean reopen;
    private boolean stopping;
    private final int[] atSeconds = {3, 8, 12, 16, 20, 25, 28, 32, 40, 48, 58, 65, 80, 88, 93, 100, 112, 124, 138, 160, 178};

    @Inject(method = "runTick", at = @At("TAIL"))
    private void world(boolean tick, CallbackInfo ci) {
        if (!Boolean.getBoolean("eviemod.metal.worldSmoke")) return;
        Minecraft mc = (Minecraft) (Object) this;
        if (java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve(".metal-world-stop"))) { mc.stop(); return; }
        if (reopen && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            reopen = false;
            mc.createWorldOpenFlows().openWorld("sodium-metal-fixture", () -> mc.setScreen(new TitleScreen()));
        }
        if (stopping && mc.level == null && mc.screen instanceof TitleScreen) {
            if (!Mtl.fenceWait(Mtl.fence(), 5000)) throw new AssertionError("Final GPU fence timed out");
            dev.eviemod.metal.compat.sodium.SodiumMetal.assertWorldReleased();
            System.out.println("EVIEMETAL_SODIUM_RESOURCES " + dev.eviemod.metal.compat.sodium.SodiumMetal.resourceSummary());
            System.out.println("EVIEMETAL_SODIUM_LIFECYCLE_OK metalBytesAfterUnload=" + Mtl.allocatedBytes());
            mc.stop(); return;
        }
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
        if (worldStart == 0) worldStart = System.nanoTime();
        double seconds = (System.nanoTime() - worldStart) / 1_000_000_000.0;
        if (phase < atSeconds.length && seconds >= atSeconds[phase]) {
            System.out.println("EVIEMETAL_SODIUM_PHASE " + phase + " seconds=" + seconds);
            switch (phase++) {
                case 0 -> {
                    mc.player.getAbilities().flying = true; mc.player.onUpdateAbilities();
                    // The test pad straddles four chunks outside a saved player's initial spawn tickets.
                    mc.getSingleplayerServer().execute(() -> {
                        for (int x = -1; x <= 0; x++) for (int z = -1; z <= 0; z++)
                            mc.getSingleplayerServer().overworld().getChunk(x, z);
                    });
                    commands(mc, "gamemode creative @a", "time set noon", "weather clear", "tp @a 0 124 10 180 20",
                            "fill -12 119 -12 12 119 12 stone", "fill -12 120 -12 12 128 12 air",
                            "fill -11 120 -5 -4 120 5 stone", "fill -10 120 -4 -5 120 4 water",
                            "fill 4 120 -5 4 123 5 glass", "fill 6 120 -5 6 123 5 red_stained_glass",
                            "setblock 1 120 1 chest[facing=south]", "setblock -2 120 0 campfire",
                            "setblock 2 120 -3 oak_sign", "summon cow 0 120 -4 {NoAI:1b}",
                            "summon armor_stand 2 120 -1 {NoGravity:1b}",
                            "particle flame 0 121 0 0.3 0.3 0.3 0.01 80 force @a");
                }
                case 1, 19 -> capture(mc, "mixed-" + phase);
                case 2 -> {
                    mc.levelRenderer.destroyBlockProgress(12345, new net.minecraft.core.BlockPos(4, 122, 0), 6);
                    capture(mc, "breaking");
                }
                case 3 -> {
                    capture(mc, "breaking");
                    mc.levelRenderer.destroyBlockProgress(12345, new net.minecraft.core.BlockPos(4, 122, 0), -1);
                    commands(mc, "setblock 4 120 0 oak_leaves", "particle campfire_cosy_smoke -2 121 0 0.2 0.5 0.2 0.01 60 force @a");
                }
                case 4 -> commands(mc, "setblock 4 120 0 glass", "tp @a 0 124 8 140 30");
                case 5 -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                case 6 -> { capture(mc, "inventory"); mc.setScreen(null); }
                case 7 -> commands(mc, "tp @a 160 140 0 90 20");
                case 8 -> commands(mc, "tp @a 320 140 0 180 20");
                case 9 -> commands(mc, "tp @a 0 124 10 180 20");
                case 10 -> mc.levelRenderer.allChanged();
                case 11 -> mc.reloadResourcePacks();
                case 12 -> mc.getWindow().setWindowed(1280, 720);
                case 13, 14 -> mc.getWindow().toggleFullScreen();
                case 15 -> commands(mc, "execute in minecraft:the_nether run tp @a 0 90 0 90 20");
                case 16 -> { assertDimension(mc, net.minecraft.world.level.Level.NETHER); commands(mc, "execute in minecraft:the_end run tp @a 0 90 0 90 20"); }
                case 17 -> { assertDimension(mc, net.minecraft.world.level.Level.END); commands(mc, "execute in minecraft:overworld run tp @a 0 124 10 180 20"); }
                case 18 -> { assertDimension(mc, net.minecraft.world.level.Level.OVERWORLD); reopen = true; mc.disconnect(new TitleScreen(), false); }
                case 20 -> { stopping = true; mc.disconnect(new TitleScreen(), false); }
            }
        }
        if (mc.level == null || mc.player == null) return;
        if (worldFrames % 600 == 0) {
            System.out.println("EVIEMETAL_SODIUM_WORLD frame=" + worldFrames + " metalBytes=" + Mtl.allocatedBytes()
                    + " position=" + mc.player.position() + " " + dev.eviemod.metal.compat.sodium.SodiumMetal.resourceSummary());
            capture(mc, "phase-" + phase + "-" + worldFrames);
        }
    }

    private static void assertDimension(Minecraft mc, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> expected) {
        if (!mc.level.dimension().equals(expected)) throw new AssertionError("Expected fixture dimension " + expected + ", got " + mc.level.dimension());
    }

    private static void commands(Minecraft mc, String... commands) {
        var server = mc.getSingleplayerServer();
        if (server == null) throw new AssertionError("No fixture server");
        server.execute(() -> {
            for (String command : commands) {
                try { server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack().withSuppressedOutput()); }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new AssertionError("Fixture command failed: " + command, e); }
            }
        });
    }

    private static void capture(Minecraft mc, String label) {
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
            try (image) { image.writeToFile(Path.of("sodium-" + label + ".png")); }
            catch (java.io.IOException e) { throw new AssertionError(e); }
        });
    }
}

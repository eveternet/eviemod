// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.fixture;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;

/** Uses the real pinned allocator/manager, inside the packaged client, with completed GPU readbacks. */
public final class SodiumStress {
    private static final Set<GpuBuffer> OWNED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int completedWorlds;
    public static void track(Supplier<String> label, GpuBuffer buffer) {
        if (label == null || !FabricLoader.getInstance().isModLoaded("sodium")) return;
        String name = label.get();
        if (name.equals("Arena buffer") || name.equals("Section time info") || name.startsWith("Sodium terrain uniforms")) OWNED.add(buffer);
    }
    public static void run() {
        if (!FabricLoader.getInstance().isModLoaded("sodium")) return;
        net.caffeinemc.mods.sodium.client.gpu.arena.MetalArenaStress.run();
        try {
            var field = SodiumWorldRenderer.class.getDeclaredField("uniformBufferManager");
            field.setAccessible(true);
            var manager = (UniformBufferManager) field.get(SodiumWorldRenderer.instance());
            if (manager == null) throw new AssertionError("Missing Sodium world uniform manager");
            for (int iteration = 0; iteration < 3; iteration++) {
                var old = manager.getSectionTimeInfo();
                // A reserved last region avoids changing currently visible section fade times.
                int id = Math.toIntExact(old.size() / 1024 - 1);
                int expected = 0x123400 + iteration;
                manager.writeMeshTimes(id, 17, expected);
                manager.resizeIfNeeded(id + 2);
                if (!old.isClosed()) throw new AssertionError("Replaced section timestamp buffer stayed open");
                var next = manager.getSectionTimeInfo();
                try (var readback = RenderSystem.getDevice().createBuffer(() -> "Timestamp fixture readback", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, next.size())) {
                    RenderSystem.getDevice().createCommandEncoder().copyToBuffer(next.slice(), readback.slice());
                    try (var map = readback.map(true, false)) {
                        if (map.data().getInt(id * 1024 + 17 * 4) != expected) throw new AssertionError("Timestamp prefix lost on resize");
                        if (map.data().getInt(Math.toIntExact(next.size() - 4)) != -1) throw new AssertionError("Timestamp tail not initialized");
                    }
                }
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError("Pinned Sodium uniform-manager anchor changed", e); }
        System.out.println("EVIEMETAL_SODIUM_RESIZE_OK timestampResizes=3");
    }
    public static void assertUnloaded() {
        if (!FabricLoader.getInstance().isModLoaded("sodium")) return;
        if (OWNED.isEmpty()) throw new AssertionError("Ownership fixture observed no Sodium buffers");
        long open = OWNED.stream().filter(buffer -> !buffer.isClosed()).count();
        if (open != 0) throw new AssertionError("Sodium retained " + open + " world-owned buffers after unload");
        System.out.println("EVIEMETAL_SODIUM_OWNERSHIP_OK world=" + ++completedWorlds + " closedBuffers=" + OWNED.size() + " openBuffers=0");
        OWNED.clear();
    }
}

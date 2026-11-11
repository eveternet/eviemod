package dev.eviemod.compat;

import net.minecraft.client.Minecraft;

/** Minecraft 26.2 moved chunk invalidation to LevelExtractor. */
public final class WorldRendering {
    private WorldRendering() {}
    public static void rebuildChunks(Minecraft client) {
        if (client.level != null && client.levelExtractor != null) client.levelExtractor.allChanged();
    }
}

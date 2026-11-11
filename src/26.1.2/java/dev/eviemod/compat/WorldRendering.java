package dev.eviemod.compat;

import net.minecraft.client.Minecraft;

/** Minecraft 26.1.2 owns chunk invalidation in LevelRenderer. */
public final class WorldRendering {
    private WorldRendering() {}
    public static void rebuildChunks(Minecraft client) {
        if (client.level != null && client.levelRenderer != null) client.levelRenderer.allChanged();
    }
}

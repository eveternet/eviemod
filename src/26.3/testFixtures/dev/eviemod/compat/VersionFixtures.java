package dev.eviemod.compat;

/** Offline-only access to version-specific vanilla fixtures; excluded from the mod JAR. */
public final class VersionFixtures {
    private VersionFixtures() {}
    public static net.minecraft.core.HolderLookup.Provider registries() {
        return net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
    }
    public static Object equipState() {
        return new net.minecraft.client.player.FirstPersonHandsAndItems();
    }
    public static Object fogColor(int rgb) {
        return new org.joml.Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }
    public static net.minecraft.client.input.KeyEvent keyEvent(int key) {
        return new net.minecraft.client.input.KeyEvent(key, org.lwjgl.sdl.SDLKeyboard.SDL_GetKeyFromScancode(key, 0, false), 0);
    }
}

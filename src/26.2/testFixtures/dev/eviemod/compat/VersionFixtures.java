package dev.eviemod.compat;

/** Offline-only access to version-specific vanilla fixtures; excluded from the mod JAR. */
public final class VersionFixtures {
    public static final int ATTACK_SWING_CALLS = 2;
    public static final String ATTACK_SWING_DESCRIPTOR = "(Lnet/minecraft/world/InteractionHand;)V";
    private VersionFixtures() {}
    public static net.minecraft.core.HolderLookup.Provider registries() {
        return net.minecraft.data.registries.VanillaRegistries.createLookup();
    }
    public static Object equipState() {
        return net.minecraft.client.Minecraft.getInstance().gameRenderer.itemInHandRenderer;
    }
    public static Object fogColor(int rgb) {
        return 0xff000000 | rgb;
    }
    public static net.minecraft.client.input.KeyEvent keyEvent(int key) {
        return new net.minecraft.client.input.KeyEvent(key, 0, 0);
    }
}

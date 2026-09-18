package dev.eviemod.paintbrush;

import java.io.IOException;
import java.util.List;
import net.azureaaron.dandelion.api.*;
import net.azureaaron.dandelion.api.patching.ConfigPatch;
import net.azureaaron.dandelion.deps.moulconfig.platform.MoulConfigScreenComponent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.slf4j.LoggerFactory;

public final class EviemodSettings {
    static final ModSettings STORE = new ModSettings(ConfigMigration.path("eviemod.json"));
    public static ImportedFeatures features() { return STORE.values().features; }
    private static String pendingError;
    private record ScreenOrigin(Screen parent, List<ItemStack> fixtures) {}
    private static final java.util.Map<Screen, ScreenOrigin> ORIGINS = new java.util.WeakHashMap<>();
    static void load() {
        try { STORE.load(); ImportedFeatureMigration.run(STORE, FabricLoader.getInstance().getConfigDir()); }
        catch (IOException e) { LoggerFactory.getLogger("eviemod").error(e.getMessage(), e); }
    }
    static void tick(Minecraft client) {
        ImportedKeyBindings.tick();
        if (pendingError != null) {
            String message = pendingError; pendingError = null;
            Screen parent = client.screen;
            client.setScreen(new AlertScreen(() -> client.setScreen(parent), Component.literal("Settings could not be saved"), Component.literal(message)));
        }
    }
    public static Screen screen(Screen parent) { return screen(parent, "", null); }
    static Screen screen(Screen parent, String search, List<ItemStack> fixtures) {
        if (STORE.error() != null) return new AlertScreen(() -> Minecraft.getInstance().setScreen(parent),
            Component.literal("Settings could not be loaded"), Component.literal(STORE.error()));
        var manager = new SettingsManager();
        var screen = (MoulConfigScreenComponent) DandelionConfigScreen.create(manager, (defaults, draft, builder) ->
            builder.title(Component.literal("eviemod")).search(search)
                .categories(SettingsCategories.create(draft, fixtures)))
            .generateScreen(parent, ConfigType.MOUL_CONFIG);
        screen.getGuiContext().setCloseRequestHandler(() -> {
            if (manager.save()) Minecraft.getInstance().setScreen(parent);
        });
        ORIGINS.put(screen, new ScreenOrigin(parent, fixtures));
        return screen;
    }

    static void rebuild(Screen current, ModSettings.Values draft, String search) {
        try {
            STORE.save(draft);
            var origin = ORIGINS.getOrDefault(current, new ScreenOrigin(null, null));
            Minecraft.getInstance().setScreen(screen(origin.parent(), search, origin.fixtures()));
        } catch (IOException e) { pendingError = e.getMessage(); }
    }

    /** Adapts the shared validated, atomic settings store. */
    private static final class SettingsManager extends net.azureaaron.dandelion.impl.ConfigManagerImpl<ModSettings.Values> {
        private final ModSettings.Values draft = STORE.values().copy();
        SettingsManager() { super(ModSettings.Values.class, ConfigMigration.path("eviemod.json"), builder -> builder); }
        @Override protected ModSettings.Values createNewConfigInstance() { return new ModSettings.Values(); }
        @Override public boolean saveAll() { return save(); }
        @Override public Class<ModSettings.Values> configClass() { return ModSettings.Values.class; }
        @Override public ModSettings.Values instance() { return draft; }
        @Override public ModSettings.Values unpatchedInstance() { return draft; }
        @Override public ModSettings.Values defaults() { return new ModSettings.Values(); }
        @Override public boolean save() {
            try { STORE.save(draft); RarityBackgrounds.clear(); return true; }
            catch (IOException e) { pendingError = e.getMessage(); return false; }
        }
        @Override public boolean load() { return false; }
        @Override public void updatePatchedInstance() {}
        @Override public void setPatches(List<ConfigPatch> patches) {
            if (!patches.isEmpty()) throw new UnsupportedOperationException("Config patches are not enabled");
        }
    }
}

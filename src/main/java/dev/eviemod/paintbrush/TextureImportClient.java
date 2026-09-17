package dev.eviemod.paintbrush;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** Desktop chooser and resource reload boundary; imported files never leave this computer. */
final class TextureImportClient {
    private TextureImportClient() {}
    static Path choosePng() {
        try (var memory = MemoryStack.stackPush()) {
            String chosen = TinyFileDialogs.tinyfd_openFileDialog("Import Paint Brush PNG", "",
                memory.pointers(memory.UTF8("*.png")), "PNG image", false);
            return chosen == null ? null : Path.of(chosen);
        }
    }
    static CompletableFuture<Identifier> importFile(Path path, ImportedTextures.Preset preset) {
        var client = Minecraft.getInstance();
        return CompletableFuture.supplyAsync(() -> {
            try { return new ImportedTextures(client.getResourcePackDirectory()).importFile(path, preset); }
            catch (Exception e) { throw new CompletionException(e); }
        }).thenComposeAsync(id -> loadPack(id), client);
    }
    static CompletableFuture<Identifier> importSkin(HelmetSkinCatalog.Skin skin) {
        var client = Minecraft.getInstance();
        return CompletableFuture.supplyAsync(() -> {
            try { return new ImportedTextures(client.getResourcePackDirectory()).importPng(skin.download(), ImportedTextures.Preset.HELMET); }
            catch (Exception e) { throw new CompletionException(e); }
        }).thenComposeAsync(id -> loadPack(id), client);
    }
    static CompletableFuture<Identifier> loadPack(Identifier id) {
        var client = Minecraft.getInstance();
        var packs = client.getResourcePackRepository();
        packs.reload();
        if (!packs.isAvailable(ImportedTextures.PACK_ID)) return CompletableFuture.failedFuture(new IllegalStateException("The imported texture pack could not be loaded."));
        packs.addPack(ImportedTextures.PACK_ID);
        client.options.updateResourcePacks(packs);
        return client.reloadResourcePacks().thenApplyAsync(ignored -> {
            if (!available(id)) throw new CompletionException(new IllegalStateException("The imported texture could not be loaded."));
            return id;
        }, client);
    }
    static boolean available(Identifier id) {
        if (id == null) return false;
        var resources = Minecraft.getInstance().getResourceManager();
        return resources.getResource(id.withPath("items/" + id.getPath() + ".json")).isPresent()
            && resources.getResource(ImportedTextures.texture(id)).isPresent();
    }
}

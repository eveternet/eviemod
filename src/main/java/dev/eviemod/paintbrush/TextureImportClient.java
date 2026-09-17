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
    static Path choosePng() { return chooseFile("Import Paint Brush PNG", "*.png", "PNG image"); }
    static Path chooseMetadata() { return chooseFile("Choose animation metadata", "*.mcmeta", "Animation metadata"); }
    private static Path chooseFile(String title, String pattern, String description) {
        try (var memory = MemoryStack.stackPush()) {
            String chosen = TinyFileDialogs.tinyfd_openFileDialog(title, "",
                memory.pointers(memory.UTF8(pattern)), description, false);
            return chosen == null ? null : Path.of(chosen);
        }
    }
    static CompletableFuture<ImportedTextures.Prepared> prepare(Path path, ImportedTextures.Preset preset) {
        return CompletableFuture.supplyAsync(() -> {
            try { return ImportedTextures.prepare(path, preset); }
            catch (Exception e) { throw new CompletionException(e); }
        });
    }
    static CompletableFuture<Identifier> importPrepared(ImportedTextures.Prepared prepared, Path metadata) {
        var client = Minecraft.getInstance();
        return CompletableFuture.supplyAsync(() -> {
            try { return new ImportedTextures(client.getResourcePackDirectory()).importPrepared(prepared, metadata); }
            catch (Exception e) { throw new CompletionException(e); }
        }).thenComposeAsync(TextureImportClient::loadPack, client);
    }
    static CompletableFuture<Identifier> importSkin(HelmetSkinCatalog.Skin skin) {
        var client = Minecraft.getInstance();
        return CompletableFuture.supplyAsync(() -> {
            try { return new ImportedTextures(client.getResourcePackDirectory()).importSkin(skin.download(), skin); }
            catch (Exception e) { throw new CompletionException(e); }
        }).thenComposeAsync(id -> loadPack(id), client);
    }
    static CompletableFuture<Identifier> loadPack(Identifier id) {
        if (ImportedTextures.isHelmet(id)) return HelmetTextures.refresh(id);
        var client = Minecraft.getInstance();
        var packs = client.getResourcePackRepository();
        packs.reload();
        if (!packs.isAvailable(ImportedTextures.PACK_ID)) return CompletableFuture.failedFuture(new IllegalStateException("The imported texture pack could not be loaded."));
        packs.addPack(ImportedTextures.PACK_ID);
        // updateResourcePacks starts an unobservable reload; persist just our selection and await one reload below.
        if (!client.options.resourcePacks.contains(ImportedTextures.PACK_ID)) {
            client.options.resourcePacks.add(ImportedTextures.PACK_ID);
            client.options.save();
        }
        return client.reloadResourcePacks().thenApplyAsync(ignored -> {
            if (!available(id) || client.getModelManager().getItemModel(id) instanceof net.minecraft.client.renderer.item.MissingItemModel) throw new CompletionException(new IllegalStateException("The imported texture could not be loaded."));
            return id;
        }, client);
    }
    static boolean available(Identifier id) {
        if (id == null) return false;
        if (ImportedTextures.isHelmet(id)) return HelmetTextures.available(id);
        var resources = Minecraft.getInstance().getResourceManager();
        return resources.getResource(id.withPath("items/" + id.getPath() + ".json")).isPresent()
            && resources.getResource(ImportedTextures.texture(id)).isPresent();
    }
}

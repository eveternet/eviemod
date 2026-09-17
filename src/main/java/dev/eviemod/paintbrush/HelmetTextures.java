package dev.eviemod.paintbrush;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Loads only the chosen skin; vanilla player-head models already exist and need no pack reload. */
final class HelmetTextures {
    private static final Map<Identifier, CompletableFuture<Identifier>> loaded = new HashMap<>();
    static CompletableFuture<Identifier> load(Identifier id) {
        if (!ImportedTextures.isHelmet(id)) return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid helmet skin"));
        return loaded.computeIfAbsent(id, key -> {
            var client = Minecraft.getInstance();
            var file = client.getResourcePackDirectory().resolve(ImportedTextures.PACK_FOLDER)
                .resolve("assets/" + ImportedTextures.NAMESPACE).resolve(ImportedTextures.texture(key).getPath());
            return CompletableFuture.supplyAsync(() -> {
                try (var stream = Files.newInputStream(file)) {
                    return ImportedTextures.validatedPng(stream.readNBytes(ImportedTextures.MAX_BYTES + 1), ImportedTextures.Preset.HELMET);
                } catch (Exception e) { throw new CompletionException(e); }
            }).thenApplyAsync(bytes -> {
                try {
                    var pixels = NativeImage.read(bytes);
                    DynamicTexture texture;
                    try { texture = new DynamicTexture(() -> "Paint Brush helmet skin", pixels); }
                    catch (RuntimeException e) { pixels.close(); throw e; }
                    try { client.getTextureManager().register(ImportedTextures.texture(key), texture); }
                    catch (RuntimeException e) { texture.close(); throw e; }
                    return key;
                } catch (Exception e) { throw new CompletionException(e); }
            }, client);
        });
    }
    static boolean available(Identifier id) {
        var future = load(id);
        return future.isDone() && !future.isCompletedExceptionally();
    }
    static CompletableFuture<Identifier> refresh(Identifier id) {
        var previous = loaded.get(id);
        if (previous != null && previous.isCompletedExceptionally()) loaded.remove(id);
        return load(id);
    }
}

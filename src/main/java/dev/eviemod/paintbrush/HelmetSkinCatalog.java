package dev.eviemod.paintbrush;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Bundled NEU catalogue; downloads only the chosen texture from Minecraft's texture service. */
final class HelmetSkinCatalog {
    record Skin(String id, String name, String texture, List<Skin> variants, String legacyHash) {
        Skin(String id, String name, String texture) { this(id, name, texture, List.of(), null); }
        net.minecraft.resources.Identifier modelId() {
            try {
                var digest = java.security.MessageDigest.getInstance("SHA-256").digest(("catalog:" + id).getBytes(StandardCharsets.UTF_8));
                return net.minecraft.resources.Identifier.fromNamespaceAndPath(ImportedTextures.NAMESPACE, "helmet/" + java.util.HexFormat.of().formatHex(digest));
            } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        }
        boolean matches(net.minecraft.resources.Identifier model) {
            return modelId().equals(model) || (legacyHash != null && model != null && model.equals(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(ImportedTextures.NAMESPACE, "helmet/" + legacyHash)));
        }
        Skin variant(String name) { return variants.stream().filter(v -> v.name().equalsIgnoreCase(name)).findFirst().orElse(null); }
        Skin {
            if (id == null || name == null || !texture.matches("[0-9a-f]{32,64}")) throw new IllegalArgumentException("Invalid helmet skin");
        }
        byte[] download() throws IOException {
            var connection = (HttpURLConnection) URI.create("https://textures.minecraft.net/texture/" + texture).toURL().openConnection();
            connection.setConnectTimeout(10_000); connection.setReadTimeout(15_000);
            connection.setInstanceFollowRedirects(false);
            try {
                if (connection.getResponseCode() != 200) throw new IOException("The skin could not be downloaded. Try again.");
                try (var input = connection.getInputStream()) {
                    byte[] bytes = input.readNBytes(ImportedTextures.MAX_BYTES + 1);
                    if (bytes.length > ImportedTextures.MAX_BYTES) throw new IOException("The skin image is too large.");
                    return bytes;
                }
            } finally { connection.disconnect(); }
        }
    }
    private static final List<Skin> SKINS = load();
    static List<String> names() { return SKINS.stream().map(Skin::name).toList(); }
    static Skin find(String value) {
        return SKINS.stream().filter(s -> s.name().equalsIgnoreCase(value) || s.id().equalsIgnoreCase(value)).findFirst().orElse(null);
    }
    record Choice(Skin skin, Skin variant) {}
    static Choice choice(net.minecraft.resources.Identifier model) {
        if (model == null) return null;
        for (var skin : SKINS) {
            if (skin.matches(model)) return new Choice(skin, null);
            for (var variant : skin.variants()) if (variant.matches(model)) return new Choice(skin, variant);
        }
        return null;
    }
    private static Skin read(com.google.gson.JsonObject item) {
        var variants = new java.util.ArrayList<Skin>();
        if (item.has("variants")) for (var variant : item.getAsJsonArray("variants")) variants.add(read(variant.getAsJsonObject()));
        return new Skin(item.get("id").getAsString(), item.get("name").getAsString(), item.get("texture").getAsString(),
            List.copyOf(variants), item.has("legacyHash") ? item.get("legacyHash").getAsString() : null);
    }
    private static List<Skin> load() {
        try (var stream = HelmetSkinCatalog.class.getResourceAsStream("/assets/eviemod/helmet-skins.json")) {
            if (stream == null) throw new IOException("Missing helmet skin catalog");
            var entries = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("skins");
            var result = new java.util.ArrayList<Skin>();
            for (var entry : entries) {
                var item = entry.getAsJsonObject();
                result.add(read(item));
            }
            return List.copyOf(result);
        } catch (IOException | RuntimeException e) { throw new IllegalStateException("Could not load helmet skin catalog", e); }
    }
}

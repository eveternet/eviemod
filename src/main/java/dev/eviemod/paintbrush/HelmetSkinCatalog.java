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
    record Skin(String id, String name, String texture) {
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
    private static List<Skin> load() {
        try (var stream = HelmetSkinCatalog.class.getResourceAsStream("/assets/eviemod/helmet-skins.json")) {
            if (stream == null) throw new IOException("Missing helmet skin catalog");
            var entries = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("skins");
            var result = new java.util.ArrayList<Skin>();
            for (var entry : entries) {
                var item = entry.getAsJsonObject();
                result.add(new Skin(item.get("id").getAsString(), item.get("name").getAsString(), item.get("texture").getAsString()));
            }
            return List.copyOf(result);
        } catch (IOException | RuntimeException e) { throw new IllegalStateException("Could not load helmet skin catalog", e); }
    }
}

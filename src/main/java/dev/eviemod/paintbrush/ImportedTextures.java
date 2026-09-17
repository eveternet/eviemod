package dev.eviemod.paintbrush;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import net.minecraft.resources.Identifier;

/** Validates local images and writes self-contained, content-addressed resource-pack assets. */
final class ImportedTextures {
    static final String NAMESPACE = "eviemod_imported";
    static final String PACK_FOLDER = "eviemod-paintbrush";
    static final String PACK_ID = "file/" + PACK_FOLDER;
    static final int MAX_BYTES = 4 * 1024 * 1024;
    enum Preset {
        BOW("Bow", "minecraft:item/bow"), SWORD("Sword", "minecraft:item/handheld"),
        HANDHELD("Handheld", "minecraft:item/handheld"), HELMET("Helmet skin", null);
        final String label, parent;
        Preset(String label, String parent) { this.label = label; this.parent = parent; }
    }
    private final Path root;
    ImportedTextures(Path resourcePacks) { root = resourcePacks.resolve(PACK_FOLDER); }

    Identifier importFile(Path file, Preset preset) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) throw new IOException("Choose a PNG file under 4 MB.");
        try (var stream = Files.newInputStream(file)) {
            Path sidecar = file.resolveSibling(file.getFileName() + ".mcmeta");
            String metadata = null;
            if (Files.exists(sidecar)) {
                if (!Files.isRegularFile(sidecar) || Files.size(sidecar) > 65536) throw new IOException("Animation metadata must be under 64 KB.");
                metadata = Files.readString(sidecar);
            }
            return importPng(stream.readNBytes(MAX_BYTES + 1), preset, metadata);
        }
    }

    synchronized Identifier importPng(byte[] bytes, Preset preset) throws IOException {
        return importPng(bytes, preset, null);
    }
    synchronized Identifier importPng(byte[] bytes, Preset preset, String metadata) throws IOException {
        byte[] png = validatedPng(bytes, preset);
        var dimensions = ImageIO.read(new ByteArrayInputStream(png));
        if (metadata != null && preset == Preset.HELMET) throw new IOException("Helmet skins need a single-frame skin PNG.");
        String animation = TextureAnimation.validate(metadata, dimensions.getWidth(), dimensions.getHeight());
        String hash;
        try {
            var digest = MessageDigest.getInstance("SHA-256"); digest.update(png);
            if (animation != null) { digest.update((byte) 0); digest.update(animation.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            hash = HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        var id = Identifier.fromNamespaceAndPath(NAMESPACE, preset.name().toLowerCase(java.util.Locale.ROOT) + "/" + hash);
        Path assets = root.resolve("assets").resolve(NAMESPACE);
        // Each file is atomic; the item definition is published last, after all dependencies exist.
        write(assets.resolve(texture(id).getPath()), png);
        if (animation != null) write(assets.resolve(texture(id).getPath() + ".mcmeta"), animation.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (preset != Preset.HELMET) write(assets.resolve("models/item/" + id.getPath() + ".json"), modelJson(id, preset).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        write(root.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"Paint Brush imported textures\",\"min_format\":84,\"max_format\":84}}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        write(assets.resolve("items/" + id.getPath() + ".json"), itemJson(id, preset).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return id;
    }

    static byte[] validatedPng(byte[] bytes, Preset preset) throws IOException {
        if (bytes.length > MAX_BYTES || bytes.length < 24 || bytes[0] != (byte) 0x89
            || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') throw new IOException("Choose a PNG file under 4 MB.");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("This PNG could not be read.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 1024 || height > 1024)
                    throw new IOException("Textures must be at most 1024 × 1024 pixels.");
                if (preset == Preset.HELMET && (width != 64 || (height != 64 && height != 32)))
                    throw new IOException("Helmet skins need a 64 × 64 or 64 × 32 Minecraft skin PNG.");
                BufferedImage image = reader.read(0);
                if (preset == Preset.HELMET && height == 32) {
                    var expanded = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                    var graphics = expanded.createGraphics();
                    try { graphics.drawImage(image, 0, 0, null); } finally { graphics.dispose(); }
                    image = expanded;
                }
                var output = new ByteArrayOutputStream();
                ImageIO.write(image, "PNG", output); // Strip unrelated metadata and validate the entire image.
                return output.toByteArray();
            } finally { reader.dispose(); }
        } catch (RuntimeException e) { throw new IOException("This PNG could not be read.", e); }
    }

    static boolean isImported(Identifier id) { return id != null && id.getNamespace().equals(NAMESPACE); }
    static boolean isHelmet(Identifier id) { return isImported(id) && id.getPath().matches("helmet/[0-9a-f]{64}"); }
    static Identifier texture(Identifier id) { return id.withPath("textures/" + (isHelmet(id) ? "entity/" : "item/") + id.getPath() + ".png"); }
    static String modelJson(Identifier id, Preset preset) {
        var root = new JsonObject(); root.addProperty("parent", preset.parent);
        var textures = new JsonObject(); textures.addProperty("layer0", id.withPath("item/" + id.getPath()).toString()); root.add("textures", textures);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }
    static String itemJson(Identifier id, Preset preset) {
        var root = new JsonObject(); var model = new JsonObject(); root.add("model", model);
        if (preset == Preset.HELMET) {
            model.addProperty("type", "minecraft:special"); model.addProperty("base", "minecraft:item/template_skull");
            var special = new JsonObject(); special.addProperty("type", "minecraft:head");
            special.addProperty("kind", "player"); special.addProperty("texture", id.toString()); model.add("model", special);
            // Same special-model transform as Minecraft's player_head item in 26.1.2.
            model.add("transformation", com.google.gson.JsonParser.parseString("{\"left_rotation\":[1,0,0,0],\"right_rotation\":[0,0,0,1],\"scale\":[1,1,1],\"translation\":[0.5,0,0.5]}"));
        } else {
            model.addProperty("type", "minecraft:model"); model.addProperty("model", id.withPath("item/" + id.getPath()).toString());
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }
    private static void write(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".paintbrush-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}

package dev.eviemod.paintbrush;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ImportedTexturesTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() {
        ModelOverridesTest.bootstrap(); ItemModels.bootstrap(); SpecialModelRenderers.bootstrap();
    }
    private static byte[] png(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffff00ff);
        var output = new ByteArrayOutputStream(); ImageIO.write(image, "PNG", output); return output.toByteArray();
    }
    @Test void generatedDefinitionsDecodeWithMinecraftAndPreservePresetTransforms() throws Exception {
        var imports = new ImportedTextures(directory);
        for (var preset : ImportedTextures.Preset.values()) {
            var id = imports.importPng(png(64, 64), preset);
            Path assets = directory.resolve(ImportedTextures.PACK_FOLDER).resolve("assets/" + ImportedTextures.NAMESPACE);
            var json = JsonParser.parseString(Files.readString(assets.resolve("items/" + id.getPath() + ".json")));
            assertTrue(ClientItem.CODEC.parse(JsonOps.INSTANCE, json).isSuccess(), json.toString());
            assertTrue(Files.isRegularFile(assets.resolve(ImportedTextures.texture(id).getPath())));
            if (preset != ImportedTextures.Preset.HELMET) {
                var model = JsonParser.parseString(Files.readString(assets.resolve("models/item/" + id.getPath() + ".json"))).getAsJsonObject();
                assertEquals(preset.parent, model.get("parent").getAsString());
                assertEquals(id.withPath("item/" + id.getPath()).toString(), model.getAsJsonObject("textures").get("layer0").getAsString());
            }
            assertEquals(id, imports.importPng(png(64, 64), preset));
        }
        assertTrue(Files.isRegularFile(directory.resolve(ImportedTextures.PACK_FOLDER).resolve("pack.mcmeta")));
    }
    @Test void legacySkinUvCanvasIsExpandedAndAlphaPreserved() throws Exception {
        var bytes = ImportedTextures.validatedPng(png(64, 32), ImportedTextures.Preset.HELMET);
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertEquals(64, image.getHeight()); assertEquals(0xffff00ff, image.getRGB(0, 0));
        assertEquals(0, image.getRGB(0, 40));
    }
    @Test void rejectsMalformedOversizedAndWrongShapeBeforePublishingAnything() throws Exception {
        var imports = new ImportedTextures(directory);
        for (byte[] invalid : new byte[][] {new byte[0], new byte[ImportedTextures.MAX_BYTES + 1], png(1025, 1), "not png".getBytes()})
            assertThrows(java.io.IOException.class, () -> imports.importPng(invalid, ImportedTextures.Preset.BOW));
        assertThrows(java.io.IOException.class, () -> imports.importPng(png(32, 32), ImportedTextures.Preset.HELMET));
        assertFalse(Files.exists(directory.resolve(ImportedTextures.PACK_FOLDER)));
    }
    @Test void localFileIsCopiedAndSurvivesSourceDeletion() throws Exception {
        var source = directory.resolve("my texture.png"); Files.write(source, png(16, 16));
        var id = new ImportedTextures(directory).importFile(source, ImportedTextures.Preset.SWORD);
        Files.delete(source);
        assertTrue(Files.isRegularFile(directory.resolve(ImportedTextures.PACK_FOLDER).resolve("assets/" + ImportedTextures.NAMESPACE + "/" + ImportedTextures.texture(id).getPath())));
    }
    @Test void helmetProfileUsesPackTextureAndDoesNotTouchTheOriginalStack() {
        var id = Identifier.parse(ImportedTextures.NAMESPACE + ":helmet/" + "a".repeat(64));
        var stack = Items.GOLDEN_HELMET.getDefaultInstance(); var before = stack.copy();
        assertTrue(HelmetSkins.supports(stack)); assertTrue(HelmetSkins.supports(Items.PLAYER_HEAD.getDefaultInstance()));
        assertFalse(HelmetSkins.supports(Items.GOLDEN_CHESTPLATE.getDefaultInstance()));
        assertFalse(HelmetSkins.supports(Items.DIAMOND_SWORD.getDefaultInstance()));
        var profile = HelmetSkins.profile(id);
        assertEquals(ImportedTextures.texture(id), profile.skinPatch().body().orElseThrow().texturePath());
        assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameComponents(before, stack));
        assertNull(stack.get(DataComponents.PROFILE));
        assertFalse(ImportedTextures.isHelmet(Identifier.parse("other:helmet/" + "a".repeat(64))));
        assertFalse(ImportedTextures.isHelmet(Identifier.parse(ImportedTextures.NAMESPACE + ":helmet/invalid")));
    }
    @Test void bundledCatalogContainsUniqueNamedSkinsAndSafeTextureHashes() {
        var names = HelmetSkinCatalog.names(); assertTrue(names.size() > 100);
        assertEquals(names.size(), names.stream().distinct().count());
        assertNotNull(HelmetSkinCatalog.find("True Warden Skin"));
        for (String name : names) assertTrue(HelmetSkinCatalog.find(name).texture().matches("[0-9a-f]{32,64}"));
        assertNull(HelmetSkinCatalog.find("made up"));
        assertThrows(IllegalArgumentException.class, () -> new HelmetSkinCatalog.Skin("id", "name", "../other"));
    }
}

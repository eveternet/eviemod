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
    @Test void explicitAnimationFileSurvivesImportAndChangesIdentity() throws Exception {
        var source = directory.resolve("hyperion.png"); Files.write(source, png(16, 304));
        var sidecar = directory.resolve("hyperion.png.mcmeta");
        Files.writeString(sidecar, "{\"animation\":{\"frametime\":2,\"interpolate\":true}}");
        var imports = new ImportedTextures(directory);
        var animated = imports.importFile(source, ImportedTextures.Preset.SWORD, sidecar);
        var copied = directory.resolve(ImportedTextures.PACK_FOLDER).resolve("assets/" + ImportedTextures.NAMESPACE)
            .resolve(ImportedTextures.texture(animated).getPath() + ".mcmeta");
        var metadata = JsonParser.parseString(Files.readString(copied)).getAsJsonObject().getAsJsonObject("animation");
        assertEquals(2, metadata.get("frametime").getAsInt()); assertTrue(metadata.get("interpolate").getAsBoolean());
        // Adjacent metadata must never be read without an explicit selection.
        assertNotEquals(animated, imports.importFile(source, ImportedTextures.Preset.SWORD));
        assertEquals(animated, imports.importPng(png(16, 304), ImportedTextures.Preset.SWORD,
            "{\"animation\":{\"interpolate\":true,\"frametime\":2}}"));
    }
    @Test void invalidAnimationNeverPublishesAssets() throws Exception {
        var imports = new ImportedTextures(directory);
        for (String metadata : new String[] {"broken", "{}", "{\"animation\":[]}",
                "{\"animation\":{\"frametime\":0}}", "{\"animation\":{\"width\":7}}",
                "{\"animation\":{\"frames\":[19]}}", "{\"animation\":{\"frames\":[]}}",
                "{\"animation\":{\"frames\":[{\"index\":0,\"time\":0}]}}"})
            assertThrows(java.io.IOException.class, () -> imports.importPng(png(16, 304), ImportedTextures.Preset.SWORD, metadata), metadata);
        assertFalse(Files.exists(directory.resolve(ImportedTextures.PACK_FOLDER)));
        assertNotNull(TextureAnimation.validate("{\"animation\":{\"frames\":[0,{\"index\":18,\"time\":3}]}}", 16, 304));
    }
    @Test void customTexturesExcludeArmorAndSkinPreviewUsesDetachedStack() {
        for (var item : new net.minecraft.world.item.Item[] {Items.GOLDEN_HELMET, Items.LEATHER_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.IRON_BOOTS, Items.PLAYER_HEAD})
            assertFalse(ItemAppearance.supportsCustomTexture(item.getDefaultInstance()));
        assertTrue(ItemAppearance.supportsCustomTexture(Items.DIAMOND_SWORD.getDefaultInstance()));
        assertTrue(ItemAppearance.supportsCustomTexture(Items.BOW.getDefaultInstance()));
        var original = Items.GOLDEN_HELMET.getDefaultInstance(); var before = original.copy();
        var copy = HelmetSkins.preview(original, Identifier.parse(ImportedTextures.NAMESPACE + ":helmet/" + "a".repeat(64)));
        assertEquals(Identifier.withDefaultNamespace("player_head"), copy.get(DataComponents.ITEM_MODEL));
        assertNotNull(copy.get(DataComponents.PROFILE));
        assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameComponents(before, original));
    }
    @Test void imageRatioPromptsOnlyForPossibleStripsAndAcceptsSeparateMetadata() throws Exception {
        var source = directory.resolve("strip.png"); Files.write(source, png(16, 304));
        assertTrue(ImportedTextures.prepare(source, ImportedTextures.Preset.SWORD).likelyAnimated());
        var metadata = directory.resolve("separately chosen.mcmeta"); Files.writeString(metadata, "{\"animation\":{\"frametime\":2}}");
        assertNotNull(new ImportedTextures(directory).importFile(source, ImportedTextures.Preset.SWORD, metadata));
        Files.write(source, png(16, 16));
        assertFalse(ImportedTextures.prepare(source, ImportedTextures.Preset.SWORD).likelyAnimated());
        Files.write(source, png(17, 30));
        assertFalse(ImportedTextures.prepare(source, ImportedTextures.Preset.SWORD).likelyAnimated());
    }
    @Test void catalogVariantsAndLegacyLabelsResolveWithoutDownloading() {
        var knight = HelmetSkinCatalog.find("NECRON_DIAMOND_KNIGHT");
        assertEquals(15, knight.variants().size());
        var red = knight.variant("Rose"); assertNotNull(red);
        var choice = HelmetSkinCatalog.choice(red.modelId());
        assertEquals(knight, choice.skin()); assertEquals(red, choice.variant());
        var warden = HelmetSkinCatalog.find("True Warden Skin");
        var legacy = Identifier.parse(ImportedTextures.NAMESPACE + ":helmet/" + warden.legacyHash());
        assertEquals(warden, HelmetSkinCatalog.choice(legacy).skin());
        var defaultWarden = HelmetSkinCatalog.find("Warden Helmet (Default)");
        assertNotNull(defaultWarden);
        assertEquals(defaultWarden, HelmetSkinCatalog.choice(defaultWarden.modelId()).skin());
        assertNotEquals(warden.modelId(), defaultWarden.modelId());
        assertNotNull(HelmetSkinCatalog.find("Necron's Helmet (Default)"));
        for (String name : HelmetSkinCatalog.names()) {
            var skin = HelmetSkinCatalog.find(name);
            assertEquals(skin.variants().size(), skin.variants().stream().map(HelmetSkinCatalog.Skin::name).distinct().count());
            for (var variant : skin.variants()) assertEquals(variant, HelmetSkinCatalog.choice(variant.modelId()).variant());
        }
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

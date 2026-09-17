package dev.eviemod.paintbrush;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Local, account-free import fixture, excluded from the release jar. */
final class TextureSmokeTest {
    private static final UUID KEY = UUID.fromString("a7379831-f3ec-4262-a9b6-710000000001");
    private int stage, ticks;
    private ItemStack helmet, before, held;
    private Object modelBeforeSkin;
    private java.util.concurrent.CompletableFuture<Void> reload;
    private Identifier texture, skin, oldModel, oldSkin;
    private String oldColor;
    private boolean oldTexturesEnabled, oldSkinsEnabled;
    private Path image, animated;
    private PaintBrushScreen editor;

    void start() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try { tick(client); }
            catch (Exception e) { throw new AssertionError("Texture fixture failed at stage " + stage, e); }
        });
    }
    private void tick(Minecraft client) throws Exception {
        if (stage == 0) {
            if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null) return;
            net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
                .build(net.minecraft.data.registries.VanillaRegistries.createLookup())
                .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
            oldColor = PaintBrushClient.colors().getValue(KEY); oldModel = PaintBrushClient.models().get(KEY); oldSkin = HelmetSkins.store().get(KEY);
            oldTexturesEnabled = EviemodSettings.STORE.values().customTextures;
            oldSkinsEnabled = EviemodSettings.STORE.values().helmetSkins;
            PaintBrushClient.models().set(KEY, null); HelmetSkins.store().set(KEY, null);
            EviemodSettings.STORE.values().customTextures = false; EviemodSettings.STORE.values().helmetSkins = false;
            helmet = Items.GOLDEN_HELMET.getDefaultInstance();
            var data = new CompoundTag(); data.putString("uuid", KEY.toString()); data.putString("id", "TEXTURE_FIXTURE_HELMET");
            helmet.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            helmet.set(DataComponents.CUSTOM_NAME, Component.literal("Imported helmet fixture")); before = helmet.copy();
            image = client.gameDirectory.toPath().resolve("texture-fixture.png");
            var png = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) png.setRGB(x, y, (x / 4 + y / 4) % 2 == 0 ? 0xff00ccbb : 0xff6633cc);
            ImageIO.write(png, "PNG", image.toFile());
            held = Items.DIAMOND_SWORD.getDefaultInstance(); held.set(DataComponents.CUSTOM_DATA, helmet.get(DataComponents.CUSTOM_DATA));
            animated = client.gameDirectory.toPath().resolve("hyperion-fixture.png");
            if (!Files.exists(animated)) {
                var strip = new BufferedImage(16, 304, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 304; y++) for (int x = 0; x < 16; x++) strip.setRGB(x, y, y / 16 % 2 == 0 ? 0xff00ccbb : 0xff6633cc);
                ImageIO.write(strip, "PNG", animated.toFile());
                Files.writeString(Path.of(animated + ".mcmeta"), "{\"animation\":{\"frametime\":2,\"interpolate\":true}}");
            }
            editor = new PaintBrushScreen(List.of(held)); client.setScreen(editor);
            next(); return;
        }
        if (++ticks > 1200) throw new AssertionError("Timed out at texture fixture stage " + stage);
        if (client.getOverlay() != null || ticks < 15) return;
        switch (stage) {
            case 1 -> {
                var field = modelField(editor, "Item model");
                clickAt(client, field.getX() + 4, field.getY() + 4);
                check(!field.isMouseOver(field.getX() + 4, field.getBottom() + 4), "Empty field must not show suggestions");
                field.setValue("minecraft:");
                check(field.isMouseOver(field.getX() + 4, field.getBottom() + 4), "Typed query must show suggestions");
                clickAt(client, field.getX() - 8, field.getY());
                check(!field.isFocused() && !field.isMouseOver(field.getX() + 4, field.getBottom() + 4), "Outside click must dismiss suggestions");
                field.setValue(""); click(client, "Bow"); editor.onFilesDrop(List.of(animated)); next();
            }
            case 2 -> {
                if (!hasButton(client, "Choose .mcmeta")) return;
                check(PaintBrushClient.models().get(KEY) == null, "Adjacent metadata must not be imported automatically");
                screenshot(client, "paintbrush-animation-prompt.png");
                // A selected metadata file can have any name and live separately from the PNG.
                var metadata = client.gameDirectory.toPath().resolve("chosen-animation.mcmeta");
                Files.copy(Path.of(animated + ".mcmeta"), metadata, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                editor.onFilesDrop(List.of(metadata)); next();
            }
            case 3 -> {
                texture = PaintBrushClient.models().get(KEY); if (texture == null) return;
                check(texture.equals(PaintBrushClient.resolve(held, held.get(DataComponents.ITEM_MODEL))), "Imported model must resolve");
                var itemState = new net.minecraft.client.renderer.item.ItemStackRenderState();
                client.getItemModelResolver().updateForTopItem(itemState, held, net.minecraft.world.item.ItemDisplayContext.GUI, null, null, 0);
                var contents = itemState.pickParticleMaterial(net.minecraft.util.RandomSource.create()).sprite().contents();
                check(contents.isAnimated() && contents.width() == 16 && contents.height() == 16 && contents.getUniqueFrames().size() == 19,
                    "All 19 animation frames must load");
                check(editor.children().stream().noneMatch(c -> c instanceof ModelField), "Custom model must hide generated ID");
                screenshot(client, "paintbrush-texture.png"); next();
            }
            case 4 -> {
                var chest = Items.GOLDEN_CHESTPLATE.getDefaultInstance(); chest.set(DataComponents.CUSTOM_DATA, helmet.get(DataComponents.CUSTOM_DATA));
                var original = chest.copy();
                // A saved uploaded texture is ignored on armor, but built-in replacements work.
                check(chest.get(DataComponents.ITEM_MODEL).equals(PaintBrushClient.resolve(chest, chest.get(DataComponents.ITEM_MODEL))), "Imported armor texture must be ignored");
                PaintBrushClient.models().set(KEY, null);
                editor = new PaintBrushScreen(List.of(chest)); client.setScreen(editor);
                check(!hasButton(client, "Import PNG & apply"), "Armor must not offer uploads");
                modelField(editor, "Item model").setValue("minecraft:leather_chestplate"); click(client, "Apply");
                check(Identifier.withDefaultNamespace("leather_chestplate").equals(PaintBrushClient.resolve(chest, chest.get(DataComponents.ITEM_MODEL))), "Gold must resolve as leather");
                check(ItemAppearance.equipmentAsset(chest, PaintBrushClient.models().get(KEY), chest.get(DataComponents.EQUIPPABLE).assetId().orElseThrow())
                    .equals(Items.LEATHER_CHESTPLATE.getDefaultInstance().get(DataComponents.EQUIPPABLE).assetId().orElseThrow()), "Worn armor asset must be leather");
                click(client, "Dye"); modelField(editor, "Dye preset or hex").setValue("#FF88CC"); click(client, "Apply");
                check(PaintBrushClient.resolveColor(chest, -1) == 0xffff88cc, "Replacement leather must accept dye");
                check(ItemStack.isSameItemSameComponents(original, chest), "Armor changes must be rendering-only");
                Class.forName("net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer");
                next();
            }
            case 5 -> {
                screenshot(client, "paintbrush-leather-dye.png");
                PaintBrushClient.models().set(KEY, texture); PaintBrushClient.colors().setValue(KEY, oldColor);
                modelBeforeSkin = client.getModelManager().getItemModel(Identifier.withDefaultNamespace("stone"));
                editor = new PaintBrushScreen(List.of(helmet)); client.setScreen(editor); click(client, "Skin");
                check(!hasButton(client, "Import skin PNG"), "Skin uploads must be removed");
                editor.onFilesDrop(List.of(image)); check(HelmetSkins.store().get(KEY) == null, "Skin file drops must be ignored");
                modelField(editor, "Helmet skin").setValue("Knight Skin (Diamond Necron Head)"); next();
            }
            case 6 -> {
                modelField(editor, "Skin variant").setValue("Rose"); click(client, "Apply"); next();
            }
            case 7 -> {
                skin = HelmetSkins.store().get(KEY); if (skin == null) return;
                check(HelmetSkinCatalog.find("NECRON_DIAMOND_KNIGHT").variant("Rose").modelId().equals(skin), "Chosen colour variant must persist");
                check(modelBeforeSkin == client.getModelManager().getItemModel(Identifier.withDefaultNamespace("stone")), "Skin must not reload resource packs");
                check(ItemStack.isSameItemSameComponents(before, helmet), "Skin must not change actual item data");
                editor = new PaintBrushScreen(List.of(helmet)); client.setScreen(editor); click(client, "Skin");
                check(modelField(editor, "Helmet skin").getValue().equals("Knight Skin (Diamond Necron Head)"), "Saved skin name must appear");
                check(modelField(editor, "Skin variant").getValue().equals("Rose"), "Saved variant must appear");
                next();
            }
            case 8 -> { screenshot(client, "paintbrush-variant-selection.png"); client.setScreen(new Preview(helmet)); next(); }
            case 9 -> { screenshot(client, "paintbrush-catalog-skin.png"); reload = client.reloadResourcePacks(); next(); }
            case 10 -> {
                if (!reload.isDone()) return; reload.join();
                check(client.getTextureManager().getTexture(ImportedTextures.texture(skin)) instanceof net.minecraft.client.renderer.texture.DynamicTexture,
                    "Skin texture must survive resource reload");
                check(skin.equals(HelmetSkins.resolve(helmet)), "Skin must still resolve after resource reload");
                var other = helmet.copy(); other.remove(DataComponents.CUSTOM_DATA);
                check(HelmetSkins.resolve(other) == null, "Skin must not leak to unknown identity");
                HelmetSkins.load(); PaintBrushClient.models().load();
                client.setScreen(editor); click(client, "Reset");
                check(HelmetSkins.store().get(KEY) == null, "Skin reset must clear skin");
                check(texture.equals(PaintBrushClient.models().get(KEY)), "Skin reset preserves model");
                editor = new PaintBrushScreen(List.of(held)); client.setScreen(editor); click(client, "Reset");
                check(PaintBrushClient.models().get(KEY) == null, "Model reset must clear texture");
                PaintBrushClient.models().set(KEY, texture);
                Files.writeString(animated, "invalid PNG"); editor.onFilesDrop(List.of(animated)); next();
            }
            case 11 -> {
                check(texture.equals(PaintBrushClient.models().get(KEY)), "Invalid import preserves previous choice");
                PaintBrushClient.models().set(KEY, oldModel); HelmetSkins.store().set(KEY, oldSkin); PaintBrushClient.colors().setValue(KEY, oldColor);
                EviemodSettings.STORE.values().helmetSkins = oldSkinsEnabled; EviemodSettings.STORE.values().customTextures = oldTexturesEnabled;
                Files.deleteIfExists(image); Files.deleteIfExists(animated); Files.deleteIfExists(Path.of(animated + ".mcmeta"));
                Files.deleteIfExists(client.gameDirectory.toPath().resolve("chosen-animation.mcmeta"));
                org.slf4j.LoggerFactory.getLogger("eviemod-fixture").info("Explicit animation metadata, autocomplete dismissal, armor model/dye, catalog variants, persisted labels and reload checks passed");
                client.stop(); next();
            }
        }
    }
    private static ModelField modelField(PaintBrushScreen screen, String label) {
        return screen.children().stream().filter(c -> c instanceof ModelField f && f.getMessage().getString().equals(label))
            .map(c -> (ModelField)c).findFirst().orElseThrow();
    }
    private static boolean hasButton(Minecraft client, String label) {
        return client.screen.children().stream().anyMatch(c -> c instanceof AbstractWidget w && w.getMessage().getString().equals(label));
    }
    private static void clickAt(Minecraft client, int x, int y) {
        var viewport = EditorViewport.fit(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), client.getWindow().getGuiScale());
        var event = new net.minecraft.client.input.MouseButtonEvent(x * viewport.scale(), y * viewport.scale(), new net.minecraft.client.input.MouseButtonInfo(0, 0));
        client.screen.mouseClicked(event, false); client.screen.mouseReleased(event);
    }
    private void next() { stage++; ticks = 0; }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void screenshot(Minecraft client, String name) {
        net.minecraft.client.Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), 1,
            message -> org.slf4j.LoggerFactory.getLogger("eviemod-fixture").info(message.getString()));
    }
    private static void click(Minecraft client, String label) {
        var widget = client.screen.children().stream().filter(c -> c instanceof AbstractWidget w && w.getMessage().getString().equals(label))
            .map(c -> (AbstractWidget) c).findFirst().orElseThrow();
        check(widget.active, label + " should be enabled");
        var viewport = EditorViewport.fit(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), client.getWindow().getGuiScale());
        var event = new net.minecraft.client.input.MouseButtonEvent((widget.getX() + 3) * viewport.scale(), (widget.getY() + 3) * viewport.scale(),
            new net.minecraft.client.input.MouseButtonInfo(0, 0));
        client.screen.mouseClicked(event, false); client.screen.mouseReleased(event);
    }
    private static final class Preview extends CompactScreen {
        private final ItemStack helmet;
        Preview(ItemStack helmet) { super(Component.literal("Worn skin fixture")); this.helmet = helmet; }
        @Override protected void init() { updateViewport(); }
        @Override protected void renderContents(net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int my, float delta) {
            g.fill(0, 0, viewWidth, viewHeight, 0xff20232e);
            g.text(font, "Imported helmet skin: item and worn appearance", 15, 15, -1);
            g.pose().pushMatrix(); g.pose().translate(30, 50); g.pose().scale(4, 4); g.item(helmet, 0, 0); g.pose().popMatrix();
            var state = new net.minecraft.client.renderer.entity.state.ArmorStandRenderState();
            state.entityType = EntityType.ARMOR_STAND; state.scale = 1; state.ageScale = 1;
            state.headEquipment = helmet; state.showArms = true;
            HelmetSkins.applyWorn(helmet, state);
            check(state.wornHeadType == net.minecraft.world.level.block.SkullBlock.Types.PLAYER, "Worn renderer must use a player skull");
            check(state.wornHeadProfile.skinPatch().body().orElseThrow().texturePath().equals(ImportedTextures.texture(HelmetSkins.resolve(helmet))), "Worn profile must point at imported texture");
            g.entity(state, 75, new org.joml.Vector3f(0, 1, 0), new org.joml.Quaternionf().rotationXYZ(0, 0.35f, (float) Math.PI),
                null, 140, 40, 340, 230);
        }
    }
}

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
            oldModel = PaintBrushClient.models().get(KEY); oldSkin = HelmetSkins.store().get(KEY);
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
            case 1 -> { click(client, "Bow"); editor.onFilesDrop(List.of(animated, Path.of(animated + ".mcmeta"))); next(); }
            case 2 -> {
                texture = PaintBrushClient.models().get(KEY); if (texture == null) return;
                check(texture.getPath().startsWith("bow/"), "Bow preset must be saved");
                check(texture.equals(PaintBrushClient.resolve(held, held.get(DataComponents.ITEM_MODEL))), "Imported model must resolve");
                check(!(client.getModelManager().getItemModel(texture) instanceof net.minecraft.client.renderer.item.MissingItemModel), "Model must bake");
                var itemState = new net.minecraft.client.renderer.item.ItemStackRenderState();
                client.getItemModelResolver().updateForTopItem(itemState, held, net.minecraft.world.item.ItemDisplayContext.GUI, null, null, 0);
                var contents = itemState.pickParticleMaterial(net.minecraft.util.RandomSource.create()).sprite().contents();
                check(contents.isAnimated(), "Imported sprite must animate");
                check(contents.width() == 16 && contents.height() == 16, "Sprite must use one frame, not the whole strip");
                check(contents.getUniqueFrames().size() == 19, "All 19 animation frames must load");
                var sprite = contents.name();
                check(sprite.equals(texture.withPath("item/" + texture.getPath())), "Imported sprite must be stitched, not missing: " + sprite);
                check(editor.children().stream().noneMatch(c -> c instanceof ModelField), "Custom model must hide generated ID");
                check(editor.children().stream().noneMatch(c -> c instanceof AbstractWidget w && w.getMessage().getString().equals("Texture")), "Texture must be merged into Model");
                screenshot(client, "paintbrush-texture.png"); next();
            }
            case 3 -> {
                modelBeforeSkin = client.getModelManager().getItemModel(Identifier.withDefaultNamespace("stone"));
                editor = new PaintBrushScreen(List.of(helmet)); client.setScreen(editor);
                check(editor.children().stream().anyMatch(c -> c instanceof AbstractWidget w && w.getMessage().getString().equals("Model") && !w.active), "Armor model tab must be disabled");
                check(helmet.get(DataComponents.ITEM_MODEL).equals(PaintBrushClient.resolve(helmet, helmet.get(DataComponents.ITEM_MODEL))), "Stored armor model must be ignored");
                editor.onFilesDrop(List.of(image)); next();
            }
            case 4 -> {
                skin = HelmetSkins.store().get(KEY); if (skin == null) return;
                check(Identifier.withDefaultNamespace("player_head").equals(PaintBrushClient.resolve(helmet, helmet.get(DataComponents.ITEM_MODEL))), "Skin must use vanilla head model");
                check(modelBeforeSkin == client.getModelManager().getItemModel(Identifier.withDefaultNamespace("stone")), "Skin import must not reload resource packs");
                check(ItemStack.isSameItemSameComponents(before, helmet), "Import must not change actual item data");
                Class.forName("net.minecraft.client.renderer.entity.LivingEntityRenderer");
                Class.forName("net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer");
                screenshot(client, "paintbrush-skin.png");
                client.setScreen(new Preview(helmet)); next();
            }
            case 5 -> { screenshot(client, "paintbrush-worn-skin.png"); next(); }
            case 6 -> {
                client.setScreen(editor);
                var input = editor.children().stream().filter(c -> c instanceof ModelField).map(c -> (ModelField) c).findFirst().orElseThrow();
                input.setValue("True Warden Skin"); click(client, "Apply"); next();
            }
            case 7 -> {
                var downloaded = HelmetSkins.store().get(KEY); if (downloaded.equals(skin)) return;
                skin = downloaded;
                check(TextureImportClient.available(skin), "Catalog skin must download and load");
                var other = helmet.copy(); var otherData = other.get(DataComponents.CUSTOM_DATA).copyTag();
                otherData.putString("uuid", UUID.randomUUID().toString()); other.set(DataComponents.CUSTOM_DATA, CustomData.of(otherData));
                check(HelmetSkins.resolve(other) == null, "Skin must not leak to another UUID");
                other.remove(DataComponents.CUSTOM_DATA); check(HelmetSkins.resolve(other) == null, "Unknown identity must not receive skin");
                client.setScreen(new Preview(helmet)); next();
            }
            case 8 -> { screenshot(client, "paintbrush-catalog-skin.png"); next(); }
            case 9 -> {
                check(modelBeforeSkin == client.getModelManager().getItemModel(Identifier.withDefaultNamespace("stone")), "Catalog skin must not reload resource packs");
                reload = client.reloadResourcePacks(); next();
            }
            case 10 -> {
                if (!reload.isDone()) return;
                reload.join();
                check(client.getTextureManager().getTexture(ImportedTextures.texture(skin)) instanceof net.minecraft.client.renderer.texture.DynamicTexture,
                    "Direct skin texture must survive a later resource reload");
                check(skin.equals(HelmetSkins.resolve(helmet)), "Skin must still resolve after resource reload");
                screenshot(client, "paintbrush-skin-after-reload.png");
                next();
            }
            case 11 -> {
                HelmetSkins.load(); PaintBrushClient.models().load();
                check(skin.equals(HelmetSkins.resolve(helmet)), "Skin selection must reload");
                client.setScreen(editor); click(client, "Reset");
                check(HelmetSkins.store().get(KEY) == null, "Skin reset must clear only skin");
                check(texture.equals(PaintBrushClient.models().get(KEY)), "Skin reset preserves texture");
                editor = new PaintBrushScreen(List.of(held)); client.setScreen(editor); click(client, "Reset");
                check(PaintBrushClient.models().get(KEY) == null, "Texture reset must clear model");
                // An invalid image must not replace a previously applied choice.
                PaintBrushClient.models().set(KEY, texture);
                Files.writeString(animated, "invalid PNG"); editor.onFilesDrop(List.of(animated)); next();
            }
            case 12 -> {
                check(texture.equals(PaintBrushClient.models().get(KEY)), "Invalid import preserves previous choice");
                check(ItemStack.isSameItemSameComponents(before, helmet), "Fixture changed source item");
                PaintBrushClient.models().set(KEY, oldModel); HelmetSkins.store().set(KEY, oldSkin);
                EviemodSettings.STORE.values().helmetSkins = oldSkinsEnabled; EviemodSettings.STORE.values().customTextures = oldTexturesEnabled;
                Files.deleteIfExists(image); Files.deleteIfExists(animated); Files.deleteIfExists(Path.of(animated + ".mcmeta"));
                org.slf4j.LoggerFactory.getLogger("eviemod-fixture").info("Animated texture import, merged UI, armor exclusion, reload-free skins, reset and invalid-file checks passed");
                client.stop(); next();
            }
        }
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

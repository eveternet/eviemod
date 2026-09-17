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
    private ItemStack helmet, before;
    private Identifier texture, skin, oldModel, oldSkin;
    private boolean oldTexturesEnabled, oldSkinsEnabled;
    private Path image;
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
            EviemodSettings.STORE.values().customTextures = true; EviemodSettings.STORE.values().helmetSkins = true;
            helmet = Items.GOLDEN_HELMET.getDefaultInstance();
            var data = new CompoundTag(); data.putString("uuid", KEY.toString()); data.putString("id", "TEXTURE_FIXTURE_HELMET");
            helmet.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            helmet.set(DataComponents.CUSTOM_NAME, Component.literal("Imported helmet fixture")); before = helmet.copy();
            image = client.gameDirectory.toPath().resolve("texture-fixture.png");
            var png = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) png.setRGB(x, y, (x / 4 + y / 4) % 2 == 0 ? 0xff00ccbb : 0xff6633cc);
            ImageIO.write(png, "PNG", image.toFile());
            editor = new PaintBrushScreen(List.of(helmet)); client.setScreen(editor);
            next(); return;
        }
        if (++ticks > 1200) throw new AssertionError("Timed out at texture fixture stage " + stage);
        if (client.getOverlay() != null || ticks < 15) return;
        switch (stage) {
            case 1 -> { click(client, "Texture"); click(client, "Bow"); editor.onFilesDrop(List.of(image)); next(); }
            case 2 -> {
                texture = PaintBrushClient.models().get(KEY); if (texture == null) return;
                check(texture.getPath().startsWith("bow/"), "Bow preset must be saved");
                check(texture.equals(PaintBrushClient.resolve(helmet, helmet.get(DataComponents.ITEM_MODEL))), "Imported model must resolve");
                check(!(client.getModelManager().getItemModel(texture) instanceof net.minecraft.client.renderer.item.MissingItemModel), "Model must bake");
                var itemState = new net.minecraft.client.renderer.item.ItemStackRenderState();
                client.getItemModelResolver().updateForTopItem(itemState, helmet, net.minecraft.world.item.ItemDisplayContext.GUI, null, null, 0);
                var sprite = itemState.pickParticleMaterial(net.minecraft.util.RandomSource.create()).sprite().contents().name();
                check(sprite.equals(texture.withPath("item/" + texture.getPath())), "Imported sprite must be stitched, not missing: " + sprite);
                screenshot(client, "paintbrush-texture.png"); next();
            }
            case 3 -> { click(client, "Skin"); editor.onFilesDrop(List.of(image)); next(); }
            case 4 -> {
                skin = HelmetSkins.store().get(KEY); if (skin == null) return;
                check(skin.equals(PaintBrushClient.resolve(helmet, helmet.get(DataComponents.ITEM_MODEL))), "Skin must take precedence");
                check(ItemStack.isSameItemSameComponents(before, helmet), "Import must not change actual item data");
                Class.forName("net.minecraft.client.renderer.entity.LivingEntityRenderer");
                Class.forName("net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer");
                screenshot(client, "paintbrush-skin.png");
                client.setScreen(new Preview(helmet)); next();
            }
            case 5 -> { screenshot(client, "paintbrush-worn-skin.png"); next(); }
            case 6 -> {
                EviemodSettings.STORE.values().helmetSkins = false;
                check(HelmetSkins.resolve(helmet) == null, "Disabled skin must not resolve");
                check(texture.equals(PaintBrushClient.resolve(helmet, helmet.get(DataComponents.ITEM_MODEL))), "Disabling skin restores model");
                EviemodSettings.STORE.values().customTextures = false;
                check(helmet.get(DataComponents.ITEM_MODEL).equals(PaintBrushClient.resolve(helmet, helmet.get(DataComponents.ITEM_MODEL))), "Disabling textures restores original");
                EviemodSettings.STORE.values().helmetSkins = true; EviemodSettings.STORE.values().customTextures = true;
                HelmetSkins.load(); PaintBrushClient.models().load();
                check(skin.equals(HelmetSkins.resolve(helmet)), "Skin selection must reload");
                client.setScreen(editor); click(client, "Reset");
                check(HelmetSkins.store().get(KEY) == null, "Skin reset must clear only skin");
                check(texture.equals(PaintBrushClient.models().get(KEY)), "Skin reset preserves texture");
                click(client, "Texture"); click(client, "Reset texture");
                check(PaintBrushClient.models().get(KEY) == null, "Texture reset must clear model");
                // An invalid image must not replace a previously applied choice.
                PaintBrushClient.models().set(KEY, texture);
                Files.writeString(image, "invalid PNG"); editor.onFilesDrop(List.of(image)); next();
            }
            case 7 -> {
                check(texture.equals(PaintBrushClient.models().get(KEY)), "Invalid import preserves previous choice");
                check(ItemStack.isSameItemSameComponents(before, helmet), "Fixture changed source item");
                PaintBrushClient.models().set(KEY, oldModel); HelmetSkins.store().set(KEY, oldSkin);
                EviemodSettings.STORE.values().helmetSkins = oldSkinsEnabled; EviemodSettings.STORE.values().customTextures = oldTexturesEnabled;
                Files.deleteIfExists(image);
                org.slf4j.LoggerFactory.getLogger("eviemod-fixture").info("Texture import, skin render state, opt-out, reload, reset and invalid-file checks passed");
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

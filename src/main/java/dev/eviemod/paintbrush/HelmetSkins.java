package dev.eviemod.paintbrush;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/** Per-UUID helmet choices, separate from model overrides so resetting a skin restores the original appearance. */
public final class HelmetSkins {
    private static ModelOverrides overrides;
    private static boolean ready;
    private static final Map<Identifier, ResolvableProfile> profiles = new HashMap<>();
    private HelmetSkins() {}
    static void load() throws IOException {
        if (overrides == null) overrides = new ModelOverrides(ConfigMigration.path("eviemod-helmet-skins.json"));
        ready = false;
        overrides.load();
        ready = true;
    }
    static ModelOverrides store() { return overrides; }
    static void requireReady() throws IOException {
        if (!ready) throw new IOException("Fix eviemod-helmet-skins.json and run /paintbrush reload before saving skins.");
    }
    public static boolean supports(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var equipment = stack.get(DataComponents.EQUIPPABLE);
        return stack.is(Items.PLAYER_HEAD) || (equipment != null && equipment.slot() == EquipmentSlot.HEAD);
    }
    public static Identifier resolve(ItemStack stack) {
        if (overrides == null || !supports(stack)) return null;
        var id = overrides.get(SkyBlockUuid.read(stack));
        return ImportedTextures.isHelmet(id) && TextureImportClient.available(id) ? id : null;
    }
    public static ItemStack renderCopy(ItemStack original) {
        var skin = resolve(original);
        if (skin == null) return original;
        return preview(original, skin);
    }
    static ItemStack preview(ItemStack original, Identifier skin) {
        var copy = original.copy();
        copy.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("player_head"));
        copy.set(DataComponents.PROFILE, profile(skin));
        return copy;
    }
    static ResolvableProfile profile(Identifier skin) {
        return profiles.computeIfAbsent(skin, id -> {
            var json = new JsonObject(); json.addProperty("texture", id.withPath("entity/" + id.getPath()).toString());
            return ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        });
    }
    public static void applyWorn(ItemStack helmet,
            net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
        var skin = resolve(helmet);
        if (skin == null) return;
        state.wornHeadType = net.minecraft.world.level.block.SkullBlock.Types.PLAYER;
        state.wornHeadProfile = profile(skin);
        state.headItem.clear();
    }
}

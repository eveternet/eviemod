package dev.eviemod.paintbrush;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.LoggerFactory;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public final class PaintBrushClient implements ClientModInitializer {
    private static ModelOverrides overrides;
    private static boolean configReady;
    private static ColorOverrides colors;
    private static boolean colorsReady;
    private static NameOverrides names;
    private static boolean namesReady;
    private static boolean openEditor, openSettings;
    private static final EquipmentColorContinuity equipmentColors = new EquipmentColorContinuity();
    private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR_SLOTS = {
        net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
        net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET
    };
    private static Object equipmentPlayer, equipmentLevel;
    private static void updateEquipmentContext(net.minecraft.client.Minecraft client) {
        if (equipmentPlayer != client.player || equipmentLevel != client.level) {
            equipmentColors.clear(); equipmentPlayer = client.player; equipmentLevel = client.level;
        }
    }

    public static ModelOverrides models() { return overrides; }
    public static ColorOverrides colors() { return colors; }
    public static NameOverrides names() { return names; }
    public static void requireReady(int tab) throws IOException {
        if (!(tab == 0 ? configReady : tab == 1 ? colorsReady : namesReady))
            throw new IOException("Fix the config file and run /paintbrush reload before saving.");
    }

    public static Component resolveName(ItemStack stack, Component original) {
        return names == null ? original : names.resolve(stack, original);
    }

    public static int resolveColor(ItemStack stack, int original) {
        int resolved = colors == null ? original : colors.resolve(stack, original, ItemAppearance.supportsModel(stack) && overrides != null && overrides.get(SkyBlockUuid.read(stack)) != null);
        var client = net.minecraft.client.Minecraft.getInstance();
        updateEquipmentContext(client);
        if (colors != null && client.player != null && ColorOverrides.isLeatherArmor(stack)) {
            for (var slot : ARMOR_SLOTS) {
                var equipped = client.player.getItemBySlot(slot);
                if (stack == equipped) {
                    resolved = equipmentColors.resolve(slot, equipped, stack, colors, resolved);
                    break;
                }
            }
        }
        return resolved;
    }

    public static int resolveWornColor(Object state, ItemStack stack, int original) {
        var client = net.minecraft.client.Minecraft.getInstance();
        updateEquipmentContext(client);
        if (colors == null || client.player == null) return original;
        var slot = WornArmorOwnership.localSlot(state, stack, client.player.getId());
        if (slot == null) return original;
        var equipped = client.player.getItemBySlot(slot);
        int resolved = equipmentColors.resolveOwnedCopy(slot, equipped, stack, colors, original);
        return resolved;
    }

    public static Identifier resolve(ItemStack stack, Identifier original) {
        var skin = HelmetSkins.resolve(stack);
        if (skin != null) return Identifier.withDefaultNamespace("player_head");
        if (!ItemAppearance.supportsModel(stack)) return original;
        Identifier resolved = overrides == null ? original : overrides.resolve(stack, original);
        if (ImportedTextures.isImported(resolved) && (ImportedTextures.isHelmet(resolved) || !TextureImportClient.available(resolved))) return original;
        return resolved;
    }

    @Override public void onInitializeClient() {
        EviemodSettings.load();
        SkyBlockSession.init();
        try { HelmetSkins.load(); }
        catch (IOException e) { LoggerFactory.getLogger("eviemod").error("Could not load helmet skins", e); }
        overrides = new ModelOverrides(ConfigMigration.path("eviemod-paintbrush.json"));
        try { overrides.load(); configReady = true; }
        catch (IOException e) { LoggerFactory.getLogger("eviemod").error("Could not load Paint Brush config", e); }
        colors = new ColorOverrides(ConfigMigration.path("eviemod-colors.json"));
        try { colors.load(); colorsReady = true; }
        catch (IOException e) { LoggerFactory.getLogger("eviemod").error("Could not load Paint Brush colors", e); }
        names = new NameOverrides(ConfigMigration.path("eviemod-names.json"));
        try { names.load(); namesReady = true; }
        catch (IOException e) { LoggerFactory.getLogger("eviemod").error("Could not load Paint Brush names", e); }
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            updateEquipmentContext(client);
            RarityBackgrounds.tick(client);
            EviemodSettings.tick(client);
            if (openSettings) { openSettings = false; client.setScreen(EviemodSettings.screen(null)); }
            if (client.player != null) for (var slot : ARMOR_SLOTS)
                equipmentColors.observe(slot, client.player.getItemBySlot(slot));
            if (openEditor) {
                openEditor = false;
                if (client.player != null) client.setScreen(new PaintBrushScreen());
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
            dispatcher.register(literal("eviemod")
                .executes(ctx -> { openSettings = true; return 1; })
                .then(literal("settings").executes(ctx -> { openSettings = true; return 1; }))
                .then(literal("reload").executes(ctx -> {
                    EviemodSettings.load(); RarityBackgrounds.clear();
                    return EviemodSettings.STORE.error() == null ? feedback(ctx.getSource(), "eviemod settings reloaded.")
                        : error(ctx.getSource(), EviemodSettings.STORE.error());
                })));
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
            literal("paintbrush")
                .executes(ctx -> { openEditor = true; return 1; })
                .then(literal("name")
                    .then(literal("set").then(argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> name(ctx.getSource(), "set", StringArgumentType.getString(ctx, "text")))))
                    .then(literal("clear").executes(ctx -> name(ctx.getSource(), "clear", null)))
                    .then(literal("info").executes(ctx -> name(ctx.getSource(), "info", null))))
                .then(literal("color")
                    .then(literal("set").then(argument("hex", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            String query = builder.getRemainingLowerCase();
                            DyePresets.names().stream().filter(n -> n.toLowerCase(java.util.Locale.ROOT).contains(query)).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> color(ctx.getSource(), "set", StringArgumentType.getString(ctx, "hex")))))
                    .then(literal("clear").executes(ctx -> color(ctx.getSource(), "clear", null)))
                    .then(literal("info").executes(ctx -> color(ctx.getSource(), "info", null))))
                .then(literal("set").then(argument("model", StringArgumentType.greedyString())
                    .executes(ctx -> edit(ctx.getSource(), StringArgumentType.getString(ctx, "model")))))
                .then(literal("clear").executes(ctx -> edit(ctx.getSource(), null)))
                .then(literal("info").executes(ctx -> info(ctx.getSource())))
                .then(literal("reload").executes(ctx -> {

                    boolean success = true;
                    try { HelmetSkins.load(); }
                    catch (IOException e) { error(ctx.getSource(), e.getMessage()); success = false; }
                    try { overrides.load(); configReady = true; }
                    catch (IOException e) { configReady = false; error(ctx.getSource(), e.getMessage()); success = false; }
                    try { colors.load(); colorsReady = true; }
                    catch (IOException e) { colorsReady = false; error(ctx.getSource(), e.getMessage()); success = false; }
                    try { names.load(); namesReady = true; }
                    catch (IOException e) { namesReady = false; error(ctx.getSource(), e.getMessage()); success = false; }
                    return success ? feedback(ctx.getSource(), "Paint Brush models, colors, names and helmet skins reloaded.") : 0;
                }))
        ));
    }

    private static int name(FabricClientCommandSource source, String action, String value) {

        var uuid = SkyBlockUuid.read(source.getPlayer().getMainHandItem());
        if (uuid == null) return error(source, "The held item has no valid SkyBlock UUID.");
        if (action.equals("info")) {
            String custom = names.get(uuid);
            return feedback(source, "UUID: " + uuid + " | Name override: " + (custom == null ? "none" : custom));
        }
        if (!namesReady) return error(source, "Fix eviemod-names.json and use /paintbrush reload before saving names.");
        try {
            names.set(uuid, value);
            return feedback(source, value == null ? "Removed name override for " + uuid : "Item renamed to " + value);
        } catch (IllegalArgumentException e) { return error(source, e.getMessage()); }
        catch (IOException e) { return error(source, "Could not save item name: " + e.getMessage()); }
    }

    private static int color(FabricClientCommandSource source, String action, String value) {

        ItemStack stack = source.getPlayer().getMainHandItem();
        var model = overrides.get(SkyBlockUuid.read(stack));
        if (!(model == null ? ColorOverrides.isDyeable(stack) : ItemAppearance.hasDyeTint(model)))
            return error(source, "Hold a dyeable item.");
        var uuid = SkyBlockUuid.read(stack);
        if (uuid == null) return error(source, "The held armor has no valid SkyBlock UUID.");
        if (action.equals("info")) {
            String custom = colors.getValue(uuid);
            return feedback(source, "UUID: " + uuid + " | Color override: " + (custom == null ? "none" : DyePresets.display(custom)));
        }
        if (!colorsReady) return error(source, "Fix eviemod-colors.json and use /paintbrush reload before saving colors.");
        try {
            String normalized = DyePresets.normalize(value);
            colors.setValue(uuid, normalized);
            return feedback(source, normalized == null ? "Removed color override for " + uuid : "Armor dye set to " + DyePresets.display(normalized));
        } catch (IllegalArgumentException e) { return error(source, e.getMessage()); }
        catch (IOException e) { return error(source, "Could not save armor color: " + e.getMessage()); }
    }

    private static int edit(FabricClientCommandSource source, String value) {

        if (value != null && !ItemAppearance.supportsModel(source.getPlayer().getMainHandItem()))
            return error(source, "Model changes are currently available for held items only.");
        if (!configReady) return error(source, "Config could not be loaded. Fix eviemod-paintbrush.json and use /paintbrush reload before saving.");
        var uuid = SkyBlockUuid.read(source.getPlayer().getMainHandItem());
        if (uuid == null) return error(source, "The held item has no valid SkyBlock UUID.");
        Identifier model = value == null ? null : Identifier.tryParse(value);
        if (value != null && model == null) return error(source, "Invalid model Identifier. Example: minecraft:diamond_sword");
        try {
            overrides.set(uuid, model);
            return feedback(source, model == null ? "Removed override for " + uuid : "Painted " + uuid + " with " + model);
        } catch (IOException e) { return error(source, "Could not save Paint Brush config: " + e.getMessage()); }
    }

    private static int info(FabricClientCommandSource source) {
        ItemStack stack = source.getPlayer().getMainHandItem();
        var uuid = SkyBlockUuid.read(stack);
        if (uuid == null) return error(source, "The held item has no valid SkyBlock UUID.");
        return feedback(source, "UUID: " + uuid + " | Original: " + stack.get(DataComponents.ITEM_MODEL)
                + " | Override: " + (overrides.get(uuid) == null ? "none" : overrides.get(uuid)));
    }

    private static int feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message)); return 1;
    }
    private static int error(FabricClientCommandSource source, String message) {
        source.sendError(Component.literal(message)); return 0;
    }
}

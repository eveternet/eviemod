package dev.eviemod.paintbrush;

import java.util.List;
import net.azureaaron.dandelion.api.*;
import net.azureaaron.dandelion.api.controllers.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Each feature owns a category factory. New features need no changes to the screen/input shell. */
final class SettingsCategories {
    @FunctionalInterface interface CategoryFactory {
        ConfigCategory create(ModSettings.Values settings, List<net.minecraft.world.item.ItemStack> fixtures);
    }
    private static final List<CategoryFactory> CATEGORIES = List.of(SettingsCategories::appearance, SettingsCategories::texturePackBypasser);
    static List<ConfigCategory> create(ModSettings.Values settings, List<net.minecraft.world.item.ItemStack> fixtures) {
        var categories = new java.util.ArrayList<>(CATEGORIES.stream().map(factory -> factory.create(settings, fixtures)).toList());
        categories.addAll(ImportedSettingsCategories.create(settings));
        return categories;
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("eviemod", path); }
    private static Component text(String value) { return Component.literal(value); }
    private static ConfigCategory appearance(ModSettings.Values draft, List<net.minecraft.world.item.ItemStack> fixtures) {
        return ConfigCategory.createBuilder().id(id("appearance")).name(text("Appearance"))
            .description(text("Inventory and interface appearance"))
            .group(OptionGroup.createBuilder().id(id("rarity")).name(text("Item rarity backgrounds"))
                .description(text("Rarity colors in SkyBlock inventory slots and the main hotbar."))
                .collapsed(false)
                .option(Option.<Boolean>createBuilder().id(id("rarity/enabled")).name(text("Rarity backgrounds"))
                    .description(text("Show rarity colors behind eligible items. Disable Skyblocker's backgrounds if both mods are installed."))
                    .binding(false, () -> draft.rarityBackgrounds, value -> draft.rarityBackgrounds = value)
                    .controller(BooleanController.createBuilder().build()).build())
                .option(Option.<ModSettings.Shape>createBuilder().id(id("rarity/shape")).name(text("Shape"))
                    .description(text("Choose the shape of item backgrounds."))
                    .binding(ModSettings.Shape.SQUARE, () -> draft.shape, value -> draft.shape = value)
                    .controller(EnumController.<ModSettings.Shape>createBuilder().dropdown(true)
                        .formatter(value -> text(value == ModSettings.Shape.SQUARE ? "Square" : "Circle")).build()).build())
                .option(Option.<Integer>createBuilder().id(id("rarity/opacity")).name(text("Opacity"))
                    .description(text("Background opacity, from 0 to 100 percent."))
                    .binding(45, () -> draft.opacity, value -> draft.opacity = value)
                    .controller(IntegerController.createBuilder().range(0, 100).slider(5).build()).build())
                .build())
            .group(paintBrush(draft, fixtures))
            .group(ImportedSettingsCategories.visuals(draft)).build();
    }
    private static ConfigCategory texturePackBypasser(ModSettings.Values draft, List<net.minecraft.world.item.ItemStack> fixtures) {
        return ConfigCategory.createBuilder().id(id("texture_pack_bypasser")).name(text("Hypixel Pack"))
            .description(text("Keep Hypixel's SkyBlock pack as a local resource pack."))
            .option(Option.<Boolean>createBuilder().id(id("texture_pack_bypasser/enabled")).name(text("Hypixel Pack"))
                .description(text("Download and update the official pack locally. Select and arrange it in Minecraft's Resource Packs screen."))
                .binding(false, () -> draft.texturePackBypasser, value -> draft.texturePackBypasser = value)
                .controller(BooleanController.createBuilder().build()).build())
            .option(ButtonOption.createBuilder().id(id("texture_pack_bypasser/check")).name(text("Check for updates"))
                .description(text("Check now and reload the pack if an update is ready and the pack is selected."))
                .prompt(text("Check for updates"))
                .action(parent -> {
                    try { EviemodSettings.STORE.save(draft); TexturePackBypasser.manualCheck(); }
                    catch (java.io.IOException e) {
                        var client = net.minecraft.client.Minecraft.getInstance();
                        client.setScreen(new net.minecraft.client.gui.screens.AlertScreen(() -> client.setScreen(parent),
                            text("Settings could not be saved"), text(e.getMessage())));
                    }
                }).build()).build();
    }
    private static OptionGroup paintBrush(ModSettings.Values draft, List<net.minecraft.world.item.ItemStack> fixtures) {
        return OptionGroup.createBuilder().id(id("paintbrush")).name(text("Paint Brush"))
            .description(text("Customize item models, dyes, names, skins and textures"))
            .option(ButtonOption.createBuilder().id(id("paintbrush/editor")).name(text("Paint Brush editor"))
                .description(text("Customize an item's model, dye and name."))
                .tags(text("model"), text("dye"), text("color"), text("name"), text("rename"), text("paintbrush"))
                .prompt(text("Open editor"))
                .action(parent -> {
                    var client = net.minecraft.client.Minecraft.getInstance();
                    try { EviemodSettings.STORE.save(draft); client.setScreen(new PaintBrushScreen(parent, fixtures)); }
                    catch (java.io.IOException e) { client.setScreen(new net.minecraft.client.gui.screens.AlertScreen(
                        () -> client.setScreen(parent), text("Settings could not be saved"), text(e.getMessage()))); }
                })
                .build()).build();
    }
}

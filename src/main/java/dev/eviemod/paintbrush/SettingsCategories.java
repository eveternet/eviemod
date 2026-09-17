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
    private static final List<CategoryFactory> CATEGORIES = List.of(SettingsCategories::appearance, SettingsCategories::paintBrush);
    static List<ConfigCategory> create(ModSettings.Values settings, List<net.minecraft.world.item.ItemStack> fixtures) {
        return CATEGORIES.stream().map(factory -> factory.create(settings, fixtures)).toList();
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
                    .binding(true, () -> draft.rarityBackgrounds, value -> draft.rarityBackgrounds = value)
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
                .build()).build();
    }
    private static ConfigCategory paintBrush(ModSettings.Values draft, List<net.minecraft.world.item.ItemStack> fixtures) {
        return ConfigCategory.createBuilder().id(id("paintbrush")).name(text("Paint Brush"))
            .description(text("Customize item models, dyes, names, skins and textures"))
            .option(Option.<Boolean>createBuilder().id(id("paintbrush/helmet_skins")).name(text("Helmet skins"))
                .description(text("Choose a helmet skin or import a skin PNG in the Paint Brush editor."))
                .binding(false, () -> draft.helmetSkins, value -> draft.helmetSkins = value)
                .controller(BooleanController.createBuilder().build()).build())
            .option(Option.<Boolean>createBuilder().id(id("paintbrush/custom_textures")).name(text("Custom textures"))
                .description(text("Import PNG textures with bow, sword or handheld positioning."))
                .binding(false, () -> draft.customTextures, value -> draft.customTextures = value)
                .controller(BooleanController.createBuilder().build()).build())
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

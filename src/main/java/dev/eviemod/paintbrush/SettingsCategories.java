package dev.eviemod.paintbrush;

import java.util.List;
import net.azureaaron.dandelion.api.*;
import net.azureaaron.dandelion.api.controllers.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Each feature owns a category factory. New features need no changes to the screen/input shell. */
final class SettingsCategories {
    @FunctionalInterface interface CategoryFactory {
        ConfigCategory create(ModSettings.Values settings, PaintBrushScreen paint);
    }
    private static final List<CategoryFactory> CATEGORIES = List.of(SettingsCategories::appearance, SettingsCategories::paintBrush);
    static List<ConfigCategory> create(ModSettings.Values settings, PaintBrushScreen paint) {
        return CATEGORIES.stream().map(factory -> factory.create(settings, paint)).toList();
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("eviemod", path); }
    private static Component text(String value) { return Component.literal(value); }
    private static ConfigCategory appearance(ModSettings.Values draft, PaintBrushScreen paint) {
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
    private static ConfigCategory paintBrush(ModSettings.Values draft, PaintBrushScreen paint) {
        return ConfigCategory.createBuilder().id(id("paintbrush")).name(text("Paint Brush"))
            .description(text("Customize an item's model, dye and name"))
            .option(Option.<String>createBuilder().id(id("paintbrush/editor")).name(text("Paint Brush item editor"))
                .description(text("Choose an item, then edit its model, dye or name."))
                .tags(text("model"), text("dye"), text("color"), text("name"), text("rename"), text("paintbrush"))
                .binding("", () -> "", value -> {})
                .controller(new PaintBrushController(paint)).build()).build();
    }
}

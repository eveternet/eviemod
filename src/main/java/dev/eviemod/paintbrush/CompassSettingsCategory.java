package dev.eviemod.paintbrush;

import dev.eviemod.features.compass.WishingCompassClient;
import net.azureaaron.dandelion.api.*;
import net.azureaaron.dandelion.api.controllers.BooleanController;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class CompassSettingsCategory {
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("eviemod", "compass/" + path); }
    static ConfigCategory create(ModSettings.Values draft, java.util.List<net.minecraft.world.item.ItemStack> fixtures) {
        return ConfigCategory.createBuilder().id(id("category")).name(Component.literal("Mining"))
            .group(OptionGroup.createBuilder().id(id("group")).name(Component.literal("Wishing Compass"))
                .option(Option.<Boolean>createBuilder().id(id("enabled")).name(Component.literal("Wishing Compass Solver"))
                    .description(Component.literal("Use your compass twice outside the Crystal Nucleus, at least eight blocks apart sideways in the same region. Wait five seconds after each use. Shows an estimated destination and uncertainty."))
                    .binding(false, () -> draft.features.skyblock.wishingCompass, value -> {
                        draft.features.skyblock.wishingCompass = value; draft.choose("skyblock.wishingCompass");
                    }).controller(BooleanController.createBuilder().build()).build())
                .option(ButtonOption.createBuilder().id(id("clear")).name(Component.literal("Clear compass observations"))
                    .description(Component.literal("Start again when your compass target changes. Observations also clear when you leave the world or lobby."))
                    .prompt(Component.literal("Clear"))
                    .action(parent -> WishingCompassClient.clear()).build())
                .build()).build();
    }
}

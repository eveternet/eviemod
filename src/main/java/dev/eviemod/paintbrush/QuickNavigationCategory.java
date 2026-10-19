package dev.eviemod.paintbrush;

import dev.eviemod.compat.ClientUi;
import java.util.List;
import net.azureaaron.dandelion.api.*;
import net.azureaaron.dandelion.api.controllers.BooleanController;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class QuickNavigationCategory {
    static ConfigCategory create(ModSettings.Values draft, List<ItemStack> fixtures) {
        return ConfigCategory.createBuilder().id(id("quick_navigation")).name(text("Quick Navigation"))
            .description(text("Command tabs around SkyBlock inventories."))
            .option(Option.<Boolean>createBuilder().id(id("quick_navigation/enabled")).name(text("Quick Navigation"))
                .description(text("Show fourteen configurable command tabs above and below inventories. Turn off the matching feature in Skyblocker and SkyOcean if installed."))
                .binding(false, () -> draft.quickNavigation.enabled, value -> draft.quickNavigation.enabled = value)
                .controller(BooleanController.createBuilder().build()).build())
            .option(ButtonOption.createBuilder().id(id("quick_navigation/editor")).name(text("Navigation tabs"))
                .description(text("Select a tab to edit its icon, command, menu highlight, tooltip and click action."))
                .tags(text("inventory"), text("buttons"), text("quick nav"), text("warp"))
                .prompt(text("Edit tabs"))
                .action(parent -> {
                    try {
                        EviemodSettings.STORE.save(draft);
                        ClientUi.setScreen(Minecraft.getInstance(), new QuickNavigationScreen(parent, draft));
                    } catch (java.io.IOException e) {
                        ClientUi.setScreen(Minecraft.getInstance(), new net.minecraft.client.gui.screens.AlertScreen(
                            () -> ClientUi.setScreen(Minecraft.getInstance(), parent), text("Settings could not be saved"), text(e.getMessage())));
                    }
                }).build()).build();
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("eviemod", path); }
    private static Component text(String value) { return Component.literal(value); }
}

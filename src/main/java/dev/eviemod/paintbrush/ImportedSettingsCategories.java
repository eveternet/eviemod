package dev.eviemod.paintbrush;

import com.mojang.blaze3d.platform.InputConstants;
import dev.eviemod.features.skyblock.PartyCommandController;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.azureaaron.dandelion.api.*;
import net.azureaaron.dandelion.api.controllers.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class ImportedSettingsCategories {
    private static Identifier id(String value) { return Identifier.fromNamespaceAndPath("eviemod", value); }
    private static Component text(String value) { return Component.literal(value); }
    private static Option<Boolean> toggle(String path, String name, String description, boolean initial, Supplier<Boolean> get, Consumer<Boolean> set) {
        return Option.<Boolean>createBuilder().id(id(path)).name(text(name)).description(text(description))
            .binding(initial, get, set).controller(BooleanController.createBuilder().build()).build();
    }
    static List<ConfigCategory> create(ModSettings.Values draft) {
        return List.of(visuals(draft), garden(draft), commands(draft), hotkeys(draft));
    }
    private static ConfigCategory visuals(ModSettings.Values draft) {
        var values = draft.features;
        return ConfigCategory.createBuilder().id(id("skyblock_visuals")).name(text("SkyBlock Visuals"))
            .option(toggle("visuals/barrier", "No Barrier Effects", "Removes client-side barrier border effects in SkyBlock.", true,
                () -> values.skyblock.noBarrierEffects, value -> values.skyblock.noBarrierEffects = value))
            .option(toggle("visuals/hearts", "Max 10 Hearts", "Scales SkyBlock health to 10 hearts outside The Rift.", true,
                () -> values.skyblock.maxTenHearts, value -> values.skyblock.maxTenHearts = value))
            .group(OptionGroup.createBuilder().id(id("soul_whip")).name(text("Soul Whip Fix")).collapsed(false)
                .option(toggle("soul_whip/enabled", "Soul Whip Fix", "Prevents repeated first-person equip animations when using or updating the held item.", true,
                    () -> values.soulWhip.enabled, value -> values.soulWhip.enabled = value)).build()).build();
    }
    private static ConfigCategory garden(ModSettings.Values draft) {
        var values = draft.features.garden;
        var category = ConfigCategory.createBuilder().id(id("garden")).name(text("Garden Tools"))
            .option(toggle("garden/mouse_lock", "Mouse Lock", "Locks the camera while grounded in the Garden holding a farming tool.", false,
                () -> values.mouseLock, value -> values.mouseLock = value))
            .option(toggle("garden/finnegan", "Force Finnegan", "Uses the shorter Finnegan pest cooldown: 75 seconds instead of 135.", false,
                () -> values.forceFinnegan, value -> values.forceFinnegan = value))
            .option(Option.<Integer>createBuilder().id(id("garden/plot")).name(text("Teleport Plot"))
                .description(text("Plot used by the teleport key."))
                .binding(1, () -> values.teleportPlot, value -> values.teleportPlot = value)
                .controller(IntegerController.createBuilder().range(1, 24).build()).build());
        ImportedKeyBindings.garden().forEach((name, mapping) -> {
            String saved = values.keys.getOrDefault(name, mapping.saveString());
            InputConstants.Key key;
            try { key = InputConstants.getKey(saved); } catch (IllegalArgumentException e) { key = InputConstants.UNKNOWN; }
            category.option(KeyMappingOption.createBuilder().id(id("garden/key/" + name))
                .name(Component.translatable(mapping.getName()))
                .keyMapping(DraftKeyMapping.bind("garden/" + name, key, value -> values.keys.put(name, value.getName()))).build());
        });
        return category.build();
    }
    private static ConfigCategory commands(ModSettings.Values draft) {
        var category = ConfigCategory.createBuilder().id(id("party_commands")).name(text("Party Commands"));
        for (var feature : PartyCommandController.FEATURES) {
            var channels = draft.features.skyblock.channels(feature.key());
            var group = OptionGroup.createBuilder().id(id("commands/" + feature.key())).name(text(feature.name()))
                .description(text(feature.description())).collapsed(false);
            for (var channel : PartyCommandController.CommandChannel.values()) {
                String path = "commands/" + feature.key() + "/" + channel.name().toLowerCase(Locale.ROOT);
                group.option(toggle(path, channel.displayName, feature.description(), channel == PartyCommandController.CommandChannel.PARTY,
                    () -> switch (channel) { case PARTY -> channels.party; case GUILD -> channels.guild; case COOP -> channels.coop; },
                    value -> { switch (channel) { case PARTY -> channels.party = value; case GUILD -> channels.guild = value; case COOP -> channels.coop = value; } }));
            }
            category.group(group.build());
        }
        return category.build();
    }
    private static ConfigCategory hotkeys(ModSettings.Values draft) {
        var values = draft.features.skyblock;
        var category = ConfigCategory.createBuilder().id(id("command_hotkeys")).name(text("Command Hotkeys"))
            .description(text("Bind keys to run chat commands. Use commands with or without a leading slash."))
            .option(toggle("hotkeys/enabled", "Command Hotkeys", "Enable custom command hotkeys.", true,
                () -> values.commandHotkeysEnabled, value -> values.commandHotkeysEnabled = value))
            .option(ButtonOption.createBuilder().id(id("hotkeys/add")).name(text("Add hotkey")).prompt(text("Add"))
                .action(parent -> { values.commandHotkeys.add(new ImportedFeatures.Hotkey()); EviemodSettings.rebuild(parent, draft, "Command Hotkeys"); }).build());
        for (int index = 0; index < values.commandHotkeys.size(); index++) {
            var hotkey = values.commandHotkeys.get(index);
            String path = "hotkeys/" + index;
            var key = hotkey.key >= 0 && hotkey.key <= 7 ? InputConstants.Type.MOUSE.getOrCreate(hotkey.key)
                : InputConstants.Type.KEYSYM.getOrCreate(hotkey.key);
            category.group(OptionGroup.createBuilder().id(id(path)).name(text("Hotkey " + (index + 1))).collapsed(false)
                .option(KeyMappingOption.createBuilder().id(id(path + "/key")).name(text("Key"))
                    .keyMapping(DraftKeyMapping.bind(path, key, value -> hotkey.key = value.getValue())).build())
                .option(Option.<String>createBuilder().id(id(path + "/command")).name(text("Command"))
                    .binding("", () -> hotkey.command, value -> hotkey.command = value)
                    .controller(StringController.createBuilder().build()).build())
                .option(ButtonOption.createBuilder().id(id(path + "/remove")).name(text("Remove hotkey")).prompt(text("Remove"))
                    .action(parent -> { values.commandHotkeys.remove(hotkey); EviemodSettings.rebuild(parent, draft, "Command Hotkeys"); }).build())
                .build());
        }
        return category.build();
    }
}

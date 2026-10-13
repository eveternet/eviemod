package dev.eviemod.paintbrush;

import com.google.gson.JsonParser;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vanilla syntax and captured SkyBlock item components. Only creates local preview stacks. */
final class QuickNavigationIcons {
    private static HolderLookup.Provider offline;
    static ItemStack parse(QuickNavigationSettings.Tab tab) {
        if (tab.empty()) return ItemStack.EMPTY;
        if (tab.capturedIcon.isBlank()) return parse(tab.icon);
        try {
            var stack = ItemStack.CODEC.parse(lookup().createSerializationContext(JsonOps.INSTANCE),
                JsonParser.parseString(tab.capturedIcon)).getOrThrow();
            if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(tab.icon))
                throw new IllegalArgumentException("Captured icon does not match its item.");
            return stack.copyWithCount(1);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Captured icon is unavailable. Choose the held item again.", e);
        }
    }
    static void capture(QuickNavigationSettings.Tab tab, ItemStack held) {
        if (held.isEmpty()) throw new IllegalArgumentException("Hold an item to use as the icon.");
        // Keep identity, head textures and pack-specific components, never change the live stack.
        String encoded = ItemStack.CODEC.encodeStart(lookup().createSerializationContext(JsonOps.INSTANCE),
            held.copyWithCount(1)).getOrThrow().toString();
        if (encoded.length() > 65536) throw new IllegalArgumentException("This item has too much data to use as an icon.");
        tab.icon = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        tab.capturedIcon = encoded;
    }
    static ItemStack parse(String input) {
        try {
            var reader = new StringReader(input);
            var stack = new ItemParser(lookup()).parse(reader).createItemStack(1);
            if (reader.canRead() || stack.isEmpty()) throw new IllegalArgumentException("Invalid icon item");
            return stack;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalArgumentException("Icon: " + e.getRawMessage().getString(), e);
        }
    }
    private static HolderLookup.Provider lookup() {
        var client = Minecraft.getInstance();
        return client != null && client.level != null ? client.level.registryAccess() : offlineLookup();
    }
    private static HolderLookup.Provider offlineLookup() {
        if (offline == null) offline = net.minecraft.data.registries.VanillaRegistries.createLookup();
        return offline;
    }
    static ItemStack preview(String input) {
        try { return parse(input); }
        catch (RuntimeException e) { return new ItemStack(Items.BARRIER); }
    }
    static ItemStack preview(QuickNavigationSettings.Tab tab) {
        try { return parse(tab); }
        catch (RuntimeException e) { return new ItemStack(Items.BARRIER); }
    }
}

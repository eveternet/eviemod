package dev.eviemod.paintbrush;

import com.mojang.brigadier.StringReader;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vanilla command item syntax, including components. Only creates local preview stacks. */
final class QuickNavigationIcons {
    private static HolderLookup.Provider offline;
    static ItemStack parse(String input) {
        try {
            var client = Minecraft.getInstance();
            var lookup = client != null && client.level != null ? client.level.registryAccess() : offlineLookup();
            var reader = new StringReader(input);
            var stack = new ItemParser(lookup).parse(reader).createItemStack(1);
            if (reader.canRead() || stack.isEmpty()) throw new IllegalArgumentException("Invalid icon item");
            return stack;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalArgumentException("Icon: " + e.getRawMessage().getString(), e);
        }
    }
    private static HolderLookup.Provider offlineLookup() {
        if (offline == null) offline = net.minecraft.data.registries.VanillaRegistries.createLookup();
        return offline;
    }
    static ItemStack preview(String input) {
        try { return parse(input); }
        catch (RuntimeException e) { return new ItemStack(Items.BARRIER); }
    }
}

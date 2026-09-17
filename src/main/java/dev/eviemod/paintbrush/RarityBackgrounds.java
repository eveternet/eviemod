package dev.eviemod.paintbrush;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class RarityBackgrounds {
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};
    private static final RarityMemory MEMORY = new RarityMemory();
    private static Object player, level;
    public static void clear() { MEMORY.clear(); }
    private static void context(Minecraft client) {
        if (player != client.player || level != client.level) {
            clear(); player = client.player; level = client.level;
        }
    }
    static void tick(Minecraft client) {
        context(client);
        if (!SkyBlockSession.active() || client.player == null || !EviemodSettings.STORE.values().rememberRarity) return;
        var inventory = client.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) MEMORY.observe(slot, inventory.getItem(slot));
        for (var slot : ARMOR_SLOTS) MEMORY.observe(100 + slot.ordinal(), client.player.getItemBySlot(slot));
    }
    public static void draw(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        var client = Minecraft.getInstance(); context(client);
        var settings = EviemodSettings.STORE.values();
        if (!SkyBlockSession.active() || !settings.rarityBackgrounds || settings.opacity == 0 || stack.isEmpty()) return;
        ItemRarity rarity = ItemRarity.read(stack);
        if (settings.rememberRarity) {
            rarity = MEMORY.resolve(stack);
            if (client.player != null) {
                var inventory = client.player.getInventory();
                boolean found = false;
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    if (stack == inventory.getItem(slot)) { rarity = MEMORY.observe(slot, stack); found = true; break; }
                }
                if (!found) for (var slot : ARMOR_SLOTS) {
                    if (stack == client.player.getItemBySlot(slot)) { rarity = MEMORY.observe(100 + slot.ordinal(), stack); break; }
                }
            }
        }
        if (rarity == null) return;
        int color = (Math.round(settings.opacity * 2.55f) << 24) | rarity.rgb;
        if (settings.shape == ModSettings.Shape.SQUARE) graphics.fill(x, y, x + 16, y + 16, color);
        else for (int row = 0; row < 16; row++) {
            double dy = row + .5 - 8;
            int halfWidth = (int) Math.floor(Math.sqrt(64 - dy * dy));
            graphics.fill(x + 8 - halfWidth, y + row, x + 8 + halfWidth, y + row + 1, color);
        }
    }
}

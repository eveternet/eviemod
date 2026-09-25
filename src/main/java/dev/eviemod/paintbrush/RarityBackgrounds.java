package dev.eviemod.paintbrush;

import java.util.IdentityHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class RarityBackgrounds {
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};
    private static final RarityMemory MEMORY = new RarityMemory();
    private static final RarityCache PARSED = new RarityCache();
    private static final IdentityHashMap<ItemStack, Integer> liveSlots = new IdentityHashMap<>();
    private static final IdentityHashMap<ItemStack, Integer> renderSlots = new IdentityHashMap<>();
    private static GuiGraphicsExtractor indexedGraphics;
    private static Object player, level;
    public static void clear() { MEMORY.clear(); PARSED.clear(); liveSlots.clear(); renderSlots.clear(); indexedGraphics = null; }
    private static void context(Minecraft client) {
        if (player != client.player || level != client.level) {
            clear(); player = client.player; level = client.level;
        }
    }
    static void tick(Minecraft client) {
        context(client);
        var settings = EviemodSettings.STORE.values();
        if (!SkyBlockSession.active() || client.player == null || !settings.rarityBackgrounds || !settings.rememberRarity) return;
        liveSlots.clear();
        indexedGraphics = null;
        var inventory = client.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            observe(slot, stack); liveSlots.putIfAbsent(stack, slot);
        }
        for (var slot : ARMOR_SLOTS) {
            var stack = client.player.getItemBySlot(slot);
            observe(100 + slot.ordinal(), stack); liveSlots.putIfAbsent(stack, 100 + slot.ordinal());
        }
    }
    static ItemRarity observe(int slot, ItemStack stack) {
        var parsed = PARSED.get(stack);
        // Memory is reconciled on every observation, including count/identity changes
        // and metadata gaps. Only the expensive fresh parse is cached.
        return MEMORY.observe(slot, stack, parsed.rarity(), parsed.tag());
    }
    private static int findSlot(Minecraft client, GuiGraphicsExtractor graphics, ItemStack stack) {
        if (client.player == null) return -1;
        var inventory = client.player.getInventory();
        Integer known = liveSlots.get(stack);
        if (known != null) {
            if (known < inventory.getContainerSize() && inventory.getItem(known) == stack) return known;
            if (known >= 100) for (var slot : ARMOR_SLOTS)
                if (known == 100 + slot.ordinal() && client.player.getItemBySlot(slot) == stack) return known;
        }
        // A container can render many non-owned stacks. Index the live inventory once per extraction,
        // including changes since the last tick, instead of scanning it for every container slot.
        if (indexedGraphics != graphics || known != null) {
            renderSlots.clear();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++)
                renderSlots.putIfAbsent(inventory.getItem(slot), slot);
            for (var slot : ARMOR_SLOTS)
                renderSlots.putIfAbsent(client.player.getItemBySlot(slot), 100 + slot.ordinal());
            indexedGraphics = graphics;
        }
        Integer current = renderSlots.get(stack);
        if (current != null) liveSlots.put(stack, current);
        else liveSlots.remove(stack);
        return current == null ? -1 : current;
    }
    public static void draw(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        var client = Minecraft.getInstance(); context(client);
        var settings = EviemodSettings.STORE.values();
        if (!SkyBlockSession.active() || !settings.rarityBackgrounds || settings.opacity == 0 || stack.isEmpty()) return;
        ItemRarity rarity;
        if (settings.rememberRarity) {
            int slot = findSlot(client, graphics, stack);
            if (slot >= 0) rarity = observe(slot, stack);
            else {
                var parsed = PARSED.get(stack);
                rarity = MEMORY.resolve(stack, parsed.rarity(), parsed.tag());
            }
        } else rarity = PARSED.get(stack).rarity();
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

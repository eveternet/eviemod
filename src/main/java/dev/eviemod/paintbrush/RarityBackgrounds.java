package dev.eviemod.paintbrush;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class RarityBackgrounds {
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};
    private static final RarityMemory MEMORY = new RarityMemory();
    private record Observed(ItemStack live, ItemStack snapshot, ItemRarity fresh) {}
    private static final Map<Integer, Observed> observed = new HashMap<>();
    private static final IdentityHashMap<ItemStack, Integer> liveSlots = new IdentityHashMap<>();
    private static final IdentityHashMap<ItemStack, Integer> renderSlots = new IdentityHashMap<>();
    private static GuiGraphicsExtractor indexedGraphics;
    private static Object player, level;
    public static void clear() { MEMORY.clear(); observed.clear(); liveSlots.clear(); renderSlots.clear(); indexedGraphics = null; }
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
        var previous = observed.get(slot);
        // Remembered rarity can change when another stack supplies metadata; only fresh rarity is stable.
        if (previous != null && previous.live() == stack && previous.fresh() != null
            && previous.snapshot().getCount() == stack.getCount()
            && ItemStack.isSameItemSameComponents(previous.snapshot(), stack)) {
            MEMORY.refreshKnown(slot);
            return previous.fresh();
        }
        var data = stack.get(DataComponents.CUSTOM_DATA);
        var tag = data == null ? null : data.copyTag();
        ItemRarity fresh = ItemRarity.read(stack, tag);
        ItemRarity rarity = MEMORY.observe(slot, stack, fresh, tag);
        // Empty slots and metadata-less items have no useful parsed value to cache.
        if (fresh != null) observed.put(slot, new Observed(stack, stack.copy(), fresh));
        else observed.remove(slot);
        return rarity;
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
            rarity = slot >= 0 ? observe(slot, stack) : MEMORY.resolve(stack);
        } else rarity = ItemRarity.read(stack);
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

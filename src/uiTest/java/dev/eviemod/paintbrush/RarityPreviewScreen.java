package dev.eviemod.paintbrush;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

final class RarityPreviewScreen extends Screen {
    private SlotRenderer slots;
    private final List<ItemStack> items = new ArrayList<>();
    RarityPreviewScreen() {
        super(Component.literal("eviemod rarity fixtures"));
        for (ItemRarity rarity : ItemRarity.values()) {
            var stack = new ItemStack(Items.LEATHER_BOOTS);
            var tag = new CompoundTag(); tag.putString("id", "TEST_BOOTS");
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(rarity.name().replace('_', ' ') + " BOOTS"))));
            items.add(stack);
        }
    }
    @Override protected void init() {
        slots = new SlotRenderer();
        slots.init(width, height);
        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> minecraft.setScreen(EviemodSettings.screen(this)))
            .bounds(width / 2 - 100, height - 40, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(width / 2 + 5, height - 40, 95, 20).build());
    }
    @Override public void onClose() { minecraft.setScreen(new TitleScreen()); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xff25262b);
        g.centeredText(font, "eviemod • rarity backgrounds", width / 2, 18, 0xffffffff);
        for (int i = 0; i < items.size(); i++) {
            int x = width / 2 - 120 + (i % 3) * 90, y = 50 + (i / 3) * 38;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xff505158);
            slots.draw(g, items.get(i), x, y, false);
            // Same item in a generic GUI preview: deliberately no background.
            g.item(items.get(i), x + 24, y);
            // Vanilla fake slots use fakeItem, outside Skyblocker's slot injection.
            slots.draw(g, items.get(i), x + 48, y, true);
            g.text(font, ItemRarity.values()[i].name(), x - 8, y + 20, 0xffffffff, false);
        }
        g.centeredText(font, "Each trio: slot / preview / fake slot", width / 2, 32, 0xffcccccc);
        super.extractRenderState(g, mx, my, delta);
    }

    private static final class SlotRenderer extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<net.minecraft.world.inventory.AbstractContainerMenu> {
        SlotRenderer() {
            super(new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
                @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) { return ItemStack.EMPTY; }
                @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
            }, new net.minecraft.world.entity.player.Inventory(null, new net.minecraft.world.entity.EntityEquipment()), Component.empty());
        }
        @Override protected void init() {}
        void draw(GuiGraphicsExtractor g, ItemStack stack, int x, int y, boolean fake) {
            var container = new net.minecraft.world.SimpleContainer(stack);
            var slot = new net.minecraft.world.inventory.Slot(container, 0, x, y) {
                @Override public boolean isFake() { return fake; }
            };
            extractSlot(g, slot, -1, -1);
        }
    }
}

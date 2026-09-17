package dev.eviemod.paintbrush;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import dev.eviemod.paintbrush.mixin.EditBoxSelectionAccessor;

public final class PaintBrushScreen extends CompactScreen {
    private final net.minecraft.client.gui.screens.Screen parent;
    private final Map<UUID, Draft> drafts = new HashMap<>();
    private final List<ItemStack> fixtures;
    private ItemStack selected = ItemStack.EMPTY;
    private Draft draft;
    private int tab, x, y, w, h, left, contentWidth;
    private List<String> models;
    private String error = "";
    private EditBox nameBox;
    private ModelField modelField;
    private final List<EditorButton> styleButtons = new ArrayList<>();
    private EditorButton colorSelection, dyeTab;
    private final Map<Identifier, Boolean> dyeModels = new HashMap<>();
    private int anchor, cursor;
    private boolean restoreNameFocus, offeredPicker;

    public PaintBrushScreen() { this(null, null); }
    PaintBrushScreen(List<ItemStack> fixtures) { this(null, fixtures); }
    PaintBrushScreen(net.minecraft.client.gui.screens.Screen parent, List<ItemStack> fixtures) {
        super(Component.literal("Paint Brush")); this.parent = parent; this.fixtures = fixtures;
        ItemStack held = minecraft.player != null ? minecraft.player.getMainHandItem()
            : fixtures != null && !fixtures.isEmpty() ? fixtures.getFirst() : ItemStack.EMPTY;
        if (SkyBlockUuid.read(held) != null) setItem(held);
    }

    @Override protected void init() {
        updateViewport();
        clearWidgets(); styleButtons.clear(); nameBox = null; modelField = null; colorSelection = null; dyeTab = null;
        w = 440; h = 260; x = (viewWidth - w) / 2; y = (viewHeight - h) / 2;
        left = x + 16; contentWidth = w - 32;
        button("Done", x + w - 62, y + 8, 50, this::onClose);
        button("Choose item", left, y + 42, 98, () -> minecraft.setScreen(new InventoryPicker()));
        if (draft == null) return;
        String[] tabs = {"Model", "Dye", "Name"};
        for (int i = 0; i < 3; i++) {
            final int t = i;
            var b = addRenderableWidget(new EditorButton(tabs[i], left + i * (contentWidth / 3), y + 74, contentWidth / 3 - 2,
                () -> { tab = t; error = ""; rebuildWidgets(); }, () -> tab == t));
            if (i == 1) { dyeTab = b; b.active = canDye(); }
        }
        int body = y + 108;
        if (tab == 0) {
            modelField = addRenderableWidget(new ModelField(font, left, body, contentWidth, availableModels(), draft.model,
                value -> { draft.model = value; draft.dirty[0] = true; error = ""; }));
        } else if (tab == 1) {
            modelField = addRenderableWidget(new ModelField(font, left, body, contentWidth, DyePresets.names(), draft.dye,
                value -> { draft.dye = value; draft.dirty[1] = true; error = ""; }, true));
            int[] colors = {0xff88cc, 0xff5555, 0xffaa00, 0xffff55, 0x55ff55, 0x55ffff, 0x5555ff, 0xaa55ff, 0xffffff, 0x000000};
            for (int i = 0; i < colors.length; i++) {
                final int rgb = colors[i];
                var b = button("■", left + i * (contentWidth / 10), body + 28, contentWidth / 10 - 2,
                    () -> { draft.dye = ColorOverrides.format(rgb); draft.dirty[1] = true; rebuildWidgets(); });
                b.setMessage(Component.literal("■").withColor(rgb));
            }
        } else {
            nameBox = field("Custom name", left, body, contentWidth, draft.name.text(), 256, value -> {
                draft.name.edit(value); draft.dirty[2] = true; error = "";
            });
            String[] labels = {"Bold", "Italic", "Glitch", "Underline", "Strike"};
            for (int i = 0; i < 5; i++) {
                final int flag = i;
                var b = addRenderableWidget(new EditorButton(labels[i], left + i * (contentWidth / 5), body + 26, contentWidth / 5 - 3, () -> {
                    draft.name.toggle(anchor, cursor, flag); draft.dirty[2] = true; restoreNameFocus = true;
                }, () -> draft.name.enabled(anchor, cursor, flag)));
                styleButtons.add(b);
            }
            field("Start color", left, body + 62, 108, draft.start, 7, value -> draft.start = value);
            field("End color (optional)", left + 114, body + 62, 134, draft.end, 7, value -> draft.end = value);
            colorSelection = button("Apply color", left + 254, body + 62, contentWidth - 254, () -> {
                try {
                    Integer start = optionalColor(draft.start);
                    if (start == null) throw new IllegalArgumentException("Enter a start color, e.g. #FF88CC.");
                    draft.name.color(anchor, cursor, start, optionalColor(draft.end)); draft.dirty[2] = true;
                    error = ""; restoreNameFocus = true;
                } catch (IllegalArgumentException e) { error = e.getMessage(); }
            });
            anchor = 0; cursor = 0; updateSelection();
        }
        button("Apply", left, y + h - 37, contentWidth / 2 - 3, this::apply);
        button("Reset", left + contentWidth / 2 + 3, y + h - 37, contentWidth / 2 - 3, this::reset);
    }
    private EditorButton button(String label, int bx, int by, int bw, Runnable action) {
        return addRenderableWidget(new EditorButton(label, bx, by, bw, action, () -> false));
    }
    private EditBox field(String label, int fx, int fy, int fw, String value, int max, java.util.function.Consumer<String> changed) {
        var box = new EditBox(font, fx, fy, fw, 20, Component.literal(label));
        box.setMaxLength(max); box.setValue(value); box.setHint(Component.literal(label)); box.setResponder(text -> { changed.accept(text); });
        return addRenderableWidget(box);
    }
    @Override public void tick() {
        if (draft == null && !offeredPicker) {
            offeredPicker = true; minecraft.setScreen(new InventoryPicker()); return;
        }
        if (restoreNameFocus && nameBox != null) {
            restoreNameFocus = false; setFocused(nameBox); nameBox.setFocused(true);
        }
        if (dyeTab != null) dyeTab.active = canDye();
        updateSelection();
    }
    private void updateSelection() {
        if (nameBox == null) return;
        if (nameBox.isFocused()) {
            anchor = ((EditBoxSelectionAccessor) nameBox).paintbrush$selectionAnchor(); cursor = nameBox.getCursorPosition();
        }
        boolean hasSelection = anchor != cursor;
        styleButtons.forEach(b -> b.active = hasSelection);
        if (colorSelection != null) colorSelection.active = hasSelection;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (modelField != null && modelField.isFocused() && (event.key() == 256 || event.key() == 258 || event.key() == 264 || event.key() == 265 || event.key() == 257)) {
            if (modelField.keyPressed(event)) return true;
        }
        return super.keyPressed(event);
    }
    private void select(ItemStack stack) {
        UUID id = SkyBlockUuid.read(stack); if (id == null) return;
        setItem(stack);
        error = ""; minecraft.setScreen(this);
    }
    private void setItem(ItemStack stack) {
        selected = stack.copy();
        draft = drafts.computeIfAbsent(SkyBlockUuid.read(stack), uuid -> new Draft(stack));
        if (tab == 1 && !canDye()) tab = 0;
    }
    private boolean canDye() {
        if (draft == null) return false;
        if (draft.model.isBlank()) return ColorOverrides.isDyeable(selected);
        Identifier id = Identifier.tryParse(draft.model);
        if (id == null) return false;
        return dyeModels.computeIfAbsent(id, ItemAppearance::hasDyeTint);
    }
    private void apply() {
        try {
            
            PaintBrushClient.requireReady(tab); UUID uuid = SkyBlockUuid.read(selected);
            if (tab == 0) {
                Identifier id = draft.model.isBlank() ? null : Identifier.tryParse(draft.model);
                if (!draft.model.isBlank() && (id == null || !availableModels().contains(id.toString())))
                    throw new IllegalArgumentException("Choose a model supplied by an active resource pack.");
                PaintBrushClient.models().set(uuid, id);
            } else if (tab == 1) {
                if (!canDye()) return;
                PaintBrushClient.colors().setValue(uuid, draft.dye);
            } else PaintBrushClient.names().setStyle(uuid, draft.name.styled());
            draft.dirty[tab] = false; error = "";
        } catch (Exception e) { error = e.getMessage(); }
    }
    private void reset() {
        try {
            
            PaintBrushClient.requireReady(tab); UUID uuid = SkyBlockUuid.read(selected);
            if (tab == 0) { PaintBrushClient.models().set(uuid, null); draft.model = ""; }
            else if (tab == 1) { PaintBrushClient.colors().set(uuid, null); draft.dye = ""; }
            else { PaintBrushClient.names().setStyle(uuid, null); draft.name = new NameDocument(null); draft.start = ""; draft.end = ""; }
            draft.dirty[tab] = false; error = ""; rebuildWidgets();
        } catch (Exception e) { error = e.getMessage(); }
    }
    private static Integer optionalColor(String value) {
        if (value.isBlank()) return null;
        Integer color = ColorOverrides.parseHex(value);
        if (color == null) throw new IllegalArgumentException("Use six hex digits, e.g. #FF88CC.");
        return color;
    }
    private List<String> availableModels() {
        if (models == null) models = minecraft.getResourceManager().listResources("items", id -> id.getPath().endsWith(".json"))
            .keySet().stream().map(id -> id.getNamespace() + ":" + id.getPath().substring(6, id.getPath().length() - 5)).sorted().toList();
        return models;
    }
    @Override protected void renderContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        updateSelection();
        g.fill(0, 0, viewWidth, viewHeight, 0x99000000);
        EditorTheme.panel(g, x, y, w, h);
        EditorTheme.panel(g, x + 5, y + 5, w - 10, 28);
        g.text(font, "Paint Brush", x + (w - font.width("Paint Brush")) / 2, y + 15, 0xffcccccc);
        EditorTheme.panel(g, x + 5, y + 37, w - 10, h - 42);
        EditorTheme.inset(g, left - 5, y + 101, contentWidth + 10, 105);
        if (draft == null) g.text(font, "Choose an item to customize", left, y + 112, 0xffcccccc);
        if (draft != null) {
            var preview = selected.copy(); preview.remove(DataComponents.CUSTOM_DATA);
            try {
                var id = ItemAppearance.previewModel(selected.get(DataComponents.ITEM_MODEL), draft.model);
                if (id != null) preview.set(DataComponents.ITEM_MODEL, id);
                var dyeValue = DyePresets.normalize(draft.dye);
                Integer rgb = dyeValue == null ? null : DyePresets.colorAt(dyeValue, System.currentTimeMillis()); if (rgb != null && canDye()) preview.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
                var name = draft.name.styled();
                if (name != null) preview.set(DataComponents.CUSTOM_NAME, name.render(selected.getOrDefault(DataComponents.CUSTOM_NAME, selected.getItemName()).getStyle()));
            } catch (IllegalArgumentException ignored) {}
            g.item(preview, left + 112, y + 44);
            g.enableScissor(left + 134, y + 42, x + w - 12, y + 64);
            g.text(font, preview.getHoverName(), left + 134, y + 48, -1); g.disableScissor();
            if (tab == 1) g.text(font, "Type to search · ↑/↓ browse · Tab selects", left, y + 184, 0xffaaaebd);
            if (tab == 2) g.text(font, "Selection color / gradient", left, y + 160, 0xffaaaebd);
        }
        renderWidgets(g, mx, my, delta);
        if (modelField != null) modelField.drawSuggestions(g);
        if (error != null && !error.isEmpty()) g.text(font, font.plainSubstrByWidth(error, contentWidth), left, y + h - 12, 0xffff8585);
    }
    @Override public boolean isPauseScreen() { return false; }
    boolean hasUnappliedEdits() { return drafts.values().stream().anyMatch(d -> d.dirty[0] || d.dirty[1] || d.dirty[2]); }
    void requestClose(Runnable close) {
        if (hasUnappliedEdits()) {
            minecraft.setScreen(new ConfirmScreen(discard -> {
                if (discard) close.run(); else minecraft.setScreen(this);
            }, Component.literal("Discard unapplied edits?"), Component.literal("Applied changes are already saved."),
                Component.literal("Discard edits"), Component.literal("Keep editing")));
        } else close.run();
    }
    @Override public void onClose() { requestClose(() -> minecraft.setScreen(parent)); }
    private static final class Draft {
        String model, dye, start = "", end = "";
        NameDocument name;
        final boolean[] dirty = new boolean[3];
        Draft(ItemStack stack) {
            var id = SkyBlockUuid.read(stack); var m = PaintBrushClient.models().get(id); model = m == null ? "" : m.toString();
            var c = PaintBrushClient.colors().getValue(id); dye = c == null ? "" : DyePresets.display(c);
            name = new NameDocument(PaintBrushClient.names().getStyle(id));
        }
    }
    private final class InventoryPicker extends CompactScreen {
        private final List<ItemStack> contents = new ArrayList<>();
        private final List<Slot> slots = new ArrayList<>();
        private int px, py;
        InventoryPicker() { super(Component.literal("Choose item")); }
        @Override protected void init() {
            updateViewport(); clearWidgets(); contents.clear(); slots.clear();
            px = (viewWidth - 260) / 2; py = (viewHeight - 204) / 2;
            if (minecraft.player != null) {
                var inventory = minecraft.player.getInventory();
                for (int i = 9; i < 36; i++) contents.add(inventory.getItem(i).copy());
                for (int i = 0; i < 9; i++) contents.add(inventory.getItem(i).copy());
                for (var slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) contents.add(minecraft.player.getItemBySlot(slot).copy());
            } else {
                for (int i = 0; i < 36; i++) contents.add(fixtures != null && i < fixtures.size() ? fixtures.get(i).copy() : ItemStack.EMPTY);
            }
            for (int i = 0; i < contents.size(); i++) {
                var stack = contents.get(i);
                int sx = px + 13 + i % 9 * 26;
                int sy = py + 32 + i / 9 * 26 + (i >= 27 ? 7 : 0) + (i >= 36 ? 7 : 0);
                var b = addRenderableWidget(new EditorButton(stack.getHoverName().getString(), sx, sy, 24, () -> select(stack), () -> false));
                b.setMessage(Component.empty()); b.active = SkyBlockUuid.read(stack) != null;
                slots.add(new Slot(stack, sx, sy, b.active));
            }
            addRenderableWidget(new EditorButton("Back", px + 196, py + 7, 50, this::onClose, () -> false));
        }
        @Override protected void renderContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
            g.fill(0, 0, viewWidth, viewHeight, 0x99000000); EditorTheme.panel(g, px, py, 260, 204);
            g.text(font, "Choose item", px + 13, py + 12, 0xff55ffff);
            renderWidgets(g, mx, my, delta);
            for (var slot : slots) {
                g.item(slot.stack, slot.x + 4, slot.y + 2);
                if (!slot.enabled) g.fill(slot.x, slot.y, slot.x + 24, slot.y + 20, 0x8820232e);
                if (!slot.stack.isEmpty() && mx >= slot.x && mx < slot.x + 24 && my >= slot.y && my < slot.y + 20) {
                    String label = slot.enabled ? slot.stack.getHoverName().getString() : "This item cannot be customized";
                    g.text(font, font.plainSubstrByWidth(label, 234), px + 13, py + 186, -1);
                }
            }
        }
        @Override public void onClose() { minecraft.setScreen(PaintBrushScreen.this); }
        @Override public boolean isPauseScreen() { return false; }
        private record Slot(ItemStack stack, int x, int y, boolean enabled) {}
    }
}

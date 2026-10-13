package dev.eviemod.features.experiments;

import dev.eviemod.compat.ClientUi;
import dev.eviemod.features.skyblock.SkyblockContext;
import dev.eviemod.paintbrush.EviemodSettings;
import dev.eviemod.paintbrush.ImportedFeatures;
import dev.eviemod.paintbrush.mixin.NavigationContainerAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Narrow Minecraft adapter: server observations, local drawing and manual click cancellation only. */
public final class ExperimentClient {
    private static ContainerScreen screen;
    private static ExperimentSession session;
    private static ExperimentSession.Game game;
    private static Object connection, level;
    private static final List<ItemStack> identities = new ArrayList<>();
    private static final Map<Integer, ItemStack> rememberedStacks = new HashMap<>();
    private static final ExperimentInventory inventory = new ExperimentInventory();
    private ExperimentClient() {}
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> ensureSession());
        ScreenEvents.AFTER_INIT.register((client, current, width, height) -> {
            ensureSession();
            ScreenEvents.afterExtract(current).register((drawn, graphics, mouseX, mouseY, delta) -> {
                if (drawn != screen || !ensureSession()) return;
                var geometry = (NavigationContainerAccessor)screen;
                for (var slot : screen.getMenu().slots) {
                    if (session.remembered(slot.index) == null) continue;
                    int x = geometry.eviemod$navigationLeft() + slot.x, y = geometry.eviemod$navigationTop() + slot.y;
                    if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                        var stack = rememberedStacks.get(slot.index);
                        if (stack != null) graphics.setTooltipForNextFrame(client.font, stack, mouseX, mouseY);
                    }
                }
            });
            ScreenEvents.remove(current).register(removed -> { if (removed == screen) reset(); });
        });
    }
    private static boolean enabled(ExperimentSession.Game kind, ImportedFeatures.Experiments settings) {
        return switch (kind) {
            case CHRONOMATRON -> settings.chronomatron;
            case ULTRASEQUENCER -> settings.ultrasequencer;
            case SUPERPAIRS -> settings.superpairs;
        };
    }
    private static boolean ensureSession() {
        var settings = EviemodSettings.features().experiments;
        if (!settings.chronomatron && !settings.ultrasequencer && !settings.superpairs) { reset(); return false; }
        var client = Minecraft.getInstance();
        var current = ClientUi.screen(client);
        if (!(current instanceof ContainerScreen container) || container.getMenu().getRowCount() != 6
            || client.player == null || client.level == null || client.player.isCreative()
            || !SkyblockContext.isHypixelServer(client) || !SkyblockContext.isActiveSkyblock()) { reset(); return false; }
        var kind = screen == container ? game : ExperimentMenu.game(current.getTitle().getString());
        if (kind == null || !enabled(kind, settings)) { reset(); return false; }
        if (screen != container || connection != client.getConnection() || level != client.level || game != kind) {
            reset(); screen = container; game = kind; session = new ExperimentSession(kind);
            connection = client.getConnection(); level = client.level;
        }
        return true;
    }
    private static void reset() {
        screen = null; session = null; game = null; connection = level = null;
        identities.clear(); rememberedStacks.clear();
        inventory.clear();
    }
    /** Called after vanilla applies a server inventory packet, never after client click prediction. */
    private static void observeServer() {
        var cells = new ArrayList<ExperimentSession.Cell>();
        for (int slot = 0; slot < 54; slot++) {
            var stack = inventory.get(slot);
            var cell = ExperimentSession.Cell.OTHER;
            if (slot >= 9 && slot < 45) {
                if (game == ExperimentSession.Game.CHRONOMATRON) {
                    String note = ExperimentMenu.note(stack);
                    if (!note.isEmpty()) cell = new ExperimentSession.Cell(ExperimentSession.Kind.NOTE, note, 0, stack.hasFoil(), false);
                } else if (game == ExperimentSession.Game.ULTRASEQUENCER) {
                    int number = ExperimentMenu.number(stack);
                    if (number > 0) cell = new ExperimentSession.Cell(ExperimentSession.Kind.NUMBER, "", number, false, false);
                    else if (isPlayablePane(stack)) cell = new ExperimentSession.Cell(ExperimentSession.Kind.HIDDEN, "", 0, false, false);
                } else if (ExperimentMenu.hidden(stack)) {
                    cell = new ExperimentSession.Cell(ExperimentSession.Kind.HIDDEN, "", 0, false,
                        ExperimentMenu.name(stack).equals("Click a second button!"));
                } else if (ExperimentMenu.reward(stack)) {
                    cell = new ExperimentSession.Cell(ExperimentMenu.powerup(stack) ? ExperimentSession.Kind.POWERUP : ExperimentSession.Kind.REWARD,
                        identity(stack), 0, false, false);
                    rememberedStacks.put(slot, stack.copy());
                }
            }
            cells.add(cell);
        }
        session.observe(new ExperimentSession.Board(ExperimentMenu.phase(game, inventory.get(49)),
            ExperimentMenu.round(inventory.get(4)), cells));
        // Keep only memories still held by the state machine, including board-change invalidation.
        rememberedStacks.entrySet().removeIf(entry -> session.hidden(entry.getKey()) && session.remembered(entry.getKey()) == null);
    }
    public static void observeSlot(int containerId, int slot, ItemStack stack) {
        if (ensureSession() && screen.getMenu().containerId == containerId && inventory.setSlot(slot, stack)) observeServer();
    }
    public static void observeContents(int containerId, List<ItemStack> items) {
        if (!ensureSession() || screen.getMenu().containerId != containerId) return;
        if (inventory.setContents(items)) observeServer();
        else { session.clear(); identities.clear(); rememberedStacks.clear(); }
    }
    private static boolean isPlayablePane(ItemStack stack) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("minecraft") && id.getPath().endsWith("_stained_glass_pane")
            && !id.getPath().equals("black_stained_glass_pane");
    }
    private static String identity(ItemStack stack) {
        for (int i = 0; i < identities.size(); i++) if (ItemStack.matches(stack, identities.get(i))) return Integer.toString(i);
        identities.add(stack.copy()); return Integer.toString(identities.size() - 1);
    }
    public static boolean cancelClick(AbstractContainerScreen<?> current, Slot slot, int slotId, int button, ContainerInput input) {
        if (!ensureSession() || current != screen || slot == null || slot.index != slotId || slotId < 9 || slotId >= 45) return false;
        if (input != ContainerInput.PICKUP || (button != 0 && button != 1)) {
            // Other input modes have different semantics. Pass through and stop guiding until a fresh round.
            session.clear(); return false;
        }
        return session.click(slotId, EviemodSettings.features().experiments.protectIncorrectClicks);
    }
    public static void draw(AbstractContainerScreen<?> current, GuiGraphicsExtractor graphics, Slot slot) {
        if (current != screen || !ensureSession() || slot.index < 9 || slot.index >= 45) return;
        int id = slot.index;
        if (session.remembered(id) != null) {
            var remembered = rememberedStacks.get(id);
            if (remembered != null) {
                graphics.item(remembered, slot.x, slot.y, id);
                graphics.itemDecorations(Minecraft.getInstance().font, remembered, slot.x, slot.y);
            }
        }
        int number = session.number(id);
        if (number > 0) graphics.text(Minecraft.getInstance().font, Integer.toString(number), slot.x + 1, slot.y + 1, 0xffffffff, true);
        int color = session.next(id) ? 0xff55ff55 : session.knownMatch(id) ? 0xffffdd55 : 0;
        if (color != 0) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 1, color);
            graphics.fill(slot.x, slot.y + 15, slot.x + 16, slot.y + 16, color);
            graphics.fill(slot.x, slot.y, slot.x + 1, slot.y + 16, color);
            graphics.fill(slot.x + 15, slot.y, slot.x + 16, slot.y + 16, color);
        }
    }
}

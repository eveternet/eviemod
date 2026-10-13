package dev.eviemod.features.experiments;

import dev.eviemod.compat.ClientUi;
import dev.eviemod.features.skyblock.SkyblockContext;
import dev.eviemod.paintbrush.EviemodSettings;
import dev.eviemod.paintbrush.ImportedFeatures;
import dev.eviemod.paintbrush.SkyBlockSession;
import dev.eviemod.paintbrush.mixin.NavigationContainerAccessor;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
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
    private static int acceptedPackets, drawPasses, drawnSlots;
    private static String lastDiagnostic = "No experiment session has been observed.";
    private static String lastReplayDiagnostic = "No replay phase was observed.";
    private static Object diagnosticConnection, diagnosticLevel;
    private static WeakReference<ContainerScreen> candidateScreen = new WeakReference<>(null);
    private static final ArrayDeque<String> candidateHistory = new ArrayDeque<>();
    private static String candidateDiagnostic = "";
    private static int candidatePackets;
    private static boolean candidateApi, candidateScoreboard;
    private static final List<ItemStack> identities = new ArrayList<>();
    private static final Map<Integer, ItemStack> rememberedStacks = new HashMap<>();
    private static final ExperimentInventory inventory = new ExperimentInventory();
    private ExperimentClient() {}
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> ensureSession());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { reset(); clearDiagnostics(); });
        ScreenEvents.AFTER_INIT.register((client, current, width, height) -> {
            ensureSession();
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
        var client = Minecraft.getInstance();
        refreshDiagnosticContext(client);
        var settings = EviemodSettings.features().experiments;
        if (!settings.chronomatron && !settings.ultrasequencer && !settings.superpairs) { reset(); return false; }
        var current = ClientUi.screen(client);
        captureCandidate(client, current, false);
        if (!(current instanceof ContainerScreen container) || container.getMenu().getRowCount() != 6
            || client.player == null || client.level == null || client.player.isCreative()
            || !SkyblockContext.isHypixelServer(client)
            || !(SkyBlockSession.active() || SkyblockContext.isActiveSkyblock())) { reset(); return false; }
        var kind = screen == container ? game : ExperimentMenu.game(current.getTitle().getString());
        if (kind == null || !enabled(kind, settings)) { reset(); return false; }
        if (screen != container || connection != client.getConnection() || level != client.level || game != kind) {
            reset(); screen = container; game = kind; session = new ExperimentSession(kind);
            lastReplayDiagnostic = "No replay phase was observed.";
            connection = client.getConnection(); level = client.level;
        }
        return true;
    }
    private static void reset() {
        if (session != null) {
            lastDiagnostic = sessionDiagnostic() + "\nLast replay: " + lastReplayDiagnostic;
        }
        screen = null; session = null; game = null; connection = level = null;
        acceptedPackets = drawPasses = drawnSlots = 0;
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
        var phase = ExperimentMenu.phase(game, inventory.get(49));
        if (phase == ExperimentSession.Phase.REPLAY || phase == ExperimentSession.Phase.PAIRS)
            lastReplayDiagnostic = sessionDiagnostic();
        // Keep only memories still held by the state machine, including board-change invalidation.
        rememberedStacks.entrySet().removeIf(entry -> session.hidden(entry.getKey()) && session.remembered(entry.getKey()) == null);
    }
    public static void observeSlot(int containerId, int slot, ItemStack stack) {
        boolean active = ensureSession();
        if (slot >= 0 && slot < 54) captureCandidatePacket(containerId);
        if (active && screen.getMenu().containerId == containerId && inventory.setSlot(slot, stack)) {
            acceptedPackets++; observeServer();
        }
    }
    public static void observeContents(int containerId, List<ItemStack> items) {
        boolean active = ensureSession();
        captureCandidatePacket(containerId);
        if (!active || screen.getMenu().containerId != containerId) return;
        if (inventory.setContents(items)) { acceptedPackets++; observeServer(); }
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
        if (!ExperimentMenu.manualClick(input, button)) {
            // Other input modes have different semantics. Pass through and stop guiding until a fresh round.
            session.clear(); return false;
        }
        return session.click(slotId, EviemodSettings.features().experiments.protectIncorrectClicks);
    }
    /** Container render tail: after slot drawing, before Screen flushes deferred tooltips. */
    public static void draw(AbstractContainerScreen<?> current, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!ensureSession() || current != screen) return;
        drawPasses++;
        var geometry = (NavigationContainerAccessor)screen;
        int left = geometry.eviemod$navigationLeft(), top = geometry.eviemod$navigationTop();
        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate(left, top);
        for (var slot : screen.getMenu().slots) {
            if (!slot.isActive() || slot.index < 9 || slot.index >= 45) continue;
            if (drawSlot(graphics, slot)) drawnSlots++;
            if (session.remembered(slot.index) == null) continue;
            int x = left + slot.x, y = top + slot.y;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                var stack = rememberedStacks.get(slot.index);
                if (stack != null) graphics.setTooltipForNextFrame(Minecraft.getInstance().font, stack, mouseX, mouseY);
            }
        }
        graphics.pose().popMatrix();
    }
    private static boolean drawSlot(GuiGraphicsExtractor graphics, Slot slot) {
        int id = slot.index;
        boolean drawn = false;
        if (session.remembered(id) != null) {
            var remembered = rememberedStacks.get(id);
            if (remembered != null) {
                graphics.item(remembered, slot.x, slot.y, id);
                graphics.itemDecorations(Minecraft.getInstance().font, remembered, slot.x, slot.y);
                drawn = true;
            }
        }
        int number = session.number(id);
        if (number > 0) { graphics.text(Minecraft.getInstance().font, Integer.toString(number), slot.x + 1, slot.y + 1, 0xffffffff, true); drawn = true; }
        int color = session.next(id) ? 0xff55ff55 : session.knownMatch(id) ? 0xffffdd55 : 0;
        if (color != 0) {
            drawn = true;
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 1, color);
            graphics.fill(slot.x, slot.y + 15, slot.x + 16, slot.y + 16, color);
            graphics.fill(slot.x, slot.y, slot.x + 1, slot.y + 16, color);
            graphics.fill(slot.x + 15, slot.y, slot.x + 16, slot.y + 16, color);
        }
        return drawn;
    }
    /** Explicit troubleshooting only. Includes menu protocol/gates, never chat or the player's inventory. */
    public static String diagnosticReport() {
        var client = Minecraft.getInstance();
        refreshDiagnosticContext(client);
        var current = ClientUi.screen(client);
        captureCandidate(client, current, true);
        var settings = EviemodSettings.features().experiments;
        String gate = "active";
        var kind = current == null ? null : ExperimentMenu.game(current.getTitle().getString());
        if (!settings.chronomatron && !settings.ultrasequencer && !settings.superpairs) gate = "all helpers disabled";
        else if (client.player == null || client.level == null) gate = "no player/world";
        else if (client.player.isCreative()) gate = "creative mode";
        else if (!SkyblockContext.isHypixelServer(client)) gate = "connection is not Hypixel";
        else if (!(SkyBlockSession.active() || SkyblockContext.isActiveSkyblock())) gate = "SkyBlock context inactive";
        else if (!(current instanceof ContainerScreen container) || container.getMenu().getRowCount() != 6) gate = "not a six-row container";
        else if (kind == null) gate = "unrecognized experiment title";
        else if (!enabled(kind, settings)) gate = "helper disabled for this experiment";
        String currentMenu = current == null ? "none" : current.getClass().getName() + " title=" + quoted(current.getTitle().getString());
        if (current instanceof AbstractContainerScreen<?> container) currentMenu += " container=" + container.getMenu().containerId;
        String observed = session != null ? sessionDiagnostic() + "\nLast replay: " + lastReplayDiagnostic
            : "Last closed session: " + lastDiagnostic;
        var candidates = new ArrayList<>(candidateHistory);
        if (!candidateDiagnostic.isEmpty()) candidates.add(candidateDiagnostic);
        return "Experiments: chronomatron=" + settings.chronomatron + ", ultrasequencer=" + settings.ultrasequencer
            + ", superpairs=" + settings.superpairs + ", protection=" + settings.protectIncorrectClicks
            + "; API SkyBlock=" + SkyBlockSession.active() + ", scoreboard SkyBlock=" + SkyblockContext.isActiveSkyblock()
            + "; gate=" + gate + "; screen=" + currentMenu + "\n" + observed
            + (candidates.isEmpty() ? "" : "\n" + String.join("\n", candidates));
    }
    private static void refreshDiagnosticContext(Minecraft client) {
        if (diagnosticConnection == client.getConnection() && diagnosticLevel == client.level) return;
        reset(); clearDiagnostics();
        diagnosticConnection = client.getConnection(); diagnosticLevel = client.level;
    }
    private static void clearDiagnostics() {
        lastDiagnostic = "No experiment session has been observed.";
        lastReplayDiagnostic = "No replay phase was observed.";
        diagnosticConnection = diagnosticLevel = null;
        candidateScreen.clear(); candidateHistory.clear(); candidateDiagnostic = ""; candidatePackets = 0;
    }
    /** Diagnostic-only live slots. These are never fed to ExperimentInventory or ExperimentSession. */
    private static void captureCandidate(Minecraft client, Screen current, boolean refresh) {
        if (!(current instanceof ContainerScreen container) || container.getMenu().getRowCount() != 6
                || client.player == null || client.level == null || !SkyblockContext.isHypixelServer(client)) return;
        var settings = EviemodSettings.features().experiments;
        if (!settings.chronomatron && !settings.ultrasequencer && !settings.superpairs) return;
        boolean api = SkyBlockSession.active(), scoreboard = SkyblockContext.isActiveSkyblock();
        if (candidateScreen.get() != container) {
            if (!candidateDiagnostic.isEmpty()) candidateHistory.addLast(candidateDiagnostic);
            while (candidateHistory.size() > 3) candidateHistory.removeFirst();
            candidateScreen = new WeakReference<>(container); candidatePackets = 0;
            refresh = true;
        }
        if (!refresh && candidateApi == api && candidateScoreboard == scoreboard) return;
        candidateApi = api; candidateScoreboard = scoreboard;
        candidateDiagnostic = "Live menu candidate " + container.getClass().getName() + " title=" + quoted(container.getTitle().getString())
            + " container=" + container.getMenu().containerId + "; API SkyBlock=" + api + ", scoreboard SkyBlock=" + scoreboard
            + ", parsed game=" + ExperimentMenu.game(container.getTitle().getString()) + ", matching packets=" + candidatePackets
            + "; slot4=" + protocolItem(container.getMenu().getSlot(4).getItem())
            + "; slot49=" + protocolItem(container.getMenu().getSlot(49).getItem());
    }
    private static void captureCandidatePacket(int containerId) {
        var client = Minecraft.getInstance();
        var container = candidateScreen.get();
        if (container == null || ClientUi.screen(client) != container || container.getMenu().containerId != containerId) return;
        candidatePackets++; captureCandidate(client, container, true);
    }
    private static String sessionDiagnostic() {
        return "Observed " + game + " title=" + quoted(screen.getTitle().getString()) + " container=" + screen.getMenu().containerId
            + "; packets=" + acceptedPackets + ", overlay passes=" + drawPasses + ", drawn slots=" + drawnSlots
            + "; slot4=" + protocolItem(inventory.get(4)) + "; slot49=" + protocolItem(inventory.get(49))
            + "; " + session.diagnostic();
    }
    private static String protocolItem(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()) + " name=" + quoted(ExperimentMenu.name(stack));
    }
    private static String quoted(String value) {
        String bounded = value.replace('\n', ' ').replace('\r', ' ').replace('"', '\'');
        return '"' + bounded.substring(0, Math.min(bounded.length(), 120)) + '"';
    }
}

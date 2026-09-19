package dev.eviemod.paintbrush;

import dev.eviemod.features.scoresync.ScoreSyncClient;
import net.azureaaron.dandelion.deps.moulconfig.gui.GuiElementComponent;
import net.azureaaron.dandelion.deps.moulconfig.gui.MoulConfigEditor;
import net.azureaaron.dandelion.deps.moulconfig.platform.MoulConfigScreenComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Real offline client and shared UI, with no Noamm classes on the runtime classpath. */
final class NoammAbsenceSmokeTest {
    private int ticks;
    void start() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() != null || ++ticks < 20) return;
            if (FabricLoader.getInstance().isModLoaded("noammaddons") || ScoreSyncClient.available())
                throw new AssertionError("Noamm must be absent and unavailable");
            if (EviemodSettings.features().skyblock.noammScoreSync)
                throw new AssertionError("Fresh settings must default off");
            // A saved opt-in from a previous session cannot expose an enableable control without Noamm.
            EviemodSettings.features().skyblock.noammScoreSync = true;
            client.setScreen(EviemodSettings.screen(client.screen));
            var screen = (MoulConfigScreenComponent) client.screen;
            var editor = (MoulConfigEditor<?>) ((GuiElementComponent) screen.getGuiContext().getRoot()).getElement();
            for (var option : editor.getAllOptions()) {
                try {
                    if (option.getDebugDeclarationLocation().equals("eviemod:commands/noamm_score_sync"))
                        throw new AssertionError("Absent Noamm exposed a score-sync control");
                } catch (UnsupportedOperationException ignored) { /* Library labels have no ID. */ }
            }
            EviemodSettings.features().skyblock.noammScoreSync = false;
            if (Thread.getAllStackTraces().keySet().stream().anyMatch(thread -> thread.getName().equals("eviemod-score-relay")))
                throw new AssertionError("Absent Noamm started relay work");
            org.slf4j.LoggerFactory.getLogger("eviemod-fixture").info("NOAMM_ABSENCE_SMOKE_PASSED: startup, default off, hidden control, no relay timer");
            client.stop();
        });
    }
}

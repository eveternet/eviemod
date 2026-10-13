package dev.eviemod.paintbrush;

import dev.eviemod.features.skyblock.SkyblockContext;
import dev.eviemod.paintbrush.mixin.NavigationContainerAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;

/** All version-sensitive screen/input access stays here and in the geometry accessor. */
final class QuickNavigationClient {
    private static final QuickNavigationClicks CLICKS = new QuickNavigationClicks();
    static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof AbstractContainerScreen<?> container && active(client))
                install(container, EviemodSettings.STORE.values().quickNavigation, () -> active(client),
                    command -> client.getConnection().sendCommand(command));
        });
    }
    private static boolean active(Minecraft client) {
        return QuickNavigationLayout.allowed(EviemodSettings.STORE.values().quickNavigation.enabled,
            SkyBlockSession.active() || SkyblockContext.isActiveSkyblock(), client.player != null,
            client.player != null && client.player.isCreative()) && client.getConnection() != null
            && SkyblockContext.isHypixelServer(client);
    }
    static List<QuickNavigationTab> install(AbstractContainerScreen<?> screen, QuickNavigationSettings settings,
            BooleanSupplier active, Consumer<String> send) {
        CLICKS.disarm();
        var widgets = new ArrayList<QuickNavigationTab>();
        String title = screen.getTitle().getString().strip();
        for (int i = 0; i < settings.tabs.size(); i++) {
            var config = settings.tabs.get(i);
            if (config.hidden || config.empty()) continue;
            // An unsupported icon affects only its own button and never changes an inventory stack.
            try { QuickNavigationIcons.parse(config); }
            catch (RuntimeException e) { continue; }
            int index = i;
            boolean matching = !config.title.isEmpty() && Pattern.compile(config.title).matcher(title).matches();
            var widget = new QuickNavigationTab(index, () -> config, () -> bounds(screen, index), () -> matching,
                () -> {
                    if (!active.getAsBoolean() || !screen.getMenu().getCarried().isEmpty()) return;
                    String command = QuickNavigationSettings.normalizedCommand(config.command);
                    if (!command.isEmpty() && CLICKS.activate(index, config.activation, Util.getMillis())) send.accept(command);
                });
            widget.tooltip(label(config));
            widgets.add(widget);
            Screens.getWidgets(screen).add(widget);
        }
        ScreenEvents.beforeExtract(screen).register((current, graphics, mx, my, delta) -> {
            boolean shown = active.getAsBoolean();
            for (var widget : widgets) {
                widget.visible = shown; widget.active = shown;
                widget.updateBounds();
            }
        });
        ScreenEvents.afterTick(screen).register(current -> {
            for (var widget : widgets) {
                widget.tooltip(CLICKS.armed(widget.index(), Util.getMillis()) ? "Click again to confirm" : label(widget.config()));
            }
        });
        boolean[] consumeRelease = {false};
        ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> {
            if (!active.getAsBoolean()) return true;
            for (var widget : widgets) {
                if (!widget.isMouseOver(event.x(), event.y())) continue;
                consumeRelease[0] = true;
                // Consume other mouse buttons and held-stack clicks too, so vanilla cannot drop an item.
                if (event.button() == 0 && screen.getMenu().getCarried().isEmpty()) {
                    screen.setFocused(widget); widget.mouseClicked(event, false);
                }
                return false;
            }
            CLICKS.disarm(); return true;
        });
        ScreenMouseEvents.allowMouseRelease(screen).register((current, event) -> {
            if (consumeRelease[0]) { consumeRelease[0] = false; return false; }
            if (active.getAsBoolean()) for (var widget : widgets)
                if (widget.isMouseOver(event.x(), event.y())) return false;
            return true;
        });
        ScreenEvents.remove(screen).register(current -> CLICKS.disarm());
        return widgets;
    }
    private static String label(QuickNavigationSettings.Tab tab) { return tab.tooltip.isBlank() ? tab.command : tab.tooltip; }
    private static QuickNavigationLayout.Bounds bounds(AbstractContainerScreen<?> screen, int index) {
        var geometry = (NavigationContainerAccessor)screen;
        int height = geometry.eviemod$navigationHeight() - (screen instanceof ContainerScreen ? 1 : 0);
        return QuickNavigationLayout.tab(index, geometry.eviemod$navigationLeft(), geometry.eviemod$navigationTop(),
            geometry.eviemod$navigationWidth(), height);
    }
}

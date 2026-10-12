package dev.eviemod.paintbrush;

import dev.eviemod.compat.ClientUi;
import java.util.ArrayList;
import java.util.List;
import net.azureaaron.dandelion.deps.moulconfig.gui.GuiElementComponent;
import net.azureaaron.dandelion.deps.moulconfig.gui.MoulConfigEditor;
import net.azureaaron.dandelion.deps.moulconfig.platform.MoulConfigScreenComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Real settings/editor interaction, native tab rendering and Fabric input guards, without a server. */
final class QuickNavigationSmokeTest {
    private int ticks;
    private Screen settings;
    private ModSettings.Values original;
    private int originalScale;
    private RuntimePreview runtime;
    private final List<String> sent = new ArrayList<>();
    void start(Screen parent) {
        var client = Minecraft.getInstance();
        original = EviemodSettings.STORE.values().copy();
        originalScale = client.options.guiScale().get();
        client.options.guiScale().set(3); client.resizeDisplay();
        settings = EviemodSettings.screen(parent); ClientUi.setScreen(client, settings);
        var editor = editor();
        var category = editor.getAllCategories().values().stream()
            .filter(value -> value.getIdentifier().equals("eviemod:quick_navigation")).findFirst().orElseThrow();
        editor.setSelectedCategory(category);
        var options = editor.getAllOptions();
        check(!(Boolean)options.stream().filter(value -> optionId(value).equals("eviemod:quick_navigation/enabled"))
            .findFirst().orElseThrow().get(), "Feature defaults off in actual UI");
        ((Runnable)options.stream().filter(value -> optionId(value).equals("eviemod:quick_navigation/editor"))
            .findFirst().orElseThrow().get()).run();
        check(ClientUi.screen(client) instanceof QuickNavigationScreen, "Settings action opens editor while disabled");
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick(mc, parent));
    }
    private void tick(Minecraft client, Screen parent) {
        ticks++;
        if (ticks == 5) {
            var screen = ClientUi.screen(client);
            var command = field(screen, "Command"); command.setValue("");
            clickEditor(screen, command);
            "/warp garden".codePoints().forEach(cp -> screen.charTyped(new CharacterEvent(cp)));
            check(command.getValue().equals("/warp garden"), "Native command input receives typing");
            field(screen, "Menu title pattern").setValue("["); check(!widget(screen, "Done").active, "Bad pattern blocks saving");
            field(screen, "Menu title pattern").setValue("Garden.*"); check(widget(screen, "Done").active, "Valid pattern recovers");
            field(screen, "Item model").setValue("minecraft:missing_item"); check(!widget(screen, "Done").active, "Bad icon blocks saving");
            field(screen, "Item model").setValue("minecraft:apple");
            clickEditor(screen, widget(screen, "Hide tab"));
            var second = screen.children().stream().filter(child -> child instanceof QuickNavigationTab tab && tab.index() == 1)
                .map(child -> (QuickNavigationTab)child).findFirst().orElseThrow();
            clickEditor(screen, second);
            check(field(screen, "Command").getValue().equals("/collection"), "Selecting preview tab loads its fields");
            screen.resize(screen.width, screen.height);
            check(field(screen, "Command").getValue().equals("/collection"), "Resize retains selected tab");
        }
        if (ticks == 10) capture(client, "quick-navigation-editor.png");
        if (ticks == 15) {
            ClientUi.screen(client).onClose(); check(ClientUi.screen(client) == settings, "Done returns to originating settings");
            var saved = EviemodSettings.STORE.values().quickNavigation;
            check(!saved.enabled && saved.tabs.get(0).hidden, "Configuring a tab does not enable background feature");
            check(saved.tabs.get(0).command.equals("/warp garden") && saved.tabs.get(0).icon.equals("minecraft:apple"), "Editor edits persist across tab switch/resize");
            settings.onClose(); check(ClientUi.screen(client) == parent, "Settings return to parent");
            check(EviemodSettings.STORE.values().quickNavigation.tabs.get(0).hidden, "Parent save preserves editor edits");
            runtime = new RuntimePreview(166, sent); ClientUi.setScreen(client, runtime);
            check(runtime.buttons.size() == 14, "All runtime default tabs installed");
            var tab = runtime.buttons.get(0);
            clickRuntime(runtime.container, tab, 1); check(sent.isEmpty(), "Right click never sends a command");
            runtime.container.getMenu().setCarried(new ItemStack(Items.DIAMOND));
            var before = runtime.container.getMenu().getCarried();
            clickRuntime(runtime.container, tab, 0); check(sent.isEmpty(), "Held item never sends a command");
            check(runtime.container.getMenu().getCarried() == before, "Held stack is untouched");
            runtime.container.getMenu().setCarried(ItemStack.EMPTY);
            clickRuntime(runtime.container, tab, 0); check(sent.equals(List.of("skills")), "Click sends one normalized command");
            clickRuntime(runtime.container, runtime.buttons.get(1), 0); check(sent.size() == 1, "Shared cooldown suppresses a second command");
            check(ScreenMouseEvents.allowMouseClick(runtime.container).invoker().allowMouseClick(runtime.container,
                new MouseButtonEvent(1, 1, new MouseButtonInfo(0, 0))), "Unrelated clicks remain available to vanilla");
        }
        if (ticks == 25) capture(client, "quick-navigation-inventory.png");
        if (ticks == 40) {
            runtime = new RuntimePreview(222, sent); ClientUi.setScreen(client, runtime);
            var warp = runtime.buttons.stream().filter(tab -> tab.index() == 7).findFirst().orElseThrow();
            clickRuntime(runtime.container, warp, 0); check(sent.size() == 1, "Warp requires confirmation");
            clickRuntime(runtime.container, warp, 0); check(sent.equals(List.of("skills", "is")), "Second click confirms warp");
            runtime.allowed = false;
            warp.onClick(new MouseButtonEvent(0, 0, new MouseButtonInfo(0, 0)), false);
            check(sent.size() == 2, "Runtime action rechecks feature/session gate"); runtime.allowed = true;
        }
        if (ticks == 50) capture(client, "quick-navigation-chest.png");
        if (ticks == 55) {
            try { EviemodSettings.STORE.save(original); } catch (java.io.IOException e) { throw new AssertionError(e); }
            client.options.guiScale().set(originalScale); client.resizeDisplay();
            org.slf4j.LoggerFactory.getLogger("eviemod-fixture").info("EVIEMOD_QUICK_NAVIGATION_SMOKE_OK");
            org.slf4j.LoggerFactory.getLogger("eviemod-fixture").info("EVIEMOD_VERSION_SMOKE_OK");
            client.stop();
        }
    }
    private static MoulConfigEditor<?> editor() {
        var screen = (MoulConfigScreenComponent)ClientUi.screen(Minecraft.getInstance());
        return (MoulConfigEditor<?>)((GuiElementComponent)screen.getGuiContext().getRoot()).getElement();
    }
    private static String optionId(net.azureaaron.dandelion.deps.moulconfig.processor.ProcessedOption option) {
        try { return option.getDebugDeclarationLocation(); }
        catch (UnsupportedOperationException e) { return ""; }
    }
    private static EditBox field(Screen screen, String label) { return (EditBox)widget(screen, label); }
    private static AbstractWidget widget(Screen screen, String label) {
        return screen.children().stream().filter(child -> child instanceof AbstractWidget w && w.getMessage().getString().equals(label))
            .map(child -> (AbstractWidget)child).findFirst().orElseThrow(() -> new AssertionError("Missing " + label));
    }
    private static void clickEditor(Screen screen, AbstractWidget widget) {
        var viewport = EditorViewport.fit(screen.width, screen.height, Minecraft.getInstance().getWindow().getGuiScale());
        var event = new MouseButtonEvent((widget.getX() + 3) * viewport.scale(), (widget.getY() + 3) * viewport.scale(), new MouseButtonInfo(0, 0));
        screen.mouseClicked(event, false); screen.mouseReleased(event);
    }
    private static void clickRuntime(AbstractContainerScreen<?> screen, QuickNavigationTab tab, int button) {
        tab.updateBounds();
        var event = new MouseButtonEvent(tab.getX() + 5, tab.getY() + 5, new MouseButtonInfo(button, 0));
        check(!ScreenMouseEvents.allowMouseClick(screen).invoker().allowMouseClick(screen, event), "Tab click consumed");
        check(!ScreenMouseEvents.allowMouseRelease(screen).invoker().allowMouseRelease(screen, event), "Tab release consumed");
    }
    private static void capture(Minecraft client, String name) {
        net.minecraft.client.Screenshot.grab(client.gameDirectory, name, ClientUi.mainRenderTarget(client), 1, message -> {});
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    /** Render a real transformed container without ticking a missing offline player. */
    private static final class RuntimePreview extends Screen {
        private final FixtureContainer container;
        private final List<String> sent;
        private List<QuickNavigationTab> buttons;
        private boolean allowed = true;
        RuntimePreview(int height, List<String> sent) {
            super(Component.literal("Navigation fixture")); this.sent = sent; container = new FixtureContainer(height);
        }
        @Override protected void init() {
            container.init(width, height);
            buttons = QuickNavigationClient.install(container, new QuickNavigationSettings(), () -> allowed, sent::add);
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            ScreenEvents.beforeExtract(container).invoker().beforeExtract(container, g, mx, my, delta);
            container.extractRenderStateWithTooltipAndSubtitles(g, mx, my, delta);
        }
    }
    private static final class FixtureContainer extends AbstractContainerScreen<AbstractContainerMenu> {
        FixtureContainer(int height) {
            super(new AbstractContainerMenu(null, 0) {
                @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
                @Override public boolean stillValid(Player player) { return true; }
            }, new Inventory(null, new net.minecraft.world.entity.EntityEquipment()), Component.literal("Your Skills"), 176, height);
        }
        @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
            var texture = Identifier.withDefaultNamespace("textures/gui/container/" + (imageHeight == 166 ? "inventory.png" : "generic_54.png"));
            if (imageHeight == 166) g.blit(RenderPipelines.GUI_TEXTURED, texture, leftPos, topPos, 0, 0, 176, 166, 256, 256);
            else {
                g.blit(RenderPipelines.GUI_TEXTURED, texture, leftPos, topPos, 0, 0, 176, 125, 256, 256);
                g.blit(RenderPipelines.GUI_TEXTURED, texture, leftPos, topPos + 125, 0, 126, 176, 96, 256, 256);
            }
        }
    }
}

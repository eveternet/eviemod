package dev.eviemod.paintbrush;

import java.util.List;
import net.azureaaron.dandelion.api.Option;
import net.azureaaron.dandelion.api.controllers.BooleanController;
import net.azureaaron.dandelion.deps.moulconfig.gui.GuiComponent;
import net.azureaaron.dandelion.deps.moulconfig.gui.HorizontalAlign;
import net.azureaaron.dandelion.deps.moulconfig.gui.VerticalAlign;
import net.azureaaron.dandelion.deps.moulconfig.gui.component.*;
import net.azureaaron.dandelion.deps.moulconfig.gui.editors.ComponentEditor;
import net.azureaaron.dandelion.deps.moulconfig.observer.GetSetter;
import net.azureaaron.dandelion.deps.moulconfig.processor.ProcessedOption;
import net.azureaaron.dandelion.impl.moulconfig.MoulConfigDefinition;

/** Dandelion's BooleanController only exposes full cards in this backport.
 * Compose MoulConfig's standard row, text, hover and switch components instead. */
final class CompactToggleController implements BooleanController {
    static final CompactToggleController INSTANCE = new CompactToggleController();
    static final int HEIGHT = 22;
    @Override public BooleanStyle style() { return BooleanStyle.ON_OFF; }
    @Override public boolean coloured() { return true; }
    @Override public ComponentEditor controllerMoulConfig(Option<Boolean> option, ProcessedOption processed, MoulConfigDefinition config) {
        var binding = new GetSetter<Boolean>() {
            @Override public Boolean get() { return (Boolean)processed.get(); }
            @Override public void set(Boolean value) { processed.set(value); }
        };
        GuiComponent row = new RowComponent(
            new SpacerComponent(() -> 8, () -> HEIGHT),
            new FixedComponent(new CenterComponent(new SwitchComponent(binding, 200)), 48, HEIGHT),
            new SpacerComponent(() -> 10, () -> HEIGHT),
            new FixedComponent(new AlignComponent(new TextComponent(processed.getName()),
                () -> HorizontalAlign.LEFT, () -> VerticalAlign.CENTER), 170, HEIGHT));
        GuiComponent content = option.description().isEmpty() ? row : new HoverComponent(row, () -> List.of(processed.getDescription()));
        return new ComponentEditor(processed) {
            @Override public GuiComponent getDelegate() { return content; }
            @Override public int getHeight() { return HEIGHT; }
        };
    }
}

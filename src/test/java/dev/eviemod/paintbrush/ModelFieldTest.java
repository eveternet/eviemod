package dev.eviemod.paintbrush;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelFieldTest {
    // Font measurement stub keeps this real-widget input test independent of a GPU.
    private final Font font = new Font(null) {
        @Override public int width(String text) { return text.length() * 6; }
        @Override public String plainSubstrByWidth(String text, int width) { return plainSubstrByWidth(text, width, false); }
        @Override public String plainSubstrByWidth(String text, int width, boolean backwards) {
            int length = Math.min(text.length(), Math.max(0, width / 6));
            return backwards ? text.substring(text.length() - length) : text.substring(0, length);
        }
    };
    private ModelField field(List<String> models, AtomicReference<String> value) {
        var field = new ModelField(font, 20, 30, 200, models, "", value::set);
        // Disable native text-input notifications in this headless test; suggestion
        // routing and acceptance still run through the real widget and container.
        field.setEditable(false);
        field.setFocused(true);
        field.setValue(models.getFirst().startsWith("minecraft:") ? "minecraft:" : "a:");
        return field;
    }
    @Test void emptyInputNeverOpensAndFocusLossOrEscapeDismisses() {
        var field = field(List.of("minecraft:apple", "minecraft:bow"), new AtomicReference<>());
        assertTrue(field.isMouseOver(25, 55));
        field.keyPressed(new KeyEvent(256, 0, 0)); assertFalse(field.isMouseOver(25, 55));
        field.keyPressed(new KeyEvent(264, 0, 0)); assertTrue(field.isMouseOver(25, 55));
        field.setFocused(false); field.setFocused(true); assertFalse(field.isMouseOver(25, 55));
        field.setValue(""); field.keyPressed(new KeyEvent(264, 0, 0)); assertFalse(field.isMouseOver(25, 55));
        field.setValue("   "); assertFalse(field.isMouseOver(25, 55));
    }
    @Test void containerRoutesSuggestionClickAndUpdatesDraft() {
        var value = new AtomicReference<String>();
        var field = field(List.of("minecraft:apple", "minecraft:bow"), value);
        var parent = new AbstractContainerEventHandler() {
            @Override public List<? extends GuiEventListener> children() { return List.of(field); }
        };
        assertSame(field, parent.getChildAt(25, 70).orElseThrow());
        assertTrue(parent.mouseClicked(new MouseButtonEvent(25, 70, new MouseButtonInfo(0, 0)), false));
        assertEquals("minecraft:bow", field.getValue());
        assertEquals("minecraft:bow", value.get());
        assertTrue(field.isFocused());
        assertEquals(field.getValue().length(), field.getCursorPosition());
        assertFalse(field.isMouseOver(25, 70)); // The accepted dropdown is closed.
    }
    @Test void lastPageOnlyClaimsVisibleRowsAndRespectsFocus() {
        var field = field(List.of("a:a", "a:b", "a:c", "a:d", "a:e", "a:f", "a:g"), new AtomicReference<>());
        for (int i = 0; i < 5; i++) field.keyPressed(new KeyEvent(264, 0, 0));
        assertTrue(field.isMouseOver(25, 50));
        assertTrue(field.isMouseOver(25, 81));
        assertFalse(field.isMouseOver(25, 82));
        assertFalse(field.isMouseOver(220, 55));
        assertTrue(field.mouseClicked(new MouseButtonEvent(25, 70, new MouseButtonInfo(0, 0)), false));
        assertEquals("a:g", field.getValue());
        field.keyPressed(new KeyEvent(264, 0, 0));
        field.setFocused(false);
        assertFalse(field.isMouseOver(25, 55));
    }
}

package dev.eviemod.paintbrush;

import net.azureaaron.dandelion.api.Option;
import net.azureaaron.dandelion.api.controllers.StringController;
import net.azureaaron.dandelion.deps.moulconfig.common.RenderContext;
import net.azureaaron.dandelion.deps.moulconfig.gui.*;
import net.azureaaron.dandelion.deps.moulconfig.platform.MoulConfigRenderContext;
import net.azureaaron.dandelion.deps.moulconfig.processor.ProcessedOption;
import net.azureaaron.dandelion.impl.moulconfig.MoulConfigDefinition;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;

/** Keeps the rich item editor inside the configuration category, including input and drafts. */
final class PaintBrushController implements StringController {
    private final PaintBrushScreen editor;
    PaintBrushController(PaintBrushScreen editor) { this.editor = editor; }
    @Override public GuiOptionEditor controllerMoulConfig(Option<String> option, ProcessedOption processed, MoulConfigDefinition definition) {
        return new GuiOptionEditor(processed) {
            private float scale = 1;
            private int mx, my, pressed = -1;
            @Override public int getHeight() { return Math.round(232 * scale) + 8; }
            @Override public void render(RenderContext context, int x, int y, int width) {
                scale = Math.min(1, width / 440f);
                int mouseX = context.getMinecraft().getMouseX(), mouseY = context.getMinecraft().getMouseY();
                var graphics = ((MoulConfigRenderContext) context).getDrawContext();
                graphics.pose().pushMatrix();
                graphics.pose().translate(x, y); graphics.pose().scale(scale, scale);
                try { editor.renderEmbedded(graphics, (int)((mouseX-x)/scale), (int)((mouseY-y)/scale)); }
                finally { graphics.pose().popMatrix(); }
            }
            @Override public boolean mouseInput(int x, int y, int width, int mouseX, int mouseY, MouseEvent event) {
                mx = (int)((mouseX - x) / scale); my = (int)((mouseY - y) / scale);
                boolean inside = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + getHeight();
                if (event instanceof MouseEvent.Click click) {
                    var nativeEvent = new MouseButtonEvent(mx, my, new MouseButtonInfo(click.getMouseButton(), modifiers()));
                    if (click.getMouseState()) {
                        if (!inside) { editor.setFocused(null); return false; }
                        pressed = click.getMouseButton(); return editor.mouseClicked(nativeEvent, false);
                    }
                    if (pressed >= 0) { pressed = -1; return editor.mouseReleased(nativeEvent); }
                } else if (event instanceof MouseEvent.Move move) {
                    if (pressed >= 0) return editor.mouseDragged(new MouseButtonEvent(mx, my, new MouseButtonInfo(pressed, modifiers())), move.getDx()/scale, move.getDy()/scale);
                    if (inside) editor.mouseMoved(mx, my);
                } else if (event instanceof MouseEvent.Scroll scroll && inside) {
                    return editor.mouseScrolled(mx, my, 0, scroll.getDWheel());
                }
                return false;
            }
            @Override public boolean keyboardInput(KeyboardEvent event) {
                if (event instanceof KeyboardEvent.KeyPressed key && key.getPressed()) {
                    // Let the config shell own Escape unless the editor dismisses a completion list.
                    if (key.getKeycode() == GLFW.GLFW_KEY_ESCAPE) return editor.dismissSuggestions();
                    return editor.keyPressed(new KeyEvent(key.getKeycode(), key.getScancode(), modifiers()));
                }
                if (event instanceof KeyboardEvent.CharTyped typed) return editor.charTyped(new CharacterEvent(typed.getChar()));
                return false;
            }
        };
    }
    private static int modifiers() {
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
        int flags = 0;
        int[] keys = {GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL,
            GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT, GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER};
        int[] masks = {GLFW.GLFW_MOD_SHIFT, GLFW.GLFW_MOD_SHIFT, GLFW.GLFW_MOD_CONTROL, GLFW.GLFW_MOD_CONTROL,
            GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SUPER, GLFW.GLFW_MOD_SUPER};
        for (int i=0; i<keys.length; i++) if (GLFW.glfwGetKey(window, keys[i]) == GLFW.GLFW_PRESS) flags |= masks[i];
        return flags;
    }
}

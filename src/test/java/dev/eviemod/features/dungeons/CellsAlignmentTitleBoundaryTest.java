package dev.eviemod.features.dungeons;

import java.io.IOException;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/** Headless bytecode checks against the actual Minecraft target in each build. */
class CellsAlignmentTitleBoundaryTest {
    private static ClassNode read(String name) throws IOException {
        try (var stream = CellsAlignmentTitleBoundaryTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    @Test void vanillaTitleAndSubtitleHaveSeparateDrawCallsInBothTargets() throws Exception {
        String target;
        try { Class.forName("net.minecraft.client.gui.Hud", false, getClass().getClassLoader()); target = "Hud"; }
        catch (ClassNotFoundException absent) { target = "Gui"; }
        var extract = method(read("net/minecraft/client/gui/" + target), "extractTitle");
        var drawnFields = new ArrayList<String>();
        String lastTextField = null;
        for (var instruction : extract.instructions) {
            if (instruction instanceof FieldInsnNode field && field.desc.equals("Lnet/minecraft/network/chat/Component;"))
                lastTextField = field.name;
            if (instruction instanceof MethodInsnNode call && call.name.equals("textWithBackdrop")) {
                assertEquals("(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V", call.desc);
                drawnFields.add(lastTextField);
            }
        }
        assertEquals(java.util.List.of("title", "subtitle"), drawnFields);
    }

    @Test void wrapperSuppressesOnlyMainTitleDuringCountdownAndRestoresItAfterward() throws Exception {
        var harness = new TitleHarness();
        var type = dev.eviemod.features.skyblock.mixin.CellsAlignmentTitleMixin.class;
        var field = type.getDeclaredField("eviemod$cellsFrame");
        field.setAccessible(true);
        var wrapper = java.util.Arrays.stream(type.getDeclaredMethods())
            .filter(m -> m.getName().equals("eviemod$onlyReplaceMainTitle")).findFirst().orElseThrow();
        wrapper.setAccessible(true);
        var calls = new java.util.ArrayList<Object[]>();
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original = args -> { calls.add(args); return null; };
        wrapper.invoke(harness, null, null, null, 1, 2, 3, 4, original);
        assertEquals(1, calls.size());
        assertArrayEquals(new Object[]{null, null, null, 1, 2, 3, 4}, calls.getFirst());
        field.set(harness, new CellsAlignmentTimer.Frame("4", 0x55ff55));
        wrapper.invoke(harness, null, null, null, 1, 2, 3, 4, original);
        assertEquals(1, calls.size());
        field.set(harness, null);
        wrapper.invoke(harness, null, null, null, 1, 2, 3, 4, original);
        assertEquals(2, calls.size());
        var node = read("dev/eviemod/features/skyblock/mixin/CellsAlignmentTitleMixin");
        var wrap = method(node, "eviemod$onlyReplaceMainTitle");
        var annotation = wrap.invisibleAnnotations.stream()
            .filter(a -> a.desc.endsWith("/WrapOperation;")).findFirst().orElseThrow();
        int atIndex = annotation.values.indexOf("at");
        var at = (org.objectweb.asm.tree.AnnotationNode) ((java.util.List<?>) annotation.values.get(atIndex + 1)).getFirst();
        assertEquals(0, at.values.get(at.values.indexOf("ordinal") + 1));
    }

    private static final class TitleHarness extends dev.eviemod.features.skyblock.mixin.CellsAlignmentTitleMixin {}

    @Test void customTitleNeverMutatesVanillaTitleOrSubtitleStateAndHasNoFade() throws Exception {
        var renderer = read("dev/eviemod/features/dungeons/CellsAlignmentTitle");
        var extract = method(renderer, "extract");
        boolean fullOpacity = false;
        boolean draw = false;
        for (var instruction : extract.instructions) {
            if (instruction instanceof FieldInsnNode field) {
                assertNotEquals(Opcodes.PUTFIELD, field.getOpcode());
                assertNotEquals(Opcodes.PUTSTATIC, field.getOpcode());
            }
            if (instruction instanceof MethodInsnNode call) {
                assertFalse(java.util.Set.of("setTitle", "setSubtitle", "setTimes", "clear", "resetTitleTimes").contains(call.name));
                if (call.name.equals("textWithBackdrop")) {
                    draw = true;
                    fullOpacity = call.getPrevious().getOpcode() == Opcodes.ICONST_M1;
                }
            }
        }
        assertTrue(draw);
        assertTrue(fullOpacity, "Title is always fully opaque, independent of vanilla fades");
    }
}

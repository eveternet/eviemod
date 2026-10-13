package dev.eviemod.features.experiments;

import dev.eviemod.paintbrush.mixin.ExperimentContainerMixin;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.Inject;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the actual game's version-sensitive draw boundary, without loading or launching a client. */
class ExperimentRenderBoundaryTest {
    private static final String REPLACING_TOOLTIP = "(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;"
        + "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;IIZLnet/minecraft/resources/Identifier;)V";
    @Test void containerTailIsAfterSlotsAndPoseRestorationButBeforeDeferredTooltips() throws IOException {
        var container = bytecode("net/minecraft/client/gui/screens/inventory/AbstractContainerScreen");
        var draw = calls(method(container, "extractRenderState"));
        assertTrue(draw.indexOf("extractContents") >= 0);
        assertTrue(draw.indexOf("extractContents") < draw.indexOf("extractTooltip"));
        assertEquals("extractTooltip", draw.getLast(), "Container tail follows vanilla tooltip scheduling");

        var contents = calls(method(container, "extractContents"));
        assertTrue(contents.indexOf("pushMatrix") < contents.indexOf("extractSlots"));
        assertTrue(contents.indexOf("extractSlots") < contents.lastIndexOf("popMatrix"));
        assertEquals("popMatrix", contents.getLast(), "Overlay receives a restored screen-space pose");

        var screen = bytecode("net/minecraft/client/gui/screens/Screen");
        var outer = calls(method(screen, "extractRenderStateWithTooltipAndSubtitles"));
        assertTrue(outer.indexOf("extractRenderState") >= 0);
        assertTrue(outer.indexOf("extractRenderState") < outer.indexOf("extractDeferredElements"),
            "Container-tail overlay and remembered tooltip scheduling must precede the same-frame flush");

        var graphics = bytecode("net/minecraft/client/gui/GuiGraphicsExtractor");
        var flush = calls(method(graphics, "extractDeferredElements"));
        assertTrue(flush.lastIndexOf("nextStratum") >= 0);
        assertTrue(flush.lastIndexOf("nextStratum") < flush.lastIndexOf("run"),
            "Deferred tooltip runs on a later stratum than the container overlay");
    }

    @Test void compiledOverlayHookUsesTheContainerBoundaryIndependentlyOfIndividualSlots() throws IOException {
        var drawHook = Arrays.stream(ExperimentContainerMixin.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("eviemod$experimentGuidance")).findFirst().orElseThrow();
        var inject = drawHook.getAnnotation(Inject.class);
        assertNotNull(inject);
        assertArrayEquals(new String[]{"extractRenderState"}, inject.method());
        assertEquals(1, inject.at().length);
        assertEquals("TAIL", inject.at()[0].value());
        assertFalse(inject.cancellable());

        var hook = method(bytecode("dev/eviemod/paintbrush/mixin/ExperimentContainerMixin"), drawHook.getName());
        assertTrue(calls(hook).contains("draw"));
        var client = bytecode("dev/eviemod/features/experiments/ExperimentClient");
        assertTrue(client.methods().stream().flatMap(method -> calls(method).stream()).noneMatch("afterExtract"::equals),
            "A Fabric afterExtract callback would draw after deferred tooltips and lose remembered tooltips");
    }

    @Test void rememberedTooltipUsesTheReplacingItemImageAndStyleApiOnTheActualGame() throws IOException {
        var graphics = bytecode("net/minecraft/client/gui/GuiGraphicsExtractor");
        var api = graphics.methods().stream().filter(method -> method.methodName().equalsString("setTooltipForNextFrame")
            && method.methodType().equalsString(REPLACING_TOOLTIP)).findFirst().orElseThrow();
        var instructions = api.code().orElseThrow().elementStream().filter(Instruction.class::isInstance)
            .map(Instruction.class::cast).toList();
        var internalCall = instructions.stream().filter(instruction -> instruction instanceof InvokeInstruction invoke
            && invoke.name().equalsString("setTooltipForNextFrameInternal")).findFirst().orElseThrow();
        // On this public signature replaceExisting is local 7; verify the real API forwards it rather than hardcoding false.
        assertEquals(7, assertInstanceOf(LoadInstruction.class, instructions.get(instructions.indexOf(internalCall) - 1)).slot());

        var helper = method(bytecode("dev/eviemod/features/experiments/ExperimentClient"), "setRememberedTooltip");
        var helperCalls = calls(helper);
        assertTrue(helperCalls.contains("getTooltipFromItem"), "Keep normal ItemStack tooltip text");
        assertTrue(helperCalls.contains("getTooltipImage"), "Keep the optional item tooltip component");
        var setter = helper.code().orElseThrow().elementStream().filter(InvokeInstruction.class::isInstance)
            .map(InvokeInstruction.class::cast).filter(invoke -> invoke.name().equalsString("setTooltipForNextFrame"))
            .findFirst().orElseThrow();
        assertEquals(REPLACING_TOOLTIP, setter.type().stringValue(), "Use the supported explicit replacement overload");
        assertTrue(helper.code().orElseThrow().elementStream().filter(FieldInstruction.class::isInstance)
            .map(FieldInstruction.class::cast).anyMatch(field -> field.name().equalsString("TOOLTIP_STYLE")), "Keep the item's tooltip style");
        assertEquals(List.of(1), helper.code().orElseThrow().elementStream().filter(ConstantInstruction.class::isInstance)
            .map(ConstantInstruction.class::cast).map(ConstantInstruction::constantValue).toList(), "Explicit replaceExisting=true");
    }

    private static ClassModel bytecode(String name) throws IOException {
        try (var input = ExperimentRenderBoundaryTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, "Missing class resource " + name);
            return ClassFile.of().parse(input.readAllBytes());
        }
    }
    private static MethodModel method(ClassModel type, String name) {
        return type.methods().stream().filter(method -> method.methodName().equalsString(name)).findFirst().orElseThrow();
    }
    private static List<String> calls(MethodModel method) {
        return method.code().stream().flatMap(code -> code.elementStream())
            .filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast)
            .map(invoke -> invoke.name().stringValue()).toList();
    }
}

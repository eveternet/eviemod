package dev.eviemod.features.experiments;

import dev.eviemod.paintbrush.mixin.ExperimentContainerMixin;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.Inject;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the actual game's version-sensitive draw boundary, without loading or launching a client. */
class ExperimentRenderBoundaryTest {
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

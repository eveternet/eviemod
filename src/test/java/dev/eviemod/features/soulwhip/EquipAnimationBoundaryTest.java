package dev.eviemod.features.soulwhip;

import java.io.IOException;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the exact Minecraft bytecode boundaries on both supported targets. */
class EquipAnimationBoundaryTest {
    private static ClassNode read(String name) throws IOException {
        try (var stream = EquipAnimationBoundaryTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(String owner, String name) throws IOException {
        return read(owner).methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static java.util.List<MethodInsnNode> calls(MethodNode method, String name) {
        var calls = new ArrayList<MethodInsnNode>();
        for (var insn : method.instructions)
            if (insn instanceof MethodInsnNode call && call.name.equals(name)) calls.add(call);
        return calls;
    }

    @Test void airMissResetsSwapTimerBeforeNormalSwingOnBothTargets() throws Exception {
        var attack = method("net/minecraft/client/Minecraft", "startAttack");
        var resets = calls(attack, "resetAttackStrengthTicker");
        assertEquals(1, resets.size());
        assertEquals("net/minecraft/client/player/LocalPlayer", resets.getFirst().owner);
        var swings = calls(attack, "swing");
        assertEquals(dev.eviemod.compat.VersionFixtures.ATTACK_SWING_CALLS, swings.size());
        assertTrue(swings.stream().allMatch(call -> call.desc.equals(dev.eviemod.compat.VersionFixtures.ATTACK_SWING_DESCRIPTOR)));
        assertTrue(attack.instructions.indexOf(resets.getFirst()) < attack.instructions.indexOf(swings.getLast()));
        var reset = method("net/minecraft/world/entity/player/Player", "resetAttackStrengthTicker");
        var fields = new ArrayList<String>();
        for (var insn : reset.instructions) if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD)
            fields.add(field.name);
        assertEquals(java.util.List.of("attackStrengthTicker", "itemSwapTicker"), fields);
    }

    @Test void rendererHasOneSwapScaleCallAndIndependentItemChangeBranch() throws Exception {
        var tick = method(dev.eviemod.compat.ClientUi.EQUIP_OWNER.replace('.', '/'), "tick");
        var scales = calls(tick, "getItemSwapScale");
        assertEquals(1, scales.size());
        assertEquals("net/minecraft/client/player/LocalPlayer", scales.getFirst().owner);
        assertEquals("(F)F", scales.getFirst().desc);
        var scale = scales.getFirst();
        // After reading the scale, vanilla compares rendered/held references and targets zero on a change.
        var next = scale.getNext();
        while (!(next instanceof JumpInsnNode)) next = next.getNext();
        assertEquals(Opcodes.IF_ACMPEQ, next.getOpcode());
        assertEquals(Opcodes.FCONST_0, next.getNext().getOpcode());
        int cubes = 0;
        for (var insn : tick.instructions) if (insn.getOpcode() == Opcodes.FMUL) cubes++;
        assertEquals(2, cubes, "Scale is cubed only for the main-hand height");
    }

    @Test void inputHooksObserveAttacksWithoutCancellingOrMutatingGameplay() throws Exception {
        var mixin = read("dev/eviemod/features/soulwhip/mixin/client/MinecraftMixin");
        for (var method : mixin.methods) {
            if (method.visibleAnnotations != null) for (var annotation : method.visibleAnnotations)
                if (annotation.desc.endsWith("/Inject;")) assertFalse(annotation.values.contains("cancellable"));
            for (var insn : method.instructions) {
                if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD)
                    assertEquals(mixin.name, field.owner, "Only the local miss marker may change");
                if (insn instanceof MethodInsnNode call)
                    assertFalse(java.util.Set.of("swing", "resetAttackStrengthTicker", "cancel", "setReturnValue").contains(call.name));
            }
        }
    }
}

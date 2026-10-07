// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.compat.sumr;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.TypeInsnNode;
import static org.junit.jupiter.api.Assertions.*;

class SumrBoundaryTest {
    @Test void integrationRequiresExplicitOptInAndExactInstalledBinary() {
        assertTrue(SumrMixinPlugin.enabled("true", "1.5.1+26.2"));
        for (String setting : new String[]{null, "", "false", "1"})
            assertFalse(SumrMixinPlugin.enabled(setting, "1.5.1+26.2"));
        for (String version : new String[]{null, "1.5.0+26.2", "1.5.2+26.2", "1.5.1+26.1.2"})
            assertFalse(SumrMixinPlugin.enabled("true", version));
    }

    @Test void actualSumrBackendGuardUsesTheImplementedInterface() throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "games/enchanted/eg_stop_unloading_my_shaders/common/ModConstants.class")) {
            assertNotNull(input);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            var guard = node.methods.stream().filter(method -> method.name.equals("isBackendHandled")).findFirst().orElseThrow();
            long anchors = java.util.stream.StreamSupport.stream(guard.instructions.spliterator(), false)
                    .filter(insn -> insn instanceof TypeInsnNode type && type.getOpcode() == org.objectweb.asm.Opcodes.INSTANCEOF
                            && type.desc.equals("games/enchanted/eg_stop_unloading_my_shaders/common/duck/GpuDeviceAdditions")).count();
            assertEquals(1, anchors);
        }
    }
}

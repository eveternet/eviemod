// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;

/** Upgrade regression: no public GlStateManager entry point may escape the audited boundary. */
class LegacyGlBoundaryTest {
    @Test void everyPinnedGlEntryPointHasAHeadGuard() throws IOException {
        ClassNode target = read("com/mojang/blaze3d/opengl/GlStateManager");
        ClassNode mixin = read("dev/eviemod/metal/mixin/LegacyGlStateMixin");
        Set<String> actual = new HashSet<>();
        for (var method : target.methods) {
            if ((method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) {
                actual.add(method.name + method.desc);
            }
        }
        Set<String> guarded = new HashSet<>();
        for (var method : mixin.methods) {
            if (method.visibleAnnotations == null) continue;
            for (var annotation : method.visibleAnnotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
                assertEquals(Boolean.TRUE, value(annotation, "cancellable"));
                var points = (java.util.List<?>) value(annotation, "at");
                assertEquals(1, points.size());
                assertEquals("HEAD", value((AnnotationNode) points.getFirst(), "value"));
                for (Object selector : (java.util.List<?>) value(annotation, "method")) {
                    assertTrue(guarded.add((String) selector), "Overlapping guard: " + selector);
                }
            }
        }
        assertEquals(actual, guarded, "Minecraft GL boundary changed; audit new/changed operations before upgrading");
    }

    private static ClassNode read(String name) throws IOException {
        try (var stream = LegacyGlBoundaryTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
            return node;
        }
    }

    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        }
        return null;
    }
}

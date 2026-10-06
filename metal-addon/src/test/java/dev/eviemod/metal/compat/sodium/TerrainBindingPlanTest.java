// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.compat.sodium;

import dev.eviemod.metal.shader.ShaderTranslator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainBindingPlanTest {
    private static final class Reflection extends HashMap<String, Integer> {
        boolean frozen;
        Reflection(Map<String, Integer> values) { super(values); }
        @Override public Set<Map.Entry<String, Integer>> entrySet() {
            assertFalse(frozen, "Reflection traversal after plan construction");
            return super.entrySet();
        }
        @Override public Integer getOrDefault(Object key, Integer fallback) {
            assertFalse(frozen, "Sampler resolution after plan construction");
            return super.getOrDefault(key, fallback);
        }
    }

    @Test void numericPlanSurvivesReflectionFreezeAndBindingPointChanges() {
        var buffers = new Reflection(Map.of(ShaderTranslator.DEFAULT_BLOCK, 0, "Globals", 1));
        var textures = new Reflection(Map.of("Atlas", 2, "Times", 3));
        var samplers = new Reflection(Map.of("Atlas", 4));
        var stage = new ShaderTranslator.Result("", "", buffers, textures, samplers, Map.of(), Map.of(), Map.of(), 32);
        var plan = new TerrainBindingPlan(stage);
        buffers.frozen = textures.frozen = samplers.frozen = true;
        assertEquals(0, plan.defaultsIndex); assertEquals(32, plan.defaultsSize);
        assertEquals(1, plan.buffers.length); assertEquals(-1, plan.buffers[0].binding);
        plan.block("Globals", 5); plan.sampler("Atlas", 6); plan.sampler("Times", 7);
        for (int batch = 0; batch < 10_000; batch++) {
            assertEquals(1, plan.buffers[0].index); assertEquals(5, plan.buffers[0].binding);
            for (var texture : plan.textures) {
                assertEquals(texture.name.equals("Atlas") ? 6 : 7, texture.unit);
                assertEquals(texture.name.equals("Atlas") ? 4 : -1, texture.samplerIndex);
            }
        }
        plan.block("Globals", 8); assertEquals(8, plan.buffers[0].binding);
        plan.sampler("Unused", 0); assertEquals(2, plan.textures.length);
    }
}

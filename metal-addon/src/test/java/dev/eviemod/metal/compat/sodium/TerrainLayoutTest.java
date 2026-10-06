// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.compat.sodium;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainLayoutTest {
    private static List<TerrainLayout.Attribute> attributes(int offset, int stride) {
        return List.of(new TerrainLayout.Attribute(0, 36, offset, stride), new TerrainLayout.Attribute(1, 3, 12, stride));
    }

    @Test void regionsShareImmutableLayoutAndLookupDoesNotResnapshot() {
        var pool = new TerrainLayout.Pool();
        var source = new ArrayList<>(attributes(0, 20));
        var first = pool.acquire(source);
        source.clear();
        var second = pool.acquire(attributes(0, 20));
        assertSame(first, second);
        assertEquals(2, first.attributes.size());
        assertThrows(UnsupportedOperationException.class, () -> first.attributes.clear());
        var pipelines = new HashMap<TerrainLayout, Long>();
        pipelines.put(first, 123L);
        for (int batch = 0; batch < 10_000; batch++) {
            assertEquals(123L, pipelines.get(second).longValue());
            assertEquals(first.hashCode(), second.hashCode());
        }
        assertEquals(1, pool.size());
        pool.release(first); assertEquals(1, pool.size());
        pool.release(second); assertEquals(0, pool.size());
    }

    @Test void allStructuralFieldsRemainPartOfTheKey() {
        var pool = new TerrainLayout.Pool();
        var original = pool.acquire(attributes(0, 20));
        var offset = pool.acquire(attributes(4, 20));
        var stride = pool.acquire(attributes(0, 24));
        var format = pool.acquire(List.of(new TerrainLayout.Attribute(0, 32, 0, 20), new TerrainLayout.Attribute(1, 3, 12, 20)));
        var index = pool.acquire(List.of(new TerrainLayout.Attribute(2, 36, 0, 20), new TerrainLayout.Attribute(1, 3, 12, 20)));
        assertEquals(5, pool.size());
        for (var different : List.of(offset, stride, format, index)) assertNotEquals(original, different);
        for (var layout : List.of(original, offset, stride, format, index)) pool.release(layout);
        assertEquals(0, pool.size());
    }

    @Test void programOwnershipPreservesCanonicalIdentityAcrossTessellationReplacement() {
        var pool = new TerrainLayout.Pool();
        var layout = pool.acquire(attributes(0, 20));
        pool.retain(layout); // One cached program/attachment variant.
        pool.release(layout); // Region tessellation is replaced.
        var replacement = pool.acquire(attributes(0, 20));
        assertSame(layout, replacement);
        pool.release(layout); // Program deleted.
        pool.release(replacement);
        assertEquals(0, pool.size());
        var next = pool.acquire(attributes(0, 20));
        assertNotSame(layout, next);
        pool.release(next);
    }

    @Test void lateDeletionAfterDeviceCleanupCannotRemoveANewEqualLayout() {
        var pool = new TerrainLayout.Pool();
        var old = pool.acquire(attributes(0, 20));
        pool.clear();
        var next = pool.acquire(attributes(0, 20));
        pool.release(old);
        assertEquals(1, pool.size());
        pool.release(next);
        assertEquals(0, pool.size());
    }
}

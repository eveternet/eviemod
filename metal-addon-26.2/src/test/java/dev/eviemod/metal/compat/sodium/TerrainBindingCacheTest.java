// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.compat.sodium;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainBindingCacheTest {
    private static final class Calls implements TerrainBindingCache.Sink {
        int buffers, bytes, textures;
        public void buffer(boolean f, int i, long h, long o) { buffers++; }
        public void bytes(boolean f, int i, long a, int n) { bytes++; }
        public void texture(boolean f, int i, long h, int si, long s) { textures++; }
    }

    @Test void tenThousandUnchangedBatchesIssueOnlyInitialBindings() {
        var calls = new Calls(); var cache = new TerrainBindingCache(calls, true);
        var program = new Object(); var arena = new Object(); var globals = new Object();
        cache.context(program, 1);
        for (int i = 0; i < 10_000; i++) {
            assertFalse(cache.context(program, 1));
            cache.buffer(false, 30, arena, 100, 0, 1);
            cache.buffer(false, 0, globals, 200, 256, 1);
            cache.bytes(false, 1, program, 300, 16, 1);
            cache.texture(true, 0, 400, 0, 500, 1);
        }
        assertEquals(2, calls.buffers); assertEquals(1, calls.bytes); assertEquals(1, calls.textures);
        assertEquals(40_000, cache.attempted); assertEquals(4, cache.applied); assertEquals(16, cache.copiedBytes);
    }

    @Test void bufferIdentityOffsetHandleAndStorageGenerationEachInvalidate() {
        var calls = new Calls(); var cache = new TerrainBindingCache(calls, true); var owner = new Object();
        cache.buffer(false, 0, owner, 100, 0, 1);
        cache.buffer(false, 0, owner, 100, 0, 1);
        cache.buffer(false, 0, owner, 100, 256, 1);
        cache.buffer(false, 0, owner, 101, 256, 2);
        cache.buffer(false, 0, owner, 101, 256, 3); // Recycled native address still has new storage.
        cache.buffer(false, 0, new Object(), 101, 256, 3);
        cache.buffer(true, 0, owner, 101, 256, 3);
        assertEquals(6, calls.buffers);
    }

    @Test void dirtyBytesAtTheSameAddressAndBufferBytesAliasCannotSkipCopies() {
        var calls = new Calls(); var cache = new TerrainBindingCache(calls, true); var owner = new Object();
        cache.bytes(false, 0, owner, 100, 16, 1);
        cache.bytes(false, 0, owner, 100, 16, 1);
        cache.bytes(false, 0, owner, 100, 16, 2);
        cache.buffer(false, 0, owner, 100, 16, 2);
        cache.bytes(false, 0, owner, 100, 16, 2);
        cache.bytes(false, 0, owner, 100, 32, 2);
        assertEquals(4, calls.bytes); assertEquals(1, calls.buffers); assertEquals(80, cache.copiedBytes);
    }

    @Test void programEncoderAndLogicalResumeInvalidateEveryKind() {
        var calls = new Calls(); var cache = new TerrainBindingCache(calls, true); var program = new Object();
        for (int phase = 0; phase < 4; phase++) {
            if (phase == 1) cache.invalidate(); // May resume onto the same native encoder after ordinary drawing.
            if (phase == 3) program = new Object();
            cache.context(program, phase < 2 ? 1 : 2);
            cache.buffer(false, 0, program, 100, 0, 1);
            cache.bytes(true, 0, program, 200, 16, 1);
            cache.texture(true, 0, 300, -1, 0, 1);
        }
        assertEquals(4, calls.buffers); assertEquals(4, calls.bytes); assertEquals(4, calls.textures);
    }

    @Test void textureGenerationAndSharedSamplerSlotsInvalidateIndependently() {
        var calls = new Calls(); var cache = new TerrainBindingCache(calls, true);
        cache.texture(true, 0, 100, 3, 200, 1);
        cache.texture(true, 0, 100, 3, 200, 1);
        cache.texture(true, 1, 101, 3, 201, 2); // Another texture overwrites shared sampler slot 3.
        cache.texture(true, 0, 100, 3, 200, 1); // Restore it even though texture slot 0 did not change.
        cache.texture(true, 0, 100, 3, 200, 3); // Same address, new resource/view generation.
        cache.texture(true, 0, 100, 3, 0, 3);   // Native setter leaves sampler untouched.
        cache.texture(true, 0, 100, 3, 200, 3);
        assertEquals(4, calls.textures);
    }

    @Test void slotsOutsideTheCacheKeepDirectBindingAndInvalidateSharedState() {
        var calls = new Calls(); var cache = new TerrainBindingCache(calls, true);
        cache.texture(true, 0, 100, 3, 200, 1);
        cache.texture(true, 64, 101, 3, 201, 2);
        cache.texture(true, 0, 100, 3, 200, 1);
        cache.texture(true, 0, 100, 40, 201, 2);
        cache.buffer(false, 32, this, 300, 0, 1);
        cache.buffer(false, 32, this, 300, 0, 1);
        cache.bytes(false, 32, this, 400, 16, 1);
        cache.bytes(false, 32, this, 400, 16, 1);
        assertEquals(4, calls.textures); assertEquals(2, calls.buffers); assertEquals(2, calls.bytes);
    }

    @Test void diagnosticsAreOffByDefaultAndFailedSettersAreRetried() {
        var calls = new Calls(); var cache = new TerrainBindingCache(calls, false);
        cache.buffer(false, 0, this, 1, 0, 1);
        assertEquals(0, cache.attempted); assertEquals(0, cache.applied);
        var failing = new TerrainBindingCache(new TerrainBindingCache.Sink() {
            int attempts;
            public void buffer(boolean f, int i, long h, long o) { if (attempts++ == 0) throw new IllegalStateException(); }
            public void bytes(boolean f, int i, long a, int n) {}
            public void texture(boolean f, int i, long h, int si, long s) {}
        }, true);
        assertThrows(IllegalStateException.class, () -> failing.buffer(false, 0, this, 1, 0, 1));
        failing.buffer(false, 0, this, 1, 0, 1);
        assertEquals(2, failing.attempted); assertEquals(1, failing.applied);
    }
}

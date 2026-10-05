// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.compat.sodium;

import dev.eviemod.metal.mtl.Mtl;
import java.util.Arrays;

/** Numeric slot cache. Buffer objects/storage and copied bytes have different identities. Render thread only. */
final class TerrainBindingCache {
    interface Sink {
        void buffer(boolean fragment, int index, long handle, long offset);
        void bytes(boolean fragment, int index, long address, int length);
        void texture(boolean fragment, int index, long handle, int samplerIndex, long sampler);
    }
    static final Sink NATIVE = new Sink() {
        public void buffer(boolean f, int i, long h, long o) { Mtl.setBuffer(f, i, h, o); }
        public void bytes(boolean f, int i, long a, int n) { Mtl.setBytes(f, i, a, n); }
        public void texture(boolean f, int i, long h, int si, long s) { Mtl.setTexture(f, i, h, si, s); }
    };
    private final Sink sink;
    private final boolean profile;
    private final Object[][] owners = new Object[2][32];
    private final byte[][] kinds = new byte[2][32];
    private final long[][] buffers = new long[2][32], offsets = new long[2][32], generations = new long[2][32];
    private final long[][] textures = new long[2][32], textureGenerations = new long[2][32];
    private final long[][] samplers = new long[2][32], samplerGenerations = new long[2][32];
    private final boolean[][] textureSet = new boolean[2][32], samplerSet = new boolean[2][32];
    private Object program;
    private long encoder = -1;
    long attempted, applied, copiedBytes;

    TerrainBindingCache(Sink sink, boolean profile) { this.sink = sink; this.profile = profile; }

    boolean context(Object program, long encoder) {
        if (this.program != program || this.encoder != encoder) {
            invalidate();
            this.program = program;
            this.encoder = encoder;
            return true;
        }
        return false;
    }

    void invalidate() {
        program = null; encoder = -1;
        for (int s = 0; s < 2; s++) {
            Arrays.fill(owners[s], null);
            Arrays.fill(kinds[s], (byte) 0);
            Arrays.fill(textureSet[s], false);
            Arrays.fill(samplerSet[s], false);
        }
    }

    void buffer(boolean fragment, int index, Object owner, long handle, long offset, long generation) {
        int s = fragment ? 1 : 0;
        if (profile) attempted++;
        if (index < 0 || index >= owners[s].length) {
            sink.buffer(fragment, index, handle, offset);
            invalidate();
            if (profile) applied++;
            return;
        }
        if (kinds[s][index] == 1 && owners[s][index] == owner && buffers[s][index] == handle
                && offsets[s][index] == offset && generations[s][index] == generation) return;
        sink.buffer(fragment, index, handle, offset);
        kinds[s][index] = 1; owners[s][index] = owner; buffers[s][index] = handle;
        offsets[s][index] = offset; generations[s][index] = generation;
        if (profile) applied++;
    }

    void bytes(boolean fragment, int index, Object owner, long address, int length, long generation) {
        int s = fragment ? 1 : 0;
        if (profile) attempted++;
        if (index < 0 || index >= owners[s].length) {
            sink.bytes(fragment, index, address, length);
            invalidate();
            if (profile) { applied++; copiedBytes += length; }
            return;
        }
        if (kinds[s][index] == 2 && owners[s][index] == owner && buffers[s][index] == address
                && offsets[s][index] == length && generations[s][index] == generation) return;
        sink.bytes(fragment, index, address, length);
        kinds[s][index] = 2; owners[s][index] = owner; buffers[s][index] = address;
        offsets[s][index] = length; generations[s][index] = generation;
        if (profile) { applied++; copiedBytes += length; }
    }

    void texture(boolean fragment, int index, long handle, int samplerIndex, long sampler, long generation) {
        int s = fragment ? 1 : 0;
        // The native setter does not touch a sampler when its handle is zero or there is no sampler slot.
        boolean hasSampler = samplerIndex >= 0 && sampler != 0;
        if (profile) attempted++;
        if (index < 0 || index >= textures[s].length || samplerIndex >= samplers[s].length) {
            // Unusual translated shaders keep the original direct-binding behavior instead of a new slot limit.
            sink.texture(fragment, index, handle, samplerIndex, sampler);
            invalidate(); // The setter may also overwrite a sampler slot inside the cached range.
            if (profile) applied++;
            return;
        }
        if (textureSet[s][index] && textures[s][index] == handle && textureGenerations[s][index] == generation
                && (!hasSampler || samplerSet[s][samplerIndex] && samplers[s][samplerIndex] == sampler
                && samplerGenerations[s][samplerIndex] == generation)) return;
        sink.texture(fragment, index, handle, samplerIndex, sampler);
        textureSet[s][index] = true; textures[s][index] = handle; textureGenerations[s][index] = generation;
        if (hasSampler) {
            samplerSet[s][samplerIndex] = true; samplers[s][samplerIndex] = sampler;
            samplerGenerations[s][samplerIndex] = generation;
        }
        if (profile) applied++;
    }
}

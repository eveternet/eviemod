// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.compat.sodium;

import dev.eviemod.metal.shader.ShaderTranslator;
import java.util.ArrayList;

/** Reflection is resolved when linking/configuring a program, rather than in the batch loop. */
final class TerrainBindingPlan {
    static final class Buffer {
        final String name;
        final int index;
        int binding = -1;
        Buffer(String name, int index) { this.name = name; this.index = index; }
    }
    static final class Texture {
        final String name;
        final int index, samplerIndex;
        int unit = -1;
        Texture(String name, int index, int samplerIndex) {
            this.name = name; this.index = index; this.samplerIndex = samplerIndex;
        }
    }

    final Buffer[] buffers;
    final Texture[] textures;
    final int defaultsIndex, defaultsSize;

    TerrainBindingPlan(ShaderTranslator.Result stage) {
        var buffers = new ArrayList<Buffer>();
        int defaults = -1;
        for (var e : stage.buffers().entrySet()) {
            if (e.getKey().equals(ShaderTranslator.DEFAULT_BLOCK)) defaults = e.getValue();
            else buffers.add(new Buffer(e.getKey(), e.getValue()));
        }
        this.buffers = buffers.toArray(Buffer[]::new);
        defaultsIndex = defaults;
        defaultsSize = stage.defaultsSize();
        var textures = new ArrayList<Texture>();
        for (var e : stage.textures().entrySet())
            textures.add(new Texture(e.getKey(), e.getValue(), stage.samplers().getOrDefault(e.getKey(), -1)));
        this.textures = textures.toArray(Texture[]::new);
    }

    void block(String name, int binding) {
        for (var buffer : buffers) if (buffer.name.equals(name)) buffer.binding = binding;
    }
    void sampler(String name, int unit) {
        for (var texture : textures) if (texture.name.equals(name)) texture.unit = unit;
    }
}

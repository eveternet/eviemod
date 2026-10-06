// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.compat.sodium;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;

/** Immutable structural layout, captured once per tessellation, never from a region draw. */
final class TerrainLayout {
    record Attribute(int index, int format, int offset, int stride) {}
    final List<Attribute> attributes;
    private final int hash;
    private int owners;

    private TerrainLayout(List<Attribute> attributes) {
        this.attributes = List.copyOf(attributes);
        hash = this.attributes.hashCode();
    }

    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof TerrainLayout layout && attributes.equals(layout.attributes);
    }

    /** Render-thread pool; tessellations and program variants retain only immutable Java data. */
    static final class Pool {
        private final Map<TerrainLayout, TerrainLayout> layouts = new HashMap<>();

        TerrainLayout acquire(GlVertexAttributeBinding[] bindings) {
            var attributes = new java.util.ArrayList<Attribute>(bindings.length);
            for (var a : bindings) attributes.add(new Attribute(a.getIndex(), SodiumMetal.vertexFormat(a), a.getPointer(), a.getStride()));
            return acquire(attributes);
        }

        TerrainLayout acquire(List<Attribute> attributes) {
            var candidate = new TerrainLayout(attributes);
            var layout = layouts.putIfAbsent(candidate, candidate);
            if (layout == null) layout = candidate;
            retain(layout);
            return layout;
        }

        void retain(TerrainLayout layout) { layout.owners++; }
        void release(TerrainLayout layout) {
            if (--layout.owners == 0 && layouts.get(layout) == layout) layouts.remove(layout);
        }

        int size() { return layouts.size(); }
        void clear() { layouts.clear(); }
    }
}

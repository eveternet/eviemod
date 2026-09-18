package dev.eviemod.paintbrush;

import com.mojang.blaze3d.platform.InputConstants;
import dev.eviemod.features.garden.GardenKeyMappings;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.slf4j.LoggerFactory;

/** Keeps native Garden mappings compatible with options.txt while eviemod owns their saved values. */
final class ImportedKeyBindings {
    private static Map<String, String> applied;
    static Map<String, KeyMapping> garden() {
        var keys = new LinkedHashMap<String, KeyMapping>();
        keys.put("tptoplot", GardenKeyMappings.TELEPORT_TO_PLOT);
        keys.put("setspawn", GardenKeyMappings.SET_SPAWN);
        keys.put("warp_garden", GardenKeyMappings.WARP_GARDEN);
        keys.put("loadouts", GardenKeyMappings.LOADOUTS);
        return keys;
    }
    static void tick() {
        if (EviemodSettings.STORE.error() != null) return;
        var configured = EviemodSettings.features().garden.keys;
        var current = new LinkedHashMap<String, String>();
        garden().forEach((name, mapping) -> current.put(name, mapping.saveString()));
        try {
            if (applied == null) {
                // Minecraft has loaded its native options by the first client tick.
                var next = EviemodSettings.STORE.values().copy();
                current.forEach(next.features.garden.keys::putIfAbsent);
                if (!next.features.garden.keys.equals(configured)) EviemodSettings.STORE.save(next);
                apply();
            } else if (!applied.equals(configured)) apply();
            else if (!current.equals(applied)) {
                // Edits from Minecraft's Controls screen remain supported too.
                var next = EviemodSettings.STORE.values().copy();
                next.features.garden.keys.putAll(current);
                EviemodSettings.STORE.save(next);
                applied = Map.copyOf(next.features.garden.keys);
            }
        } catch (IOException | RuntimeException e) {
            LoggerFactory.getLogger("eviemod").warn("Could not save Garden key bindings", e);
            apply();
        }
    }
    private static void apply() {
        var keys = EviemodSettings.features().garden.keys;
        garden().forEach((name, mapping) -> {
            if (keys.containsKey(name)) {
                try { mapping.setKey(InputConstants.getKey(keys.get(name))); }
                catch (IllegalArgumentException e) { LoggerFactory.getLogger("eviemod").warn("Unsupported Garden binding {}", name, e); }
            }
        });
        KeyMapping.resetMapping();
        applied = Map.copyOf(keys);
    }
}

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
    private static Map<String, String> configuredSnapshot, appliedSnapshot;
    private static long retryAfter;
    static Map<String, KeyMapping> garden() {
        var keys = new LinkedHashMap<String, KeyMapping>();
        keys.put("tptoplot", GardenKeyMappings.TELEPORT_TO_PLOT);
        keys.put("setspawn", GardenKeyMappings.SET_SPAWN);
        keys.put("warp_garden", GardenKeyMappings.WARP_GARDEN);
        return keys;
    }
    static void tick() {
        if (EviemodSettings.STORE.error() != null || System.nanoTime() < retryAfter) return;
        var configured = EviemodSettings.features().garden.keys;
        var current = new LinkedHashMap<String, String>();
        garden().forEach((name, mapping) -> current.put(name, mapping.saveString()));
        try {
            if (configuredSnapshot == null) {
                // Minecraft has loaded its native options by the first client tick.
                var next = EviemodSettings.STORE.values().copy();
                current.forEach(next.features.garden.keys::putIfAbsent);
                if (!next.features.garden.keys.equals(configured)) EviemodSettings.STORE.save(next);
                apply();
            } else if (!configuredSnapshot.equals(configured)) apply();
            else if (!current.equals(appliedSnapshot)) {
                // Edits from Minecraft's Controls screen remain supported too.
                var next = EviemodSettings.STORE.values().copy();
                current.forEach((name, value) -> {
                    if (!value.equals(appliedSnapshot.get(name))) next.features.garden.keys.put(name, value);
                });
                EviemodSettings.STORE.save(next);
                configuredSnapshot = Map.copyOf(next.features.garden.keys);
                appliedSnapshot = Map.copyOf(current);
            }
        } catch (IOException | RuntimeException e) {
            LoggerFactory.getLogger("eviemod").warn("Could not save Garden key bindings", e);
            apply();
            retryAfter = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
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
        configuredSnapshot = Map.copyOf(keys);
        var current = new LinkedHashMap<String, String>();
        garden().forEach((name, mapping) -> current.put(name, mapping.saveString()));
        appliedSnapshot = Map.copyOf(current);
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal;

import java.util.Locale;
import java.util.Set;

/** Startup policy, kept independent of Minecraft and native loading. */
public final class MetalSupport {
    private MetalSupport() {}

    public static String unavailableReason(boolean enabled, String os, String arch, String osVersion, Set<String> mods) {
        if (!enabled) return "disabled (opt in with -Deviemod.metal=true)";
        if (!os.toLowerCase(Locale.ROOT).startsWith("mac")) return "requires macOS";
        if (!arch.equalsIgnoreCase("aarch64") && !arch.equalsIgnoreCase("arm64")) return "requires an ARM64 JVM on Apple Silicon";
        try {
            if (Integer.parseInt(osVersion.split("\\.")[0]) < 14) return "requires macOS 14 or newer";
        } catch (NumberFormatException e) { return "unknown macOS version"; }
        // These replace rendering or call OpenGL directly. Expand only for verified conflicts.
        for (String mod : Set.of("iris", "vulkanmod", "metallum", "metalcraft", "metalrender")) {
            if (mods.contains(mod)) return "unsupported renderer installed: " + mod;
        }
        return null;
    }
}

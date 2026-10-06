package dev.eviemod.metal;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetalSupportTest {
    @Test void defaultsToEnabledAndPreservesJvmOverride() {
        assertTrue(MetalSupport.isEnabled(null));
        assertTrue(MetalSupport.isEnabled("true"));
        assertTrue(MetalSupport.isEnabled("TRUE"));
        assertFalse(MetalSupport.isEnabled("false"));
        assertFalse(MetalSupport.isEnabled("FALSE"));
        assertFalse(MetalSupport.isEnabled(""));
        assertFalse(MetalSupport.isEnabled("invalid"));
        assertNull(MetalSupport.unavailableReason(MetalSupport.isEnabled(null),
                "Mac OS X", "aarch64", "27.0", Set.of()));
        assertNotNull(MetalSupport.unavailableReason(MetalSupport.isEnabled("false"),
                "Mac OS X", "aarch64", "27.0", Set.of()));
    }
    @Test void requiresSupportedHostAndNativeJvm() {
        assertNotNull(MetalSupport.unavailableReason(true, "Linux", "aarch64", "27.0", Set.of()));
        assertNotNull(MetalSupport.unavailableReason(true, "Mac OS X", "x86_64", "27.0", Set.of()));
        assertNotNull(MetalSupport.unavailableReason(true, "Mac OS X", "aarch64", "13.6", Set.of()));
        assertNotNull(MetalSupport.unavailableReason(true, "Mac OS X", "aarch64", "unknown", Set.of()));
        assertNull(MetalSupport.unavailableReason(true, "Mac OS X", "aarch64", "14.0", Set.of()));
        assertNull(MetalSupport.unavailableReason(true, "Mac OS X", "arm64", "27.0", Set.of("eviemod")));
    }
    @Test void knownRendererConflictsFallBackBeforeLoadingNatives() {
        for (String mod : Set.of("iris", "vulkanmod", "metallum", "metalcraft", "metalrender")) {
            assertTrue(MetalSupport.unavailableReason(true, "Mac OS X", "aarch64", "27.0", Set.of(mod)).contains(mod));
        }
    }
    @Test void sodiumIsAllowedWithDefaultMetalAndCanBeDisabled() {
        assertNull(MetalSupport.unavailableReason(MetalSupport.isEnabled(null), "Mac OS X", "aarch64", "27.0", Set.of("sodium")));
        assertNotNull(MetalSupport.unavailableReason(MetalSupport.isEnabled("false"), "Mac OS X", "aarch64", "27.0", Set.of("sodium")));
        assertNotNull(MetalSupport.unavailableReason(true, "Mac OS X", "aarch64", "27.0", Set.of("sodium", "iris")));
    }
}

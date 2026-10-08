// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.compat.sumr;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

/** Apply the interface automatically when the inspected optional binary is installed. */
public final class SumrMixinPlugin implements IMixinConfigPlugin {
    static boolean supportedVersion(String version) {
        return "1.5.1+26.2".equals(version);
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        String version = FabricLoader.getInstance().getModContainer("eg_stop_unloading_my_shaders")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(null);
        return supportedVersion(version);
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}

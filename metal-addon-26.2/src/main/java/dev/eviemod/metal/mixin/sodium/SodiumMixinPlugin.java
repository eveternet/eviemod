// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin.sodium;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
/** 26.2 has no GL-device interception: its terrain frontend uses Blaze3D resources. */
public final class SodiumMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return FabricLoader.getInstance().getModContainer("sodium").map(m -> m.getMetadata().getVersion().getFriendlyString().equals("0.9.2+mc26.2")).orElse(false);
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}

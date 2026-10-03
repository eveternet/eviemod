// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin.sodium;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.*;

/** Optional pinned boundary. Runtime guards preserve Sodium's GL behavior when Metal is disabled. */
public final class SodiumMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        var sodium = FabricLoader.getInstance().getModContainer("sodium");
        return sodium.isPresent() && sodium.get().getMetadata().getVersion().getFriendlyString().equals("0.9.2+mc26.1.2");
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        // Mixin cannot inject into another mixin's private handlers before those handlers are merged.
        // These priority-500 anchors run after Sodium. Match exact owner/name/descriptor and fail on drift.
        if (mixin.endsWith("LifecycleAnchorsMixin$Minecraft")) {
            int changed = 0;
            for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions) {
                if (!(insn instanceof MethodInsnNode call) || !call.owner.equals("org/lwjgl/opengl/GL32C")) continue;
                String replacement = switch (call.name + call.desc) {
                    case "glFenceSync(II)J" -> "fence";
                    case "glClientWaitSync(JIJ)I" -> "waitFor";
                    case "glDeleteSync(J)V" -> "delete";
                    default -> null;
                };
                if (replacement != null && method.name.contains("sodium")) {
                    call.owner = "dev/eviemod/metal/compat/sodium/SodiumFrameSync";
                    call.name = replacement;
                    changed++;
                }
            }
            if (changed != 3) throw new IllegalStateException("Sodium frame fence boundary changed: " + changed);
        }
        if (mixin.endsWith("LifecycleAnchorsMixin$RenderSystem")) {
            int changed = 0;
            for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call && call.owner.endsWith("/GlContextInfo") && call.name.equals("create")) {
                    if (!method.desc.equals("()V")) throw new IllegalStateException("Sodium GL context check signature changed");
                    gate(method, false);
                    changed++;
                }
            }
            if (changed != 1) throw new IllegalStateException("Sodium GL context-check boundary changed: " + changed);
        }
        if (mixin.endsWith("LifecycleAnchorsMixin$Options")) {
            int changed = 0;
            for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call && call.owner.equals("org/lwjgl/opengl/GL") && call.name.equals("getCapabilities")) {
                    if (!method.desc.endsWith(")Z")) throw new IllegalStateException("Sodium GL option predicate changed");
                    gate(method, true);
                    changed++;
                }
            }
            if (changed != 1) throw new IllegalStateException("Sodium GL context option boundary changed: " + changed);
        }
    }

    private static void gate(MethodNode method, boolean predicate) {
        InsnList guard = new InsnList();
        LabelNode original = new LabelNode();
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/eviemod/metal/MetalBootstrap", "isActive", "()Z", false));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, original));
        if (predicate) guard.add(new InsnNode(Opcodes.ICONST_0));
        guard.add(new InsnNode(predicate ? Opcodes.IRETURN : Opcodes.RETURN));
        guard.add(original);
        method.instructions.insert(guard);
    }
}

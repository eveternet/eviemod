package dev.eviemod.paintbrush.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.eviemod.paintbrush.HelmetSkins;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class HelmetSkinArmorMixin {
    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void paintbrush$hideReplacedHelmet(PoseStack pose, SubmitNodeCollector collector, ItemStack stack,
            EquipmentSlot slot, int light, HumanoidRenderState state, CallbackInfo ci) {
        if (slot == EquipmentSlot.HEAD && HelmetSkins.resolve(stack) != null) ci.cancel();
    }
}

package dev.eviemod.paintbrush.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.eviemod.paintbrush.PaintBrushClient;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {
    // Replace only this render call's asset; never edit EQUIPPABLE on the real stack.
    @org.spongepowered.asm.mixin.injection.ModifyVariable(
        method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at = @At("HEAD"), argsOnly = true
    )
    private net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> paintbrush$asset(
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> original,
            @Local(argsOnly = true) ItemStack stack) {
        var models = PaintBrushClient.models();
        return dev.eviemod.paintbrush.ItemAppearance.equipmentAsset(stack,
            models == null ? null : models.get(dev.eviemod.paintbrush.SkyBlockUuid.read(stack)), original);
    }

    @ModifyExpressionValue(
        method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/DyedItemColor;getOrDefault(Lnet/minecraft/world/item/ItemStack;I)I")
    )
    private int paintbrush$wornColor(int original, @Local(argsOnly = true) Object state, @Local(argsOnly = true) ItemStack stack) {
        return PaintBrushClient.resolveWornColor(state, stack, original);
    }
}

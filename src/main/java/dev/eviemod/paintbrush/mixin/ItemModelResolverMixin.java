package dev.eviemod.paintbrush.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.eviemod.paintbrush.PaintBrushClient;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    // Only replace the renderer's lookup result. The real stack and its components
    // remain untouched, including the stack passed to dynamic item model predicates.
    @ModifyExpressionValue(
        method = {"appendItemLayers", "shouldPlaySwapAnimation", "swapAnimationScale"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;")
    )
    private Object paintbrush$model(Object original, @Local(argsOnly = true) ItemStack stack) {
        return PaintBrushClient.resolve(stack, (Identifier) original);
    }
}

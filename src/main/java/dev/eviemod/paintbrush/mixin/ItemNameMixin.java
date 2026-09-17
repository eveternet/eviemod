package dev.eviemod.paintbrush.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.eviemod.paintbrush.PaintBrushClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public abstract class ItemNameMixin {
    // Replace display text only; CUSTOM_NAME and serialized stack data stay intact.
    @ModifyReturnValue(method = "getHoverName", at = @At("RETURN"))
    private Component paintbrush$name(Component original) {
        return PaintBrushClient.resolveName((ItemStack) (Object) this, original);
    }
}

package dev.eviemod.paintbrush.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.eviemod.paintbrush.PaintBrushClient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DyedItemColor.class)
public abstract class DyedItemColorMixin {
    // Shared by item dye tinting and worn equipment rendering. Never writes DYED_COLOR.
    @ModifyReturnValue(method = "getOrDefault", at = @At("RETURN"))
    private static int paintbrush$color(int original, ItemStack stack, int fallback) {
        return PaintBrushClient.resolveColor(stack, original);
    }
}

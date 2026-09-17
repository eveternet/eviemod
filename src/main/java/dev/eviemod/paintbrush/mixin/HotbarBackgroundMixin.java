package dev.eviemod.paintbrush.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.eviemod.paintbrush.RarityBackgrounds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class HotbarBackgroundMixin {
    // Ordinal zero is the nine main hotbar slots. The later invocations draw the offhand.
    @WrapOperation(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V", ordinal = 0))
    private void eviemod$hotbarBackground(Gui gui, GuiGraphicsExtractor graphics, int x, int y,
            DeltaTracker delta, Player player, ItemStack stack, int seed, Operation<Void> original) {
        RarityBackgrounds.draw(graphics, stack, x, y);
        original.call(gui, graphics, x, y, delta, player, stack, seed);
    }
}

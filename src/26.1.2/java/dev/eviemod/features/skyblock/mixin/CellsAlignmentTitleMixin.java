package dev.eviemod.features.skyblock.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.eviemod.features.dungeons.CellsAlignmentClient;
import dev.eviemod.features.dungeons.CellsAlignmentTimer;
import dev.eviemod.features.dungeons.CellsAlignmentTitle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Title-only draw replacement; vanilla subtitle state and its timer remain untouched. */
@Mixin(Gui.class)
public abstract class CellsAlignmentTitleMixin {
    @Unique private CellsAlignmentTimer.Frame eviemod$cellsFrame;

    @Inject(method = "extractTitle", at = @At("HEAD"))
    private void eviemod$captureCellsTitle(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        eviemod$cellsFrame = CellsAlignmentClient.frame();
        CellsAlignmentTitle.extract(graphics, Minecraft.getInstance().font, eviemod$cellsFrame);
    }

    @WrapOperation(method = "extractTitle", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V",
        ordinal = 0))
    private void eviemod$onlyReplaceMainTitle(GuiGraphicsExtractor graphics, Font font, Component text,
                                             int x, int y, int width, int color, Operation<Void> original) {
        if (eviemod$cellsFrame == null) original.call(graphics, font, text, x, y, width, color);
    }
}

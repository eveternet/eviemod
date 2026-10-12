package dev.eviemod.paintbrush.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** No public container geometry API exists in either supported Minecraft target. */
@Mixin(AbstractContainerScreen.class)
public interface NavigationContainerAccessor {
    @Accessor("leftPos") int eviemod$navigationLeft();
    @Accessor("topPos") int eviemod$navigationTop();
    @Accessor("imageWidth") int eviemod$navigationWidth();
    @Accessor("imageHeight") int eviemod$navigationHeight();
}

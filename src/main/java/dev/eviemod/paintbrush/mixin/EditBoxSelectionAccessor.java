package dev.eviemod.paintbrush.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditBox.class)
public interface EditBoxSelectionAccessor {
    @Accessor("highlightPos") int paintbrush$selectionAnchor();
}

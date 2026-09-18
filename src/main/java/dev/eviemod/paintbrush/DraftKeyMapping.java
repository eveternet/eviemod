package dev.eviemod.paintbrush;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/** Public KeyMapping API adapter for Dandelion's standard editor, without changing live input. */
final class DraftKeyMapping extends KeyMapping {
    private static final Map<String, DraftKeyMapping> EDITORS = new HashMap<>();
    private InputConstants.Key draft = InputConstants.UNKNOWN;
    private Consumer<InputConstants.Key> changed = key -> {};
    private DraftKeyMapping(String id) {
        // The superclass remains unbound, including when Dandelion rebuilds the native key map.
        super("key.eviemod.draft." + id, InputConstants.Type.KEYSYM, -1, Category.MISC);
    }
    static DraftKeyMapping bind(String id, InputConstants.Key key, Consumer<InputConstants.Key> changed) {
        var mapping = EDITORS.computeIfAbsent(id, DraftKeyMapping::new);
        mapping.draft = key;
        mapping.changed = changed;
        return mapping;
    }
    @Override public void setKey(InputConstants.Key key) { draft = key; changed.accept(key); }
    @Override public Component getTranslatedKeyMessage() { return draft.getDisplayName(); }
    @Override public boolean isDefault() { return draft.equals(InputConstants.UNKNOWN); }
    @Override public String saveString() { return draft.getName(); }
}

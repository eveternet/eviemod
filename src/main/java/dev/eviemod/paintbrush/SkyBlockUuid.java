package dev.eviemod.paintbrush;

import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public final class SkyBlockUuid {
    private static final Pattern UUID_SHAPE = Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private SkyBlockUuid() {}

    public static UUID read(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        var tag = data.copyTag();
        // Current Hypixel format: both fields live directly in custom_data.
        if (tag.getStringOr("id", "").isBlank()) return null;
        return parse(tag.getStringOr("uuid", ""));
    }

    public static UUID parse(String value) {
        // UUID.fromString alone accepts abbreviated groups, so validate the full shape.
        if (value == null || !UUID_SHAPE.matcher(value).matches()) return null;
        return UUID.fromString(value);
    }
}

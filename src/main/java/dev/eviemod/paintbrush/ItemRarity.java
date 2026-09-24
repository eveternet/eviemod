package dev.eviemod.paintbrush;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Lookup behavior verified against the supplied Skyblocker 6.10.2+26.1.2 JAR. */
public enum ItemRarity {
    COMMON(0xffffff), UNCOMMON(0x55ff55), RARE(0x5555ff), EPIC(0xaa00aa),
    LEGENDARY(0xffaa00), MYTHIC(0xff55ff), DIVINE(0x55ffff),
    SPECIAL(0xff5555), VERY_SPECIAL(0xff5555), ULTIMATE(0xaa0000), ADMIN(0xaa0000);
    public final int rgb;
    ItemRarity(int rgb) { this.rgb = rgb; }

    private static Optional<String> containsName(String text) {
        String match = null;
        for (ItemRarity rarity : values()) {
            if (text.contains(rarity.name().replace('_', ' '))) match = rarity.name();
        }
        if (text.contains("UNKNOWN")) match = "UNKNOWN";
        return Optional.ofNullable(match);
    }
    private static ItemRarity known(String name) { return name.equals("UNKNOWN") ? null : valueOf(name); }

    static ItemRarity read(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return read(stack, data == null ? null : data.copyTag());
    }
    static ItemRarity read(ItemStack stack, CompoundTag tag) {
        if (stack.isEmpty()) return null;
        if (tag != null && tag.getStringOr("id", "").equals("PET")) {
            // A malformed pet is UNKNOWN; Skyblocker does not fall through to lore/style.
            try {
                String raw = tag.getStringOr("petInfo", "");
                Pet pet = Pet.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(raw)).result().orElse(null);
                if (pet == null) return null;
                ItemRarity tier = known(pet.tier());
                if (pet.heldItem().filter("PET_ITEM_TIER_BOOST"::equals).isPresent())
                    return tier == null ? COMMON : tier == ADMIN ? null : values()[tier.ordinal() + 1];
                return tier;
            } catch (RuntimeException ignored) { return null; }
        }
        // Scan all lore bottom-to-top; lore need not be the final line or have custom data.
        var lore = stack.get(DataComponents.LORE);
        if (lore != null) for (int i = lore.lines().size() - 1; i >= 0; i--) {
            var match = containsName(lore.lines().get(i).getString());
            if (match.isPresent()) return known(match.get());
        }
        var style = stack.get(DataComponents.TOOLTIP_STYLE);
        if (style != null && style.getNamespace().equals("hypixel_skyblock"))
            return containsName(style.getPath().toUpperCase(Locale.ENGLISH)).map(ItemRarity::known).orElse(null);
        return null;
    }

    private record Pet(String type, double exp, String tier, Optional<String> uuid,
                       Optional<String> heldItem, Optional<String> skin) {
        private static final Codec<String> TIER = Codec.STRING.comapFlatMap(value -> {
            try { if (!value.equals("UNKNOWN")) valueOf(value); return DataResult.success(value); }
            catch (IllegalArgumentException e) { return DataResult.error(() -> "Unknown pet tier"); }
        }, value -> value);
        private static final Codec<Pet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(Pet::type),
            Codec.DOUBLE.optionalFieldOf("exp", 0d).forGetter(Pet::exp),
            TIER.fieldOf("tier").forGetter(Pet::tier),
            Codec.STRING.optionalFieldOf("uuid").forGetter(Pet::uuid),
            Codec.STRING.optionalFieldOf("heldItem").forGetter(Pet::heldItem),
            Codec.STRING.optionalFieldOf("skin").forGetter(Pet::skin)
        ).apply(instance, Pet::new));
    }

    static boolean missingMetadata(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return missingMetadata(stack, data == null ? null : data.copyTag());
    }
    static boolean missingMetadata(ItemStack stack, CompoundTag tag) {
        if (tag != null && tag.getStringOr("id", "").equals("PET")) return false;
        var lore = stack.get(DataComponents.LORE);
        return stack.get(DataComponents.TOOLTIP_STYLE) == null && (lore == null || lore.lines().isEmpty());
    }
}

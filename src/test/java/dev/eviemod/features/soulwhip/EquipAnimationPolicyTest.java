package dev.eviemod.features.soulwhip;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EquipAnimationPolicyTest {
    private static final String UUID = "d320530e-052d-48fa-bf56-09c2e1a4a12d";
    private final EquipAnimationPolicy policy = new EquipAnimationPolicy();
    private final Object player = new Object();

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
            .build(dev.eviemod.compat.VersionFixtures.registries())
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    }

    private static ItemStack whip(String uuid) {
        var stack = new ItemStack(Items.FISHING_ROD);
        var tag = new CompoundTag();
        tag.putString("id", "SOUL_WHIP");
        if (uuid != null) tag.putString("uuid", uuid);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private float scale(float original, ItemStack held) {
        return policy.itemSwapScale(original, true, player, 0, held, held, false);
    }

    @Test void emptyAirDipIsSuppressedThroughoutCooldownAndRepeatedMisses() {
        var held = whip(UUID);
        policy.attack(true, true, player, 0, held);
        for (float vanilla : new float[]{0.1F, 0.2F, 0.5F, 0.9F}) assertEquals(1.0F, scale(vanilla, held));
        policy.attack(true, true, player, 0, held);
        assertEquals(1.0F, scale(0.1F, held));
        assertEquals(1.0F, scale(1.0F, held));
        assertEquals(0.2F, scale(0.2F, held), "Recovered cooldown cannot suppress a later reset");
    }

    @Test void normalAttacksAndDisabledFeatureRetainVanillaScale() {
        var held = whip(UUID);
        assertEquals(0.2F, scale(0.2F, held));
        policy.attack(false, true, player, 0, held);
        assertEquals(0.2F, scale(0.2F, held));
        policy.attack(true, true, player, 0, held);
        policy.attack(true, false, player, 0, held); // Entity, block or piercing attack.
        assertEquals(0.2F, scale(0.2F, held));
    }

    @Test void slotSwitchEndsSuppressionEvenWithSameItem() {
        var held = whip(UUID);
        policy.attack(true, true, player, 0, held);
        assertEquals(0.2F, policy.itemSwapScale(0.2F, true, player, 1, held, held, false));
        assertEquals(0.2F, scale(0.2F, held), "Switching back must not revive the old miss");
        policy.attack(true, true, player, 0, held);
        assertEquals(0.2F, policy.itemSwapScale(0.2F, true, player, 0, held, held, true));
    }

    @Test void itemReplacementKeepsEquipTransition() {
        var held = whip(UUID);
        for (var replacement : new ItemStack[]{new ItemStack(Items.DIAMOND_SWORD),
                whip("eb11aa00-052d-48fa-bf56-09c2e1a4a12d"), ItemStack.EMPTY}) {
            policy.attack(true, true, player, 0, held);
            assertEquals(0.2F, policy.itemSwapScale(0.2F, true, player, 0, replacement, held, false));
            assertEquals(0.2F, scale(0.2F, replacement));
        }
        policy.attack(true, true, player, 0, held);
        assertEquals(0.2F, policy.itemSwapScale(0.2F, true, player, 0, held, ItemStack.EMPTY, false),
            "An item still being equipped must finish its vanilla transition");
    }

    @Test void soulWhipUpdatesKeepContinuityWithoutMutatingStacks() {
        var before = whip(UUID);
        var updated = before.copy();
        updated.set(DataComponents.CUSTOM_NAME, Component.literal("Updated Soul Whip"));
        var snapshot = before.copy();
        assertTrue(EquipAnimationPolicy.sameHeldItem(before, updated));
        policy.attack(true, true, player, 0, before);
        assertEquals(1.0F, scale(0.2F, updated));
        assertTrue(ItemStack.matches(before, snapshot));
        assertNotEquals(before.get(DataComponents.CUSTOM_NAME), updated.get(DataComponents.CUSTOM_NAME));
    }

    @Test void unknownOrMalformedIdentityDoesNotHideItemChanges() {
        for (String uuid : new String[]{null, "broken", "1-1-1-1-1"}) {
            var before = whip(uuid);
            var renamed = before.copy();
            renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Different item"));
            assertFalse(EquipAnimationPolicy.sameHeldItem(before, renamed));
            assertFalse(EquipAnimationPolicy.sameHeldItem(whip(UUID), before));
            assertTrue(EquipAnimationPolicy.sameHeldItem(before, before.copy()));
        }
    }

    @Test void disableDisconnectAndPlayerReplacementClearMissState() {
        var held = whip(UUID);
        policy.attack(true, true, player, 0, held);
        assertEquals(0.2F, policy.itemSwapScale(0.2F, false, player, 0, held, held, false));
        assertEquals(0.2F, scale(0.2F, held));
        for (Object nextPlayer : new Object[]{null, new Object()}) {
            policy.attack(true, true, player, 0, held);
            assertEquals(0.2F, policy.itemSwapScale(0.2F, true, nextPlayer, 0, held, held, false));
            assertEquals(0.2F, scale(0.2F, held));
        }
        policy.attack(true, true, player, 0, held);
        policy.clear();
        assertEquals(0.2F, scale(0.2F, held));
    }
}

package dev.eviemod.features.compass;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompassProtocolTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
            .build(net.minecraft.data.registries.VanillaRegistries.createLookup())
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    }
    @Test void identityUsesStructuredDataAndDoesNotTrustTheDisplayName() {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Wishing Compass"));
        assertEquals("", WishingCompassClient.itemId(stack));
        var data = new CompoundTag(); data.putString("id", "WISHING_COMPASS");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        assertEquals("WISHING_COMPASS", WishingCompassClient.itemId(stack));
        var wrapped = new CompoundTag(); wrapped.put("ExtraAttributes", data);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(wrapped));
        assertEquals("WISHING_COMPASS", WishingCompassClient.itemId(stack));
        wrapped.putString("id", "OTHER_ITEM"); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(wrapped));
        assertEquals("OTHER_ITEM", WishingCompassClient.itemId(stack));
    }
    @Test void oneCountCompassPredictionCanEmptyTheStackWithoutLosingItsSentPacketIdentity() {
        var stack = new ItemStack(Items.PLAYER_HEAD);
        var data = new CompoundTag(); data.putString("id", "WISHING_COMPASS");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        Object world = new Object(), connection = new Object(), player = new Object();
        var before = new CompassUseIdentity.Snapshot(world, connection, player, "mini1",
            WishingCompassClient.itemId(stack).equals("WISHING_COMPASS"), false);
        stack.shrink(1); // BlockItem.place's local prediction consumes one item before send.
        assertTrue(stack.isEmpty()); assertEquals("", WishingCompassClient.itemId(stack));
        var packet = new net.minecraft.network.protocol.game.ServerboundUseItemOnPacket(
            net.minecraft.world.InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.ZERO,
                net.minecraft.core.Direction.UP, net.minecraft.core.BlockPos.ZERO, false), 7);
        var identities = new CompassUseIdentity();
        identities.bind(packet, false, before);
        assertTrue(identities.consume(packet, false, world, connection, player, "mini1"));
        assertFalse(identities.consume(packet, false, world, connection, player, "mini1"));
    }
    @Test void packetCentreIsUsableRegardlessOfHowVanillaRandomizesItsRenderedParticles() {
        assertTrue(WishingCompassClient.usablePacket(packet(0, 1)));
        assertTrue(WishingCompassClient.usablePacket(packet(1, 0)));
        assertTrue(WishingCompassClient.usablePacket(packet(1, 1)));
        assertTrue(WishingCompassClient.usablePacket(packet(2, 0)));
        assertTrue(WishingCompassClient.usablePacket(packet(25, 1)));
        assertFalse(WishingCompassClient.usablePacket(packet(-1, 0)));
        assertFalse(WishingCompassClient.usablePacket(new ClientboundLevelParticlesPacket(
            ParticleTypes.FLAME, false, false, 500, 100, 500, 0, 0, 0, 0, 1)));
    }
    private static ClientboundLevelParticlesPacket packet(int count, float offset) {
        return new ClientboundLevelParticlesPacket(ParticleTypes.HAPPY_VILLAGER, false, false,
            500, 100, 500, offset, 0, 0, 0, count);
    }
    @Test void changingScentCountdownDoesNotChangeTheTargetFingerprint() {
        var first = WishingCompassClient.visiblePrerequisites(java.util.List.of("Crystals:", "§aKing's Scent: 13m 02s", "§bJade: ✔ Found"));
        var next = WishingCompassClient.visiblePrerequisites(java.util.List.of("Jade: ✔ Found", "King's Scent: 13m 01s", "Crystals:", "Other player"));
        assertEquals(first, next);
        assertNotEquals(first, WishingCompassClient.visiblePrerequisites(java.util.List.of("Crystals:", "Jade: ✔ Found")));
        assertNotEquals(first, WishingCompassClient.visiblePrerequisites(java.util.List.of("Crystals:", "Jade: ✔ Placed", "King's Scent: 12m 58s")));
    }
    @Test void regionChangesDiscardUnsolvedPairsButPreserveNavigation() {
        var states = CompassContext.prerequisites(CompassContextTest.rows("✔ Found"), "");
        var prior = new WishingCompassClient.TargetContext(CompassContext.Region.JUNGLE, true, states);
        var acrossBorder = new WishingCompassClient.TargetContext(CompassContext.Region.MITHRIL, true, states);
        assertFalse(prior.compatibleWith(acrossBorder, false));
        assertTrue(prior.compatibleWith(acrossBorder, true));
        assertFalse(prior.compatibleWith(new WishingCompassClient.TargetContext(CompassContext.Region.MITHRIL, false, prior.prerequisites()), true));
        var placed = CompassContext.prerequisites(CompassContextTest.rows("✔ Placed"), "");
        assertFalse(prior.compatibleWith(new WishingCompassClient.TargetContext(CompassContext.Region.JUNGLE, true, placed), true));
    }
}

package dev.eviemod.paintbrush;

import dev.eviemod.features.skyblock.SkyblockContext;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.HypixelNetworking;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** Uses the same location event and SKYBLOCK game-type check as Skyblocker 6.10.2. */
final class SkyBlockSession {
    private static boolean skyblock;
    static void init() {
        var events = new Object2IntOpenHashMap<net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<net.azureaaron.hmapi.network.packet.s2c.HypixelS2CPacket>>();
        events.put(LocationUpdateS2CPacket.ID, 1);
        HypixelNetworking.registerToEvents(events);
        HypixelPacketEvents.LOCATION_UPDATE.register(packet -> {
            if (packet instanceof LocationUpdateS2CPacket location) update(location.serverType().orElse(""));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> update(""));
    }
    static void update(String serverType) {
        boolean next = "SKYBLOCK".equals(serverType) && SkyblockContext.isHypixelServer(Minecraft.getInstance());
        if (next != skyblock) RarityBackgrounds.clear();
        skyblock = next;
        if (next) TexturePackBypasser.enteredSkyBlock();
    }
    static boolean active() {
        var client = Minecraft.getInstance();
        return allowed(skyblock, SkyblockContext.isHypixelServer(client), FabricLoader.getInstance().isDevelopmentEnvironment(),
            client.level == null || client.isLocalServer());
    }
    static boolean allowed(boolean skyblock, boolean hypixel, boolean development, boolean localOrNoWorld) {
        // Skyblocker permits its own local development fixtures; never a release bypass.
        return (skyblock && hypixel && !localOrNoWorld) || (development && localOrNoWorld);
    }
}

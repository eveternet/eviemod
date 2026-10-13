package dev.eviemod.features.compass;

import dev.eviemod.features.skyblock.SkyblockContext;
import dev.eviemod.paintbrush.EviemodSettings;
import java.util.Locale;
import java.util.Objects;
import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Own vanilla use callbacks arm a short packet window. Never sends, cancels or shares anything. */
public final class WishingCompassClient {
    private static final CompassSolver SOLVER = new CompassSolver();
    private static String session;
    private static Object level, connection;
    private static String context;
    private WishingCompassClient() {}
    private static long now() { return System.nanoTime() / 1_000_000; }
    public static void clear() { SOLVER.reset(); context = null; }
    public static void initialize() {
        // SkyBlockSession already subscribes to this HM API event; reuse that subscription.
        HypixelPacketEvents.LOCATION_UPDATE.register(packet -> {
            if (!(packet instanceof LocationUpdateS2CPacket location)) return;
            var client = Minecraft.getInstance();
            String next = SkyblockContext.isHypixelServer(client)
                && location.serverType().orElse("").equals("SKYBLOCK")
                && location.mode().orElse("").equals("crystal_hollows")
                ? location.serverName() + ":" + location.mode().orElse("") + ":" + location.map().orElse("") : null;
            session = next; SOLVER.session(next);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            session = null; SOLVER.session(null); level = null; connection = null;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player == Minecraft.getInstance().player) use(player.getItemInHand(hand));
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player == Minecraft.getInstance().player) use(player.getItemInHand(hand));
            return InteractionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!active(client)) { clear(); return; }
            refreshWorld(client);
            String nextContext = targetContext(client);
            if (!Objects.equals(context, nextContext)) { clear(); context = nextContext; }
            SOLVER.tick(now());
            var estimate = SOLVER.estimate();
            if (estimate == null || client.options.hideGui) return;
            var p = estimate.position();
            Vec3 position = new Vec3(p.x(), p.y(), p.z());
            try (var ignored = client.collectPerTickGizmos()) {
                Gizmos.circle(position, (float) estimate.uncertainty(), GizmoStyle.stroke(0xFF80FFAA, 2)).setAlwaysOnTop();
                Gizmos.line(position, position.add(0, 4, 0), 0xFF80FFAA, 3).setAlwaysOnTop();
                Gizmos.billboardText(label(estimate) + " | " + Math.round(client.player.position().distanceTo(position)) + "m",
                    position.add(0, 5, 0), TextGizmo.Style.forColorAndCentered(0xFF80FFAA)).setAlwaysOnTop();
            }
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("eviemod", "wishing_compass"), (graphics, delta) -> {
            var client = Minecraft.getInstance();
            if (!active(client) || client.level != level || client.getConnection() != connection || client.options.hideGui) return;
            if (!SOLVER.observing() && SOLVER.estimate() == null && SOLVER.status().equals("Use a Wishing Compass to begin.")) return;
            String text = SOLVER.estimate() == null ? SOLVER.status() : label(SOLVER.estimate());
            graphics.text(client.font, text, (graphics.guiWidth() - client.font.width(text)) / 2, graphics.guiHeight() / 2 + 36, 0xFF80FFAA, true);
        });
    }
    private static String label(CompassSolver.Estimate estimate) {
        var p = estimate.position();
        return "Compass estimate ~" + Math.round(p.x()) + " " + Math.round(p.y()) + " " + Math.round(p.z())
            + " (±" + (int) Math.ceil(estimate.uncertainty()) + " blocks)";
    }
    private static boolean active(Minecraft client) {
        return EviemodSettings.features().skyblock.wishingCompass && session != null
            && client.level != null && client.player != null && SkyblockContext.isHypixelServer(client);
    }
    private static void refreshWorld(Minecraft client) {
        if (client.level != level || client.getConnection() != connection) {
            clear(); level = client.level; connection = client.getConnection();
        }
    }
    private static void use(ItemStack stack) {
        var client = Minecraft.getInstance();
        if (!active(client) || !itemId(stack).equals("WISHING_COMPASS")) return;
        refreshWorld(client);
        context = targetContext(client);
        Vec3 pos = client.player.position();
        SOLVER.use(new CompassSolver.Point(pos.x, pos.y, pos.z), context, now());
    }
    public static void particle(ClientboundLevelParticlesPacket packet) {
        var client = Minecraft.getInstance();
        if (!active(client) || packet.getParticle().getType() != ParticleTypes.HAPPY_VILLAGER) return;
        refreshWorld(client);
        // Burst packets have randomized positions and cannot provide a reliable observation.
        if (packet.getCount() > 1 || packet.getXDist() != 0 || packet.getYDist() != 0 || packet.getZDist() != 0) return;
        SOLVER.particle(new CompassSolver.Point(packet.getX(), packet.getY(), packet.getZ()), now());
    }
    static String itemId(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        var tag = data.copyTag();
        String direct = tag.getStringOr("id", "");
        return direct.isEmpty() ? tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id", "") : direct;
    }
    private static String targetContext(Minecraft client) {
        Vec3 pos = client.player.position();
        // Region changes and visible target prerequisites invalidate a pair, without guessing a structure name.
        String zone = pos.y < 64 ? "magma" : (pos.x < 512 ? "west" : "east") + (pos.z < 512 ? "north" : "south");
        boolean key = client.player.getInventory().getNonEquipmentItems().stream().anyMatch(stack -> itemId(stack).equals("JUNGLE_KEY"));
        var prerequisites = new java.util.TreeSet<String>();
        for (var info : client.getConnection().getOnlinePlayers()) {
            var display = info.getTabListDisplayName();
            if (display == null) continue;
            String line = display.getString().replaceAll("§.", "").toLowerCase(Locale.ROOT).trim();
            if (line.contains("king's scent") || line.contains("crystal") &&
                (line.contains("amethyst") || line.contains("amber") || line.contains("jade") || line.contains("sapphire") || line.contains("topaz")))
                prerequisites.add(line);
        }
        return zone + ":" + key + ":" + prerequisites;
    }
}

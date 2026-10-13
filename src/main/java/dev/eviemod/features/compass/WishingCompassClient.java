package dev.eviemod.features.compass;

import dev.eviemod.features.skyblock.SkyblockContext;
import dev.eviemod.compat.ClientUi;
import dev.eviemod.paintbrush.EviemodSettings;
import java.util.Objects;
import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Own sent use packets arm a short observation. Never sends, cancels or shares anything. */
public final class WishingCompassClient {
    private static final CompassSolver SOLVER = new CompassSolver();
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("eviemod");
    private static String session;
    private static Object level, connection;
    private static TargetContext context;
    private static CompassContext.FooterSnapshot footer;
    private static int packetCount, largestBurst;
    private static boolean packetOffsets;
    record TargetContext(CompassContext.Region region, boolean key, CompassContext.Prerequisites prerequisites) {
        boolean compatibleWith(TargetContext next, boolean navigating) {
            return next != null && key == next.key && prerequisites.equals(next.prerequisites)
                && prerequisites.readyFor(region) && next.region != CompassContext.Region.UNKNOWN
                && (navigating || region == next.region);
        }
    }
    private WishingCompassClient() {}
    private static long now() { return System.nanoTime() / 1_000_000; }
    public static void clear() {
        SOLVER.reset(); context = null; packetCount = 0; largestBurst = 0; packetOffsets = false;
    }
    public static void initialize() {
        // SkyBlockSession already subscribes to this HM API event; reuse that subscription.
        HypixelPacketEvents.LOCATION_UPDATE.register(packet -> {
            if (!(packet instanceof LocationUpdateS2CPacket location)) return;
            var client = Minecraft.getInstance();
            String next = SkyblockContext.isHypixelServer(client)
                && location.serverType().orElse("").equals("SKYBLOCK")
                && location.mode().orElse("").equals("crystal_hollows")
                ? location.serverName() + ":" + location.lobbyName().orElse("") + ":" + location.mode().orElse("") + ":" + location.map().orElse("") : null;
            if (!Objects.equals(session, next)) footer = null;
            session = next; SOLVER.session(next);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            session = null; SOLVER.session(null); clear(); level = null; connection = null; footer = null;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!active(client)) { clear(); return; }
            refreshWorld(client);
            if (SOLVER.hasObservations()) {
                TargetContext nextContext = targetContext(client);
                if (context == null || !context.compatibleWith(nextContext, SOLVER.estimate() != null)) { clear(); context = nextContext; }
            }
            boolean wasObserving = SOLVER.observing();
            SOLVER.tick(now());
            if (wasObserving && !SOLVER.observing()) logReading();
            var estimate = SOLVER.estimate();
            if (estimate == null || ClientUi.hudHidden(client)) return;
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
            if (!active(client) || client.level != level || client.getConnection() != connection || ClientUi.hudHidden(client)) return;
            if (!SOLVER.observing() && SOLVER.estimate() == null && SOLVER.status().equals("Use a Wishing Compass to begin.")) return;
            String text = SOLVER.estimate() == null ? SOLVER.status() : label(SOLVER.estimate());
            var lines = client.font.split(net.minecraft.network.chat.Component.literal(text), Math.max(40, graphics.guiWidth() - 24));
            for (int i = 0; i < lines.size(); i++)
                graphics.text(client.font, lines.get(i), (graphics.guiWidth() - client.font.width(lines.get(i))) / 2,
                    graphics.guiHeight() / 2 + 36 + i * 10, 0xFF80FFAA, true);
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
        if (!context.prerequisites().readyFor(context.region())) {
            String message = !context.region().eligible() ? "Use your compass outside the Crystal Nucleus."
                : !context.prerequisites().completeCrystals() ? "Crystal status unavailable. Enable Crystals in your SkyBlock tab widgets, then try again."
                : "King's Scent status unavailable. Enable Active Effects in your SkyBlock tab widgets, then try again.";
            SOLVER.rejectUse(message);
            LOGGER.info("[Wishing Compass] {}", message);
            return;
        }
        Vec3 pos = client.player.position();
        long priorCapture = SOLVER.observationStarted();
        SOLVER.use(new CompassSolver.Point(pos.x, pos.y, pos.z), context.toString(), now());
        if (SOLVER.observing() && SOLVER.observationStarted() != priorCapture) {
            if (priorCapture != Long.MIN_VALUE) LOGGER.info("[Wishing Compass] Previous reading: {}; seenPackets={}, largestCount={}, nonzeroOffsets={}",
                SOLVER.diagnostic(), packetCount, largestBurst, packetOffsets);
            packetCount = 0; largestBurst = 0; packetOffsets = false;
            LOGGER.info("[Wishing Compass] Reading started in {}.", context.region());
        }
    }
    /** Called only for a vanilla use packet actually emitted by the local connection. */
    public static void sentUse(InteractionHand hand) {
        var player = Minecraft.getInstance().player;
        if (player != null) use(player.getItemInHand(hand));
    }
    /** Bounded local diagnosis; excludes raw tab text, player names and item data. */
    public static String diagnosticReport() {
        var client = Minecraft.getInstance();
        return "[Wishing Compass] enabled=" + EviemodSettings.features().skyblock.wishingCompass
            + ", active=" + active(client) + ", hollowsSession=" + (session != null)
            + ", observing=" + SOLVER.observing() + ", estimate=" + (SOLVER.estimate() != null)
            + ", context=" + (context == null ? "unavailable" : context.region()
                + "/crystalsKnown=" + context.prerequisites().completeCrystals()
                + "/scent=" + context.prerequisites().scent())
            + "; status=" + SOLVER.status() + "; current=" + SOLVER.currentDiagnostic()
            + "; last=" + SOLVER.diagnostic() + "; seenPackets=" + packetCount
            + ", largestCount=" + largestBurst + ", nonzeroOffsets=" + packetOffsets;
    }
    private static void logReading() {
        LOGGER.info("[Wishing Compass] {}; {}; seenPackets={}, largestCount={}, nonzeroOffsets={}",
            SOLVER.status(), SOLVER.diagnostic(), packetCount, largestBurst, packetOffsets);
    }
    public static void particle(ClientboundLevelParticlesPacket packet) {
        var client = Minecraft.getInstance();
        if (!active(client) || packet.getParticle().getType() != ParticleTypes.HAPPY_VILLAGER) return;
        refreshWorld(client);
        if (!usablePacket(packet)) return;
        if (SOLVER.observing()) {
            packetCount++;
            largestBurst = Math.max(largestBurst, packet.getCount());
            packetOffsets |= packet.getXDist() != 0 || packet.getYDist() != 0 || packet.getZDist() != 0;
        }
        boolean wasObserving = SOLVER.observing();
        SOLVER.particle(new CompassSolver.Point(packet.getX(), packet.getY(), packet.getZ()), now());
        if (wasObserving && !SOLVER.observing()) logReading();
    }
    public static void tabFooter(net.minecraft.network.chat.Component text) {
        var client = Minecraft.getInstance();
        if (!SkyblockContext.isHypixelServer(client)) return;
        footer = new CompassContext.FooterSnapshot(client.level, client.getConnection(), text.getString());
    }
    static boolean usablePacket(ClientboundLevelParticlesPacket packet) {
        // A packet gives an exact server-selected centre. Count/offsets randomize the
        // rendered particles, not this centre; Skyblocker's solver also consumes it.
        return packet.getParticle().getType() == ParticleTypes.HAPPY_VILLAGER && packet.getCount() >= 0;
    }
    static String itemId(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        var tag = data.copyTag();
        String direct = tag.getStringOr("id", "");
        return direct.isEmpty() ? tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id", "") : direct;
    }
    private static TargetContext targetContext(Minecraft client) {
        Vec3 pos = client.player.position();
        // Region changes and visible target prerequisites invalidate a pair, without guessing a structure name.
        var zone = CompassContext.region(new CompassSolver.Point(pos.x, pos.y, pos.z));
        boolean key = client.player.getInventory().getNonEquipmentItems().stream().anyMatch(stack -> itemId(stack).equals("JUNGLE_KEY"));
        var lines = new java.util.ArrayList<String>();
        for (var info : client.getConnection().getOnlinePlayers()) {
            var display = info.getTabListDisplayName();
            if (display != null) lines.add(display.getString());
        }
        String footerText = footer == null ? null : footer.forWorld(client.level, client.getConnection());
        return new TargetContext(zone, key, CompassContext.prerequisites(lines, footerText));
    }
    static String visiblePrerequisites(java.util.List<String> lines) {
        return CompassContext.prerequisites(lines, null).toString();
    }
}

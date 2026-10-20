package dev.eviemod.features.dungeons;

import java.util.List;
import java.util.regex.Pattern;
import dev.eviemod.features.skyblock.SkyblockContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;

/** Tab/scoreboard heuristics grounded in the supplied Catacombs tab screenshot. */
public final class DungeonTankDetector {
    private static final Pattern PLAYER = Pattern.compile(
        "(?:\\[[0-9]{1,4}\\] )?([A-Za-z0-9_]{1,16}) \\(([^()]+)\\)");
    private static final Pattern TANK = Pattern.compile("Tank (?:[IVXLCDM]+|[0-9]{1,2})");
    private static final Pattern FLOOR = Pattern.compile("(?:⏣\\s*)?The Catacombs \\((?:[FM][1-7]|E)\\)");

    public static boolean eligible(Minecraft client) {
        if (client.player == null || client.level == null || !SkyblockContext.isHypixelServer(client)) return false;
        var connection = client.getConnection();
        var scoreboard = client.level.getScoreboard();
        var sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (connection == null || sidebar == null) return false;
        var rows = connection.getOnlinePlayers().stream()
            .map(info -> info.getTabListDisplayName())
            .filter(java.util.Objects::nonNull).map(text -> text.getString()).toList();
        var lines = scoreboard.listPlayerScores(sidebar).stream().filter(entry -> !entry.isHidden()).map(entry -> {
            if (entry.display() != null) return entry.display().getString();
            var team = scoreboard.getPlayersTeam(entry.owner());
            return team == null ? entry.owner() : team.getPlayerPrefix().getString() + entry.owner() + team.getPlayerSuffix().getString();
        }).toList();
        return eligible(true, sidebar.getDisplayName().getString(), lines, rows, client.player.getGameProfile().name());
    }

    static boolean eligible(boolean hypixel, String sidebarTitle, List<String> sidebar, List<String> tab, String localName) {
        String title = CellsAlignmentTimer.plain(sidebarTitle);
        if (!hypixel || !(title.equals("SKYBLOCK") || title.equals("SKYBLOCK CO-OP"))
            || sidebar.stream().noneMatch(line -> FLOOR.matcher(CellsAlignmentTimer.plain(line)).matches())
            || tab.stream().noneMatch(line -> CellsAlignmentTimer.plain(line).equals("Dungeon: Catacombs"))) return false;
        boolean found = false;
        for (String row : tab) {
            var player = PLAYER.matcher(CellsAlignmentTimer.plain(row));
            if (!player.matches() || !player.group(1).equalsIgnoreCase(localName)) continue;
            // A conflicting/unknown local class is not permission to trigger.
            if (!TANK.matcher(player.group(2)).matches()) return false;
            found = true;
        }
        return found;
    }

    private DungeonTankDetector() {}
}

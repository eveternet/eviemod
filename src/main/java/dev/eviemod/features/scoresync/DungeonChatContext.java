package dev.eviemod.features.scoresync;

import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;

/** Scoreboard heuristic isolated from relay logic; unknown data never permits chat cancellation. */
final class DungeonChatContext {
    private static final Pattern FLOOR = Pattern.compile("^(?:⏣ )?The Catacombs \\((?:[FM]([1-7])|E)\\)$");

    static int floor(Minecraft client) {
        if (client.level == null) return -1;
        var scoreboard = client.level.getScoreboard();
        var sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) return -1;
        for (var entry : scoreboard.listPlayerScores(sidebar)) {
            String line;
            if (entry.display() != null) line = entry.display().getString();
            else {
                var team = scoreboard.getPlayersTeam(entry.owner());
                line = team == null ? entry.owner()
                    : team.getPlayerPrefix().getString() + entry.owner() + team.getPlayerSuffix().getString();
            }
            int floor = parseFloor(line);
            if (floor >= 0) return floor;
        }
        return -1;
    }

    static int parseFloor(String line) {
        var match = FLOOR.matcher(ScoreRelay.stripFormatting(line).strip());
        return match.matches() ? (match.group(1) == null ? 0 : Integer.parseInt(match.group(1))) : -1;
    }
    private DungeonChatContext() {}
}

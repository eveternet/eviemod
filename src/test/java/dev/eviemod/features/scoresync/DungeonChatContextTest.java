package dev.eviemod.features.scoresync;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DungeonChatContextTest {
    @Test void knownScoreboardLocationsAndUnknownData() {
        assertEquals(6, DungeonChatContext.parseFloor(" §7⏣ §cThe Catacombs (F6) "));
        assertEquals(7, DungeonChatContext.parseFloor("The Catacombs (M7)"));
        assertEquals(0, DungeonChatContext.parseFloor("The Catacombs (E)"));
        assertEquals(-1, DungeonChatContext.parseFloor("Dungeon Hub"));
        assertEquals(-1, DungeonChatContext.parseFloor("The Catacombs (F8)"));
        assertEquals(-1, DungeonChatContext.parseFloor("Player: The Catacombs (F7)"));
    }
}

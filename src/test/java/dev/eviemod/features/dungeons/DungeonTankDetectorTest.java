package dev.eviemod.features.dungeons;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DungeonTankDetectorTest {
    private static boolean detect(String... rows) {
        return DungeonTankDetector.eligible(true, "§eSKYBLOCK", List.of("§7⏣ §cThe Catacombs §7(M7)"), List.of(rows), "eveternet");
    }

    @Test void screenshotBerserkRowDoesNotEnableTankFeature() {
        assertFalse(detect("Party (1)", "[500] eveternet (Berserk XLI)", "Dungeon: Catacombs"));
    }

    @Test void findsLocalPlayerByNameNotHeaderOrOrdering() {
        assertTrue(detect("Party (2)", "[500] Someone (Mage L)", "Dungeon: Catacombs", "§8[§c500§8] §beveternet §d(Tank XLI)"));
        assertTrue(detect("Dungeon: Catacombs", "EVETERNET (Tank 41)"));
        assertFalse(detect("Dungeon: Catacombs", "[500] Other (Tank L)", "[500] eveternet (Archer L)"));
        assertFalse(detect("Dungeon: Catacombs", "[500] not_eveternet (Tank L)"));
    }

    @Test void missingUnknownConflictingOrMalformedClassFailsClosed() {
        for (String row : new String[]{"", "eveternet", "[500] eveternet (EMPTY)", "[500] eveternet (DEAD)",
            "[500] eveternet (Tank)", "[500] eveternet (Tank ???)", "Guild > eveternet (Tank L)"}) {
            assertFalse(detect("Dungeon: Catacombs", row), row);
        }
        assertFalse(detect("Dungeon: Catacombs", "eveternet (Tank L)", "eveternet (Mage L)"));
        assertFalse(detect("Dungeon: Catacombs", "eveternet (Tank L)", "eveternet (EMPTY)"));
    }

    @Test void requiresHypixelSkyblockRealDungeonFloorAndDungeonTabEvidence() {
        var tab = List.of("Dungeon: Catacombs", "[500] eveternet (Tank XLI)");
        assertFalse(DungeonTankDetector.eligible(false, "SKYBLOCK", List.of("The Catacombs (M7)"), tab, "eveternet"));
        assertFalse(DungeonTankDetector.eligible(true, "BED WARS", List.of("The Catacombs (M7)"), tab, "eveternet"));
        for (String location : new String[]{"Dungeon Hub", "The Catacombs", "The Catacombs (F0)", "The Catacombs (M8)", ""}) {
            assertFalse(DungeonTankDetector.eligible(true, "SKYBLOCK", List.of(location), tab, "eveternet"));
        }
        assertFalse(detect("Area: Dungeon Hub", "eveternet (Tank L)"));
        for (String floor : new String[]{"F1", "F7", "M1", "M7", "E"}) {
            assertTrue(DungeonTankDetector.eligible(true, "SKYBLOCK", List.of("The Catacombs (" + floor + ")"), tab, "eveternet"));
        }
    }
}

package dev.eviemod.features.compass;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.eviemod.features.compass.CompassContext.*;

class CompassContextTest {
    static List<String> rows(String amethyst) {
        return List.of("§dCrystals:", "§5Amethyst: " + amethyst, "Jade: ✖ Not Found", "Amber: ✖ Not Found",
            "Sapphire: ✖ Not Found", "Topaz: ✖ Not Found");
    }
    @Test void realSectionRowsDetectAcquisitionAndPlacementAndRejectBothPairAndWaypoint() {
        var before = prerequisites(rows("✖ Not Found"), "");
        var acquired = prerequisites(rows("✔ Found"), "");
        var placed = prerequisites(rows("✔ Placed"), "");
        assertTrue(before.completeCrystals()); assertTrue(acquired.completeCrystals()); assertTrue(placed.completeCrystals());
        assertEquals(Status.NOT_FOUND, before.crystals().get(Crystal.AMETHYST.ordinal()));
        assertEquals(Status.FOUND, acquired.crystals().get(Crystal.AMETHYST.ordinal()));
        assertEquals(Status.PLACED, placed.crystals().get(Crystal.AMETHYST.ordinal()));
        var first = new WishingCompassClient.TargetContext(Region.JUNGLE, true, before);
        for (var nextState : List.of(acquired, placed)) for (boolean navigating : new boolean[]{false, true})
            assertFalse(first.compatibleWith(new WishingCompassClient.TargetContext(Region.JUNGLE, true, nextState), navigating));
        assertFalse(new WishingCompassClient.TargetContext(Region.JUNGLE, true, acquired)
            .compatibleWith(new WishingCompassClient.TargetContext(Region.JUNGLE, true, placed), true));
    }
    @Test void unorderedFormattedRowsAndUnrelatedEntriesDoNotAlterVerifiedState() {
        var unordered = new ArrayList<>(rows("✔ Found"));
        java.util.Collections.reverse(unordered); unordered.add("Other player");
        assertEquals(prerequisites(rows("✔ Found"), ""), prerequisites(unordered, ""));
    }
    @Test void missingPartialConflictingAndUnknownCrystalDataCannotMasqueradeAsStable() {
        var missing = prerequisites(List.of("Other player"), "");
        var partial = prerequisites(List.of("Crystals:", "Amethyst: ✔ Found"), "");
        var unrecognized = prerequisites(rows("Claimed"), "");
        var conflictingRows = new ArrayList<>(rows("✔ Found")); conflictingRows.add("Amethyst: ✖ Not Found");
        var conflicting = prerequisites(conflictingRows, "");
        assertFalse(missing.crystalSection()); assertTrue(partial.crystalSection());
        for (var state : List.of(missing, partial, unrecognized, conflicting)) {
            assertFalse(state.completeCrystals()); assertFalse(state.readyFor(Region.JUNGLE));
            var unknown = new WishingCompassClient.TargetContext(Region.JUNGLE, false, state);
            assertFalse(unknown.compatibleWith(unknown, false)); assertFalse(unknown.compatibleWith(unknown, true));
        }
    }
    @Test void footerOnlyScentAppearanceAndRemovalInvalidatePairsAndWaypointsButCountdownDoesNot() {
        var absent = prerequisites(rows("✖ Not Found"), "Active Effects:\nSpeed I 03:00");
        var present = prerequisites(rows("✖ Not Found"), "§aActive Effects:\nKing's Scent I 13m 02s");
        var countdown = prerequisites(rows("✖ Not Found"), "Active Effects:\nKing's Scent I 13m 01s");
        var removed = prerequisites(rows("✖ Not Found"), "");
        assertEquals(Scent.ABSENT, absent.scent()); assertEquals(Scent.PRESENT, present.scent());
        assertEquals(present, countdown); assertEquals(Scent.ABSENT, removed.scent());
        var first = new WishingCompassClient.TargetContext(Region.GOBLIN, false, absent);
        var active = new WishingCompassClient.TargetContext(Region.GOBLIN, false, present);
        for (boolean navigating : new boolean[]{false, true}) {
            assertFalse(first.compatibleWith(active, navigating));
            assertFalse(active.compatibleWith(new WishingCompassClient.TargetContext(Region.GOBLIN, false, removed), navigating));
            assertTrue(active.compatibleWith(new WishingCompassClient.TargetContext(Region.GOBLIN, false, countdown), navigating));
        }
    }
    @Test void effectsCanAlsoComeFromListEntriesAndUnknownGoblinStateCannotBePaired() {
        var unknown = prerequisites(rows("✖ Not Found"), null);
        assertEquals(Scent.UNKNOWN, unknown.scent()); assertFalse(unknown.readyFor(Region.GOBLIN));
        assertTrue(unknown.readyFor(Region.JUNGLE));
        var lines = new ArrayList<>(rows("✖ Not Found")); lines.add("Active Effects:");
        assertEquals(Scent.ABSENT, prerequisites(lines, null).scent());
        lines.add("King's Scent I 02:00"); assertEquals(Scent.PRESENT, prerequisites(lines, null).scent());
    }
    @Test void footerSnapshotCannotLeakAcrossWorldOrConnectionChanges() {
        Object level = new Object(), connection = new Object();
        var footer = new FooterSnapshot(level, connection, "King's Scent I 02:00");
        assertEquals("King's Scent I 02:00", footer.forWorld(level, connection));
        assertNull(footer.forWorld(new Object(), connection)); assertNull(footer.forWorld(level, new Object()));
        var cleared = new FooterSnapshot(level, connection, ""); assertEquals("", cleared.forWorld(level, connection));
    }
    @Test void nucleusBoundsAreExplicitAndGuardEveryFaceAndCorner() {
        assertEquals(Region.PRECURSOR, region(new CompassSolver.Point(570, 100, 570)));
        assertEquals(Region.NUCLEUS, region(new CompassSolver.Point(550, 100, 550)));
        for (double x : new double[]{462, 564}) for (double y : new double[]{63, 181}) for (double z : new double[]{461, 565})
            assertEquals(Region.NUCLEUS, region(new CompassSolver.Point(x, y, z)));
        assertEquals(Region.JUNGLE, region(new CompassSolver.Point(461.99, 100, 500)));
        assertEquals(Region.PRECURSOR, region(new CompassSolver.Point(564.01, 100, 550)));
        assertEquals(Region.MAGMA, region(new CompassSolver.Point(500, 62.99, 500)));
        assertEquals(Region.JUNGLE, region(new CompassSolver.Point(500, 181.01, 500)));
        assertEquals(Region.JUNGLE, region(new CompassSolver.Point(500, 100, 460.99)));
        assertEquals(Region.PRECURSOR, region(new CompassSolver.Point(550, 100, 565.01)));
        assertEquals(Region.UNKNOWN, region(new CompassSolver.Point(200, 100, 500)));
    }
    @Test void enteringNucleusInvalidatesUnsolvedPairWhileSolvedWaypointCanBeNavigatedThroughIt() {
        var state = prerequisites(rows("✖ Not Found"), "");
        var outside = new WishingCompassClient.TargetContext(Region.PRECURSOR, false, state);
        var inside = new WishingCompassClient.TargetContext(Region.NUCLEUS, false, state);
        assertFalse(state.readyFor(inside.region()));
        assertFalse(outside.compatibleWith(inside, false)); assertTrue(outside.compatibleWith(inside, true));
    }
}

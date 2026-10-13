package dev.eviemod.features.compass;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.eviemod.features.compass.CompassSolver.Point;

class CompassSolverTest {
    private static final Point TARGET = new Point(700, 100, 650);
    private static final Point A = new Point(580, 110, 570), B = new Point(610, 110, 560);
    private static List<Point> trail(Point origin, Point target) {
        Point direction = target.subtract(origin).scale(1 / target.distance(origin));
        var result = new ArrayList<Point>();
        for (int i = 0; i < 24; i++) result.add(origin.add(new Point(0, 1, 0)).add(direction.scale(i * 0.5)));
        return result;
    }
    private static CompassSolver solver() {
        var solver = new CompassSolver(); solver.session("mini1:crystal_hollows"); return solver;
    }
    private static void observe(CompassSolver solver, Point used, Point target, long time) {
        solver.use(used, "east-south", time);
        var particles = trail(used, target);
        for (int i = 0; i < particles.size(); i++) solver.particle(particles.get(i), time + 50 + i * 40);
        for (int i = 0; i < 3; i++) solver.particle(particles.get(i), time + 2000 + i * 40);
        solver.tick(time + 5000);
    }
    private static void feed(CompassSolver solver, List<Point> points, long start) {
        for (int i = 0; i < points.size(); i++) solver.particle(points.get(i), start + i * 40);
        for (int i = 0; i < 3; i++) solver.particle(points.get(i), start + 1000 + i * 40);
    }
    private static void feedQualified(CompassSolver solver, Point used, long start, boolean repeat) {
        var points = new ArrayList<>(trail(used, TARGET));
        if (repeat) feed(solver, points, start);
        else {
            points.add(points.getLast().add(points.get(1).subtract(points.getFirst())));
            for (int i = 0; i < points.size(); i++) solver.particle(points.get(i), start + i * 40);
        }
    }
    @Test void completedRepeatFinishesAfterQuietInsteadOfRequiringFiveSecondsPerUse() {
        var solver = solver(); observe(solver, A, TARGET, 0);
        solver.use(B, "east-south", 6000); feed(solver, trail(B, TARGET), 6050);
        assertTrue(solver.observing()); assertNull(solver.estimate());
        solver.tick(7629); assertTrue(solver.observing()); assertNull(solver.estimate());
        solver.tick(7630); assertFalse(solver.observing()); assertNotNull(solver.estimate());
    }
    @Test void competingCompleteTrailsRejectTheReviewProbeInEitherArrivalOrder() {
        Point ownTarget = new Point(720, 100, 720);
        for (boolean foreignFirst : new boolean[]{true, false}) {
            var solver = solver();
            for (int use = 0; use < 2; use++) {
                Point used = use == 0 ? A : B;
                long time = use * 6000L;
                solver.use(used, "east-south", time);
                var foreign = trail(used.add(new Point(5, 0, 0)), TARGET);
                var own = trail(used, ownTarget);
                feed(solver, foreignFirst ? foreign : own, time + 50);
                feed(solver, foreignFirst ? own : foreign, time + 1500);
                assertNull(solver.estimate()); assertTrue(solver.observing());
                solver.tick(time + 5000);
                assertNull(solver.estimate()); assertTrue(solver.status().contains("Multiple"));
            }
        }
    }
    @Test void competingInterleavedTrailsAreNotHiddenByTheFirstTrack() {
        var solver = solver(); solver.use(A, "east-south", 0);
        var own = trail(A, new Point(720, 100, 720));
        var foreign = trail(A.add(new Point(5, 0, 0)), TARGET);
        for (int i = 0; i < own.size(); i++) {
            solver.particle(foreign.get(i), 50 + i * 80); solver.particle(own.get(i), 90 + i * 80);
        }
        for (int i = 0; i < 3; i++) {
            solver.particle(foreign.get(i), 2100 + i * 80); solver.particle(own.get(i), 2140 + i * 80);
        }
        solver.tick(5000); assertNull(solver.estimate()); assertTrue(solver.status().contains("Multiple"));
    }
    @Test void aLaterTrailWithTheSameStartAndASmallDistinctAngleIsStillAmbiguous() {
        var solver = solver();
        for (int use = 0; use < 2; use++) {
            Point used = use == 0 ? A : B; long time = use * 6000L;
            solver.use(used, "east-south", time);
            feed(solver, trail(used, TARGET), time + 50);
            feed(solver, trail(used, TARGET.add(new Point(0, 0, 4))), time + 1500);
            solver.tick(time + 5000);
            assertNull(solver.estimate()); assertTrue(solver.status().contains("Multiple"));
        }
    }
    @Test void competingFirstPassArrivingBeforeQuietWithMissingRepeatInvalidatesTheObservation() {
        var solver = solver(); observe(solver, A, TARGET, 0);
        solver.use(B, "east-south", 6000);
        feed(solver, trail(B.add(new Point(5, 0, 0)), TARGET), 6050);
        var own = trail(B, new Point(720, 100, 720));
        for (int i = 0; i < own.size(); i++) solver.particle(own.get(i), 7490 + i * 40);
        solver.tick(11000); assertNull(solver.estimate()); assertTrue(solver.status().contains("Multiple"));
        observe(solver, A, TARGET, 12000); assertNull(solver.estimate());
    }
    @Test void repeatedPassesOfTheSameTrailRemainOneCandidate() {
        var solver = solver();
        for (int use = 0; use < 2; use++) {
            Point used = use == 0 ? A : B; long time = use * 6000L;
            solver.use(used, "east-south", time);
            var points = trail(used, TARGET); feed(solver, points, time + 50);
            for (int i = 0; i < points.size(); i++) solver.particle(points.get(i), time + 2500 + i * 40);
            solver.tick(time + 5000);
        }
        assertNotNull(solver.estimate());
    }
    @Test void noisyUniqueTrailStillTriangulatesAndPacketsOutsideWindowCannotRetractIt() {
        var solver = solver();
        for (int use = 0; use < 2; use++) {
            Point used = use == 0 ? A : B; long time = use * 6000L;
            solver.use(used, "east-south", time);
            var points = trail(used, TARGET);
            for (int i = 1; i < points.size() - 1; i++) points.set(i, points.get(i).add(new Point(0, i % 2 == 0 ? 0.02 : -0.02, 0)));
            feed(solver, points, time + 50); solver.tick(time + 5000);
        }
        var estimate = solver.estimate(); assertNotNull(estimate);
        feed(solver, trail(B, new Point(720, 100, 720)), 12000);
        assertSame(estimate, solver.estimate());
    }
    @Test void exceedingCaptureBoundsRejectsRatherThanDiscardingCompetingEvidence() {
        var solver = solver(); solver.use(A, "east-south", 0);
        feed(solver, trail(A, TARGET), 50);
        for (int i = 0; i < 600; i++) solver.particle(A, 1250 + i);
        solver.tick(5000); assertNull(solver.estimate()); assertTrue(solver.status().contains("Multiple"));
    }
    @Test void rejectingAnUnsupportedUseDiscardsPriorObservationsAndSolvedWaypoints() {
        var solver = solver(); observe(solver, A, TARGET, 0);
        solver.rejectUse("Use your compass outside the Crystal Nucleus.");
        observe(solver, B, TARGET, 6000); assertNull(solver.estimate());
        observe(solver, A, TARGET, 12000); assertNotNull(solver.estimate());
        solver.rejectUse("Crystal status unavailable."); assertNull(solver.estimate()); assertFalse(solver.observing());
        assertEquals("Crystal status unavailable.", solver.status());
    }
    @Test void triangulatesInThreeDimensionsWithNonzeroUncertainty() {
        var solver = solver();
        observe(solver, A, TARGET, 0);
        assertNull(solver.estimate()); assertFalse(solver.observing());
        observe(solver, B, TARGET, 6000);
        assertNotNull(solver.estimate());
        // These trails begin one block above the use position, as emitted particles may do.
        assertTrue(solver.estimate().position().distance(TARGET.add(new Point(0, 1, 0))) < 0.001);
        assertTrue(solver.estimate().uncertainty() >= 2);
    }
    @Test void ignoresParticlesWithoutAnOwnUseOrTrustedSession() {
        var solver = solver();
        for (Point point : trail(A, TARGET)) solver.particle(point, 10);
        assertFalse(solver.observing()); assertNull(solver.estimate());
        solver.session(null); observe(solver, A, TARGET, 6000);
        assertFalse(solver.observing()); assertNull(solver.estimate());
    }
    @Test void requiresRepeatConfirmationRatherThanAPartialStraightLine() {
        var solver = solver(); solver.use(A, "zone", 0);
        var particles = trail(A, TARGET);
        for (int i = 0; i < particles.size(); i++) solver.particle(particles.get(i), i * 40);
        solver.particle(particles.getFirst(), 2000);
        assertTrue(solver.observing());
        solver.tick(5000); assertFalse(solver.observing()); assertNull(solver.estimate());
        assertTrue(solver.status().contains("incomplete"));
    }
    @Test void timesOutWithoutParticlesAndIgnoresLatePackets() {
        var solver = solver(); solver.use(A, "zone", 0); solver.tick(5000);
        for (Point p : trail(A, TARGET)) solver.particle(p, 5100);
        assertNull(solver.estimate()); assertFalse(solver.observing());
    }
    @Test void firstParticleMustBeNearOwnUse() {
        var solver = solver(); solver.use(A, "zone", 0);
        for (Point p : trail(new Point(750, 100, 700), TARGET)) solver.particle(p, 100);
        solver.tick(5000); assertTrue(solver.status().contains("incomplete"));
    }
    @Test void duplicatedUseCallbacksDoNotDestroyCapture() {
        var solver = solver(); solver.use(A, "zone", 0); solver.use(A, "zone", 20);
        assertTrue(solver.observing());
    }
    @Test void repeatedAttemptsDoNotEraseThePendingCaptureOrExtendItsTimeout() {
        var solver = solver(); solver.use(A, "zone", 0); solver.use(B, "zone", 1000);
        assertTrue(solver.observing()); assertNull(solver.estimate());
        solver.use(A, "zone", 4900); solver.tick(5000);
        assertFalse(solver.observing()); assertTrue(solver.status().contains("incomplete"));
    }
    @Test void holdingUseThroughACompleteTrailStillRecordsIt() {
        var solver = solver(); solver.use(A, "east-south", 0);
        var particles = trail(A, TARGET);
        for (int i = 0; i < particles.size(); i++) {
            solver.particle(particles.get(i), 50 + i * 40);
            if (i % 5 == 0) solver.use(A, "east-south", 50 + i * 40);
        }
        for (int i = 0; i < 3; i++) solver.particle(particles.get(i), 1000 + i * 40);
        solver.tick(1580);
        assertFalse(solver.observing()); assertTrue(solver.status().contains("recorded"));
        observe(solver, B, TARGET, 2000); assertNotNull(solver.estimate());
    }
    @Test void aHeldUseAfterQualificationCannotArmAnEmptyCaptureThatSwallowsTheMovedUse() {
        for (boolean repeat : new boolean[]{true, false}) for (long extraUse : new long[]{1200, 1700}) {
            var solver = solver(); solver.use(A, "east-south", 0); feedQualified(solver, A, 50, repeat);
            solver.use(A, "east-south", extraUse);
            assertFalse(solver.observing(), "Same-position repetition must preserve the qualified first ray.");
            solver.use(B, "east-south", 2000); feedQualified(solver, B, 2050, repeat);
            solver.tick(3630);
            assertNotNull(solver.estimate(), "The useful moved use must solve without an empty five-second reading.");
        }
    }
    @Test void heldUsesAfterASolutionPreserveTheWaypointWithoutExtendingItsLifetime() {
        var solver = solver(); observe(solver, A, TARGET, 0); observe(solver, B, TARGET, 6000);
        var estimate = solver.estimate(); assertNotNull(estimate);
        solver.use(B, "east-south", 11200); solver.use(B, "east-south", 11400);
        assertSame(estimate, solver.estimate()); assertFalse(solver.observing());
        solver.use(B, "east-south", 310800); assertSame(estimate, solver.estimate());
        solver.tick(311000); assertNull(solver.estimate());
    }
    @Test void changedTargetContextStillRestartsASolveAtTheSamePosition() {
        var solver = solver(); observe(solver, A, TARGET, 0); observe(solver, B, TARGET, 6000);
        assertNotNull(solver.estimate());
        solver.use(B, "changed-key-or-crystal", 11200);
        assertNull(solver.estimate()); assertTrue(solver.observing());
    }
    @Test void aTwentyFiveParticlePassQualifiesAfterTheReferenceIdleIntervalWithoutARepeat() {
        var solver = solver(); solver.use(A, "zone", 0);
        var particles = new ArrayList<>(trail(A, TARGET));
        Point step = particles.get(1).subtract(particles.getFirst());
        particles.add(particles.getLast().add(step));
        for (int i = 0; i < particles.size(); i++) solver.particle(particles.get(i), 50 + i * 40);
        solver.tick(1509); assertTrue(solver.observing());
        solver.tick(1510); assertFalse(solver.observing()); assertTrue(solver.status().contains("recorded"));
        assertTrue(solver.diagnostic().contains("longestPass=25"));
    }
    @Test void supportsTheCoarseSpacingAcceptedByTheCurrentSkyblockerReference() {
        var solver = solver(); solver.use(A, "zone", 0);
        Point direction = TARGET.subtract(A).scale(1 / TARGET.distance(A));
        for (int i = 0; i < 25; i++) solver.particle(A.add(new Point(0, 1.62, 0)).add(direction.scale(i * 0.85)), 50 + i * 40);
        solver.tick(1510); assertFalse(solver.observing()); assertTrue(solver.status().contains("recorded"));
    }
    @Test void nextRealUseCanCloseARepeatConfirmedReadingAtTheLiveTwoSecondCadence() {
        var solver = solver(); solver.use(A, "east-south", 0);
        feed(solver, trail(A, TARGET), 50);
        solver.use(B, "east-south", 2000);
        feed(solver, trail(B, TARGET), 2050);
        solver.tick(3630); assertNotNull(solver.estimate());
    }
    @Test void eightBlockBaselineCanSolveADistantHollowsTargetInsteadOfAnArbitraryFiveDegreeRejection() {
        var solver = solver(); Point target = new Point(562, 120, 808);
        Point start = new Point(600, 130, 600), second = start.add(new Point(8, 0, 0));
        solver.use(start, "east-south", 0); feed(solver, trail(start, target), 50); solver.tick(1630);
        solver.use(second, "east-south", 2000); feed(solver, trail(second, target), 2050); solver.tick(3630);
        assertNotNull(solver.estimate());
        assertTrue(solver.estimate().position().distance(target.add(new Point(0, 1, 0))) < 0.01);
        assertTrue(solver.estimate().uncertainty() >= 2);
    }
    @Test void tooCloseUseKeepsFirstObservationForLaterRetry() {
        var solver = solver(); observe(solver, A, TARGET, 0);
        observe(solver, A.add(new Point(2, 0, 0)), TARGET, 6000);
        assertNull(solver.estimate()); assertTrue(solver.status().contains("eight"));
        observe(solver, B, TARGET, 12000); assertNotNull(solver.estimate());
    }
    @Test void lobbySwitchDiscardBothSolvedAndPendingObservations() {
        var solver = solver(); observe(solver, A, TARGET, 0); observe(solver, B, TARGET, 6000);
        assertNotNull(solver.estimate()); solver.session("mini2:crystal_hollows"); assertNull(solver.estimate());
        observe(solver, B, TARGET, 12000); assertNull(solver.estimate());
        solver.session(null); assertFalse(solver.observing());
    }
    @Test void repeatedLocationPacketDoesNotEraseAnObservation() {
        var solver = solver(); observe(solver, A, TARGET, 0);
        solver.session("mini1:crystal_hollows"); observe(solver, B, TARGET, 6000);
        assertNotNull(solver.estimate());
    }
    @Test void changedTargetPrerequisitesDiscardThePriorRay() {
        var solver = solver(); observe(solver, A, TARGET, 0);
        solver.use(B, "different-zone-or-key", 6000);
        var particles = trail(B, TARGET);
        for (Point p : particles) solver.particle(p, 6100);
        for (int i = 0; i < 3; i++) solver.particle(particles.get(i), 8000);
        solver.tick(11000);
        assertNull(solver.estimate()); assertTrue(solver.status().contains("recorded"));
    }
    @Test void manualResetAndExpiryClearAllResults() {
        var solver = solver(); observe(solver, A, TARGET, 0); solver.reset();
        observe(solver, B, TARGET, 6000); assertNull(solver.estimate());
        solver.tick(320000); assertTrue(solver.status().contains("begin"));
        observe(solver, A, TARGET, 320000); observe(solver, B, TARGET, 326000);
        assertNotNull(solver.estimate()); solver.tick(640000); assertNull(solver.estimate());
    }
    @Test void newUseAfterSolutionClearsTheOldWaypointImmediately() {
        var solver = solver(); observe(solver, A, TARGET, 0); observe(solver, B, TARGET, 6000);
        solver.use(A, "east-south", 12000); assertNull(solver.estimate()); assertTrue(solver.observing());
    }
    @Test void retriesCannotExtendAnOldObservationBeyondItsLifetime() {
        var solver = solver(); observe(solver, A, TARGET, 0);
        solver.use(A, "east-south", 290000);
        solver.tick(302100);
        observe(solver, B, TARGET, 310000);
        assertNull(solver.estimate()); assertTrue(solver.status().contains("recorded"));
    }
    @Test void parallelAndNearlyParallelRaysAreNotSolved() {
        var a = new CompassSolver.Ray(A, new Point(1, 0, 0), 0.005);
        assertNull(CompassSolver.intersect(a, new CompassSolver.Ray(B, new Point(1, 0, 0), 0.005)));
        assertNull(CompassSolver.intersect(a, new CompassSolver.Ray(B, new Point(0.99995, 0, 0.01), 0.005)));
    }
    @Test void rejectsIntersectionBehindTheTrail() {
        var a = new CompassSolver.Ray(A, new Point(1, 0, 0), 0.005);
        var b = new CompassSolver.Ray(new Point(550, 110, 560), new Point(0, 0, 1), 0.005);
        assertNull(CompassSolver.intersect(a, b));
    }
    @Test void rejectsSkewTrailsAndOutOfBoundsTargets() {
        var a = new CompassSolver.Ray(A, new Point(1, 0, 0), 0.005);
        assertNull(CompassSolver.intersect(a, new CompassSolver.Ray(new Point(600, 120, 560), new Point(0, 0, 1), 0.005)));
        assertNull(CompassSolver.intersect(a, new CompassSolver.Ray(new Point(900, 110, 560), new Point(0, 0, 1), 0.005)));
    }
    @Test void rejectsWideUncertaintyEvenWhenTheRaysIntersect() {
        var a = new CompassSolver.Ray(new Point(300, 100, 300), new Point(1, 0, 0), 0.1);
        var b = new CompassSolver.Ray(new Point(800, 100, 250), new Point(0, 0, 1), 0.1);
        assertNull(CompassSolver.intersect(a, b));
    }
    @Test void noisyStraightSamplesFitButCurvedAndBacktrackingSamplesFail() {
        var noisy = new ArrayList<>(trail(A, TARGET));
        for (int i = 1; i < noisy.size() - 1; i++) noisy.set(i, noisy.get(i).add(new Point(0, (i % 2 == 0 ? 1 : -1) * 0.03, 0)));
        assertNotNull(CompassSolver.fit(noisy));
        noisy.set(8, noisy.get(8).add(new Point(0, 2, 0))); assertNull(CompassSolver.fit(noisy));
        var backwards = new ArrayList<>(trail(A, TARGET));
        java.util.Collections.swap(backwards, 8, 14); assertNull(CompassSolver.fit(backwards));
        assertNull(CompassSolver.fit(trail(A, TARGET).subList(0, 5)));
    }
    @Test void nonfinitePacketsAndUsePositionsAreIgnored() {
        var solver = solver(); solver.use(new Point(Double.NaN, 100, 500), "zone", 0);
        assertFalse(solver.observing());
        solver.use(A, "zone", 1000); solver.particle(new Point(0, Double.POSITIVE_INFINITY, 0), 1100);
        solver.tick(6000); assertNull(solver.estimate());
    }
}

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
    @Test void overlappingUsesInvalidateCaptureWithoutInventingAnEstimate() {
        var solver = solver(); solver.use(A, "zone", 0); solver.use(B, "zone", 1000);
        assertFalse(solver.observing()); assertNull(solver.estimate());
        assertTrue(solver.status().contains("overlap"));
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
        assertNull(solver.estimate()); assertTrue(solver.status().contains("recorded"));
    }
    @Test void manualResetAndExpiryClearAllResults() {
        var solver = solver(); observe(solver, A, TARGET, 0); solver.reset();
        observe(solver, B, TARGET, 6000); assertNull(solver.estimate());
        solver.tick(310000); assertTrue(solver.status().contains("begin"));
        observe(solver, A, TARGET, 320000); observe(solver, B, TARGET, 326000);
        assertNotNull(solver.estimate()); solver.tick(630000); assertNull(solver.estimate());
    }
    @Test void newUseAfterSolutionClearsTheOldWaypointImmediately() {
        var solver = solver(); observe(solver, A, TARGET, 0); observe(solver, B, TARGET, 6000);
        solver.use(A, "east-south", 12000); assertNull(solver.estimate()); assertTrue(solver.observing());
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

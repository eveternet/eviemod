package dev.eviemod.features.compass;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded, transient observations. No game, persistence or network dependencies. */
public final class CompassSolver {
    static final long CAPTURE_MILLIS = 5_000;
    static final long QUIET_MILLIS = 500;
    static final long EXPIRY_MILLIS = 300_000;
    public record Point(double x, double y, double z) {
        Point add(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        Point subtract(Point p) { return new Point(x - p.x, y - p.y, z - p.z); }
        Point scale(double s) { return new Point(x * s, y * s, z * s); }
        double dot(Point p) { return x * p.x + y * p.y + z * p.z; }
        double length() { return Math.sqrt(dot(this)); }
        double distance(Point p) { return subtract(p).length(); }
        boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }
    record Ray(Point origin, Point direction, double angularError) {}
    public record Estimate(Point position, double uncertainty) {}
    private record Observation(Point usedAt, Ray ray, long time) {}
    private String session, targetContext;
    private Capture capture;
    private Observation first;
    private Estimate estimate;
    private Point solvedUseAt;
    private long lastUse = Long.MIN_VALUE, updatedAt;
    private String status = "Use a Wishing Compass to begin.";
    private String diagnostic = "No compass observation.";

    public String status() { return status; }
    public Estimate estimate() { return estimate; }
    public boolean observing() { return capture != null; }
    long observationStarted() { return capture == null ? Long.MIN_VALUE : capture.started; }
    public boolean hasObservations() { return capture != null || first != null || estimate != null; }
    public String diagnostic() { return diagnostic; }
    public String currentDiagnostic() { return capture == null ? "No pending capture." : capture.diagnostic(); }
    public void reset() {
        capture = null; first = null; estimate = null; solvedUseAt = null; targetContext = null;
        lastUse = Long.MIN_VALUE;
        status = "Use a Wishing Compass to begin.";
        diagnostic = "No compass observation.";
    }
    public void session(String next) {
        if (!Objects.equals(session, next)) { reset(); session = next; }
    }
    public void rejectUse(String message) { reset(); status = message; }
    public void tick(long now) {
        if (first != null && now - first.time >= EXPIRY_MILLIS || estimate != null && now - updatedAt >= EXPIRY_MILLIS) {
            reset(); return;
        }
        if (capture != null && (now - capture.started >= CAPTURE_MILLIS
            || now - capture.lastParticle >= QUIET_MILLIS && capture.result().ray != null)) {
            Capture completed = capture;
            capture = null;
            finish(completed, now);
        }
    }
    public void use(Point position, String context, long now) {
        tick(now);
        if (session == null || position == null || !position.finite() || context == null) return;
        if (!Objects.equals(context, targetContext)) { reset(); targetContext = context; }
        // A block/item send pair can describe the same vanilla interaction. Never cancel it.
        if (lastUse != Long.MIN_VALUE && now - lastUse < 150) return;
        if (capture != null) {
            CaptureResult ready = capture.result();
            if (ready.ray != null || ready.ambiguous) {
                Capture completed = capture; capture = null; finish(completed, now);
            } else {
                // A held button or an unsuccessful retry must not erase or prolong the reading.
                // If the server sends another distinct trail, candidate checks reject the pair.
                status = "Still reading your compass trail...";
                return;
            }
        }
        if (estimate != null) {
            // Vanilla repeats a held use every four ticks. Such sends do not request a
            // new solve merely because the useful second reading has just finished.
            if (solvedUseAt != null && solvedUseAt.distance(position) < 8) return;
            reset(); targetContext = context;
        }
        if (first != null && first.usedAt.distance(position) < 8) {
            status = "Trail recorded. Move at least eight blocks sideways and use your compass again.";
            return;
        }
        lastUse = now; updatedAt = now; estimate = null;
        capture = new Capture(position, now);
        status = "Reading your compass trail...";
    }
    public void particle(Point position, long now) {
        tick(now);
        if (capture == null || position == null || !position.finite()) return;
        capture.accept(position, now);
    }
    private void finish(Capture completed, long now) {
        CaptureResult result = completed.result();
        diagnostic = completed.diagnostic();
        Ray ray = result.ray;
        estimate = null;
        if (result.ambiguous) {
            first = null;
            status = "Multiple possible trails detected. Wait for them to finish, then use your compass again.";
            return;
        }
        if (ray == null) {
            status = "Trail incomplete. Wait for it to finish, then use your compass again.";
            return;
        }
        updatedAt = now;
        Point usedAt = completed.usedAt;
        Observation next = new Observation(usedAt, ray, now);
        if (first == null) {
            first = next;
            status = "Trail recorded. Move at least eight blocks sideways and use your compass again.";
            return;
        }
        if (first.usedAt.distance(usedAt) < 8) {
            status = "Move at least eight blocks sideways, then use your compass again.";
            return;
        }
        estimate = intersect(first.ray, ray);
        if (estimate == null) {
            // Retain only the latest ray; changed targets cannot accumulate into a confident average.
            first = next;
            status = "Trails do not give a reliable destination. Move farther sideways and try again.";
        } else {
            first = null;
            solvedUseAt = usedAt;
            status = "Compass destination estimate";
        }
    }
    static Ray fit(List<Point> samples) {
        if (samples.size() < 8) return null;
        Point start = samples.getFirst(), delta = samples.getLast().subtract(start);
        double span = delta.length();
        if (span < 4 || span > 32) return null;
        Point direction = delta.scale(1 / span);
        double error = 0;
        // Least squares axis via covariance power iteration, oriented with emission order.
        Point mean = new Point(0, 0, 0);
        for (Point sample : samples) mean = mean.add(sample);
        mean = mean.scale(1.0 / samples.size());
        for (int i = 0; i < 12; i++) {
            Point axis = new Point(0, 0, 0);
            for (Point sample : samples) {
                Point centered = sample.subtract(mean);
                axis = axis.add(centered.scale(centered.dot(direction)));
            }
            if (axis.length() < 1e-9) return null;
            direction = axis.scale(1 / axis.length());
        }
        if (direction.dot(delta) < 0) direction = direction.scale(-1);
        double previous = -1;
        for (Point sample : samples) {
            Point relative = sample.subtract(start);
            double progress = relative.dot(direction);
            if (progress < previous - 0.15) return null;
            previous = progress;
            error = Math.max(error, relative.subtract(direction.scale(progress)).length());
        }
        if (error > 0.2) return null;
        // Packet centres are doubles; use a small coordinate allowance rather than an
        // arbitrary angular floor that made an advertised eight-block baseline unusable.
        return new Ray(mean, direction, Math.max(0.001, error) / span);
    }
    static Estimate intersect(Ray a, Ray b) {
        Point offset = a.origin.subtract(b.origin);
        double dot = a.direction.dot(b.direction), determinant = 1 - dot * dot;
        if (determinant < 1e-6) return null; // Numerically parallel; error allowance guards the rest.
        double ad = a.direction.dot(offset), bd = b.direction.dot(offset);
        double alongA = (dot * bd - ad) / determinant;
        double alongB = (bd - dot * ad) / determinant;
        if (alongA < 0 || alongB < 0 || alongA > 1_000 || alongB > 1_000) return null;
        Point pa = a.origin.add(a.direction.scale(alongA)), pb = b.origin.add(b.direction.scale(alongB));
        double gap = pa.distance(pb);
        double uncertainty = Math.max(2, gap / 2 +
            (alongA * a.angularError + alongB * b.angularError) / Math.sqrt(determinant));
        Point position = pa.add(pb).scale(0.5);
        if (gap > 3 || uncertainty > 40 || !inHollows(position)) return null;
        return new Estimate(position, uncertainty);
    }
    static boolean inHollows(Point p) {
        return p.finite() && p.x >= 201 && p.x <= 824 && p.z >= 201 && p.z <= 824 && p.y >= 30 && p.y <= 189;
    }
    private record CaptureResult(Ray ray, boolean ambiguous) {}
    private static final class Capture {
        final Point usedAt;
        final long started;
        final List<Trail> trails = new ArrayList<>();
        long lastParticle;
        int packets;
        boolean overflow;
        Capture(Point usedAt, long started) { this.usedAt = usedAt; this.started = started; lastParticle = started; }
        void accept(Point point, long now) {
            if (++packets > 512) { overflow = true; return; }
            // Unrelated distant particles cannot keep a completed local reading alive.
            if (point.distance(usedAt) <= 41) lastParticle = now;
            for (Trail trail : trails) trail.accept(point);
            if (point.distance(usedAt) <= 9 && trails.stream().noneMatch(trail -> !trail.complete && point.distance(trail.start) <= 0.25)) {
                if (trails.size() >= 128) overflow = true;
                else trails.add(new Trail(point));
            }
        }
        CaptureResult result() {
            if (overflow) return new CaptureResult(null, true);
            Ray selected = null;
            double widest = 0;
            for (Trail trail : trails) {
                Ray candidate = trail.qualified();
                if (candidate != null && trail.span() > widest) {
                    selected = candidate; widest = trail.span();
                }
            }
            if (selected == null) return new CaptureResult(null, false);
            // A coherent competing first pass is already enough to make ownership ambiguous,
            // even if its repeat arrives too late. Never publish a provisional waypoint.
            for (Trail trail : trails) {
                Ray candidate = trail.confirmed != null ? trail.confirmed : fit(trail.samples);
                if (candidate != null && !sameTrail(selected, candidate)) return new CaptureResult(null, true);
            }
            return new CaptureResult(selected, false);
        }
        String diagnostic() {
            int longest = 0, repeated = 0;
            double span = 0;
            for (Trail trail : trails) {
                longest = Math.max(longest, trail.samples.size());
                span = Math.max(span, trail.span());
                if (trail.confirmed != null) repeated++;
            }
            CaptureResult result = result();
            return "packets=" + packets + ", longestPass=" + longest + ", span=" + Math.round(span * 100) / 100.0
                + ", repeats=" + repeated + ", result=" + (result.ambiguous ? "ambiguous" : result.ray != null ? "qualified" : "incomplete");
        }
        private static boolean sameTrail(Ray a, Ray b) {
            double angularAllowance = a.angularError + b.angularError;
            if (a.direction.dot(b.direction) < Math.cos(angularAllowance)) return false;
            Point offset = a.origin.subtract(b.origin);
            double positionAllowance = Math.max(0.1, angularAllowance * Math.max(8, offset.length()));
            return offset.subtract(a.direction.scale(offset.dot(a.direction))).length() <= positionAllowance
                && offset.subtract(b.direction.scale(offset.dot(b.direction))).length() <= positionAllowance;
        }
    }
    private static final class Trail {
        final Point start;
        final List<Point> samples = new ArrayList<>();
        int repeatIndex = -1;
        boolean complete;
        Ray confirmed;
        Trail(Point start) { this.start = start; samples.add(start); }
        double span() { return samples.getLast().distance(start); }
        Ray qualified() { return confirmed != null ? confirmed : samples.size() >= 25 ? fit(samples) : null; }
        void accept(Point point) {
            if (complete) return;
            if (repeatIndex >= 0) {
                if (point.distance(samples.get(repeatIndex)) <= 0.6 && ++repeatIndex >= 3) {
                    complete = true; confirmed = fit(samples);
                }
                return;
            }
            Point previous = samples.getLast();
            if (samples.size() >= 8 && previous.distance(start) >= 4 && point.distance(start) <= 0.6) {
                repeatIndex = 1;
            } else if (point.distance(previous) > 0.05 && point.distance(previous) <= 0.9 && samples.size() < 128) {
                samples.add(point);
            }
        }
    }
}

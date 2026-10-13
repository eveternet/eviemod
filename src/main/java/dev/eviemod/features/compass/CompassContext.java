package dev.eviemod.features.compass;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Reference-backed tab metadata and coordinate guards; no destination names are inferred. */
final class CompassContext {
    enum Region {
        JUNGLE, MITHRIL, GOBLIN, PRECURSOR, MAGMA, NUCLEUS, UNKNOWN;
        boolean eligible() { return this != NUCLEUS && this != UNKNOWN; }
    }
    enum Crystal { AMETHYST, JADE, AMBER, SAPPHIRE, TOPAZ }
    enum Status { NOT_FOUND, FOUND, PLACED, UNKNOWN }
    enum Scent { PRESENT, ABSENT, UNKNOWN }
    record Prerequisites(boolean crystalSection, List<Status> crystals, Scent scent) {
        Prerequisites { crystals = List.copyOf(crystals); }
        boolean completeCrystals() {
            return crystalSection && crystals.size() == Crystal.values().length && crystals.stream().noneMatch(value -> value == Status.UNKNOWN);
        }
        boolean readyFor(Region region) {
            return region.eligible() && completeCrystals() && (region != Region.GOBLIN || scent != Scent.UNKNOWN);
        }
    }
    record FooterSnapshot(Object level, Object connection, String text) {
        String forWorld(Object currentLevel, Object currentConnection) {
            return level == currentLevel && connection == currentConnection ? text : null;
        }
    }
    private static final Pattern FORMAT = Pattern.compile("§.");
    private static final Pattern CRYSTAL_ROW = Pattern.compile("^(amethyst|jade|amber|sapphire|topaz)(?: crystal)?\\s*:\\s*(.*)$");
    private CompassContext() {}

    static Region region(CompassSolver.Point position) {
        if (!CompassSolver.inHollows(position)) return Region.UNKNOWN;
        // Include the boundary itself conservatively. Above Y181 the quadrant applies again.
        if (position.x() >= 462 && position.x() <= 564 && position.y() >= 63 && position.y() <= 181
            && position.z() >= 461 && position.z() <= 565) return Region.NUCLEUS;
        if (position.y() < 64) return Region.MAGMA;
        if (position.x() < 512) return position.z() < 512 ? Region.JUNGLE : Region.GOBLIN;
        return position.z() < 512 ? Region.MITHRIL : Region.PRECURSOR;
    }
    static Prerequisites prerequisites(List<String> entries, String footer) {
        var lines = entries.stream().flatMap(value -> value.lines()).map(CompassContext::normalize).toList();
        boolean section = lines.stream().anyMatch(value -> value.equals("crystals:"));
        Status[] states = new Status[Crystal.values().length];
        Arrays.fill(states, Status.UNKNOWN);
        boolean[] seen = new boolean[states.length];
        if (section) for (String line : lines) {
            var match = CRYSTAL_ROW.matcher(line);
            if (!match.matches()) continue;
            int index = Crystal.valueOf(match.group(1).toUpperCase(Locale.ROOT)).ordinal();
            String value = match.group(2).replace("✖", "").replace("✔", "").replace("✓", "").replace("✘", "").trim();
            Status status = switch (value) {
                case "not found" -> Status.NOT_FOUND;
                case "found" -> Status.FOUND;
                case "placed" -> Status.PLACED;
                default -> Status.UNKNOWN;
            };
            states[index] = seen[index] && states[index] != status ? Status.UNKNOWN : status;
            seen[index] = true;
        }
        boolean present = lines.stream().anyMatch(value -> value.contains("king's scent"))
            || footer != null && normalize(footer).contains("king's scent");
        boolean effectsKnown = footer != null || lines.stream().anyMatch(value -> value.startsWith("active effects:"));
        Scent scent = present ? Scent.PRESENT : effectsKnown ? Scent.ABSENT : Scent.UNKNOWN;
        return new Prerequisites(section, Arrays.asList(states), scent);
    }
    private static String normalize(String value) { return FORMAT.matcher(value).replaceAll("").toLowerCase(Locale.ROOT).trim(); }
}

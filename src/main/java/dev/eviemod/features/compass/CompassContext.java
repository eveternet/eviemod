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
    private static final Pattern EFFECT_HEADER = Pattern.compile("^active effects\\s*(?::\\s*)?(?:\\(([0-9]{1,3})\\)|([0-9]{1,3}))?$");
    private static final Pattern EFFECT_COUNT = Pattern.compile("^you have ([0-9]{1,3}) active effects?\\.?$");
    private static final String DURATION = "(?:[0-9]+:[0-9]{2}(?::[0-9]{2})?|(?:[0-9]+\\s*[dhms]\\s*)+)";
    private static final Pattern EFFECT_ROW = Pattern.compile("^(?:[•●*]\\s*)?([\\p{L}][\\p{L} '’\\-]+?)\\s+[ivxlcdm]+\\s*(?::\\s*)?" + DURATION + "$");
    private static final Pattern SCENT_ROW = Pattern.compile("^(?:[•●*]\\s*)?king's scent(?:\\s+i)?\\s*(?::\\s*)?(?:" + DURATION + ")?$");
    private static final Pattern NO_EFFECTS = Pattern.compile("^(?:no (?:active |potion )?effects(?: (?:are )?(?:currently )?active)?|you have no active effects)\\.?$");
    private static final Pattern INCOMPLETE_EFFECTS = Pattern.compile("^(?:loading\\.*|\\.{3}|…|(?:and )?[0-9]+ more(?: effects)?\\.*)$");
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
        var footerLines = footer == null ? List.<String>of() : footer.lines().map(CompassContext::normalize).toList();
        boolean present = lines.stream().anyMatch(CompassContext::scentRow) || footerLines.stream().anyMatch(CompassContext::scentRow);
        boolean effectsKnown = completeEffects(lines) || completeEffects(footerLines);
        Scent scent = present ? Scent.PRESENT : effectsKnown ? Scent.ABSENT : Scent.UNKNOWN;
        return new Prerequisites(section, Arrays.asList(states), scent);
    }
    private static boolean scentRow(String line) { return SCENT_ROW.matcher(withoutParentheses(line)).matches(); }
    private static boolean completeEffects(List<String> lines) {
        boolean section = false, explicitNone = false, conflictingCount = false, incomplete = false;
        Integer count = null;
        var effects = new java.util.HashSet<String>();
        for (String line : lines) {
            var header = EFFECT_HEADER.matcher(line);
            if (header.matches()) {
                section = true;
                String number = header.group(1) != null ? header.group(1) : header.group(2);
                if (number != null) {
                    int next = Integer.parseInt(number);
                    conflictingCount |= count != null && count != next;
                    count = next;
                }
            }
            var total = EFFECT_COUNT.matcher(line);
            if (total.matches()) {
                int next = Integer.parseInt(total.group(1));
                conflictingCount |= count != null && count != next;
                count = next;
            }
            explicitNone |= NO_EFFECTS.matcher(line).matches();
            incomplete |= INCOMPLETE_EFFECTS.matcher(line).matches();
            var row = EFFECT_ROW.matcher(withoutParentheses(line));
            if (row.matches()) effects.add(row.group(1));
        }
        if (!section || conflictingCount || incomplete) return false;
        if (explicitNone) return effects.isEmpty() && (count == null || count == 0);
        if (count != null) return count == effects.size();
        // A section with effect rows is disclosed data; an isolated header is not.
        return !effects.isEmpty();
    }
    private static String withoutParentheses(String value) { return value.replace("(", "").replace(")", "").trim(); }
    private static String normalize(String value) { return FORMAT.matcher(value).replaceAll("").toLowerCase(Locale.ROOT).trim(); }
}

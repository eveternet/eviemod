package dev.eviemod.features.experiments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One open table only. Observations are server snapshots; clicks always remain manual. */
public final class ExperimentSession {
    public enum Game { CHRONOMATRON, ULTRASEQUENCER, SUPERPAIRS }
    public enum Phase { UNKNOWN, REMEMBER, REPLAY, PAIRS }
    public enum Kind { OTHER, NOTE, NUMBER, HIDDEN, REWARD, POWERUP }
    public record Cell(Kind kind, String identity, int number, boolean lit, boolean second) {
        public static final Cell OTHER = new Cell(Kind.OTHER, "", 0, false, false);
    }
    public record Board(Phase phase, int round, List<Cell> cells) {
        public Board { cells = List.copyOf(cells); }
    }
    private final Game game;
    private Board board;
    private Phase phase = Phase.UNKNOWN;
    private int round, cursor, litSlot = -1;
    private boolean recording, complete, awaitingReveal;
    private final List<String> notes = new ArrayList<>();
    private final Map<Integer, Integer> numbers = new HashMap<>();
    private final Map<Integer, Cell> memory = new HashMap<>();

    public ExperimentSession(Game game) { this.game = game; }
    public void clear() {
        board = null; phase = Phase.UNKNOWN; round = cursor = 0; litSlot = -1;
        recording = complete = awaitingReveal = false;
        notes.clear(); numbers.clear(); memory.clear();
    }
    public void observe(Board next) {
        if (next.cells().size() != 54 || next.phase() == Phase.UNKNOWN) { clear(); return; }
        if (game == Game.SUPERPAIRS) {
            if (next.phase() != Phase.PAIRS) { clear(); return; }
            // A changed known card means this is a different board. Never keep the old identities.
            for (int slot = 9; slot < 45; slot++) {
                Cell cell = next.cells().get(slot), known = memory.get(slot);
                if (known != null && revealed(cell) && !known.identity().equals(cell.identity())) {
                    memory.clear(); break;
                }
            }
            for (int slot = 9; slot < 45; slot++) {
                Cell cell = next.cells().get(slot);
                if (revealed(cell)) memory.put(slot, cell);
                else if (cell.kind() == Kind.OTHER) memory.remove(slot);
            }
            // Do not infer a first card until the server has supplied the second-choice prompt.
            if (!next.equals(board)) awaitingReveal = false;
            board = next; phase = Phase.PAIRS; return;
        }
        if (next.phase() != Phase.REMEMBER && next.phase() != Phase.REPLAY) { clear(); return; }
        if (next.phase() == Phase.REMEMBER && (phase != Phase.REMEMBER || next.round() != round)) {
            notes.clear(); numbers.clear(); cursor = 0; litSlot = -1; complete = false;
            round = next.round(); recording = round > 0;
        } else if (next.round() != round) {
            // A round counter changing before the instruction packet invalidates the old guidance.
            complete = false; recording = false;
        }
        if (next.phase() == Phase.REMEMBER && recording) {
            if (game == Game.CHRONOMATRON) {
                int lit = -1;
                for (int slot = 9; slot < 45; slot++) {
                    Cell cell = next.cells().get(slot);
                    if (cell.kind() == Kind.NOTE && cell.lit()) {
                        if (lit != -1) { recording = false; break; }
                        lit = slot;
                    }
                }
                if (recording && lit != -1 && lit != litSlot) notes.add(next.cells().get(lit).identity());
                litSlot = lit;
                if (notes.size() > round) recording = false;
            } else {
                // Rebuild from the current reveal, so partial packets cannot leave stale positions.
                var observed = new HashMap<Integer, Integer>();
                for (int slot = 9; slot < 45; slot++) {
                    Cell cell = next.cells().get(slot);
                    if (cell.kind() != Kind.NUMBER) continue;
                    if (cell.number() < 1 || cell.number() > round || observed.put(cell.number(), slot) != null) {
                        recording = false; break;
                    }
                }
                if (recording && observed.size() == round) { numbers.clear(); numbers.putAll(observed); }
            }
        }
        if (next.phase() == Phase.REPLAY && phase == Phase.REMEMBER) {
            complete = recording && (game == Game.CHRONOMATRON ? notes.size() == round : numbers.size() == round);
            cursor = 0;
        }
        board = next; phase = next.phase();
    }
    private static boolean revealed(Cell cell) { return cell.kind() == Kind.REWARD || cell.kind() == Kind.POWERUP; }
    private boolean playable(int slot) {
        if (board == null || slot < 9 || slot >= 45) return false;
        Kind kind = board.cells().get(slot).kind();
        return kind != Kind.OTHER;
    }
    public boolean hidden(int slot) { return playable(slot) && board.cells().get(slot).kind() == Kind.HIDDEN; }
    public Cell remembered(int slot) { return hidden(slot) ? memory.get(slot) : null; }
    public int number(int slot) {
        if (phase != Phase.REPLAY || !complete || game != Game.ULTRASEQUENCER) return 0;
        return numbers.entrySet().stream().filter(e -> e.getValue() == slot).map(Map.Entry::getKey).findFirst().orElse(0);
    }
    public boolean next(int slot) {
        if (!playable(slot)) return false;
        if (game == Game.SUPERPAIRS) {
            Cell first = firstCard(), known = remembered(slot);
            return first != null && known != null && known.kind() == Kind.REWARD && first.identity().equals(known.identity());
        }
        if (phase != Phase.REPLAY || !complete || cursor >= round) return false;
        return game == Game.CHRONOMATRON ? board.cells().get(slot).identity().equals(notes.get(cursor))
            : numbers.getOrDefault(cursor + 1, -1) == slot;
    }
    public boolean knownMatch(int slot) {
        Cell known = remembered(slot);
        if (known == null || known.kind() != Kind.REWARD) return false;
        return memory.entrySet().stream().anyMatch(e -> e.getKey() != slot && hidden(e.getKey())
            && e.getValue().kind() == Kind.REWARD && known.identity().equals(e.getValue().identity()));
    }
    private Cell firstCard() {
        if (board == null || awaitingReveal || !board.cells().stream().anyMatch(Cell::second)) return null;
        Cell first = null;
        for (int slot = 9; slot < 45; slot++) {
            Cell cell = board.cells().get(slot);
            if (!revealed(cell)) continue;
            if (cell.kind() == Kind.POWERUP || first != null) return null;
            first = cell;
        }
        return first;
    }
    /** Return true only when a known wrong selection may be cancelled. */
    public boolean click(int slot, boolean protect) {
        if (!playable(slot)) return false;
        if (game == Game.SUPERPAIRS) {
            Cell first = firstCard(), known = remembered(slot);
            boolean wrong = first != null && known != null && known.kind() == Kind.REWARD
                && !first.identity().equals(known.identity());
            if (protect && wrong) return true;
            // Multiple clicks before a reveal cannot reuse a stale first-card observation.
            awaitingReveal = true; return false;
        }
        if (phase != Phase.REPLAY || !complete || cursor >= round) return false;
        if (next(slot)) cursor++;
        else if (protect) return true;
        else complete = false; // A manually allowed mistake ends our reliable replay state.
        return false;
    }
}

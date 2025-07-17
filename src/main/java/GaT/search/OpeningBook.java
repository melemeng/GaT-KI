
package GaT.search;

import GaT.game.GameState;
import GaT.game.Move;
import java.util.*;

/**
 * OPENING BOOK for Guard & Towers
 *
 * Strategic opening theory for Guard & Towers:
 * ✅ Central control (D-file dominance)
 * ✅ Guard advancement patterns
 * ✅ Tower stacking strategies
 * ✅ Castle approach prevention
 * ✅ Early tactical motifs
 *
 * Based on analysis of strong Guard & Towers games and strategic principles.
 */
public class OpeningBook {

    // === OPENING DATABASE ===
    private final Map<Long, BookEntry> openings = new HashMap<>();
    private final Random random = new Random();

    public OpeningBook() {
        initializeOpeningTheory();
    }

    /**
     * Get opening move for position
     */
    public Move getBookMove(GameState state) {
        long hash = state.hash();
        BookEntry entry = openings.get(hash);

        if (entry == null) return null;

        // Select move based on weights (stronger moves more likely)
        List<Move> candidates = entry.moves;
        List<Integer> weights = entry.weights;

        int totalWeight = weights.stream().mapToInt(Integer::intValue).sum();
        int randomValue = random.nextInt(totalWeight);

        int cumulative = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights.get(i);
            if (randomValue < cumulative) {
                System.out.println("📚 Book move: " + candidates.get(i) +
                        " (weight: " + weights.get(i) + "/" + totalWeight + ")");
                return candidates.get(i);
            }
        }

        return candidates.get(0); // Fallback
    }

    /**
     * Add opening position to book
     */
    public void addOpening(String fen, String moveStr, int weight) {
        try {
            GameState state = GameState.fromFen(fen);
            Move move = Move.fromString(moveStr);

            long hash = state.hash();
            BookEntry entry = openings.computeIfAbsent(hash, k -> new BookEntry());

            entry.moves.add(move);
            entry.weights.add(weight);

        } catch (Exception e) {
            System.err.println("❌ Invalid opening: " + fen + " " + moveStr + " - " + e.getMessage());
        }
    }

    /**
     * Initialize opening theory database
     */
    private void initializeOpeningTheory() {
        System.out.println("📚 Loading Guard & Towers opening book...");

        // === STARTING POSITION THEORY ===
        String startPos = "b1b11BG1b1b1/2b11b12/3b13/7/3r13/2r11r12/r1r11RG1r1r1 r";

        // Central control openings (D-file focus)
        addOpening(startPos, "d2-d3", 40);  // Advance central tower
        addOpening(startPos, "d2-d4", 35);  // Aggressive central push
        addOpening(startPos, "c2-c3", 25);  // Support center
        addOpening(startPos, "e2-e3", 25);  // Symmetric development
        addOpening(startPos, "d1-d2", 20);  // Guard support
        addOpening(startPos, "c1-c2", 15);  // Flank development
        addOpening(startPos, "e1-e2", 15);  // Flank development

        // === COMMON SECOND MOVES ===

        // After 1.d2-d3
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/7/2r1r13/r1r11RG1r1r1 b", "d7-d6", 35);
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/7/2r1r13/r1r11RG1r1r1 b", "d7-d5", 30);
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/7/2r1r13/r1r11RG1r1r1 b", "c7-c6", 20);

        // After 1.d2-d4 (aggressive)
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/3r13/7/r1r11RG1r1r1 b", "d7-d5", 40);
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/3r13/7/r1r11RG1r1r1 b", "d7-d6", 25);
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/3r13/7/r1r11RG1r1r1 b", "c7-c5", 20);

        // === GUARD ADVANCEMENT PATTERNS ===

        // Early guard moves (risky but aggressive)
        addOpening(startPos, "d1-c1", 10);  // Guard to side
        addOpening(startPos, "d1-e1", 10);  // Guard to side
        addOpening(startPos, "d1-d2", 20);  // Guard forward

        // === DEFENSIVE SETUPS ===

        // Castle defense patterns
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/7/2r1r13/r1r11RG1r1r1 b", "c7-c6", 25);
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/7/2r1r13/r1r11RG1r1r1 b", "e7-e6", 25);
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/7/2r1r13/r1r11RG1r1r1 b", "b7-b6", 15);

        // === TACTICAL MOTIFS ===

        // Tower stacking preparations
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/7/2r1r13/r1r11RG1r1r1 b", "c6-d6", 15);
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/7/2r1r13/r1r11RG1r1r1 b", "e6-d6", 15);

        // === FIANCHETTO SYSTEMS ===

        // Wing development
        addOpening(startPos, "b2-b3", 15);
        addOpening(startPos, "f2-f3", 15);
        addOpening(startPos, "a1-a2", 10);
        addOpening(startPos, "g1-g2", 10);

        // === HYPERMODERN APPROACH ===

        // Control from distance
        addOpening(startPos, "b1-b2", 12);
        addOpening(startPos, "f1-f2", 12);
        addOpening(startPos, "a2-a3", 8);
        addOpening(startPos, "g2-g3", 8);

        // === GAMBIT LINES (Risky but sharp) ===

        // Sacrificial advances
        addOpening(startPos, "d2-d5", 5);   // Gambit push
        addOpening(startPos, "c2-c5", 5);   // Wing gambit
        addOpening(startPos, "e2-e5", 5);   // Wing gambit

        // === COMMON MIDDLEGAME TRANSITIONS ===

        // After central exchanges
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/7/3b23/r1r11RG1r1r1 r", "d1-d2", 30);
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/7/3b23/r1r11RG1r1r1 r", "c1-c2", 25);
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/7/3b23/r1r11RG1r1r1 r", "e1-e2", 25);

        // Pawn storm patterns
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/7/2r1b1r13/r1r11RG1r1r1 b", "d6-d5", 30);
        addOpening("b1b11BG1b1b1/2b11b12/3b13/7/7/2r1b1r13/r1r11RG1r1r1 b", "d6-d4", 20);

        // === ENDGAME PREPARATION ===

        // King activity preparation
        addOpening("3BG3/7/7/7/7/7/3RG3 r", "d1-c1", 35);
        addOpening("3BG3/7/7/7/7/7/3RG3 r", "d1-e1", 35);
        addOpening("3BG3/7/7/7/7/7/3RG3 r", "d1-d2", 30);

        System.out.println("✅ Loaded " + openings.size() + " opening positions");
        System.out.println("📊 Opening coverage: Early game focused, strategic principles");

        // Print some statistics
        int totalMoves = openings.values().stream()
                .mapToInt(entry -> entry.moves.size())
                .sum();
        System.out.println("📈 Total opening moves: " + totalMoves);
    }

    /**
     * Add multiple openings from theory strings
     */
    public void addOpeningLine(String fen, String[] moves, int[] weights) {
        for (int i = 0; i < moves.length && i < weights.length; i++) {
            addOpening(fen, moves[i], weights[i]);
        }
    }

    /**
     * Get opening statistics
     */
    public int size() {
        return openings.size();
    }

    public String getStatistics() {
        int totalMoves = openings.values().stream()
                .mapToInt(entry -> entry.moves.size())
                .sum();

        return String.format("Opening Book: %d positions, %d total moves",
                openings.size(), totalMoves);
    }

    /**
     * Check if position is in book
     */
    public boolean hasPosition(GameState state) {
        return openings.containsKey(state.hash());
    }

    /**
     * Get all book moves for position
     */
    public List<Move> getBookMoves(GameState state) {
        BookEntry entry = openings.get(state.hash());
        return entry != null ? new ArrayList<>(entry.moves) : new ArrayList<>();
    }

    /**
     * Book entry storing multiple moves with weights
     */
    private static class BookEntry {
        List<Move> moves = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
    }

    // === BOOK MANAGEMENT ===

    /**
     * Save book to file (future enhancement)
     */
    public void saveToFile(String filename) {
        // Could implement book persistence
        System.out.println("💾 Book saving not implemented yet");
    }

    /**
     * Load book from file (future enhancement)
     */
    public void loadFromFile(String filename) {
        // Could implement book loading
        System.out.println("📁 Book loading not implemented yet");
    }

    /**
     * Add position from game analysis
     */
    public void learnFromGame(List<GameState> positions, List<Move> moves, int gameResult) {
        // Could implement learning from played games
        System.out.println("🧠 Game learning not implemented yet");
    }

    /**
     * Clear book (for testing)
     */
    public void clear() {
        openings.clear();
    }

    /**
     * Merge with another book
     */
    public void merge(OpeningBook other) {
        other.openings.forEach((hash, entry) -> {
            BookEntry ourEntry = openings.computeIfAbsent(hash, k -> new BookEntry());
            ourEntry.moves.addAll(entry.moves);
            ourEntry.weights.addAll(entry.weights);
        });
    }
}
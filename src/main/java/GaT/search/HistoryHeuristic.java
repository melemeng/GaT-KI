package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;

/**
 * HISTORY HEURISTIC IMPLEMENTATION - SINGLE-THREADED VERSION
 * Optimized single-threaded implementation of the History Heuristic for Guard & Towers
 *
 * FEATURES:
 * ✅ Removed move.piece dependency (doesn't exist in Move class)
 * ✅ Uses GameState.redToMove to determine player color
 * ✅ Compatible with existing MoveOrdering architecture
 * ✅ Single-threaded optimization (no locks)
 * ✅ Fast access for search algorithms
 *
 * @author Guard & Towers AI Team
 */
public class HistoryHeuristic {

    // === CONFIGURATION CONSTANTS ===
    private static final int MAX_HISTORY_SCORE = 10000;
    private static final int AGING_SHIFT = 1; // Divide scores by 2 when aging
    private static final int AGING_THRESHOLD = 50000; // Age when max entry exceeds this

    // === HISTORY TABLES ===
    // Separate tables for Red and Blue moves to avoid interference
    private final int[][] redHistoryTable = new int[64][64]; // [from][to]
    private final int[][] blueHistoryTable = new int[64][64]; // [from][to]

    // Statistics for debugging/optimization
    private long updateCount = 0;
    private long maxScoreEver = 0;

    // === CORE INTERFACE ===

    /**
     * Update history score for a move that caused a beta cutoff
     *
     * @param move The move that caused the cutoff
     * @param depth Current search depth (higher depth = more important)
     * @param isRedMove Whether this is a red player move
     */
    public void update(Move move, int depth, boolean isRedMove) {
        if (move == null || !isValidSquare(move.from) || !isValidSquare(move.to)) {
            return;
        }

        // Calculate bonus based on depth (deeper searches are more valuable)
        int bonus = calculateDepthBonus(depth);

        // Determine which player's table to update
        int[][] table = isRedMove ? redHistoryTable : blueHistoryTable;

        // Update history score
        int currentScore = table[move.from][move.to];
        table[move.from][move.to] = Math.min(currentScore + bonus, MAX_HISTORY_SCORE);

        // Statistics
        updateCount++;
        maxScoreEver = Math.max(maxScoreEver, table[move.from][move.to]);

        // Age table if necessary
        if (table[move.from][move.to] > AGING_THRESHOLD) {
            ageHistoryTable(table);
        }
    }

    /**
     * Get history score for a move
     *
     * @param move The move to score
     * @param isRedMove Whether this is a red player move
     * @return History score (higher = better historically)
     */
    public int getScore(Move move, boolean isRedMove) {
        if (move == null || !isValidSquare(move.from) || !isValidSquare(move.to)) {
            return 0;
        }

        int[][] table = isRedMove ? redHistoryTable : blueHistoryTable;
        return table[move.from][move.to];
    }

    // === INTEGRATION HELPERS ===

    /**
     * Check if a move should use history heuristic
     * (i.e., is a quiet move - not capture, not killer)
     *
     * @param move The move to check
     * @param state Current game state
     * @return true if move should use history scoring
     */
    public boolean isQuietMove(Move move, GameState state) {
        if (move == null || state == null) {
            return false;
        }

        // Check if it's a capture
        long toBit = GameState.bit(move.to);
        boolean isCapture = ((state.redTowers | state.blueTowers |
                state.redGuard | state.blueGuard) & toBit) != 0;

        return !isCapture; // Not a capture = quiet move (killer check done in MoveOrdering)
    }

    // === MAINTENANCE OPERATIONS ===

    /**
     * Reset all history tables (call before new search)
     */
    public void reset() {
        clearTable(redHistoryTable);
        clearTable(blueHistoryTable);
        updateCount = 0;
        maxScoreEver = 0;
    }

    /**
     * Age history tables by reducing all scores
     * (call periodically to prevent score inflation)
     */
    public void age() {
        ageHistoryTable(redHistoryTable);
        ageHistoryTable(blueHistoryTable);
    }

    // === UTILITY METHODS ===

    private int calculateDepthBonus(int depth) {
        // Quadratic bonus: deeper searches get exponentially more weight
        return depth * depth;
    }

    private boolean isValidSquare(int square) {
        return square >= 0 && square < 64;
    }

    private void clearTable(int[][] table) {
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                table[i][j] = 0;
            }
        }
    }

    private void ageHistoryTable(int[][] table) {
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                table[i][j] >>>= AGING_SHIFT; // Unsigned right shift = divide by 2
            }
        }
    }

    // === DEBUGGING/STATISTICS ===

    /**
     * Get statistics for debugging
     */
    public String getStatistics() {
        int redEntries = countNonZeroEntries(redHistoryTable);
        int blueEntries = countNonZeroEntries(blueHistoryTable);

        return String.format(
                "HistoryHeuristic Stats: Updates=%d, MaxScore=%d, " +
                        "RedEntries=%d, BlueEntries=%d",
                updateCount, maxScoreEver, redEntries, blueEntries
        );
    }

    private int countNonZeroEntries(int[][] table) {
        int count = 0;
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                if (table[i][j] > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Get the highest scoring move for debugging
     */
    public String getBestHistoryMove() {
        int maxScore = 0;
        int bestFrom = -1, bestTo = -1;
        String color = "";

        // Check red table
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                if (redHistoryTable[i][j] > maxScore) {
                    maxScore = redHistoryTable[i][j];
                    bestFrom = i;
                    bestTo = j;
                    color = "RED";
                }
            }
        }

        // Check blue table
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                if (blueHistoryTable[i][j] > maxScore) {
                    maxScore = blueHistoryTable[i][j];
                    bestFrom = i;
                    bestTo = j;
                    color = "BLUE";
                }
            }
        }

        if (maxScore > 0) {
            return String.format("%s move %d->%d (score: %d)",
                    color, bestFrom, bestTo, maxScore);
        } else {
            return "No history moves recorded";
        }
    }
}
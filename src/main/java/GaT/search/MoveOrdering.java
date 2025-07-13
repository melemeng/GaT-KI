package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;

import java.util.List;

/**
 * SIMPLIFIED MOVE ORDERING - Chess Programming Wiki Standard
 *
 * "Good move ordering is the single most important speed improvement" - CPW
 *
 * SIMPLIFIED APPROACH:
 * ✅ Single unified scoreMove() method
 * ✅ Clear priorities: TT-Move > Captures (MVV-LVA) > Killer > History
 * ✅ Removed duplicate history systems (unified to one)
 * ✅ Simplified MVV-LVA for Turm & Wächter
 * ✅ Clean, focused implementation
 * ✅ Removed complex ordering strategies
 */
public class MoveOrdering {

    // === CORE TABLES ===
    private Move[][] killerMoves;
    private int[][] historyTable;    // UNIFIED: Only one history system
    private Move[] pvLine;

    // === CONSTANTS ===
    private static final int TT_MOVE_PRIORITY = 1000000;      // Absolute highest
    private static final int CAPTURE_BASE_PRIORITY = 100000;  // Captures
    private static final int KILLER_1_PRIORITY = 10000;       // First killer
    private static final int KILLER_2_PRIORITY = 9000;        // Second killer
    private static final int HISTORY_MAX_SCORE = 1000;        // History cap
    private static final int POSITIONAL_MAX_SCORE = 500;      // Positional cap

    private static final int HISTORY_MAX = SearchConfig.HISTORY_MAX_VALUE;
    private int killerAge = 0;

    // === CONSTRUCTOR ===
    public MoveOrdering() {
        initializeTables();
    }

    private void initializeTables() {
        killerMoves = new Move[SearchConfig.MAX_KILLER_DEPTH][SearchConfig.KILLER_MOVE_SLOTS];
        historyTable = new int[GameState.NUM_SQUARES][GameState.NUM_SQUARES];
        pvLine = new Move[SearchConfig.MAX_KILLER_DEPTH];
        killerAge = 0;
    }

    // === MAIN INTERFACE: SIMPLIFIED ORDERING ===

    /**
     * UNIFIED MOVE ORDERING - Simple and effective
     * Based on Chess Programming Wiki recommendations
     */
    public void orderMoves(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        if (moves.size() <= 1) return;

        // Simple, effective sorting
        moves.sort((a, b) -> Integer.compare(
                scoreMove(b, state, depth, ttEntry),  // b first for descending order
                scoreMove(a, state, depth, ttEntry)
        ));

        // Update statistics
        SearchStatistics.getInstance().incrementTotalMoveOrderingQueries();
    }

    // === CORE SCORING: CPW STANDARD ===

    /**
     * UNIFIED MOVE SCORING - Chess Programming Wiki Standard
     *
     * Priority Order: TT-Move > Captures (MVV-LVA) > Killer Moves > History Heuristic
     */
    public int scoreMove(Move move, GameState state, int depth, TTEntry ttEntry) {
        // === 1. TT-MOVE (ABSOLUTE PRIORITY) ===
        if (ttEntry != null && move.equals(ttEntry.bestMove)) {
            return TT_MOVE_PRIORITY;
        }

        // === 2. CAPTURES (MVV-LVA) ===
        if (isCapture(move, state)) {
            return CAPTURE_BASE_PRIORITY + evaluateMVVLVA(move, state);
        }

        // === 3. KILLER MOVES ===
        if (isKillerMove(move, depth)) {
            return getKillerPriority(move, depth);
        }

        // === 4. HISTORY HEURISTIC (UNIFIED - only one version!) ===
        int historyScore = getUnifiedHistoryScore(move, state);

        // === 5. BASIC POSITIONAL HINTS ===
        int positionalScore = getBasicPositionalScore(move, state);

        return historyScore + positionalScore;
    }

    // === SIMPLIFIED MVV-LVA FOR TURM & WÄCHTER ===

    /**
     * Most Valuable Victim - Least Valuable Attacker
     * Specialized for Turm & Wächter game mechanics
     */
    private int evaluateMVVLVA(Move move, GameState state) {
        int victimValue = getVictimValue(move, state);
        int attackerValue = getAttackerValue(move, state);

        // MVV-LVA: High victim value, low attacker value
        return victimValue * 100 - attackerValue;
    }

    private int getVictimValue(Move move, GameState state) {
        long toBit = GameState.bit(move.to);

        // Guard capture = highest priority
        if ((state.redGuard & toBit) != 0 || (state.blueGuard & toBit) != 0) {
            return 1000;
        }

        // Tower capture = material value (height)
        boolean isRed = state.redToMove;
        int victimHeight = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
        return victimHeight * 100;
    }

    private int getAttackerValue(Move move, GameState state) {
        // Guard = low value (should attack preferably)
        if (isGuardMove(move, state)) {
            return 10;
        }

        // Tower = height value
        boolean isRed = state.redToMove;
        int attackerHeight = isRed ? state.redStackHeights[move.from] : state.blueStackHeights[move.from];
        return attackerHeight;
    }

    // === UNIFIED HISTORY - REMOVED DUPLICATES ===

    /**
     * UNIFIED HISTORY SCORE - Only one history system!
     * Removed duplicate history usage for clarity and performance
     */
    private int getUnifiedHistoryScore(Move move, GameState state) {
        if (isCapture(move, state)) return 0; // Only for quiet moves

        int historyScore = 0;

        // Standard History Table (the only one we keep)
        if (move.from < historyTable.length && move.to < historyTable[move.from].length) {
            historyScore = historyTable[move.from][move.to] / 100; // Scaled down
        }

        return Math.min(historyScore, HISTORY_MAX_SCORE); // Capped
    }

    // === KILLER MOVE MANAGEMENT - SIMPLIFIED ===

    private boolean isKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length) return false;

        return move.equals(killerMoves[depth][0]) || move.equals(killerMoves[depth][1]);
    }

    private int getKillerPriority(Move move, int depth) {
        if (depth >= killerMoves.length) return 0;

        if (move.equals(killerMoves[depth][0])) return KILLER_1_PRIORITY;
        if (move.equals(killerMoves[depth][1])) return KILLER_2_PRIORITY;

        return 0;
    }

    // === BASIC POSITIONAL SCORING ===

    /**
     * SIMPLIFIED positional scoring - basic but effective
     */
    private int getBasicPositionalScore(Move move, GameState state) {
        int score = 0;

        // Central squares preference
        if (isCentralSquare(move.to)) {
            score += 50;
        }

        // Guard advancement to enemy castle
        if (isGuardMove(move, state)) {
            score += getGuardAdvancementScore(move, state);
        }

        // Development from back rank
        if (isDevelopmentMove(move, state)) {
            score += 30;
        }

        return Math.min(score, POSITIONAL_MAX_SCORE); // Capped
    }

    private int getGuardAdvancementScore(Move move, GameState state) {
        boolean isRed = state.redToMove;
        int toRank = GameState.rank(move.to);
        int targetRank = isRed ? 0 : 6; // Enemy castle rank

        // Closer to target = better
        int distance = Math.abs(toRank - targetRank);
        return Math.max(0, (7 - distance) * 15);
    }

    // === SIMPLIFIED HELPER METHODS ===

    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private boolean isGuardMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
    }

    private boolean isCentralSquare(int square) {
        int file = GameState.file(square);
        int rank = GameState.rank(square);
        return file >= 2 && file <= 4 && rank >= 2 && rank <= 4;
    }

    private boolean isDevelopmentMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        int fromRank = GameState.rank(move.from);
        int toRank = GameState.rank(move.to);

        // Moving pieces forward from back rank
        return isRed ? (fromRank == 6 && toRank < 6) : (fromRank == 0 && toRank > 0);
    }

    // === CLEAN UPDATE METHODS ===

    /**
     * SIMPLIFIED killer move update
     */
    public void storeKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length || isCapture(move, null)) return;

        // Only update if not already first killer
        if (!move.equals(killerMoves[depth][0])) {
            killerMoves[depth][1] = killerMoves[depth][0];
            killerMoves[depth][0] = move;
        }

        SearchStatistics.getInstance().incrementKillerMoveHits();
    }

    /**
     * SIMPLIFIED history update - only one system!
     */
    public void updateHistory(Move move, int depth, GameState state) {
        if (isCapture(move, state)) return; // Only quiet moves

        // Update standard history table
        if (move.from < historyTable.length && move.to < historyTable[move.from].length) {
            historyTable[move.from][move.to] += depth * depth;

            // Overflow prevention
            if (historyTable[move.from][move.to] > HISTORY_MAX) {
                ageHistoryTable();
            }
        }

        SearchStatistics.getInstance().incrementHistoryMoveHits();
    }

    /**
     * Store PV move for next iteration
     */
    public void storePVMove(Move move, int depth) {
        if (depth < pvLine.length) {
            pvLine[depth] = move;
        }
    }

    // === MAINTENANCE METHODS ===

    /**
     * Age history table to prevent overflow
     */
    private void ageHistoryTable() {
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            for (int j = 0; j < GameState.NUM_SQUARES; j++) {
                historyTable[i][j] /= 2;
            }
        }
    }

    /**
     * Reset for new search
     */
    public void resetForNewSearch() {
        killerAge++;
        if (killerAge > SearchConfig.HISTORY_AGING_THRESHOLD) {
            killerMoves = new Move[SearchConfig.MAX_KILLER_DEPTH][SearchConfig.KILLER_MOVE_SLOTS];
            killerAge = 0;
        }
    }

    /**
     * Clear all data
     */
    public void clear() {
        initializeTables();
    }

    // === LEGACY COMPATIBILITY METHODS ===

    /**
     * Legacy method for compatibility - delegates to main scoreMove
     */
    public int scoreMoveAdvanced(Move move, GameState state, int depth) {
        return scoreMove(move, state, depth, null);
    }

    /**
     * Legacy orderMovesAdvanced - delegates to main orderMoves
     */
    public void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        orderMoves(moves, state, depth, ttEntry);
    }

    /**
     * Legacy updateHistoryOnCutoff - delegates to simplified update
     */
    public void updateHistoryOnCutoff(Move move, GameState state, int depth) {
        updateHistory(move, depth, state);
    }

    // === STATISTICS ===

    /**
     * Get simplified statistics
     */
    public String getStatistics() {
        int killerCount = 0;
        int historyCount = 0;

        for (int i = 0; i < killerMoves.length; i++) {
            if (killerMoves[i][0] != null) killerCount++;
            if (killerMoves[i][1] != null) killerCount++;
        }

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            for (int j = 0; j < GameState.NUM_SQUARES; j++) {
                if (historyTable[i][j] > 0) historyCount++;
            }
        }

        return String.format("SimplifiedMoveOrdering: %d killers, %d history entries, age=%d",
                killerCount, historyCount, killerAge);
    }

    // === COMPATIBILITY GETTERS ===

    /**
     * Compatibility getter for tests
     */
    public HistoryHeuristic getHistoryHeuristic() {
        // Return null since we removed the duplicate history system
        return null;
    }
    public void resetKillerMoves() {
        resetForNewSearch();
    }
}
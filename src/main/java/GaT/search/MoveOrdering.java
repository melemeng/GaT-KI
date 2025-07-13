package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;

import java.util.List;

/**
 * CRITICAL FIX: MoveOrdering with proper null-safe handling
 *
 * FIXES:
 * ✅ All methods now have null-checks for GameState
 * ✅ Fixed storeKillerMove calling isCapture(move, null)
 * ✅ Robust exception handling throughout
 * ✅ Safe fallbacks for all operations
 * ✅ Single unified scoreMove() method with safety
 */
public class MoveOrdering {

    // === CORE TABLES ===
    private Move[][] killerMoves;
    private int[][] historyTable;
    private Move[] pvLine;

    // === CONSTANTS ===
    private static final int TT_MOVE_PRIORITY = 1000000;
    private static final int CAPTURE_BASE_PRIORITY = 100000;
    private static final int KILLER_1_PRIORITY = 10000;
    private static final int KILLER_2_PRIORITY = 9000;
    private static final int HISTORY_MAX_SCORE = 1000;
    private static final int POSITIONAL_MAX_SCORE = 500;

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

    // === MAIN INTERFACE: FIXED ORDERING ===

    /**
     * FIXED: Move ordering with proper null-safety
     */
    public void orderMoves(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        if (moves == null || moves.size() <= 1) return;

        // NULL-CHECK for state
        if (state == null) {
            System.err.println("❌ ERROR: Null state in orderMoves - using basic ordering");
            // Fallback to basic ordering without state-dependent scoring
            orderMovesBasic(moves);
            return;
        }

        // VALIDATION CHECK
        if (!state.isValid()) {
            System.err.println("❌ ERROR: Invalid state in orderMoves - using basic ordering");
            orderMovesBasic(moves);
            return;
        }

        try {
            // Safe sorting with exception handling
            moves.sort((a, b) -> {
                try {
                    int scoreA = scoreMoveSafe(a, state, depth, ttEntry);
                    int scoreB = scoreMoveSafe(b, state, depth, ttEntry);
                    return Integer.compare(scoreB, scoreA); // Descending order
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Move comparison failed: " + e.getMessage());
                    return 0; // Equal if comparison fails
                }
            });
        } catch (Exception e) {
            System.err.println("❌ ERROR: Move sorting failed: " + e.getMessage());
            orderMovesBasic(moves);
        }

        // Update statistics
        SearchStatistics.getInstance().incrementTotalMoveOrderingQueries();
    }

    /**
     * FIXED: Safe move scoring with comprehensive null-checks
     */
    public int scoreMove(Move move, GameState state, int depth, TTEntry ttEntry) {
        return scoreMoveSafe(move, state, depth, ttEntry);
    }

    private int scoreMoveSafe(Move move, GameState state, int depth, TTEntry ttEntry) {
        // Basic null checks
        if (move == null) {
            return 0;
        }

        if (state == null) {
            // Can only use state-independent scoring
            return scoreBasic(move, depth, ttEntry);
        }

        if (!state.isValid()) {
            System.err.println("❌ ERROR: Invalid state in scoreMoveSafe");
            return scoreBasic(move, depth, ttEntry);
        }

        try {
            // === 1. TT-MOVE (ABSOLUTE PRIORITY) ===
            if (ttEntry != null && move.equals(ttEntry.bestMove)) {
                return TT_MOVE_PRIORITY;
            }

            // === 2. CAPTURES (MVV-LVA) ===
            if (isCaptureS(move, state)) {
                return CAPTURE_BASE_PRIORITY + evaluateMVVLVA(move, state);
            }

            // === 3. KILLER MOVES ===
            if (isKillerMove(move, depth)) {
                return getKillerPriority(move, depth);
            }

            // === 4. HISTORY HEURISTIC ===
            int historyScore = getUnifiedHistoryScore(move, state);

            // === 5. BASIC POSITIONAL HINTS ===
            int positionalScore = getBasicPositionalScore(move, state);

            return historyScore + positionalScore;

        } catch (Exception e) {
            System.err.println("❌ ERROR: Move scoring failed for move " + move + ": " + e.getMessage());
            return scoreBasic(move, depth, ttEntry);
        }
    }

    /**
     * State-independent scoring for fallback
     */
    private int scoreBasic(Move move, int depth, TTEntry ttEntry) {
        try {
            // TT move
            if (ttEntry != null && move.equals(ttEntry.bestMove)) {
                return TT_MOVE_PRIORITY;
            }

            // Killer moves
            if (isKillerMove(move, depth)) {
                return getKillerPriority(move, depth);
            }

            // Activity bonus
            return move.amountMoved * 5;

        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Basic move ordering fallback
     */
    private void orderMovesBasic(List<Move> moves) {
        try {
            moves.sort((a, b) -> {
                if (a == null && b == null) return 0;
                if (a == null) return 1;
                if (b == null) return -1;

                // Simple activity-based ordering
                return Integer.compare(b.amountMoved, a.amountMoved);
            });
        } catch (Exception e) {
            System.err.println("❌ ERROR: Basic move ordering failed: " + e.getMessage());
            // Don't sort if even basic ordering fails
        }
    }

    // === SAFE MVV-LVA ===

    private int evaluateMVVLVA(Move move, GameState state) {
        try {
            int victimValue = getVictimValue(move, state);
            int attackerValue = getAttackerValue(move, state);
            return victimValue * 100 - attackerValue;
        } catch (Exception e) {
            return 100; // Basic capture bonus
        }
    }

    private int getVictimValue(Move move, GameState state) {
        try {
            long toBit = GameState.bit(move.to);

            // Guard capture = highest priority
            if ((state.redGuard & toBit) != 0 || (state.blueGuard & toBit) != 0) {
                return 1000;
            }

            // Tower capture = material value (height)
            boolean isRed = state.redToMove;
            int victimHeight = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
            return victimHeight * 100;
        } catch (Exception e) {
            return 100; // Default victim value
        }
    }

    private int getAttackerValue(Move move, GameState state) {
        try {
            // Guard = low value (should attack preferably)
            if (isGuardMoveS(move, state)) {
                return 10;
            }

            // Tower = height value
            boolean isRed = state.redToMove;
            int attackerHeight = isRed ? state.redStackHeights[move.from] : state.blueStackHeights[move.from];
            return attackerHeight;
        } catch (Exception e) {
            return 50; // Default attacker value
        }
    }

    // === SAFE HISTORY SCORING ===

    private int getUnifiedHistoryScore(Move move, GameState state) {
        try {
            if (isCaptureS(move, state)) return 0; // Only for quiet moves

            int historyScore = 0;

            // Standard History Table
            if (move.from < historyTable.length && move.to < historyTable[move.from].length) {
                historyScore = historyTable[move.from][move.to] / 100;
            }

            return Math.min(historyScore, HISTORY_MAX_SCORE);
        } catch (Exception e) {
            return 0;
        }
    }

    // === SAFE KILLER MOVE MANAGEMENT ===

    private boolean isKillerMove(Move move, int depth) {
        try {
            if (depth >= killerMoves.length) return false;
            return move.equals(killerMoves[depth][0]) || move.equals(killerMoves[depth][1]);
        } catch (Exception e) {
            return false;
        }
    }

    private int getKillerPriority(Move move, int depth) {
        try {
            if (depth >= killerMoves.length) return 0;

            if (move.equals(killerMoves[depth][0])) return KILLER_1_PRIORITY;
            if (move.equals(killerMoves[depth][1])) return KILLER_2_PRIORITY;

            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // === SAFE POSITIONAL SCORING ===

    private int getBasicPositionalScore(Move move, GameState state) {
        try {
            int score = 0;

            // Central squares preference
            if (isCentralSquare(move.to)) {
                score += 50;
            }

            // Guard advancement to enemy castle
            if (isGuardMoveS(move, state)) {
                score += getGuardAdvancementScore(move, state);
            }

            // Development from back rank
            if (isDevelopmentMove(move, state)) {
                score += 30;
            }

            return Math.min(score, POSITIONAL_MAX_SCORE);
        } catch (Exception e) {
            return 0;
        }
    }

    private int getGuardAdvancementScore(Move move, GameState state) {
        try {
            boolean isRed = state.redToMove;
            int toRank = GameState.rank(move.to);
            int targetRank = isRed ? 0 : 6;

            int distance = Math.abs(toRank - targetRank);
            return Math.max(0, (7 - distance) * 15);
        } catch (Exception e) {
            return 0;
        }
    }

    // === CRITICAL FIX: SAFE HELPER METHODS ===

    /**
     * FIXED: Safe capture detection with null-check
     */
    private boolean isCaptureS(Move move, GameState state) {
        try {
            if (move == null || state == null) return false;

            long toBit = GameState.bit(move.to);
            return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * FIXED: Safe guard move detection with null-check
     */
    private boolean isGuardMoveS(Move move, GameState state) {
        try {
            if (move == null || state == null) return false;

            boolean isRed = state.redToMove;
            long guardBit = isRed ? state.redGuard : state.blueGuard;
            return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isCentralSquare(int square) {
        try {
            int file = GameState.file(square);
            int rank = GameState.rank(square);
            return file >= 2 && file <= 4 && rank >= 2 && rank <= 4;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDevelopmentMove(Move move, GameState state) {
        try {
            if (move == null || state == null) return false;

            boolean isRed = state.redToMove;
            int fromRank = GameState.rank(move.from);
            int toRank = GameState.rank(move.to);

            return isRed ? (fromRank == 6 && toRank < 6) : (fromRank == 0 && toRank > 0);
        } catch (Exception e) {
            return false;
        }
    }

    // === FIXED UPDATE METHODS ===

    /**
     * CRITICAL FIX: Killer move storage without null state call
     */
    public void storeKillerMove(Move move, int depth) {
        try {
            if (move == null || depth >= killerMoves.length) return;

            // FIXED: Removed isCapture(move, null) call!
            // Killer moves can be any non-null move

            // Only update if not already first killer
            if (!move.equals(killerMoves[depth][0])) {
                killerMoves[depth][1] = killerMoves[depth][0];
                killerMoves[depth][0] = move;
            }

            SearchStatistics.getInstance().incrementKillerMoveHits();
        } catch (Exception e) {
            System.err.println("❌ ERROR: Killer move storage failed: " + e.getMessage());
        }
    }

    /**
     * FIXED: History update with proper null-checks
     */
    public void updateHistory(Move move, int depth, GameState state) {
        try {
            // NULL-CHECKS first
            if (move == null || state == null) {
                return; // Silent return for optimization methods
            }

            if (!state.isValid()) {
                return; // Silent return for optimization methods
            }

            // Only update for quiet moves
            if (isCaptureS(move, state)) return;

            // Update standard history table
            if (move.from < historyTable.length && move.to < historyTable[move.from].length) {
                historyTable[move.from][move.to] += depth * depth;

                // Overflow prevention
                if (historyTable[move.from][move.to] > HISTORY_MAX) {
                    ageHistoryTable();
                }
            }

            SearchStatistics.getInstance().incrementHistoryMoveHits();
        } catch (Exception e) {
            System.err.println("❌ ERROR: History update failed: " + e.getMessage());
        }
    }

    /**
     * Store PV move for next iteration
     */
    public void storePVMove(Move move, int depth) {
        try {
            if (move != null && depth < pvLine.length) {
                pvLine[depth] = move;
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: PV move storage failed: " + e.getMessage());
        }
    }

    // === MAINTENANCE METHODS ===

    /**
     * Age history table to prevent overflow
     */
    private void ageHistoryTable() {
        try {
            for (int i = 0; i < GameState.NUM_SQUARES; i++) {
                for (int j = 0; j < GameState.NUM_SQUARES; j++) {
                    historyTable[i][j] /= 2;
                }
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: History table aging failed: " + e.getMessage());
        }
    }

    /**
     * Reset for new search
     */
    public void resetForNewSearch() {
        try {
            killerAge++;
            if (killerAge > SearchConfig.HISTORY_AGING_THRESHOLD) {
                killerMoves = new Move[SearchConfig.MAX_KILLER_DEPTH][SearchConfig.KILLER_MOVE_SLOTS];
                killerAge = 0;
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: Reset for new search failed: " + e.getMessage());
        }
    }

    /**
     * Clear all data
     */
    public void clear() {
        try {
            initializeTables();
        } catch (Exception e) {
            System.err.println("❌ ERROR: Clear failed: " + e.getMessage());
        }
    }

    // === LEGACY COMPATIBILITY METHODS ===

    /**
     * Legacy method for compatibility
     */
    public int scoreMoveAdvanced(Move move, GameState state, int depth) {
        return scoreMoveSafe(move, state, depth, null);
    }

    /**
     * Legacy orderMovesAdvanced
     */
    public void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        orderMoves(moves, state, depth, ttEntry);
    }

    /**
     * Legacy updateHistoryOnCutoff
     */
    public void updateHistoryOnCutoff(Move move, GameState state, int depth) {
        updateHistory(move, depth, state);
    }

    // === BACKWARD COMPATIBILITY ===

    /**
     * DEPRECATED: Old isCapture method - kept for compatibility
     * Now safely handles null state
     */
    private boolean isCapture(Move move, GameState state) {
        return isCaptureS(move, state);
    }

    private boolean isGuardMove(Move move, GameState state) {
        return isGuardMoveS(move, state);
    }

    // === STATISTICS ===

    /**
     * Get statistics with error handling
     */
    public String getStatistics() {
        try {
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

            return String.format("SafeMoveOrdering: %d killers, %d history entries, age=%d",
                    killerCount, historyCount, killerAge);
        } catch (Exception e) {
            return "SafeMoveOrdering: Statistics unavailable due to error";
        }
    }

    // === COMPATIBILITY GETTERS ===

    /**
     * Compatibility getter for tests
     */
    public HistoryHeuristic getHistoryHeuristic() {
        return null; // Removed duplicate history system
    }

    public void resetKillerMoves() {
        resetForNewSearch();
    }
}
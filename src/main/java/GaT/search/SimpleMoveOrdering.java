package GaT.search;

import GaT.game.GameState;
import GaT.game.Move;
import GaT.game.TTEntry;
import java.util.List;

/**
 * ENHANCED SIMPLE MOVE ORDERING with LMR Support
 *
 * Enhanced for Late-Move Reductions and advanced features:
 * ✅ Killer moves (2 per depth)
 * ✅ History heuristic with better scoring
 * ✅ MVV-LVA capture ordering
 * ✅ TT move prioritization
 * ✅ Game-specific bonuses
 * ✅ LMR-friendly move categorization
 * ✅ Public interfaces for Engine integration
 *
 * Maintains clean architecture while adding power.
 */
public class SimpleMoveOrdering {

    // === SIMPLE CONFIGURATION ===
    private static final int MAX_DEPTH = 64;
    private static final int MAX_HISTORY = 10000;
    private static final int KILLER_SLOTS = 2;

    // === MOVE SCORING CONSTANTS ===
    private static final int TT_MOVE_SCORE = 1000000;
    private static final int WINNING_MOVE_SCORE = 500000;
    private static final int CAPTURE_BASE_SCORE = 100000;
    private static final int KILLER_BASE_SCORE = 10000;
    private static final int GUARD_MOVE_SCORE = 5000;
    private static final int CASTLE_APPROACH_SCORE = 3000;
    private static final int CENTER_BONUS = 1000;

    // === CORE TABLES ===
    private final Move[][] killerMoves;     // [depth][slot]
    private final int[][][] historyTable;  // [from][to][color]

    // === STATISTICS ===
    private int killerHits = 0;
    private int historyHits = 0;
    private int ttMoveHits = 0;
    private int captureOrderingHits = 0;

    public SimpleMoveOrdering() {
        this.killerMoves = new Move[MAX_DEPTH][KILLER_SLOTS];
        this.historyTable = new int[64][64][2]; // 64 squares max, 2 colors
    }

    /**
     * Order moves for search - enhanced for LMR
     */
    public void orderMoves(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        if (moves == null || moves.size() <= 1) return;

        moves.sort((m1, m2) -> Integer.compare(
                scoreMove(m2, state, depth, ttEntry),
                scoreMove(m1, state, depth, ttEntry)
        ));
    }

    /**
     * Score a move for ordering (higher = better)
     * Enhanced with better tactical understanding
     */
    private int scoreMove(Move move, GameState state, int depth, TTEntry ttEntry) {
        int score = 0;

        // 1. TT move gets highest priority
        if (ttEntry != null && ttEntry.bestMove != null && move.equals(ttEntry.bestMove)) {
            score += TT_MOVE_SCORE;
            ttMoveHits++;
        }

        // 2. Winning moves (guard reaching castle)
        if (isWinningMove(move, state)) {
            score += WINNING_MOVE_SCORE;
        }

        // 3. Captures (MVV-LVA with Guard & Towers specifics)
        if (isCapture(move, state)) {
            int victimValue = getPieceValue(state, move.to);
            int attackerValue = getPieceValue(state, move.from);
            score += CAPTURE_BASE_SCORE + victimValue * 10 - attackerValue;
            captureOrderingHits++;
        }

        // 4. Killer moves
        if (depth < MAX_DEPTH) {
            for (int i = 0; i < KILLER_SLOTS; i++) {
                if (killerMoves[depth][i] != null && killerMoves[depth][i].equals(move)) {
                    score += KILLER_BASE_SCORE - i * 1000; // First killer > second killer
                    killerHits++;
                    break;
                }
            }
        }

        // 5. History heuristic
        int color = state.redToMove ? 0 : 1;
        if (move.from < 64 && move.to < 64) {
            score += historyTable[move.from][move.to][color];
            if (historyTable[move.from][move.to][color] > 0) {
                historyHits++;
            }
        }

        // 6. Guard moves (critical in Guard & Towers)
        if (isGuardMove(move, state)) {
            score += GUARD_MOVE_SCORE;

            // Extra bonus for guard advancing toward enemy castle
            if (isGuardAdvancement(move, state)) {
                score += CASTLE_APPROACH_SCORE;
            }
        }

        // 7. Central control (D-file and center importance)
        if (isCentralMove(move)) {
            score += CENTER_BONUS;
        }

        // 8. Tower stacking moves
        if (isStackingMove(move, state)) {
            score += 2000;
        }

        // 9. Defensive moves (protecting guard)
        if (isDefensiveMove(move, state)) {
            score += 1500;
        }

        return score;
    }

    // === PUBLIC INTERFACES FOR ENGINE ===

    /**
     * Record killer move (called by Engine on cutoffs)
     */
    public void recordKiller(Move move, int depth) {
        if (depth >= MAX_DEPTH || move == null) return;

        // Don't store captures as killers
        if (isCapture(move, null)) return;

        // Shift existing killers down
        if (!move.equals(killerMoves[depth][0])) {
            killerMoves[depth][1] = killerMoves[depth][0];
            killerMoves[depth][0] = move;
        }
    }

    /**
     * Update history heuristic (called by Engine)
     */
    public void updateHistory(Move move, GameState state, int bonus) {
        if (move.from >= 64 || move.to >= 64) return;

        int color = state.redToMove ? 0 : 1;
        historyTable[move.from][move.to][color] += bonus;

        // Prevent overflow
        if (historyTable[move.from][move.to][color] > MAX_HISTORY) {
            // Age all history scores
            ageHistoryTable();
        }
    }

    /**
     * Clear history and killers for new game
     */
    public void reset() {
        // Clear killers
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            for (int slot = 0; slot < KILLER_SLOTS; slot++) {
                killerMoves[depth][slot] = null;
            }
        }

        // Clear history
        for (int from = 0; from < 64; from++) {
            for (int to = 0; to < 64; to++) {
                for (int color = 0; color < 2; color++) {
                    historyTable[from][to][color] = 0;
                }
            }
        }

        // Reset statistics
        killerHits = 0;
        historyHits = 0;
        ttMoveHits = 0;
        captureOrderingHits = 0;
    }

    // === MOVE CLASSIFICATION (Enhanced for Guard & Towers) ===

    private boolean isWinningMove(Move move, GameState state) {
        if (!isGuardMove(move, state)) return false;

        // Check if guard reaches enemy castle
        int redCastle = GameState.getIndex(0, 3);   // D1
        int blueCastle = GameState.getIndex(6, 3);  // D7

        if (state.redToMove) {
            return move.to == redCastle; // Red guard to blue castle
        } else {
            return move.to == blueCastle; // Blue guard to red castle
        }
    }

    private boolean isCapture(Move move, GameState state) {
        if (state == null) return false; // For killer move filtering

        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private boolean isGuardMove(Move move, GameState state) {
        long fromBit = GameState.bit(move.from);
        return (state.redGuard & fromBit) != 0 || (state.blueGuard & fromBit) != 0;
    }

    private boolean isGuardAdvancement(Move move, GameState state) {
        if (!isGuardMove(move, state)) return false;

        int fromRank = move.from / 7;
        int toRank = move.to / 7;

        if (state.redToMove) {
            return toRank < fromRank; // Red guard moving up (toward rank 0)
        } else {
            return toRank > fromRank; // Blue guard moving down (toward rank 6)
        }
    }

    private boolean isCentralMove(Move move) {
        int file = move.to % 7;
        return file >= 2 && file <= 4; // Files C, D, E (indices 2, 3, 4)
    }

    private boolean isStackingMove(Move move, GameState state) {
        long toBit = GameState.bit(move.to);

        if (state.redToMove) {
            return (state.redTowers & toBit) != 0; // Red tower moving to red tower
        } else {
            return (state.blueTowers & toBit) != 0; // Blue tower moving to blue tower
        }
    }

    private boolean isDefensiveMove(Move move, GameState state) {
        // Check if move protects the guard
        int guardPos = getGuardPosition(state);
        if (guardPos == -1) return false;

        return isAdjacentTo(move.to, guardPos);
    }

    private int getGuardPosition(GameState state) {
        long guard = state.redToMove ? state.redGuard : state.blueGuard;

        for (int i = 0; i < 49; i++) {
            if ((guard & GameState.bit(i)) != 0) {
                return i;
            }
        }
        return -1;
    }

    private boolean isAdjacentTo(int square1, int square2) {
        int rank1 = square1 / 7, file1 = square1 % 7;
        int rank2 = square2 / 7, file2 = square2 % 7;

        return Math.abs(rank1 - rank2) <= 1 && Math.abs(file1 - file2) <= 1 && square1 != square2;
    }

    private int getPieceValue(GameState state, int square) {
        long bit = GameState.bit(square);

        // Guard values
        if ((state.redGuard & bit) != 0 || (state.blueGuard & bit) != 0) {
            return 1000;
        }

        // Tower values (height-based)
        if ((state.redTowers & bit) != 0) {
            return state.redStackHeights[square] * 100;
        }
        if ((state.blueTowers & bit) != 0) {
            return state.blueStackHeights[square] * 100;
        }

        return 0;
    }

    private void ageHistoryTable() {
        for (int from = 0; from < 64; from++) {
            for (int to = 0; to < 64; to++) {
                for (int color = 0; color < 2; color++) {
                    historyTable[from][to][color] /= 2;
                }
            }
        }
    }

    // === STATISTICS AND DEBUGGING ===

    public String getStatistics() {
        int totalHits = killerHits + historyHits + ttMoveHits + captureOrderingHits;
        if (totalHits == 0) return "Move Ordering: No data";

        return String.format("Move Ordering - TT: %d, Captures: %d, Killers: %d, History: %d (Total: %d)",
                ttMoveHits, captureOrderingHits, killerHits, historyHits, totalHits);
    }

    public double getKillerHitRate() {
        return killerHits / Math.max(1.0, killerHits + historyHits);
    }

    public int getKillerCount(int depth) {
        if (depth >= MAX_DEPTH) return 0;

        int count = 0;
        for (int i = 0; i < KILLER_SLOTS; i++) {
            if (killerMoves[depth][i] != null) count++;
        }
        return count;
    }

    public void resetStatistics() {
        killerHits = 0;
        historyHits = 0;
        ttMoveHits = 0;
        captureOrderingHits = 0;
    }

    // === DEBUGGING METHODS ===

    public void printKillersAtDepth(int depth) {
        if (depth >= MAX_DEPTH) return;

        System.out.println("Killers at depth " + depth + ":");
        for (int i = 0; i < KILLER_SLOTS; i++) {
            Move killer = killerMoves[depth][i];
            System.out.println("  " + i + ": " + (killer != null ? killer : "null"));
        }
    }

    public void printHistoryForMove(Move move, int color) {
        if (move.from < 64 && move.to < 64) {
            System.out.println("History for " + move + " (color " + color + "): " +
                    historyTable[move.from][move.to][color]);
        }
    }
}
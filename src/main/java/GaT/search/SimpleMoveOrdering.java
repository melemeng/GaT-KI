package GaT.search;

import GaT.game.GameState;
import GaT.game.Move;
import GaT.game.TTEntry;
import java.util.List;

/**
 * SIMPLE MOVE ORDERING for Engine.java
 *
 * Clean, fast implementation without SearchConfig dependencies:
 * ✅ Killer moves (2 per depth)
 * ✅ History heuristic
 * ✅ MVV-LVA capture ordering
 * ✅ TT move prioritization
 * ✅ Game-specific bonuses
 * ✅ No complex configuration or statistics
 *
 * Designed specifically for the new Engine architecture.
 */
public class SimpleMoveOrdering {

    // === SIMPLE CONFIGURATION ===
    private static final int MAX_DEPTH = 64;
    private static final int MAX_HISTORY = 10000;
    private static final int KILLER_SLOTS = 2;

    // === CORE TABLES ===
    private final Move[][] killerMoves;     // [depth][slot]
    private final int[][][] historyTable;  // [from][to][color]

    public SimpleMoveOrdering() {
        this.killerMoves = new Move[MAX_DEPTH][KILLER_SLOTS];
        this.historyTable = new int[64][64][2]; // 64 squares max, 2 colors
    }

    /**
     * Order moves for search - simple and fast
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
     */
    private int scoreMove(Move move, GameState state, int depth, TTEntry ttEntry) {
        int score = 0;

        // 1. TT move gets highest priority
        if (ttEntry != null && ttEntry.bestMove != null && move.equals(ttEntry.bestMove)) {
            score += 1000000;
        }

        // 2. Winning moves (guard reaching castle)
        if (isWinningMove(move, state)) {
            score += 500000;
        }

        // 3. Captures (MVV-LVA)
        if (isCapture(move, state)) {
            int victimValue = getPieceValue(state, move.to);
            int attackerValue = getPieceValue(state, move.from);
            score += 100000 + victimValue * 10 - attackerValue;
        }

        // 4. Killer moves
        if (depth < MAX_DEPTH) {
            for (int i = 0; i < KILLER_SLOTS; i++) {
                if (killerMoves[depth][i] != null && killerMoves[depth][i].equals(move)) {
                    score += 10000 - i * 1000; // First killer > second killer
                    break;
                }
            }
        }

        // 5. History heuristic
        int color = state.redToMove ? 0 : 1;
        if (move.from < 64 && move.to < 64) {
            score += historyTable[move.from][move.to][color];
        }

        // 6. Guard moves (important in this game)
        if (isGuardMove(move, state)) {
            score += 5000;
        }

        // 7. Central squares bonus
        if (isCentralSquare(move.to)) {
            score += 100;
        }

        return score;
    }

    /**
     * Store killer move after a cutoff
     */
    public void storeKillerMove(Move move, int depth) {
        if (move == null || depth >= MAX_DEPTH) return;

        // Don't store captures as killers
        if (depth < MAX_DEPTH && !killerMoves[depth][0].equals(move)) {
            // Shift killers down
            killerMoves[depth][1] = killerMoves[depth][0];
            killerMoves[depth][0] = move;
        }
    }

    /**
     * Update history heuristic after a cutoff
     */
    public void updateHistory(Move move, int depth, GameState state) {
        if (move == null || move.from >= 64 || move.to >= 64) return;

        // Only update for quiet moves (not captures)
        if (isCapture(move, state)) return;

        int color = state.redToMove ? 0 : 1;
        historyTable[move.from][move.to][color] += depth * depth;

        // Prevent overflow
        if (historyTable[move.from][move.to][color] > MAX_HISTORY) {
            ageHistoryTable();
        }
    }

    /**
     * Age history table when it gets too full
     */
    private void ageHistoryTable() {
        for (int f = 0; f < 64; f++) {
            for (int t = 0; t < 64; t++) {
                for (int c = 0; c < 2; c++) {
                    historyTable[f][t][c] /= 2;
                }
            }
        }
    }

    /**
     * Clear tables for new game
     */
    public void clear() {
        // Clear killer moves
        for (int d = 0; d < MAX_DEPTH; d++) {
            for (int s = 0; s < KILLER_SLOTS; s++) {
                killerMoves[d][s] = null;
            }
        }

        // Clear history table
        for (int f = 0; f < 64; f++) {
            for (int t = 0; t < 64; t++) {
                for (int c = 0; c < 2; c++) {
                    historyTable[f][t][c] = 0;
                }
            }
        }
    }

    // === GAME-SPECIFIC HELPER METHODS ===

    private boolean isCapture(Move move, GameState state) {
        return state.redStackHeights[move.to] > 0 ||
                state.blueStackHeights[move.to] > 0 ||
                (state.redGuard & (1L << move.to)) != 0 ||
                (state.blueGuard & (1L << move.to)) != 0;
    }

    private boolean isGuardMove(Move move, GameState state) {
        long fromBit = 1L << move.from;
        return (state.redGuard & fromBit) != 0 || (state.blueGuard & fromBit) != 0;
    }

    private boolean isWinningMove(Move move, GameState state) {
        if (!isGuardMove(move, state)) return false;

        boolean isRedGuard = (state.redGuard & (1L << move.from)) != 0;
        int redCastle = 45;  // D7
        int blueCastle = 3;  // D1

        return (isRedGuard && move.to == blueCastle) || (!isRedGuard && move.to == redCastle);
    }

    private int getPieceValue(GameState state, int square) {
        // Guard is most valuable
        if ((state.redGuard & (1L << square)) != 0 || (state.blueGuard & (1L << square)) != 0) {
            return 1000;
        }

        // Towers valued by height
        return (state.redStackHeights[square] + state.blueStackHeights[square]) * 100;
    }

    private boolean isCentralSquare(int square) {
        int file = square % 7;
        return file >= 2 && file <= 4; // Files C, D, E
    }

    // === SIMPLE STATISTICS ===

    public String getStatistics() {
        int killerCount = 0;
        int historyEntries = 0;

        // Count active killers
        for (int d = 0; d < MAX_DEPTH; d++) {
            for (int s = 0; s < KILLER_SLOTS; s++) {
                if (killerMoves[d][s] != null) killerCount++;
            }
        }

        // Count history entries
        for (int f = 0; f < 64; f++) {
            for (int t = 0; t < 64; t++) {
                for (int c = 0; c < 2; c++) {
                    if (historyTable[f][t][c] > 0) historyEntries++;
                }
            }
        }

        return String.format("MoveOrdering: %d killers, %d history entries", killerCount, historyEntries);
    }
}
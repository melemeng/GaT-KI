package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.evaluation.Evaluator;
import java.util.List;

/**
 * FIXED MOVE ORDERING for Turm & Wächter
 *
 * FIXES:
 * ✅ Removed duplicate scoreMove() methods
 * ✅ Fixed method name conflicts
 * ✅ Clean interface with no ambiguity
 */
public class MoveOrdering {

    // === CORE TABLES ===
    private Move[][] killerMoves;
    private int[][] historyTable;
    private int killerAge = 0;

    // === CONSTRUCTOR ===
    public MoveOrdering() {
        this.killerMoves = new Move[SearchConfig.MAX_KILLER_DEPTH][2]; // 2 killers per depth
        this.historyTable = new int[GameState.NUM_SQUARES][GameState.NUM_SQUARES];
    }

    // === MAIN INTERFACE ===

    /**
     * Fast move ordering using Evaluator's static methods
     */
    public void orderMoves(List<Move> moves, GameState state, int depth, TTEntry ttEntry) {
        if (moves == null || moves.size() <= 1) return;

        // Simple, fast sort
        moves.sort((a, b) -> Integer.compare(
                scoreMoveInternal(b, state, depth, ttEntry),
                scoreMoveInternal(a, state, depth, ttEntry)
        ));
    }

    /**
     * Internal move scoring - uses Evaluator's static methods
     */
    private int scoreMoveInternal(Move move, GameState state, int depth, TTEntry ttEntry) {
        // 1. TT move (highest priority)
        if (ttEntry != null && ttEntry.bestMove != null && move.equals(ttEntry.bestMove)) {
            return 1000000;
        }

        // 2. Winning moves (guard reaching castle)
        if (Evaluator.isWinningMove(move, state)) {
            return 500000;
        }

        // 3. Captures (MVV-LVA)
        if (Evaluator.isCapture(move, state)) {
            return 100000 + getMVVLVA(move, state);
        }

        // 4. Killer moves
        if (depth < SearchConfig.MAX_KILLER_DEPTH) {
            if (move.equals(killerMoves[depth][0])) return 10000;
            if (move.equals(killerMoves[depth][1])) return 9000;
        }

        // 5. History heuristic
        return historyTable[move.from][move.to] / 100;
    }

    /**
     * Simple MVV-LVA using Evaluator.getPieceValue()
     */
    private int getMVVLVA(Move move, GameState state) {
        int victim = Evaluator.getPieceValue(state, move.to);
        int attacker = Evaluator.getPieceValue(state, move.from);
        return victim * 100 - attacker;
    }

    // === UPDATE METHODS ===

    /**
     * Store killer move
     */
    public void storeKillerMove(Move move, int depth) {
        if (move == null || depth >= SearchConfig.MAX_KILLER_DEPTH) return;

        // Don't store captures as killers
        if (killerMoves[depth][0] == null || !move.equals(killerMoves[depth][0])) {
            killerMoves[depth][1] = killerMoves[depth][0];
            killerMoves[depth][0] = move;
        }
    }

    /**
     * Update history heuristic
     */
    public void updateHistory(Move move, int depth, GameState state) {
        if (move == null || Evaluator.isCapture(move, state)) return; // Only quiet moves

        if (move.from < historyTable.length && move.to < historyTable[move.from].length) {
            historyTable[move.from][move.to] += depth * depth;

            // Prevent overflow
            if (historyTable[move.from][move.to] > SearchConfig.HISTORY_MAX_VALUE) {
                ageHistoryTable();
            }
        }
    }

    /**
     * Age history table when it gets too full
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
        if (killerAge > 20) { // Reset every 20 searches
            killerMoves = new Move[SearchConfig.MAX_KILLER_DEPTH][2];
            killerAge = 0;
        }
    }

    // === LEGACY COMPATIBILITY METHODS ===

    public void clear() {
        killerMoves = new Move[SearchConfig.MAX_KILLER_DEPTH][2];
        historyTable = new int[GameState.NUM_SQUARES][GameState.NUM_SQUARES];
        killerAge = 0;
    }

    public void resetKillerMoves() {
        resetForNewSearch();
    }

    public void updateHistoryOnCutoff(Move move, GameState state, int depth) {
        updateHistory(move, depth, state);
    }

    // === PUBLIC INTERFACE FOR OTHER CLASSES ===

    /**
     * Public method for external scoring (different name to avoid conflicts)
     */
    public int evaluateMove(Move move, GameState state, int depth, TTEntry ttEntry) {
        return scoreMoveInternal(move, state, depth, ttEntry);
    }

    // === STATISTICS ===

    public String getStatistics() {
        int killerCount = 0;
        int historyEntries = 0;

        // Count killers
        for (int i = 0; i < SearchConfig.MAX_KILLER_DEPTH && i < killerMoves.length; i++) {
            if (killerMoves[i][0] != null) killerCount++;
            if (killerMoves[i][1] != null) killerCount++;
        }

        // Count history entries
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            for (int j = 0; j < GameState.NUM_SQUARES; j++) {
                if (historyTable[i][j] > 0) historyEntries++;
            }
        }

        return String.format("MoveOrdering[Clean]: %d killers, %d history entries, age=%d",
                killerCount, historyEntries, killerAge);
    }
}
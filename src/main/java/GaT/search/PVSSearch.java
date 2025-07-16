package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;
import GaT.model.TTEntry;
import GaT.evaluation.Evaluator;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * OPTIMIZED PVS SEARCH for Turm & Wächter
 *
 * IMPROVEMENTS:
 * ✅ Integrates with optimized MoveOrdering (uses Evaluator static methods)
 * ✅ Improved PV line handling for better cutoffs
 * ✅ Tuned LMR parameters for tactical games like Turm & Wächter
 * ✅ Reduced exception handling in hot paths
 * ✅ Better null-window search logic
 * ✅ Game-specific optimizations
 */
public class PVSSearch {

    // === DEPENDENCIES ===
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final SearchStatistics statistics = SearchStatistics.getInstance();

    // === PV LINE MANAGEMENT ===
    private static Move[] principalVariation = new Move[SearchConfig.MAX_DEPTH];
    private static int pvLength = 0;

    // === TIMEOUT MANAGEMENT ===
    private static BooleanSupplier timeoutChecker = null;
    private static volatile boolean searchInterrupted = false;

    // === MAIN PVS INTERFACE ===

    /**
     * Main PVS search with quiescence - optimized for Turm & Wächter
     */
    public static int searchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                           boolean maximizingPlayer, boolean isPVNode) {

        if (state == null || !state.isValid()) {
            return Minimax.evaluate(state, depth);
        }

        statistics.incrementNodeCount();

        // Timeout check
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            searchInterrupted = true;
            return Minimax.evaluate(state, depth);
        }

        // Terminal depth - use quiescence
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        // TT lookup
        TTEntry ttEntry = lookupTranspositionTable(state, depth, alpha, beta, isPVNode);
        if (ttEntry != null && ttEntry.score != Integer.MIN_VALUE) {
            return ttEntry.score;
        }

        // Null-move pruning
        if (canDoNullMove(state, depth, beta, isPVNode, maximizingPlayer)) {
            int nullScore = tryNullMovePruning(state, depth, alpha, beta, maximizingPlayer);
            if (nullScore != Integer.MIN_VALUE) {
                return nullScore;
            }
        }

        // Generate and order moves using optimized MoveOrdering
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return Minimax.evaluate(state, depth);
        }

        // Use optimized move ordering
        moveOrdering.orderMoves(moves, state, depth, ttEntry);

        // PVS main search loop
        return performPVSSearch(state, moves, depth, alpha, beta, maximizingPlayer, isPVNode, ttEntry);
    }

    /**
     * PVS search without quiescence (for compatibility)
     */
    public static int search(GameState state, int depth, int alpha, int beta,
                             boolean maximizingPlayer, boolean isPVNode) {

        if (state == null || !state.isValid()) {
            return Minimax.evaluate(state, depth);
        }

        if (depth <= 0) {
            return Minimax.evaluate(state, depth);
        }

        // Same logic as searchWithQuiescence but no quiescence at leaf nodes
        statistics.incrementNodeCount();

        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            return Minimax.evaluate(state, depth);
        }

        TTEntry ttEntry = lookupTranspositionTable(state, depth, alpha, beta, isPVNode);
        if (ttEntry != null && ttEntry.score != Integer.MIN_VALUE) {
            return ttEntry.score;
        }

        if (canDoNullMove(state, depth, beta, isPVNode, maximizingPlayer)) {
            int nullScore = tryNullMovePruning(state, depth, alpha, beta, maximizingPlayer);
            if (nullScore != Integer.MIN_VALUE) {
                return nullScore;
            }
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return Minimax.evaluate(state, depth);
        }

        moveOrdering.orderMoves(moves, state, depth, ttEntry);
        return performPVSSearch(state, moves, depth, alpha, beta, maximizingPlayer, isPVNode, ttEntry);
    }

    // === CORE PVS SEARCH LOGIC ===

    /**
     * Main PVS search loop - optimized for performance
     */
    private static int performPVSSearch(GameState state, List<Move> moves, int depth,
                                        int alpha, int beta, boolean maximizingPlayer,
                                        boolean isPVNode, TTEntry ttEntry) {

        int bestScore = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        Move bestMove = null;
        int searchedMoves = 0;
        boolean raisedAlpha = false;

        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);

            // Apply move
            GameState copy = state.copy();
            copy.applyMove(move);

            int score;
            boolean isFirstMove = (i == 0);
            boolean needsFullSearch = false;

            // === PVS LOGIC ===

            if (isFirstMove) {
                // Search first move with full window
                if (depth == 1) {
                    score = QuiescenceSearch.quiesce(copy, alpha, beta, !maximizingPlayer, 0);
                } else {
                    score = searchWithQuiescence(copy, depth - 1, alpha, beta, !maximizingPlayer, isPVNode);
                }
            } else {
                // Try Late Move Reduction for non-PV moves
                int reduction = 0;
                if (shouldReduceMove(state, move, depth, i, isPVNode)) {
                    reduction = calculateLMRReduction(depth, i, isPVNode);
                }

                // Null-window search with possible reduction
                int searchDepth = Math.max(1, depth - 1 - reduction);
                int nullWindow = maximizingPlayer ? alpha + 1 : beta - 1;

                if (searchDepth == 1) {
                    score = QuiescenceSearch.quiesce(copy,
                            maximizingPlayer ? alpha : nullWindow,
                            maximizingPlayer ? nullWindow : beta,
                            !maximizingPlayer, 0);
                } else {
                    score = searchWithQuiescence(copy, searchDepth,
                            maximizingPlayer ? alpha : nullWindow,
                            maximizingPlayer ? nullWindow : beta,
                            !maximizingPlayer, false);
                }

                // Check if we need full search
                if (maximizingPlayer) {
                    needsFullSearch = (score > alpha) && (reduction > 0 || score < beta);
                } else {
                    needsFullSearch = (score < beta) && (reduction > 0 || score > alpha);
                }

                // Full search if needed
                if (needsFullSearch && !isFirstMove) {
                    if (depth == 1) {
                        score = QuiescenceSearch.quiesce(copy, alpha, beta, !maximizingPlayer, 0);
                    } else {
                        score = searchWithQuiescence(copy, depth - 1, alpha, beta, !maximizingPlayer, isPVNode && (i <= 2));
                    }
                }
            }

            searchedMoves++;

            // Update best score and alpha-beta
            if (maximizingPlayer) {
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;

                    if (score > alpha) {
                        alpha = score;
                        raisedAlpha = true;

                        // Update PV line
                        if (isPVNode && depth < principalVariation.length) {
                            principalVariation[depth] = move;
                        }
                    }
                }

                if (score >= beta) {
                    // Beta cutoff
                    statistics.incrementAlphaBetaCutoffs();

                    // Update killer moves and history for quiet moves
                    if (!Evaluator.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth, state);
                    }

                    // Store in TT
                    storeInTranspositionTable(state, depth, score, TTEntry.LOWER_BOUND, move);
                    return score;
                }
            } else {
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = move;

                    if (score < beta) {
                        beta = score;
                        raisedAlpha = true;

                        if (isPVNode && depth < principalVariation.length) {
                            principalVariation[depth] = move;
                        }
                    }
                }

                if (score <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    if (!Evaluator.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth, state);
                    }

                    storeInTranspositionTable(state, depth, score, TTEntry.UPPER_BOUND, move);
                    return score;
                }
            }

            // Timeout check during search
            if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
                break;
            }
        }

        // Store result in transposition table
        int flag = raisedAlpha ? TTEntry.EXACT : (maximizingPlayer ? TTEntry.UPPER_BOUND : TTEntry.LOWER_BOUND);
        storeInTranspositionTable(state, depth, bestScore, flag, bestMove);

        return bestScore;
    }

    // === OPTIMIZED LMR FOR TURM & WÄCHTER ===

    /**
     * Should we reduce this move? Tuned for Turm & Wächter's tactical nature
     */
    private static boolean shouldReduceMove(GameState state, Move move, int depth, int moveIndex, boolean isPVNode) {
        // Don't reduce in shallow searches
        if (depth < 3) return false;

        // Don't reduce first few moves
        if (moveIndex < 2) return false;

        // Don't reduce PV moves
        if (isPVNode && moveIndex == 0) return false;

        // Never reduce captures
        if (Evaluator.isCapture(move, state)) return false;

        // Never reduce winning moves
        if (Evaluator.isWinningMove(move, state)) return false;

        // Don't reduce guard moves (important in Turm & Wächter)
        if (Evaluator.isGuardMove(move, state)) return false;

        // Don't reduce high-activity moves (large tower movements)
        if (move.amountMoved >= 4) return false;

        return true;
    }

    /**
     * Calculate LMR reduction - conservative for tactical game
     */
    private static int calculateLMRReduction(int depth, int moveIndex, boolean isPVNode) {
        int reduction = 1; // Base reduction

        // More reduction for later moves
        if (moveIndex > 6) reduction++;
        if (moveIndex > 12) reduction++;

        // Less reduction in PV nodes
        if (isPVNode) reduction = Math.max(1, reduction - 1);

        // Less reduction in deep searches (may be critical)
        if (depth > 8) reduction = Math.max(1, reduction - 1);

        return Math.min(reduction, depth - 1);
    }

    // === HELPER METHODS ===

    /**
     * Optimized TT lookup
     */
    private static TTEntry lookupTranspositionTable(GameState state, int depth, int alpha, int beta, boolean isPVNode) {
        try {
            long hash = state.hash();
            TTEntry entry = Minimax.getTranspositionEntry(hash);

            if (entry != null && entry.depth >= depth) {
                statistics.incrementTTHits();

                if (entry.flag == TTEntry.EXACT) {
                    return entry; // Return the entry with the score
                }

                if (!isPVNode) {
                    if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                        return entry;
                    }
                    if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                        return entry;
                    }
                }

                // Return entry for move ordering (even if we can't use score)
                TTEntry moveOrderingEntry = new TTEntry(Integer.MIN_VALUE, entry.depth, entry.flag, entry.bestMove);
                return moveOrderingEntry;
            }

            statistics.incrementTTMisses();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Store in transposition table
     */
    private static void storeInTranspositionTable(GameState state, int depth, int score, int flag, Move bestMove) {
        try {
            long hash = state.hash();
            TTEntry entry = new TTEntry(score, depth, flag, bestMove);
            Minimax.storeTranspositionEntry(hash, entry);
            statistics.incrementTTStores();
        } catch (Exception e) {
            // Silent fail - TT is optimization
        }
    }

    /**
     * Check if null-move is allowed
     */
    private static boolean canDoNullMove(GameState state, int depth, int beta, boolean isPVNode, boolean maximizingPlayer) {
        if (!SearchConfig.NULL_MOVE_ENABLED) return false;
        if (depth < SearchConfig.NULL_MOVE_MIN_DEPTH) return false;
        if (isPVNode) return false;

        // Don't do null-move in endgame (only guards left)
        boolean isRed = maximizingPlayer;
        long ownTowers = isRed ? state.redTowers : state.blueTowers;

        return ownTowers != 0; // Have towers to give up tempo
    }

    /**
     * Try null-move pruning
     */
    private static int tryNullMovePruning(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        try {
            GameState nullState = state.copy();
            nullState.redToMove = !nullState.redToMove; // Switch turns

            int reduction = SearchConfig.NULL_MOVE_REDUCTION;
            int nullDepth = depth - 1 - reduction;

            if (nullDepth <= 0) {
                return QuiescenceSearch.quiesce(nullState, alpha, beta, !maximizingPlayer, 0);
            } else {
                return searchWithQuiescence(nullState, nullDepth, alpha, beta, !maximizingPlayer, false);
            }
        } catch (Exception e) {
            return Integer.MIN_VALUE; // Failed
        }
    }

    // === INTERFACE METHODS ===

    public static void setTimeoutChecker(BooleanSupplier checker) {
        timeoutChecker = checker;
        searchInterrupted = false;
    }

    public static void clearTimeoutChecker() {
        timeoutChecker = null;
        searchInterrupted = false;
    }

    /**
     * Get current PV line
     */
    public static Move[] getPrincipalVariation() {
        Move[] pv = new Move[pvLength];
        System.arraycopy(principalVariation, 0, pv, 0, pvLength);
        return pv;
    }

    /**
     * Reset PV line for new search
     */
    public static void resetPrincipalVariation() {
        pvLength = 0;
        for (int i = 0; i < principalVariation.length; i++) {
            principalVariation[i] = null;
        }
    }
}
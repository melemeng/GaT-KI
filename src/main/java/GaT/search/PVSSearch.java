package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.search.MoveGenerator;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * ENHANCED PRINCIPAL VARIATION SEARCH - WITH HISTORY HEURISTIC
 *
 * ENHANCEMENTS:
 * ✅ History Heuristic integration for better move ordering
 * ✅ Graceful timeout handling
 * ✅ Enhanced statistics tracking
 * ✅ Proper cutoff handling with history updates
 * ✅ Optimized for single-threaded performance
 */
public class PVSSearch {

    // === DEPENDENCIES - ENHANCED ===
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final SearchStatistics statistics = SearchStatistics.getInstance();

    // === TIMEOUT MANAGEMENT ===
    private static BooleanSupplier timeoutChecker = null;
    private static volatile boolean searchInterrupted = false;

    // === MAIN PVS INTERFACE ===

    /**
     * Standard PVS without Quiescence - ENHANCED WITH HISTORY
     */
    public static int search(GameState state, int depth, int alpha, int beta,
                             boolean maximizingPlayer, boolean isPVNode) {

        statistics.incrementNodeCount();

        // Graceful timeout handling
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            searchInterrupted = true;
            return Minimax.evaluate(state, depth);
        }

        if (searchInterrupted) {
            return Minimax.evaluate(state, depth);
        }

        // TT-Lookup with caution for PV nodes
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);
        if (entry != null && entry.depth >= depth) {
            statistics.incrementTTHits();

            if (entry.flag == TTEntry.EXACT && (!isPVNode || depth <= 0)) {
                return entry.score;
            } else if (!isPVNode) {
                if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                    return entry.score;
                } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                    return entry.score;
                }
            }
        } else {
            statistics.incrementTTMisses();
        }

        // Terminal conditions
        if (depth == 0 || Minimax.isGameOver(state)) {
            statistics.incrementLeafNodeCount();
            return Minimax.evaluate(state, depth);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        statistics.addMovesGenerated(moves.size());

        // Enhanced move ordering for PV vs Non-PV nodes
        if (isPVNode) {
            orderMovesForPV(moves, state, depth, entry);
        } else {
            moveOrdering.orderMoves(moves, state, depth, entry);
        }

        Move bestMove = null;
        int originalAlpha = alpha;
        boolean isFirstMove = true;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (int i = 0; i < moves.size(); i++) {
                if (i % 3 == 0 && searchInterrupted) {
                    break;
                }

                Move move = moves.get(i);
                GameState copy = state.copy();
                copy.applyMove(move);
                statistics.addMovesSearched(1);

                int eval;

                if (isFirstMove || isPVNode) {
                    eval = search(copy, depth - 1, alpha, beta, false, isPVNode);
                    isFirstMove = false;
                } else {
                    int nullWindow = isPVNode ? alpha + 10 : alpha + 1;
                    eval = search(copy, depth - 1, alpha, nullWindow, false, false);

                    if (eval > alpha && eval < beta) {
                        eval = search(copy, depth - 1, eval, beta, false, true);
                    }
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    // ENHANCED HISTORY UPDATE
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistoryOnCutoff(move, state, depth); // ← CORRECTED SIGNATURE
                    }
                    break;
                }
            }

            storeTTEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;

            for (int i = 0; i < moves.size(); i++) {
                if (i % 3 == 0 && searchInterrupted) {
                    break;
                }

                Move move = moves.get(i);
                GameState copy = state.copy();
                copy.applyMove(move);
                statistics.addMovesSearched(1);

                int eval;

                if (isFirstMove || isPVNode) {
                    eval = search(copy, depth - 1, alpha, beta, true, isPVNode);
                    isFirstMove = false;
                } else {
                    int nullWindow = isPVNode ? beta - 10 : beta - 1;
                    eval = search(copy, depth - 1, nullWindow, beta, true, false);

                    if (eval < beta && eval > alpha) {
                        eval = search(copy, depth - 1, alpha, eval, true, true);
                    }
                }

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    // ENHANCED HISTORY UPDATE
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistoryOnCutoff(move, state, depth); // ← CORRECTED SIGNATURE
                    }
                    break;
                }
            }

            storeTTEntry(hash, minEval, depth, originalAlpha, beta, bestMove);
            return minEval;
        }
    }

    /**
     * PVS with Quiescence Search Integration - ENHANCED WITH HISTORY
     */
    public static int searchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                           boolean maximizingPlayer, boolean isPVNode) {

        statistics.incrementNodeCount();

        // Graceful timeout handling
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            searchInterrupted = true;
            return Minimax.evaluate(state, depth);
        }

        if (searchInterrupted) {
            return Minimax.evaluate(state, depth);
        }

        // TT-Lookup with PV node caution
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);
        if (entry != null && entry.depth >= depth) {
            statistics.incrementTTHits();

            if (entry.flag == TTEntry.EXACT && (!isPVNode || depth <= 0)) {
                return entry.score;
            } else if (!isPVNode) {
                if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                    return entry.score;
                } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                    return entry.score;
                }
            }
        } else {
            statistics.incrementTTMisses();
        }

        // Terminal conditions
        if (Minimax.isGameOver(state)) {
            statistics.incrementLeafNodeCount();
            return Minimax.evaluate(state, depth);
        }

        // Quiescence Search when depth exhausted
        if (depth <= 0) {
            statistics.incrementQNodeCount();
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        statistics.addMovesGenerated(moves.size());

        // Enhanced move ordering for PV vs Non-PV nodes
        if (isPVNode) {
            orderMovesForPV(moves, state, depth, entry);
        } else {
            moveOrdering.orderMoves(moves, state, depth, entry);
        }

        Move bestMove = null;
        int originalAlpha = alpha;
        boolean isFirstMove = true;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (int i = 0; i < moves.size(); i++) {
                if (i % 3 == 0 && searchInterrupted) {
                    break;
                }

                Move move = moves.get(i);
                GameState copy = state.copy();
                copy.applyMove(move);
                statistics.addMovesSearched(1);

                int eval;

                if (isFirstMove || isPVNode) {
                    eval = searchWithQuiescence(copy, depth - 1, alpha, beta, false, isPVNode);
                    isFirstMove = false;
                } else {
                    int nullWindow = isPVNode ? alpha + 10 : alpha + 1;
                    eval = searchWithQuiescence(copy, depth - 1, alpha, nullWindow, false, false);

                    if (eval > alpha && eval < beta) {
                        eval = searchWithQuiescence(copy, depth - 1, eval, beta, false, true);
                    }
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    // ENHANCED HISTORY UPDATE
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistoryOnCutoff(move, state, depth); // ← CORRECTED SIGNATURE
                    }
                    break;
                }
            }

            storeTTEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;

        } else {
            // === MINIMIZING PLAYER ===
            int minEval = Integer.MAX_VALUE;

            for (int i = 0; i < moves.size(); i++) {
                if (i % 3 == 0 && searchInterrupted) {
                    break;
                }

                Move move = moves.get(i);
                GameState copy = state.copy();
                copy.applyMove(move);
                statistics.addMovesSearched(1);

                int eval;

                if (isFirstMove || isPVNode) {
                    eval = searchWithQuiescence(copy, depth - 1, alpha, beta, true, isPVNode);
                    isFirstMove = false;
                } else {
                    int nullWindow = isPVNode ? beta - 10 : beta - 1;
                    eval = searchWithQuiescence(copy, depth - 1, nullWindow, beta, true, false);

                    if (eval < beta && eval > alpha) {
                        eval = searchWithQuiescence(copy, depth - 1, alpha, eval, true, true);
                    }
                }

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    // ENHANCED HISTORY UPDATE
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistoryOnCutoff(move, state, depth); // ← CORRECTED SIGNATURE
                    }
                    break;
                }
            }

            storeTTEntry(hash, minEval, depth, originalAlpha, beta, bestMove);
            return minEval;
        }
    }

    // === ENHANCED MOVE ORDERING FOR PV NODES ===

    /**
     * Special move ordering for PV nodes - ENHANCED
     */
    private static void orderMovesForPV(List<Move> moves, GameState state, int depth, TTEntry entry) {
        // TT Move has highest priority
        if (entry != null && entry.bestMove != null) {
            for (int i = 0; i < moves.size(); i++) {
                if (moves.get(i).equals(entry.bestMove)) {
                    Move ttMove = moves.remove(i);
                    moves.add(0, ttMove);
                    break;
                }
            }
        }

        // Enhanced move ordering for PV nodes
        if (moves.size() > 1) {
            int startIndex = (entry != null && entry.bestMove != null) ? 1 : 0;
            List<Move> restMoves = moves.subList(startIndex, moves.size());

            restMoves.sort((a, b) -> {
                int scoreA = scoreMoveForPVEnhanced(state, a, depth);
                int scoreB = scoreMoveForPVEnhanced(state, b, depth);
                return Integer.compare(scoreB, scoreA);
            });
        }
    }

    /**
     * Enhanced move scoring for PV nodes with history integration
     */
    private static int scoreMoveForPVEnhanced(GameState state, Move move, int depth) {
        int score = Minimax.scoreMove(state, move);

        // Enhanced history scoring for PV nodes
        if (!Minimax.isCapture(move, state)) {
            // Get history score from both systems
            if (moveOrdering.getHistoryHeuristic().isQuietMove(move, state)) {
                boolean isRedMove = state.redToMove;
                score += moveOrdering.getHistoryHeuristic().getScore(move, isRedMove);
            }

            // PV-specific bonuses
            score += move.to * 2;
            score += move.amountMoved * 5;

            // Central control bonus
            int targetFile = GameState.file(move.to);
            int targetRank = GameState.rank(move.to);
            int centrality = Math.abs(targetFile - 3) + Math.abs(targetRank - 3);
            score += (6 - centrality) * 3;

            // Anti-repetition bonus
            long hash = state.hash();
            score += (int)(hash % 20) - 10;
        }

        return score;
    }

    // === HELPER METHODS ===

    /**
     * Store entry in transposition table
     */
    private static void storeTTEntry(long hash, int score, int depth, int originalAlpha, int beta, Move bestMove) {
        int flag;
        if (score <= originalAlpha) {
            flag = TTEntry.UPPER_BOUND;
        } else if (score >= beta) {
            flag = TTEntry.LOWER_BOUND;
        } else {
            flag = TTEntry.EXACT;
        }

        TTEntry entry = new TTEntry(score, depth, flag, bestMove);
        Minimax.storeTranspositionEntry(hash, entry);
        statistics.incrementTTStores();
    }

    // === TIMEOUT MANAGEMENT ===

    /**
     * Set timeout checker from TimedMinimax
     */
    public static void setTimeoutChecker(BooleanSupplier checker) {
        timeoutChecker = checker;
    }

    /**
     * Clear timeout checker (for cleanup)
     */
    public static void clearTimeoutChecker() {
        timeoutChecker = null;
    }

    /**
     * Reset search state for clean start
     */
    public static void resetSearchState() {
        searchInterrupted = false;
    }

    // === LEGACY COMPATIBILITY ===

    /**
     * Legacy method for backward compatibility
     */
    public static void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry entry) {
        orderMovesForPV(moves, state, depth, entry);
    }
}
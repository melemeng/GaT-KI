package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * PRINCIPAL VARIATION SEARCH - Refactored for new architecture
 *
 * Responsibilities:
 * - PVS algorithm implementation
 * - PVS with Quiescence integration
 * - Null window search optimization
 * - Enhanced move ordering for PV nodes
 */
public class PVSSearch {

    // === DEPENDENCIES ===
    private static final MoveOrdering moveOrdering = new MoveOrdering();

    // === TIMEOUT MANAGEMENT ===
    private static BooleanSupplier timeoutChecker = null;

    // === MAIN PVS INTERFACE ===

    /**
     * Standard PVS without Quiescence
     */
    public static int search(GameState state, int depth, int alpha, int beta,
                             boolean maximizingPlayer, boolean isPVNode) {

        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Timeout");
        }

        // TT-Lookup with caution for PV nodes
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);
        if (entry != null && entry.depth >= depth) {
            // In PV nodes, only use EXACT scores or be more careful
            if (entry.flag == TTEntry.EXACT && (!isPVNode || depth <= 0)) {
                return entry.score;
            } else if (!isPVNode) { // Only normal TT cutoffs in non-PV nodes
                if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                    return entry.score;
                } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                    return entry.score;
                }
            }
        }

        // Terminal conditions
        if (depth == 0 || Minimax.isGameOver(state)) {
            return Minimax.evaluate(state, depth);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Enhanced move ordering for PV vs Non-PV nodes
        if (isPVNode) {
            orderMovesForPV(moves, state, depth, entry);
        } else {
            moveOrdering.orderMovesEnhanced(moves, state, depth, entry, null,
                    getRemainingTimeEstimate());
        }

        Move bestMove = null;
        int originalAlpha = alpha;
        boolean isFirstMove = true;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                Minimax.counter++;

                int eval;

                if (isFirstMove || isPVNode) {
                    // First moves AND PV nodes get full search
                    eval = search(copy, depth - 1, alpha, beta, false, isPVNode);
                    isFirstMove = false;
                } else {
                    // Less aggressive null window for better differentiation
                    int nullWindow = isPVNode ? alpha + 10 : alpha + 1;
                    eval = search(copy, depth - 1, alpha, nullWindow, false, false);

                    if (eval > alpha && eval < beta) {
                        // Re-search with full window
                        eval = search(copy, depth - 1, eval, beta, false, true);
                    }
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistoryOnCutoff(move, state.redToMove, depth);
                    }
                    break;
                }
            }

            storeTTEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                Minimax.counter++;

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
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistoryOnCutoff(move, state.redToMove, depth);
                    }
                    break;
                }
            }

            storeTTEntry(hash, minEval, depth, originalAlpha, beta, bestMove);
            return minEval;
        }
    }

    /**
     * PVS with Quiescence Search Integration
     */
    public static int searchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                           boolean maximizingPlayer, boolean isPVNode) {

        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Timeout");
        }

        // TT-Lookup with PV node caution
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);
        if (entry != null && entry.depth >= depth) {
            if (entry.flag == TTEntry.EXACT && (!isPVNode || depth <= 0)) {
                return entry.score;
            } else if (!isPVNode) {
                if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                    return entry.score;
                } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                    return entry.score;
                }
            }
        }

        // Terminal conditions
        if (Minimax.isGameOver(state)) {
            return Minimax.evaluate(state, depth);
        }

        // Quiescence Search when depth exhausted
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Enhanced move ordering for PV vs Non-PV nodes
        if (isPVNode) {
            orderMovesForPV(moves, state, depth, entry);
        } else {
            moveOrdering.orderMovesEnhanced(moves, state, depth, entry, null,
                    getRemainingTimeEstimate());
        }

        Move bestMove = null;
        int originalAlpha = alpha;
        boolean isFirstMove = true;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                Minimax.counter++;

                int eval;

                if (isFirstMove || isPVNode) {
                    // First moves AND PV nodes get full search
                    eval = searchWithQuiescence(copy, depth - 1, alpha, beta, false, isPVNode);
                    isFirstMove = false;
                } else {
                    // Null window search
                    int nullWindow = isPVNode ? alpha + 10 : alpha + 1;
                    eval = searchWithQuiescence(copy, depth - 1, alpha, nullWindow, false, false);

                    if (eval > alpha && eval < beta) {
                        // Re-search with full window
                        eval = searchWithQuiescence(copy, depth - 1, eval, beta, false, true);
                    }
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistoryOnCutoff(move, state.redToMove, depth);
                    }
                    break;
                }
            }

            storeTTEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                Minimax.counter++;

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
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistoryOnCutoff(move, state.redToMove, depth);
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
     * Special move ordering for PV nodes
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

        // In PV nodes, evaluate more moves with high priority
        if (moves.size() > 1) {
            int startIndex = (entry != null && entry.bestMove != null) ? 1 : 0;
            List<Move> restMoves = moves.subList(startIndex, moves.size());

            restMoves.sort((a, b) -> {
                int scoreA = scoreMoveForPV(state, a, depth);
                int scoreB = scoreMoveForPV(state, b, depth);
                return Integer.compare(scoreB, scoreA);
            });
        }
    }

    /**
     * Enhanced move scoring for PV nodes
     */
    private static int scoreMoveForPV(GameState state, Move move, int depth) {
        // Start with basic move score
        MoveOrdering.ThreatAnalysis threats = new MoveOrdering.ThreatAnalysis();
        int score = moveOrdering.scoreMoveEnhanced(move, state, depth, null, null, threats);

        // In PV nodes, also score quiet moves higher for diversity
        if (!Minimax.isCapture(move, state)) {
            // Bonus for different target squares (anti-repetition)
            score += move.to * 2;

            // Bonus for different amounts moved
            score += move.amountMoved * 5;

            // Bonus for central moves
            int targetFile = GameState.file(move.to);
            int targetRank = GameState.rank(move.to);
            int centrality = Math.abs(targetFile - 3) + Math.abs(targetRank - 3);
            score += (6 - centrality) * 3;

            // Anti-repetition: bonus for moves to new squares
            long hash = state.hash();
            score += (int)(hash % 20) - 10; // Pseudo-random variation based on position
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
    }

    /**
     * Estimate remaining time for move ordering
     */
    private static long getRemainingTimeEstimate() {
        // Default to reasonable time if no timeout checker available
        return timeoutChecker != null ? 30000 : 180000; // 30s default or 3min fallback
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

    // === LEGACY COMPATIBILITY ===

    /**
     * Legacy method for backward compatibility
     */
    public static void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry entry) {
        orderMovesForPV(moves, state, depth, entry);
    }
}
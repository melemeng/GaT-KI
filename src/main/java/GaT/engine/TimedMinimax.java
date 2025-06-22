package GaT.engine;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.search.Minimax;
import GaT.search.MoveGenerator;
import GaT.search.MoveOrdering;
import GaT.search.QuiescenceSearch;
import GaT.search.PVSSearch;

import java.util.List;

/**
 * TIMED MINIMAX ENGINE - Enhanced with new architecture
 *
 * Responsibilities:
 * - Iterative deepening with time control
 * - Integration with refactored components
 * - Multiple search strategies
 * - Tournament-ready time management
 */
public class TimedMinimax {

    // === TIME MANAGEMENT ===
    private static long timeLimitMillis;
    private static long startTime;

    // === DEPENDENCIES ===
    private static final MoveOrdering moveOrdering = new MoveOrdering();

    // === TIMEOUT EXCEPTION ===
    private static class TimeoutException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    // === MAIN PUBLIC INTERFACES ===

    /**
     * Find best move with time limit - LEGACY COMPATIBLE
     */
    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveIterative(state, maxDepth, timeMillis, Minimax.SearchStrategy.ALPHA_BETA);
    }

    /**
     * Find best move with quiescence search and time control
     */
    public static Move findBestMoveWithTimeAndQuiescence(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveIterative(state, maxDepth, timeMillis, Minimax.SearchStrategy.ALPHA_BETA_Q);
    }

    /**
     * ULTIMATE AI - PVS + Quiescence + Iterative Deepening
     */
    public static Move findBestMoveUltimate(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveIterative(state, maxDepth, timeMillis, Minimax.SearchStrategy.PVS_Q);
    }

    /**
     * Find best move with specific strategy and time control
     */
    public static Move findBestMoveWithStrategy(GameState state, int maxDepth, long timeMillis,
                                                Minimax.SearchStrategy strategy) {
        return findBestMoveIterative(state, maxDepth, timeMillis, strategy);
    }

    // === CORE ITERATIVE DEEPENING ===

    /**
     * ENHANCED Iterative Deepening with comprehensive time management
     */
    private static Move findBestMoveIterative(GameState state, int maxDepth, long timeMillis,
                                              Minimax.SearchStrategy strategy) {
        // Setup time management
        TimedMinimax.timeLimitMillis = timeMillis;
        TimedMinimax.startTime = System.currentTimeMillis();

        // Initialize components
        moveOrdering.resetKillerMoves();
        Minimax.resetPruningStats();

        if (strategy == Minimax.SearchStrategy.ALPHA_BETA_Q || strategy == Minimax.SearchStrategy.PVS_Q) {
            QuiescenceSearch.setRemainingTime(timeMillis);
            QuiescenceSearch.resetQuiescenceStats();
        }

        if (strategy == Minimax.SearchStrategy.PVS || strategy == Minimax.SearchStrategy.PVS_Q) {
            PVSSearch.setTimeoutChecker(() -> timedOut());
        }

        Move bestMove = null;
        Move lastCompleteMove = null;
        int bestDepth = 0;

        System.out.println("=== Iterative Deepening with " + strategy + " ===");
        System.out.println("Time limit: " + timeMillis + "ms, Max depth: " + maxDepth);

        // Iterative deepening loop
        for (int depth = 1; depth <= maxDepth; depth++) {
            if (timedOut()) {
                System.out.println("⏱ Time limit reached before depth " + depth);
                break;
            }

            long depthStartTime = System.currentTimeMillis();

            try {
                Move candidate = searchAtDepth(state, depth, strategy);

                if (candidate != null) {
                    lastCompleteMove = candidate;
                    bestMove = candidate;
                    bestDepth = depth;

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    logDepthCompletion(depth, candidate, depthTime);

                    // Early termination for winning moves
                    if (isWinningMove(candidate, state)) {
                        System.out.println("🎯 Winning move found at depth " + depth);
                        break;
                    }
                }

            } catch (TimeoutException e) {
                System.out.println("⏱ Timeout at depth " + depth);
                break;
            } catch (RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().contains("Timeout")) {
                    System.out.println("⏱ Search timeout at depth " + depth);
                    break;
                } else {
                    System.err.println("❌ Search error at depth " + depth + ": " + e.getMessage());
                    break;
                }
            }

            // Adaptive time management
            if (!shouldContinueSearch(timeMillis)) {
                System.out.println("⚡ Stopping early due to time constraints");
                break;
            }
        }

        // Final statistics
        printFinalStatistics(strategy, bestDepth, bestMove);

        return bestMove != null ? bestMove : lastCompleteMove;
    }

    // === DEPTH-SPECIFIC SEARCH ===

    /**
     * Search at specific depth with given strategy
     */
    private static Move searchAtDepth(GameState state, int depth, Minimax.SearchStrategy strategy)
            throws TimeoutException {

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) return null;

        // Enhanced move ordering
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);
        moveOrdering.orderMovesEnhanced(moves, state, depth, entry, null,
                timeLimitMillis - (System.currentTimeMillis() - startTime));

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        // Search all moves
        for (Move move : moves) {
            if (timedOut()) throw new TimeoutException();

            GameState copy = state.copy();
            copy.applyMove(move);

            try {
                int score = searchWithTimeoutSupport(copy, depth - 1, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, !isRed, strategy);

                if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                    bestScore = score;
                    bestMove = move;
                }

            } catch (RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().equals("Timeout")) {
                    throw new TimeoutException();
                }
                throw e;
            }
        }

        return bestMove;
    }

    // === TIMEOUT-AWARE SEARCH DISPATCH ===

    /**
     * Search with timeout support for all strategies
     */
    private static int searchWithTimeoutSupport(GameState state, int depth, int alpha, int beta,
                                                boolean maximizingPlayer, Minimax.SearchStrategy strategy) {
        // Regular timeout check
        if (timedOut()) {
            throw new RuntimeException("Timeout");
        }

        // Dispatch to appropriate search method
        switch (strategy) {
            case ALPHA_BETA:
                return Minimax.minimaxWithTimeout(state, depth, alpha, beta, maximizingPlayer,
                        () -> timedOut());
            case ALPHA_BETA_Q:
                return searchWithQuiescenceAndTimeout(state, depth, alpha, beta, maximizingPlayer);
            case PVS:
                return PVSSearch.search(state, depth, alpha, beta, maximizingPlayer, true);
            case PVS_Q:
                return PVSSearch.searchWithQuiescence(state, depth, alpha, beta, maximizingPlayer, true);
            default:
                throw new IllegalArgumentException("Unknown search strategy: " + strategy);
        }
    }

    /**
     * Alpha-Beta with Quiescence and timeout support
     */
    private static int searchWithQuiescenceAndTimeout(GameState state, int depth, int alpha, int beta,
                                                      boolean maximizingPlayer) {
        if (timedOut()) {
            throw new RuntimeException("Timeout");
        }

        // Check transposition table first
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);
        if (entry != null && entry.depth >= depth) {
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        }

        // Terminal conditions
        if (Minimax.isGameOver(state)) {
            return Minimax.evaluate(state, depth);
        }

        // Use quiescence search when depth <= 0
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        // Regular alpha-beta search
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        moveOrdering.orderMovesEnhanced(moves, state, depth, entry, null,
                timeLimitMillis - (System.currentTimeMillis() - startTime));

        Move bestMove = null;
        int originalAlpha = alpha;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                if (timedOut()) throw new RuntimeException("Timeout");

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = searchWithQuiescenceAndTimeout(copy, depth - 1, alpha, beta, false);

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break;
                }
            }

            // Store in transposition table
            int flag = maxEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    maxEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            storeInTranspositionTable(hash, maxEval, depth, flag, bestMove);

            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                if (timedOut()) throw new RuntimeException("Timeout");

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = searchWithQuiescenceAndTimeout(copy, depth - 1, alpha, beta, true);

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break;
                }
            }

            int flag = minEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    minEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            storeInTranspositionTable(hash, minEval, depth, flag, bestMove);

            return minEval;
        }
    }

    // === TIME MANAGEMENT HELPERS ===

    /**
     * Check if we should continue searching
     */
    private static boolean shouldContinueSearch(long timeMillis) {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = timeMillis - elapsed;

        // Stop if less than 20% time remaining
        return remaining > timeMillis * 0.2;
    }

    /**
     * Check if time limit has been reached
     */
    private static boolean timedOut() {
        return System.currentTimeMillis() - startTime >= timeLimitMillis;
    }

    // === UTILITY METHODS ===

    /**
     * Check if move is winning
     */
    private static boolean isWinningMove(Move move, GameState state) {
        GameState afterMove = state.copy();
        afterMove.applyMove(move);
        return Minimax.isGameOver(afterMove);
    }

    /**
     * Store entry in transposition table
     */
    private static void storeInTranspositionTable(long hash, int score, int depth, int flag, Move bestMove) {
        TTEntry entry = new TTEntry(score, depth, flag, bestMove);
        Minimax.storeTranspositionEntry(hash, entry);
    }

    // === LOGGING METHODS ===

    /**
     * Log completion of a depth level
     */
    private static void logDepthCompletion(int depth, Move bestMove, long timeUsed) {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = timeLimitMillis - elapsed;

        System.out.printf("✓ Depth %d: %s (%dms) | Elapsed: %dms | Remaining: %dms%n",
                depth, bestMove, timeUsed, elapsed, remaining);
    }

    /**
     * Print final search statistics
     */
    private static void printFinalStatistics(Minimax.SearchStrategy strategy, int bestDepth, Move bestMove) {
        long totalTime = System.currentTimeMillis() - startTime;

        System.out.println("=== Search Complete ===");
        System.out.println("Strategy: " + strategy);
        System.out.println("Best depth reached: " + bestDepth);
        System.out.println("Best move: " + bestMove);
        System.out.println("Total time: " + totalTime + "ms");

        // Strategy-specific statistics
        if (strategy == Minimax.SearchStrategy.ALPHA_BETA_Q || strategy == Minimax.SearchStrategy.PVS_Q) {
            if (QuiescenceSearch.qNodes > 0) {
                System.out.println("Q-nodes: " + QuiescenceSearch.qNodes);
                double standPatRate = (100.0 * QuiescenceSearch.standPatCutoffs) / QuiescenceSearch.qNodes;
                System.out.printf("Stand-pat rate: %.1f%%%n", standPatRate);
            }
        }

        System.out.println("====================");
    }

    // === LEGACY COMPATIBILITY ===

    /**
     * Legacy method - find best move with PVS
     */
    public static Move findBestMoveWithPVS(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveIterative(state, maxDepth, timeMillis, Minimax.SearchStrategy.PVS);
    }
}
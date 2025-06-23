package GaT.engine;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.search.Minimax;
import GaT.search.MoveGenerator;
import GaT.search.MoveOrdering;
import GaT.search.QuiescenceSearch;
import GaT.search.PVSSearch;
import GaT.search.SearchEngine;
import GaT.search.SearchStatistics;
import GaT.search.TranspositionTable;
import GaT.evaluation.Evaluator;

import java.util.List;

/**
 * FIXED TIMED MINIMAX ENGINE - Properly integrated with unified SearchStrategy
 *
 * FIXES:
 * ✅ 1. Uses only SearchConfig.SearchStrategy (no more enum conflicts)
 * ✅ 2. Proper SearchEngine initialization and usage
 * ✅ 3. Consistent architecture (no mixing static/instance)
 * ✅ 4. Null pointer protection throughout
 * ✅ 5. Fallback mechanisms for robustness
 * ✅ 6. Better error handling and logging
 */
public class TimedMinimax {

    // === INTEGRATED COMPONENTS ===
    private static final Evaluator evaluator = new Evaluator();
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final TranspositionTable transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);
    private static final SearchStatistics statistics = SearchStatistics.getInstance();
    private static final SearchEngine searchEngine = new SearchEngine(evaluator, moveOrdering, transpositionTable, statistics);

    // === TIME MANAGEMENT ===
    private static long timeLimitMillis;
    private static long startTime;

    // === TIMEOUT EXCEPTION ===
    private static class TimeoutException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    // === MAIN PUBLIC INTERFACES - FIXED ENUM USAGE ===

    /**
     * Find best move with time limit - LEGACY COMPATIBLE
     */
    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveIterative(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.ALPHA_BETA);
    }

    /**
     * Find best move with quiescence search and time control
     */
    public static Move findBestMoveWithTimeAndQuiescence(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveIterative(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.ALPHA_BETA_Q);
    }

    /**
     * ULTIMATE AI - PVS + Quiescence + Iterative Deepening
     */
    public static Move findBestMoveUltimate(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveIterative(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.PVS_Q);
    }

    /**
     * Find best move with specific strategy and time control - FIXED enum
     */
    public static Move findBestMoveWithStrategy(GameState state, int maxDepth, long timeMillis,
                                                SearchConfig.SearchStrategy strategy) {
        return findBestMoveIterative(state, maxDepth, timeMillis, strategy);
    }

    // === CORE ITERATIVE DEEPENING - FIXED ===

    /**
     * FIXED Iterative Deepening with proper SearchConfig.SearchStrategy integration
     */
    private static Move findBestMoveIterative(GameState state, int maxDepth, long timeMillis,
                                              SearchConfig.SearchStrategy strategy) {
        // === VALIDATION ===
        if (state == null) {
            System.err.println("❌ CRITICAL: Null game state provided!");
            return null;
        }

        if (strategy == null) {
            System.err.println("⚠️ Null strategy provided, defaulting to ALPHA_BETA");
            strategy = SearchConfig.SearchStrategy.ALPHA_BETA;
        }

        // Setup time management
        TimedMinimax.timeLimitMillis = timeMillis;
        TimedMinimax.startTime = System.currentTimeMillis();

        // Initialize components
        moveOrdering.resetKillerMoves();
        statistics.reset();
        statistics.startSearch();

        if (strategy == SearchConfig.SearchStrategy.ALPHA_BETA_Q || strategy == SearchConfig.SearchStrategy.PVS_Q) {
            QuiescenceSearch.setRemainingTime(timeMillis);
            QuiescenceSearch.resetQuiescenceStats();
        }

        if (strategy == SearchConfig.SearchStrategy.PVS || strategy == SearchConfig.SearchStrategy.PVS_Q) {
            PVSSearch.setTimeoutChecker(() -> timedOut());
        }

        // === EMERGENCY FALLBACK CHECK ===
        List<Move> emergencyMoves = MoveGenerator.generateAllMoves(state);
        if (emergencyMoves.isEmpty()) {
            System.err.println("❌ CRITICAL: No legal moves available!");
            return null;
        }

        Move bestMove = null;
        Move lastCompleteMove = emergencyMoves.get(0); // Emergency fallback
        int bestDepth = 0;

        System.out.println("=== FIXED Iterative Deepening with " + strategy + " ===");
        System.out.println("Time limit: " + timeMillis + "ms, Max depth: " + maxDepth);
        System.out.println("Available moves: " + emergencyMoves.size());

        // Iterative deepening loop
        for (int depth = 1; depth <= maxDepth; depth++) {
            if (timedOut()) {
                System.out.println("⏱ Time limit reached before depth " + depth);
                break;
            }

            long depthStartTime = System.currentTimeMillis();

            try {
                Move candidate = searchAtDepthFixed(state, depth, strategy);

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
                } else {
                    System.out.println("⚠️ Depth " + depth + " returned null, using previous best");
                    break;
                }

            } catch (TimeoutException e) {
                System.out.println("⏱ Timeout at depth " + depth);
                break;
            } catch (Exception e) {
                System.err.println("❌ Search error at depth " + depth + ": " + e.getMessage());
                e.printStackTrace();
                break;
            }

            // Adaptive time management
            if (!shouldContinueSearch(timeMillis)) {
                System.out.println("⚡ Stopping early due to time constraints");
                break;
            }
        }

        // Final statistics
        statistics.endSearch();
        printFinalStatistics(strategy, bestDepth, bestMove != null ? bestMove : lastCompleteMove);

        // ENSURE WE NEVER RETURN NULL
        Move finalMove = bestMove != null ? bestMove : lastCompleteMove;
        if (finalMove == null) {
            System.err.println("🚨 EMERGENCY: All searches failed, using first legal move");
            finalMove = emergencyMoves.get(0);
        }

        return finalMove;
    }

    // === FIXED DEPTH-SPECIFIC SEARCH ===

    /**
     * FIXED Search at specific depth with proper SearchEngine integration
     */
    private static Move searchAtDepthFixed(GameState state, int depth, SearchConfig.SearchStrategy strategy)
            throws TimeoutException {

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            System.err.println("❌ No moves at depth " + depth);
            return null;
        }

        // Enhanced move ordering
        long hash = state.hash();
        TTEntry entry = transpositionTable.get(hash);
        long remainingTime = timeLimitMillis - (System.currentTimeMillis() - startTime);
        moveOrdering.orderMovesEnhanced(moves, state, depth, entry, null, remainingTime);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        // Search all moves
        for (Move move : moves) {
            if (timedOut()) throw new TimeoutException();

            try {
                GameState copy = state.copy();
                copy.applyMove(move);

                int score = searchWithTimeoutSupportFixed(copy, depth - 1, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, !isRed, strategy);

                if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                    bestScore = score;
                    bestMove = move;
                }

            } catch (TimeoutException e) {
                throw e;
            } catch (Exception e) {
                System.err.println("⚠️ Error searching move " + move + ": " + e.getMessage());
                continue; // Try next move
            }
        }

        return bestMove;
    }

    // === FIXED TIMEOUT-AWARE SEARCH DISPATCH ===

    /**
     * FIXED Search with proper SearchEngine integration and timeout support
     */
    private static int searchWithTimeoutSupportFixed(GameState state, int depth, int alpha, int beta,
                                                     boolean maximizingPlayer, SearchConfig.SearchStrategy strategy) {
        // Regular timeout check
        if (timedOut()) {
            throw new TimeoutException();
        }

        // Set timeout checker for SearchEngine
        searchEngine.setTimeoutChecker(() -> timedOut());

        try {
            // Dispatch to appropriate search method using SearchEngine
            return searchEngine.searchWithTimeout(state, depth, alpha, beta, maximizingPlayer, strategy, () -> timedOut());

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Timeout")) {
                throw new TimeoutException();
            }
            throw e;
        } finally {
            searchEngine.clearTimeoutChecker();
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
        try {
            GameState afterMove = state.copy();
            afterMove.applyMove(move);
            return Minimax.isGameOver(afterMove);
        } catch (Exception e) {
            System.err.println("⚠️ Error checking winning move: " + e.getMessage());
            return false;
        }
    }

    // === LOGGING METHODS ===

    /**
     * Log completion of a depth level
     */
    private static void logDepthCompletion(int depth, Move bestMove, long timeUsed) {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = timeLimitMillis - elapsed;

        System.out.printf("✓ Depth %d: %s (%dms) | Elapsed: %dms | Remaining: %dms | Nodes: %,d%n",
                depth, bestMove, timeUsed, elapsed, remaining, statistics.getTotalNodes());
    }

    /**
     * Print final search statistics
     */
    private static void printFinalStatistics(SearchConfig.SearchStrategy strategy, int bestDepth, Move bestMove) {
        long totalTime = System.currentTimeMillis() - startTime;

        System.out.println("=== FIXED Search Complete ===");
        System.out.println("Strategy: " + strategy);
        System.out.println("Best depth reached: " + bestDepth);
        System.out.println("Best move: " + bestMove);
        System.out.println("Total time: " + totalTime + "ms");
        System.out.println("Total nodes: " + statistics.getTotalNodes());

        if (totalTime > 0) {
            System.out.println("Nodes per second: " + (statistics.getTotalNodes() * 1000 / totalTime));
        }

        // Strategy-specific statistics
        if (strategy == SearchConfig.SearchStrategy.ALPHA_BETA_Q || strategy == SearchConfig.SearchStrategy.PVS_Q) {
            if (QuiescenceSearch.qNodes > 0) {
                System.out.println("Q-nodes: " + QuiescenceSearch.qNodes);
                double standPatRate = (100.0 * QuiescenceSearch.standPatCutoffs) / QuiescenceSearch.qNodes;
                System.out.printf("Stand-pat rate: %.1f%%%n", standPatRate);
            }
        }

        System.out.println("====================");
    }

    // === LEGACY COMPATIBILITY - FIXED ===

    /**
     * Legacy method - find best move with PVS
     */
    public static Move findBestMoveWithPVS(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveIterative(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.PVS);
    }

    // === COMPONENT ACCESS ===

    /**
     * Get SearchEngine instance for direct access if needed
     */
    public static SearchEngine getSearchEngine() {
        return searchEngine;
    }

    /**
     * Get Evaluator instance
     */
    public static Evaluator getEvaluator() {
        return evaluator;
    }

    /**
     * Get current search statistics
     */
    public static SearchStatistics getStatistics() {
        return statistics;
    }
}
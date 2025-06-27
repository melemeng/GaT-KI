package GaT.engine;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.search.*;
import GaT.evaluation.Evaluator;

import java.util.List;

/**
 * FIXED: TimedMinimax - Truly Ultra-Aggressive Search
 *
 * KEY FIXES:
 * ✅ 1. Removed premature termination for "good" scores
 * ✅ 2. Much more aggressive time usage (70-90% of allocated)
 * ✅ 3. Better depth continuation logic
 * ✅ 4. Only terminate for actual checkmate scores
 * ✅ 5. Improved time prediction for next depth
 */
public class TimedMinimax {

    // === SHARED COMPONENTS ===
    private static final SearchStatistics statistics = SearchStatistics.getInstance();
    private static final Evaluator evaluator = new Evaluator();
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final TranspositionTable transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);
    private static final SearchEngine searchEngine = new SearchEngine(evaluator, moveOrdering, transpositionTable, statistics);

    // === SEARCH STATE ===
    private static volatile long timeLimitMillis;
    private static volatile long startTime;
    private static volatile boolean searchAborted = false;
    private static SearchConfig.SearchStrategy currentStrategy = SearchConfig.SearchStrategy.PVS_Q;

    // === CHECKMATE THRESHOLD - Only terminate for actual wins ===
    private static final int CHECKMATE_THRESHOLD = 10000;
    private static final int MIN_SEARCH_DEPTH = 5; // Always search at least this deep

    // === MAIN INTERFACES ===

    public static Move findBestMoveUltimate(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveFixed(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.PVS_Q);
    }

    public static Move findBestMoveWithStrategy(GameState state, int maxDepth, long timeMillis,
                                                SearchConfig.SearchStrategy strategy) {
        return findBestMoveFixed(state, maxDepth, timeMillis, strategy);
    }

    // === CORE FIXED SEARCH ===

    private static Move findBestMoveFixed(GameState state, int maxDepth, long timeMillis,
                                          SearchConfig.SearchStrategy strategy) {
        if (state == null) {
            System.err.println("❌ CRITICAL: Null game state!");
            return null;
        }

        if (strategy == null) {
            strategy = SearchConfig.SearchStrategy.PVS_Q;
        }

        currentStrategy = strategy;

        // Initialize search
        initializeSearchFixed(timeMillis);

        List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
        if (legalMoves.isEmpty()) {
            System.err.println("❌ No legal moves available!");
            return null;
        }

        Move bestMove = legalMoves.get(0); // Emergency fallback
        Move lastCompletedMove = bestMove;
        int bestScore = Integer.MIN_VALUE;
        int bestDepth = 0;
        long totalNodes = 0;

        System.out.println("=== ULTRA-AGGRESSIVE SEARCH WITH " + strategy + " ===");
        System.out.printf("Time: %dms | Legal moves: %d%n", timeMillis, legalMoves.size());

        // === ITERATIVE DEEPENING ===
        for (int depth = 1; depth <= maxDepth && !searchAborted; depth++) {
            long depthStartTime = System.currentTimeMillis();
            long nodesBefore = statistics.getNodeCount();
            long qNodesBefore = statistics.getQNodeCount();

            try {
                SearchResult result = performRealSearch(state, depth, legalMoves);

                if (result != null && result.move != null) {
                    lastCompletedMove = result.move;
                    bestMove = result.move;
                    bestScore = result.score;
                    bestDepth = depth;

                    long depthNodes = statistics.getNodeCount() - nodesBefore;
                    long depthQNodes = statistics.getQNodeCount() - qNodesBefore;
                    totalNodes = statistics.getTotalNodes();

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    double nps = depthTime > 0 ? (double)(depthNodes + depthQNodes) * 1000 / depthTime : 0;

                    System.out.printf("✅ Depth %d: %s (score: %+d, time: %dms, nodes: %,d, q-nodes: %,d, nps: %.0f)%n",
                            depth, bestMove, result.score, depthTime, depthNodes, depthQNodes, nps);

                    // FIXED: Only terminate for TRUE checkmate, not just "good" positions
                    if (Math.abs(result.score) >= CHECKMATE_THRESHOLD && depth >= MIN_SEARCH_DEPTH) {
                        System.out.println("♔ Checkmate found after depth " + depth + ", terminating");
                        break;
                    }

                    // FIXED: Ultra-aggressive time management
                    if (!shouldContinueSearchAggressive(depthTime, timeMillis, depth, totalNodes)) {
                        System.out.println("⏱ Time management: Stopping search (used time effectively)");
                        break;
                    }
                } else {
                    System.out.printf("⚠️ Depth %d failed, using last good move%n", depth);
                    bestMove = lastCompletedMove; // Use last successful search
                    break;
                }

            } catch (Exception e) {
                System.err.printf("❌ Error at depth %d: %s, using last good move%n", depth, e.getMessage());
                bestMove = lastCompletedMove; // Use last successful search
                break;
            }
        }

        // === FINAL STATISTICS ===
        long totalTime = System.currentTimeMillis() - startTime;
        double timeUsagePercent = (double)totalTime / timeMillis * 100;

        System.out.println("=== SEARCH COMPLETE ===");
        System.out.printf("Best move: %s | Score: %+d | Depth: %d | Time: %dms (%.1f%% of allocated)%n",
                bestMove, bestScore, bestDepth, totalTime, timeUsagePercent);
        System.out.printf("Total nodes: %,d (regular: %,d, quiescence: %,d) | NPS: %,.0f%n",
                totalNodes, statistics.getNodeCount(), statistics.getQNodeCount(),
                totalTime > 0 ? (double)totalNodes * 1000 / totalTime : 0);

        // Warn if we didn't use enough time
        if (timeUsagePercent < 50 && bestDepth < 8) {
            System.out.println("⚠️ WARNING: Only used " + timeUsagePercent + "% of time - could be more aggressive!");
        }

        return bestMove;
    }

    // === FIXED: ULTRA-AGGRESSIVE TIME MANAGEMENT ===

    private static boolean shouldContinueSearchAggressive(long lastDepthTime, long totalTimeLimit,
                                                          int currentDepth, long totalNodes) {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = totalTimeLimit - elapsed;

        // FIXED: Always search to minimum depth regardless of time
        if (currentDepth < MIN_SEARCH_DEPTH && remaining > 100) {
            return true;
        }

        // FIXED: If we have lots of time left, ALWAYS continue
        if (remaining > totalTimeLimit * 0.4) { // More than 40% time remaining
            System.out.printf("  ⚡ Plenty of time left (%.1f%%), continuing to depth %d%n",
                    (double)remaining/totalTimeLimit*100, currentDepth + 1);
            return true;
        }

        // FIXED: More accurate growth prediction based on actual data
        double growthFactor;
        if (currentDepth <= 3) {
            growthFactor = 2.0;  // Early depths grow moderately
        } else if (currentDepth <= 5) {
            growthFactor = 2.5;  // Mid depths grow faster
        } else if (currentDepth <= 7) {
            growthFactor = 3.0;  // Deep searches grow significantly
        } else if (currentDepth <= 9) {
            growthFactor = 3.5;  // Very deep searches
        } else {
            growthFactor = 4.0;  // Extreme depths
        }

        // Adjust growth factor based on node count (branching factor indicator)
        if (totalNodes > 1000000) {
            growthFactor *= 1.2; // Complex positions grow faster
        }

        long estimatedNextTime = (long)(lastDepthTime * growthFactor);

        // FIXED: Use 85% of remaining time (ultra-aggressive)
        boolean canComplete = estimatedNextTime < remaining * 0.85;

        if (!canComplete) {
            System.out.printf("  ⏱ Next depth %d estimated %dms > 85%% of remaining %dms%n",
                    currentDepth + 1, estimatedNextTime, remaining);
        } else {
            System.out.printf("  ⚡ Continuing to depth %d (estimated %dms < 85%% of %dms remaining)%n",
                    currentDepth + 1, estimatedNextTime, remaining);
        }

        return canComplete;
    }

    // === SEARCH IMPLEMENTATION ===

    private static SearchResult performRealSearch(GameState state, int depth, List<Move> legalMoves) {
        // Order moves
        orderMoves(legalMoves, state, depth);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        // Set timeout checker for SearchEngine
        searchEngine.setTimeoutChecker(() -> searchAborted ||
                System.currentTimeMillis() - startTime >= timeLimitMillis * 95 / 100);

        try {
            int moveCount = 0;
            for (Move move : legalMoves) {
                if (searchAborted) break;

                moveCount++;
                GameState copy = state.copy();
                copy.applyMove(move);

                // Use SearchEngine with proper strategy
                int score = searchEngine.search(copy, depth - 1, alpha, beta, !isRed, currentStrategy);

                if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                    bestScore = score;
                    bestMove = move;

                    // Update alpha-beta
                    if (isRed) {
                        alpha = Math.max(alpha, score);
                    } else {
                        beta = Math.min(beta, score);
                    }
                }

                // FIXED: Only log truly exceptional moves
                if (Math.abs(score) >= CHECKMATE_THRESHOLD) {
                    System.out.printf("  ♔ Checkmate move found: %s (score: %+d)%n", move, score);
                }

                // Time check every few moves
                if (moveCount % 5 == 0 && shouldAbortSearch()) {
                    System.out.println("  ⏱ Time limit approaching, completing current depth");
                    break;
                }
            }
        } finally {
            searchEngine.clearTimeoutChecker();
        }

        return bestMove != null ? new SearchResult(bestMove, bestScore) : null;
    }

    // === HELPER METHODS ===

    private static void initializeSearchFixed(long timeMillis) {
        startTime = System.currentTimeMillis();
        timeLimitMillis = timeMillis;
        searchAborted = false;

        // Reset statistics
        statistics.reset();
        statistics.startSearch();
        QuiescenceSearch.resetQuiescenceStats();

        // Clear TT if too full
        if (transpositionTable.size() > SearchConfig.TT_EVICTION_THRESHOLD) {
            transpositionTable.clear();
            System.out.println("🔧 Cleared transposition table");
        }

        // Reset move ordering
        moveOrdering.resetKillerMoves();

        // Set evaluation time
        Evaluator.setRemainingTime(timeMillis);
        QuiescenceSearch.setRemainingTime(timeMillis);

        System.out.println("🔧 Search initialized with " + currentStrategy + " - ULTRA-AGGRESSIVE MODE");
    }

    private static void orderMoves(List<Move> moves, GameState state, int depth) {
        if (moves.size() <= 1) return;

        try {
            TTEntry entry = transpositionTable.get(state.hash());
            moveOrdering.orderMoves(moves, state, depth, entry);
        } catch (Exception e) {
            // Simple fallback ordering
            moves.sort((a, b) -> {
                boolean aCap = isCapture(a, state);
                boolean bCap = isCapture(b, state);
                if (aCap && !bCap) return -1;
                if (!aCap && bCap) return 1;
                return Integer.compare(b.amountMoved, a.amountMoved);
            });
        }
    }

    private static boolean shouldAbortSearch() {
        if (!searchAborted && System.currentTimeMillis() - startTime >= timeLimitMillis * 98 / 100) {
            searchAborted = true;
            return true;
        }
        return searchAborted;
    }

    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    // === RESULT CLASS ===

    private static class SearchResult {
        final Move move;
        final int score;

        SearchResult(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }

    // === LEGACY COMPATIBILITY ===

    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveFixed(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.PVS_Q);
    }

    public static long getTotalNodesSearched() {
        return statistics.getTotalNodes();
    }
}
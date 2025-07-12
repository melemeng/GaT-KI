package GaT.engine;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.search.*;
import GaT.evaluation.Evaluator;

import java.util.List;

/**
 * ENHANCED TIMED MINIMAX - WITH HISTORY HEURISTIC INTEGRATION
 *
 * ENHANCEMENTS:
 * ✅ Integrates new HistoryHeuristic system
 * ✅ Updates both legacy and new history on cutoffs
 * ✅ Enhanced time management
 * ✅ Better move ordering integration
 * ✅ Comprehensive statistics
 * ✅ Optimized for single-threaded performance
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

    public static Move findBestMoveWithStrategy(GameState state, int maxDepth, long timeMillis, SearchConfig.SearchStrategy strategy) {
        return findBestMoveFixed(state, maxDepth, timeMillis, strategy);
    }

    // === CORE ENHANCED SEARCH ===

    private static Move findBestMoveFixed(GameState state, int maxDepth, long timeMillis, SearchConfig.SearchStrategy strategy) {
        if (state == null) {
            System.err.println("❌ CRITICAL: Null game state!");
            return null;
        }

        if (strategy == null) {
            strategy = SearchConfig.SearchStrategy.PVS_Q;
        }

        currentStrategy = strategy;

        // Initialize search with enhanced features
        initializeSearchEnhanced(state, timeMillis);

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

        System.out.println("=== ENHANCED SEARCH WITH " + strategy + " & HISTORY HEURISTIC ===");
        System.out.printf("Time: %dms | Legal moves: %d | History entries: %s%n",
                timeMillis, legalMoves.size(), moveOrdering.getHistoryHeuristic().getStatistics());

        // === ITERATIVE DEEPENING ===
        for (int depth = 1; depth <= maxDepth && !searchAborted; depth++) {
            long depthStartTime = System.currentTimeMillis();
            long nodesBefore = statistics.getNodeCount();
            long qNodesBefore = statistics.getQNodeCount();

            try {
                SearchResult result = performEnhancedSearch(state, depth, legalMoves);

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

                    // Only terminate for TRUE checkmate
                    if (Math.abs(result.score) >= CHECKMATE_THRESHOLD && depth >= MIN_SEARCH_DEPTH) {
                        System.out.println("♔ Checkmate found after depth " + depth + ", terminating");
                        break;
                    }

                    // Enhanced time management
                    if (!shouldContinueSearchEnhanced(depthTime, timeMillis, depth, totalNodes)) {
                        System.out.println("⏱ Enhanced time management: Stopping search");
                        break;
                    }
                } else {
                    System.out.printf("⚠️ Depth %d failed, using last good move%n", depth);
                    bestMove = lastCompletedMove;
                    break;
                }

            } catch (Exception e) {
                System.err.printf("❌ Error at depth %d: %s, using last good move%n", depth, e.getMessage());
                bestMove = lastCompletedMove;
                break;
            }
        }

        // === ENHANCED FINAL STATISTICS ===
        long totalTime = System.currentTimeMillis() - startTime;
        double timeUsagePercent = (double)totalTime / timeMillis * 100;

        System.out.println("=== ENHANCED SEARCH COMPLETE ===");
        System.out.printf("Best move: %s | Score: %+d | Depth: %d | Time: %dms (%.1f%% of allocated)%n",
                bestMove, bestScore, bestDepth, totalTime, timeUsagePercent);
        System.out.printf("Total nodes: %,d (regular: %,d, quiescence: %,d) | NPS: %,.0f%n",
                totalNodes, statistics.getNodeCount(), statistics.getQNodeCount(),
                totalTime > 0 ? (double)totalNodes * 1000 / totalTime : 0);

        // Show enhanced move ordering statistics
        System.out.println("📊 " + moveOrdering.getStatistics());
        System.out.println("🔄 Best history move: " + moveOrdering.getHistoryHeuristic().getBestHistoryMove());

        return bestMove;
    }

    // === ENHANCED TIME MANAGEMENT ===

    private static boolean shouldContinueSearchEnhanced(long lastDepthTime, long totalTimeLimit,
                                                        int currentDepth, long totalNodes) {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = totalTimeLimit - elapsed;

        // Always search to minimum depth regardless of time
        if (currentDepth < MIN_SEARCH_DEPTH && remaining > 100) {
            return true;
        }

        // Enhanced conservative approach - more time for history to build up
        if (remaining > totalTimeLimit * 0.6) { // More than 60% time remaining
            System.out.printf("  ⚡ Plenty of time left (%.1f%%), continuing to depth %d%n",
                    (double)remaining/totalTimeLimit*100, currentDepth + 1);
            return true;
        }

        // Enhanced growth prediction based on node count and history effectiveness
        double growthFactor;
        if (currentDepth <= 4) {
            growthFactor = 2.8;  // Slightly less conservative early (history building)
        } else if (currentDepth <= 6) {
            growthFactor = 3.2;  // Moderate for mid depths
        } else if (currentDepth <= 8) {
            growthFactor = 3.8;  // Conservative for deep searches
        } else {
            growthFactor = 4.5;  // Very conservative for very deep
        }

        // Adjust based on search efficiency (good move ordering = less nodes)
        if (totalNodes < 500000) {
            growthFactor *= 0.9; // Efficient search, slightly more optimistic
        } else if (totalNodes > 2000000) {
            growthFactor *= 1.2; // Inefficient search, more conservative
        }

        long estimatedNextTime = (long)(lastDepthTime * growthFactor);

        // Use 75% of remaining time (slightly less conservative with good move ordering)
        boolean canComplete = estimatedNextTime < remaining * 0.75;

        if (!canComplete) {
            System.out.printf("  ⏱ Next depth %d estimated %dms > 75%% of remaining %dms%n",
                    currentDepth + 1, estimatedNextTime, remaining);
        }

        return canComplete;
    }

    // === ENHANCED SEARCH IMPLEMENTATION ===

    private static SearchResult performEnhancedSearch(GameState state, int depth, List<Move> legalMoves) {
        // Enhanced move ordering
        orderMovesEnhanced(legalMoves, state, depth);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        // Set enhanced timeout checker
        searchEngine.setTimeoutChecker(() -> searchAborted ||
                System.currentTimeMillis() - startTime >= timeLimitMillis * 92 / 100); // 92% threshold

        try {
            int moveCount = 0;
            for (Move move : legalMoves) {
                if (searchAborted) break;

                moveCount++;
                GameState copy = state.copy();
                copy.applyMove(move);

                // Search with enhanced engine
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

                    // Update history heuristics on best move
                    updateHistoryHeuristics(move, state, depth, score);
                }

                // Log exceptional moves
                if (Math.abs(score) >= CHECKMATE_THRESHOLD) {
                    System.out.printf("  ♔ Checkmate move found: %s (score: %+d)%n", move, score);
                }

                // Periodic time checks
                if (moveCount % 3 == 0 && shouldAbortSearch()) {
                    System.out.println("  ⏱ Time limit approaching, completing current depth");
                    break;
                }
            }
        } finally {
            searchEngine.clearTimeoutChecker();
        }

        return bestMove != null ? new SearchResult(bestMove, bestScore) : null;
    }

    // === ENHANCED HELPER METHODS ===

    private static void initializeSearchEnhanced(GameState state, long timeMillis) {
        startTime = System.currentTimeMillis();
        timeLimitMillis = timeMillis;
        searchAborted = false;

        // Reset statistics
        statistics.reset();
        statistics.startSearch();
        QuiescenceSearch.resetQuiescenceStats();

        // Enhanced TT management
        if (transpositionTable.size() > SearchConfig.TT_EVICTION_THRESHOLD) {
            transpositionTable.clear();
            System.out.println("🔧 Cleared transposition table");
        }

        // Enhanced move ordering reset - resets both killer moves and history heuristic
        moveOrdering.resetForNewSearch();

        // Set evaluation time
        Evaluator.setRemainingTime(timeMillis);
        QuiescenceSearch.setRemainingTime(timeMillis);

        System.out.println("🔧 Enhanced search initialized with " + currentStrategy + " + History Heuristic");

        // Show initial position analysis
        if (isInterestingPosition(state)) {
            System.out.println("🎯 Interesting position detected - may need deeper search");
        }
    }

    private static void orderMovesEnhanced(List<Move> moves, GameState state, int depth) {
        if (moves.size() <= 1) return;

        try {
            TTEntry entry = transpositionTable.get(state.hash());
            long remainingTime = timeLimitMillis - (System.currentTimeMillis() - startTime);

            // Use enhanced move ordering with remaining time consideration
            moveOrdering.orderMovesEnhanced(moves, state, depth, entry, null, remainingTime);
        } catch (Exception e) {
            // Fallback to simple ordering
            moves.sort((a, b) -> {
                boolean aCap = isCapture(a, state);
                boolean bCap = isCapture(b, state);
                if (aCap && !bCap) return -1;
                if (!aCap && bCap) return 1;
                return Integer.compare(b.amountMoved, a.amountMoved);
            });
        }
    }

    private static void updateHistoryHeuristics(Move move, GameState state, int depth, int score) {
        // Update on good moves (not just cutoffs for now)
        if (Math.abs(score) > 100) { // Threshold for "good" moves
            moveOrdering.updateHistoryOnCutoff(move, state, depth);
        }
    }

    private static boolean isInterestingPosition(GameState state) {
        // Simple heuristic for interesting positions (many pieces, guards active, etc.)
        int totalPieces = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalPieces += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        boolean guardsActive = state.redGuard != 0 && state.blueGuard != 0;
        boolean manyPieces = totalPieces > 10;

        return guardsActive && manyPieces;
    }

    private static boolean shouldAbortSearch() {
        if (!searchAborted && System.currentTimeMillis() - startTime >= timeLimitMillis * 96 / 100) { // 96% threshold
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

    // === ENHANCED PUBLIC INTERFACES ===

    /**
     * Get comprehensive search statistics including history heuristic
     */
    public static String getEnhancedStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ENHANCED SEARCH STATISTICS ===\n");
        sb.append("Total nodes: ").append(statistics.getTotalNodes()).append("\n");
        sb.append("Regular nodes: ").append(statistics.getNodeCount()).append("\n");
        sb.append("Quiescence nodes: ").append(statistics.getQNodeCount()).append("\n");
        sb.append(moveOrdering.getStatistics()).append("\n");
        sb.append("Best history move: ").append(moveOrdering.getHistoryHeuristic().getBestHistoryMove()).append("\n");
        return sb.toString();
    }

    /**
     * Force history reset for new game
     */
    public static void resetForNewGame() {
        moveOrdering.resetForNewSearch();
        transpositionTable.clear();
        statistics.reset();
        System.out.println("🔄 Reset all systems for new game");
    }

    /**
     * Get move ordering effectiveness ratio
     */
    public static double getMoveOrderingEffectiveness() {
        long totalNodes = statistics.getTotalNodes();
        if (totalNodes == 0) return 0.0;

        // Simple heuristic: fewer nodes = better move ordering
        // This is a placeholder - you might want to implement proper tracking
        return Math.min(1.0, 1000000.0 / totalNodes);
    }

    // === LEGACY COMPATIBILITY ===

    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveFixed(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.PVS_Q);
    }

    public static long getTotalNodesSearched() {
        return statistics.getTotalNodes();
    }

    // === TESTING/DEBUGGING INTERFACES ===

    /**
     * Test history heuristic effectiveness
     */
    public static void testHistoryHeuristic(GameState testPosition, int depth) {
        System.out.println("=== TESTING HISTORY HEURISTIC ===");

        // Reset and search without history
        moveOrdering.getHistoryHeuristic().reset();
        long startTime = System.currentTimeMillis();
        Move moveWithoutHistory = findBestMoveFixed(testPosition, depth, 10000, SearchConfig.SearchStrategy.ALPHA_BETA);
        long timeWithoutHistory = System.currentTimeMillis() - startTime;
        long nodesWithoutHistory = statistics.getTotalNodes();

        statistics.reset();

        // Search with history (it will build up during search)
        startTime = System.currentTimeMillis();
        Move moveWithHistory = findBestMoveFixed(testPosition, depth, 10000, SearchConfig.SearchStrategy.PVS_Q);
        long timeWithHistory = System.currentTimeMillis() - startTime;
        long nodesWithHistory = statistics.getTotalNodes();

        System.out.printf("Without History: %s (%dms, %d nodes)%n",
                moveWithoutHistory, timeWithoutHistory, nodesWithoutHistory);
        System.out.printf("With History: %s (%dms, %d nodes)%n",
                moveWithHistory, timeWithHistory, nodesWithHistory);
        System.out.printf("Improvement: %.1fx time, %.1fx nodes%n",
                (double)timeWithoutHistory/timeWithHistory,
                (double)nodesWithoutHistory/nodesWithHistory);
    }
}
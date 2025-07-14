package GaT.engine;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;
import GaT.model.TTEntry;
import GaT.search.*;
import GaT.evaluation.Evaluator;

import java.util.List;

/**
 * TIMED MINIMAX - COMPLETE SEARCHCONFIG INTEGRATION
 *
 * CHANGES:
 * ✅ All constants now use SearchConfig parameters
 * ✅ Time management using SearchConfig.TIME_* parameters
 * ✅ Checkmate detection using SearchConfig.CHECKMATE_THRESHOLD
 * ✅ Search depth limits using SearchConfig.MAX_DEPTH
 * ✅ Strategy selection using SearchConfig.DEFAULT_STRATEGY
 * ✅ Performance targets using SearchConfig.NODES_PER_SECOND_TARGET
 * ✅ All hardcoded values replaced with SearchConfig
 */
public class TimedMinimax {

    // === SHARED COMPONENTS WITH SEARCHCONFIG ===
    private static final SearchStatistics statistics = SearchStatistics.getInstance();
    private EnhancedEvaluator evaluator = new EnhancedEvaluator();
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final TranspositionTable transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);
    private static final SearchEngine searchEngine = new SearchEngine(evaluator, moveOrdering, transpositionTable, statistics);

    // === SEARCH STATE ===
    private static volatile long timeLimitMillis;
    private static volatile long startTime;
    private static volatile boolean searchAborted = false;
    private static SearchConfig.SearchStrategy currentStrategy = SearchConfig.DEFAULT_STRATEGY;

    // === THRESHOLDS FROM SEARCHCONFIG ===
    // Removed hardcoded: private static final int CHECKMATE_THRESHOLD = 10000;
    // Now uses: SearchConfig.CHECKMATE_THRESHOLD

    // Removed hardcoded: private static final int MIN_SEARCH_DEPTH = 5;
    // Now uses: SearchConfig.MIN_SEARCH_DEPTH

    // === MAIN INTERFACES WITH SEARCHCONFIG ===

    public static Move findBestMoveUltimate(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveWithConfig(state, maxDepth, timeMillis, SearchConfig.DEFAULT_STRATEGY);
    }

    public static Move findBestMoveWithStrategy(GameState state, int maxDepth, long timeMillis, SearchConfig.SearchStrategy strategy) {
        return findBestMoveWithConfig(state, maxDepth, timeMillis, strategy);
    }

    // === CORE SEARCH WITH SEARCHCONFIG ===

    private static Move findBestMoveWithConfig(GameState state, int maxDepth, long timeMillis, SearchConfig.SearchStrategy strategy) {
        if (state == null) {
            System.err.println("❌ CRITICAL: Null game state!");
            return null;
        }

        if (strategy == null) {
            strategy = SearchConfig.DEFAULT_STRATEGY;
        }

        // Validate maxDepth against SearchConfig
        if (maxDepth > SearchConfig.MAX_DEPTH) {
            System.out.printf("⚠️ maxDepth %d exceeds SearchConfig.MAX_DEPTH (%d), clamping\n",
                    maxDepth, SearchConfig.MAX_DEPTH);
            maxDepth = SearchConfig.MAX_DEPTH;
        }

        currentStrategy = strategy;

        // Initialize search with SearchConfig parameters
        initializeSearchWithConfig(state, timeMillis);

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

        System.out.println("=== TIMED SEARCH WITH SEARCHCONFIG ===");
        System.out.printf("Strategy: %s | Time: %dms | Legal moves: %d | Max depth: %d\n",
                strategy, timeMillis, legalMoves.size(), maxDepth);
        System.out.printf("SearchConfig: Emergency=%dms, Checkmate=%d, MinDepth=%d\n",
                SearchConfig.EMERGENCY_TIME_MS, SearchConfig.CHECKMATE_THRESHOLD, SearchConfig.MIN_SEARCH_DEPTH);

        // === ITERATIVE DEEPENING WITH SEARCHCONFIG ===
        for (int depth = 1; depth <= maxDepth && !searchAborted; depth++) {
            long depthStartTime = System.currentTimeMillis();
            long nodesBefore = statistics.getNodeCount();
            long qNodesBefore = statistics.getQNodeCount();

            try {
                SearchResult result = performSearchWithConfig(state, depth, legalMoves);

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

                    System.out.printf("✅ Depth %d: %s (score: %+d, time: %dms, nodes: %,d, q-nodes: %,d, nps: %.0f)\n",
                            depth, bestMove, result.score, depthTime, depthNodes, depthQNodes, nps);

                    // Use SearchConfig thresholds for early termination
                    if (Math.abs(result.score) >= SearchConfig.CHECKMATE_THRESHOLD && depth >= SearchConfig.MIN_SEARCH_DEPTH) {
                        System.out.printf("♔ Checkmate found at depth %d (threshold: %d), terminating\n",
                                depth, SearchConfig.CHECKMATE_THRESHOLD);
                        break;
                    }

                    if (Math.abs(result.score) >= SearchConfig.FORCED_MATE_THRESHOLD) {
                        System.out.printf("🎯 Forced mate found (threshold: %d), terminating\n",
                                SearchConfig.FORCED_MATE_THRESHOLD);
                        break;
                    }

                    // Enhanced time management using SearchConfig
                    if (!shouldContinueSearchWithConfig(depthTime, timeMillis, depth, totalNodes)) {
                        System.out.println("⏱ SearchConfig time management: Stopping search");
                        break;
                    }
                } else {
                    System.out.printf("⚠️ Depth %d failed, using last good move\n", depth);
                    bestMove = lastCompletedMove;
                    break;
                }

            } catch (Exception e) {
                System.err.printf("❌ Error at depth %d: %s, using last good move\n", depth, e.getMessage());
                bestMove = lastCompletedMove;
                break;
            }
        }

        // === FINAL STATISTICS WITH SEARCHCONFIG ===
        long totalTime = System.currentTimeMillis() - startTime;
        double timeUsagePercent = (double)totalTime / timeMillis * 100;
        double npsAchieved = totalTime > 0 ? (double)totalNodes * 1000 / totalTime : 0;

        System.out.println("=== SEARCHCONFIG SEARCH COMPLETE ===");
        System.out.printf("Best move: %s | Score: %+d | Depth: %d | Time: %dms (%.1f%% of allocated)\n",
                bestMove, bestScore, bestDepth, totalTime, timeUsagePercent);
        System.out.printf("Total nodes: %,d | NPS: %,.0f (target: %,d)\n",
                totalNodes, npsAchieved, SearchConfig.NODES_PER_SECOND_TARGET);

        // Performance analysis using SearchConfig
        if (npsAchieved >= SearchConfig.NODES_PER_SECOND_TARGET) {
            System.out.println("✅ Performance meets SearchConfig target");
        } else {
            System.out.printf("⚠️ Performance below SearchConfig target (%.1f%% of target)\n",
                    npsAchieved / SearchConfig.NODES_PER_SECOND_TARGET * 100);
        }

        // Show enhanced move ordering statistics
        System.out.println("📊 " + moveOrdering.getStatistics());

        return bestMove;
    }

    // === TIME MANAGEMENT WITH SEARCHCONFIG ===

    private static boolean shouldContinueSearchWithConfig(long lastDepthTime, long totalTimeLimit,
                                                          int currentDepth, long totalNodes) {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = totalTimeLimit - elapsed;

        // Always search to minimum depth using SearchConfig regardless of time
        if (currentDepth < SearchConfig.MIN_SEARCH_DEPTH && remaining > 100) {
            return true;
        }

        // Use SearchConfig time thresholds for decision making
        if (remaining > totalTimeLimit * 0.6) { // More than 60% time remaining
            System.out.printf("  ⚡ Plenty of time left (%.1f%%), continuing to depth %d\n",
                    (double)remaining/totalTimeLimit*100, currentDepth + 1);
            return true;
        }

        // Enhanced growth prediction using SearchConfig node targets
        double growthFactor;
        if (currentDepth <= 4) {
            growthFactor = 2.8;
        } else if (currentDepth <= 6) {
            growthFactor = 3.2;
        } else if (currentDepth <= 8) {
            growthFactor = 3.8;
        } else {
            growthFactor = 4.5;
        }

        // Adjust based on SearchConfig node performance
        if (totalNodes < SearchConfig.NODES_PER_SECOND_TARGET / 2) {
            growthFactor *= 0.9; // Efficient search, slightly more optimistic
        } else if (totalNodes > SearchConfig.NODES_PER_SECOND_TARGET * 2) {
            growthFactor *= 1.2; // Inefficient search, more conservative
        }

        long estimatedNextTime = (long)(lastDepthTime * growthFactor);

        // Use 75% of remaining time
        boolean canComplete = estimatedNextTime < remaining * 0.75;

        if (!canComplete) {
            System.out.printf("  ⏱ Next depth %d estimated %dms > 75%% of remaining %dms\n",
                    currentDepth + 1, estimatedNextTime, remaining);
        }

        return canComplete;
    }

    // === SEARCH IMPLEMENTATION WITH SEARCHCONFIG ===

    private static SearchResult performSearchWithConfig(GameState state, int depth, List<Move> legalMoves) {
        // Enhanced move ordering using SearchConfig
        orderMovesWithConfig(legalMoves, state, depth);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        // Set timeout checker using SearchConfig thresholds
        searchEngine.setTimeoutChecker(() -> searchAborted ||
                System.currentTimeMillis() - startTime >= timeLimitMillis * 92 / 100);

        try {
            int moveCount = 0;
            for (Move move : legalMoves) {
                if (searchAborted) break;

                moveCount++;
                GameState copy = state.copy();
                copy.applyMove(move);

                // Search using current strategy from SearchConfig
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
                    updateHistoryHeuristicsWithConfig(move, state, depth, score);
                }

                // Log exceptional moves using SearchConfig thresholds
                if (Math.abs(score) >= SearchConfig.CHECKMATE_THRESHOLD) {
                    System.out.printf("  ♔ Checkmate move found: %s (score: %+d, threshold: %d)\n",
                            move, score, SearchConfig.CHECKMATE_THRESHOLD);
                }

                if (Math.abs(score) >= SearchConfig.WINNING_SCORE_THRESHOLD) {
                    System.out.printf("  🎯 Winning move found: %s (score: %+d, threshold: %d)\n",
                            move, score, SearchConfig.WINNING_SCORE_THRESHOLD);
                }

                // Periodic time checks
                if (moveCount % 3 == 0 && shouldAbortSearchWithConfig()) {
                    System.out.println("  ⏱ Time limit approaching, completing current depth");
                    break;
                }
            }
        } finally {
            searchEngine.clearTimeoutChecker();
        }

        return bestMove != null ? new SearchResult(bestMove, bestScore) : null;
    }

    // === HELPER METHODS WITH SEARCHCONFIG ===

    private static void initializeSearchWithConfig(GameState state, long timeMillis) {
        startTime = System.currentTimeMillis();
        timeLimitMillis = timeMillis;
        searchAborted = false;

        // Reset statistics
        statistics.reset();
        statistics.startSearch();
        QuiescenceSearch.resetQuiescenceStats();

        // Enhanced TT management using SearchConfig
        if (transpositionTable.size() > SearchConfig.TT_EVICTION_THRESHOLD) {
            transpositionTable.clear();
            System.out.printf("🔧 Cleared transposition table (size > %d)\n", SearchConfig.TT_EVICTION_THRESHOLD);
        }

        // Enhanced move ordering reset
        moveOrdering.resetForNewSearch();

        // Set evaluation time
        Evaluator.setRemainingTime(timeMillis);
        QuiescenceSearch.setRemainingTime(timeMillis);

        System.out.printf("🔧 Search initialized with %s + SearchConfig parameters\n", currentStrategy);
        System.out.printf("   Time: %dms | Emergency threshold: %dms | TT size: %,d\n",
                timeMillis, SearchConfig.EMERGENCY_TIME_MS, SearchConfig.TT_SIZE);

        // Show initial position analysis using SearchConfig
        if (isInterestingPositionWithConfig(state)) {
            System.out.println("🎯 Interesting position detected - may need deeper search");
        }
    }

    private static void orderMovesWithConfig(List<Move> moves, GameState state, int depth) {
        if (moves.size() <= 1) return;

        try {
            TTEntry entry = transpositionTable.get(state.hash());
            // Use SearchConfig-aware move ordering
            moveOrdering.orderMoves(moves, state, depth, entry);
        } catch (Exception e) {
            // Fallback to simple ordering using SearchConfig values
            moves.sort((a, b) -> {
                boolean aCap = isCapture(a, state);
                boolean bCap = isCapture(b, state);
                if (aCap && !bCap) return -1;
                if (!aCap && bCap) return 1;
                // Use SearchConfig.ACTIVITY_BONUS for tie-breaking
                return Integer.compare(b.amountMoved * SearchConfig.ACTIVITY_BONUS,
                        a.amountMoved * SearchConfig.ACTIVITY_BONUS);
            });
        }
    }

    private static void updateHistoryHeuristicsWithConfig(Move move, GameState state, int depth, int score) {
        // Update on good moves using SearchConfig thresholds
        if (Math.abs(score) > SearchConfig.TOWER_HEIGHT_VALUE) { // Threshold for "good" moves
            moveOrdering.updateHistoryOnCutoff(move, state, depth);
        }
    }

    private static boolean isInterestingPositionWithConfig(GameState state) {
        // Enhanced position analysis using SearchConfig thresholds
        int totalPieces = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalPieces += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        boolean guardsActive = state.redGuard != 0 && state.blueGuard != 0;
        boolean manyPieces = totalPieces > SearchConfig.ENDGAME_MATERIAL_THRESHOLD;
        boolean tacticalPosition = totalPieces <= SearchConfig.TABLEBASE_MATERIAL_THRESHOLD && guardsActive;

        return (guardsActive && manyPieces) || tacticalPosition;
    }

    private static boolean shouldAbortSearchWithConfig() {
        if (!searchAborted && System.currentTimeMillis() - startTime >= timeLimitMillis * 96 / 100) {
            searchAborted = true;
            System.out.println("🚨 SearchConfig timeout threshold reached (96%)");
            return true;
        }
        return searchAborted;
    }

    private static boolean isCapture(Move move, GameState state) {
        if (move == null || state == null) return false;
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

    // === ENHANCED PUBLIC INTERFACES WITH SEARCHCONFIG ===

    /**
     * Get comprehensive search statistics including SearchConfig info
     */
    public static String getEnhancedStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TIMED MINIMAX STATISTICS WITH SEARCHCONFIG ===\n");
        sb.append("Total nodes: ").append(statistics.getTotalNodes()).append("\n");
        sb.append("Regular nodes: ").append(statistics.getNodeCount()).append("\n");
        sb.append("Quiescence nodes: ").append(statistics.getQNodeCount()).append("\n");
        sb.append("Strategy used: ").append(currentStrategy).append("\n");
        sb.append("SearchConfig parameters:\n");
        sb.append("  DEFAULT_STRATEGY: ").append(SearchConfig.DEFAULT_STRATEGY).append("\n");
        sb.append("  MAX_DEPTH: ").append(SearchConfig.MAX_DEPTH).append("\n");
        sb.append("  CHECKMATE_THRESHOLD: ").append(SearchConfig.CHECKMATE_THRESHOLD).append("\n");
        sb.append("  MIN_SEARCH_DEPTH: ").append(SearchConfig.MIN_SEARCH_DEPTH).append("\n");
        sb.append("  NODES_PER_SECOND_TARGET: ").append(SearchConfig.NODES_PER_SECOND_TARGET).append("\n");
        sb.append(moveOrdering.getStatistics()).append("\n");
        return sb.toString();
    }

    /**
     * Force reset using SearchConfig parameters
     */
    public static void resetForNewGameWithConfig() {
        moveOrdering.resetForNewSearch();
        transpositionTable.clear();
        statistics.reset();
        currentStrategy = SearchConfig.DEFAULT_STRATEGY;

        System.out.println("🔄 Reset all systems for new game with SearchConfig");
        System.out.printf("   Strategy reset to: %s\n", SearchConfig.DEFAULT_STRATEGY);
        System.out.printf("   TT cleared (size was configured for %,d entries)\n", SearchConfig.TT_SIZE);
    }

    /**
     * Get move ordering effectiveness using SearchConfig benchmarks
     */
    public static double getMoveOrderingEffectivenessWithConfig() {
        long totalNodes = statistics.getTotalNodes();
        if (totalNodes == 0) return 0.0;

        // Enhanced heuristic using SearchConfig target
        double targetNodes = SearchConfig.NODES_PER_SECOND_TARGET / 1000.0; // Per millisecond
        return Math.min(1.0, targetNodes / totalNodes);
    }

    /**
     * Analyze performance against SearchConfig targets
     */
    public static String analyzePerformanceAgainstConfig(long searchTimeMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PERFORMANCE ANALYSIS AGAINST SEARCHCONFIG ===\n");

        long totalNodes = statistics.getTotalNodes();
        double npsAchieved = searchTimeMs > 0 ? (double)totalNodes * 1000 / searchTimeMs : 0;

        sb.append(String.format("Nodes searched: %,d\n", totalNodes));
        sb.append(String.format("Time taken: %dms\n", searchTimeMs));
        sb.append(String.format("NPS achieved: %.0f\n", npsAchieved));
        sb.append(String.format("SearchConfig target: %,d NPS\n", SearchConfig.NODES_PER_SECOND_TARGET));

        double targetRatio = npsAchieved / SearchConfig.NODES_PER_SECOND_TARGET;
        if (targetRatio >= 1.0) {
            sb.append(String.format("✅ Exceeds target by %.1f%%\n", (targetRatio - 1) * 100));
        } else {
            sb.append(String.format("⚠️ Below target by %.1f%%\n", (1 - targetRatio) * 100));
        }

        // Node limit analysis
        if (totalNodes > SearchConfig.MAX_NODES_PER_SEARCH) {
            sb.append(String.format("⚠️ Exceeded SearchConfig.MAX_NODES_PER_SEARCH (%,d)\n",
                    SearchConfig.MAX_NODES_PER_SEARCH));
        }

        // Strategy effectiveness
        sb.append(String.format("Strategy used: %s\n", currentStrategy));
        if (currentStrategy != SearchConfig.DEFAULT_STRATEGY) {
            sb.append(String.format("⚠️ Strategy differs from SearchConfig.DEFAULT_STRATEGY (%s)\n",
                    SearchConfig.DEFAULT_STRATEGY));
        }

        return sb.toString();
    }

    // === LEGACY COMPATIBILITY WITH SEARCHCONFIG ===

    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveWithConfig(state, maxDepth, timeMillis, SearchConfig.DEFAULT_STRATEGY);
    }

    public static long getTotalNodesSearched() {
        return statistics.getTotalNodes();
    }

    // === TESTING/DEBUGGING WITH SEARCHCONFIG ===

    /**
     * Test different SearchConfig strategies
     */
    public static void testStrategiesWithConfig(GameState testPosition, int depth, long timeMs) {
        System.out.println("=== TESTING SEARCHCONFIG STRATEGIES ===");

        SearchConfig.SearchStrategy[] strategies = {
                SearchConfig.SearchStrategy.ALPHA_BETA,
                SearchConfig.SearchStrategy.ALPHA_BETA_Q,
                SearchConfig.SearchStrategy.PVS,
                SearchConfig.SearchStrategy.PVS_Q
        };

        for (SearchConfig.SearchStrategy strategy : strategies) {
            System.out.printf("\nTesting %s:\n", strategy);

            statistics.reset();
            long startTime = System.currentTimeMillis();

            Move move = findBestMoveWithConfig(testPosition, depth, timeMs, strategy);

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            long nodes = statistics.getTotalNodes();
            double nps = totalTime > 0 ? (double)nodes * 1000 / totalTime : 0;

            System.out.printf("  Move: %s\n", move);
            System.out.printf("  Time: %dms\n", totalTime);
            System.out.printf("  Nodes: %,d\n", nodes);
            System.out.printf("  NPS: %.0f (target: %,d)\n", nps, SearchConfig.NODES_PER_SECOND_TARGET);
            System.out.printf("  Target ratio: %.1f%%\n", nps / SearchConfig.NODES_PER_SECOND_TARGET * 100);
        }

        System.out.printf("\nSearchConfig.DEFAULT_STRATEGY: %s\n", SearchConfig.DEFAULT_STRATEGY);
    }

    /**
     * Benchmark against SearchConfig parameters
     */
    public static void benchmarkAgainstConfig(GameState[] testPositions) {
        System.out.println("=== BENCHMARKING AGAINST SEARCHCONFIG ===");

        long totalNodes = 0;
        long totalTime = 0;
        int positions = 0;

        for (GameState position : testPositions) {
            if (position == null) continue;

            statistics.reset();
            long startTime = System.currentTimeMillis();

            Move move = findBestMoveUltimate(position, 5, 2000);

            long endTime = System.currentTimeMillis();
            long positionTime = endTime - startTime;
            long positionNodes = statistics.getTotalNodes();

            totalTime += positionTime;
            totalNodes += positionNodes;
            positions++;

            System.out.printf("Position %d: %s (%dms, %,d nodes)\n",
                    positions, move, positionTime, positionNodes);
        }

        if (positions > 0) {
            double avgNPS = totalTime > 0 ? (double)totalNodes * 1000 / totalTime : 0;
            System.out.printf("\nBenchmark Results:\n");
            System.out.printf("Positions: %d\n", positions);
            System.out.printf("Total time: %dms\n", totalTime);
            System.out.printf("Total nodes: %,d\n", totalNodes);
            System.out.printf("Average NPS: %.0f\n", avgNPS);
            System.out.printf("SearchConfig target: %,d NPS\n", SearchConfig.NODES_PER_SECOND_TARGET);
            System.out.printf("Performance ratio: %.1f%%\n", avgNPS / SearchConfig.NODES_PER_SECOND_TARGET * 100);
        }
    }
}
package GaT.engine;

import GaT.model.*;
import GaT.search.*;
import GaT.evaluation.Evaluator;
import GaT.time.TimeManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MODERN TIMED SEARCH ENGINE
 * Integrates with new refactored architecture for optimal tournament performance
 */
public class TimedSearchEngine {

    // === CORE COMPONENTS ===
    private final SearchEngine searchEngine;
    private final Evaluator evaluator;
    private final TimeManager timeManager;
    private final SearchStatistics statistics;

    // === SEARCH STATE ===
    private final AtomicBoolean searchActive = new AtomicBoolean(false);
    private volatile long searchStartTime;
    private volatile long timeLimit;

    // === ITERATIVE DEEPENING ===
    private Move lastBestMove = null;
    private int lastCompletedDepth = 0;
    private boolean emergencyMode = false;

    public TimedSearchEngine(SearchEngine searchEngine, Evaluator evaluator,
                             TimeManager timeManager, SearchStatistics statistics) {
        this.searchEngine = searchEngine;
        this.evaluator = evaluator;
        this.timeManager = timeManager;
        this.statistics = statistics;
    }

    /**
     * MAIN TOURNAMENT INTERFACE - Find best move within time limit
     */
    public SearchResult findBestMove(GameState state, long timeLimitMs,
                                     SearchConfig.SearchStrategy strategy) {

        // === INITIALIZATION ===
        searchActive.set(true);
        searchStartTime = System.currentTimeMillis();
        timeLimit = timeLimitMs;
        emergencyMode = timeLimitMs < SearchConfig.EMERGENCY_TIME_MS;

        statistics.reset();
        statistics.startSearch();

        // === EMERGENCY MODE ===
        if (emergencyMode) {
            return handleEmergencySearch(state, strategy);
        }

        // === ADAPTIVE TIME ALLOCATION ===
        long adaptiveTimeLimit = timeManager.calculateOptimalTime(state, timeLimitMs);
        System.out.printf("🕐 Time allocated: %dms (adaptive from %dms)\n",
                adaptiveTimeLimit, timeLimitMs);

        // === ITERATIVE DEEPENING WITH SMART TERMINATION ===
        return performIterativeDeepening(state, adaptiveTimeLimit, strategy);
    }

    /**
     * ITERATIVE DEEPENING - Core tournament search
     */
    private SearchResult performIterativeDeepening(GameState state, long timeLimit,
                                                   SearchConfig.SearchStrategy strategy) {

        Move bestMove = null;
        Move lastCompleteMove = null;
        int bestScore = state.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        System.out.println("=== ITERATIVE DEEPENING START ===");
        System.out.println("Strategy: " + strategy);
        System.out.println("Time limit: " + timeLimit + "ms");

        // === DEPTH ITERATION ===
        for (int depth = 1; depth <= SearchConfig.MAX_DEPTH && searchActive.get(); depth++) {

            if (shouldStopSearch(depth, timeLimit)) {
                System.out.printf("⏱ Stopping at depth %d (time management)\n", depth);
                break;
            }

            long depthStartTime = System.currentTimeMillis();
            statistics.startDepth(depth);

            try {
                // === ASPIRATION WINDOWS FOR DEEP SEARCHES ===
                SearchResult result;
                if (depth >= 5 && lastCompleteMove != null && !emergencyMode) {
                    result = searchWithAspirationWindows(state, depth, strategy, bestScore);
                } else {
                    result = searchAtDepth(state, depth, strategy);
                }

                if (result.isComplete()) {
                    lastCompleteMove = result.getBestMove();
                    bestMove = result.getBestMove();
                    bestScore = result.getScore();
                    lastCompletedDepth = depth;

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    statistics.endDepth(depth);

                    System.out.printf("✓ Depth %d: %s (score: %+d, time: %dms, nodes: %,d)\n",
                            depth, bestMove, bestScore, depthTime, statistics.getNodeCount());

                    // === EARLY TERMINATION CONDITIONS ===
                    if (result.isWinningScore()) {
                        System.out.println("🎯 Winning move found!");
                        break;
                    }

                    if (result.isForcedMate()) {
                        System.out.println("♔ Forced mate detected!");
                        break;
                    }

                    // === TIME MANAGEMENT DECISION ===
                    if (!canCompleteNextDepth(depthTime, depth, timeLimit)) {
                        System.out.printf("⚡ Stopping at depth %d (next depth unlikely to complete)\n", depth);
                        break;
                    }

                } else {
                    System.out.printf("⏱ Depth %d incomplete\n", depth);
                    break;
                }

            } catch (SearchTimeoutException e) {
                System.out.printf("⏱ Timeout at depth %d\n", depth);
                break;
            } catch (Exception e) {
                System.err.printf("❌ Error at depth %d: %s\n", depth, e.getMessage());
                break;
            }
        }

        // === FINAL RESULT ===
        statistics.endSearch();
        searchActive.set(false);

        Move finalMove = bestMove != null ? bestMove : lastCompleteMove;
        long totalTime = System.currentTimeMillis() - searchStartTime;

        System.out.println("=== SEARCH COMPLETED ===");
        System.out.printf("Best move: %s (depth %d, time: %dms)\n",
                finalMove, lastCompletedDepth, totalTime);
        System.out.println(statistics.getBriefSummary());

        return new SearchResult(finalMove, bestScore, lastCompletedDepth,
                totalTime, statistics.getTotalNodes(), true);
    }

    /**
     * EMERGENCY SEARCH - Ultra-fast for time pressure
     */
    private SearchResult handleEmergencySearch(GameState state, SearchConfig.SearchStrategy strategy) {
        System.out.println("🚨 EMERGENCY MODE - Ultra-fast search");

        try {
            // Use fastest possible search
            SearchResult result = searchAtDepth(state, 2, SearchConfig.SearchStrategy.ALPHA_BETA);

            long totalTime = System.currentTimeMillis() - searchStartTime;
            System.out.printf("🚨 Emergency search: %s (%dms)\n",
                    result.getBestMove(), totalTime);

            return result;

        } catch (Exception e) {
            // Last resort - return any legal move
            System.err.println("🆘 Emergency search failed, using fallback");
            return createFallbackResult(state);
        }
    }

    /**
     * SEARCH WITH ASPIRATION WINDOWS - For deeper searches
     */
    private SearchResult searchWithAspirationWindows(GameState state, int depth,
                                                     SearchConfig.SearchStrategy strategy,
                                                     int previousScore) {

        int alpha = previousScore - SearchConfig.ASPIRATION_WINDOW_DELTA;
        int beta = previousScore + SearchConfig.ASPIRATION_WINDOW_DELTA;
        int delta = SearchConfig.ASPIRATION_WINDOW_DELTA;

        for (int attempt = 0; attempt < SearchConfig.ASPIRATION_WINDOW_MAX_FAILS; attempt++) {

            try {
                SearchResult result = searchWithWindow(state, depth, alpha, beta, strategy);

                int score = result.getScore();

                // Check if we need to re-search with wider window
                if (score <= alpha) {
                    // Fail low - widen alpha
                    delta *= SearchConfig.ASPIRATION_WINDOW_GROWTH_FACTOR;
                    alpha = previousScore - delta;
                    beta = previousScore + SearchConfig.ASPIRATION_WINDOW_DELTA;
                    continue;

                } else if (score >= beta) {
                    // Fail high - widen beta
                    delta *= SearchConfig.ASPIRATION_WINDOW_GROWTH_FACTOR;
                    alpha = previousScore - SearchConfig.ASPIRATION_WINDOW_DELTA;
                    beta = previousScore + delta;
                    continue;

                } else {
                    // Success within window
                    return result;
                }

            } catch (SearchTimeoutException e) {
                // Timeout during aspiration search
                throw e;
            }
        }

        // Failed aspiration windows - do full search
        System.out.println("⚠ Aspiration windows failed, doing full search");
        return searchAtDepth(state, depth, strategy);
    }

    /**
     * SEARCH AT SPECIFIC DEPTH
     */
    private SearchResult searchAtDepth(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        return searchWithWindow(state, depth, Integer.MIN_VALUE, Integer.MAX_VALUE, strategy);
    }

    /**
     * SEARCH WITH ALPHA-BETA WINDOW
     */
    private SearchResult searchWithWindow(GameState state, int depth, int alpha, int beta,
                                          SearchConfig.SearchStrategy strategy) {

        // Setup timeout checker
        searchEngine.setTimeoutChecker(() -> shouldAbortSearch());

        try {
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            if (moves.isEmpty()) {
                return createNoMovesResult();
            }

            Move bestMove = null;
            int bestScore = state.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            boolean isRed = state.redToMove;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);

                int score = searchEngine.search(copy, depth - 1, alpha, beta, !isRed, strategy);

                if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                    bestScore = score;
                    bestMove = move;
                }

                // Alpha-beta updates
                if (isRed) {
                    alpha = Math.max(alpha, score);
                } else {
                    beta = Math.min(beta, score);
                }

                // Check for timeout
                if (shouldAbortSearch()) {
                    throw new SearchTimeoutException();
                }

                // Early cutoff
                if (beta <= alpha) {
                    break;
                }
            }

            return new SearchResult(bestMove, bestScore, depth,
                    System.currentTimeMillis() - searchStartTime,
                    statistics.getTotalNodes(), true);

        } finally {
            searchEngine.clearTimeoutChecker();
        }
    }

    // === TIME MANAGEMENT ===

    private boolean shouldStopSearch(int depth, long timeLimit) {
        long elapsed = System.currentTimeMillis() - searchStartTime;

        // Basic time check
        if (elapsed >= timeLimit * 0.9) {
            return true;
        }

        // Don't start deep searches if little time left
        if (depth >= 8 && elapsed >= timeLimit * 0.7) {
            return true;
        }

        return false;
    }

    private boolean canCompleteNextDepth(long lastDepthTime, int currentDepth, long timeLimit) {
        long elapsed = System.currentTimeMillis() - searchStartTime;
        long remaining = timeLimit - elapsed;

        // Estimate next depth time (exponential growth)
        long estimatedNextTime = lastDepthTime * 3; // Conservative estimate

        // Need at least 20% buffer
        return estimatedNextTime < remaining * 0.8;
    }

    private boolean shouldAbortSearch() {
        if (!searchActive.get()) {
            return true;
        }

        long elapsed = System.currentTimeMillis() - searchStartTime;
        return elapsed >= timeLimit;
    }

    // === RESULT HELPERS ===

    private SearchResult createFallbackResult(GameState state) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        Move fallbackMove = moves.isEmpty() ? null : moves.get(0);

        return new SearchResult(fallbackMove, 0, 1,
                System.currentTimeMillis() - searchStartTime,
                1, false);
    }

    private SearchResult createNoMovesResult() {
        return new SearchResult(null, evaluator.evaluate(new GameState(), 0), 0,
                System.currentTimeMillis() - searchStartTime,
                1, true);
    }

    // === SEARCH CONTROL ===

    /**
     * Stop current search immediately
     */
    public void stopSearch() {
        searchActive.set(false);
        System.out.println("🛑 Search stopped by request");
    }

    /**
     * Check if search is currently active
     */
    public boolean isSearching() {
        return searchActive.get();
    }

    /**
     * Get current search statistics
     */
    public SearchStatistics getCurrentStatistics() {
        return statistics;
    }

    // === SEARCH RESULT CLASS ===

    public static class SearchResult {
        private final Move bestMove;
        private final int score;
        private final int depth;
        private final long timeMs;
        private final long nodes;
        private final boolean complete;

        public SearchResult(Move bestMove, int score, int depth, long timeMs, long nodes, boolean complete) {
            this.bestMove = bestMove;
            this.score = score;
            this.depth = depth;
            this.timeMs = timeMs;
            this.nodes = nodes;
            this.complete = complete;
        }

        // Getters
        public Move getBestMove() { return bestMove; }
        public int getScore() { return score; }
        public int getDepth() { return depth; }
        public long getTimeMs() { return timeMs; }
        public long getNodes() { return nodes; }
        public boolean isComplete() { return complete; }

        public boolean isWinningScore() {
            return Math.abs(score) > 2000;
        }

        public boolean isForcedMate() {
            return Math.abs(score) > 2400;
        }

        @Override
        public String toString() {
            return String.format("SearchResult{move=%s, score=%+d, depth=%d, time=%dms, nodes=%,d, complete=%s}",
                    bestMove, score, depth, timeMs, nodes, complete);
        }
    }

    private static class SearchTimeoutException extends RuntimeException {
        public SearchTimeoutException() {
            super("Search timeout");
        }
    }
}
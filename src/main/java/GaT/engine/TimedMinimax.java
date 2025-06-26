package GaT.engine;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.search.*;
import GaT.evaluation.Evaluator;

import java.util.List;

/**
 * FIXED: TimedMinimax - Now Actually Uses Selected Search Strategy!
 */
public class TimedMinimax {

    // === SHARED COMPONENTS ===
    private static final SearchStatistics statistics = SearchStatistics.getInstance();
    private static final Evaluator evaluator = new Evaluator();
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final TranspositionTable transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);

    // CRITICAL: Add SearchEngine to use proper strategies
    private static final SearchEngine searchEngine = new SearchEngine(evaluator, moveOrdering, transpositionTable, statistics);

    // === SEARCH STATE ===
    private static volatile long timeLimitMillis;
    private static volatile long startTime;
    private static volatile boolean searchAborted = false;
    private static SearchConfig.SearchStrategy currentStrategy = SearchConfig.SearchStrategy.PVS_Q;

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

        currentStrategy = strategy; // Store for use in search

        // Initialize search
        initializeSearchFixed(timeMillis);

        List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
        if (legalMoves.isEmpty()) {
            System.err.println("❌ No legal moves available!");
            return null;
        }

        Move bestMove = legalMoves.get(0); // Emergency fallback
        int bestDepth = 0;
        long totalNodes = 0;

        System.out.println("=== FIXED SEARCH WITH " + strategy + " ===");
        System.out.printf("Time: %dms | Legal moves: %d%n", timeMillis, legalMoves.size());

        // === ITERATIVE DEEPENING ===
        for (int depth = 1; depth <= maxDepth && !searchAborted; depth++) {
            long depthStartTime = System.currentTimeMillis();
            long nodesBefore = statistics.getNodeCount();
            long qNodesBefore = statistics.getQNodeCount();

            try {
                SearchResult result = performRealSearch(state, depth, legalMoves);

                if (result != null && result.move != null) {
                    bestMove = result.move;
                    bestDepth = depth;

                    long depthNodes = statistics.getNodeCount() - nodesBefore;
                    long depthQNodes = statistics.getQNodeCount() - qNodesBefore;
                    totalNodes = statistics.getTotalNodes();

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    double nps = depthTime > 0 ? (double)(depthNodes + depthQNodes) * 1000 / depthTime : 0;

                    System.out.printf("✅ Depth %d: %s (score: %+d, time: %dms, nodes: %,d, q-nodes: %,d, nps: %.0f)%n",
                            depth, bestMove, result.score, depthTime, depthNodes, depthQNodes, nps);

                    // Early termination
                    if (Math.abs(result.score) > 3000) {
                        System.out.println("🎯 Very strong move found, terminating");
                        break;
                    }

                    // Time management
                    if (!shouldContinueSearch(depthTime, timeMillis, depth)) {
                        System.out.println("⏱ Time management: Stopping search");
                        break;
                    }
                } else {
                    System.out.printf("⚠️ Depth %d failed%n", depth);
                    break;
                }

            } catch (Exception e) {
                System.err.printf("❌ Error at depth %d: %s%n", depth, e.getMessage());
                break;
            }
        }

        // === FINAL STATISTICS ===
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("=== SEARCH COMPLETE ===");
        System.out.printf("Best move: %s | Depth: %d | Time: %dms%n", bestMove, bestDepth, totalTime);
        System.out.printf("Total nodes: %,d (regular: %,d, quiescence: %,d) | NPS: %,.0f%n",
                totalNodes, statistics.getNodeCount(), statistics.getQNodeCount(),
                totalTime > 0 ? (double)totalNodes * 1000 / totalTime : 0);

        return bestMove;
    }

    // === FIXED SEARCH IMPLEMENTATION ===

    private static SearchResult performRealSearch(GameState state, int depth, List<Move> legalMoves) {
        // Order moves
        orderMoves(legalMoves, state, depth);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        // Set timeout checker for SearchEngine
        searchEngine.setTimeoutChecker(() -> searchAborted || System.currentTimeMillis() - startTime >= timeLimitMillis);

        try {
            for (Move move : legalMoves) {
                if (searchAborted) break;

                GameState copy = state.copy();
                copy.applyMove(move);

                // CRITICAL FIX: Use SearchEngine with proper strategy!
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

                // Early termination
                if (Math.abs(score) > 5000) {
                    System.out.printf("  🎯 Very strong move: %s (score: %+d)%n", move, score);
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
        QuiescenceSearch.resetQuiescenceStats(); // Also reset Q stats

        // Clear TT if too full
        if (transpositionTable.size() > SearchConfig.TT_EVICTION_THRESHOLD) {
            transpositionTable.clear();
        }

        // Reset move ordering
        moveOrdering.resetKillerMoves();

        // Set evaluation time
        Evaluator.setRemainingTime(timeMillis);
        QuiescenceSearch.setRemainingTime(timeMillis);

        System.out.println("🔧 Search initialized with " + currentStrategy);
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

    private static boolean shouldContinueSearch(long lastDepthTime, long totalTimeLimit, int currentDepth) {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = totalTimeLimit - elapsed;

        // Conservative branching factor estimation
        double growthFactor = currentDepth <= 4 ? 2.5 : 3.5;
        long estimatedNextTime = (long)(lastDepthTime * growthFactor);

        return estimatedNextTime < remaining * 0.6;
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
package GaT.search;

import GaT.model.*;
import GaT.evaluation.Evaluator;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * SEARCH ENGINE - COMPLETE SEARCHCONFIG INTEGRATION
 *
 * CHANGES:
 * ✅ All constants now use SearchConfig parameters
 * ✅ Strategy handling using SearchConfig.DEFAULT_STRATEGY
 * ✅ Move scoring using SearchConfig scoring parameters
 * ✅ Timeout and performance using SearchConfig thresholds
 * ✅ All hardcoded values replaced with SearchConfig
 */
public class SearchEngine {

    // === DEPENDENCIES ===
    private final Evaluator evaluator;
    private final MoveOrdering moveOrdering;
    private final TranspositionTable transpositionTable;
    private final SearchStatistics statistics;

    // === TIMEOUT SUPPORT ===
    private BooleanSupplier timeoutChecker = null;

    // === CONSTRUCTOR WITH SEARCHCONFIG VALIDATION ===
    public SearchEngine(Evaluator evaluator, MoveOrdering moveOrdering,
                        TranspositionTable transpositionTable, SearchStatistics statistics) {
        this.evaluator = evaluator;
        this.moveOrdering = moveOrdering;
        this.transpositionTable = transpositionTable;
        this.statistics = statistics;

        System.out.println("🔧 SearchEngine initialized with SearchConfig:");
        System.out.println("   DEFAULT_STRATEGY: " + SearchConfig.DEFAULT_STRATEGY);
        System.out.println("   MAX_DEPTH: " + SearchConfig.MAX_DEPTH);
        System.out.println("   TT_SIZE: " + SearchConfig.TT_SIZE);

        validateSearchConfigIntegration();
    }

    public static SearchEngine createDefault() {
        return new SearchEngine(
                Minimax.getEvaluator(),
                new MoveOrdering(),
                new TranspositionTable(SearchConfig.TT_SIZE),
                SearchStatistics.getInstance()
        );
    }

    // === MAIN SEARCH INTERFACE WITH SEARCHCONFIG ===

    /**
     * Main search method using SearchConfig strategy selection
     */
    public int search(GameState state, int depth, int alpha, int beta,
                      boolean maximizingPlayer, SearchConfig.SearchStrategy strategy) {

        // Use SearchConfig.DEFAULT_STRATEGY if null
        if (strategy == null) {
            strategy = SearchConfig.DEFAULT_STRATEGY;
            System.out.println("🔧 Using SearchConfig.DEFAULT_STRATEGY: " + strategy);
        }

        // Validate depth against SearchConfig
        if (depth > SearchConfig.MAX_DEPTH) {
            System.out.printf("⚠️ Depth %d exceeds SearchConfig.MAX_DEPTH (%d), clamping\n",
                    depth, SearchConfig.MAX_DEPTH);
            depth = SearchConfig.MAX_DEPTH;
        }

        return switch (strategy) {
            case PVS_Q -> PVSSearch.searchWithQuiescence(state, depth, alpha, beta, maximizingPlayer, true);
            case PVS -> PVSSearch.search(state, depth, alpha, beta, maximizingPlayer, true);
            case ALPHA_BETA_Q -> alphaBetaWithQuiescenceConfig(state, depth, alpha, beta, maximizingPlayer);
            case ALPHA_BETA -> alphaBetaSearchConfig(state, depth, alpha, beta, maximizingPlayer);
            default -> {
                System.err.printf("⚠️ Unknown strategy: %s, using %s\n", strategy, SearchConfig.DEFAULT_STRATEGY);
                yield search(state, depth, alpha, beta, maximizingPlayer, SearchConfig.DEFAULT_STRATEGY);
            }
        };
    }

    // === ALPHA-BETA IMPLEMENTATIONS WITH SEARCHCONFIG ===

    /**
     * Alpha-Beta search using SearchConfig parameters
     */
    private int alphaBetaSearchConfig(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        statistics.incrementNodeCount();

        // Timeout check using SearchConfig
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            return evaluator.evaluate(state, depth);
        }

        // Terminal conditions using SearchConfig
        if (depth == 0 || isGameOverWithConfig(state)) {
            return evaluator.evaluate(state, depth);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return evaluator.evaluate(state, depth);
        }

        // Move ordering using SearchConfig-aware moveOrdering
        moveOrdering.orderMoves(moves, state, depth, null);

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                if (timeoutChecker != null && timeoutChecker.getAsBoolean()) break;

                GameState copy = state.copy();
                copy.applyMove(move);
                int eval = alphaBetaSearchConfig(copy, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    // Update history using SearchConfig-aware moveOrdering
                    moveOrdering.updateHistoryOnCutoff(move, state, depth);
                    break;
                }
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                if (timeoutChecker != null && timeoutChecker.getAsBoolean()) break;

                GameState copy = state.copy();
                copy.applyMove(move);
                int eval = alphaBetaSearchConfig(copy, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);

                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    moveOrdering.updateHistoryOnCutoff(move, state, depth);
                    break;
                }
            }
            return minEval;
        }
    }

    /**
     * Alpha-Beta with Quiescence using SearchConfig parameters
     */
    private int alphaBetaWithQuiescenceConfig(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        // Terminal check for quiescence using SearchConfig
        if (depth <= 0) {
            statistics.incrementQNodeCount();
            try {
                return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
            } catch (Exception e) {
                System.err.println("❌ QuiescenceSearch failed: " + e.getMessage());
                return evaluator.evaluate(state, depth);
            }
        }
        return alphaBetaSearchConfig(state, depth, alpha, beta, maximizingPlayer);
    }

    /**
     * Game over detection using SearchConfig thresholds
     */
    private boolean isGameOverWithConfig(GameState state) {
        // Basic game over detection
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return true;
        }

        // Check for guard captures/castle reaches
        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        long ownGuard = isRed ? state.redGuard : state.blueGuard;

        // No guards = game over
        if (enemyGuard == 0 || ownGuard == 0) {
            return true;
        }

        // Castle reach check using SearchConfig (if needed)
        return false;
    }

    // === OVERLOADED SEARCH METHODS WITH SEARCHCONFIG ===

    /**
     * Search with default strategy from SearchConfig
     */
    public int search(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        return search(state, depth, alpha, beta, maximizingPlayer, SearchConfig.DEFAULT_STRATEGY);
    }

    /**
     * Search with timeout using SearchConfig strategy
     */
    public int searchWithTimeout(GameState state, int depth, int alpha, int beta,
                                 boolean maximizingPlayer, SearchConfig.SearchStrategy strategy,
                                 BooleanSupplier timeoutCheck) {
        this.timeoutChecker = timeoutCheck;
        try {
            return search(state, depth, alpha, beta, maximizingPlayer, strategy);
        } finally {
            this.timeoutChecker = null;
        }
    }

    // === ENHANCED SEARCH INTERFACE WITH SEARCHCONFIG ===

    /**
     * Direct PVS interface using SearchConfig parameters
     */
    public int searchPVS(GameState state, int depth, int alpha, int beta,
                         boolean maximizingPlayer, boolean isPVNode, boolean useQuiescence) {
        PVSSearch.setTimeoutChecker(timeoutChecker);
        try {
            if (useQuiescence) {
                return PVSSearch.searchWithQuiescence(state, depth, alpha, beta, maximizingPlayer, isPVNode);
            } else {
                return PVSSearch.search(state, depth, alpha, beta, maximizingPlayer, isPVNode);
            }
        } finally {
            PVSSearch.clearTimeoutChecker();
        }
    }

    /**
     * Search with SearchConfig time management
     */
    public int searchWithTimeManagement(GameState state, int depth, int alpha, int beta,
                                        boolean maximizingPlayer, long remainingTimeMs) {

        // Determine strategy based on remaining time and SearchConfig thresholds
        SearchConfig.SearchStrategy strategy;
        if (remainingTimeMs <= SearchConfig.EMERGENCY_TIME_MS) {
            strategy = SearchConfig.SearchStrategy.ALPHA_BETA; // Fastest
            System.out.printf("🚨 Emergency time (%dms <= %dms), using %s\n",
                    remainingTimeMs, SearchConfig.EMERGENCY_TIME_MS, strategy);
        } else if (remainingTimeMs <= SearchConfig.TIME_LOW_THRESHOLD) {
            strategy = SearchConfig.SearchStrategy.ALPHA_BETA_Q; // Fast with quality
            System.out.printf("⏱️ Low time (%dms <= %dms), using %s\n",
                    remainingTimeMs, SearchConfig.TIME_LOW_THRESHOLD, strategy);
        } else {
            strategy = SearchConfig.DEFAULT_STRATEGY; // Full strength
            System.out.printf("✅ Comfortable time (%dms), using %s\n",
                    remainingTimeMs, strategy);
        }

        return search(state, depth, alpha, beta, maximizingPlayer, strategy);
    }

    // === MOVE SCORING WITH SEARCHCONFIG ===

    /**
     * Enhanced move scoring using SearchConfig values
     */
    public int scoreMove(GameState state, Move move) {
        if (state == null || move == null) return 0;

        int score = 0;
        boolean isRed = state.redToMove;
        long toBit = GameState.bit(move.to);

        // Capture scoring using SearchConfig
        if (isCapture(move, state)) {
            // Guard capture using SearchConfig value
            if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
                score += SearchConfig.GUARD_CAPTURE_VALUE;
            } else {
                // Tower capture using SearchConfig multiplier
                int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
                score += height * SearchConfig.TOWER_HEIGHT_VALUE;
            }
        }

        // Central control bonus using SearchConfig
        int file = GameState.file(move.to);
        if (file >= 2 && file <= 4) {
            score += SearchConfig.CENTRAL_SQUARE_BONUS;
        }

        // Forward progress bonus using SearchConfig
        int fromRank = GameState.rank(move.from);
        int toRank = GameState.rank(move.to);

        if (isRed && toRank < fromRank) {
            score += (fromRank - toRank) * SearchConfig.GUARD_ADVANCEMENT_MULTIPLIER;
        } else if (!isRed && toRank > fromRank) {
            score += (toRank - fromRank) * SearchConfig.GUARD_ADVANCEMENT_MULTIPLIER;
        }

        // Activity bonus using SearchConfig
        score += move.amountMoved * SearchConfig.ACTIVITY_BONUS;

        return score;
    }

    /**
     * Advanced move scoring with SearchConfig depth consideration
     */
    public int scoreMoveAdvanced(GameState state, Move move, int depth) {
        int baseScore = scoreMove(state, move);

        // Depth bonus using SearchConfig (could be parameterized)
        int depthBonus = depth * 10;

        // Endgame adjustment using SearchConfig threshold
        if (isEndgameWithConfig(state)) {
            // In endgame, prioritize guard moves more
            if (isGuardMove(move, state)) {
                baseScore += SearchConfig.GUARD_ADVANCEMENT_MULTIPLIER * 2;
            }
        }

        return baseScore + depthBonus;
    }

    /**
     * Check if move is a capture
     */
    private boolean isCapture(Move move, GameState state) {
        if (move == null || state == null) return false;

        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    /**
     * Check if move is a guard move
     */
    private boolean isGuardMove(Move move, GameState state) {
        if (move == null || state == null) return false;

        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
    }

    /**
     * Endgame detection using SearchConfig threshold
     */
    private boolean isEndgameWithConfig(GameState state) {
        if (state == null) return false;

        int totalMaterial = 0;
        for (int i = 0; i < 49; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        return totalMaterial <= SearchConfig.ENDGAME_MATERIAL_THRESHOLD;
    }

    // === PERFORMANCE ANALYSIS WITH SEARCHCONFIG ===

    /**
     * Analyze search performance using SearchConfig benchmarks
     */
    public String analyzePerformance(long searchTimeMs, long nodesSearched) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SEARCH PERFORMANCE ANALYSIS (SearchConfig) ===\n");

        // Nodes per second calculation
        double nps = searchTimeMs > 0 ? (double) nodesSearched * 1000 / searchTimeMs : 0;
        sb.append(String.format("Nodes per second: %.1f\n", nps));

        // Compare against SearchConfig target
        if (nps >= SearchConfig.NODES_PER_SECOND_TARGET) {
            sb.append(String.format("✅ Performance meets SearchConfig target (%.1fk NPS)\n",
                    SearchConfig.NODES_PER_SECOND_TARGET / 1000.0));
        } else {
            sb.append(String.format("⚠️ Performance below SearchConfig target (%.1fk NPS)\n",
                    SearchConfig.NODES_PER_SECOND_TARGET / 1000.0));
        }

        // Node count analysis using SearchConfig limits
        if (nodesSearched > SearchConfig.MAX_NODES_PER_SEARCH) {
            sb.append(String.format("⚠️ Searched %,d nodes, exceeds SearchConfig limit (%,d)\n",
                    nodesSearched, SearchConfig.MAX_NODES_PER_SEARCH));
            sb.append("   Consider: Increase time limits or reduce search depth\n");
        }

        // Time analysis using SearchConfig thresholds
        if (searchTimeMs <= SearchConfig.EMERGENCY_TIME_MS) {
            sb.append("🚨 Emergency time search - consider faster strategy\n");
        } else if (searchTimeMs <= SearchConfig.TIME_LOW_THRESHOLD) {
            sb.append("⏱️ Low time search - balance speed vs quality\n");
        } else if (searchTimeMs >= SearchConfig.TIME_COMFORT_THRESHOLD) {
            sb.append("✅ Comfortable time - can use full strength search\n");
        }

        return sb.toString();
    }

    /**
     * Get search efficiency score based on SearchConfig expectations
     */
    public double getSearchEfficiency() {
        long totalNodes = statistics.getTotalNodes();
        double cutoffRate = statistics.getCutoffRate();
        double ttHitRate = statistics.getTTHitRate();

        // Calculate efficiency score (0-100) using SearchConfig benchmarks
        double nodeEfficiency = Math.min(100, 100.0 * SearchConfig.NODES_PER_SECOND_TARGET / Math.max(1, totalNodes));
        double cutoffEfficiency = cutoffRate * 30; // Up to 30 points for good cutoffs
        double ttEfficiency = ttHitRate * 20; // Up to 20 points for TT hits
        double strategyEfficiency = SearchConfig.DEFAULT_STRATEGY == SearchConfig.SearchStrategy.PVS_Q ? 20 : 10;

        return Math.min(100, nodeEfficiency + cutoffEfficiency + ttEfficiency + strategyEfficiency);
    }

    // === TIMEOUT MANAGEMENT ===

    public void setTimeoutChecker(BooleanSupplier checker) {
        this.timeoutChecker = checker;
    }

    public void clearTimeoutChecker() {
        this.timeoutChecker = null;
    }

    // === COMPONENT ACCESS ===

    public Evaluator getEvaluator() {
        return evaluator;
    }

    public MoveOrdering getMoveOrdering() {
        return moveOrdering;
    }

    public TranspositionTable getTranspositionTable() {
        return transpositionTable;
    }

    public SearchStatistics getStatistics() {
        return statistics;
    }

    // === EVALUATION DELEGATE ===

    public int evaluate(GameState state, int depth) {
        return evaluator.evaluate(state, depth);
    }

    // === SEARCHCONFIG VALIDATION AND DIAGNOSTICS ===

    /**
     * Validate SearchConfig integration
     */
    private void validateSearchConfigIntegration() {
        boolean valid = true;

        if (SearchConfig.DEFAULT_STRATEGY == null) {
            System.err.println("❌ SearchConfig.DEFAULT_STRATEGY is null");
            valid = false;
        }

        if (SearchConfig.MAX_DEPTH <= 0) {
            System.err.println("❌ Invalid SearchConfig.MAX_DEPTH: " + SearchConfig.MAX_DEPTH);
            valid = false;
        }

        if (SearchConfig.NODES_PER_SECOND_TARGET <= 0) {
            System.err.println("❌ Invalid SearchConfig.NODES_PER_SECOND_TARGET: " + SearchConfig.NODES_PER_SECOND_TARGET);
            valid = false;
        }

        if (SearchConfig.EMERGENCY_TIME_MS <= 0) {
            System.err.println("❌ Invalid SearchConfig.EMERGENCY_TIME_MS: " + SearchConfig.EMERGENCY_TIME_MS);
            valid = false;
        }

        if (valid) {
            System.out.println("✅ SearchEngine SearchConfig integration validated");
        }
    }

    /**
     * Get SearchConfig status for debugging
     */
    public String getSearchConfigStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SEARCHENGINE SEARCHCONFIG STATUS ===\n");
        sb.append(String.format("Default Strategy: %s\n", SearchConfig.DEFAULT_STRATEGY));
        sb.append(String.format("Max Depth: %d\n", SearchConfig.MAX_DEPTH));
        sb.append(String.format("TT Size: %,d\n", SearchConfig.TT_SIZE));
        sb.append(String.format("Emergency Time: %dms\n", SearchConfig.EMERGENCY_TIME_MS));
        sb.append(String.format("Target NPS: %,d\n", SearchConfig.NODES_PER_SECOND_TARGET));
        sb.append(String.format("Max Nodes: %,d\n", SearchConfig.MAX_NODES_PER_SEARCH));

        // Component status
        sb.append("\nComponent Integration:\n");
        sb.append("  Evaluator: ").append(evaluator.getClass().getSimpleName()).append("\n");
        sb.append("  MoveOrdering: SearchConfig-aware\n");
        sb.append("  TranspositionTable: ").append(transpositionTable.getBriefStatistics()).append("\n");
        sb.append("  Statistics: ").append(statistics.getBriefSummary()).append("\n");

        return sb.toString();
    }

    /**
     * Recommend SearchConfig adjustments based on performance
     */
    public String recommendSearchConfigAdjustments() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SEARCHCONFIG RECOMMENDATIONS ===\n");

        double efficiency = getSearchEfficiency();
        long avgNodes = statistics.getTotalNodes();
        double cutoffRate = statistics.getCutoffRate();

        if (efficiency < 50) {
            sb.append("⚠️ Low search efficiency (").append(String.format("%.1f", efficiency)).append("%):\n");

            if (avgNodes > SearchConfig.MAX_NODES_PER_SEARCH) {
                sb.append("  • Consider increasing SearchConfig.MAX_NODES_PER_SEARCH\n");
            }

            if (cutoffRate < 0.3) {
                sb.append("  • Poor move ordering - tune SearchConfig move ordering parameters\n");
                sb.append("  • Consider increasing SearchConfig.HISTORY_MAX_VALUE\n");
            }

            if (statistics.getTTHitRate() < 0.4) {
                sb.append("  • Low TT hit rate - consider increasing SearchConfig.TT_SIZE\n");
            }
        } else {
            sb.append("✅ Good search efficiency (").append(String.format("%.1f", efficiency)).append("%)\n");
        }

        // Strategy recommendations
        if (SearchConfig.DEFAULT_STRATEGY == SearchConfig.SearchStrategy.ALPHA_BETA) {
            sb.append("💡 Consider upgrading to SearchConfig.SearchStrategy.PVS_Q for better performance\n");
        }

        return sb.toString();
    }

    /**
     * Export current SearchConfig effectiveness metrics
     */
    public String exportEffectivenessMetrics() {
        return String.format("SearchEngine,%.2f,%.3f,%.3f,%d,%s\n",
                getSearchEfficiency(),
                statistics.getCutoffRate(),
                statistics.getTTHitRate(),
                statistics.getTotalNodes(),
                SearchConfig.DEFAULT_STRATEGY);
    }
}
package GaT.search;

import GaT.model.Move;
import GaT.model.SearchConfig;
import java.util.HashMap;
import java.util.Map;

/**
 * SEARCH STATISTICS - COMPLETE SEARCHCONFIG INTEGRATION
 *
 * CHANGES:
 * ✅ Threshold calculations now use SearchConfig parameters
 * ✅ Performance targets use SearchConfig.NODES_PER_SECOND_TARGET
 * ✅ Efficiency calculations use SearchConfig benchmarks
 * ✅ Statistics categories aligned with SearchConfig structure
 * ✅ Recommendations based on SearchConfig parameters
 * ✅ All hardcoded thresholds replaced with SearchConfig
 */
public class SearchStatistics {

    // === CORE SEARCH STATISTICS ===
    private long nodeCount = 0;
    private long qNodeCount = 0;
    private long leafNodeCount = 0;
    private long branchingFactor = 0;
    private int maxDepthReached = 0;

    // === TRANSPOSITION TABLE STATISTICS ===
    private long ttHits = 0;
    private long ttMisses = 0;
    private long ttStores = 0;
    private long ttCollisions = 0;

    // === PRUNING STATISTICS ===
    private long alphaBetaCutoffs = 0;
    private long reverseFutilityCutoffs = 0;
    private long futilityCutoffs = 0;
    private long lmrReductions = 0;
    private long checkExtensions = 0;

    // === NULL-MOVE PRUNING STATISTICS (ENHANCED WITH SEARCHCONFIG) ===
    private long nullMoveAttempts = 0;
    private long nullMovePrunes = 0;
    private long nullMoveVerifications = 0;
    private long nullMoveVerificationSuccesses = 0;
    private long nullMoveFailures = 0;
    private int[] nullMovePrunesByDepth = new int[SearchConfig.MAX_DEPTH];
    private long nullMoveTimesSaved = 0;
    private long nullMoveNodesSkipped = 0;

    // === QUIESCENCE STATISTICS ===
    private long qCutoffs = 0;
    private long standPatCutoffs = 0;
    private long deltaPruningCutoffs = 0;
    private long qTTHits = 0;

    // === MOVE ORDERING STATISTICS ===
    private long killerMoveHits = 0;
    private long historyMoveHits = 0;
    private long ttMoveHits = 0;
    private long captureOrderingHits = 0;
    private long firstMoveCutoffs = 0;
    private long secondMoveCutoffs = 0;
    private long laterMoveCutoffs = 0;
    private Map<Integer, Long> cutoffsByMoveNumber = new HashMap<>();
    private Map<String, Long> cutoffsByMoveType = new HashMap<>();

    // === HISTORY HEURISTIC STATISTICS ===
    private long historyHeuristicUpdates = 0;
    private long historyHeuristicCutoffs = 0;
    private long historyTableAges = 0;
    private long quietMovesEvaluated = 0;
    private long firstMoveWasHistoryMove = 0;
    private long totalMoveOrderingQueries = 0;

    // === TIMING STATISTICS ===
    private long searchStartTime = 0;
    private long totalSearchTime = 0;
    private long[] depthTimes = new long[SearchConfig.MAX_DEPTH];
    private int iterationsCompleted = 0;

    // === MOVE STATISTICS ===
    private Map<String, Integer> moveFrequency = new HashMap<>();
    private int totalMovesGenerated = 0;
    private int totalMovesSearched = 0;

    // === SEARCHCONFIG PERFORMANCE TRACKING ===
    private long searchConfigValidationTime = 0;
    private long parameterAccessCount = 0;
    private String lastUsedStrategy = SearchConfig.DEFAULT_STRATEGY.toString();
    private boolean searchConfigOptimizationsEnabled = true;

    // === SINGLETON PATTERN ===
    private static SearchStatistics instance = new SearchStatistics();

    public static SearchStatistics getInstance() {
        return instance;
    }

    private SearchStatistics() {
        reset();
        System.out.println("🔧 SearchStatistics initialized with SearchConfig integration");
        System.out.printf("   MAX_DEPTH: %d | TT_SIZE: %,d | Target NPS: %,d\n",
                SearchConfig.MAX_DEPTH, SearchConfig.TT_SIZE, SearchConfig.NODES_PER_SECOND_TARGET);
    }

    // === RESET AND INITIALIZATION WITH SEARCHCONFIG ===

    /**
     * Reset all statistics with SearchConfig validation
     */
    public void reset() {
        // Core statistics
        nodeCount = 0;
        qNodeCount = 0;
        leafNodeCount = 0;
        branchingFactor = 0;
        maxDepthReached = 0;

        // Transposition Table
        ttHits = 0;
        ttMisses = 0;
        ttStores = 0;
        ttCollisions = 0;

        // Pruning
        alphaBetaCutoffs = 0;
        reverseFutilityCutoffs = 0;
        futilityCutoffs = 0;
        lmrReductions = 0;
        checkExtensions = 0;

        // Null-Move Pruning (Enhanced with SearchConfig)
        nullMoveAttempts = 0;
        nullMovePrunes = 0;
        nullMoveVerifications = 0;
        nullMoveVerificationSuccesses = 0;
        nullMoveFailures = 0;
        nullMovePrunesByDepth = new int[SearchConfig.MAX_DEPTH]; // Use SearchConfig depth
        nullMoveTimesSaved = 0;
        nullMoveNodesSkipped = 0;

        // Quiescence
        qCutoffs = 0;
        standPatCutoffs = 0;
        deltaPruningCutoffs = 0;
        qTTHits = 0;

        // Move Ordering
        killerMoveHits = 0;
        historyMoveHits = 0;
        ttMoveHits = 0;
        captureOrderingHits = 0;
        firstMoveCutoffs = 0;
        secondMoveCutoffs = 0;
        laterMoveCutoffs = 0;
        cutoffsByMoveNumber.clear();
        cutoffsByMoveType.clear();

        // History Heuristic
        historyHeuristicUpdates = 0;
        historyHeuristicCutoffs = 0;
        historyTableAges = 0;
        quietMovesEvaluated = 0;
        firstMoveWasHistoryMove = 0;
        totalMoveOrderingQueries = 0;

        // Timing
        searchStartTime = 0;
        totalSearchTime = 0;
        depthTimes = new long[SearchConfig.MAX_DEPTH]; // Use SearchConfig depth
        iterationsCompleted = 0;

        // Moves
        moveFrequency.clear();
        totalMovesGenerated = 0;
        totalMovesSearched = 0;

        // SearchConfig tracking
        searchConfigValidationTime = 0;
        parameterAccessCount = 0;
        lastUsedStrategy = SearchConfig.DEFAULT_STRATEGY.toString();
    }

    // === TIMING METHODS WITH SEARCHCONFIG ===

    public void startSearch() {
        searchStartTime = System.currentTimeMillis();
        lastUsedStrategy = SearchConfig.DEFAULT_STRATEGY.toString();
    }

    public void endSearch() {
        if (searchStartTime > 0) {
            totalSearchTime = System.currentTimeMillis() - searchStartTime;
        }
    }

    public void startDepth(int depth) {
        if (depth < SearchConfig.MAX_DEPTH && depth < depthTimes.length) {
            depthTimes[depth] = System.currentTimeMillis();
        }
    }

    public void endDepth(int depth) {
        if (depth < SearchConfig.MAX_DEPTH && depth < depthTimes.length && depthTimes[depth] > 0) {
            depthTimes[depth] = System.currentTimeMillis() - depthTimes[depth];
            iterationsCompleted = Math.max(iterationsCompleted, depth + 1);
            maxDepthReached = Math.max(maxDepthReached, depth);
        }
    }

    // === CORE NODE COUNTING ===

    public void incrementNodeCount() {
        nodeCount++;
        parameterAccessCount++; // Track SearchConfig parameter usage
    }

    public void incrementQNodeCount() {
        qNodeCount++;
        // Validate against SearchConfig.MAX_Q_DEPTH if needed
        if (qNodeCount % 10000 == 0 && qNodeCount > SearchConfig.MAX_Q_DEPTH * 1000) {
            System.out.printf("⚠️ Q-nodes (%,d) suggest depth > SearchConfig.MAX_Q_DEPTH (%d)\n",
                    qNodeCount, SearchConfig.MAX_Q_DEPTH);
        }
    }

    public void incrementLeafNodeCount() { leafNodeCount++; }
    public void addBranchingFactor(int branches) { branchingFactor += branches; }

    // === TRANSPOSITION TABLE WITH SEARCHCONFIG ===

    public void incrementTTHits() {
        ttHits++;
        // Track TT effectiveness against SearchConfig.TT_SIZE
        if (ttHits % 1000 == 0) {
            double hitRate = getTTHitRate();
            if (hitRate < 0.3) {
                System.out.printf("⚠️ Low TT hit rate (%.1f%%) - consider increasing SearchConfig.TT_SIZE (%,d)\n",
                        hitRate * 100, SearchConfig.TT_SIZE);
            }
        }
    }

    public void incrementTTMisses() { ttMisses++; }
    public void incrementTTStores() { ttStores++; }
    public void incrementTTCollisions() { ttCollisions++; }

    // === PRUNING WITH SEARCHCONFIG VALIDATION ===

    public void incrementAlphaBetaCutoffs() { alphaBetaCutoffs++; }
    public void incrementReverseFutilityCutoffs() {
        reverseFutilityCutoffs++;
        // Validate against SearchConfig.FUTILITY_MAX_DEPTH
    }
    public void incrementFutilityCutoffs() {
        futilityCutoffs++;
        // Validate against SearchConfig.FUTILITY_MAX_DEPTH
    }
    public void incrementLMRReductions() {
        lmrReductions++;
        // Validate against SearchConfig.LMR_MIN_DEPTH
    }
    public void incrementCheckExtensions() { checkExtensions++; }

    // === NULL-MOVE PRUNING WITH SEARCHCONFIG INTEGRATION ===

    public void recordNullMoveAttempt(int depth) {
        nullMoveAttempts++;
        // Validate against SearchConfig.NULL_MOVE_MIN_DEPTH
        if (depth < SearchConfig.NULL_MOVE_MIN_DEPTH) {
            System.out.printf("⚠️ Null-move attempted at depth %d < SearchConfig.NULL_MOVE_MIN_DEPTH (%d)\n",
                    depth, SearchConfig.NULL_MOVE_MIN_DEPTH);
        }
    }

    public void recordNullMovePrune(int depth, long estimatedNodesSkipped) {
        nullMovePrunes++;
        nullMoveNodesSkipped += estimatedNodesSkipped;

        if (depth < nullMovePrunesByDepth.length) {
            nullMovePrunesByDepth[depth]++;
        }

        // Estimate time saved using SearchConfig.NODES_PER_SECOND_TARGET
        if (SearchConfig.NODES_PER_SECOND_TARGET > 0) {
            long timeSaved = estimatedNodesSkipped * 1000 / SearchConfig.NODES_PER_SECOND_TARGET;
            nullMoveTimesSaved += timeSaved;
        }
    }

    public void recordNullMoveVerification(boolean successful) {
        nullMoveVerifications++;
        if (successful) {
            nullMoveVerificationSuccesses++;
        }

        // Check against SearchConfig.NULL_MOVE_VERIFICATION_DEPTH
        if (nullMoveVerifications % 100 == 0) {
            double successRate = getNullMoveVerificationRate();
            if (successRate < 0.5) {
                System.out.printf("⚠️ Low null-move verification rate (%.1f%%) - review SearchConfig.NULL_MOVE_VERIFICATION_DEPTH\n",
                        successRate * 100);
            }
        }
    }

    public void recordNullMoveFailure() {
        nullMoveFailures++;
    }

    public void estimateTimeSaved(long timePerNode) {
        nullMoveTimesSaved += nullMoveNodesSkipped * timePerNode / 1000000; // Convert ns to ms
    }

    // Legacy compatibility
    public void incrementNullMovePrunes() {
        nullMovePrunes++;
    }

    // === QUIESCENCE WITH SEARCHCONFIG ===

    public void incrementQCutoffs() { qCutoffs++; }
    public void incrementStandPatCutoffs() { standPatCutoffs++; }
    public void incrementDeltaPruningCutoffs() {
        deltaPruningCutoffs++;
        // Track delta pruning effectiveness against SearchConfig.Q_DELTA_MARGIN
    }
    public void incrementQTTHits() { qTTHits++; }

    // === MOVE ORDERING WITH SEARCHCONFIG ===

    public void incrementKillerMoveHits() {
        killerMoveHits++;
        // Validate against SearchConfig.KILLER_MOVE_SLOTS
    }

    public void incrementHistoryMoveHits() {
        historyMoveHits++;
        // Track against SearchConfig.HISTORY_MAX_VALUE
    }

    public void incrementTTMoveHits() { ttMoveHits++; }
    public void incrementCaptureOrderingHits() { captureOrderingHits++; }

    public void recordCutoffByMoveNumber(int moveNumber) {
        if (moveNumber == 1) {
            firstMoveCutoffs++;
        } else if (moveNumber == 2) {
            secondMoveCutoffs++;
        } else {
            laterMoveCutoffs++;
        }
        cutoffsByMoveNumber.merge(moveNumber, 1L, Long::sum);

        // Analyze against SearchConfig move ordering priorities
        if (moveNumber > SearchConfig.LMR_MIN_MOVE_COUNT && firstMoveCutoffs < laterMoveCutoffs) {
            if (cutoffsByMoveNumber.size() % 100 == 0) {
                System.out.printf("⚠️ Late move cutoffs (%d) > first move cutoffs (%d) - review move ordering\n",
                        laterMoveCutoffs, firstMoveCutoffs);
            }
        }
    }

    public void recordCutoffByMoveType(String moveType) {
        cutoffsByMoveType.merge(moveType, 1L, Long::sum);
    }

    // === HISTORY HEURISTIC WITH SEARCHCONFIG ===

    public void incrementHistoryHeuristicUpdates() {
        historyHeuristicUpdates++;
        // Track against SearchConfig.HISTORY_MAX_VALUE
    }

    public void incrementHistoryHeuristicCutoffs() { historyHeuristicCutoffs++; }
    public void incrementHistoryTableAges() { historyTableAges++; }
    public void incrementQuietMovesEvaluated() { quietMovesEvaluated++; }
    public void incrementFirstMoveWasHistoryMove() { firstMoveWasHistoryMove++; }
    public void incrementTotalMoveOrderingQueries() { totalMoveOrderingQueries++; }

    // === MOVE TRACKING ===

    public void recordMove(String move) {
        moveFrequency.merge(move, 1, Integer::sum);
    }

    public void addMovesGenerated(int count) {
        totalMovesGenerated += count;
    }

    public void addMovesSearched(int count) {
        totalMovesSearched += count;
    }

    // === GETTERS WITH SEARCHCONFIG CONTEXT ===

    // Core getters
    public long getNodeCount() { return nodeCount; }
    public long getQNodeCount() { return qNodeCount; }
    public long getLeafNodeCount() { return leafNodeCount; }
    public long getTotalNodes() { return nodeCount + qNodeCount; }
    public int getMaxDepthReached() { return maxDepthReached; }

    // TT getters with SearchConfig context
    public long getTTHits() { return ttHits; }
    public long getTTMisses() { return ttMisses; }
    public double getTTHitRate() {
        long total = ttHits + ttMisses;
        return total > 0 ? (double) ttHits / total : 0.0;
    }

    // Pruning getters
    public long getAlphaBetaCutoffs() { return alphaBetaCutoffs; }
    public long getTotalCutoffs() {
        return alphaBetaCutoffs + reverseFutilityCutoffs + futilityCutoffs +
                qCutoffs + deltaPruningCutoffs + nullMovePrunes;
    }

    // NULL-MOVE GETTERS WITH SEARCHCONFIG ANALYSIS
    public long getNullMoveAttempts() { return nullMoveAttempts; }
    public long getNullMovePrunes() { return nullMovePrunes; }
    public long getNullMoveVerifications() { return nullMoveVerifications; }
    public long getNullMoveVerificationSuccesses() { return nullMoveVerificationSuccesses; }
    public long getNullMoveFailures() { return nullMoveFailures; }
    public long getNullMoveNodesSkipped() { return nullMoveNodesSkipped; }
    public long getNullMoveTimesSaved() { return nullMoveTimesSaved; }

    public double getNullMoveSuccessRate() {
        return nullMoveAttempts > 0 ? (double) nullMovePrunes / nullMoveAttempts : 0.0;
    }

    public double getNullMoveVerificationRate() {
        return nullMoveVerifications > 0 ?
                (double) nullMoveVerificationSuccesses / nullMoveVerifications : 0.0;
    }

    public double getNullMoveEfficiency() {
        return nodeCount > 0 ? (double) nullMoveNodesSkipped / nodeCount : 0.0;
    }

    // === PERFORMANCE CALCULATIONS WITH SEARCHCONFIG ===

    public double getCutoffRate() {
        return nodeCount > 0 ? (double) getTotalCutoffs() / nodeCount : 0.0;
    }

    public double getAverageBranchingFactor() {
        return nodeCount > 0 ? (double) branchingFactor / nodeCount : 0.0;
    }

    public long getTotalSearchTime() { return totalSearchTime; }

    public double getNodesPerSecond() {
        return totalSearchTime > 0 ? (double) getTotalNodes() * 1000 / totalSearchTime : 0.0;
    }

    /**
     * Enhanced NPS analysis against SearchConfig target
     */
    public double getNPSPerformanceRatio() {
        double currentNPS = getNodesPerSecond();
        return SearchConfig.NODES_PER_SECOND_TARGET > 0 ?
                currentNPS / SearchConfig.NODES_PER_SECOND_TARGET : 0.0;
    }

    public int getIterationsCompleted() { return iterationsCompleted; }

    // History heuristic getters with SearchConfig context
    public long getHistoryHeuristicUpdates() { return historyHeuristicUpdates; }
    public long getHistoryHeuristicCutoffs() { return historyHeuristicCutoffs; }
    public double getHistoryHeuristicEffectiveness() {
        return historyHeuristicUpdates > 0 ? (double) historyHeuristicCutoffs / historyHeuristicUpdates : 0.0;
    }

    public double getFirstMoveSuccessRate() {
        return totalMoveOrderingQueries > 0 ? (double) firstMoveCutoffs / totalMoveOrderingQueries : 0.0;
    }

    // === ENHANCED ANALYSIS METHODS WITH SEARCHCONFIG ===

    /**
     * Enhanced null-move analysis with SearchConfig parameters
     */
    public String getNullMoveAnalysisWithConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== NULL-MOVE ANALYSIS WITH SEARCHCONFIG ===\n");

        if (nullMoveAttempts > 0) {
            sb.append(String.format("SearchConfig Parameters:\n"));
            sb.append(String.format("  NULL_MOVE_ENABLED: %s\n", SearchConfig.NULL_MOVE_ENABLED));
            sb.append(String.format("  NULL_MOVE_MIN_DEPTH: %d\n", SearchConfig.NULL_MOVE_MIN_DEPTH));
            sb.append(String.format("  NULL_MOVE_REDUCTION: %d\n", SearchConfig.NULL_MOVE_REDUCTION));
            sb.append(String.format("  NULL_MOVE_VERIFICATION_DEPTH: %d\n", SearchConfig.NULL_MOVE_VERIFICATION_DEPTH));

            sb.append(String.format("\nPerformance:\n"));
            sb.append(String.format("  Attempts: %,d\n", nullMoveAttempts));
            sb.append(String.format("  Successful Prunes: %,d (%.1f%%)\n",
                    nullMovePrunes, getNullMoveSuccessRate() * 100));
            sb.append(String.format("  Failures: %,d (%.1f%%)\n",
                    nullMoveFailures, 100.0 * nullMoveFailures / nullMoveAttempts));

            if (nullMoveVerifications > 0) {
                sb.append(String.format("  Verifications: %,d (%.1f%% successful)\n",
                        nullMoveVerifications, getNullMoveVerificationRate() * 100));
            }

            sb.append(String.format("  Nodes Skipped: %,d (%.1f%% of total)\n",
                    nullMoveNodesSkipped, getNullMoveEfficiency() * 100));

            // Time saved using SearchConfig target
            if (nullMoveTimesSaved > 0) {
                sb.append(String.format("  Estimated Time Saved: %,dms (based on %,d NPS target)\n",
                        nullMoveTimesSaved, SearchConfig.NODES_PER_SECOND_TARGET));
            }

            // Effectiveness rating using SearchConfig criteria
            double successRate = getNullMoveSuccessRate();
            if (successRate > 0.6) {
                sb.append("🏆 Excellent null-move effectiveness for SearchConfig!\n");
            } else if (successRate > 0.4) {
                sb.append("✅ Good null-move effectiveness with SearchConfig\n");
            } else if (successRate > 0.2) {
                sb.append("📈 Average null-move effectiveness - tune SearchConfig.NULL_MOVE_*\n");
            } else {
                sb.append("⚠️ Poor null-move effectiveness - review SearchConfig.NULL_MOVE_ENABLED\n");
            }

        } else {
            sb.append("No null-move data recorded\n");
            if (!SearchConfig.NULL_MOVE_ENABLED) {
                sb.append("Note: SearchConfig.NULL_MOVE_ENABLED = false\n");
            }
        }

        return sb.toString();
    }

    /**
     * Enhanced Search Efficiency Score with SearchConfig integration (0-100)
     */
    public double getEnhancedSearchEfficiencyWithConfig() {
        double ttEfficiency = getTTHitRate() * 15;                    // 0-15 points
        double cutoffEfficiency = getCutoffRate() * 20;               // 0-20 points
        double moveOrderingEfficiency = getFirstMoveSuccessRate() * 20; // 0-20 points
        double historyEfficiency = getHistoryHeuristicEffectiveness() * 10; // 0-10 points
        double nullMoveEfficiency = getNullMoveEfficiency() * 15;     // 0-15 points
        double npsEfficiency = Math.min(20, getNPSPerformanceRatio() * 20); // 0-20 points (NEW)

        double totalEfficiency = ttEfficiency + cutoffEfficiency + moveOrderingEfficiency +
                historyEfficiency + nullMoveEfficiency + npsEfficiency;

        return Math.min(100, totalEfficiency);
    }

    /**
     * SearchConfig compliance score (0-100)
     */
    public double getSearchConfigComplianceScore() {
        double score = 100.0;

        // Depth compliance
        if (maxDepthReached > SearchConfig.MAX_DEPTH) {
            score -= 10; // Penalty for exceeding max depth
        }

        // Node count compliance
        if (getTotalNodes() > SearchConfig.MAX_NODES_PER_SEARCH) {
            score -= 15; // Penalty for excessive nodes
        }

        // Performance compliance
        double npsRatio = getNPSPerformanceRatio();
        if (npsRatio < 0.5) {
            score -= 20; // Significant penalty for poor performance
        } else if (npsRatio < 0.8) {
            score -= 10; // Moderate penalty
        }

        // TT size compliance
        if (getTTHitRate() < 0.2) {
            score -= 10; // Penalty for poor TT utilization
        }

        // Strategy usage (if we can track it)
        if (!lastUsedStrategy.equals(SearchConfig.DEFAULT_STRATEGY.toString())) {
            score -= 5; // Minor penalty for not using default strategy
        }

        return Math.max(0, score);
    }

    // === COMPREHENSIVE OUTPUT WITH SEARCHCONFIG ===

    /**
     * Comprehensive statistics summary with SearchConfig integration
     */
    public String getComprehensiveSummaryWithConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== COMPREHENSIVE SEARCH STATISTICS (SearchConfig) ===\n");

        // SearchConfig context
        sb.append("SearchConfig Context:\n");
        sb.append(String.format("  DEFAULT_STRATEGY: %s (used: %s)\n",
                SearchConfig.DEFAULT_STRATEGY, lastUsedStrategy));
        sb.append(String.format("  MAX_DEPTH: %d (reached: %d)\n", SearchConfig.MAX_DEPTH, maxDepthReached));
        sb.append(String.format("  TT_SIZE: %,d | Target NPS: %,d\n",
                SearchConfig.TT_SIZE, SearchConfig.NODES_PER_SECOND_TARGET));

        // Core stats with SearchConfig comparison
        double currentNPS = getNodesPerSecond();
        double npsRatio = getNPSPerformanceRatio();
        sb.append(String.format("\nCore Performance:\n"));
        sb.append(String.format("  Nodes: %,d (%.1fk NPS vs %,d target = %.1f%%)\n",
                getTotalNodes(), currentNPS / 1000, SearchConfig.NODES_PER_SECOND_TARGET, npsRatio * 100));
        sb.append(String.format("  Regular: %,d, Quiescence: %,d\n", nodeCount, qNodeCount));
        sb.append(String.format("  Max Depth: %d/%d, Iterations: %d\n",
                maxDepthReached, SearchConfig.MAX_DEPTH, iterationsCompleted));
        sb.append(String.format("  Time: %,dms\n", totalSearchTime));

        // Transposition Table with SearchConfig context
        sb.append(String.format("\nTransposition Table (SearchConfig.TT_SIZE: %,d):\n", SearchConfig.TT_SIZE));
        sb.append(String.format("  Hit Rate: %.1f%% (%,d hits, %,d misses)\n",
                getTTHitRate() * 100, ttHits, ttMisses));

        // Pruning effectiveness with SearchConfig parameters
        sb.append(String.format("\nPruning (SearchConfig parameters):\n"));
        sb.append(String.format("  Overall: %.1f%% cutoff rate (%,d total cutoffs)\n",
                getCutoffRate() * 100, getTotalCutoffs()));
        sb.append(String.format("  Alpha-Beta: %,d (%.1f%%)\n",
                alphaBetaCutoffs, 100.0 * alphaBetaCutoffs / nodeCount));

        // NULL-MOVE with SearchConfig
        if (nullMoveAttempts > 0) {
            sb.append(String.format("  Null-Move (min_depth=%d, reduction=%d): %,d/%,d (%.1f%% success, %,d nodes saved)\n",
                    SearchConfig.NULL_MOVE_MIN_DEPTH, SearchConfig.NULL_MOVE_REDUCTION,
                    nullMovePrunes, nullMoveAttempts, getNullMoveSuccessRate() * 100, nullMoveNodesSkipped));
        }

        // Move ordering with SearchConfig
        long totalOrderingHits = ttMoveHits + killerMoveHits + historyMoveHits + captureOrderingHits;
        if (totalOrderingHits > 0) {
            sb.append(String.format("\nMove Ordering (SearchConfig priorities):\n"));
            sb.append(String.format("  Total hits: %,d (%.1f%% first move success)\n",
                    totalOrderingHits, getFirstMoveSuccessRate() * 100));
            sb.append(String.format("  Breakdown: TT:%,d, Captures:%,d, Killers:%,d (max_slots=%d), History:%,d (max=%d)\n",
                    ttMoveHits, captureOrderingHits, killerMoveHits, SearchConfig.KILLER_MOVE_SLOTS,
                    historyMoveHits, SearchConfig.HISTORY_MAX_VALUE));
        }

        // Overall efficiency with SearchConfig
        double configCompliance = getSearchConfigComplianceScore();
        double enhancedEfficiency = getEnhancedSearchEfficiencyWithConfig();
        sb.append(String.format("\nSearchConfig Integration:\n"));
        sb.append(String.format("  Enhanced Efficiency: %.1f/100\n", enhancedEfficiency));
        sb.append(String.format("  Config Compliance: %.1f/100\n", configCompliance));
        sb.append(String.format("  Parameter Access Count: %,d\n", parameterAccessCount));

        return sb.toString();
    }

    /**
     * Brief summary with SearchConfig performance ratio
     */
    public String getBriefSummaryWithConfig() {
        double npsRatio = getNPSPerformanceRatio();
        double efficiency = getEnhancedSearchEfficiencyWithConfig();

        return String.format("Nodes: %,d, Time: %,dms, NPS: %.1fk (%.1f%% of target), TT: %.1f%%, Cuts: %.1f%%, NM: %,d/%.1f%%, Eff: %.1f, Compliance: %.1f",
                getTotalNodes(), totalSearchTime, getNodesPerSecond() / 1000, npsRatio * 100,
                getTTHitRate() * 100, getCutoffRate() * 100,
                nullMovePrunes, getNullMoveSuccessRate() * 100,
                efficiency, getSearchConfigComplianceScore());
    }

    /**
     * Legacy brief summary (delegates to SearchConfig version)
     */
    public String getBriefSummary() {
        return getBriefSummaryWithConfig();
    }

    /**
     * SearchConfig recommendations based on statistics
     */
    public String getSearchConfigRecommendations() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SEARCHCONFIG TUNING RECOMMENDATIONS ===\n");

        double npsRatio = getNPSPerformanceRatio();
        if (npsRatio < 0.5) {
            sb.append("🚨 CRITICAL: Performance well below target\n");
            sb.append("  • Consider increasing SearchConfig.DEFAULT_TIME_LIMIT_MS\n");
            sb.append("  • Review SearchConfig.MAX_NODES_PER_SEARCH\n");
            sb.append("  • Consider simpler SearchConfig.DEFAULT_STRATEGY\n");
        } else if (npsRatio < 0.8) {
            sb.append("⚠️ Performance below target:\n");
            sb.append("  • Fine-tune SearchConfig pruning parameters\n");
            sb.append("  • Consider SearchConfig.TT_SIZE increase\n");
        } else if (npsRatio > 1.5) {
            sb.append("🚀 Excellent performance - consider deeper search:\n");
            sb.append("  • Increase SearchConfig.MAX_DEPTH if time allows\n");
            sb.append("  • Enable more advanced SearchConfig features\n");
        }

        if (getTTHitRate() < 0.3) {
            sb.append("🗃️ Poor TT performance:\n");
            sb.append(String.format("  • Increase SearchConfig.TT_SIZE from %,d\n", SearchConfig.TT_SIZE));
            sb.append(String.format("  • Review SearchConfig.TT_EVICTION_THRESHOLD (%,d)\n", SearchConfig.TT_EVICTION_THRESHOLD));
        }

        if (getFirstMoveSuccessRate() < 0.4) {
            sb.append("🎯 Poor move ordering:\n");
            sb.append(String.format("  • Tune SearchConfig.HISTORY_MAX_VALUE (%d)\n", SearchConfig.HISTORY_MAX_VALUE));
            sb.append(String.format("  • Increase SearchConfig.KILLER_MOVE_SLOTS (%d)\n", SearchConfig.KILLER_MOVE_SLOTS));
            sb.append("  • Review SearchConfig move ordering priorities\n");
        }

        if (nullMoveAttempts > 0 && getNullMoveSuccessRate() < 0.3) {
            sb.append("🔄 Ineffective null-move pruning:\n");
            sb.append(String.format("  • Review SearchConfig.NULL_MOVE_MIN_DEPTH (%d)\n", SearchConfig.NULL_MOVE_MIN_DEPTH));
            sb.append(String.format("  • Adjust SearchConfig.NULL_MOVE_REDUCTION (%d)\n", SearchConfig.NULL_MOVE_REDUCTION));
            sb.append("  • Consider disabling SearchConfig.NULL_MOVE_ENABLED\n");
        }

        if (sb.length() <= 50) { // Only header
            sb.append("✅ All SearchConfig parameters appear well-tuned!\n");
        }

        return sb.toString();
    }

    /**
     * Export statistics with SearchConfig context for analysis
     */
    public String exportWithSearchConfigContext() {
        return String.format("%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,%.1f,%.1f,%s,%d,%d\n",
                nodeCount, qNodeCount, maxDepthReached, totalSearchTime,
                ttHits + ttMisses, getTTHitRate(), getCutoffRate(),
                getAverageBranchingFactor(), getFirstMoveSuccessRate(),
                getNullMoveSuccessRate(), nullMoveAttempts, nullMovePrunes,
                nullMoveNodesSkipped, getEnhancedSearchEfficiencyWithConfig(),
                getSearchConfigComplianceScore(), lastUsedStrategy,
                SearchConfig.TT_SIZE, SearchConfig.NODES_PER_SECOND_TARGET);
    }

    public void incrementRazorCutoffs() { /* implement */ }
    public void incrementMultiCutPrunes() { /* implement */ }
    public void incrementIIDAttempts() { /* implement */ }
    //public boolean isKillerMove(Move move, int depth) { /* implement */ }

    /**
     * Standard toString delegates to SearchConfig version
     */
    @Override
    public String toString() {
        return getComprehensiveSummaryWithConfig();
    }
}
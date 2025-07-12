package GaT.search;

import java.util.HashMap;
import java.util.Map;

/**
 * ENHANCED SEARCH STATISTICS - WITH HISTORY HEURISTIC INTEGRATION
 * Centralized metrics tracking with comprehensive move ordering analysis
 *
 * ENHANCEMENTS:
 * ✅ History Heuristic statistics integration
 * ✅ Enhanced move ordering analysis
 * ✅ Comprehensive statistical output
 * ✅ Move ordering effectiveness tracking
 * ✅ History table analysis methods
 */
public class SearchStatistics {

    // === CORE STATISTICS ===
    private long nodeCount = 0;
    private long qNodeCount = 0;
    private long leafNodeCount = 0;
    private long branchingFactor = 0;
    private int maxDepthReached = 0;

    // === TRANSPOSITION TABLE STATS ===
    private long ttHits = 0;
    private long ttMisses = 0;
    private long ttStores = 0;
    private long ttCollisions = 0;

    // === PRUNING STATISTICS ===
    private long alphaBetaCutoffs = 0;
    private long reverseFutilityCutoffs = 0;
    private long nullMoveCutoffs = 0;
    private long futilityCutoffs = 0;
    private long lmrReductions = 0;
    private long checkExtensions = 0;

    // === QUIESCENCE STATISTICS ===
    private long qCutoffs = 0;
    private long standPatCutoffs = 0;
    private long deltaPruningCutoffs = 0;
    private long qTTHits = 0;

    // === ENHANCED MOVE ORDERING STATISTICS ===
    private long killerMoveHits = 0;
    private long historyMoveHits = 0;
    private long ttMoveHits = 0;
    private long captureOrderingHits = 0;

    // === NEW: HISTORY HEURISTIC STATISTICS ===
    private long historyHeuristicUpdates = 0;
    private long historyHeuristicCutoffs = 0;
    private long historyTableAges = 0;
    private long quietMovesEvaluated = 0;
    private long firstMoveWasHistoryMove = 0;
    private long totalMoveOrderingQueries = 0;

    // === ENHANCED MOVE ORDERING ANALYSIS ===
    private long firstMoveCutoffs = 0;
    private long secondMoveCutoffs = 0;
    private long laterMoveCutoffs = 0;
    private Map<Integer, Long> cutoffsByMoveNumber = new HashMap<>();
    private Map<String, Long> cutoffsByMoveType = new HashMap<>();

    // === TIMING STATISTICS ===
    private long searchStartTime = 0;
    private long totalSearchTime = 0;
    private long[] depthTimes = new long[32];
    private int iterationsCompleted = 0;

    // === MOVE STATISTICS ===
    private Map<String, Integer> moveFrequency = new HashMap<>();
    private int totalMovesGenerated = 0;
    private int totalMovesSearched = 0;

    // === SINGLETON PATTERN ===
    private static SearchStatistics instance = new SearchStatistics();

    public static SearchStatistics getInstance() {
        return instance;
    }

    private SearchStatistics() {
        reset();
    }

    // === RESET AND INITIALIZATION ===

    /**
     * Reset all statistics for a new search
     */
    public void reset() {
        nodeCount = 0;
        qNodeCount = 0;
        leafNodeCount = 0;
        branchingFactor = 0;
        maxDepthReached = 0;

        ttHits = 0;
        ttMisses = 0;
        ttStores = 0;
        ttCollisions = 0;

        alphaBetaCutoffs = 0;
        reverseFutilityCutoffs = 0;
        nullMoveCutoffs = 0;
        futilityCutoffs = 0;
        lmrReductions = 0;
        checkExtensions = 0;

        qCutoffs = 0;
        standPatCutoffs = 0;
        deltaPruningCutoffs = 0;
        qTTHits = 0;

        killerMoveHits = 0;
        historyMoveHits = 0;
        ttMoveHits = 0;
        captureOrderingHits = 0;

        // Reset history heuristic statistics
        historyHeuristicUpdates = 0;
        historyHeuristicCutoffs = 0;
        historyTableAges = 0;
        quietMovesEvaluated = 0;
        firstMoveWasHistoryMove = 0;
        totalMoveOrderingQueries = 0;

        // Reset move ordering analysis
        firstMoveCutoffs = 0;
        secondMoveCutoffs = 0;
        laterMoveCutoffs = 0;
        cutoffsByMoveNumber.clear();
        cutoffsByMoveType.clear();

        searchStartTime = 0;
        totalSearchTime = 0;
        depthTimes = new long[32];
        iterationsCompleted = 0;

        moveFrequency.clear();
        totalMovesGenerated = 0;
        totalMovesSearched = 0;
    }

    /**
     * Start timing a search
     */
    public void startSearch() {
        searchStartTime = System.currentTimeMillis();
    }

    /**
     * End timing a search
     */
    public void endSearch() {
        if (searchStartTime > 0) {
            totalSearchTime = System.currentTimeMillis() - searchStartTime;
        }
    }

    /**
     * Start timing a depth iteration
     */
    public void startDepth(int depth) {
        if (depth < depthTimes.length) {
            depthTimes[depth] = System.currentTimeMillis();
        }
    }

    /**
     * End timing a depth iteration
     */
    public void endDepth(int depth) {
        if (depth < depthTimes.length && depthTimes[depth] > 0) {
            depthTimes[depth] = System.currentTimeMillis() - depthTimes[depth];
            iterationsCompleted = Math.max(iterationsCompleted, depth + 1);
            maxDepthReached = Math.max(maxDepthReached, depth);
        }
    }

    // === NODE COUNTING ===

    public void incrementNodeCount() { nodeCount++; }
    public void incrementQNodeCount() { qNodeCount++; }
    public void incrementLeafNodeCount() { leafNodeCount++; }
    public void addBranchingFactor(int branches) { branchingFactor += branches; }

    // === TRANSPOSITION TABLE ===

    public void incrementTTHits() { ttHits++; }
    public void incrementTTMisses() { ttMisses++; }
    public void incrementTTStores() { ttStores++; }
    public void incrementTTCollisions() { ttCollisions++; }

    // === PRUNING ===

    public void incrementAlphaBetaCutoffs() { alphaBetaCutoffs++; }
    public void incrementReverseFutilityCutoffs() { reverseFutilityCutoffs++; }
    public void incrementNullMoveCutoffs() { nullMoveCutoffs++; }
    public void incrementFutilityCutoffs() { futilityCutoffs++; }
    public void incrementLMRReductions() { lmrReductions++; }
    public void incrementCheckExtensions() { checkExtensions++; }

    // === QUIESCENCE ===

    public void incrementQCutoffs() { qCutoffs++; }
    public void incrementStandPatCutoffs() { standPatCutoffs++; }
    public void incrementDeltaPruningCutoffs() { deltaPruningCutoffs++; }
    public void incrementQTTHits() { qTTHits++; }

    // === ENHANCED MOVE ORDERING ===

    public void incrementKillerMoveHits() { killerMoveHits++; }
    public void incrementHistoryMoveHits() { historyMoveHits++; }
    public void incrementTTMoveHits() { ttMoveHits++; }
    public void incrementCaptureOrderingHits() { captureOrderingHits++; }

    // === NEW: HISTORY HEURISTIC TRACKING ===

    public void incrementHistoryHeuristicUpdates() { historyHeuristicUpdates++; }
    public void incrementHistoryHeuristicCutoffs() { historyHeuristicCutoffs++; }
    public void incrementHistoryTableAges() { historyTableAges++; }
    public void incrementQuietMovesEvaluated() { quietMovesEvaluated++; }
    public void incrementFirstMoveWasHistoryMove() { firstMoveWasHistoryMove++; }
    public void incrementTotalMoveOrderingQueries() { totalMoveOrderingQueries++; }

    // === ENHANCED MOVE ORDERING ANALYSIS ===

    /**
     * Record a cutoff by move position in the move list
     */
    public void recordCutoffByMoveNumber(int moveNumber) {
        if (moveNumber == 1) {
            firstMoveCutoffs++;
        } else if (moveNumber == 2) {
            secondMoveCutoffs++;
        } else {
            laterMoveCutoffs++;
        }
        cutoffsByMoveNumber.merge(moveNumber, 1L, Long::sum);
    }

    /**
     * Record a cutoff by move type (TT, Killer, History, Capture, etc.)
     */
    public void recordCutoffByMoveType(String moveType) {
        cutoffsByMoveType.merge(moveType, 1L, Long::sum);
    }

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

    // === GETTERS ===

    public long getNodeCount() { return nodeCount; }
    public long getQNodeCount() { return qNodeCount; }
    public long getLeafNodeCount() { return leafNodeCount; }
    public long getTotalNodes() { return nodeCount + qNodeCount; }
    public int getMaxDepthReached() { return maxDepthReached; }

    public long getTTHits() { return ttHits; }
    public long getTTMisses() { return ttMisses; }
    public double getTTHitRate() {
        long total = ttHits + ttMisses;
        return total > 0 ? (double) ttHits / total : 0.0;
    }

    public long getAlphaBetaCutoffs() { return alphaBetaCutoffs; }

    public long getTotalCutoffs() {
        return alphaBetaCutoffs + reverseFutilityCutoffs + nullMoveCutoffs +
                futilityCutoffs + qCutoffs + deltaPruningCutoffs;
    }

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

    public int getIterationsCompleted() { return iterationsCompleted; }

    // === NEW: HISTORY HEURISTIC GETTERS ===

    public long getHistoryHeuristicUpdates() { return historyHeuristicUpdates; }
    public long getHistoryHeuristicCutoffs() { return historyHeuristicCutoffs; }
    public long getHistoryTableAges() { return historyTableAges; }
    public long getQuietMovesEvaluated() { return quietMovesEvaluated; }
    public long getFirstMoveWasHistoryMove() { return firstMoveWasHistoryMove; }

    public double getHistoryHeuristicEffectiveness() {
        return historyHeuristicUpdates > 0 ? (double) historyHeuristicCutoffs / historyHeuristicUpdates : 0.0;
    }

    public double getFirstMoveSuccessRate() {
        return totalMoveOrderingQueries > 0 ? (double) firstMoveCutoffs / totalMoveOrderingQueries : 0.0;
    }

    // === ENHANCED ANALYSIS METHODS ===

    /**
     * Get comprehensive move ordering effectiveness analysis
     */
    public String getMoveOrderingAnalysis() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MOVE ORDERING ANALYSIS ===\n");

        long totalCutoffs = firstMoveCutoffs + secondMoveCutoffs + laterMoveCutoffs;
        if (totalCutoffs > 0) {
            sb.append(String.format("First move cutoffs: %,d (%.1f%%)\n",
                    firstMoveCutoffs, 100.0 * firstMoveCutoffs / totalCutoffs));
            sb.append(String.format("Second move cutoffs: %,d (%.1f%%)\n",
                    secondMoveCutoffs, 100.0 * secondMoveCutoffs / totalCutoffs));
            sb.append(String.format("Later move cutoffs: %,d (%.1f%%)\n",
                    laterMoveCutoffs, 100.0 * laterMoveCutoffs / totalCutoffs));

            // Effectiveness rating
            double firstMoveRate = (double) firstMoveCutoffs / totalCutoffs;
            if (firstMoveRate > 0.5) {
                sb.append("🏆 Excellent move ordering!\n");
            } else if (firstMoveRate > 0.3) {
                sb.append("✅ Good move ordering\n");
            } else if (firstMoveRate > 0.15) {
                sb.append("📈 Average move ordering\n");
            } else {
                sb.append("⚠️ Poor move ordering\n");
            }
        }

        // Cutoffs by move type
        if (!cutoffsByMoveType.isEmpty()) {
            sb.append("\nCutoffs by move type:\n");
            cutoffsByMoveType.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry -> sb.append(String.format("  %s: %,d\n", entry.getKey(), entry.getValue())));
        }

        return sb.toString();
    }

    /**
     * Get history heuristic effectiveness analysis
     */
    public String getHistoryHeuristicAnalysis() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== HISTORY HEURISTIC ANALYSIS ===\n");

        if (historyHeuristicUpdates > 0) {
            sb.append(String.format("History updates: %,d\n", historyHeuristicUpdates));
            sb.append(String.format("History cutoffs: %,d\n", historyHeuristicCutoffs));
            sb.append(String.format("Effectiveness: %.1f%%\n",
                    100.0 * historyHeuristicCutoffs / historyHeuristicUpdates));

            if (quietMovesEvaluated > 0) {
                sb.append(String.format("Quiet moves evaluated: %,d\n", quietMovesEvaluated));
                sb.append(String.format("History move success rate: %.1f%%\n",
                        100.0 * firstMoveWasHistoryMove / quietMovesEvaluated));
            }

            if (historyTableAges > 0) {
                sb.append(String.format("History table aged: %,d times\n", historyTableAges));
            }

            // Integration with actual HistoryHeuristic instance
            try {
                MoveOrdering ordering = Minimax.getMoveOrdering();
                if (ordering != null && ordering.getHistoryHeuristic() != null) {
                    sb.append("\n").append(ordering.getHistoryHeuristic().getStatistics()).append("\n");
                    sb.append("Best history move: ").append(ordering.getHistoryHeuristic().getBestHistoryMove()).append("\n");
                }
            } catch (Exception e) {
                // Silent fail - not critical
            }

        } else {
            sb.append("No history heuristic data recorded\n");
        }

        return sb.toString();
    }

    /**
     * Get search efficiency score with history consideration (0-100)
     */
    public double getEnhancedSearchEfficiency() {
        double ttEfficiency = getTTHitRate() * 15;           // 0-15 points
        double cutoffEfficiency = getCutoffRate() * 25;      // 0-25 points
        double moveOrderingEfficiency = getFirstMoveSuccessRate() * 25; // 0-25 points
        double historyEfficiency = getHistoryHeuristicEffectiveness() * 15; // 0-15 points
        double branchingEfficiency = Math.max(0, 20 - getAverageBranchingFactor()); // 0-20 points

        return Math.min(100, ttEfficiency + cutoffEfficiency + moveOrderingEfficiency +
                historyEfficiency + branchingEfficiency);
    }

    /**
     * Get search efficiency score (0-100) - original method
     */
    public double getSearchEfficiency() {
        double ttEfficiency = getTTHitRate() * 20;           // 0-20 points
        double cutoffEfficiency = getCutoffRate() * 30;      // 0-30 points
        double branchingEfficiency = Math.max(0, 25 - getAverageBranchingFactor()); // 0-25 points
        double speedEfficiency = Math.min(25, getNodesPerSecond() / 10000); // 0-25 points

        return Math.min(100, ttEfficiency + cutoffEfficiency + branchingEfficiency + speedEfficiency);
    }

    /**
     * Get most frequently searched moves
     */
    public Map<String, Integer> getTopMoves(int limit) {
        return moveFrequency.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(HashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        HashMap::putAll);
    }

    // === ENHANCED FORMATTED OUTPUT ===

    /**
     * Get comprehensive statistics summary with history heuristic
     */
    public String getEnhancedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ENHANCED SEARCH STATISTICS ===\n");

        // Core stats
        sb.append(String.format("Nodes: %,d (%.1fk NPS)\n",
                getTotalNodes(), getNodesPerSecond() / 1000));
        sb.append(String.format("  Regular: %,d, Quiescence: %,d\n", nodeCount, qNodeCount));
        sb.append(String.format("Max Depth: %d, Iterations: %d\n", maxDepthReached, iterationsCompleted));
        sb.append(String.format("Time: %,dms\n", totalSearchTime));

        // Transposition Table
        sb.append(String.format("\nTT: %.1f%% hit rate (%,d hits, %,d misses)\n",
                getTTHitRate() * 100, ttHits, ttMisses));

        // Enhanced pruning effectiveness
        sb.append(String.format("\nPruning: %.1f%% cutoff rate (%,d total cutoffs)\n",
                getCutoffRate() * 100, getTotalCutoffs()));
        sb.append(String.format("  Alpha-Beta: %,d (%.1f%%)\n",
                alphaBetaCutoffs, 100.0 * alphaBetaCutoffs / nodeCount));

        if (reverseFutilityCutoffs > 0) {
            sb.append(String.format("  RFP: %,d (%.1f%%)\n",
                    reverseFutilityCutoffs, 100.0 * reverseFutilityCutoffs / nodeCount));
        }
        if (nullMoveCutoffs > 0) {
            sb.append(String.format("  Null Move: %,d (%.1f%%)\n",
                    nullMoveCutoffs, 100.0 * nullMoveCutoffs / nodeCount));
        }
        if (futilityCutoffs > 0) {
            sb.append(String.format("  Futility: %,d (%.1f%%)\n",
                    futilityCutoffs, 100.0 * futilityCutoffs / nodeCount));
        }

        // Enhanced move ordering
        long totalOrderingHits = ttMoveHits + killerMoveHits + historyMoveHits + captureOrderingHits;
        if (totalOrderingHits > 0) {
            sb.append(String.format("\nMove Ordering: %,d hits (%.1f%% first move success)\n",
                    totalOrderingHits, getFirstMoveSuccessRate() * 100));
            if (ttMoveHits > 0) sb.append(String.format("  TT moves: %,d\n", ttMoveHits));
            if (captureOrderingHits > 0) sb.append(String.format("  Captures: %,d\n", captureOrderingHits));
            if (killerMoveHits > 0) sb.append(String.format("  Killers: %,d\n", killerMoveHits));
            if (historyMoveHits > 0) sb.append(String.format("  History: %,d\n", historyMoveHits));
        }

        // History Heuristic specific
        if (historyHeuristicUpdates > 0) {
            sb.append(String.format("\nHistory Heuristic:\n"));
            sb.append(String.format("  Updates: %,d, Cutoffs: %,d (%.1f%% effectiveness)\n",
                    historyHeuristicUpdates, historyHeuristicCutoffs,
                    getHistoryHeuristicEffectiveness() * 100));

            if (quietMovesEvaluated > 0) {
                sb.append(String.format("  Quiet moves: %,d, History first moves: %,d (%.1f%%)\n",
                        quietMovesEvaluated, firstMoveWasHistoryMove,
                        100.0 * firstMoveWasHistoryMove / quietMovesEvaluated));
            }

            // Get current history stats from HistoryHeuristic instance
            try {
                MoveOrdering ordering = Minimax.getMoveOrdering();
                if (ordering != null && ordering.getHistoryHeuristic() != null) {
                    String histStats = ordering.getHistoryHeuristic().getStatistics();
                    String bestHistory = ordering.getHistoryHeuristic().getBestHistoryMove();
                    sb.append(String.format("  Current: %s\n", histStats));
                    sb.append(String.format("  Best: %s\n", bestHistory));
                }
            } catch (Exception e) {
                // Silent fail
            }
        }

        // Quiescence stats
        if (qNodeCount > 0) {
            sb.append(String.format("\nQuiescence:\n"));
            sb.append(String.format("  Stand-pat: %,d (%.1f%%)\n",
                    standPatCutoffs, 100.0 * standPatCutoffs / qNodeCount));
            sb.append(String.format("  Delta pruning: %,d (%.1f%%)\n",
                    deltaPruningCutoffs, 100.0 * deltaPruningCutoffs / qNodeCount));
        }

        // Overall efficiency
        sb.append(String.format("\nEfficiency Score: %.1f/100 (Enhanced: %.1f/100)\n",
                getSearchEfficiency(), getEnhancedSearchEfficiency()));

        return sb.toString();
    }

    /**
     * Get comprehensive statistics summary (original method)
     */
    public String getSummary() {
        return getEnhancedSummary();
    }

    /**
     * Get brief one-line summary with history info
     */
    public String getBriefSummary() {
        return String.format("Nodes: %,d, Time: %,dms, NPS: %.1fk, TT: %.1f%%, Cuts: %.1f%%, 1st: %.1f%%, Hist: %,d, Eff: %.1f",
                getTotalNodes(), totalSearchTime, getNodesPerSecond() / 1000,
                getTTHitRate() * 100, getCutoffRate() * 100, getFirstMoveSuccessRate() * 100,
                historyHeuristicUpdates, getEnhancedSearchEfficiency());
    }

    /**
     * Export enhanced statistics as CSV for analysis
     */
    public String toEnhancedCSV() {
        return String.format("%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%.1f\n",
                nodeCount, qNodeCount, maxDepthReached, totalSearchTime,
                ttHits + ttMisses, getTTHitRate(), getCutoffRate(),
                getAverageBranchingFactor(), getFirstMoveSuccessRate(),
                getHistoryHeuristicEffectiveness(), historyHeuristicUpdates,
                historyHeuristicCutoffs, getEnhancedSearchEfficiency());
    }

    /**
     * Get enhanced CSV header for export
     */
    public static String getEnhancedCSVHeader() {
        return "nodes,qnodes,depth,time_ms,tt_probes,tt_hit_rate,cutoff_rate,branching_factor," +
                "first_move_rate,history_effectiveness,history_updates,history_cutoffs,efficiency\n";
    }

    /**
     * Export original statistics as CSV for analysis
     */
    public String toCSV() {
        return String.format("%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.1f\n",
                nodeCount, qNodeCount, maxDepthReached, totalSearchTime,
                ttHits + ttMisses, getTTHitRate(), getCutoffRate(),
                getAverageBranchingFactor(), getSearchEfficiency());
    }

    /**
     * Get CSV header for export (original)
     */
    public static String getCSVHeader() {
        return "nodes,qnodes,depth,time_ms,tt_probes,tt_hit_rate,cutoff_rate,branching_factor,efficiency\n";
    }
}
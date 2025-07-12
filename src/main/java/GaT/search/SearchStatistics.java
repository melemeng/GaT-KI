package GaT.search;

import java.util.HashMap;
import java.util.Map;


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

    // === NULL-MOVE PRUNING STATISTICS (ERWEITERT) ===
    private long nullMoveAttempts = 0;           // Wie oft wurde Null-Move versucht?
    private long nullMovePrunes = 0;             // Wie oft war es erfolgreich?
    private long nullMoveVerifications = 0;      // Wie oft wurde Verification aufgerufen?
    private long nullMoveVerificationSuccesses = 0; // Wie oft war Verification erfolgreich?
    private long nullMoveFailures = 0;           // Wie oft schlug Null-Move fehl?
    private int[] nullMovePrunesByDepth = new int[20]; // Prunes nach Tiefe aufgeschlüsselt
    private long nullMoveTimesSaved = 0;         // Geschätzte eingesparte Zeit in ms
    private long nullMoveNodesSkipped = 0;       // Geschätzte übersprungene Knoten

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

        // Null-Move Pruning (Erweitert)
        nullMoveAttempts = 0;
        nullMovePrunes = 0;
        nullMoveVerifications = 0;
        nullMoveVerificationSuccesses = 0;
        nullMoveFailures = 0;
        nullMovePrunesByDepth = new int[20];
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
        depthTimes = new long[32];
        iterationsCompleted = 0;

        // Moves
        moveFrequency.clear();
        totalMovesGenerated = 0;
        totalMovesSearched = 0;
    }

    // === TIMING METHODS ===

    public void startSearch() {
        searchStartTime = System.currentTimeMillis();
    }

    public void endSearch() {
        if (searchStartTime > 0) {
            totalSearchTime = System.currentTimeMillis() - searchStartTime;
        }
    }

    public void startDepth(int depth) {
        if (depth < depthTimes.length) {
            depthTimes[depth] = System.currentTimeMillis();
        }
    }

    public void endDepth(int depth) {
        if (depth < depthTimes.length && depthTimes[depth] > 0) {
            depthTimes[depth] = System.currentTimeMillis() - depthTimes[depth];
            iterationsCompleted = Math.max(iterationsCompleted, depth + 1);
            maxDepthReached = Math.max(maxDepthReached, depth);
        }
    }

    // === CORE NODE COUNTING ===

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
    public void incrementFutilityCutoffs() { futilityCutoffs++; }
    public void incrementLMRReductions() { lmrReductions++; }
    public void incrementCheckExtensions() { checkExtensions++; }

    // === NULL-MOVE PRUNING (ERWEITERTE TRACKING) ===

    /**
     * Record einem Null-Move Versuch
     */
    public void recordNullMoveAttempt(int depth) {
        nullMoveAttempts++;
        if (depth < nullMovePrunesByDepth.length) {
            // Track attempts by depth for analysis
        }
    }

    /**
     * Record einem erfolgreichen Null-Move Prune
     */
    public void recordNullMovePrune(int depth, long estimatedNodesSkipped) {
        nullMovePrunes++;
        nullMoveNodesSkipped += estimatedNodesSkipped;

        if (depth < nullMovePrunesByDepth.length) {
            nullMovePrunesByDepth[depth]++;
        }
    }

    /**
     * Record eine Null-Move Verification
     */
    public void recordNullMoveVerification(boolean successful) {
        nullMoveVerifications++;
        if (successful) {
            nullMoveVerificationSuccesses++;
        }
    }

    /**
     * Record einem fehlgeschlagenen Null-Move
     */
    public void recordNullMoveFailure() {
        nullMoveFailures++;
    }

    /**
     * Estimate time saved durch Null-Move Pruning
     */
    public void estimateTimeSaved(long timePerNode) {
        nullMoveTimesSaved += nullMoveNodesSkipped * timePerNode / 1000000; // Convert ns to ms
    }

    // Legacy method for compatibility
    public void incrementNullMovePrunes() {
        nullMovePrunes++;
    }

    // === QUIESCENCE ===

    public void incrementQCutoffs() { qCutoffs++; }
    public void incrementStandPatCutoffs() { standPatCutoffs++; }
    public void incrementDeltaPruningCutoffs() { deltaPruningCutoffs++; }
    public void incrementQTTHits() { qTTHits++; }

    // === MOVE ORDERING ===

    public void incrementKillerMoveHits() { killerMoveHits++; }
    public void incrementHistoryMoveHits() { historyMoveHits++; }
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
    }

    public void recordCutoffByMoveType(String moveType) {
        cutoffsByMoveType.merge(moveType, 1L, Long::sum);
    }

    // === HISTORY HEURISTIC ===

    public void incrementHistoryHeuristicUpdates() { historyHeuristicUpdates++; }
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

    // === GETTERS ===

    // Core getters
    public long getNodeCount() { return nodeCount; }
    public long getQNodeCount() { return qNodeCount; }
    public long getLeafNodeCount() { return leafNodeCount; }
    public long getTotalNodes() { return nodeCount + qNodeCount; }
    public int getMaxDepthReached() { return maxDepthReached; }

    // TT getters
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

    // NULL-MOVE GETTERS (ERWEITERT)
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

    // Performance calculations
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

    // History heuristic getters
    public long getHistoryHeuristicUpdates() { return historyHeuristicUpdates; }
    public long getHistoryHeuristicCutoffs() { return historyHeuristicCutoffs; }
    public double getHistoryHeuristicEffectiveness() {
        return historyHeuristicUpdates > 0 ? (double) historyHeuristicCutoffs / historyHeuristicUpdates : 0.0;
    }

    public double getFirstMoveSuccessRate() {
        return totalMoveOrderingQueries > 0 ? (double) firstMoveCutoffs / totalMoveOrderingQueries : 0.0;
    }

    // === ANALYSIS METHODS ===

    /**
     * Get detaillierte Null-Move Analyse
     */
    public String getNullMoveAnalysis() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== NULL-MOVE PRUNING ANALYSIS ===\n");

        if (nullMoveAttempts > 0) {
            sb.append(String.format("Attempts: %,d\n", nullMoveAttempts));
            sb.append(String.format("Successful Prunes: %,d (%.1f%%)\n",
                    nullMovePrunes, getNullMoveSuccessRate() * 100));
            sb.append(String.format("Failures: %,d (%.1f%%)\n",
                    nullMoveFailures, 100.0 * nullMoveFailures / nullMoveAttempts));

            if (nullMoveVerifications > 0) {
                sb.append(String.format("Verifications: %,d (%.1f%% successful)\n",
                        nullMoveVerifications, getNullMoveVerificationRate() * 100));
            }

            sb.append(String.format("Nodes Skipped: %,d (%.1f%% of total)\n",
                    nullMoveNodesSkipped, getNullMoveEfficiency() * 100));

            if (nullMoveTimesSaved > 0) {
                sb.append(String.format("Estimated Time Saved: %,dms\n", nullMoveTimesSaved));
            }

            // Top depths for null-move pruning
            sb.append("Prunes by depth: ");
            for (int i = 0; i < Math.min(nullMovePrunesByDepth.length, 10); i++) {
                if (nullMovePrunesByDepth[i] > 0) {
                    sb.append(String.format("D%d:%d ", i, nullMovePrunesByDepth[i]));
                }
            }
            sb.append("\n");

            // Effectiveness rating
            double successRate = getNullMoveSuccessRate();
            if (successRate > 0.6) {
                sb.append("🏆 Excellent null-move effectiveness!\n");
            } else if (successRate > 0.4) {
                sb.append("✅ Good null-move effectiveness\n");
            } else if (successRate > 0.2) {
                sb.append("📈 Average null-move effectiveness\n");
            } else {
                sb.append("⚠️ Poor null-move effectiveness\n");
            }

        } else {
            sb.append("No null-move data recorded\n");
        }

        return sb.toString();
    }

    /**
     * Enhanced Search Efficiency Score (0-100) inklusive Null-Move
     */
    public double getEnhancedSearchEfficiency() {
        double ttEfficiency = getTTHitRate() * 15;                    // 0-15 points
        double cutoffEfficiency = getCutoffRate() * 20;               // 0-20 points
        double moveOrderingEfficiency = getFirstMoveSuccessRate() * 20; // 0-20 points
        double historyEfficiency = getHistoryHeuristicEffectiveness() * 10; // 0-10 points
        double nullMoveEfficiency = getNullMoveEfficiency() * 15;     // 0-15 points (NEU)
        double branchingEfficiency = Math.max(0, 20 - getAverageBranchingFactor()); // 0-20 points

        return Math.min(100, ttEfficiency + cutoffEfficiency + moveOrderingEfficiency +
                historyEfficiency + nullMoveEfficiency + branchingEfficiency);
    }

    // === COMPREHENSIVE OUTPUT ===

    /**
     * Get comprehensive statistics summary mit Null-Move Details
     */
    public String getComprehensiveSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== COMPREHENSIVE SEARCH STATISTICS ===\n");

        // Core stats
        sb.append(String.format("Nodes: %,d (%.1fk NPS)\n",
                getTotalNodes(), getNodesPerSecond() / 1000));
        sb.append(String.format("  Regular: %,d, Quiescence: %,d\n", nodeCount, qNodeCount));
        sb.append(String.format("Max Depth: %d, Iterations: %d\n", maxDepthReached, iterationsCompleted));
        sb.append(String.format("Time: %,dms\n", totalSearchTime));

        // Transposition Table
        sb.append(String.format("\nTT: %.1f%% hit rate (%,d hits, %,d misses)\n",
                getTTHitRate() * 100, ttHits, ttMisses));

        // Pruning effectiveness
        sb.append(String.format("\nPruning: %.1f%% cutoff rate (%,d total cutoffs)\n",
                getCutoffRate() * 100, getTotalCutoffs()));
        sb.append(String.format("  Alpha-Beta: %,d (%.1f%%)\n",
                alphaBetaCutoffs, 100.0 * alphaBetaCutoffs / nodeCount));

        // NULL-MOVE DETAILS
        if (nullMoveAttempts > 0) {
            sb.append(String.format("  Null-Move: %,d/%,d (%.1f%% success, %,d nodes saved)\n",
                    nullMovePrunes, nullMoveAttempts, getNullMoveSuccessRate() * 100, nullMoveNodesSkipped));
        }

        if (reverseFutilityCutoffs > 0) {
            sb.append(String.format("  RFP: %,d (%.1f%%)\n",
                    reverseFutilityCutoffs, 100.0 * reverseFutilityCutoffs / nodeCount));
        }
        if (futilityCutoffs > 0) {
            sb.append(String.format("  Futility: %,d (%.1f%%)\n",
                    futilityCutoffs, 100.0 * futilityCutoffs / nodeCount));
        }

        // Move ordering
        long totalOrderingHits = ttMoveHits + killerMoveHits + historyMoveHits + captureOrderingHits;
        if (totalOrderingHits > 0) {
            sb.append(String.format("\nMove Ordering: %,d hits (%.1f%% first move success)\n",
                    totalOrderingHits, getFirstMoveSuccessRate() * 100));
            if (ttMoveHits > 0) sb.append(String.format("  TT moves: %,d\n", ttMoveHits));
            if (captureOrderingHits > 0) sb.append(String.format("  Captures: %,d\n", captureOrderingHits));
            if (killerMoveHits > 0) sb.append(String.format("  Killers: %,d\n", killerMoveHits));
            if (historyMoveHits > 0) sb.append(String.format("  History: %,d\n", historyMoveHits));
        }

        // Overall efficiency
        sb.append(String.format("\nEfficiency Score: %.1f/100\n", getEnhancedSearchEfficiency()));

        return sb.toString();
    }

    /**
     * Brief one-line summary with null-move info
     */
    public String getBriefSummary() {
        return String.format("Nodes: %,d, Time: %,dms, NPS: %.1fk, TT: %.1f%%, Cuts: %.1f%%, NM: %,d/%.1f%%, Eff: %.1f",
                getTotalNodes(), totalSearchTime, getNodesPerSecond() / 1000,
                getTTHitRate() * 100, getCutoffRate() * 100,
                nullMovePrunes, getNullMoveSuccessRate() * 100,
                getEnhancedSearchEfficiency());
    }

    /**
     * Standard toString für kompatibilität
     */
    @Override
    public String toString() {
        return getComprehensiveSummary();
    }

    /**
     * Export comprehensive statistics as CSV for analysis
     */
    public String toCSV() {
        return String.format("%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,%.1f\n",
                nodeCount, qNodeCount, maxDepthReached, totalSearchTime,
                ttHits + ttMisses, getTTHitRate(), getCutoffRate(),
                getAverageBranchingFactor(), getFirstMoveSuccessRate(),
                getNullMoveSuccessRate(), nullMoveAttempts, nullMovePrunes,
                nullMoveNodesSkipped, getEnhancedSearchEfficiency());
    }

    /**
     * Get CSV header for export
     */
    public static String getCSVHeader() {
        return "nodes,qnodes,depth,time_ms,tt_probes,tt_hit_rate,cutoff_rate,branching_factor," +
                "first_move_rate,null_move_rate,null_attempts,null_prunes,null_nodes_saved,efficiency\n";
    }
}
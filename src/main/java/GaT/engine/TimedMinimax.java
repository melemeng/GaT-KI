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
 * FIXED TIMED MINIMAX - Node Count Bug repariert + Aggressivere Zeitnutzung
 *
 * KRITISCHE FIXES:
 * ✅ 1. Node counting funktioniert jetzt korrekt
 * ✅ 2. Echte Suche wird durchgeführt
 * ✅ 3. Aggressivere Zeitnutzung
 * ✅ 4. Bessere Tiefenkontrolle
 * ✅ 5. Echte NPS-Berechnung
 */
public class TimedMinimax {

    // === THREAD-SAFE COMPONENTS ===
    private static volatile Evaluator evaluator = new Evaluator();
    private static volatile MoveOrdering moveOrdering = new MoveOrdering();
    private static volatile TranspositionTable transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);
    private static volatile SearchStatistics statistics = SearchStatistics.getInstance();
    private static volatile SearchEngine searchEngine = new SearchEngine(evaluator, moveOrdering, transpositionTable, statistics);

    // === SEARCH STATE ===
    private static volatile long timeLimitMillis;
    private static volatile long startTime;

    // FIXED: Globale Node-Tracking Variablen
    private static volatile long globalNodesSearched = 0;
    private static volatile long currentDepthNodes = 0;

    // === MAIN INTERFACES ===

    public static Move findBestMoveUltimate(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveRobust(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.PVS_Q);
    }

    public static Move findBestMoveWithStrategy(GameState state, int maxDepth, long timeMillis,
                                                SearchConfig.SearchStrategy strategy) {
        return findBestMoveRobust(state, maxDepth, timeMillis, strategy);
    }

    // === CORE ROBUST SEARCH - NODE COUNT FIXED ===

    private static Move findBestMoveRobust(GameState state, int maxDepth, long timeMillis,
                                           SearchConfig.SearchStrategy strategy) {
        if (state == null) {
            System.err.println("❌ CRITICAL: Null game state!");
            return null;
        }

        if (strategy == null) {
            strategy = SearchConfig.SearchStrategy.ALPHA_BETA;
        }

        // FIXED: Vollständige Initialisierung mit Node-Tracking
        initializeSearchComponentsFixed(timeMillis, strategy);

        List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
        if (legalMoves.isEmpty()) {
            System.err.println("❌ No legal moves available!");
            return null;
        }

        Move emergencyMove = legalMoves.get(0);
        Move bestMove = emergencyMove;
        int bestDepth = 0;

        // FIXED: Echte Node-Zählung
        globalNodesSearched = 0;

        System.out.println("=== FIXED ROBUST SEARCH WITH NODE COUNTING ===");
        System.out.printf("Strategy: %s | Time: %dms | Legal moves: %d%n",
                strategy, timeMillis, legalMoves.size());

        // === ITERATIVE DEEPENING MIT KORREKTER NODE-ZÄHLUNG ===
        for (int depth = 1; depth <= maxDepth && !timedOut(); depth++) {
            long depthStartTime = System.currentTimeMillis();
            statistics.startDepth(depth);

            // FIXED: Reset node counter für diese Tiefe
            long nodesBefore = statistics.getNodeCount();
            currentDepthNodes = 0;

            try {
                // FIXED: Echte Suche mit korrekter Node-Zählung
                SearchResult result = searchDepthWithNodeCountingFixed(state, depth, strategy, legalMoves);

                if (result != null && result.move != null) {
                    bestMove = result.move;
                    bestDepth = depth;

                    // FIXED: Korrekte Node-Zählung
                    long depthNodes = statistics.getNodeCount() - nodesBefore;
                    globalNodesSearched += depthNodes;

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    statistics.endDepth(depth);

                    // FIXED: Echte NPS-Berechnung
                    double nps = depthTime > 0 ? (double)depthNodes * 1000 / depthTime : 0;

                    System.out.printf("✅ Depth %d: %s (score: %+d, time: %dms, nodes: %,d, nps: %.0f)%n",
                            depth, bestMove, result.score, depthTime, depthNodes, nps);

                    // FIXED: Weniger aggressive early termination
                    if (Math.abs(result.score) > 2000) {
                        System.out.println("🎯 Strong move found, terminating early");
                        break;
                    }

                    // FIXED: Aggressiveres Zeit-Management
                    if (!shouldContinueToNextDepthFixed(depthTime, timeMillis, depth)) {
                        System.out.println("⏱ Time management: Stopping search");
                        break;
                    }
                } else {
                    System.out.printf("⚠️ Depth %d failed, using depth %d result%n", depth, bestDepth);
                    break;
                }

            } catch (Exception e) {
                System.err.printf("❌ Error at depth %d: %s%n", depth, e.getMessage());
                if (bestMove == emergencyMove && bestDepth == 0) {
                    bestMove = searchSimpleFallback(state, legalMoves);
                }
                break;
            }
        }

        // === FINAL STATISTICS ===
        statistics.endSearch();
        long totalTime = System.currentTimeMillis() - startTime;

        System.out.println("=== FIXED SEARCH COMPLETE ===");
        System.out.printf("Best move: %s | Depth: %d | Time: %dms%n", bestMove, bestDepth, totalTime);
        System.out.printf("Total nodes: %,d | NPS: %,.0f%n", globalNodesSearched,
                totalTime > 0 ? (double)globalNodesSearched * 1000 / totalTime : 0);

        // Update legacy counter mit echten Nodes
        Minimax.counter = (int) globalNodesSearched;

        return bestMove != null ? bestMove : emergencyMove;
    }

    // === FIXED DEPTH SEARCH MIT KORREKTER NODE-ZÄHLUNG ===

    private static SearchResult searchDepthWithNodeCountingFixed(GameState state, int depth,
                                                                 SearchConfig.SearchStrategy strategy,
                                                                 List<Move> legalMoves) {
        // FIXED: Korrekte Move-Ordering
        orderMovesRobust(legalMoves, state, depth);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        // FIXED: Alpha-Beta Fenster für korrekte Suche
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        long depthNodesBefore = statistics.getNodeCount();

        int moveCount = 0;
        for (Move move : legalMoves) {
            if (timedOut()) break;

            moveCount++;
            try {
                GameState copy = state.copy();
                copy.applyMove(move);

                // FIXED: Echte Suche mit korrektem Alpha-Beta
                int score = searchMoveWithNodeTrackingFixed(copy, depth - 1, alpha, beta, !isRed, strategy);

                // FIXED: Korrekte Best-Move Logik
                if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                    bestScore = score;
                    bestMove = move;

                    // Alpha-Beta Update
                    if (isRed) {
                        alpha = Math.max(alpha, score);
                    } else {
                        beta = Math.min(beta, score);
                    }
                }

                // FIXED: Weniger aggressive early termination
                if (Math.abs(score) > 5000) {
                    System.out.printf("  🎯 Very strong move found: %s (score: %+d) after %d/%d moves%n",
                            move, score, moveCount, legalMoves.size());
                    break;
                }

            } catch (Exception e) {
                System.err.printf("  ⚠️ Error evaluating move %s: %s%n", move, e.getMessage());
                continue;
            }
        }

        long depthNodesAfter = statistics.getNodeCount();
        long depthNodes = depthNodesAfter - depthNodesBefore;

        return bestMove != null ? new SearchResult(bestMove, bestScore, depthNodes) : null;
    }

    /**
     * FIXED: Echter Search mit korrekter Node-Zählung
     */
    private static int searchMoveWithNodeTrackingFixed(GameState state, int depth, int alpha, int beta,
                                                       boolean maximizingPlayer, SearchConfig.SearchStrategy strategy) {
        try {
            // FIXED: Timeout-Checker setzen
            searchEngine.setTimeoutChecker(() -> timedOut());

            // FIXED: Echte Suche mit korrekten Parametern
            int result = searchEngine.search(state, depth, alpha, beta, maximizingPlayer, strategy);

            return result;

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Timeout")) {
                throw e;
            }
            // Fallback to evaluation
            return evaluator.evaluate(state, depth);
        } finally {
            searchEngine.clearTimeoutChecker();
        }
    }

    // === FIXED TIME MANAGEMENT ===

    /**
     * FIXED: Viel aggressiveres Zeit-Management
     */
    private static boolean shouldContinueToNextDepthFixed(long lastDepthTime, long totalTimeLimit, int currentDepth) {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = totalTimeLimit - elapsed;

        // FIXED: Weniger konservative Schätzung
        double growthFactor;
        if (currentDepth <= 3) {
            growthFactor = 2.0;  // War 2.5
        } else if (currentDepth <= 6) {
            growthFactor = 2.5;  // War 3.5
        } else {
            growthFactor = 3.0;  // War 5.0
        }

        long estimatedNextTime = (long)(lastDepthTime * growthFactor);

        // FIXED: Viel weniger konservativer Sicherheitspuffer
        double safetyBuffer = 0.6; // War 0.3-0.75

        boolean canContinue = estimatedNextTime < remaining * safetyBuffer;

        if (!canContinue) {
            System.out.printf("⏱️ Next depth %d estimated %dms > remaining %dms * %.1f%n",
                    currentDepth + 1, estimatedNextTime, remaining, safetyBuffer);
        }

        return canContinue;
    }

    /**
     * FIXED: Vollständige Initialisierung mit Node-Tracking
     */
    private static void initializeSearchComponentsFixed(long timeMillis, SearchConfig.SearchStrategy strategy) {
        startTime = System.currentTimeMillis();
        timeLimitMillis = timeMillis;

        // FIXED: Reset ALL counters
        globalNodesSearched = 0;
        currentDepthNodes = 0;

        // Reset statistics PROPERLY
        statistics.reset();
        statistics.startSearch();

        // Reset move ordering
        moveOrdering.resetKillerMoves();

        // Setup strategy-specific components
        if (strategy == SearchConfig.SearchStrategy.ALPHA_BETA_Q ||
                strategy == SearchConfig.SearchStrategy.PVS_Q) {
            QuiescenceSearch.setRemainingTime(timeMillis);
            QuiescenceSearch.resetQuiescenceStats();
        }

        if (strategy == SearchConfig.SearchStrategy.PVS ||
                strategy == SearchConfig.SearchStrategy.PVS_Q) {
            PVSSearch.setTimeoutChecker(() -> timedOut());
        }

        // Set evaluation time for adaptive evaluation
        Evaluator.setRemainingTime(timeMillis);

        System.out.println("🔧 Search components initialized with node tracking");
    }

    // === UTILITY METHODS ===

    private static boolean timedOut() {
        return System.currentTimeMillis() - startTime >= timeLimitMillis;
    }

    private static void orderMovesRobust(List<Move> moves, GameState state, int depth) {
        if (moves == null || moves.size() <= 1) return;

        try {
            TTEntry entry = null;
            try {
                long hash = state.hash();
                entry = transpositionTable.get(hash);
            } catch (Exception e) {
                // Ignore TT errors
            }

            moveOrdering.orderMoves(moves, state, depth, entry);

        } catch (Exception e) {
            System.err.printf("⚠️ Move ordering failed safely: %s%n", e.getMessage());
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

    private static Move searchSimpleFallback(GameState state, List<Move> legalMoves) {
        System.out.println("🚨 Using emergency fallback search");

        Move bestMove = legalMoves.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (Move move : legalMoves) {
            try {
                GameState copy = state.copy();
                copy.applyMove(move);

                int score = evaluator.evaluate(copy, 0);
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
            } catch (Exception e) {
                continue;
            }
        }

        System.out.printf("🚨 Fallback selected: %s (score: %+d)%n", bestMove, bestScore);
        return bestMove;
    }

    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    // === RESULT CLASSES ===

    private static class SearchResult {
        final Move move;
        final int score;
        final long nodesSearched;

        SearchResult(Move move, int score, long nodesSearched) {
            this.move = move;
            this.score = score;
            this.nodesSearched = nodesSearched;
        }
    }

    // === LEGACY COMPATIBILITY ===

    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveRobust(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.ALPHA_BETA);
    }

    public static Move findBestMoveWithPVS(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveRobust(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.PVS);
    }

    // === COMPONENT ACCESS ===

    public static SearchEngine getSearchEngine() { return searchEngine; }
    public static Evaluator getEvaluator() { return evaluator; }
    public static SearchStatistics getStatistics() { return statistics; }

    // FIXED: Echte Node-Count zurückgeben
    public static long getTotalNodesSearched() { return globalNodesSearched; }
}
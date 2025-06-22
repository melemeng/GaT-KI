package GaT.engine;

import GaT.model.*;
import GaT.search.*;
import GaT.evaluation.Evaluator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FINALE TIMED SEARCH ENGINE - Alle Verbesserungen implementiert
 *
 * VERBESSERUNGEN:
 * ✅ 1. EINZELNER KONSTRUKTOR (Original hatte 2 verwirrende Konstruktoren)
 * ✅ 2. THREAD-SAFE STATE (volatile + synchronized für shared state)
 * ✅ 3. OVERFLOW-SCHUTZ (Integer bounds checking bei aspiration windows)
 * ✅ 4. ROBUSTE EXCEPTION-BEHANDLUNG (Graceful fallbacks, keine crashes)
 * ✅ 5. INTELLIGENTERE ZEITSCHÄTZUNG (Bessere Heuristiken für nächste Tiefe)
 * ✅ 6. VERBESSERTE LOGGING (Mehr informative Debug-Ausgaben)
 * ✅ 7. KONSTANTEN KONSOLIDIERT (Keine Duplikate mehr)
 * ✅ 8. EMERGENCY FALLBACKS (Mehrschichtige Sicherheit)
 */
public class TimedSearchEngine {

    // === CORE COMPONENTS ===
    private final SearchEngine searchEngine;
    private final Evaluator evaluator;
    private final TimeManager timeManager;
    private final SearchStatistics statistics;

    // === SEARCH STATE (THREAD-SAFE) ===
    private final AtomicBoolean searchActive = new AtomicBoolean(false);
    private volatile long searchStartTime;
    private volatile long timeLimit;
    private volatile Move lastBestMove = null;
    private volatile int lastCompletedDepth = 0;
    private volatile boolean emergencyMode = false;

    // === VERBESSERUNG 1: EINZELNER KONSTRUKTOR ===
    // Original hatte 2 verwirrende Konstruktoren - jetzt nur noch einer
    public TimedSearchEngine(Evaluator evaluator, TimeManager timeManager) {
        this.searchEngine = new SearchEngine(
                evaluator,
                new MoveOrdering(),
                new TranspositionTable(SearchConfig.TT_SIZE),
                SearchStatistics.getInstance()
        );
        this.evaluator = evaluator;
        this.timeManager = timeManager;
        this.statistics = SearchStatistics.getInstance();

        System.out.println("🚀 TimedSearchEngine initialized with enhanced features");
    }

    /**
     * MAIN TOURNAMENT INTERFACE - Mit allen Verbesserungen
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

        // === VERBESSERUNG 2: ROBUSTE EMERGENCY-BEHANDLUNG ===
        if (emergencyMode) {
            System.out.println("🚨 EMERGENCY MODE: " + timeLimitMs + "ms");
            return handleEmergencySearch(state, strategy);
        }

        // === VERBESSERUNG 3: SICHERE ZEITBERECHNUNG ===
        // Original: timeManager.calculateOptimalTime() - Methode existierte nicht!
        // Neu: Verwende existierende calculateTimeForMove mit Sicherheitscheck
        long adaptiveTimeLimit;
        try {
            adaptiveTimeLimit = Math.min(timeLimitMs, timeManager.calculateTimeForMove(state));
            // Zusätzlicher Sicherheitscheck
            adaptiveTimeLimit = Math.max(200, Math.min(adaptiveTimeLimit, timeLimitMs * 85 / 100));
        } catch (Exception e) {
            System.err.println("⚠️ Time calculation failed, using safe default: " + e.getMessage());
            adaptiveTimeLimit = timeLimitMs / 2; // Safe fallback
        }

        System.out.printf("🕐 Time allocated: %dms (adaptive from %dms, emergency=%s)\n",
                adaptiveTimeLimit, timeLimitMs, emergencyMode);

        return performIterativeDeepening(state, adaptiveTimeLimit, strategy);
    }

    /**
     * VERBESSERUNG 4: THREAD-SAFE ITERATIVE DEEPENING
     */
    private SearchResult performIterativeDeepening(GameState state, long timeLimit,
                                                   SearchConfig.SearchStrategy strategy) {

        Move bestMove = null;
        Move lastCompleteMove = null;
        int bestScore = state.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        System.out.println("=== ENHANCED ITERATIVE DEEPENING ===");
        System.out.printf("Strategy: %s | Time: %dms | Emergency: %s\n",
                strategy, timeLimit, emergencyMode);

        // === DEPTH ITERATION WITH BETTER TIME MANAGEMENT ===
        for (int depth = 1; depth <= SearchConfig.MAX_DEPTH && searchActive.get(); depth++) {

            // VERBESSERUNG 5: INTELLIGENTERE STOP-LOGIK
            if (shouldStopSearchEnhanced(depth, timeLimit)) {
                System.out.printf("⏱ Enhanced time management: Stopping at depth %d\n", depth);
                break;
            }

            long depthStartTime = System.currentTimeMillis();
            statistics.startDepth(depth);

            try {
                SearchResult result;

                // VERBESSERUNG 6: SICHERE ASPIRATION WINDOWS
                if (depth >= 4 && lastCompleteMove != null && !emergencyMode && timeLimit > 2000) {
                    result = searchWithAspirationWindowsEnhanced(state, depth, strategy, bestScore);
                } else {
                    result = searchAtDepth(state, depth, strategy);
                }

                if (result != null && result.isComplete()) {
                    // VERBESSERUNG 7: THREAD-SAFE STATE UPDATE
                    synchronized (this) {
                        lastCompleteMove = result.getBestMove();
                        bestMove = result.getBestMove();
                        bestScore = result.getScore();
                        lastCompletedDepth = depth;
                        lastBestMove = result.getBestMove();
                    }

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    statistics.endDepth(depth);

                    System.out.printf("✓ Depth %d: %s (score: %+d, time: %dms, nodes: %,d, nps: %.1fk)\n",
                            depth, bestMove, bestScore, depthTime, statistics.getNodeCount(),
                            statistics.getNodeCount() / Math.max(1.0, depthTime));

                    // === ENHANCED EARLY TERMINATION ===
                    if (result.isWinningScore()) {
                        System.out.println("🎯 Winning move found - terminating search!");
                        break;
                    }

                    if (result.isForcedMate()) {
                        System.out.println("♔ Forced mate detected - terminating search!");
                        break;
                    }

                    // VERBESSERUNG 8: BESSERE ZEIT-VORHERSAGE
                    if (!canCompleteNextDepthEnhanced(depthTime, depth, timeLimit)) {
                        System.out.printf("⚡ Enhanced prediction: Next depth %d unlikely to complete\n", depth + 1);
                        break;
                    }

                } else {
                    System.out.printf("⏱ Depth %d incomplete or failed\n", depth);
                    break;
                }

            } catch (SearchTimeoutException e) {
                System.out.printf("⏱ Timeout at depth %d\n", depth);
                break;
            } catch (Exception e) {
                System.err.printf("❌ Error at depth %d: %s\n", depth, e.getMessage());
                e.printStackTrace();
                // VERBESSERUNG 9: Weiter versuchen bei nicht-kritischen Fehlern
                if (bestMove != null) {
                    System.out.println("🔄 Continuing with best move found so far");
                    break;
                } else {
                    System.err.println("🚨 Critical error with no fallback - using emergency search");
                    return handleEmergencySearch(state, SearchConfig.SearchStrategy.ALPHA_BETA);
                }
            }
        }

        // === FINAL RESULT WITH VALIDATION ===
        statistics.endSearch();
        searchActive.set(false);

        Move finalMove = bestMove != null ? bestMove : lastCompleteMove;
        long totalTime = System.currentTimeMillis() - searchStartTime;

        // VERBESSERUNG 10: VALIDIERUNG DES FINAL MOVES
        if (finalMove == null) {
            System.err.println("🚨 No move found - using emergency fallback");
            return createEmergencyFallbackResult(state);
        }

        System.out.println("=== ENHANCED SEARCH COMPLETED ===");
        System.out.printf("Final move: %s | Depth: %d | Time: %dms | Efficiency: %.1f%%\n",
                finalMove, lastCompletedDepth, totalTime,
                100.0 * statistics.getTotalNodes() / Math.max(1, totalTime));
        System.out.println(statistics.getBriefSummary());

        return new SearchResult(finalMove, bestScore, lastCompletedDepth,
                totalTime, statistics.getTotalNodes(), true);
    }

    /**
     * VERBESSERUNG 11: ENHANCED EMERGENCY SEARCH
     */
    private SearchResult handleEmergencySearch(GameState state, SearchConfig.SearchStrategy strategy) {
        System.out.println("🚨 ENHANCED EMERGENCY MODE");

        try {
            // Sehr flache, aber schnelle Suche
            SearchResult result = searchAtDepth(state, 1, SearchConfig.SearchStrategy.ALPHA_BETA);

            if (result != null && result.getBestMove() != null) {
                long totalTime = System.currentTimeMillis() - searchStartTime;
                System.out.printf("🚨 Emergency search successful: %s (%dms)\n",
                        result.getBestMove(), totalTime);
                return result;
            }
        } catch (Exception e) {
            System.err.println("🚨 Emergency search failed: " + e.getMessage());
        }

        // Last resort fallback
        return createEmergencyFallbackResult(state);
    }

    /**
     * VERBESSERUNG 12: SICHERE ASPIRATION WINDOWS MIT OVERFLOW-SCHUTZ
     */
    private SearchResult searchWithAspirationWindowsEnhanced(GameState state, int depth,
                                                             SearchConfig.SearchStrategy strategy,
                                                             int previousScore) {

        // OVERFLOW-SCHUTZ: Sichere Bounds für aspiration windows
        int safeMin = Integer.MIN_VALUE + 10000;
        int safeMax = Integer.MAX_VALUE - 10000;

        int alpha = Math.max(safeMin, previousScore - SearchConfig.ASPIRATION_WINDOW_DELTA);
        int beta = Math.min(safeMax, previousScore + SearchConfig.ASPIRATION_WINDOW_DELTA);
        int delta = SearchConfig.ASPIRATION_WINDOW_DELTA;

        System.out.printf("🔍 Aspiration search: [%d, %d] around %d\n", alpha, beta, previousScore);

        for (int attempt = 0; attempt < SearchConfig.ASPIRATION_WINDOW_MAX_FAILS; attempt++) {

            try {
                SearchResult result = searchWithWindow(state, depth, alpha, beta, strategy);

                if (result == null) {
                    System.out.println("⚠️ Null result from aspiration search");
                    break;
                }

                int score = result.getScore();

                // Check bounds with enhanced logging
                if (score <= alpha) {
                    delta = Math.min(delta * SearchConfig.ASPIRATION_WINDOW_GROWTH_FACTOR, 5000);
                    alpha = Math.max(safeMin, previousScore - delta);
                    System.out.printf("⚠️ Fail low (%d <= %d), widening to [%d, %d]\n",
                            score, alpha + delta, alpha, beta);
                    continue;

                } else if (score >= beta) {
                    delta = Math.min(delta * SearchConfig.ASPIRATION_WINDOW_GROWTH_FACTOR, 5000);
                    beta = Math.min(safeMax, previousScore + delta);
                    System.out.printf("⚠️ Fail high (%d >= %d), widening to [%d, %d]\n",
                            score, beta - delta, alpha, beta);
                    continue;

                } else {
                    System.out.printf("✓ Aspiration success: %d in [%d, %d]\n", score, alpha, beta);
                    return result;
                }

            } catch (SearchTimeoutException e) {
                System.out.println("⏱️ Timeout during aspiration search");
                throw e;
            } catch (Exception e) {
                System.err.println("❌ Error in aspiration search: " + e.getMessage());
                break;
            }
        }

        System.out.println("⚠️ Aspiration windows exhausted, falling back to full search");
        return searchAtDepth(state, depth, strategy);
    }

    /**
     * VERBESSERUNG 13: ROBUSTE WINDOW-SEARCH
     */
    private SearchResult searchWithWindow(GameState state, int depth, int alpha, int beta,
                                          SearchConfig.SearchStrategy strategy) {

        searchEngine.setTimeoutChecker(this::shouldAbortSearch);

        try {
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            if (moves.isEmpty()) {
                return createNoMovesResult();
            }

            Move bestMove = null;
            int bestScore = state.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            boolean isRed = state.redToMove;
            int moveCount = 0;

            for (Move move : moves) {
                moveCount++;

                // Häufigere Timeout-Checks
                if (moveCount % 5 == 0 && shouldAbortSearch()) {
                    throw new SearchTimeoutException();
                }

                try {
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

                    // Early cutoff
                    if (beta <= alpha) {
                        System.out.printf("✂️ Cutoff after move %d/%d\n", moveCount, moves.size());
                        break;
                    }

                } catch (Exception e) {
                    System.err.printf("⚠️ Error evaluating move %s: %s\n", move, e.getMessage());
                    // Continue with other moves
                    continue;
                }
            }

            return new SearchResult(bestMove, bestScore, depth,
                    System.currentTimeMillis() - searchStartTime,
                    statistics.getTotalNodes(), true);

        } catch (SearchTimeoutException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("❌ Critical error in window search: " + e.getMessage());
            return createEmergencyFallbackResult(state);
        } finally {
            searchEngine.clearTimeoutChecker();
        }
    }

    // === VERBESSERUNG 14: INTELLIGENTERE ZEIT-HEURISTIKEN ===

    private boolean shouldStopSearchEnhanced(int depth, long timeLimit) {
        long elapsed = System.currentTimeMillis() - searchStartTime;
        double timeUsedRatio = (double) elapsed / timeLimit;

        // Adaptive thresholds basierend auf emergency mode
        double stopThreshold = emergencyMode ? 0.7 : 0.8;
        double deepSearchThreshold = emergencyMode ? 0.4 : 0.6;

        if (timeUsedRatio >= stopThreshold) {
            return true;
        }

        // Vermeide tiefe Suchen wenn wenig Zeit
        if (depth >= 6 && timeUsedRatio >= deepSearchThreshold) {
            return true;
        }

        // Sehr tiefe Suchen nur mit viel Zeit
        if (depth >= 10 && timeUsedRatio >= 0.4) {
            return true;
        }

        return false;
    }

    private boolean canCompleteNextDepthEnhanced(long lastDepthTime, int currentDepth, long timeLimit) {
        long elapsed = System.currentTimeMillis() - searchStartTime;
        long remaining = timeLimit - elapsed;

        // Bessere Wachstums-Vorhersage basierend auf Tiefe
        double growthFactor;
        if (currentDepth <= 3) {
            growthFactor = 2.5;  // Frühe Tiefen wachsen moderat
        } else if (currentDepth <= 6) {
            growthFactor = 3.5;  // Mittlere Tiefen wachsen schneller
        } else {
            growthFactor = 5.0;  // Tiefe Suchen explodieren
        }

        long estimatedNextTime = (long)(lastDepthTime * growthFactor);

        // Puffer basierend auf Spielsituation
        double safetyBuffer = emergencyMode ? 0.5 : 0.75;

        boolean canComplete = estimatedNextTime < remaining * safetyBuffer;

        if (!canComplete) {
            System.out.printf("⏱️ Next depth %d estimated %dms > remaining %dms * %.1f\n",
                    currentDepth + 1, estimatedNextTime, remaining, safetyBuffer);
        }

        return canComplete;
    }

    private boolean shouldAbortSearch() {
        if (!searchActive.get()) {
            return true;
        }

        long elapsed = System.currentTimeMillis() - searchStartTime;
        boolean timeout = elapsed >= timeLimit;

        if (timeout) {
            System.out.printf("⏱️ Search timeout: %dms >= %dms\n", elapsed, timeLimit);
        }

        return timeout;
    }

    // === VERBESSERUNG 15: ROBUSTE FALLBACK-MECHANISMEN ===

    private SearchResult createEmergencyFallbackResult(GameState state) {
        System.out.println("🆘 Creating emergency fallback result");

        try {
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            Move fallbackMove = null;

            if (!moves.isEmpty()) {
                // Intelligente Fallback-Auswahl
                for (Move move : moves) {
                    // 1. Bevorzuge Schlagzüge
                    if (Minimax.isCapture(move, state)) {
                        fallbackMove = move;
                        System.out.println("🆘 Emergency: Using capture " + move);
                        break;
                    }
                }

                // 2. Falls keine Schlagzüge, bevorzuge Wächter-Züge
                if (fallbackMove == null) {
                    boolean isRed = state.redToMove;
                    long guardBit = isRed ? state.redGuard : state.blueGuard;
                    if (guardBit != 0) {
                        int guardPos = Long.numberOfTrailingZeros(guardBit);
                        for (Move move : moves) {
                            if (move.from == guardPos) {
                                fallbackMove = move;
                                System.out.println("🆘 Emergency: Using guard move " + move);
                                break;
                            }
                        }
                    }
                }

                // 3. Letzter Ausweg: irgendein Zug
                if (fallbackMove == null) {
                    fallbackMove = moves.get(0);
                    System.out.println("🆘 Emergency: Using first legal move " + fallbackMove);
                }
            }

            return new SearchResult(fallbackMove, 0, 0,
                    System.currentTimeMillis() - searchStartTime, 1, false);

        } catch (Exception e) {
            System.err.println("🆘 Critical: Even emergency fallback failed: " + e.getMessage());
            return new SearchResult(null, 0, 0, 0, 0, false);
        }
    }

    private SearchResult createNoMovesResult() {
        return new SearchResult(null, evaluator.evaluate(new GameState(), 0), 0,
                System.currentTimeMillis() - searchStartTime, 1, true);
    }

    private SearchResult searchAtDepth(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        return searchWithWindow(state, depth, Integer.MIN_VALUE, Integer.MAX_VALUE, strategy);
    }

    // === PUBLIC CONTROL METHODS ===

    public void stopSearch() {
        searchActive.set(false);
        System.out.println("🛑 Enhanced search stopped by request");
    }

    public boolean isSearching() {
        return searchActive.get();
    }

    public SearchStatistics getCurrentStatistics() {
        return statistics;
    }

    public synchronized Move getLastBestMove() {
        return lastBestMove;
    }

    public synchronized int getLastCompletedDepth() {
        return lastCompletedDepth;
    }

    // === SEARCH RESULT CLASS (UNCHANGED) ===

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
            super("Enhanced search timeout");
        }
    }
}
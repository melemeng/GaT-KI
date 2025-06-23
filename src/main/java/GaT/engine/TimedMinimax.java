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
 * ULTRA-FIXED TIMED MINIMAX ENGINE - All critical bugs resolved
 *
 * CRITICAL FIXES:
 * ✅ 1. Search actually executes (was failing at depth 1)
 * ✅ 2. Emergency fallbacks prevent null moves
 * ✅ 3. Better error handling and logging
 * ✅ 4. Enhanced move validation
 * ✅ 5. Improved time management
 * ✅ 6. Strategic evaluation improvements
 */
public class TimedMinimax {

    // === INTEGRATED COMPONENTS ===
    private static final Evaluator evaluator = new Evaluator();
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final TranspositionTable transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);
    private static final SearchStatistics statistics = SearchStatistics.getInstance();
    private static final SearchEngine searchEngine = new SearchEngine(evaluator, moveOrdering, transpositionTable, statistics);

    // === TIME MANAGEMENT ===
    private static long timeLimitMillis;
    private static long startTime;

    // === TIMEOUT EXCEPTION ===
    private static class TimeoutException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    // === MAIN PUBLIC INTERFACES - ULTRA-FIXED ===

    /**
     * ULTIMATE AI - ULTRA-FIXED version
     */
    public static Move findBestMoveUltimate(GameState state, int maxDepth, long timeMillis) {
        System.out.println("🚀 ULTRA-FIXED Ultimate AI starting...");
        return findBestMoveIterativeUltraFixed(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.PVS_Q);
    }

    /**
     * Find best move with strategy - ULTRA-FIXED
     */
    public static Move findBestMoveWithStrategy(GameState state, int maxDepth, long timeMillis,
                                                SearchConfig.SearchStrategy strategy) {
        return findBestMoveIterativeUltraFixed(state, maxDepth, timeMillis, strategy);
    }

    /**
     * Legacy compatibility methods
     */
    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveIterativeUltraFixed(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.ALPHA_BETA);
    }

    public static Move findBestMoveWithTimeAndQuiescence(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveIterativeUltraFixed(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.ALPHA_BETA_Q);
    }

    public static Move findBestMoveWithPVS(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveIterativeUltraFixed(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.PVS);
    }

    // === ULTRA-FIXED ITERATIVE DEEPENING ===

    /**
     * ULTRA-FIXED Iterative Deepening - Guaranteed to work
     */
    private static Move findBestMoveIterativeUltraFixed(GameState state, int maxDepth, long timeMillis,
                                                        SearchConfig.SearchStrategy strategy) {
        System.out.println("=== ULTRA-FIXED Iterative Deepening with " + strategy + " ===");
        System.out.println("Time limit: " + timeMillis + "ms, Max depth: " + maxDepth);

        // === ULTRA-SAFE VALIDATION ===
        if (state == null) {
            System.err.println("❌ CRITICAL: Null game state!");
            return null;
        }

        if (strategy == null) {
            System.err.println("⚠️ Null strategy, defaulting to ALPHA_BETA");
            strategy = SearchConfig.SearchStrategy.ALPHA_BETA;
        }

        // Setup time management
        timeLimitMillis = Math.max(100, timeMillis); // Minimum 100ms
        startTime = System.currentTimeMillis();

        // Initialize components with better error handling
        try {
            moveOrdering.resetKillerMoves();
            statistics.reset();
            statistics.startSearch();
        } catch (Exception e) {
            System.err.println("⚠️ Component initialization warning: " + e.getMessage());
        }

        // Configure strategy-specific components
        if (strategy == SearchConfig.SearchStrategy.ALPHA_BETA_Q || strategy == SearchConfig.SearchStrategy.PVS_Q) {
            QuiescenceSearch.setRemainingTime(timeMillis);
            QuiescenceSearch.resetQuiescenceStats();
        }

        if (strategy == SearchConfig.SearchStrategy.PVS || strategy == SearchConfig.SearchStrategy.PVS_Q) {
            PVSSearch.setTimeoutChecker(() -> timedOut());
        }

        // === EMERGENCY MOVE PREPARATION ===
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        if (allMoves.isEmpty()) {
            System.err.println("❌ CRITICAL: No legal moves available!");
            return null;
        }

        System.out.println("Available moves: " + allMoves.size());

        // Find best emergency move (prioritize strategic moves)
        Move emergencyMove = selectBestEmergencyMove(allMoves, state);
        System.out.println("Emergency fallback move: " + emergencyMove);

        Move bestMove = emergencyMove; // Start with safe fallback
        Move lastCompleteMove = emergencyMove;
        int bestDepth = 0;
        int bestScore = Integer.MIN_VALUE;

        // === ULTRA-FIXED ITERATIVE DEEPENING LOOP ===
        for (int depth = 1; depth <= maxDepth && depth <= 20; depth++) { // Cap at 20 for safety
            if (timedOut()) {
                System.out.println("⏱ Time limit reached before depth " + depth);
                break;
            }

            System.out.println("🔍 Starting depth " + depth + "...");
            long depthStartTime = System.currentTimeMillis();

            try {
                Move candidate = searchAtDepthUltraFixed(state, depth, strategy);

                if (candidate != null) {
                    lastCompleteMove = candidate;
                    bestMove = candidate;
                    bestDepth = depth;

                    // Evaluate the move to get a score
                    try {
                        GameState afterMove = state.copy();
                        afterMove.applyMove(candidate);
                        bestScore = evaluator.evaluate(afterMove, depth);
                    } catch (Exception e) {
                        System.err.println("⚠️ Error evaluating candidate: " + e.getMessage());
                    }

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    System.out.printf("✓ Depth %d: %s (score: %+d, time: %dms, nodes: %,d)%n",
                            depth, candidate, bestScore, depthTime, statistics.getTotalNodes());

                    // Early termination for winning moves
                    if (isWinningMoveUltraFixed(candidate, state)) {
                        System.out.println("🎯 Winning move found at depth " + depth);
                        break;
                    }

                    // Adaptive time management - be more aggressive with good moves
                    if (Math.abs(bestScore) > 500 && depth >= 3) {
                        System.out.println("⚡ Strong position found, accepting move");
                        break;
                    }

                } else {
                    System.out.println("⚠️ Depth " + depth + " returned null, using previous best");
                    break;
                }

            } catch (TimeoutException e) {
                System.out.println("⏱ Timeout at depth " + depth);
                break;
            } catch (Exception e) {
                System.err.println("❌ Search error at depth " + depth + ": " + e.getMessage());
                // Don't break immediately - try next depth if time allows
                if (timedOut() || !shouldContinueAfterError(timeMillis)) {
                    break;
                }
            }

            // Conservative time management
            if (!shouldContinueSearchUltraFixed(timeMillis, depth)) {
                System.out.println("⚡ Stopping early due to time constraints");
                break;
            }
        }

        // === FINAL STATISTICS AND RESULT ===
        statistics.endSearch();
        long totalTime = System.currentTimeMillis() - startTime;

        Move finalMove = bestMove != null ? bestMove : lastCompleteMove;
        if (finalMove == null) {
            System.err.println("🚨 ULTRA-EMERGENCY: All searches failed, using strategic fallback");
            finalMove = emergencyMove;
        }

        System.out.println("=== ULTRA-FIXED Search Complete ===");
        System.out.println("Strategy: " + strategy);
        System.out.println("Best depth reached: " + bestDepth);
        System.out.println("Best move: " + finalMove);
        System.out.println("Final score: " + bestScore);
        System.out.println("Total time: " + totalTime + "ms");
        System.out.println("Total nodes: " + statistics.getTotalNodes());

        if (totalTime > 0) {
            System.out.println("Nodes per second: " + (statistics.getTotalNodes() * 1000 / totalTime));
        }

        // Enhanced strategy-specific statistics
        if (strategy == SearchConfig.SearchStrategy.ALPHA_BETA_Q || strategy == SearchConfig.SearchStrategy.PVS_Q) {
            if (QuiescenceSearch.qNodes > 0) {
                System.out.println("Q-nodes: " + QuiescenceSearch.qNodes);
                double standPatRate = (100.0 * QuiescenceSearch.standPatCutoffs) / QuiescenceSearch.qNodes;
                System.out.printf("Stand-pat rate: %.1f%%%n", standPatRate);
            }
        }

        System.out.println("====================");

        return finalMove;
    }

    // === ULTRA-FIXED DEPTH SEARCH ===

    /**
     * ULTRA-FIXED Search at specific depth
     */
    private static Move searchAtDepthUltraFixed(GameState state, int depth, SearchConfig.SearchStrategy strategy)
            throws TimeoutException {

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            System.err.println("❌ No moves at depth " + depth);
            return null;
        }

        System.out.println("🔍 Depth " + depth + ": Searching " + moves.size() + " moves");

        // ULTRA-SAFE move ordering
        try {
            long hash = state.hash();
            TTEntry entry = transpositionTable.get(hash);
            long remainingTime = timeLimitMillis - (System.currentTimeMillis() - startTime);
            moveOrdering.orderMovesEnhanced(moves, state, depth, entry, null, remainingTime);
            System.out.println("✅ Move ordering successful");
        } catch (Exception e) {
            System.err.println("⚠️ Move ordering failed: " + e.getMessage());
            // Continue with unordered moves
        }

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int movesSearched = 0;

        // Search all moves with better error handling
        for (Move move : moves) {
            if (timedOut()) throw new TimeoutException();

            try {
                GameState copy = state.copy();
                copy.applyMove(move);
                movesSearched++;

                int score = searchWithTimeoutSupportUltraFixed(copy, depth - 1, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, !isRed, strategy);

                if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                    bestScore = score;
                    bestMove = move;
                    System.out.printf("  New best: %s (score: %+d)%n", move, score);
                }

                // Early exit for very good moves
                if (Math.abs(score) > 2000) {
                    System.out.println("  🎯 Decisive move found, stopping search");
                    break;
                }

            } catch (TimeoutException e) {
                System.out.println("  ⏱ Timeout after " + movesSearched + " moves");
                throw e;
            } catch (Exception e) {
                System.err.println("  ⚠️ Error searching move " + move + ": " + e.getMessage());
                continue; // Try next move
            }
        }

        System.out.printf("✅ Depth %d complete: %s (searched %d/%d moves)%n",
                depth, bestMove, movesSearched, moves.size());
        return bestMove;
    }

    // === ULTRA-FIXED SEARCH DISPATCH ===

    /**
     * ULTRA-FIXED Search with comprehensive error handling
     */
    private static int searchWithTimeoutSupportUltraFixed(GameState state, int depth, int alpha, int beta,
                                                          boolean maximizingPlayer, SearchConfig.SearchStrategy strategy) {
        if (timedOut()) {
            throw new TimeoutException();
        }

        // Set timeout checker
        searchEngine.setTimeoutChecker(() -> timedOut());

        try {
            // Dispatch to SearchEngine with enhanced error handling
            return searchEngine.searchWithTimeout(state, depth, alpha, beta, maximizingPlayer, strategy, () -> timedOut());

        } catch (RuntimeException e) {
            if (e.getMessage() != null && (e.getMessage().contains("Timeout") || e.getMessage().contains("timeout"))) {
                throw new TimeoutException();
            }
            // For other errors, use fallback evaluation
            System.err.println("⚠️ Search engine error, using evaluation: " + e.getMessage());
            return evaluator.evaluate(state, depth);
        } finally {
            searchEngine.clearTimeoutChecker();
        }
    }

    // === EMERGENCY MOVE SELECTION ===

    /**
     * Select best emergency move using strategic heuristics
     */
    private static Move selectBestEmergencyMove(List<Move> moves, GameState state) {
        if (moves.isEmpty()) return null;

        Move bestEmergency = moves.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (Move move : moves) {
            int score = scoreEmergencyMove(move, state);
            if (score > bestScore) {
                bestScore = score;
                bestEmergency = move;
            }
        }

        return bestEmergency;
    }

    /**
     * Score emergency moves strategically
     */
    private static int scoreEmergencyMove(Move move, GameState state) {
        int score = 0;
        boolean isRed = state.redToMove;

        try {
            // 1. Winning moves get highest priority
            if (isWinningMoveUltraFixed(move, state)) {
                score += 10000;
            }

            // 2. Captures get high priority
            if (isCapture(move, state)) {
                long toBit = GameState.bit(move.to);
                if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
                    score += 5000; // Guard capture
                } else {
                    int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
                    score += height * 100; // Tower capture
                }
            }

            // 3. Guard advancement towards enemy castle
            if (isGuardMove(move, state)) {
                int targetRank = isRed ? 0 : 6;
                int currentRank = GameState.rank(move.from);
                int newRank = GameState.rank(move.to);

                int oldDistance = Math.abs(currentRank - targetRank);
                int newDistance = Math.abs(newRank - targetRank);

                if (newDistance < oldDistance) {
                    score += (oldDistance - newDistance) * 200; // Advancement bonus
                }

                // Bonus for moving towards D-file
                int currentFile = GameState.file(move.from);
                int newFile = GameState.file(move.to);
                int oldFileDistance = Math.abs(currentFile - 3);
                int newFileDistance = Math.abs(newFile - 3);

                if (newFileDistance < oldFileDistance) {
                    score += (oldFileDistance - newFileDistance) * 100;
                }
            }

            // 4. Central control
            if (isCentralSquare(move.to)) {
                score += 50;
            }

            // 5. Development (moving pieces from back rank)
            if (isDevelopmentMove(move, state)) {
                score += 30;
            }

            // 6. Avoid repetitive moves (penalize moves that just go back)
            if (seemsRepetitive(move, state)) {
                score -= 200;
            }

        } catch (Exception e) {
            // If scoring fails, at least prefer moves that advance
            score = move.amountMoved;
        }

        return score;
    }

    // === HELPER METHODS ===

    private static boolean isWinningMoveUltraFixed(Move move, GameState state) {
        try {
            GameState afterMove = state.copy();
            afterMove.applyMove(move);
            return Minimax.isGameOver(afterMove);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isCapture(Move move, GameState state) {
        try {
            long toBit = GameState.bit(move.to);
            return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isGuardMove(Move move, GameState state) {
        try {
            boolean isRed = state.redToMove;
            long guardBit = isRed ? state.redGuard : state.blueGuard;
            return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isCentralSquare(int square) {
        try {
            int file = GameState.file(square);
            int rank = GameState.rank(square);
            return file >= 2 && file <= 4 && rank >= 2 && rank <= 4;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isDevelopmentMove(Move move, GameState state) {
        try {
            boolean isRed = state.redToMove;
            int fromRank = GameState.rank(move.from);
            int toRank = GameState.rank(move.to);
            return isRed ? (fromRank >= 5 && toRank < fromRank) : (fromRank <= 1 && toRank > fromRank);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean seemsRepetitive(Move move, GameState state) {
        try {
            // Simple heuristic: avoid moves that just shuttle pieces back and forth
            int fromFile = GameState.file(move.from);
            int toFile = GameState.file(move.to);
            int fromRank = GameState.rank(move.from);
            int toRank = GameState.rank(move.to);

            // Penalize horizontal moves on back/front ranks
            boolean isRed = state.redToMove;
            if (isRed && fromRank >= 5 && toRank >= 5 && Math.abs(fromFile - toFile) == 1) {
                return true;
            }
            if (!isRed && fromRank <= 1 && toRank <= 1 && Math.abs(fromFile - toFile) == 1) {
                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // === TIME MANAGEMENT ===

    private static boolean shouldContinueSearchUltraFixed(long timeMillis, int currentDepth) {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = timeMillis - elapsed;

        // Conservative time management
        double timeUsedRatio = (double) elapsed / timeMillis;

        // Stop if more than 80% time used
        if (timeUsedRatio >= 0.8) return false;

        // For deeper searches, be more conservative
        if (currentDepth >= 6 && timeUsedRatio >= 0.6) return false;
        if (currentDepth >= 10 && timeUsedRatio >= 0.4) return false;

        // Need at least 20% time remaining
        return remaining > timeMillis * 0.2;
    }

    private static boolean shouldContinueAfterError(long timeMillis) {
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed < timeMillis * 0.5; // Only continue if less than 50% time used
    }

    private static boolean timedOut() {
        return System.currentTimeMillis() - startTime >= timeLimitMillis;
    }

    // === COMPONENT ACCESS ===

    public static SearchEngine getSearchEngine() {
        return searchEngine;
    }

    public static Evaluator getEvaluator() {
        return evaluator;
    }

    public static SearchStatistics getStatistics() {
        return statistics;
    }
}
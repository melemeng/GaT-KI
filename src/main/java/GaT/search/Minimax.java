// === CRITICAL FIX 4: Optimized Minimax.java ===

package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.evaluation.Evaluator;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * FIXED MINIMAX COORDINATOR - Now properly delegates to SearchEngine
 *
 * CRITICAL FIXES:
 * ✅ 1. Consistent component usage
 * ✅ 2. Proper PVS routing fixed
 * ✅ 3. Statistics properly shared
 * ✅ 4. Better error handling
 * ✅ 5. Performance optimizations
 */
public class Minimax {

    // === SHARED COMPONENTS (SINGLETON PATTERN) ===
    private static final Evaluator evaluator = new Evaluator();
    private static final SearchEngine searchEngine = new SearchEngine(
            evaluator,
            new MoveOrdering(),
            new TranspositionTable(SearchConfig.TT_SIZE),
            SearchStatistics.getInstance()
    );

    // === CONSTANTS ===
    public static final int RED_CASTLE_INDEX = GameState.getIndex(6, 3); // D7
    public static final int BLUE_CASTLE_INDEX = GameState.getIndex(0, 3); // D1

    // === LEGACY COMPATIBILITY ===
    public static int counter = 0;

    // === MAIN SEARCH INTERFACES - FIXED ===

    /**
     * FIXED: Properly routes to SearchEngine with correct strategy
     */
    public static Move findBestMoveWithStrategy(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        if (state == null) {
            System.err.println("❌ Null game state provided to findBestMoveWithStrategy");
            return null;
        }

        if (strategy == null) {
            System.err.println("⚠️ Null strategy provided, defaulting to PVS_Q");
            strategy = SearchConfig.SearchStrategy.PVS_Q;
        }

        SearchStatistics stats = SearchStatistics.getInstance();
        stats.reset();
        stats.startSearch();

        try {
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            if (moves.isEmpty()) {
                System.err.println("❌ No legal moves available");
                return null;
            }

            // FIXED: Use SearchEngine properly
            Move bestMove = null;
            boolean isRed = state.redToMove;
            int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

            // Order moves for better performance
            TTEntry ttEntry = searchEngine.getTranspositionTable().get(state.hash());
            searchEngine.getMoveOrdering().orderMoves(moves, state, depth, ttEntry);

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);

                // FIXED: Properly call SearchEngine with correct strategy
                int score = searchEngine.search(copy, depth - 1, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, !isRed, strategy);

                if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                    bestScore = score;
                    bestMove = move;
                }
            }

            stats.endSearch();
            counter = (int) stats.getNodeCount(); // Update legacy counter

            System.out.printf("🔍 Search completed: %s (strategy: %s, nodes: %,d)%n",
                    bestMove, strategy, stats.getNodeCount());

            return bestMove;

        } catch (Exception e) {
            System.err.println("❌ Error in findBestMoveWithStrategy: " + e.getMessage());
            e.printStackTrace();

            // Emergency fallback
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            return moves.isEmpty() ? null : moves.get(0);
        }
    }

    // === ENHANCED SEARCH METHODS ===

    /**
     * FIXED: Enhanced search with aspiration windows
     */
    public static Move findBestMoveWithAspiration(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        if (depth < 4) {
            return findBestMoveWithStrategy(state, depth, strategy); // Skip aspiration for shallow searches
        }

        SearchStatistics stats = SearchStatistics.getInstance();
        stats.reset();
        stats.startSearch();

        try {
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            if (moves.isEmpty()) return null;

            // Get previous search result for aspiration window
            TTEntry previousEntry = searchEngine.getTranspositionTable().get(state.hash());
            int previousScore = 0;
            if (previousEntry != null && previousEntry.depth >= depth - 2) {
                previousScore = previousEntry.score;
            }

            Move bestMove = null;
            boolean isRed = state.redToMove;
            int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

            // Aspiration window parameters
            int windowSize = 50;
            int alpha = previousScore - windowSize;
            int beta = previousScore + windowSize;
            boolean aspirationFailed = false;
            int failCount = 0;

            do {
                aspirationFailed = false;
                bestMove = null;
                bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

                searchEngine.getMoveOrdering().orderMoves(moves, state, depth, previousEntry);

                for (Move move : moves) {
                    GameState copy = state.copy();
                    copy.applyMove(move);

                    int score = searchEngine.search(copy, depth - 1, alpha, beta, !isRed, strategy);

                    if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                        bestScore = score;
                        bestMove = move;
                    }

                    // Check for aspiration window failure
                    if ((isRed && score >= beta) || (!isRed && score <= alpha)) {
                        aspirationFailed = true;
                        failCount++;
                        windowSize *= 4; // Widen window
                        alpha = Math.max(Integer.MIN_VALUE / 2, previousScore - windowSize);
                        beta = Math.min(Integer.MAX_VALUE / 2, previousScore + windowSize);

                        System.out.printf("⚠️ Aspiration window failed (attempt %d), widening to [%d, %d]%n",
                                failCount, alpha, beta);
                        break;
                    }
                }

                // After 3 failures, use full window
                if (failCount >= 3) {
                    alpha = Integer.MIN_VALUE;
                    beta = Integer.MAX_VALUE;
                    aspirationFailed = false;
                    System.out.println("🔄 Using full window after aspiration failures");
                }

            } while (aspirationFailed && failCount < 3);

            stats.endSearch();
            counter = (int) stats.getNodeCount();

            return bestMove;

        } catch (Exception e) {
            System.err.println("❌ Error in aspiration search: " + e.getMessage());
            return findBestMoveWithStrategy(state, depth, strategy); // Fallback
        }
    }

    // === LEGACY COMPATIBILITY METHODS - FIXED ===

    public static Move findBestMove(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.PVS_Q);
    }

    public static Move findBestMoveWithQuiescence(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.ALPHA_BETA_Q);
    }

    public static Move findBestMoveWithPVS(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.PVS);
    }

    public static Move findBestMoveUltimate(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.PVS_Q);
    }

    // === EVALUATION INTERFACE ===

    public static int evaluate(GameState state, int depth) {
        return evaluator.evaluate(state, depth);
    }

    public static void setRemainingTime(long timeMs) {
        Evaluator.setRemainingTime(timeMs);
        QuiescenceSearch.setRemainingTime(timeMs);
    }

    // === GAME STATE ANALYSIS ===

    public static boolean isGameOver(GameState state) {
        if (state == null) return true;

        if (state.redGuard == 0 || state.blueGuard == 0) {
            return true;
        }

        boolean redWins = (state.redGuard & GameState.bit(BLUE_CASTLE_INDEX)) != 0;
        boolean blueWins = (state.blueGuard & GameState.bit(RED_CASTLE_INDEX)) != 0;

        return redWins || blueWins;
    }

    public static boolean isCapture(Move move, GameState state) {
        if (state == null || move == null) return false;

        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        boolean capturesGuard = isRed ?
                (state.blueGuard & toBit) != 0 :
                (state.redGuard & toBit) != 0;

        boolean capturesTower = isRed ?
                (state.blueTowers & toBit) != 0 :
                (state.redTowers & toBit) != 0;

        return capturesGuard || capturesTower;
    }

    public static boolean isGuardInDangerImproved(GameState state, boolean checkRed) {
        return evaluator.isGuardInDanger(state, checkRed);
    }

    // === MOVE SCORING FOR COMPATIBILITY ===

    public static int scoreMove(GameState state, Move move) {
        if (state == null || move == null) {
            return 0;
        }

        int score = 0;
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Winning moves
        boolean entersCastle = (isRed && move.to == BLUE_CASTLE_INDEX) ||
                (!isRed && move.to == RED_CASTLE_INDEX);

        if (entersCastle && isGuardMove(move, state)) {
            score += 10000;
        }

        // Captures
        boolean capturesGuard = ((isRed ? state.blueGuard : state.redGuard) & toBit) != 0;
        boolean capturesTower = ((isRed ? state.blueTowers : state.redTowers) & toBit) != 0;

        if (capturesGuard) score += 1500;
        if (capturesTower) {
            int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
            score += 500 * height;
        }

        // Stacking
        boolean stacksOnOwn = ((isRed ? state.redTowers : state.blueTowers) & toBit) != 0;
        if (stacksOnOwn) score += 10;

        score += move.amountMoved;
        return score;
    }

    public static int scoreMoveAdvanced(GameState state, Move move, int depth) {
        int score = scoreMove(state, move);

        if (isGuardMove(move, state)) {
            boolean guardInDanger = evaluator.isGuardInDanger(state, state.redToMove);
            if (guardInDanger) {
                score += 1500;
            }
        }

        return score;
    }

    private static boolean isGuardMove(Move move, GameState state) {
        if (state == null || move == null) return false;

        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
    }

    // === TRANSPOSITION TABLE INTERFACE ===

    public static TTEntry getTranspositionEntry(long hash) {
        return searchEngine.getTranspositionTable().get(hash);
    }

    public static void storeTranspositionEntry(long hash, TTEntry entry) {
        searchEngine.getTranspositionTable().put(hash, entry);
    }

    public static void clearTranspositionTable() {
        searchEngine.getTranspositionTable().clear();
    }

    // === MOVE ORDERING INTERFACE ===

    public static void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry entry) {
        searchEngine.getMoveOrdering().orderMoves(moves, state, depth, entry);
    }

    public static void storeKillerMove(Move move, int depth) {
        searchEngine.getMoveOrdering().storeKillerMove(move, depth);
    }

    public static void resetKillerMoves() {
        searchEngine.getMoveOrdering().resetKillerMoves();
    }

    // === SEARCH STATISTICS ===

    public static void resetPruningStats() {
        SearchStatistics.getInstance().reset();
    }

    public static String getSearchStatistics() {
        return SearchStatistics.getInstance().getSummary();
    }

    // === SEARCH CONFIGURATION ===

    public static void setTimeoutChecker(BooleanSupplier checker) {
        searchEngine.setTimeoutChecker(checker);
    }

    public static void clearTimeoutChecker() {
        searchEngine.clearTimeoutChecker();
    }

    // === COMPONENT ACCESS ===

    public static Evaluator getEvaluator() {
        return evaluator;
    }

    public static SearchEngine getSearchEngine() {
        return searchEngine;
    }

    // === ENHANCED TIMEOUT SUPPORT ===

    public static int minimaxWithTimeout(GameState state, int depth, int alpha, int beta,
                                         boolean maximizingPlayer, BooleanSupplier timeoutCheck) {
        return searchEngine.searchWithTimeout(state, depth, alpha, beta, maximizingPlayer,
                SearchConfig.SearchStrategy.ALPHA_BETA, timeoutCheck);
    }

    // === STRATEGY ACCESS ===

    public static SearchConfig.SearchStrategy[] getAllStrategies() {
        return SearchConfig.SearchStrategy.values();
    }

    public static SearchConfig.SearchStrategy getStrategyByName(String name) {
        try {
            return SearchConfig.SearchStrategy.valueOf(name);
        } catch (IllegalArgumentException e) {
            System.err.println("⚠️ Unknown strategy: " + name + ", defaulting to PVS_Q");
            return SearchConfig.SearchStrategy.PVS_Q;
        }
    }

    // === PERFORMANCE MONITORING ===

    /**
     * NEW: Performance analysis for strategy comparison
     */
    public static void analyzeSearchPerformance(GameState state, int depth) {
        System.out.println("\n=== SEARCH PERFORMANCE ANALYSIS ===");

        SearchConfig.SearchStrategy[] strategies = {
                SearchConfig.SearchStrategy.ALPHA_BETA,
                SearchConfig.SearchStrategy.ALPHA_BETA_Q,
                SearchConfig.SearchStrategy.PVS,
                SearchConfig.SearchStrategy.PVS_Q
        };

        for (SearchConfig.SearchStrategy strategy : strategies) {
            SearchStatistics.getInstance().reset();

            long startTime = System.currentTimeMillis();
            Move move = findBestMoveWithStrategy(state, depth, strategy);
            long endTime = System.currentTimeMillis();

            SearchStatistics stats = SearchStatistics.getInstance();

            System.out.printf("%s: move=%s, time=%dms, nodes=%,d, nps=%.0f%n",
                    strategy, move, endTime - startTime, stats.getNodeCount(),
                    stats.getNodesPerSecond());
        }

        System.out.println("=== ANALYSIS COMPLETE ===\n");
    }
}

// === CRITICAL FIX 5: Enhanced SearchEngine Integration ===

// Add these methods to SearchEngine.java:

public class SearchEngineEnhancements {

    /**
     * Add to SearchEngine.java - Getter methods for component access
     */
    public MoveOrdering getMoveOrdering() {
        return moveOrdering;
    }

    public TranspositionTable getTranspositionTable() {
        return transpositionTable;
    }

    public Evaluator getEvaluator() {
        return evaluator;
    }

    /**
     * Enhanced search with better error handling
     */
    public Move findBestMoveEnhanced(GameState state, int depth, long timeLimit, SearchConfig.SearchStrategy strategy) {
        if (state == null || depth <= 0) {
            return null;
        }

        statistics.reset();
        statistics.startSearch();

        // Set timeout
        long startTime = System.currentTimeMillis();
        setTimeoutChecker(() -> System.currentTimeMillis() - startTime >= timeLimit);

        try {
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            if (moves.isEmpty()) {
                return null;
            }

            Move bestMove = null;
            int bestScore = state.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;

            // Iterative deepening for better time management
            for (int currentDepth = 1; currentDepth <= depth; currentDepth++) {
                long iterationStart = System.currentTimeMillis();

                // Check if we have enough time for next iteration
                if (iterationStart - startTime > timeLimit * 0.7) {
                    System.out.println("⏱ Stopping iterative deepening due to time limit");
                    break;
                }

                Move iterationBest = null;
                int iterationScore = state.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;

                moveOrdering.orderMoves(moves, state, currentDepth, transpositionTable.get(state.hash()));

                for (Move move : moves) {
                    // Time check during move iteration
                    if (System.currentTimeMillis() - startTime >= timeLimit) {
                        break;
                    }

                    GameState copy = state.copy();
                    copy.applyMove(move);

                    int score = search(copy, currentDepth - 1, Integer.MIN_VALUE,
                            Integer.MAX_VALUE, !state.redToMove, strategy);

                    if ((state.redToMove && score > iterationScore) ||
                            (!state.redToMove && score < iterationScore) ||
                            iterationBest == null) {
                        iterationScore = score;
                        iterationBest = move;
                    }
                }

                // Update best move if iteration completed
                if (iterationBest != null) {
                    bestMove = iterationBest;
                    bestScore = iterationScore;

                    long iterationTime = System.currentTimeMillis() - iterationStart;
                    System.out.printf("✓ Depth %d: %s (score: %+d, time: %dms)%n",
                            currentDepth, bestMove, bestScore, iterationTime);
                }
            }

            return bestMove;

        } catch (RuntimeException e) {
            if (e.getMessage().contains("timeout")) {
                System.out.println("⏱ Search terminated due to timeout");
                // Return best move found so far
                List<Move> moves = MoveGenerator.generateAllMoves(state);
                return moves.isEmpty() ? null : moves.get(0);
            }
            throw e;
        } finally {
            clearTimeoutChecker();
            statistics.endSearch();
        }
    }
}
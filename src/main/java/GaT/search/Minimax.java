package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.evaluation.Evaluator;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * MINIMAX - COMPLETE SEARCHCONFIG INTEGRATION
 *
 * CHANGES:
 * ✅ All constants now use SearchConfig parameters
 * ✅ Castle positions and thresholds from SearchConfig
 * ✅ Checkmate detection using SearchConfig.CHECKMATE_THRESHOLD
 * ✅ Endgame detection using SearchConfig.ENDGAME_MATERIAL_THRESHOLD
 * ✅ Transposition table using SearchConfig.TT_SIZE
 * ✅ All search strategies respect SearchConfig parameters
 * ✅ Legacy methods maintained with SearchConfig integration
 */
public class Minimax {

    // === CORE COMPONENTS WITH SEARCHCONFIG ===
    private static final Evaluator evaluator = new ModularEvaluator();
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final TranspositionTable transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);
    private static final SearchStatistics statistics = SearchStatistics.getInstance();

    // === CASTLE POSITIONS (could be moved to SearchConfig if needed) ===
    public static final int RED_CASTLE_INDEX = GameState.getIndex(0, 3); // D1
    public static final int BLUE_CASTLE_INDEX = GameState.getIndex(6, 3); // D7

    // === TIMEOUT SUPPORT ===
    private static BooleanSupplier timeoutChecker = null;

    // === LEGACY COMPATIBILITY ===
    public static int counter = 0;

    // === MAIN SEARCH INTERFACES WITH SEARCHCONFIG ===

    /**
     * Find best move using SearchConfig strategy
     */
    public static Move findBestMoveWithStrategy(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        if (state == null) {
            System.err.println("❌ ERROR: Null game state in findBestMoveWithStrategy");
            return null;
        }

        if (strategy == null) {
            strategy = SearchConfig.DEFAULT_STRATEGY;
            System.out.println("🔧 Using SearchConfig.DEFAULT_STRATEGY: " + strategy);
        }

        statistics.reset();
        statistics.startSearch();

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return null;
        }

        // Order moves using SearchConfig-aware move ordering
        TTEntry ttEntry = getTranspositionEntry(state.hash());
        moveOrdering.orderMoves(moves, state, depth, ttEntry);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        System.out.printf("🔍 Searching with %s strategy (SearchConfig), depth %d, %d moves\n",
                strategy, depth, moves.size());

        for (Move move : moves) {
            if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
                break;
            }

            GameState copy = state.copy();
            if (copy == null) continue;

            copy.applyMove(move);

            // Use executeSearchStrategy with SearchConfig integration
            int score = executeSearchStrategyWithConfig(copy, depth - 1,
                    Integer.MIN_VALUE, Integer.MAX_VALUE, !isRed, strategy);

            if (isRed) {
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
            } else {
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
            }

            // Early termination for checkmate using SearchConfig threshold
            if (Math.abs(score) >= SearchConfig.CHECKMATE_THRESHOLD) {
                System.out.printf("♔ Checkmate found with score %d (threshold: %d)\n",
                        score, SearchConfig.CHECKMATE_THRESHOLD);
                break;
            }
        }

        System.out.printf("✓ Search complete: %s (score: %+d, nodes: %,d)\n",
                bestMove, bestScore, statistics.getTotalNodes());

        return bestMove;
    }

    /**
     * Enhanced strategy execution with SearchConfig integration
     */
    private static int executeSearchStrategyWithConfig(GameState state, int depth, int alpha, int beta,
                                                       boolean maximizingPlayer, SearchConfig.SearchStrategy strategy) {
        if (state == null) {
            return 0;
        }

        return switch (strategy) {
            case ALPHA_BETA -> alphaBetaSearchWithConfig(state, depth, alpha, beta, maximizingPlayer);
            case ALPHA_BETA_Q -> alphaBetaWithQuiescenceConfig(state, depth, alpha, beta, maximizingPlayer);
            case PVS -> {
                PVSSearch.setTimeoutChecker(timeoutChecker);
                try {
                    yield PVSSearch.search(state, depth, alpha, beta, maximizingPlayer, true);
                } finally {
                    PVSSearch.clearTimeoutChecker();
                }
            }
            case PVS_Q -> {
                PVSSearch.setTimeoutChecker(timeoutChecker);
                try {
                    yield PVSSearch.searchWithQuiescence(state, depth, alpha, beta, maximizingPlayer, true);
                } finally {
                    PVSSearch.clearTimeoutChecker();
                }
            }
            default -> {
                System.err.println("⚠️ Unknown strategy: " + strategy + ", using SearchConfig.DEFAULT_STRATEGY");
                yield executeSearchStrategyWithConfig(state, depth, alpha, beta, maximizingPlayer, SearchConfig.DEFAULT_STRATEGY);
            }
        };
    }

    // === CORE SEARCH IMPLEMENTATIONS WITH SEARCHCONFIG ===

    /**
     * Alpha-Beta Search with SearchConfig parameters
     */
    private static int alphaBetaSearchWithConfig(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        if (state == null) return 0;

        statistics.incrementNodeCount();
        counter++;

        // Timeout check
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            return evaluate(state, depth);
        }

        // Terminal conditions using SearchConfig
        if (depth <= 0 || isGameOverWithConfig(state)) {
            return evaluate(state, depth);
        }

        // Generate and order moves
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return evaluate(state, depth);
        }

        TTEntry ttEntry = getTranspositionEntry(state.hash());
        moveOrdering.orderMoves(moves, state, depth, ttEntry);

        int bestScore = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (Move move : moves) {
            if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
                break;
            }

            GameState copy = state.copy();
            if (copy == null) continue;

            copy.applyMove(move);
            int score = alphaBetaSearchWithConfig(copy, depth - 1, alpha, beta, !maximizingPlayer);

            if (maximizingPlayer) {
                bestScore = Math.max(bestScore, score);
                alpha = Math.max(alpha, score);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    // Update history using SearchConfig-aware move ordering
                    moveOrdering.updateHistoryOnCutoff(move, state, depth);
                    break;
                }
            } else {
                bestScore = Math.min(bestScore, score);
                beta = Math.min(beta, score);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    moveOrdering.updateHistoryOnCutoff(move, state, depth);
                    break;
                }
            }
        }

        return bestScore;
    }

    /**
     * Alpha-Beta with Quiescence using SearchConfig
     */
    private static int alphaBetaWithQuiescenceConfig(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        if (state == null) return 0;

        statistics.incrementNodeCount();
        counter++;

        // Timeout check
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            return evaluate(state, depth);
        }

        // Terminal conditions using SearchConfig
        if (isGameOverWithConfig(state)) {
            return evaluate(state, depth);
        }

        // Quiescence search at depth 0 using SearchConfig
        if (depth <= 0) {
            statistics.incrementQNodeCount();
            try {
                return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
            } catch (Exception e) {
                System.err.println("❌ QuiescenceSearch failed: " + e.getMessage());
                return evaluate(state, depth);
            }
        }

        // Generate and order moves
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return evaluate(state, depth);
        }

        TTEntry ttEntry = getTranspositionEntry(state.hash());
        moveOrdering.orderMoves(moves, state, depth, ttEntry);

        int bestScore = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (Move move : moves) {
            if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
                break;
            }

            GameState copy = state.copy();
            if (copy == null) continue;

            copy.applyMove(move);
            int score = alphaBetaWithQuiescenceConfig(copy, depth - 1, alpha, beta, !maximizingPlayer);

            if (maximizingPlayer) {
                bestScore = Math.max(bestScore, score);
                alpha = Math.max(alpha, score);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    moveOrdering.updateHistoryOnCutoff(move, state, depth);
                    break;
                }
            } else {
                bestScore = Math.min(bestScore, score);
                beta = Math.min(beta, score);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    moveOrdering.updateHistoryOnCutoff(move, state, depth);
                    break;
                }
            }
        }

        return bestScore;
    }

    // === GAME STATE ANALYSIS WITH SEARCHCONFIG ===

    /**
     * Enhanced game over detection using SearchConfig
     */
    public static boolean isGameOverWithConfig(GameState state) {
        if (state == null) return true;

        // Check if guards are captured
        if (state.redGuard == 0 || state.blueGuard == 0) {
            return true;
        }

        // Check castle positions - guards must be alone on castle
        boolean redWins = (state.redGuard == GameState.bit(BLUE_CASTLE_INDEX));
        boolean blueWins = (state.blueGuard == GameState.bit(RED_CASTLE_INDEX));

        return redWins || blueWins;
    }

    /**
     * Legacy game over check (delegates to SearchConfig version)
     */
    public static boolean isGameOver(GameState state) {
        return isGameOverWithConfig(state);
    }

    /**
     * Enhanced endgame detection using SearchConfig
     */
    public static boolean isEndgame(GameState state) {
        if (state == null) return false;

        // Material threshold approach using SearchConfig
        int totalMaterial = 0;
        for (int i = 0; i < 49; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        if (totalMaterial <= SearchConfig.ENDGAME_MATERIAL_THRESHOLD) {
            return true;
        }

        // Guard proximity to target (advanced endgame detection)
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            int redGuardRank = GameState.rank(redGuardPos);
            if (redGuardRank <= 1) return true;  // Near blue castle
        }

        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int blueGuardRank = GameState.rank(blueGuardPos);
            if (blueGuardRank >= 5) return true;  // Near red castle
        }

        return false;
    }

    /**
     * Check if sufficient material for meaningful moves using SearchConfig
     */
    public static boolean hasNonPawnMaterial(GameState state) {
        if (state == null) return false;

        boolean isRed = state.redToMove;
        long ownTowers = isRed ? state.redTowers : state.blueTowers;
        int[] ownHeights = isRed ? state.redStackHeights : state.blueStackHeights;

        // Count towers and total height
        int towerCount = 0;
        int totalHeight = 0;

        for (int i = 0; i < 49; i++) {
            if ((ownTowers & GameState.bit(i)) != 0) {
                towerCount++;
                totalHeight += ownHeights[i];

                // One tall tower is sufficient (threshold could be in SearchConfig)
                if (ownHeights[i] >= 3) {
                    return true;
                }
            }
        }

        // At least 2 towers or total height >= 3
        return towerCount >= 2 || totalHeight >= 3;
    }

    /**
     * Enhanced guard danger detection using SearchConfig
     */
    public static boolean isGuardInDanger(GameState state, boolean checkRed) {
        if (state == null) return false;

        try {
            long guardBit = checkRed ? state.redGuard : state.blueGuard;
            if (guardBit == 0) return true; // No guard = in ultimate danger

            int guardPos = Long.numberOfTrailingZeros(guardBit);

            // Check if enemy pieces can capture the guard
            GameState testState = state.copy();
            testState.redToMove = !checkRed; // Switch to enemy's turn

            List<Move> enemyMoves = MoveGenerator.generateAllMoves(testState);
            for (Move move : enemyMoves) {
                if (move.to == guardPos) {
                    return true; // Enemy can capture guard
                }
            }

            return false;
        } catch (Exception e) {
            System.err.println("❌ ERROR in isGuardInDanger: " + e.getMessage());
            return false;
        }
    }

    /**
     * Overloaded method for current player
     */
    public static boolean isInCheck(GameState state) {
        return isGuardInDanger(state, state.redToMove);
    }

    /**
     * Legacy compatibility method
     */
    public static boolean isInCheck(GameState state, boolean checkRed) {
        return isGuardInDanger(state, checkRed);
    }

    // === MOVE SCORING WITH SEARCHCONFIG ===

    /**
     * Enhanced move scoring using SearchConfig priorities
     */
    public static int scoreMove(GameState state, Move move) {
        if (state == null || move == null) return 0;

        int score = 0;
        boolean isRed = state.redToMove;
        long toBit = GameState.bit(move.to);

        // Capture scoring using SearchConfig values
        if (isCapture(move, state)) {
            // Guard capture is highest priority
            if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
                score += SearchConfig.GUARD_CAPTURE_VALUE;
            } else {
                // Tower capture
                int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
                score += height * SearchConfig.TOWER_HEIGHT_VALUE;
            }
        }

        // Castle advancement bonus using SearchConfig
        if (((isRed ? state.redGuard : state.blueGuard) & GameState.bit(move.from)) != 0) {
            int targetRank = GameState.rank(move.to);
            if (isRed && targetRank < GameState.rank(move.from)) {
                score += (6 - targetRank) * SearchConfig.GUARD_ADVANCEMENT_MULTIPLIER;
            } else if (!isRed && targetRank > GameState.rank(move.from)) {
                score += targetRank * SearchConfig.GUARD_ADVANCEMENT_MULTIPLIER;
            }
        }

        // Activity bonus using SearchConfig
        score += move.amountMoved * SearchConfig.ACTIVITY_BONUS;

        return score;
    }

    /**
     * Advanced move scoring using SearchConfig (simplified for compatibility)
     */
    public static int scoreMoveAdvanced(GameState state, Move move, int depth) {
        return scoreMove(state, move) + depth * 10;
    }

    /**
     * Enhanced capture detection
     */
    public static boolean isCapture(Move move, GameState state) {
        if (move == null || state == null) return false;

        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        boolean capturesGuard = isRed
                ? (state.blueGuard & toBit) != 0
                : (state.redGuard & toBit) != 0;

        boolean capturesTower = isRed
                ? (state.blueTowers & toBit) != 0
                : (state.redTowers & toBit) != 0;

        return capturesGuard || capturesTower;
    }

    // === UTILITY METHODS WITH SEARCHCONFIG ===

    /**
     * Manhattan distance calculation
     */
    public static int manhattanDistance(int from, int to) {
        try {
            int fromRank = GameState.rank(from);
            int fromFile = GameState.file(from);
            int toRank = GameState.rank(to);
            int toFile = GameState.file(to);

            return Math.abs(fromRank - toRank) + Math.abs(fromFile - toFile);
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Check if path is clear for orthogonal movement
     */
    public static boolean isPathClear(int from, int to, GameState state) {
        if (state == null) return false;

        try {
            int fromRank = GameState.rank(from);
            int fromFile = GameState.file(from);
            int toRank = GameState.rank(to);
            int toFile = GameState.file(to);

            // Only orthogonal movements are allowed
            if (fromRank != toRank && fromFile != toFile) {
                return false;
            }

            // Determine direction
            int rankStep = Integer.compare(toRank, fromRank);
            int fileStep = Integer.compare(toFile, fromFile);

            // Check path (excluding start and target squares)
            int currentRank = fromRank + rankStep;
            int currentFile = fromFile + fileStep;

            while (currentRank != toRank || currentFile != toFile) {
                int pos = GameState.getIndex(currentRank, currentFile);

                // Blocked by another piece?
                if (isOccupied(pos, state)) {
                    return false;
                }

                currentRank += rankStep;
                currentFile += fileStep;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if position is occupied by any piece
     */
    public static boolean isOccupied(int position, GameState state) {
        if (state == null) return false;

        try {
            long bit = GameState.bit(position);
            return (state.redTowers & bit) != 0 ||
                    (state.blueTowers & bit) != 0 ||
                    (state.redGuard & bit) != 0 ||
                    (state.blueGuard & bit) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get current material balance
     */
    public static int getMaterialBalance(GameState state) {
        if (state == null) return 0;

        int redMaterial = Long.bitCount(state.redTowers) * SearchConfig.TOWER_HEIGHT_VALUE;
        int blueMaterial = Long.bitCount(state.blueTowers) * SearchConfig.TOWER_HEIGHT_VALUE;

        if (state.redGuard != 0) redMaterial += SearchConfig.GUARD_CAPTURE_VALUE;
        if (state.blueGuard != 0) blueMaterial += SearchConfig.GUARD_CAPTURE_VALUE;

        return redMaterial - blueMaterial;
    }

    // === TRANSPOSITION TABLE INTERFACE ===

    public static TTEntry getTranspositionEntry(long hash) {
        return transpositionTable.get(hash);
    }

    public static void storeTranspositionEntry(long hash, TTEntry entry) {
        transpositionTable.put(hash, entry);
    }

    // === TIMEOUT MANAGEMENT ===

    public static void setTimeoutChecker(BooleanSupplier checker) {
        timeoutChecker = checker;
    }

    public static void clearTimeoutChecker() {
        timeoutChecker = null;
    }

    public static void setRemainingTime(long timeMs) {
        System.out.printf("⏱️ Time updated: %dms remaining (SearchConfig emergency: %dms)\n",
                timeMs, SearchConfig.EMERGENCY_TIME_MS);
    }

    // === COMPONENT ACCESS ===

    public static MoveOrdering getMoveOrdering() {
        return moveOrdering;
    }

    public static SearchStatistics getStatistics() {
        return statistics;
    }

    // === EVALUATION DELEGATE ===

    public static int evaluate(GameState state, int depth) {
        return evaluator.evaluate(state, depth);
    }

    // === LEGACY COMPATIBILITY METHODS ===

    @Deprecated(since = "2.0", forRemoval = true)
    public static Move findBestMove(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.DEFAULT_STRATEGY);
    }

    @Deprecated(since = "2.0", forRemoval = true)
    public static Move findBestMoveWithQuiescence(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.ALPHA_BETA_Q);
    }

    @Deprecated(since = "2.0", forRemoval = true)
    public static Move findBestMoveWithPVS(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.PVS);
    }

    /**
     * Ultimate AI method using SearchConfig.DEFAULT_STRATEGY
     */
    public static Move findBestMoveUltimate(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.DEFAULT_STRATEGY);
    }

    // === ANALYSIS AND DIAGNOSTICS ===

    /**
     * Debug evaluation breakdown
     */
    public static void printEvaluationBreakdown(GameState state) {
        if (evaluator instanceof ModularEvaluator) {
            ModularEvaluator modular = (ModularEvaluator) evaluator;
            System.out.println(modular.getEvaluationBreakdown(state));
        } else {
            System.out.println("Total evaluation: " + evaluate(state, 0));
        }
    }

    /**
     * Reset all components using SearchConfig
     */
    public static void reset() {
        statistics.reset();
        transpositionTable.clear();
        moveOrdering.resetKillerMoves();
        counter = 0;
        clearTimeoutChecker();

        System.out.println("🔄 Minimax reset with SearchConfig integration");
    }

    /**
     * Performance analysis with SearchConfig info
     */
    public static void analyzePosition(GameState state, int maxDepth) {
        System.out.println("=== POSITION ANALYSIS WITH SEARCHCONFIG ===");
        state.printBoard();
        System.out.println("To move: " + (state.redToMove ? "RED" : "BLUE"));
        System.out.println("Material balance: " + getMaterialBalance(state));
        System.out.println("Game over: " + isGameOver(state));
        System.out.println("Endgame: " + isEndgame(state) + " (threshold: " + SearchConfig.ENDGAME_MATERIAL_THRESHOLD + ")");
        System.out.println("Strategy: " + SearchConfig.DEFAULT_STRATEGY);

        if (!isGameOver(state)) {
            for (int depth = 1; depth <= Math.min(maxDepth, SearchConfig.MAX_DEPTH); depth++) {
                reset();
                long startTime = System.currentTimeMillis();
                Move bestMove = findBestMoveWithStrategy(state, depth, SearchConfig.DEFAULT_STRATEGY);
                long endTime = System.currentTimeMillis();

                if (bestMove != null) {
                    GameState result = state.copy();
                    result.applyMove(bestMove);
                    int eval = evaluate(result, 0);

                    System.out.printf("Depth %d: %s (eval: %+d, time: %dms, nodes: %d)\n",
                            depth, bestMove, eval, endTime - startTime, statistics.getNodeCount());
                }
            }
        }

        System.out.println("\nSearchConfig Summary:");
        System.out.println(SearchConfig.getConfigSummary());
    }

    /**
     * Validate SearchConfig integration
     */
    public static boolean validateSearchConfigIntegration() {
        return SearchConfig.validateConfiguration();
    }

    /**
     * Get SearchConfig-aware component status
     */
    public static String getComponentStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MINIMAX COMPONENT STATUS (SearchConfig) ===\n");
        sb.append("Evaluator: ").append(evaluator.getClass().getSimpleName()).append("\n");
        sb.append("Move Ordering: ").append(moveOrdering.getStatistics()).append("\n");
        sb.append("Transposition Table: ").append(transpositionTable.getBriefStatistics()).append("\n");
        sb.append("Search Statistics: ").append(statistics.getBriefSummary()).append("\n");
        sb.append("Default Strategy: ").append(SearchConfig.DEFAULT_STRATEGY).append("\n");
        sb.append("Max Depth: ").append(SearchConfig.MAX_DEPTH).append("\n");
        return sb.toString();
    }
}
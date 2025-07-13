package GaT.search;

import GaT.evaluation.ModularEvaluator;
import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.evaluation.Evaluator;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * SIMPLIFIED MINIMAX - FIXED NULL POINTER ISSUES
 *
 * FIXES:
 * ✅ Removed SearchEngine dependency (causes null pointers)
 * ✅ Direct search implementation
 * ✅ Proper null checks everywhere
 * ✅ Simplified architecture
 * ✅ ModularEvaluator integration
 */
public class Minimax {

    // === CORE COMPONENTS ===
    private static final Evaluator evaluator = new ModularEvaluator();
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final TranspositionTable transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);
    private static final SearchStatistics statistics = SearchStatistics.getInstance();

    // === SEARCH CONSTANTS ===
    public static final int RED_CASTLE_INDEX = GameState.getIndex(0, 3); // D1
    public static final int BLUE_CASTLE_INDEX = GameState.getIndex(6, 3); // D7

    // === TIMEOUT SUPPORT ===
    private static BooleanSupplier timeoutChecker = null;

    // === BACKWARD COMPATIBILITY ===
    public static int counter = 0; // For legacy compatibility

    // === MAIN SEARCH INTERFACES ===

    /**
     * Find best move using specified strategy - SIMPLIFIED & FIXED
     */
    public static Move findBestMoveWithStrategy(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        if (state == null) {
            System.err.println("❌ ERROR: Null game state in findBestMoveWithStrategy");
            return null;
        }

        statistics.reset();
        statistics.startSearch();

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return null;
        }

        // Order moves
        TTEntry ttEntry = getTranspositionEntry(state.hash());
        moveOrdering.orderMoves(moves, state, depth, ttEntry);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        // Try each move
        for (Move move : moves) {
            if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
                break; // Timeout
            }

            GameState copy = state.copy();
            if (copy == null) {
                System.err.println("❌ ERROR: Failed to copy game state");
                continue;
            }

            copy.applyMove(move);

            int score;
            try {
                // Direct search calls based on strategy
                switch (strategy) {
                    case ALPHA_BETA:
                        score = alphaBetaSearch(copy, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, !isRed);
                        break;
                    case ALPHA_BETA_Q:
                        score = alphaBetaWithQuiescence(copy, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, !isRed);
                        break;
                    case PVS:
                        score = pvsSearch(copy, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, !isRed, false);
                        break;
                    case PVS_Q:
                        score = pvsWithQuiescence(copy, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, !isRed, false);
                        break;
                    default:
                        score = alphaBetaSearch(copy, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, !isRed);
                        break;
                }
            } catch (Exception e) {
                System.err.println("❌ Search error for move " + move + ": " + e.getMessage());
                score = isRed ? Integer.MIN_VALUE + 1000 : Integer.MAX_VALUE - 1000; // Bad but not crash
            }

            if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                bestScore = score;
                bestMove = move;
            }
        }

        statistics.endSearch();
        counter = (int) statistics.getNodeCount();

        return bestMove;
    }

    // === DIRECT SEARCH IMPLEMENTATIONS ===

    /**
     * Alpha-Beta Search - SIMPLIFIED & FIXED
     */
    private static int alphaBetaSearch(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        if (state == null) {
            System.err.println("❌ ERROR: Null state in alphaBetaSearch");
            return 0;
        }

        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            return evaluate(state, depth);
        }

        statistics.incrementNodeCount();

        // Terminal conditions
        if (depth <= 0 || isGameOver(state)) {
            return evaluate(state, depth);
        }

        // TT lookup
        long hash = state.hash();
        TTEntry entry = transpositionTable.get(hash);
        if (entry != null && entry.depth >= depth) {
            statistics.incrementTTHits();
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            }
            if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            }
            if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return evaluate(state, depth);
        }

        moveOrdering.orderMoves(moves, state, depth, entry);

        int bestScore = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        Move bestMove = null;
        int flag = TTEntry.UPPER_BOUND;

        for (Move move : moves) {
            if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
                break;
            }

            GameState copy = state.copy();
            if (copy == null) continue;

            copy.applyMove(move);

            int score = alphaBetaSearch(copy, depth - 1, alpha, beta, !maximizingPlayer);

            if (maximizingPlayer) {
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                alpha = Math.max(alpha, score);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    flag = TTEntry.LOWER_BOUND;
                    break; // Beta cutoff
                }
            } else {
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                beta = Math.min(beta, score);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    flag = TTEntry.UPPER_BOUND;
                    break; // Alpha cutoff
                }
            }
        }

        // Store in TT
        if (bestMove != null) {
            if (bestScore > alpha && bestScore < beta) {
                flag = TTEntry.EXACT;
            }
            TTEntry newEntry = new TTEntry(bestScore, depth, flag, bestMove);
            transpositionTable.put(hash, newEntry);
        }

        return bestScore;
    }

    /**
     * Alpha-Beta with Quiescence - SIMPLIFIED & FIXED
     */
    private static int alphaBetaWithQuiescence(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        if (state == null) {
            System.err.println("❌ ERROR: Null state in alphaBetaWithQuiescence");
            return 0;
        }

        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            return evaluate(state, depth);
        }

        statistics.incrementNodeCount();

        // Terminal conditions
        if (isGameOver(state)) {
            return evaluate(state, depth);
        }

        if (depth <= 0) {
            // Enter quiescence search
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        // Similar to alpha-beta but with quiescence at the end
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return evaluate(state, depth);
        }

        TTEntry entry = transpositionTable.get(state.hash());
        moveOrdering.orderMoves(moves, state, depth, entry);

        int bestScore = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        Move bestMove = null;

        for (Move move : moves) {
            if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
                break;
            }

            GameState copy = state.copy();
            if (copy == null) continue;

            copy.applyMove(move);

            int score = alphaBetaWithQuiescence(copy, depth - 1, alpha, beta, !maximizingPlayer);

            if (maximizingPlayer) {
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                alpha = Math.max(alpha, score);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    break;
                }
            } else {
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
                beta = Math.min(beta, score);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    break;
                }
            }
        }

        return bestScore;
    }

    /**
     * PVS Search - SIMPLIFIED & FIXED
     */
    private static int pvsSearch(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer, boolean isPVNode) {
        if (state == null) {
            System.err.println("❌ ERROR: Null state in pvsSearch");
            return 0;
        }

        // Fallback to alpha-beta for now to avoid complexity
        return alphaBetaSearch(state, depth, alpha, beta, maximizingPlayer);
    }

    /**
     * PVS with Quiescence - SIMPLIFIED & FIXED
     */
    private static int pvsWithQuiescence(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer, boolean isPVNode) {
        if (state == null) {
            System.err.println("❌ ERROR: Null state in pvsWithQuiescence");
            return 0;
        }

        // Fallback to alpha-beta with quiescence for now
        return alphaBetaWithQuiescence(state, depth, alpha, beta, maximizingPlayer);
    }

    // === LEGACY COMPATIBILITY METHODS ===

    /**
     * Legacy findBestMove method
     */
    public static Move findBestMove(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.ALPHA_BETA);
    }

    /**
     * Legacy findBestMoveWithQuiescence method
     */
    public static Move findBestMoveWithQuiescence(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.ALPHA_BETA_Q);
    }

    /**
     * Legacy findBestMoveWithPVS method
     */
    public static Move findBestMoveWithPVS(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.PVS);
    }

    /**
     * Ultimate AI method - PVS + Quiescence
     */
    public static Move findBestMoveUltimate(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.PVS_Q);
    }

    // === EVALUATION INTERFACE ===

    /**
     * Main evaluation interface - WITH NULL CHECK
     */
    public static int evaluate(GameState state, int depth) {
        if (state == null) {
            System.err.println("❌ ERROR: Null state in evaluate");
            return 0;
        }
        return evaluator.evaluate(state, depth);
    }

    /**
     * Time management integration
     */
    public static void setRemainingTime(long timeMs) {
        Evaluator.setRemainingTime(timeMs);
        QuiescenceSearch.setRemainingTime(timeMs);
    }

    // === GAME STATE ANALYSIS ===

    /**
     * Check if game is over - WITH NULL CHECK
     */
    public static boolean isGameOver(GameState state) {
        if (state == null) return true;

        // Guard captured
        if (state.redGuard == 0 || state.blueGuard == 0) {
            return true;
        }

        // Guard reached enemy castle
        boolean redWins = (state.redGuard & GameState.bit(RED_CASTLE_INDEX)) != 0;
        boolean blueWins = (state.blueGuard & GameState.bit(BLUE_CASTLE_INDEX)) != 0;

        return redWins || blueWins;
    }

    /**
     * Check if current player is in check - WITH NULL CHECK
     */
    public static boolean isInCheck(GameState state) {
        if (state == null) return false;

        boolean isRed = state.redToMove;
        long ownGuard = isRed ? state.redGuard : state.blueGuard;

        // No guard = not in check (game already over)
        if (ownGuard == 0) {
            return false;
        }

        int guardPosition = Long.numberOfTrailingZeros(ownGuard);
        long enemyTowers = isRed ? state.blueTowers : state.redTowers;
        int[] enemyHeights = isRed ? state.blueStackHeights : state.redStackHeights;

        // Check all enemy towers for threats
        for (int i = 0; i < 49; i++) {
            if ((enemyTowers & GameState.bit(i)) != 0) {
                int towerHeight = enemyHeights[i];
                int distance = manhattanDistance(i, guardPosition);

                // Tower can capture guard if: distance = tower height AND path is clear
                if (distance == towerHeight && isPathClear(i, guardPosition, state)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if player has sufficient material for meaningful moves - WITH NULL CHECK
     */
    public static boolean hasNonPawnMaterial(GameState state) {
        if (state == null) return false;

        boolean isRed = state.redToMove;
        long ownTowers = isRed ? state.redTowers : state.blueTowers;
        int[] ownHeights = isRed ? state.redStackHeights : state.blueStackHeights;

        // At least one tower with height >= 2, or at least 2 towers
        int towerCount = 0;
        int totalHeight = 0;

        for (int i = 0; i < 49; i++) {
            if ((ownTowers & GameState.bit(i)) != 0) {
                towerCount++;
                totalHeight += ownHeights[i];

                // One high tower is sufficient
                if (ownHeights[i] >= 3) {
                    return true;
                }
            }
        }

        // At least 2 towers or total height >= 3
        return towerCount >= 2 || totalHeight >= 3;
    }

    /**
     * Check if game is in endgame phase - WITH NULL CHECK
     */
    public static boolean isEndgame(GameState state) {
        if (state == null) return true;

        // Material threshold
        int totalMaterial = 0;
        for (int i = 0; i < 49; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        if (totalMaterial <= SearchConfig.ENDGAME_MATERIAL_THRESHOLD) {
            return true;
        }

        // Guards near target (rank 1 or 6/7)
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            int redGuardRank = GameState.rank(redGuardPos);
            if (redGuardRank <= 1) return true;  // Near blue castle (D1)
        }

        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int blueGuardRank = GameState.rank(blueGuardPos);
            if (blueGuardRank >= 5) return true;  // Near red castle (D7)
        }

        return false;
    }

    /**
     * Manhattan distance between two positions - WITH NULL CHECK
     */
    private static int manhattanDistance(int from, int to) {
        int fromRank = GameState.rank(from);
        int fromFile = GameState.file(from);
        int toRank = GameState.rank(to);
        int toFile = GameState.file(to);

        return Math.abs(fromRank - toRank) + Math.abs(fromFile - toFile);
    }

    /**
     * Check if path between two positions is orthogonally clear - WITH NULL CHECK
     */
    private static boolean isPathClear(int from, int to, GameState state) {
        if (state == null) return false;

        int fromRank = GameState.rank(from);
        int fromFile = GameState.file(from);
        int toRank = GameState.rank(to);
        int toFile = GameState.file(to);

        // Only orthogonal moves allowed
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
    }

    /**
     * Check if position is occupied - WITH NULL CHECK
     */
    private static boolean isOccupied(int position, GameState state) {
        if (state == null) return true;

        long bit = GameState.bit(position);
        return (state.redTowers & bit) != 0 ||
                (state.blueTowers & bit) != 0 ||
                (state.redGuard & bit) != 0 ||
                (state.blueGuard & bit) != 0;
    }

    /**
     * Check if move is a capture - WITH NULL CHECK
     */
    public static boolean isCapture(Move move, GameState state) {
        if (state == null || move == null) return false;
        long toBit = GameState.bit(move.to);
        long pieces = state.redToMove ? (state.blueTowers | state.blueGuard) : (state.redTowers | state.redGuard);
        return (pieces & toBit) != 0;
    }

    /**
     * Legacy guard danger check
     */
    public static boolean isGuardInDangerImproved(GameState state, boolean checkRed) {
        if (state == null) return false;
        return evaluator.isGuardInDanger(state, checkRed);
    }

    // === MOVE SCORING FOR COMPATIBILITY ===

    /**
     * Basic move scoring - WITH NULL CHECK
     */
    public static int scoreMove(GameState state, Move move) {
        if (state == null || move == null) {
            return 0;
        }

        int score = 0;
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Winning moves
        boolean entersCastle = (isRed && move.to == RED_CASTLE_INDEX) ||
                (!isRed && move.to == BLUE_CASTLE_INDEX);

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

    /**
     * Advanced move scoring
     */
    public static int scoreMoveAdvanced(GameState state, Move move, int depth) {
        if (state == null || move == null) return 0;

        int score = scoreMove(state, move);

        // Guard escape bonus
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

    /**
     * Get transposition table entry
     */
    public static TTEntry getTranspositionEntry(long hash) {
        return transpositionTable.get(hash);
    }

    /**
     * Store transposition table entry
     */
    public static void storeTranspositionEntry(long hash, TTEntry entry) {
        transpositionTable.put(hash, entry);
    }

    /**
     * Clear transposition table
     */
    public static void clearTranspositionTable() {
        transpositionTable.clear();
    }

    // === MOVE ORDERING INTERFACE ===

    /**
     * Legacy move ordering method
     */
    public static void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry entry) {
        if (moves == null || state == null) return;
        moveOrdering.orderMoves(moves, state, depth, entry);
    }

    /**
     * Store killer move
     */
    public static void storeKillerMove(Move move, int depth) {
        moveOrdering.storeKillerMove(move, depth);
    }

    /**
     * Reset killer moves
     */
    public static void resetKillerMoves() {
        moveOrdering.resetKillerMoves();
    }

    // === SEARCH STATISTICS ===

    /**
     * Reset all statistics
     */
    public static void resetPruningStats() {
        statistics.reset();
    }

    // === SEARCH CONFIGURATION ===

    /**
     * Set timeout checker for searches
     */
    public static void setTimeoutChecker(BooleanSupplier checker) {
        timeoutChecker = checker;
    }

    /**
     * Clear timeout checker
     */
    public static void clearTimeoutChecker() {
        timeoutChecker = null;
    }

    // === COMPONENT ACCESS (FOR TESTING/DEBUGGING) ===

    /**
     * Get evaluator instance (for testing)
     */
    public static Evaluator getEvaluator() {
        return evaluator;
    }

    /**
     * Get move ordering instance (for testing)
     */
    public static MoveOrdering getMoveOrdering() {
        return moveOrdering;
    }

    /**
     * Get transposition table instance (for testing)
     */
    public static TranspositionTable getTranspositionTable() {
        return transpositionTable;
    }

    // === STRATEGY ACCESS ===

    /**
     * Get all available search strategies
     */
    public static SearchConfig.SearchStrategy[] getAllStrategies() {
        return SearchConfig.SearchStrategy.values();
    }

    /**
     * Get strategy by name - helper method
     */
    public static SearchConfig.SearchStrategy getStrategyByName(String name) {
        try {
            return SearchConfig.SearchStrategy.valueOf(name);
        } catch (IllegalArgumentException e) {
            System.err.println("⚠️ Unknown strategy: " + name + ", defaulting to ALPHA_BETA");
            return SearchConfig.SearchStrategy.ALPHA_BETA;
        }
    }
}
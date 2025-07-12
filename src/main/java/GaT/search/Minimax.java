package GaT.search;

import GaT.evaluation.ModularEvaluator;
import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.evaluation.Evaluator;

import java.util.List;
import java.util.function.BooleanSupplier;
import GaT.search.MoveGenerator;
import GaT.search.QuiescenceSearch;

/**
 * MINIMAX SEARCH COORDINATOR - FIXED: Unified SearchStrategy enum
 * Now acts as a facade/coordinator for all search operations
 *
 * FIXES:
 * ✅ Removed duplicate SearchStrategy enum
 * ✅ Uses only SearchConfig.SearchStrategy
 * ✅ All method signatures updated
 * ✅ Legacy compatibility maintained
 */
public class Minimax {

    // === CORE COMPONENTS ===
    private static final Evaluator evaluator = new ModularEvaluator();;
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final TranspositionTable transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);
    private static final SearchStatistics statistics = SearchStatistics.getInstance();
    private static final SearchEngine searchEngine = new SearchEngine(evaluator, moveOrdering, transpositionTable, statistics);

    // === SEARCH CONSTANTS ===
    public static final int RED_CASTLE_INDEX = GameState.getIndex(6, 3); // D7
    public static final int BLUE_CASTLE_INDEX = GameState.getIndex(0, 3); // D1

    // === BACKWARD COMPATIBILITY ===
    public static int counter = 0; // For legacy compatibility

    // === MAIN SEARCH INTERFACES - FIXED ===

    /**
     * Find best move using specified strategy - FIXED enum usage
     */
    public static Move findBestMoveWithStrategy(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        statistics.reset();
        statistics.startSearch();

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return null;
        }

        moveOrdering.orderMoves(moves, state, depth, getTranspositionEntry(state.hash()));

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (Move move : moves) {
            GameState copy = state.copy();
            copy.applyMove(move);

            int score = searchEngine.search(copy, depth - 1, Integer.MIN_VALUE,
                    Integer.MAX_VALUE, !isRed, strategy);

            if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                bestScore = score;
                bestMove = move;
            }
        }

        statistics.endSearch();
        counter = (int) statistics.getNodeCount(); // Update legacy counter

        return bestMove;
    }

    /**
     * Enhanced search with aspiration windows - FIXED enum usage
     */
    public static Move findBestMoveWithAspiration(GameState state, int depth, SearchConfig.SearchStrategy strategy) {
        statistics.reset();
        statistics.startSearch();

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return null;
        }

        TTEntry previousEntry = getTranspositionEntry(state.hash());
        moveOrdering.orderMoves(moves, state, depth, previousEntry);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        // Aspiration window setup
        int previousScore = 0;
        if (previousEntry != null && previousEntry.depth >= depth - 2) {
            previousScore = previousEntry.score;
            // Move TT move to front
            if (previousEntry.bestMove != null && moves.contains(previousEntry.bestMove)) {
                moves.remove(previousEntry.bestMove);
                moves.add(0, previousEntry.bestMove);
            }
        }

        int delta = 50;
        int alpha = previousScore - delta;
        int beta = previousScore + delta;

        boolean searchComplete = false;
        int failCount = 0;

        while (!searchComplete && failCount < 3) {
            try {
                bestMove = null;
                bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

                for (Move move : moves) {
                    GameState copy = state.copy();
                    copy.applyMove(move);

                    int score = searchEngine.search(copy, depth - 1, alpha, beta, !isRed, strategy);

                    if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                        bestScore = score;
                        bestMove = move;
                    }

                    // Aspiration window fail check
                    if ((isRed && score >= beta) || (!isRed && score <= alpha)) {
                        throw new AspirationFailException();
                    }

                    if (isRed) {
                        alpha = Math.max(alpha, score);
                    } else {
                        beta = Math.min(beta, score);
                    }
                }
                searchComplete = true;

            } catch (AspirationFailException e) {
                failCount++;
                delta *= 4;
                alpha = previousScore - delta;
                beta = previousScore + delta;

                if (failCount >= 3) {
                    alpha = Integer.MIN_VALUE;
                    beta = Integer.MAX_VALUE;
                }
            }
        }

        statistics.endSearch();
        counter = (int) statistics.getNodeCount();

        return bestMove;
    }

    public static boolean isInCheck(GameState state) {
        boolean isRed = state.redToMove;
        long ownGuard = isRed ? state.redGuard : state.blueGuard;

        // Kein Wächter = nicht im Schach (Spiel bereits vorbei)
        if (ownGuard == 0) {
            return false;
        }

        int guardPosition = Long.numberOfTrailingZeros(ownGuard);
        long enemyTowers = isRed ? state.blueTowers : state.redTowers;
        int[] enemyHeights = isRed ? state.blueStackHeights : state.redStackHeights;

        // Prüfe alle gegnerischen Türme auf Bedrohung
        for (int i = 0; i < 49; i++) {
            if ((enemyTowers & GameState.bit(i)) != 0) {
                int towerHeight = enemyHeights[i];
                int distance = manhattanDistance(i, guardPosition);

                // Turm kann Wächter erobern wenn:
                // 1. Entfernung = Turmhöhe (Guard & Towers Bewegungsregel)
                // 2. Pfad ist frei
                if (distance == towerHeight && isPathClear(i, guardPosition, state)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Prüft ob genügend Material für sinnvolle Züge vorhanden ist
     * In Guard & Towers: Mindestens Türme oder strategische Bedrohung
     */
    public static boolean hasNonPawnMaterial(GameState state) {
        boolean isRed = state.redToMove;
        long ownTowers = isRed ? state.redTowers : state.blueTowers;
        int[] ownHeights = isRed ? state.redStackHeights : state.blueStackHeights;

        // Mindestens ein Turm mit Höhe >= 2, oder mindestens 2 Türme
        int towerCount = 0;
        int totalHeight = 0;

        for (int i = 0; i < 49; i++) {
            if ((ownTowers & GameState.bit(i)) != 0) {
                towerCount++;
                totalHeight += ownHeights[i];

                // Ein hoher Turm reicht aus
                if (ownHeights[i] >= 3) {
                    return true;
                }
            }
        }

        // Mindestens 2 Türme oder Gesamthöhe >= 3
        return towerCount >= 2 || totalHeight >= 3;
    }

    /**
     * Prüft ob das Spiel im Endspiel ist
     * Guard & Towers Endspiel: Wenig Material oder Wächter nah am Ziel
     */
    public static boolean isEndgame(GameState state) {
        // Materialschwelle
        int totalMaterial = 0;
        for (int i = 0; i < 49; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        if (totalMaterial <= SearchConfig.ENDGAME_MATERIAL_THRESHOLD) {
            return true;
        }

        // Wächter nah am Ziel (Rang 1 oder 6/7)
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            int redGuardRank = GameState.rank(redGuardPos);
            if (redGuardRank <= 1) return true;  // Nah an blauem Schloss (D1)
        }

        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int blueGuardRank = GameState.rank(blueGuardPos);
            if (blueGuardRank >= 5) return true;  // Nah an rotem Schloss (D7)
        }

        return false;
    }

    /**
     * Manhattan-Distanz zwischen zwei Positionen (für Guard & Towers Bewegung)
     */
    private static int manhattanDistance(int from, int to) {
        int fromRank = GameState.rank(from);
        int fromFile = GameState.file(from);
        int toRank = GameState.rank(to);
        int toFile = GameState.file(to);

        return Math.abs(fromRank - toRank) + Math.abs(fromFile - toFile);
    }

    /**
     * Prüft ob der Pfad zwischen zwei Positionen orthogonal frei ist
     */
    private static boolean isPathClear(int from, int to, GameState state) {
        int fromRank = GameState.rank(from);
        int fromFile = GameState.file(from);
        int toRank = GameState.rank(to);
        int toFile = GameState.file(to);

        // Nur orthogonale Bewegungen sind erlaubt
        if (fromRank != toRank && fromFile != toFile) {
            return false;
        }

        // Richtung bestimmen
        int rankStep = Integer.compare(toRank, fromRank);
        int fileStep = Integer.compare(toFile, fromFile);

        // Pfad prüfen (ohne Start- und Zielfeld)
        int currentRank = fromRank + rankStep;
        int currentFile = fromFile + fileStep;

        while (currentRank != toRank || currentFile != toFile) {
            int pos = GameState.getIndex(currentRank, currentFile);

            // Blockiert durch andere Figur?
            if (isOccupied(pos, state)) {
                return false;
            }

            currentRank += rankStep;
            currentFile += fileStep;
        }

        return true;
    }

    /**
     * Hilfsmethode: Prüft ob Position besetzt ist
     */
    private static boolean isOccupied(int position, GameState state) {
        long bit = GameState.bit(position);
        return (state.redTowers & bit) != 0 ||
                (state.blueTowers & bit) != 0 ||
                (state.redGuard & bit) != 0 ||
                (state.blueGuard & bit) != 0;
    }

    // === LEGACY COMPATIBILITY METHODS - FIXED ===

    /**
     * Legacy findBestMove method - FIXED to use SearchConfig.SearchStrategy
     */
    public static Move findBestMove(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.ALPHA_BETA);
    }

    /**
     * Legacy findBestMoveWithQuiescence method - FIXED
     */
    public static Move findBestMoveWithQuiescence(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.ALPHA_BETA_Q);
    }

    /**
     * Legacy findBestMoveWithPVS method - FIXED
     */
    public static Move findBestMoveWithPVS(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.PVS);
    }

    /**
     * Ultimate AI method - PVS + Quiescence - FIXED
     */
    public static Move findBestMoveUltimate(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchConfig.SearchStrategy.PVS_Q);
    }

    /**
     * Legacy minimax with timeout - FIXED
     */
    public static int minimaxWithTimeout(GameState state, int depth, int alpha, int beta,
                                         boolean maximizingPlayer, BooleanSupplier timeoutCheck) {
        return searchEngine.searchWithTimeout(state, depth, alpha, beta, maximizingPlayer,
                SearchConfig.SearchStrategy.ALPHA_BETA, timeoutCheck);
    }

    // === EVALUATION INTERFACE ===

    /**
     * Main evaluation interface
     */
    public static int evaluate(GameState state, int depth) {
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
     * Check if game is over
     */
    public static boolean isGameOver(GameState state) {
        // Guard captured
        if (state.redGuard == 0 || state.blueGuard == 0) {
            return true;
        }

        // Guard reached enemy castle
        boolean blueGuardOnD7 = (state.blueGuard & GameState.bit(RED_CASTLE_INDEX)) != 0;
        boolean redGuardOnD1 = (state.redGuard & GameState.bit(BLUE_CASTLE_INDEX)) != 0;

        return blueGuardOnD7 || redGuardOnD1;
    }

    /**
     * Check if move is a capture
     */
    public static boolean isCapture(Move move, GameState state) {
        if (state == null) return false;
        long toBit = GameState.bit(move.to);
        long pieces = state.redToMove ? (state.blueTowers | state.blueGuard) : (state.redTowers | state.redGuard);
        return (pieces & toBit) != 0;
    }

    /**
     * Legacy guard danger check
     */
    public static boolean isGuardInDangerImproved(GameState state, boolean checkRed) {
        return evaluator.isGuardInDanger(state, checkRed);
    }

    // === MOVE SCORING FOR COMPATIBILITY ===

    /**
     * Basic move scoring
     */
    public static int scoreMove(GameState state, Move move) {
        if (state == null) {
            return move.amountMoved;
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

    /**
     * Advanced move scoring
     */
    public static int scoreMoveAdvanced(GameState state, Move move, int depth) {
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

    /**
     * Get search statistics summary

    public static String getSearchStatistics() {
        return statistics.getSummary();
    }*/

    // === SEARCH CONFIGURATION ===

    /**
     * Set timeout checker for searches
     */
    public static void setTimeoutChecker(BooleanSupplier checker) {
        searchEngine.setTimeoutChecker(checker);
    }

    /**
     * Clear timeout checker
     */
    public static void clearTimeoutChecker() {
        searchEngine.clearTimeoutChecker();
    }

    // === HELPER CLASSES ===

    private static class AspirationFailException extends RuntimeException {}

    // === COMPONENT ACCESS (FOR TESTING/DEBUGGING) ===

    /**
     * Get evaluator instance (for testing)
     */
    public static Evaluator getEvaluator() {
        return evaluator;
    }

    /**
     * Get search engine instance (for testing)
     */
    public static SearchEngine getSearchEngine() {
        return searchEngine;
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

    // === UNIFIED STRATEGY ACCESS - FIXED ===

    /**
     * Get all available search strategies - FIXED to use SearchConfig
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
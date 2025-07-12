package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;
import GaT.model.TTEntry;
import GaT.search.MoveGenerator;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * ENHANCED PRINCIPAL VARIATION SEARCH - WITH LMR AND OPTIMIZED STRUCTURE
 *
 * ENHANCEMENTS:
 * ✅ History Heuristic integration for better move ordering
 * ✅ Null-Move Pruning with verification
 * ✅ Late-Move Reductions (LMR)
 * ✅ Graceful timeout handling
 * ✅ Enhanced statistics tracking
 * ✅ Optimized structure without code duplication
 * ✅ Proper cutoff handling with history updates
 */
public class PVSSearch {

    // === DEPENDENCIES ===
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final SearchStatistics statistics = SearchStatistics.getInstance();

    // === TIMEOUT MANAGEMENT ===
    private static BooleanSupplier timeoutChecker = null;
    private static volatile boolean searchInterrupted = false;

    // === SEARCH FUNCTION TYPES ===
    @FunctionalInterface
    private interface SearchFunction {
        int search(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer, boolean isPVNode);
    }

    // === MAIN PVS INTERFACE ===

    /**
     * Standard PVS without Quiescence - ENHANCED WITH LMR
     */
    public static int search(GameState state, int depth, int alpha, int beta,
                             boolean maximizingPlayer, boolean isPVNode) {

        statistics.incrementNodeCount();

        // Graceful timeout handling
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            searchInterrupted = true;
            return Minimax.evaluate(state, depth);
        }

        if (searchInterrupted) {
            return Minimax.evaluate(state, depth);
        }

        // TT-Lookup with caution for PV nodes
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);
        if (entry != null && entry.depth >= depth) {
            statistics.incrementTTHits();

            if (entry.flag == TTEntry.EXACT && (!isPVNode || depth <= 0)) {
                return entry.score;
            } else if (!isPVNode) {
                if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                    return entry.score;
                } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                    return entry.score;
                }
            }
        } else {
            statistics.incrementTTMisses();
        }

        // Null-Move Pruning
        int nullMoveResult = tryNullMovePruning(state, depth, alpha, beta, maximizingPlayer, isPVNode, PVSSearch::search);
        if (nullMoveResult != Integer.MIN_VALUE) {
            return nullMoveResult;
        }

        // Terminal conditions
        if (depth == 0 || Minimax.isGameOver(state)) {
            statistics.incrementLeafNodeCount();
            return Minimax.evaluate(state, depth);
        }

        // Main search with LMR
        return performMainSearch(state, depth, alpha, beta, maximizingPlayer, isPVNode, entry, PVSSearch::search);
    }

    /**
     * PVS with Quiescence Search Integration - ENHANCED WITH LMR
     */
    public static int searchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                           boolean maximizingPlayer, boolean isPVNode) {

        statistics.incrementNodeCount();

        // Graceful timeout handling
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            searchInterrupted = true;
            return Minimax.evaluate(state, depth);
        }

        if (searchInterrupted) {
            return Minimax.evaluate(state, depth);
        }

        // TT-Lookup with PV node caution
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);
        if (entry != null && entry.depth >= depth) {
            statistics.incrementTTHits();

            if (entry.flag == TTEntry.EXACT && (!isPVNode || depth <= 0)) {
                return entry.score;
            } else if (!isPVNode) {
                if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                    return entry.score;
                } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                    return entry.score;
                }
            }
        } else {
            statistics.incrementTTMisses();
        }

        // Null-Move Pruning
        int nullMoveResult = tryNullMovePruning(state, depth, alpha, beta, maximizingPlayer, isPVNode, PVSSearch::searchWithQuiescence);
        if (nullMoveResult != Integer.MIN_VALUE) {
            return nullMoveResult;
        }

        // Terminal conditions
        if (Minimax.isGameOver(state)) {
            statistics.incrementLeafNodeCount();
            return Minimax.evaluate(state, depth);
        }

        // Quiescence Search when depth exhausted
        if (depth <= 0) {
            statistics.incrementQNodeCount();
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        // Main search with LMR
        return performMainSearch(state, depth, alpha, beta, maximizingPlayer, isPVNode, entry, PVSSearch::searchWithQuiescence);
    }

    // === REFACTORED COMMON SEARCH LOGIC ===

    /**
     * Versucht Null-Move Pruning - gemeinsame Logik für beide Suchfunktionen
     */
    private static int tryNullMovePruning(GameState state, int depth, int alpha, int beta,
                                          boolean maximizingPlayer, boolean isPVNode,
                                          SearchFunction searchFunc) {

        if (!isPVNode && depth >= SearchConfig.NULL_MOVE_MIN_DEPTH &&
                beta != Integer.MAX_VALUE && canApplyNullMove(state, depth)) {

            statistics.recordNullMoveAttempt(depth);

            // Null-Move State erstellen
            GameState nullMoveState = state.copy();
            nullMoveState.redToMove = !state.redToMove;

            // Adaptive Reduction verwenden
            int reduction = getNullMoveReduction(state, depth);
            int reducedDepth = depth - 1 - reduction;

            // Geschätzte Knoten für Statistik
            long estimatedNodes = (long) Math.pow(4, Math.max(0, reducedDepth));

            // Reduzierte Suche durchführen
            int nullMoveScore = searchFunc.search(nullMoveState, reducedDepth, -beta, -beta + 1,
                    !maximizingPlayer, false);

            // Fail-High Detection mit verbesserter Statistik
            if ((maximizingPlayer && nullMoveScore >= beta) ||
                    (!maximizingPlayer && nullMoveScore <= alpha)) {

                statistics.recordNullMovePrune(depth, estimatedNodes);

                // Verification bei kritischen Positionen
                if (depth >= SearchConfig.NULL_MOVE_VERIFICATION_DEPTH &&
                        Math.abs(nullMoveScore) < 2000 && isTacticalPosition(state)) {

                    statistics.recordNullMoveVerification(false);

                    // Verification Search mit reduzierter Tiefe
                    int verifyDepth = Math.max(1, depth - reduction - 1);
                    int verifyScore = searchFunc.search(state, verifyDepth, alpha, beta,
                            maximizingPlayer, false);

                    if ((maximizingPlayer && verifyScore >= beta) ||
                            (!maximizingPlayer && verifyScore <= alpha)) {
                        statistics.recordNullMoveVerification(true);
                        return maximizingPlayer ? beta : alpha;
                    } else {
                        statistics.recordNullMoveFailure();
                    }
                } else {
                    // Direkter Cutoff bei eindeutigen Positionen
                    return maximizingPlayer ? beta : alpha;
                }
            } else {
                statistics.recordNullMoveFailure();
            }
        }

        return Integer.MIN_VALUE; // Kein Null-Move Cutoff
    }

    /**
     * Hauptsuchlogik mit LMR - gemeinsam für beide Suchfunktionen
     */
    private static int performMainSearch(GameState state, int depth, int alpha, int beta,
                                         boolean maximizingPlayer, boolean isPVNode, TTEntry entry,
                                         SearchFunction searchFunc) {

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        statistics.addMovesGenerated(moves.size());

        // Enhanced move ordering for PV vs Non-PV nodes
        if (isPVNode) {
            orderMovesForPV(moves, state, depth, entry);
        } else {
            moveOrdering.orderMoves(moves, state, depth, entry);
        }

        Move bestMove = null;
        int originalAlpha = alpha;
        long hash = state.hash();

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            boolean isFirstMove = true;

            for (int i = 0; i < moves.size(); i++) {
                if (i % 3 == 0 && searchInterrupted) break;

                Move move = moves.get(i);
                GameState copy = state.copy();
                copy.applyMove(move);
                statistics.addMovesSearched(1);

                int eval = performSingleMove(copy, move, depth, alpha, beta, isPVNode,
                        isFirstMove, i, state, searchFunc);

                if (isFirstMove) isFirstMove = false;

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistoryOnCutoff(move, state, depth);
                    }
                    break;
                }
            }

            storeTTEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;
            boolean isFirstMove = true;

            for (int i = 0; i < moves.size(); i++) {
                if (i % 3 == 0 && searchInterrupted) break;

                Move move = moves.get(i);
                GameState copy = state.copy();
                copy.applyMove(move);
                statistics.addMovesSearched(1);

                int eval = performSingleMove(copy, move, depth, alpha, beta, isPVNode,
                        isFirstMove, i, state, searchFunc);

                if (isFirstMove) isFirstMove = false;

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistoryOnCutoff(move, state, depth);
                    }
                    break;
                }
            }

            storeTTEntry(hash, minEval, depth, originalAlpha, beta, bestMove);

            // Estimate time saved by null-move pruning
            if (statistics.getNullMoveNodesSkipped() > 0) {
                long avgTimePerNode = statistics.getTotalSearchTime() > 0 ?
                        statistics.getTotalSearchTime() * 1000000 / statistics.getTotalNodes() : 1000;
                statistics.estimateTimeSaved(avgTimePerNode);
            }

            return minEval;
        }
    }

    /**
     * Führt die Suche für einen einzelnen Zug durch - mit LMR Integration
     */
    private static int performSingleMove(GameState copy, Move move, int depth, int alpha, int beta,
                                         boolean isPVNode, boolean isFirstMove, int moveIndex,
                                         GameState originalState, SearchFunction searchFunc) {

        boolean maximizingPlayer = !originalState.redToMove; // Kopie hat Spieler gewechselt
        boolean needsFullSearch = true;
        int eval = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE; // FIX: Initialisierung

        // === LATE MOVE REDUCTIONS ===
        if (shouldReduceMove(originalState, move, depth, moveIndex, isPVNode)) {
            int reduction = calculateLMRReduction(originalState, move, depth, moveIndex);
            int reducedDepth = Math.max(0, depth - 1 - reduction);

            // Reduzierte Suche mit null-window
            if (maximizingPlayer) {
                eval = searchFunc.search(copy, reducedDepth, alpha, alpha + 1, true, false);
            } else {
                eval = searchFunc.search(copy, reducedDepth, beta - 1, beta, false, false);
            }

            statistics.incrementLMRReductions();

            // Re-search falls reduzierte Suche zu gut war
            boolean needsReSearch = maximizingPlayer ? (eval > alpha) : (eval < beta);

            if (needsReSearch) {
                // Normale Tiefe mit null-window
                if (maximizingPlayer) {
                    eval = searchFunc.search(copy, depth - 1, alpha, alpha + 1, true, false);
                    needsFullSearch = (eval > alpha && isPVNode);
                } else {
                    eval = searchFunc.search(copy, depth - 1, beta - 1, beta, false, false);
                    needsFullSearch = (eval < beta && eval > alpha && isPVNode);
                }
            } else {
                needsFullSearch = false;
            }
        }

        // === NORMALE SUCHE (falls nötig) ===
        if (needsFullSearch) {
            if (isFirstMove || isPVNode) {
                eval = searchFunc.search(copy, depth - 1, alpha, beta, maximizingPlayer, isPVNode);
            } else {
                // Null-window search
                int nullWindow = maximizingPlayer ? alpha + 1 : beta - 1;
                eval = searchFunc.search(copy, depth - 1,
                        maximizingPlayer ? alpha : nullWindow,
                        maximizingPlayer ? nullWindow : beta,
                        maximizingPlayer, false);

                // Re-search bei PV-Node falls nötig
                if (isPVNode && ((maximizingPlayer && eval > alpha && eval < beta) ||
                        (!maximizingPlayer && eval < beta && eval > alpha))) {
                    eval = searchFunc.search(copy, depth - 1, alpha, beta, maximizingPlayer, true);
                }
            }
        }

        return eval;
    }

    // === LMR HELPER METHODS ===

    /**
     * Prüft ob ein Zug für Late-Move Reduction geeignet ist
     */
    private static boolean shouldReduceMove(GameState state, Move move, int depth, int moveIndex, boolean isPVNode) {
        // Grundbedingungen
        if (depth < SearchConfig.LMR_MIN_DEPTH) return false;
        if (moveIndex < SearchConfig.LMR_MIN_MOVE_COUNT) return false;
        if (isPVNode && moveIndex == 0) return false; // Nie den ersten PV-Zug reduzieren

        // Wichtige Züge NICHT reduzieren:
        if (Minimax.isCapture(move, state)) return false;
        if (isGuardMove(move, state)) return false;
        if (threatsEnemyGuard(move, state)) return false;
        if (moveTowardsCastle(move, state)) return false;
        if (move.amountMoved >= 4) return false; // Hohe Türme

        return true;
    }

    /**
     * Berechnet LMR-Reduktion für einen Zug
     */
    private static int calculateLMRReduction(GameState state, Move move, int depth, int moveIndex) {
        int reduction = 1; // Base reduction

        // Mehr Reduktion für späte Züge
        if (moveIndex > 8) reduction++;
        if (moveIndex > 16) reduction++;

        // Mehr Reduktion für tiefe Knoten
        if (depth > 8) reduction++;

        // Mehr Reduktion für ruhige Positionen
        if (isQuietPosition(state)) reduction++;

        // Weniger Reduktion für zentrumsnahe Züge
        if (isNearCenter(move.to)) reduction = Math.max(1, reduction - 1);

        // Weniger Reduktion für Vorwärts-Züge
        if (isAdvancingMove(move, state)) reduction = Math.max(1, reduction - 1);

        // Weniger Reduktion für taktische Positionen
        if (isTacticalPosition(state)) reduction = Math.max(1, reduction - 1);

        return Math.min(reduction, 3); // Max 3 Reduktion
    }

    // === HELPER METHODS ===

    private static boolean isGuardMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
    }

    private static boolean threatsEnemyGuard(Move move, GameState state) {
        long enemyGuard = state.redToMove ? state.blueGuard : state.redGuard;
        if (enemyGuard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(enemyGuard);
        int rankDiff = Math.abs(GameState.rank(move.to) - GameState.rank(guardPos));
        int fileDiff = Math.abs(GameState.file(move.to) - GameState.file(guardPos));

        if (rankDiff != 0 && fileDiff != 0) return false;
        return Math.max(rankDiff, fileDiff) <= move.amountMoved;
    }

    private static boolean moveTowardsCastle(Move move, GameState state) {
        if (!isGuardMove(move, state)) return false;

        int enemyCastle = state.redToMove ?
                GameState.getIndex(0, 3) : GameState.getIndex(6, 3);

        int oldDistance = manhattanDistance(move.from, enemyCastle);
        int newDistance = manhattanDistance(move.to, enemyCastle);

        return newDistance < oldDistance;
    }

    private static boolean isNearCenter(int square) {
        int file = GameState.file(square);
        int rank = GameState.rank(square);
        return file >= 2 && file <= 4 && rank >= 2 && rank <= 4;
    }

    private static boolean isAdvancingMove(Move move, GameState state) {
        if (state.redToMove) {
            return GameState.rank(move.to) < GameState.rank(move.from); // Nach vorne für Rot
        } else {
            return GameState.rank(move.to) > GameState.rank(move.from); // Nach vorne für Blau
        }
    }

    // === EXISTING HELPER METHODS ===

    private static boolean canApplyNullMove(GameState state, int depth) {
        if (!SearchConfig.NULL_MOVE_ENABLED) return false;
        if (depth < SearchConfig.NULL_MOVE_MIN_DEPTH) return false;
        if (Minimax.isInCheck(state)) return false;
        if (!Minimax.hasNonPawnMaterial(state)) return false;
        if (Minimax.isEndgame(state) && getTotalMaterial(state) <= 4) return false;
        return true;
    }

    private static int getTotalMaterial(GameState state) {
        int total = 0;
        for (int i = 0; i < 49; i++) {
            total += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        total += (state.redGuard != 0 ? 1 : 0) + (state.blueGuard != 0 ? 1 : 0);
        return total;
    }

    private static int getNullMoveReduction(GameState state, int depth) {
        int baseReduction = SearchConfig.NULL_MOVE_REDUCTION;

        if (Minimax.isEndgame(state)) {
            return Math.max(1, baseReduction - 1);
        }

        if (depth >= 10) {
            return baseReduction + 1;
        }

        if (isTacticalPosition(state)) {
            return Math.max(1, baseReduction - 1);
        }

        if (isQuietPosition(state)) {
            return baseReduction + 1;
        }

        return baseReduction;
    }

    private static boolean isTacticalPosition(GameState state) {
        boolean isRed = state.redToMove;

        // Wächter nah beieinander = taktisch
        if (state.redGuard != 0 && state.blueGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int distance = manhattanDistance(redGuardPos, blueGuardPos);

            if (distance <= 3) {
                return true;
            }
        }

        // Hohe Türme in der Nähe des gegnerischen Wächters
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        if (enemyGuard != 0) {
            int enemyGuardPos = Long.numberOfTrailingZeros(enemyGuard);
            long ownTowers = isRed ? state.redTowers : state.blueTowers;
            int[] ownHeights = isRed ? state.redStackHeights : state.blueStackHeights;

            for (int i = 0; i < 49; i++) {
                if ((ownTowers & GameState.bit(i)) != 0 && ownHeights[i] >= 3) {
                    int distance = manhattanDistance(i, enemyGuardPos);
                    if (distance <= ownHeights[i] + 1) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isQuietPosition(GameState state) {
        if (isTacticalPosition(state)) return false;

        boolean isRed = state.redToMove;
        long ownTowers = isRed ? state.redTowers : state.blueTowers;
        int[] ownHeights = isRed ? state.redStackHeights : state.blueStackHeights;

        int highTowers = 0;
        for (int i = 0; i < 49; i++) {
            if ((ownTowers & GameState.bit(i)) != 0 && ownHeights[i] >= 4) {
                highTowers++;
            }
        }

        return highTowers <= 1;
    }

    private static int manhattanDistance(int from, int to) {
        int fromRank = GameState.rank(from);
        int fromFile = GameState.file(from);
        int toRank = GameState.rank(to);
        int toFile = GameState.file(to);

        return Math.abs(fromRank - toRank) + Math.abs(fromFile - toFile);
    }

    // === MOVE ORDERING ===

    private static void orderMovesForPV(List<Move> moves, GameState state, int depth, TTEntry entry) {
        // TT Move has highest priority
        if (entry != null && entry.bestMove != null) {
            for (int i = 0; i < moves.size(); i++) {
                if (moves.get(i).equals(entry.bestMove)) {
                    Move ttMove = moves.remove(i);
                    moves.add(0, ttMove);
                    break;
                }
            }
        }

        // Enhanced move ordering for PV nodes
        if (moves.size() > 1) {
            int startIndex = (entry != null && entry.bestMove != null) ? 1 : 0;
            List<Move> restMoves = moves.subList(startIndex, moves.size());

            restMoves.sort((a, b) -> {
                int scoreA = scoreMoveForPVEnhanced(state, a, depth);
                int scoreB = scoreMoveForPVEnhanced(state, b, depth);
                return Integer.compare(scoreB, scoreA);
            });
        }
    }

    private static int scoreMoveForPVEnhanced(GameState state, Move move, int depth) {
        int score = Minimax.scoreMove(state, move);

        if (!Minimax.isCapture(move, state)) {
            if (moveOrdering.getHistoryHeuristic().isQuietMove(move, state)) {
                boolean isRedMove = state.redToMove;
                score += moveOrdering.getHistoryHeuristic().getScore(move, isRedMove);
            }

            score += move.to * 2;
            score += move.amountMoved * 5;

            int targetFile = GameState.file(move.to);
            int targetRank = GameState.rank(move.to);
            int centrality = Math.abs(targetFile - 3) + Math.abs(targetRank - 3);
            score += (6 - centrality) * 3;

            long hash = state.hash();
            score += (int)(hash % 20) - 10;
        }

        return score;
    }

    private static void storeTTEntry(long hash, int score, int depth, int originalAlpha, int beta, Move bestMove) {
        int flag;
        if (score <= originalAlpha) {
            flag = TTEntry.UPPER_BOUND;
        } else if (score >= beta) {
            flag = TTEntry.LOWER_BOUND;
        } else {
            flag = TTEntry.EXACT;
        }

        TTEntry entry = new TTEntry(score, depth, flag, bestMove);
        Minimax.storeTranspositionEntry(hash, entry);
        statistics.incrementTTStores();
    }

    // === TIMEOUT MANAGEMENT ===

    public static void setTimeoutChecker(BooleanSupplier checker) {
        timeoutChecker = checker;
    }

    public static void clearTimeoutChecker() {
        timeoutChecker = null;
    }

    public static void resetSearchState() {
        searchInterrupted = false;
    }

    // === LEGACY COMPATIBILITY ===

    public static void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry entry) {
        orderMovesForPV(moves, state, depth, entry);
    }
}
package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;
import GaT.model.TTEntry;
import GaT.search.MoveGenerator;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * ENHANCED PRINCIPAL VARIATION SEARCH - WITH HISTORY HEURISTIC
 *
 * ENHANCEMENTS:
 * ✅ History Heuristic integration for better move ordering
 * ✅ Graceful timeout handling
 * ✅ Enhanced statistics tracking
 * ✅ Proper cutoff handling with history updates
 * ✅ Optimized for single-threaded performance
 */
public class PVSSearch {

    // === DEPENDENCIES - ENHANCED ===
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final SearchStatistics statistics = SearchStatistics.getInstance();

    // === TIMEOUT MANAGEMENT ===
    private static BooleanSupplier timeoutChecker = null;
    private static volatile boolean searchInterrupted = false;

    // === MAIN PVS INTERFACE ===

    /**
     * Standard PVS without Quiescence - ENHANCED WITH HISTORY
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

        // *** NEUE NULL-MOVE PRUNING SEKTION ***
        // Null-Move Pruning (nur für Non-PV nodes und Beta-Cutoff Suche)
        if (!isPVNode && depth >= SearchConfig.NULL_MOVE_MIN_DEPTH &&
                beta != Integer.MAX_VALUE && canApplyNullMove(state, depth)) {

            // *** ERWEITERTE STATISTIK-AUFZEICHNUNG ***
            statistics.recordNullMoveAttempt(depth);

            // Null-Move State erstellen
            GameState nullMoveState = state.copy();
            nullMoveState.redToMove = !state.redToMove;

            // *** ADAPTIVE REDUCTION VERWENDEN ***
            int reduction = getNullMoveReduction(state, depth);
            int reducedDepth = depth - 1 - reduction;

            // Geschätzte Knoten für Statistik
            long estimatedNodes = (long) Math.pow(4, Math.max(0, reducedDepth));

            // Reduzierte Suche durchführen
            int nullMoveScore = search(nullMoveState, reducedDepth, -beta, -beta + 1,
                    !maximizingPlayer, false);

            // Fail-High Detection mit verbesserter Statistik
            if ((maximizingPlayer && nullMoveScore >= beta) ||
                    (!maximizingPlayer && nullMoveScore <= alpha)) {

                // *** ERFOLGREICHE PRUNE AUFZEICHNEN ***
                statistics.recordNullMovePrune(depth, estimatedNodes);

                // Verification bei kritischen Positionen
                if (depth >= SearchConfig.NULL_MOVE_VERIFICATION_DEPTH &&
                        Math.abs(nullMoveScore) < 2000 && isTacticalPosition(state)) {

                    statistics.recordNullMoveVerification(false); // Start verification

                    // Verification Search mit reduzierter Tiefe
                    int verifyDepth = Math.max(1, depth - reduction - 1);
                    int verifyScore = search(state, verifyDepth, alpha, beta,
                            maximizingPlayer, false);

                    if ((maximizingPlayer && verifyScore >= beta) ||
                            (!maximizingPlayer && verifyScore <= alpha)) {
                        statistics.recordNullMoveVerification(true); // Verification successful
                        return maximizingPlayer ? beta : alpha;
                    } else {
                        statistics.recordNullMoveFailure(); // Verification failed
                    }
                } else {
                    // Direkter Cutoff bei eindeutigen Positionen
                    return maximizingPlayer ? beta : alpha;
                }
            } else {
                // *** FEHLGESCHLAGENE PRUNE AUFZEICHNEN ***
                statistics.recordNullMoveFailure();
            }
        }

        // Terminal conditions
        if (depth == 0 || Minimax.isGameOver(state)) {
            statistics.incrementLeafNodeCount();
            return Minimax.evaluate(state, depth);
        }

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
        boolean isFirstMove = true;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (int i = 0; i < moves.size(); i++) {
                if (i % 3 == 0 && searchInterrupted) {
                    break;
                }

                Move move = moves.get(i);
                GameState copy = state.copy();
                copy.applyMove(move);
                statistics.addMovesSearched(1);

                int eval;

                if (isFirstMove || isPVNode) {
                    eval = search(copy, depth - 1, alpha, beta, false, isPVNode);
                    isFirstMove = false;
                } else {
                    int nullWindow = isPVNode ? alpha + 10 : alpha + 1;
                    eval = search(copy, depth - 1, alpha, nullWindow, false, false);

                    if (eval > alpha && eval < beta) {
                        eval = search(copy, depth - 1, eval, beta, false, true);
                    }
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    // ENHANCED HISTORY UPDATE
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistoryOnCutoff(move, state, depth); // ← CORRECTED SIGNATURE
                    }
                    break;
                }
            }

            storeTTEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;

            for (int i = 0; i < moves.size(); i++) {
                if (i % 3 == 0 && searchInterrupted) {
                    break;
                }

                Move move = moves.get(i);
                GameState copy = state.copy();
                copy.applyMove(move);
                statistics.addMovesSearched(1);

                int eval;

                if (isFirstMove || isPVNode) {
                    eval = search(copy, depth - 1, alpha, beta, true, isPVNode);
                    isFirstMove = false;
                } else {
                    int nullWindow = isPVNode ? beta - 10 : beta - 1;
                    eval = search(copy, depth - 1, nullWindow, beta, true, false);

                    if (eval < beta && eval > alpha) {
                        eval = search(copy, depth - 1, alpha, eval, true, true);
                    }
                }

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    // ENHANCED HISTORY UPDATE
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistoryOnCutoff(move, state, depth); // ← CORRECTED SIGNATURE
                    }
                    break;
                }
            }

            storeTTEntry(hash, minEval, depth, originalAlpha, beta, bestMove);
            return minEval;
        }
    }

    /**
     * PVS with Quiescence Search Integration - ENHANCED WITH HISTORY
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

        // *** NEUE NULL-MOVE PRUNING SEKTION ***
        if (!isPVNode && depth >= SearchConfig.NULL_MOVE_MIN_DEPTH &&
                beta != Integer.MAX_VALUE && canApplyNullMove(state, depth)) {

            // *** ERWEITERTE STATISTIK-AUFZEICHNUNG ***
            statistics.recordNullMoveAttempt(depth);

            // Null-Move State erstellen
            GameState nullMoveState = state.copy();
            nullMoveState.redToMove = !state.redToMove;

            // *** ADAPTIVE REDUCTION VERWENDEN ***
            int reduction = getNullMoveReduction(state, depth);
            int reducedDepth = depth - 1 - reduction;

            // Geschätzte Knoten für Statistik
            long estimatedNodes = (long) Math.pow(4, Math.max(0, reducedDepth));

            // Reduzierte Suche durchführen
            int nullMoveScore = search(nullMoveState, reducedDepth, -beta, -beta + 1,
                    !maximizingPlayer, false);

            // Fail-High Detection mit verbesserter Statistik
            if ((maximizingPlayer && nullMoveScore >= beta) ||
                    (!maximizingPlayer && nullMoveScore <= alpha)) {

                // *** ERFOLGREICHE PRUNE AUFZEICHNEN ***
                statistics.recordNullMovePrune(depth, estimatedNodes);

                // Verification bei kritischen Positionen
                if (depth >= SearchConfig.NULL_MOVE_VERIFICATION_DEPTH &&
                        Math.abs(nullMoveScore) < 2000 && isTacticalPosition(state)) {

                    statistics.recordNullMoveVerification(false); // Start verification

                    // Verification Search mit reduzierter Tiefe
                    int verifyDepth = Math.max(1, depth - reduction - 1);
                    int verifyScore = search(state, verifyDepth, alpha, beta,
                            maximizingPlayer, false);

                    if ((maximizingPlayer && verifyScore >= beta) ||
                            (!maximizingPlayer && verifyScore <= alpha)) {
                        statistics.recordNullMoveVerification(true); // Verification successful
                        return maximizingPlayer ? beta : alpha;
                    } else {
                        statistics.recordNullMoveFailure(); // Verification failed
                    }
                } else {
                    // Direkter Cutoff bei eindeutigen Positionen
                    return maximizingPlayer ? beta : alpha;
                }
            } else {
                // *** FEHLGESCHLAGENE PRUNE AUFZEICHNEN ***
                statistics.recordNullMoveFailure();
            }
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
        boolean isFirstMove = true;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (int i = 0; i < moves.size(); i++) {
                if (i % 3 == 0 && searchInterrupted) {
                    break;
                }

                Move move = moves.get(i);
                GameState copy = state.copy();
                copy.applyMove(move);
                statistics.addMovesSearched(1);

                int eval;

                if (isFirstMove || isPVNode) {
                    eval = searchWithQuiescence(copy, depth - 1, alpha, beta, false, isPVNode);
                    isFirstMove = false;
                } else {
                    int nullWindow = isPVNode ? alpha + 10 : alpha + 1;
                    eval = searchWithQuiescence(copy, depth - 1, alpha, nullWindow, false, false);

                    if (eval > alpha && eval < beta) {
                        eval = searchWithQuiescence(copy, depth - 1, eval, beta, false, true);
                    }
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    // ENHANCED HISTORY UPDATE
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistoryOnCutoff(move, state, depth); // ← CORRECTED SIGNATURE
                    }
                    break;
                }
            }

            storeTTEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;

        } else {
            // === MINIMIZING PLAYER ===
            int minEval = Integer.MAX_VALUE;

            for (int i = 0; i < moves.size(); i++) {
                if (i % 3 == 0 && searchInterrupted) {
                    break;
                }

                Move move = moves.get(i);
                GameState copy = state.copy();
                copy.applyMove(move);
                statistics.addMovesSearched(1);

                int eval;

                if (isFirstMove || isPVNode) {
                    eval = searchWithQuiescence(copy, depth - 1, alpha, beta, true, isPVNode);
                    isFirstMove = false;
                } else {
                    int nullWindow = isPVNode ? beta - 10 : beta - 1;
                    eval = searchWithQuiescence(copy, depth - 1, nullWindow, beta, true, false);

                    if (eval < beta && eval > alpha) {
                        eval = searchWithQuiescence(copy, depth - 1, alpha, eval, true, true);
                    }
                }

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    // ENHANCED HISTORY UPDATE
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistoryOnCutoff(move, state, depth); // ← CORRECTED SIGNATURE
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

    // === ENHANCED MOVE ORDERING FOR PV NODES ===

    /**
     * Special move ordering for PV nodes - ENHANCED
     */
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

    /**
     * Enhanced move scoring for PV nodes with history integration
     */
    private static int scoreMoveForPVEnhanced(GameState state, Move move, int depth) {
        int score = Minimax.scoreMove(state, move);

        // Enhanced history scoring for PV nodes
        if (!Minimax.isCapture(move, state)) {
            // Get history score from both systems
            if (moveOrdering.getHistoryHeuristic().isQuietMove(move, state)) {
                boolean isRedMove = state.redToMove;
                score += moveOrdering.getHistoryHeuristic().getScore(move, isRedMove);
            }

            // PV-specific bonuses
            score += move.to * 2;
            score += move.amountMoved * 5;

            // Central control bonus
            int targetFile = GameState.file(move.to);
            int targetRank = GameState.rank(move.to);
            int centrality = Math.abs(targetFile - 3) + Math.abs(targetRank - 3);
            score += (6 - centrality) * 3;

            // Anti-repetition bonus
            long hash = state.hash();
            score += (int)(hash % 20) - 10;
        }

        return score;
    }

    /**
     * Prüft, ob Null-Move Pruning angewendet werden kann
     * @param state Current game state
     * @param depth Current search depth
     * @return true wenn Null-Move sicher anwendbar ist
     */
    private static boolean canApplyNullMove(GameState state, int depth) {
        // Null-Move global deaktiviert
        if (!SearchConfig.NULL_MOVE_ENABLED) {
            return false;
        }

        // Minimale Tiefe nicht erreicht
        if (depth < SearchConfig.NULL_MOVE_MIN_DEPTH) {
            return false;
        }

        // Wächter im Schach - kein Null-Move!
        if (Minimax.isInCheck(state)) {
            return false;
        }

        // Kein Material für sinnvolle Züge (kritisch in Guard & Towers)
        if (!Minimax.hasNonPawnMaterial(state)) {
            return false;
        }

        // Endspiel-Vorsicht: Bei sehr wenig Material kann Null-Move gefährlich sein
        if (Minimax.isEndgame(state) && getTotalMaterial(state) <= 4) {
            return false;
        }

        return true;
    }



    // === HELPER METHODS ===

    /**
     * Store entry in transposition table
     */
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

    /**
     * Set timeout checker from TimedMinimax
     */
    public static void setTimeoutChecker(BooleanSupplier checker) {
        timeoutChecker = checker;
    }

    /**
     * Clear timeout checker (for cleanup)
     */
    public static void clearTimeoutChecker() {
        timeoutChecker = null;
    }

    /**
     * Reset search state for clean start
     */
    public static void resetSearchState() {
        searchInterrupted = false;
    }

    // === LEGACY COMPATIBILITY ===

    /**
     * Legacy method for backward compatibility
     */
    public static void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry entry) {
        orderMovesForPV(moves, state, depth, entry);
    }


    /**
     * Hilfsmethode: Gesamtmaterial für Null-Move Entscheidung
     */
    private static int getTotalMaterial(GameState state) {
        int total = 0;
        for (int i = 0; i < 49; i++) {
            total += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        // Wächter zählen jeweils als 1
        total += (state.redGuard != 0 ? 1 : 0) + (state.blueGuard != 0 ? 1 : 0);
        return total;
    }


    /**
     * Erweiterte Null-Move Reduction basierend auf Position und Tiefe
     * Diese Methode gehört als private static method in PVSSearch.java
     */
    private static int getNullMoveReduction(GameState state, int depth) {
        int baseReduction = SearchConfig.NULL_MOVE_REDUCTION;

        // === SPIELPHASEN-ANPASSUNG ===

        // Vorsichtiger im Endspiel (weniger aggressive Reduction)
        if (Minimax.isEndgame(state)) {
            return Math.max(1, baseReduction - 1);
        }

        // === TIEFENABHÄNGIGE ANPASSUNG ===

        // Aggressiver bei großer Tiefe (mehr Reduction möglich)
        if (depth >= 10) {
            return baseReduction + 1;
        }

        // === GUARD & TOWERS SPEZIFISCHE ANPASSUNGEN ===

        // Weniger Reduction bei taktischen Positionen
        if (isTacticalPosition(state)) {
            return Math.max(1, baseReduction - 1);
        }

        // Mehr Reduction bei ruhigen Positionen
        if (isQuietPosition(state)) {
            return baseReduction + 1;
        }

        return baseReduction;
    }

    /**
     * Prüft ob Position taktisch ist (vorsichtigere Null-Move Behandlung)
     */
    private static boolean isTacticalPosition(GameState state) {
        boolean isRed = state.redToMove;

        // Wächter nah beieinander = taktisch
        if (state.redGuard != 0 && state.blueGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int distance = manhattanDistance(redGuardPos, blueGuardPos);

            if (distance <= 3) {
                return true;  // Wächter-Kampf möglich
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
                        return true;  // Eroberung möglich
                    }
                }
            }
        }

        return false;
    }

    /**
     * Prüft ob Position ruhig ist (aggressive Null-Move Behandlung)
     */
    private static boolean isQuietPosition(GameState state) {
        // Keine direkten Bedrohungen
        if (isTacticalPosition(state)) {
            return false;
        }

        // Wenige hohe Türme = ruhiger
        boolean isRed = state.redToMove;
        long ownTowers = isRed ? state.redTowers : state.blueTowers;
        int[] ownHeights = isRed ? state.redStackHeights : state.blueStackHeights;

        int highTowers = 0;
        for (int i = 0; i < 49; i++) {
            if ((ownTowers & GameState.bit(i)) != 0 && ownHeights[i] >= 4) {
                highTowers++;
            }
        }

        return highTowers <= 1;  // Maximal 1 hoher Turm = ruhig
    }

    /**
     * Manhattan-Distanz zwischen zwei Positionen
     */
    private static int manhattanDistance(int from, int to) {
        int fromRank = GameState.rank(from);
        int fromFile = GameState.file(from);
        int toRank = GameState.rank(to);
        int toFile = GameState.file(to);

        return Math.abs(fromRank - toRank) + Math.abs(fromFile - toFile);
    }
}
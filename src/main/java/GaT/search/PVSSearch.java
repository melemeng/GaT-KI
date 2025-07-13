package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;
import GaT.model.TTEntry;
import GaT.search.MoveGenerator;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * FIXED PRINCIPAL VARIATION SEARCH - COMPATIBLE WITH SIMPLIFIED MOVE ORDERING
 *
 * FIXES:
 * ✅ Removed dependencies on HistoryHeuristic (now simplified in MoveOrdering)
 * ✅ Compatible with simplified moveOrdering.updateHistory() method
 * ✅ Uses simplified move scoring without HistoryHeuristic.isQuietMove()
 * ✅ Maintains all LMR and PVS optimizations
 * ✅ Fixed null pointer errors from HistoryHeuristic
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
     * Standard PVS without Quiescence - FIXED FOR SIMPLIFIED MOVE ORDERING
     */
    /**
     * NULL-SAFE PVS searchWithQuiescence - MAIN ENTRY POINT FIX
     */
    public static int searchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                           boolean maximizingPlayer, boolean isPVNode) {

        // CRITICAL NULL CHECK AT MAIN ENTRY POINT
        if (state == null) {
            System.err.println("❌ CRITICAL: Null state passed to PVSSearch.searchWithQuiescence");
            return 0; // Safe neutral value
        }

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
        long hash;
        try {
            hash = state.hash();
        } catch (Exception e) {
            System.err.println("❌ ERROR: state.hash() failed: " + e.getMessage());
            // Continue without TT lookup
            hash = 0;
        }

        TTEntry entry = null;
        if (hash != 0) {
            try {
                entry = Minimax.getTranspositionEntry(hash);
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
            } catch (Exception e) {
                System.err.println("❌ ERROR: TT lookup failed: " + e.getMessage());
                // Continue without TT
            }
        }

        // Null-Move Pruning
        int nullMoveResult = tryNullMovePruning(state, depth, alpha, beta, maximizingPlayer, isPVNode, PVSSearch::searchWithQuiescence);
        if (nullMoveResult != Integer.MIN_VALUE) {
            return nullMoveResult;
        }

        // Terminal conditions
        try {
            if (Minimax.isGameOver(state)) {
                statistics.incrementLeafNodeCount();
                return Minimax.evaluate(state, depth);
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: Game over check failed: " + e.getMessage());
            // Continue with search
        }

        // Quiescence Search when depth exhausted
        if (depth <= 0) {
            statistics.incrementQNodeCount();
            try {
                return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
            } catch (Exception e) {
                System.err.println("❌ ERROR: QuiescenceSearch.quiesce failed: " + e.getMessage());
                return Minimax.evaluate(state, depth);
            }
        }

        // Main search with LMR
        try {
            return performMainSearch(state, depth, alpha, beta, maximizingPlayer, isPVNode, entry, PVSSearch::searchWithQuiescence);
        } catch (Exception e) {
            System.err.println("❌ ERROR: performMainSearch failed: " + e.getMessage());
            return Minimax.evaluate(state, depth);
        }
    }

    /**
     * NULL-SAFE PVS search - SECOND ENTRY POINT FIX
     */
    public static int search(GameState state, int depth, int alpha, int beta,
                             boolean maximizingPlayer, boolean isPVNode) {

        // CRITICAL NULL CHECK AT MAIN ENTRY POINT
        if (state == null) {
            System.err.println("❌ CRITICAL: Null state passed to PVSSearch.search");
            return 0; // Safe neutral value
        }

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
        long hash;
        try {
            hash = state.hash();
        } catch (Exception e) {
            System.err.println("❌ ERROR: state.hash() failed: " + e.getMessage());
            hash = 0;
        }

        TTEntry entry = null;
        if (hash != 0) {
            try {
                entry = Minimax.getTranspositionEntry(hash);
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
            } catch (Exception e) {
                System.err.println("❌ ERROR: TT lookup failed: " + e.getMessage());
            }
        }

        // Null-Move Pruning
        int nullMoveResult = tryNullMovePruning(state, depth, alpha, beta, maximizingPlayer, isPVNode, PVSSearch::search);
        if (nullMoveResult != Integer.MIN_VALUE) {
            return nullMoveResult;
        }

        // Terminal conditions
        try {
            if (depth == 0 || Minimax.isGameOver(state)) {
                statistics.incrementLeafNodeCount();
                return Minimax.evaluate(state, depth);
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: Terminal condition check failed: " + e.getMessage());
            return Minimax.evaluate(state, depth);
        }

        // Main search with LMR
        try {
            return performMainSearch(state, depth, alpha, beta, maximizingPlayer, isPVNode, entry, PVSSearch::search);
        } catch (Exception e) {
            System.err.println("❌ ERROR: performMainSearch failed: " + e.getMessage());
            return Minimax.evaluate(state, depth);
        }
    }

    // === REFACTORED COMMON SEARCH LOGIC ===

    /**
     * NULL-SAFE tryNullMovePruning - ADDITIONAL CRITICAL FIX
     */
    private static int tryNullMovePruning(GameState state, int depth, int alpha, int beta,
                                          boolean maximizingPlayer, boolean isPVNode,
                                          SearchFunction searchFunc) {

        // CRITICAL NULL CHECK
        if (state == null) {
            System.err.println("❌ ERROR: Null state in tryNullMovePruning");
            return Integer.MIN_VALUE; // No null-move pruning
        }

        // Skip null-move in PV nodes or shallow searches
        if (isPVNode || depth < 3) {
            return Integer.MIN_VALUE;
        }

        // Skip if in check or endgame
        try {
            if (Minimax.isInCheck(state) || Minimax.isEndgame(state)) {
                return Integer.MIN_VALUE;
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: Check/Endgame test failed in tryNullMovePruning: " + e.getMessage());
            return Integer.MIN_VALUE;
        }

        // Skip if insufficient material
        try {
            if (!Minimax.hasNonPawnMaterial(state)) {
                return Integer.MIN_VALUE;
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: Material test failed in tryNullMovePruning: " + e.getMessage());
            return Integer.MIN_VALUE;
        }

        statistics.recordNullMoveAttempt(depth);

        try {
            // Create null-move state (just switch turns)
            GameState nullMoveState = state.copy();
            if (nullMoveState == null) {
                System.err.println("❌ ERROR: state.copy() returned null in tryNullMovePruning");
                return Integer.MIN_VALUE;
            }

            // Switch turn without making a move
            nullMoveState.redToMove = !nullMoveState.redToMove;

            // Reduced depth search
            int reduction = Math.min(3, depth / 4 + 3);
            int nullDepth = Math.max(0, depth - 1 - reduction);

            int nullScore;
            try {
                nullScore = searchFunc.search(nullMoveState, nullDepth, -beta, -alpha, !maximizingPlayer, false);
            } catch (Exception e) {
                System.err.println("❌ ERROR: Null-move search failed: " + e.getMessage());
                return Integer.MIN_VALUE;
            }

            // Null-move cutoff?
            if (maximizingPlayer && nullScore >= beta) {
                statistics.recordNullMovePrune(depth, Math.max(1, depth * depth));

                // Verification search for critical positions
                if (depth >= 6 && nullScore < 5000) {
                    try {
                        int verifyScore = searchFunc.search(state, depth - 5, alpha, beta, maximizingPlayer, false);
                        statistics.recordNullMoveVerification(verifyScore >= beta);

                        if (verifyScore >= beta) {
                            return maximizingPlayer ? beta : alpha;
                        } else {
                            statistics.recordNullMoveFailure();
                        }
                    } catch (Exception e) {
                        System.err.println("❌ ERROR: Verification search failed: " + e.getMessage());
                        // Continue with normal search
                    }
                } else {
                    // Direct cutoff for clear positions
                    return maximizingPlayer ? beta : alpha;
                }
            } else if (!maximizingPlayer && nullScore <= alpha) {
                statistics.recordNullMovePrune(depth, Math.max(1, depth * depth));
                return maximizingPlayer ? beta : alpha;
            } else {
                statistics.recordNullMoveFailure();
            }

        } catch (Exception e) {
            System.err.println("❌ ERROR: General null-move pruning failed: " + e.getMessage());
            statistics.recordNullMoveFailure();
        }

        return Integer.MIN_VALUE; // No null-move cutoff
    }

    /**
     * Hauptsuchlogik mit LMR - gemeinsam für beide Suchfunktionen
     */
    private static int performMainSearch(GameState state, int depth, int alpha, int beta,
                                         boolean maximizingPlayer, boolean isPVNode, TTEntry entry,
                                         SearchFunction searchFunc) {

        // CRITICAL NULL CHECK
        if (state == null) {
            System.err.println("❌ ERROR: Null state in performMainSearch");
            return maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        statistics.addMovesGenerated(moves.size());

        // FIXED: Use simplified move ordering
        moveOrdering.orderMoves(moves, state, depth, entry);

        Move bestMove = null;
        int originalAlpha = alpha;
        long hash = state.hash();

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            boolean isFirstMove = true;

            for (int i = 0; i < moves.size(); i++) {
                if (i % 3 == 0 && searchInterrupted) break;

                Move move = moves.get(i);

                // CRITICAL NULL-SAFE COPY
                GameState copy = state.copy();
                if (copy == null) {
                    System.err.println("❌ ERROR: state.copy() returned null for move " + move);
                    continue; // Skip this move
                }

                try {
                    copy.applyMove(move);
                    // VERIFY STATE IS STILL VALID
                    if (copy.redTowers == 0 && copy.blueTowers == 0 && copy.redGuard == 0 && copy.blueGuard == 0) {
                        System.err.println("❌ ERROR: applyMove() corrupted state for move " + move);
                        continue; // Skip this move
                    }
                } catch (Exception e) {
                    System.err.println("❌ ERROR: applyMove() failed for move " + move + ": " + e.getMessage());
                    continue; // Skip this move
                }

                statistics.addMovesSearched(1);

                int eval = performSingleMoveSafe(copy, move, depth, alpha, beta, isPVNode,
                        isFirstMove, i, state, searchFunc);

                if (isFirstMove) isFirstMove = false;

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    // FIXED: Use simplified history update
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth, state);
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

                // CRITICAL NULL-SAFE COPY
                GameState copy = state.copy();
                if (copy == null) {
                    System.err.println("❌ ERROR: state.copy() returned null for move " + move);
                    continue; // Skip this move
                }

                try {
                    copy.applyMove(move);
                    // VERIFY STATE IS STILL VALID
                    if (copy.redTowers == 0 && copy.blueTowers == 0 && copy.redGuard == 0 && copy.blueGuard == 0) {
                        System.err.println("❌ ERROR: applyMove() corrupted state for move " + move);
                        continue; // Skip this move
                    }
                } catch (Exception e) {
                    System.err.println("❌ ERROR: applyMove() failed for move " + move + ": " + e.getMessage());
                    continue; // Skip this move
                }

                statistics.addMovesSearched(1);

                int eval = performSingleMoveSafe(copy, move, depth, alpha, beta, isPVNode,
                        isFirstMove, i, state, searchFunc);

                if (isFirstMove) isFirstMove = false;

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();

                    // FIXED: Use simplified history update
                    if (!Minimax.isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth, state);
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
     * NULL-SAFE performSingleMove - CRITICAL FIX
     */
    private static int performSingleMoveSafe(GameState copy, Move move, int depth, int alpha, int beta,
                                             boolean isPVNode, boolean isFirstMove, int moveIndex,
                                             GameState originalState, SearchFunction searchFunc) {

        // CRITICAL NULL CHECK
        if (copy == null) {
            System.err.println("❌ ERROR: Null copy state in performSingleMoveSafe for move " + move);
            return originalState.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }

        if (originalState == null) {
            System.err.println("❌ ERROR: Null originalState in performSingleMoveSafe");
            return copy.redToMove ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        }

        boolean maximizingPlayer = !originalState.redToMove; // Kopie hat Spieler gewechselt
        boolean needsFullSearch = true;
        int eval = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        // === LATE MOVE REDUCTIONS ===
        if (shouldReduceMove(originalState, move, depth, moveIndex, isPVNode)) {
            int reduction = calculateLMRReduction(originalState, move, depth, moveIndex);
            int reducedDepth = Math.max(0, depth - 1 - reduction);

            try {
                // Reduzierte Suche mit null-window
                if (maximizingPlayer) {
                    eval = searchFunc.search(copy, reducedDepth, alpha, alpha + 1, true, false);
                } else {
                    eval = searchFunc.search(copy, reducedDepth, beta - 1, beta, false, false);
                }

                statistics.incrementLMRReductions();

                // Re-search falls reduzierte Suche zu gut war
                boolean needsReSearch = maximizingPlayer ?
                        (eval > alpha) : (eval < beta);

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
            } catch (Exception e) {
                System.err.println("❌ ERROR: LMR search failed for move " + move + ": " + e.getMessage());
                needsFullSearch = true; // Fall back to full search
            }
        }

        // === NORMALE SUCHE (falls nötig) ===
        if (needsFullSearch) {
            try {
                if (isFirstMove || isPVNode) {
                    eval = searchFunc.search(copy, depth - 1, alpha, beta, maximizingPlayer, isPVNode);
                } else {
                    // Null-window search
                    int nullWindow = maximizingPlayer ? alpha + 1 : beta - 1;
                    eval = searchFunc.search(copy, depth - 1,
                            maximizingPlayer ? alpha : nullWindow,
                            maximizingPlayer ? nullWindow : beta,
                            maximizingPlayer, false);

                    // Re-search with full window if needed
                    if (isPVNode && ((maximizingPlayer && eval > alpha) || (!maximizingPlayer && eval < beta))) {
                        eval = searchFunc.search(copy, depth - 1, alpha, beta, maximizingPlayer, true);
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ ERROR: Full search failed for move " + move + ": " + e.getMessage());
                // Return a conservative evaluation
                eval = Minimax.evaluate(copy, depth - 1);
            }
        }

        return eval;
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

    // === FIXED MOVE SCORING (NO HISTORYHEURISTIC DEPENDENCY) ===

    /**
     * FIXED: Simplified move scoring without HistoryHeuristic dependency
     */
    private static int scoreMoveForPVSimplified(GameState state, Move move, int depth) {
        // Use the unified scoreMove from MoveOrdering
        return moveOrdering.scoreMove(move, state, depth, null);
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

    /**
     * FIXED: Legacy compatibility method - uses simplified move ordering
     */
    public static void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry entry) {
        moveOrdering.orderMoves(moves, state, depth, entry);
    }
}
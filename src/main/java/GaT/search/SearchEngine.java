package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.evaluation.Evaluator;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * FIXED SEARCH ENGINE - Now properly routes PVS calls
 *
 * CRITICAL FIXES:
 * ✅ 1. PVS and PVS_Q now call actual PVS methods
 * ✅ 2. Proper strategy routing implemented
 * ✅ 3. Enhanced error handling
 * ✅ 4. Better timeout integration
 * ✅ 5. Statistics properly tracked
 */
public class SearchEngine {

    // === DEPENDENCIES ===
    private final Evaluator evaluator;
    private final MoveOrdering moveOrdering;
    private final TranspositionTable transpositionTable;
    private final SearchStatistics statistics;

    // === SEARCH CONSTANTS ===
    private static final int CASTLE_REACH_SCORE = 2500;

    // === TIMEOUT SUPPORT ===
    private BooleanSupplier timeoutChecker = null;

    public SearchEngine(Evaluator evaluator, MoveOrdering moveOrdering,
                        TranspositionTable transpositionTable, SearchStatistics statistics) {
        this.evaluator = evaluator;
        this.moveOrdering = moveOrdering;
        this.transpositionTable = transpositionTable;
        this.statistics = statistics;
    }

    // === MAIN SEARCH INTERFACE - FIXED ===

    /**
     * FIXED: Now properly routes to PVS methods
     */
    public int search(GameState state, int depth, int alpha, int beta,
                      boolean maximizingPlayer, SearchConfig.SearchStrategy strategy) {

        if (strategy == null) {
            System.err.println("⚠️ Null strategy provided, defaulting to ALPHA_BETA");
            strategy = SearchConfig.SearchStrategy.ALPHA_BETA;
        }

        try {
            return switch (strategy) {
                case ALPHA_BETA -> alphaBetaSearch(state, depth, alpha, beta, maximizingPlayer);
                case ALPHA_BETA_Q -> alphaBetaWithQuiescence(state, depth, alpha, beta, maximizingPlayer);

                // ✅ FIXED: Now actually calls PVS methods!
                case PVS -> {
                    PVSSearch.setTimeoutChecker(timeoutChecker);
                    yield PVSSearch.search(state, depth, alpha, beta, maximizingPlayer, true);
                }
                case PVS_Q -> {
                    PVSSearch.setTimeoutChecker(timeoutChecker);
                    yield PVSSearch.searchWithQuiescence(state, depth, alpha, beta, maximizingPlayer, true);
                }

                default -> {
                    System.err.println("⚠️ Unknown strategy: " + strategy + ", using ALPHA_BETA");
                    yield alphaBetaSearch(state, depth, alpha, beta, maximizingPlayer);
                }
            };
        } catch (Exception e) {
            System.err.println("❌ Search error with strategy " + strategy + ": " + e.getMessage());
            // Fallback to basic alpha-beta
            return alphaBetaSearch(state, depth, alpha, beta, maximizingPlayer);
        } finally {
            // Clean up PVS timeout checker
            if (strategy == SearchConfig.SearchStrategy.PVS || strategy == SearchConfig.SearchStrategy.PVS_Q) {
                PVSSearch.clearTimeoutChecker();
            }
        }
    }

    public int searchWithTimeout(GameState state, int depth, int alpha, int beta,
                                 boolean maximizingPlayer, SearchConfig.SearchStrategy strategy,
                                 BooleanSupplier timeoutCheck) {
        this.timeoutChecker = timeoutCheck;
        try {
            return search(state, depth, alpha, beta, maximizingPlayer, strategy);
        } finally {
            this.timeoutChecker = null;
        }
    }

    // === ENHANCED SEARCH INTERFACE ===

    /**
     * NEW: Direct PVS interface with proper PV node tracking
     */
    public int searchPVS(GameState state, int depth, int alpha, int beta,
                         boolean maximizingPlayer, boolean isPVNode, boolean useQuiescence) {
        PVSSearch.setTimeoutChecker(timeoutChecker);
        try {
            if (useQuiescence) {
                return PVSSearch.searchWithQuiescence(state, depth, alpha, beta, maximizingPlayer, isPVNode);
            } else {
                return PVSSearch.search(state, depth, alpha, beta, maximizingPlayer, isPVNode);
            }
        } finally {
            PVSSearch.clearTimeoutChecker();
        }
    }

    /**
     * NEW: Direct Quiescence search interface
     */
    public int searchQuiescence(GameState state, int alpha, int beta, boolean maximizingPlayer, int qDepth) {
        return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, qDepth);
    }

    // === ALPHA-BETA SEARCH ALGORITHMS ===

    /**
     * Enhanced Alpha-Beta with better pruning
     */
    public int alphaBetaSearch(GameState state, int depth, int alpha, int beta,
                               boolean maximizingPlayer) {
        return alphaBetaInternal(state, depth, alpha, beta, maximizingPlayer, false);
    }

    /**
     * Internal alpha-beta with enhanced features
     */
    private int alphaBetaInternal(GameState state, int depth, int alpha, int beta,
                                  boolean maximizingPlayer, boolean nullMoveUsed) {
        statistics.incrementNodeCount();

        // === TIMEOUT CHECK ===
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        // === TRANSPOSITION TABLE PROBE ===
        long hash = state.hash();
        TTEntry entry = transpositionTable.get(hash);
        if (entry != null && entry.depth >= depth) {
            statistics.incrementTTHits();
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        } else if (entry == null) {
            statistics.incrementTTMisses();
        }

        // === TERMINAL CONDITIONS ===
        if (depth == 0 || isGameOver(state)) {
            statistics.incrementLeafNodeCount();
            return evaluator.evaluate(state, depth);
        }

        // === PRUNING CHECKS ===
        boolean inCheck = isInCheckSimple(state);

        // Reverse Futility Pruning
        if (canApplyReverseFutilityPruning(state, depth, beta, maximizingPlayer, inCheck)) {
            statistics.incrementReverseFutilityCutoffs();
            return evaluator.evaluate(state, depth);
        }

        // Null Move Pruning
        if (canApplyNullMovePruning(state, depth, beta, maximizingPlayer, nullMoveUsed, inCheck)) {
            GameState nullState = state.copy();
            nullState.redToMove = !nullState.redToMove;

            int reduction = calculateNullMoveReduction(depth);
            int nullScore = -alphaBetaInternal(nullState, depth - 1 - reduction, -beta, -beta + 1,
                    !maximizingPlayer, true);

            if (nullScore >= beta) {
                statistics.incrementNullMoveCutoffs();
                return nullScore;
            }
        }

        // === EXTENSIONS ===
        int extension = 0;
        if (inCheck && depth < SearchConfig.MAX_EXTENSION_DEPTH) {
            extension = SearchConfig.CHECK_EXTENSION_DEPTH;
            statistics.incrementCheckExtensions();
        }

        // === MOVE GENERATION AND SEARCH ===
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        statistics.addMovesGenerated(moves.size());
        statistics.addBranchingFactor(moves.size());

        moveOrdering.orderMoves(moves, state, depth, entry);

        Move bestMove = null;
        int originalAlpha = alpha;
        int moveCount = 0;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                moveCount++;
                statistics.addMovesSearched(1);

                boolean isCapture = isCapture(move, state);
                boolean givesCheck = isInCheckSimple(copy);

                // Futility Pruning
                if (canApplyFutilityPruning(state, depth, alpha, move, isCapture, givesCheck, inCheck)) {
                    statistics.incrementFutilityCutoffs();
                    continue;
                }

                int eval;
                int newDepth = depth - 1 + extension;

                // Late Move Reductions
                if (shouldApplyLMR(moveCount, newDepth, isCapture, givesCheck, inCheck)) {
                    int reduction = calculateLMRReduction(moveCount, newDepth, isCapture);
                    eval = -alphaBetaInternal(copy, newDepth - reduction, -beta, -alpha, false, false);

                    // Re-search if promising
                    if (eval > alpha) {
                        eval = -alphaBetaInternal(copy, newDepth, -beta, -alpha, false, false);
                    }
                    statistics.incrementLMRReductions();
                } else {
                    eval = -alphaBetaInternal(copy, newDepth, -beta, -alpha, false, false);
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                    moveOrdering.storePVMove(move, depth);
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    if (!isCapture) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth);
                    }
                    break;
                }
            }

            // Checkmate/Stalemate detection
            if (moves.isEmpty()) {
                return inCheck ? (-CASTLE_REACH_SCORE - depth) : 0;
            }

            storeTranspositionEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;

        } else {
            // Minimizing player (similar structure)
            int minEval = Integer.MAX_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                moveCount++;
                statistics.addMovesSearched(1);

                boolean isCapture = isCapture(move, state);
                boolean givesCheck = isInCheckSimple(copy);

                if (canApplyFutilityPruning(state, depth, beta, move, isCapture, givesCheck, inCheck)) {
                    statistics.incrementFutilityCutoffs();
                    continue;
                }

                int eval;
                int newDepth = depth - 1 + extension;

                if (shouldApplyLMR(moveCount, newDepth, isCapture, givesCheck, inCheck)) {
                    int reduction = calculateLMRReduction(moveCount, newDepth, isCapture);
                    eval = -alphaBetaInternal(copy, newDepth - reduction, -beta, -alpha, true, false);

                    if (eval < beta) {
                        eval = -alphaBetaInternal(copy, newDepth, -beta, -alpha, true, false);
                    }
                    statistics.incrementLMRReductions();
                } else {
                    eval = -alphaBetaInternal(copy, newDepth, -beta, -alpha, true, false);
                }

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                    moveOrdering.storePVMove(move, depth);
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    if (!isCapture) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth);
                    }
                    break;
                }
            }

            if (moves.isEmpty()) {
                return inCheck ? (CASTLE_REACH_SCORE + depth) : 0;
            }

            storeTranspositionEntry(hash, minEval, depth, originalAlpha, beta, bestMove);
            return minEval;
        }
    }

    /**
     * Alpha-Beta with Quiescence Search
     */
    public int alphaBetaWithQuiescence(GameState state, int depth, int alpha, int beta,
                                       boolean maximizingPlayer) {
        return alphaBetaWithQuiescenceInternal(state, depth, alpha, beta, maximizingPlayer, false);
    }

    private int alphaBetaWithQuiescenceInternal(GameState state, int depth, int alpha, int beta,
                                                boolean maximizingPlayer, boolean nullMoveUsed) {
        statistics.incrementNodeCount();

        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Search timeout");
        }

        // TT probe
        long hash = state.hash();
        TTEntry entry = transpositionTable.get(hash);
        if (entry != null && entry.depth >= depth) {
            statistics.incrementTTHits();
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        }

        if (isGameOver(state)) {
            statistics.incrementLeafNodeCount();
            return evaluator.evaluate(state, depth);
        }

        // ✅ FIXED: Proper Quiescence integration at leaf nodes
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        boolean inCheck = isInCheckSimple(state);

        // Pruning (similar to alpha-beta)
        if (canApplyReverseFutilityPruning(state, depth, beta, maximizingPlayer, inCheck)) {
            statistics.incrementReverseFutilityCutoffs();
            return evaluator.evaluate(state, depth);
        }

        if (canApplyNullMovePruning(state, depth, beta, maximizingPlayer, nullMoveUsed, inCheck)) {
            GameState nullState = state.copy();
            nullState.redToMove = !nullState.redToMove;

            int reduction = calculateNullMoveReduction(depth);
            int nullScore = -alphaBetaWithQuiescenceInternal(nullState, depth - 1 - reduction,
                    -beta, -beta + 1, !maximizingPlayer, true);

            if (nullScore >= beta) {
                statistics.incrementNullMoveCutoffs();
                return nullScore;
            }
        }

        // Extensions
        int extension = 0;
        if (inCheck && depth < SearchConfig.MAX_EXTENSION_DEPTH) {
            extension = SearchConfig.CHECK_EXTENSION_DEPTH;
            statistics.incrementCheckExtensions();
        }

        // Regular search with quiescence at leaves
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        moveOrdering.orderMoves(moves, state, depth, entry);

        Move bestMove = null;
        int originalAlpha = alpha;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = -alphaBetaWithQuiescenceInternal(copy, depth - 1 + extension,
                        -beta, -alpha, false, false);

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                    moveOrdering.storePVMove(move, depth);
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    if (!isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth);
                    }
                    break;
                }
            }

            storeTranspositionEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = -alphaBetaWithQuiescenceInternal(copy, depth - 1 + extension,
                        -beta, -alpha, true, false);

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                    moveOrdering.storePVMove(move, depth);
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    statistics.incrementAlphaBetaCutoffs();
                    if (!isCapture(move, state)) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth);
                    }
                    break;
                }
            }

            storeTranspositionEntry(hash, minEval, depth, originalAlpha, beta, bestMove);
            return minEval;
        }
    }

    // === ENHANCED PRUNING METHODS ===

    private boolean shouldApplyLMR(int moveCount, int depth, boolean isCapture, boolean givesCheck, boolean inCheck) {
        return moveCount > SearchConfig.LMR_MIN_MOVE_COUNT &&
                depth > SearchConfig.LMR_MIN_DEPTH &&
                !isCapture && !givesCheck && !inCheck;
    }

    private boolean canApplyReverseFutilityPruning(GameState state, int depth, int beta,
                                                   boolean maximizingPlayer, boolean inCheck) {
        if (depth > SearchConfig.FUTILITY_MAX_DEPTH) return false;
        if (inCheck) return false;
        if (isEndgame(state)) return false;

        int eval = evaluator.evaluate(state, depth);
        int margin = SearchConfig.REVERSE_FUTILITY_MARGINS[Math.min(depth, SearchConfig.FUTILITY_MAX_DEPTH)];

        if (maximizingPlayer) {
            return eval >= beta + margin;
        } else {
            return eval <= beta - margin;
        }
    }

    private boolean canApplyNullMovePruning(GameState state, int depth, int beta,
                                            boolean maximizingPlayer, boolean nullMoveUsed, boolean inCheck) {
        if (depth < SearchConfig.NULL_MOVE_MIN_DEPTH) return false;
        if (nullMoveUsed) return false;
        if (inCheck) return false;
        if (isEndgame(state)) return false;
        if (hasOnlyLowValuePieces(state, state.redToMove)) return false;

        int eval = evaluator.evaluate(state, depth);
        return maximizingPlayer ? eval >= beta : eval <= beta;
    }

    private boolean canApplyFutilityPruning(GameState state, int depth, int bound, Move move,
                                            boolean isCapture, boolean givesCheck, boolean inCheck) {
        if (depth > SearchConfig.FUTILITY_MAX_DEPTH) return false;
        if (isCapture) return false;
        if (givesCheck) return false;
        if (inCheck) return false;
        if (isWinningMove(move, state)) return false;

        int eval = evaluator.evaluate(state, depth);
        int margin = SearchConfig.FUTILITY_MARGINS[Math.min(depth, SearchConfig.FUTILITY_MAX_DEPTH)];

        return eval + margin < bound;
    }

    // === HELPER METHODS ===

    private int calculateNullMoveReduction(int depth) {
        if (depth >= 6) return 3;
        if (depth >= 4) return 2;
        return 1;
    }

    private int calculateLMRReduction(int moveCount, int depth, boolean isCapture) {
        if (isCapture) return 0;
        if (depth < SearchConfig.LMR_MIN_DEPTH) return 0;
        if (moveCount < SearchConfig.LMR_MIN_MOVE_COUNT) return 0;

        if (moveCount > 12) return 3;
        if (moveCount > 8) return 2;
        return 1;
    }

    private boolean isGameOver(GameState state) {
        if (state.redGuard == 0 || state.blueGuard == 0) {
            return true;
        }

        long redCastlePos = GameState.bit(GameState.getIndex(0, 3));
        long blueCastlePos = GameState.bit(GameState.getIndex(6, 3));

        boolean redGuardOnD1 = (state.redGuard & redCastlePos) != 0;
        boolean blueGuardOnD7 = (state.blueGuard & blueCastlePos) != 0;

        return redGuardOnD1 || blueGuardOnD7;
    }

    private boolean isInCheckSimple(GameState state) {
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;

        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        long enemyPieces = isRed ? (state.blueTowers | state.blueGuard) : (state.redTowers | state.redGuard);

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((enemyPieces & GameState.bit(i)) != 0) {
                if (canPieceAttackSquare(state, i, guardPos, !isRed)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean canPieceAttackSquare(GameState state, int fromSquare, int toSquare, boolean isPieceRed) {
        boolean isGuard = isPieceRed ?
                (state.redGuard & GameState.bit(fromSquare)) != 0 :
                (state.blueGuard & GameState.bit(fromSquare)) != 0;

        int range = isGuard ? 1 :
                (isPieceRed ? state.redStackHeights[fromSquare] : state.blueStackHeights[fromSquare]);

        if (range <= 0) return false;

        int rankDiff = Math.abs(GameState.rank(fromSquare) - GameState.rank(toSquare));
        int fileDiff = Math.abs(GameState.file(fromSquare) - GameState.file(toSquare));

        if (rankDiff != 0 && fileDiff != 0) return false;

        int distance = Math.max(rankDiff, fileDiff);
        if (distance > range) return false;

        return isPathClearForAttack(state, fromSquare, toSquare);
    }

    private boolean isPathClearForAttack(GameState state, int from, int to) {
        int rankDiff = GameState.rank(to) - GameState.rank(from);
        int fileDiff = GameState.file(to) - GameState.file(from);

        if (rankDiff != 0 && fileDiff != 0) return false;

        int step = rankDiff != 0 ? (rankDiff > 0 ? 7 : -7) : (fileDiff > 0 ? 1 : -1);
        int current = from + step;

        while (current != to) {
            if (isOccupied(current, state)) return false;
            current += step;
        }

        return true;
    }

    private boolean isOccupied(int square, GameState state) {
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard)
                & GameState.bit(square)) != 0;
    }

    private boolean isEndgame(GameState state) {
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return totalMaterial <= SearchConfig.ENDGAME_MATERIAL_THRESHOLD;
    }

    private boolean hasOnlyLowValuePieces(GameState state, boolean isRed) {
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
        }
        return totalMaterial <= 2;
    }

    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private boolean isWinningMove(Move move, GameState state) {
        boolean isRed = state.redToMove;

        if (move.amountMoved == 1) {
            long fromBit = GameState.bit(move.from);
            boolean isGuardMove = (isRed && (state.redGuard & fromBit) != 0) ||
                    (!isRed && (state.blueGuard & fromBit) != 0);

            if (isGuardMove) {
                int targetCastle = isRed ?
                        GameState.getIndex(0, 3) :
                        GameState.getIndex(6, 3);
                return move.to == targetCastle;
            }
        }

        long toBit = GameState.bit(move.to);
        return ((isRed ? state.blueGuard : state.redGuard) & toBit) != 0;
    }

    private void storeTranspositionEntry(long hash, int score, int depth, int originalAlpha, int beta, Move bestMove) {
        int flag;
        if (score <= originalAlpha) {
            flag = TTEntry.UPPER_BOUND;
        } else if (score >= beta) {
            flag = TTEntry.LOWER_BOUND;
        } else {
            flag = TTEntry.EXACT;
        }

        TTEntry entry = new TTEntry(score, depth, flag, bestMove);
        transpositionTable.put(hash, entry);
        statistics.incrementTTStores();
    }

    // === SEARCH CONFIGURATION ===

    public void setTimeoutChecker(BooleanSupplier checker) {
        this.timeoutChecker = checker;
    }

    public void clearTimeoutChecker() {
        this.timeoutChecker = null;
    }

    public void resetStatistics() {
        statistics.reset();
    }

    public SearchStatistics getStatistics() {
        return statistics;
    }
}
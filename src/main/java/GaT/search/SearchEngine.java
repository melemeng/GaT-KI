package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.evaluation.Evaluator;

import java.util.List;
import java.util.function.BooleanSupplier;


import GaT.search.MoveGenerator;
import GaT.search.PVSSearch;
import GaT.search.QuiescenceSearch;


/**
 * CORE SEARCH ENGINE - Extracted from Minimax
 * Handles all search algorithms, pruning, and extensions
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

    // === MAIN SEARCH INTERFACE ===

    /**
     * Main search dispatcher - routes to appropriate algorithm
     */
    // Add this fix to the search method in SearchEngine.java:

    public int search(GameState state, int depth, int alpha, int beta,
                      boolean maximizingPlayer, SearchConfig.SearchStrategy strategy) {

        // Handle null strategy gracefully
        if (strategy == null) {
            System.err.println("⚠️ Null strategy provided, defaulting to ALPHA_BETA");
            strategy = SearchConfig.SearchStrategy.ALPHA_BETA;
        }

        try {
            return switch (strategy) {
                case ALPHA_BETA -> alphaBetaSearch(state, depth, alpha, beta, maximizingPlayer);
                case ALPHA_BETA_Q -> alphaBetaWithQuiescence(state, depth, alpha, beta, maximizingPlayer);
                case PVS -> pvsSearch(state, depth, alpha, beta, maximizingPlayer, true);
                case PVS_Q -> pvsWithQuiescence(state, depth, alpha, beta, maximizingPlayer, true);
                default -> {
                    System.err.println("⚠️ Unknown strategy: " + strategy + ", using ALPHA_BETA");
                    yield alphaBetaSearch(state, depth, alpha, beta, maximizingPlayer);
                }
            };
        } catch (Exception e) {
            System.err.println("❌ Search error with strategy " + strategy + ": " + e.getMessage());
            // Fallback to basic alpha-beta
            return alphaBetaSearch(state, depth, alpha, beta, maximizingPlayer);
        }
    }

    // Also update searchWithTimeout method signature:
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



    // === ALPHA-BETA SEARCH ALGORITHMS ===

    /**
     * ENHANCED ALPHA-BETA SEARCH - With all pruning techniques
     * (Extracted from Minimax.minimaxEnhanced)
     */
    public int alphaBetaSearch(GameState state, int depth, int alpha, int beta,
                               boolean maximizingPlayer) {
        return alphaBetaInternal(state, depth, alpha, beta, maximizingPlayer, false);
    }

    /**
     * Internal alpha-beta with null move support
     */
    private int alphaBetaInternal(GameState state, int depth, int alpha, int beta,
                                  boolean maximizingPlayer, boolean nullMoveUsed) {
        statistics.incrementNodeCount();

        // === TIMEOUT CHECK ===
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Timeout");
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

        boolean inCheck = isInCheck(state);

        // === REVERSE FUTILITY PRUNING ===
        if (canApplyReverseFutilityPruning(state, depth, beta, maximizingPlayer, inCheck)) {
            statistics.incrementReverseFutilityCutoffs();
            return evaluator.evaluate(state, depth);
        }

        // === NULL MOVE PRUNING ===
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

        // === CHECK EXTENSIONS ===
        int extension = 0;
        if (inCheck && depth < SearchConfig.MAX_EXTENSION_DEPTH) {
            extension = SearchConfig.CHECK_EXTENSION_DEPTH;
            statistics.incrementCheckExtensions();
        }

        // === MOVE GENERATION AND ORDERING ===
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        statistics.addMovesGenerated(moves.size());
        statistics.addBranchingFactor(moves.size());

        moveOrdering.orderMoves(moves, state, depth, entry);

        Move bestMove = null;
        int originalAlpha = alpha;
        int moveCount = 0;
        boolean foundLegalMove = false;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                moveCount++;
                foundLegalMove = true;
                statistics.addMovesSearched(1);

                boolean isCapture = isCapture(move, state);
                boolean givesCheck = isInCheck(copy);

                // === FUTILITY PRUNING ===
                if (canApplyFutilityPruning(state, depth, alpha, move, isCapture, givesCheck, inCheck)) {
                    statistics.incrementFutilityCutoffs();
                    continue;
                }

                int eval;
                int newDepth = depth - 1 + extension;

                // === LATE MOVE REDUCTIONS ===
                if (moveCount > SearchConfig.LMR_MIN_MOVE_COUNT && newDepth > SearchConfig.LMR_MIN_DEPTH &&
                        !isCapture && !givesCheck && !inCheck) {
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
                    // Store killer move if not capture
                    if (!isCapture) {
                        moveOrdering.storeKillerMove(move, depth);
                        moveOrdering.updateHistory(move, depth);
                    }
                    break;
                }
            }

            // === CHECKMATE/STALEMATE DETECTION ===
            if (!foundLegalMove) {
                return inCheck ? (-CASTLE_REACH_SCORE - depth) : 0;
            }

            // === STORE IN TRANSPOSITION TABLE ===
            int flag = maxEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    maxEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            storeTranspositionEntry(hash, maxEval, depth, flag, bestMove);

            return maxEval;

        } else {
            // === MINIMIZING PLAYER ===
            int minEval = Integer.MAX_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                moveCount++;
                foundLegalMove = true;
                statistics.addMovesSearched(1);

                boolean isCapture = isCapture(move, state);
                boolean givesCheck = isInCheck(copy);

                // === FUTILITY PRUNING ===
                if (canApplyFutilityPruning(state, depth, beta, move, isCapture, givesCheck, inCheck)) {
                    statistics.incrementFutilityCutoffs();
                    continue;
                }

                int eval;
                int newDepth = depth - 1 + extension;

                // === LATE MOVE REDUCTIONS ===
                if (moveCount > SearchConfig.LMR_MIN_MOVE_COUNT && newDepth > SearchConfig.LMR_MIN_DEPTH &&
                        !isCapture && !givesCheck && !inCheck) {
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

            if (!foundLegalMove) {
                return inCheck ? (CASTLE_REACH_SCORE + depth) : 0;
            }

            int flag = minEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    minEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            storeTranspositionEntry(hash, minEval, depth, flag, bestMove);

            return minEval;
        }
    }

    /**
     * ALPHA-BETA WITH QUIESCENCE SEARCH
     */
    public int alphaBetaWithQuiescence(GameState state, int depth, int alpha, int beta,
                                       boolean maximizingPlayer) {
        return alphaBetaWithQuiescenceInternal(state, depth, alpha, beta, maximizingPlayer, false);
    }

    private int alphaBetaWithQuiescenceInternal(GameState state, int depth, int alpha, int beta,
                                                boolean maximizingPlayer, boolean nullMoveUsed) {
        statistics.incrementNodeCount();

        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Timeout");
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

        boolean inCheck = isInCheck(state);

        // Pruning (same as alpha-beta)
        if (depth > 0 && canApplyReverseFutilityPruning(state, depth, beta, maximizingPlayer, inCheck)) {
            statistics.incrementReverseFutilityCutoffs();
            return evaluator.evaluate(state, depth);
        }

        if (depth > 0 && canApplyNullMovePruning(state, depth, beta, maximizingPlayer, nullMoveUsed, inCheck)) {
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

        // === QUIESCENCE SEARCH AT LEAF NODES ===
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
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

            int flag = maxEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    maxEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            storeTranspositionEntry(hash, maxEval, depth, flag, bestMove);

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

            int flag = minEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    minEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            storeTranspositionEntry(hash, minEval, depth, flag, bestMove);

            return minEval;
        }
    }

    // === PRINCIPAL VARIATION SEARCH ===

    /**
     * PRINCIPAL VARIATION SEARCH (PVS)
     */
    public int pvsSearch(GameState state, int depth, int alpha, int beta,
                         boolean maximizingPlayer, boolean isPVNode) {
        // Delegate to existing PVSSearch class
        return PVSSearch.search(state, depth, alpha, beta, maximizingPlayer, isPVNode);
    }

    /**
     * PVS WITH QUIESCENCE
     */
    public int pvsWithQuiescence(GameState state, int depth, int alpha, int beta,
                                 boolean maximizingPlayer, boolean isPVNode) {
        // Delegate to existing PVSSearch class
        return PVSSearch.searchWithQuiescence(state, depth, alpha, beta, maximizingPlayer, isPVNode);
    }

    // === PRUNING HELPER METHODS ===

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

    // === GAME STATE ANALYSIS ===

    private boolean isGameOver(GameState state) {
        // Guard captured
        if (state.redGuard == 0 || state.blueGuard == 0) {
            return true;
        }

        // Guard reached enemy castle
        long redCastlePos = GameState.bit(GameState.getIndex(0, 3)); // D1
        long blueCastlePos = GameState.bit(GameState.getIndex(6, 3)); // D7

        boolean redGuardOnD1 = (state.redGuard & redCastlePos) != 0;
        boolean blueGuardOnD7 = (state.blueGuard & blueCastlePos) != 0;

        return redGuardOnD1 || blueGuardOnD7;
    }

    private boolean isInCheck(GameState state) {
        if (state.redToMove) {
            return state.redGuard != 0 && evaluator.getSafetyEvaluator().isGuardInDanger(state, true);
        } else {
            return state.blueGuard != 0 && evaluator.getSafetyEvaluator().isGuardInDanger(state, false);
        }
    }

    private boolean isEndgame(GameState state) {
        return evaluator.getMaterialEvaluator().getTotalMaterial(state) <= SearchConfig.ENDGAME_MATERIAL_THRESHOLD;
    }

    private boolean hasOnlyLowValuePieces(GameState state, boolean isRed) {
        return evaluator.getMaterialEvaluator().getTotalMaterial(state, isRed) <= 2;
    }

    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private boolean isWinningMove(Move move, GameState state) {
        boolean isRed = state.redToMove;

        // Guard reaches enemy castle
        if (move.amountMoved == 1) {
            long fromBit = GameState.bit(move.from);
            boolean isGuardMove = (isRed && (state.redGuard & fromBit) != 0) ||
                    (!isRed && (state.blueGuard & fromBit) != 0);

            if (isGuardMove) {
                int targetCastle = isRed ?
                        GameState.getIndex(0, 3) : // D1 for red
                        GameState.getIndex(6, 3);  // D7 for blue
                return move.to == targetCastle;
            }
        }

        // Captures enemy guard
        long toBit = GameState.bit(move.to);
        return ((isRed ? state.blueGuard : state.redGuard) & toBit) != 0;
    }

    // === TRANSPOSITION TABLE HELPERS ===

    private void storeTranspositionEntry(long hash, int score, int depth, int flag, Move bestMove) {
        TTEntry entry = new TTEntry(score, depth, flag, bestMove);
        transpositionTable.put(hash, entry);
        statistics.incrementTTStores();
    }

    // === SEARCH CONFIGURATION ===

    /**
     * Set timeout checker for search termination
     */
    public void setTimeoutChecker(BooleanSupplier checker) {
        this.timeoutChecker = checker;
    }

    /**
     * Clear timeout checker
     */
    public void clearTimeoutChecker() {
        this.timeoutChecker = null;
    }

    /**
     * Reset search statistics
     */
    public void resetStatistics() {
        statistics.reset();
    }

    /**
     * Get search statistics
     */
    public SearchStatistics getStatistics() {
        return statistics;
    }
}
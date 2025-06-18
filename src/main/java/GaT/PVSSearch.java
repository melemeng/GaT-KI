package GaT;

import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.Objects.TTEntry;

import java.util.List;
import java.util.function.BooleanSupplier;


public class PVSSearch {

    private static BooleanSupplier timeoutChecker = null;


    public static int search(GameState state, int depth, int alpha, int beta,
                             boolean maximizingPlayer, boolean isPVNode) {

        // Timeout check
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Timeout");
        }

        // Check transposition table
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);
        if (entry != null && entry.depth >= depth && !isPVNode) {
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        }

        // Terminal conditions
        if (depth == 0 || Minimax.isGameOver(state)) {
            return Minimax.evaluate(state, depth);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        Minimax.orderMovesAdvanced(moves, state, depth, entry);

        Move bestMove = null;
        int originalAlpha = alpha;
        boolean isFirstMove = true;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                Minimax.counter++;

                int eval;

                if (isFirstMove) {
                    // First move: search with full window (this is the Principal Variation)
                    eval = search(copy, depth - 1, alpha, beta, false, true);
                    isFirstMove = false;
                } else {
                    // Other moves: try null window first
                    eval = search(copy, depth - 1, alpha, alpha + 1, false, false);

                    // If null window fails high, re-search with full window
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
                    // Beta cutoff - store killer move
                    if (!isCapture(move, state)) {
                        Minimax.storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            // Store in TT
            storeTTEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                Minimax.counter++;

                int eval;

                if (isFirstMove) {
                    // First move: search with full window
                    eval = search(copy, depth - 1, alpha, beta, true, true);
                    isFirstMove = false;
                } else {
                    // Other moves: try null window first
                    eval = search(copy, depth - 1, beta - 1, beta, true, false);

                    // If null window fails low, re-search with full window
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
                    // Alpha cutoff - store killer move
                    if (!isCapture(move, state)) {
                        Minimax.storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            // Store in TT
            storeTTEntry(hash, minEval, depth, originalAlpha, beta, bestMove);
            return minEval;
        }
    }

    /**
     * PVS with YOUR existing QuiescenceSearch
     */
    public static int searchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                           boolean maximizingPlayer, boolean isPVNode) {

        // Timeout check
        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Timeout");
        }

        // Check transposition table
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);
        if (entry != null && entry.depth >= depth && !isPVNode) {
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        }

        // Terminal conditions
        if (Minimax.isGameOver(state)) {
            return Minimax.evaluate(state, depth);
        }

        // LEVERAGE YOUR EXISTING QuiescenceSearch when depth runs out
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        Minimax.orderMovesAdvanced(moves, state, depth, entry);

        Move bestMove = null;
        int originalAlpha = alpha;
        boolean isFirstMove = true;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                Minimax.counter++;

                int eval;

                if (isFirstMove) {
                    // First move: PVS with full window + YOUR QuiescenceSearch
                    eval = searchWithQuiescence(copy, depth - 1, alpha, beta, false, true);
                    isFirstMove = false;
                } else {
                    // Other moves: PVS null window + YOUR QuiescenceSearch
                    eval = searchWithQuiescence(copy, depth - 1, alpha, alpha + 1, false, false);
                    if (eval > alpha && eval < beta) {
                        // Re-search with full window + YOUR QuiescenceSearch
                        eval = searchWithQuiescence(copy, depth - 1, eval, beta, false, true);
                    }
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        Minimax.storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            storeTTEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                Minimax.counter++;

                int eval;

                if (isFirstMove) {
                    // First move: PVS with full window + YOUR QuiescenceSearch
                    eval = searchWithQuiescence(copy, depth - 1, alpha, beta, true, true);
                    isFirstMove = false;
                } else {
                    // Other moves: PVS null window + YOUR QuiescenceSearch
                    eval = searchWithQuiescence(copy, depth - 1, beta - 1, beta, true, false);
                    if (eval < beta && eval > alpha) {
                        // Re-search with full window + YOUR QuiescenceSearch
                        eval = searchWithQuiescence(copy, depth - 1, alpha, eval, true, true);
                    }
                }

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        Minimax.storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            storeTTEntry(hash, minEval, depth, originalAlpha, beta, bestMove);
            return minEval;
        }
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
    }

    /**
     * Check if move is a capture
     */
    private static boolean isCapture(Move move, GameState state) {
        if (state == null) return false;

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

    /**
     * Set timeout checker from TimedMinimax
     */
    public static void setTimeoutChecker(BooleanSupplier checker) {
        timeoutChecker = checker;
    }
}
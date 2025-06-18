// Clean Minimax.java - REMOVE everything after line 700 to fix duplicates!

package GaT;

import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.Objects.TTEntry;

import java.util.HashMap;
import java.util.List;
import java.util.function.BooleanSupplier;

import static GaT.Objects.GameState.getIndex;

public class Minimax {
    public static final int RED_CASTLE_INDEX = getIndex(6, 3); // D7
    public static final int BLUE_CASTLE_INDEX = getIndex(0, 3); // D1
    public static int counter = 0;

    private static final HashMap<Long, TTEntry> transpositionTable = new HashMap<>();

    final static int[] centralSquares = {
            GameState.getIndex(2, 3), // D3
            GameState.getIndex(3, 3), // D4
            GameState.getIndex(4, 3)  // D5
    };

    // === KILLER MOVES & PRINCIPAL VARIATION ===
    private static Move[][] killerMoves = new Move[20][2]; // [depth][primary/secondary]
    private static int killerAge = 0;
    private static Move[] pvLine = new Move[20];

    /**
     * LEGACY COMPATIBILITY METHODS - Keep your existing interface
     */

    /**
     * Original findBestMove - uses Alpha-Beta
     */
    public static Move findBestMove(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.ALPHA_BETA);
    }

    /**
     * Quiescence version
     */
    public static Move findBestMoveWithQuiescence(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.ALPHA_BETA_Q);
    }

    /**
     * NEW: PVS version
     */
    public static Move findBestMoveWithPVS(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.PVS);
    }

    /**
     * NEW: Ultimate version - PVS + Quiescence
     */
    public static Move findBestMoveUltimate(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.PVS_Q);
    }

    // === SEARCH STRATEGY CONFIGURATION ===
    public enum SearchStrategy {
        ALPHA_BETA,           // Classic Alpha-Beta
        ALPHA_BETA_Q,         // Alpha-Beta + Quiescence
        PVS,                  // Principal Variation Search
        PVS_Q                 // PVS + Quiescence (Ultimate)
    }

    /**
     * UNIFIED SEARCH INTERFACE - Clean strategy selection
     */
    public static Move findBestMoveWithStrategy(GameState state, int depth, SearchStrategy strategy) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        orderMovesAdvanced(moves, state, depth, getTranspositionEntry(state.hash()));

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        System.out.println("=== Starting " + strategy + " Search (Depth " + depth + ") ===");

        counter = 0;
        if (strategy == SearchStrategy.ALPHA_BETA_Q || strategy == SearchStrategy.PVS_Q) {
            QuiescenceSearch.resetQuiescenceStats();
        }

        for (Move move : moves) {
            GameState copy = state.copy();
            copy.applyMove(move);
            counter++;

            int score = searchWithStrategy(copy, depth - 1, Integer.MIN_VALUE,
                    Integer.MAX_VALUE, !isRed, strategy, true);

            System.out.println(move + " -> Score: " + score);

            if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                bestScore = score;
                bestMove = move;
            }
        }

        System.out.println("Search nodes: " + counter);
        if (strategy == SearchStrategy.ALPHA_BETA_Q || strategy == SearchStrategy.PVS_Q) {
            if (QuiescenceSearch.qNodes > 0) {
                System.out.println("Q-nodes: " + QuiescenceSearch.qNodes);
                System.out.println("Stand-pat rate: " + (100.0 * QuiescenceSearch.standPatCutoffs / QuiescenceSearch.qNodes) + "%");
            }
        }
        System.out.println("Best move: " + bestMove + " (Score: " + bestScore + ")");

        return bestMove;
    }

    /**
     * STRATEGY DISPATCHER - Routes to appropriate search method
     */
    private static int searchWithStrategy(GameState state, int depth, int alpha, int beta,
                                          boolean maximizingPlayer, SearchStrategy strategy, boolean isPVNode) {
        switch (strategy) {
            case ALPHA_BETA:
                return minimax(state, depth, alpha, beta, maximizingPlayer);

            case ALPHA_BETA_Q:
                return minimaxWithQuiescence(state, depth, alpha, beta, maximizingPlayer);

            case PVS:
                return PVSSearch.search(state, depth, alpha, beta, maximizingPlayer, isPVNode);

            case PVS_Q:
                return PVSSearch.searchWithQuiescence(state, depth, alpha, beta, maximizingPlayer, isPVNode);

            default:
                throw new IllegalArgumentException("Unknown search strategy: " + strategy);
        }
    }

    // === CORE SEARCH ALGORITHMS ===

    /**
     * Classic Alpha-Beta Minimax
     */
    private static int minimax(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        // Check transposition table
        long hash = state.hash();
        TTEntry entry = transpositionTable.get(hash);
        if (entry != null && entry.depth >= depth) {
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        }

        if (depth == 0 || isGameOver(state)) {
            return evaluate(state, depth);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        orderMovesAdvanced(moves, state, depth, entry);

        Move bestMove = null;
        int originalAlpha = alpha;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;
                int eval = minimax(copy, depth - 1, alpha, beta, false);

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                    storePVMove(move, depth);
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            int flag = maxEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    maxEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            transpositionTable.put(hash, new TTEntry(maxEval, depth, flag, bestMove));

            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;
                int eval = minimax(copy, depth - 1, alpha, beta, true);

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                    storePVMove(move, depth);
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            int flag = minEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    minEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            transpositionTable.put(hash, new TTEntry(minEval, depth, flag, bestMove));

            return minEval;
        }
    }

    /**
     * Alpha-Beta with Quiescence Search integration
     */
    private static int minimaxWithQuiescence(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        // Check transposition table
        long hash = state.hash();
        TTEntry entry = transpositionTable.get(hash);
        if (entry != null && entry.depth >= depth) {
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        }

        // Terminal conditions
        if (isGameOver(state)) {
            return evaluate(state, depth);
        }

        // QUIESCENCE INTEGRATION: Use quiescence search when depth <= 0
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        orderMovesAdvanced(moves, state, depth, entry);

        Move bestMove = null;
        int originalAlpha = alpha;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;

                int eval = minimaxWithQuiescence(copy, depth - 1, alpha, beta, false);

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                    storePVMove(move, depth);
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            int flag = maxEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    maxEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            transpositionTable.put(hash, new TTEntry(maxEval, depth, flag, bestMove));

            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;

                int eval = minimaxWithQuiescence(copy, depth - 1, alpha, beta, true);

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                    storePVMove(move, depth);
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            int flag = minEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    minEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            transpositionTable.put(hash, new TTEntry(minEval, depth, flag, bestMove));

            return minEval;
        }
    }

    // === TIMEOUT SUPPORT FOR INTEGRATION ===

    /**
     * Minimax with timeout support for TimedMinimax
     */
    public static int minimaxWithTimeout(GameState state, int depth, int alpha, int beta,
                                         boolean maximizingPlayer, BooleanSupplier timeoutCheck) {
        if (timeoutCheck.getAsBoolean()) {
            throw new RuntimeException("Timeout");
        }

        // Use the strategy-based search with timeout
        SearchStrategy strategy = SearchStrategy.ALPHA_BETA; // Default for compatibility
        return searchWithTimeoutSupport(state, depth, alpha, beta, maximizingPlayer, strategy, timeoutCheck, false);
    }

    /**
     * Enhanced timeout support with strategy selection
     */
    private static int searchWithTimeoutSupport(GameState state, int depth, int alpha, int beta,
                                                boolean maximizingPlayer, SearchStrategy strategy,
                                                BooleanSupplier timeoutCheck, boolean isPVNode) {
        if (timeoutCheck.getAsBoolean()) {
            throw new RuntimeException("Timeout");
        }

        // Set timeout checker for PVSSearch if using PVS
        if (strategy == SearchStrategy.PVS || strategy == SearchStrategy.PVS_Q) {
            PVSSearch.setTimeoutChecker(timeoutCheck);
        }

        // Delegate to appropriate search method
        return searchWithStrategy(state, depth, alpha, beta, maximizingPlayer, strategy, isPVNode);
    }

    // === MOVE ORDERING & HEURISTICS ===

    /**
     * Enhanced Move Ordering - same as before but cleaner
     */
    public static void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry entry) {
        // 1. TT Move at first position
        if (entry != null && entry.bestMove != null) {
            for (int i = 0; i < moves.size(); i++) {
                if (moves.get(i).equals(entry.bestMove)) {
                    Move ttMove = moves.remove(i);
                    moves.add(0, ttMove);
                    break;
                }
            }
        }

        // 2. Sort remaining moves with comprehensive scoring
        if (moves.size() > 1) {
            int startIndex = (entry != null && entry.bestMove != null) ? 1 : 0;
            List<Move> restMoves = moves.subList(startIndex, moves.size());

            restMoves.sort((a, b) -> {
                int scoreA = scoreMoveAdvanced(state, a, depth);
                int scoreB = scoreMoveAdvanced(state, b, depth);
                return Integer.compare(scoreB, scoreA);
            });
        }
    }

    /**
     * Advanced move scoring with all heuristics
     */
    public static int scoreMoveAdvanced(GameState state, Move move, int depth) {
        int score = scoreMove(state, move); // Base tactical score

        // PV Move bonus (second highest priority after TT move)
        if (depth < pvLine.length && move.equals(pvLine[depth])) {
            score += 15000;
        }

        // Killer Move bonuses
        if (depth < killerMoves.length) {
            if (move.equals(killerMoves[depth][0])) {
                score += 9000; // Primary killer
            } else if (move.equals(killerMoves[depth][1])) {
                score += 8000; // Secondary killer
            }
        }

        // Positional bonuses for non-tactical moves
        if (!isCapture(move, state)) {
            score += getPositionalBonus(move, state);
        }

        return score;
    }

    /**
     * Positional bonus calculation
     */
    private static int getPositionalBonus(Move move, GameState state) {
        int bonus = 0;

        // Central control bonus
        for (int centralSquare : centralSquares) {
            if (move.to == centralSquare) {
                bonus += 50;
                break;
            }
        }

        // Guard advancement bonus
        boolean isRed = state.redToMove;
        boolean isGuardMove = (move.amountMoved == 1) &&
                (((isRed && (state.redGuard & GameState.bit(move.from)) != 0)) ||
                        (!isRed && (state.blueGuard & GameState.bit(move.from)) != 0));

        if (isGuardMove) {
            int targetRank = GameState.rank(move.to);
            if (isRed && targetRank < 3) { // Moving towards blue castle
                bonus += (3 - targetRank) * 20;
            } else if (!isRed && targetRank > 3) { // Moving towards red castle
                bonus += (targetRank - 3) * 20;
            }
        }

        return bonus;
    }

    // === PUBLIC INTERFACE METHODS FOR INTEGRATION ===

    /**
     * Public access to transposition table for PVSSearch
     */
    public static void storeTranspositionEntry(long hash, TTEntry entry) {
        transpositionTable.put(hash, entry);
    }

    /**
     * Public access to killer moves for PVSSearch
     */
    public static void storeKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length) return;
        if (move.equals(killerMoves[depth][0])) return;
        killerMoves[depth][1] = killerMoves[depth][0];
        killerMoves[depth][0] = move;
    }

    /**
     * Public access to PV line for PVSSearch
     */
    public static void storePVMove(Move move, int depth) {
        if (depth < pvLine.length) {
            pvLine[depth] = move;
        }
    }

    /**
     * Reset killer moves periodically
     */
    public static void resetKillerMoves() {
        killerAge++;
        if (killerAge > 1000) {
            killerMoves = new Move[20][2];
            killerAge = 0;
        }
    }

    /**
     * Public access to transposition table
     */
    public static TTEntry getTranspositionEntry(long hash) {
        return transpositionTable.get(hash);
    }

    // === ALL YOUR EXISTING EVALUATION METHODS (unchanged) ===

    public static boolean isGameOver(GameState state) {
        boolean blueGuardOnD7 = (state.blueGuard & GameState.bit(getIndex(6, 3))) != 0;
        boolean redGuardOnD1 = (state.redGuard & GameState.bit(getIndex(0, 3))) != 0;
        return state.redGuard == 0 || state.blueGuard == 0 || blueGuardOnD7 || redGuardOnD1;
    }

    public static int evaluate(GameState state, int depth) {
        // YOUR COMPLETE EXISTING EVALUATION FUNCTION
        // (Keep all your existing evaluation code here - it's perfect!)

        // Simplified version for this example - use your full evaluation
        boolean redWinsByCastle = state.redGuard == GameState.bit(BLUE_CASTLE_INDEX);
        boolean blueWinsByCastle = state.blueGuard == GameState.bit(RED_CASTLE_INDEX);

        if (state.redGuard == 0 || blueWinsByCastle) return -10000 - depth;
        if (state.blueGuard == 0 || redWinsByCastle) return 10000 + depth;

        int redScore = 0;
        int blueScore = 0;

        // Material count
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            redScore += state.redStackHeights[i] * 100;
            blueScore += state.blueStackHeights[i] * 100;
        }

        return redScore - blueScore;
    }

    public static int scoreMove(GameState state, Move move) {
        // YOUR EXISTING scoreMove implementation
        if (state == null) {
            return move.amountMoved;
        }

        int to = move.to;
        long toBit = GameState.bit(to);
        boolean isRed = state.redToMove;

        boolean capturesGuard = isRed
                ? (state.blueGuard & toBit) != 0
                : (state.redGuard & toBit) != 0;

        boolean capturesTower = isRed
                ? (state.blueTowers & toBit) != 0
                : (state.redTowers & toBit) != 0;

        int score = 0;
        if (capturesGuard) score += 10000;
        if (capturesTower) score += 500;
        score += move.amountMoved;

        return score;
    }

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
}
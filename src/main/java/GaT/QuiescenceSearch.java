package GaT;

import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.Objects.TTEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Quiescence Search implementation for Guard & Towers
 *
 * Quiescence Search extends the search in tactical positions to avoid
 * the horizon effect. It only considers "quiet" positions for evaluation,
 * continuing to search captures, checks, and other forcing moves.
 */
public class QuiescenceSearch {

    private static final HashMap<Long, TTEntry> qTable = new HashMap<>();
    private static final int MAX_Q_DEPTH = 8; // Limit quiescence depth

    // Statistics for analysis
    public static long qNodes = 0;
    public static long qCutoffs = 0;
    public static long standPatCutoffs = 0;
    public static long qTTHits = 0;

    /**
     * Public interface for quiescence search - called by Minimax
     */
    public static int quiesce(GameState state, int alpha, int beta, boolean maximizingPlayer, int qDepth) {
        return quiesceInternal(state, alpha, beta, maximizingPlayer, qDepth);
    }

    /**
     * Quiescence search - only considers captures, checks, and other forcing moves
     */
    private static int quiesceInternal(GameState state, int alpha, int beta, boolean maximizingPlayer, int qDepth) {
        qNodes++;

        // Prevent infinite quiescence search
        if (qDepth >= MAX_Q_DEPTH) {
            return Minimax.evaluate(state, -qDepth);
        }

        // Check quiescence transposition table
        long hash = state.hash();
        TTEntry qEntry = qTable.get(hash);
        if (qEntry != null) {
            qTTHits++;
            if (qEntry.flag == TTEntry.EXACT) {
                return qEntry.score;
            } else if (qEntry.flag == TTEntry.LOWER_BOUND && qEntry.score >= beta) {
                return qEntry.score;
            } else if (qEntry.flag == TTEntry.UPPER_BOUND && qEntry.score <= alpha) {
                return qEntry.score;
            }
        }

        // Stand pat evaluation
        int standPat = Minimax.evaluate(state, -qDepth);

        if (maximizingPlayer) {
            if (standPat >= beta) {
                standPatCutoffs++;
                return beta; // Beta cutoff
            }
            alpha = Math.max(alpha, standPat);

            // Generate only tactical moves (captures, checks, promotions)
            List<Move> tacticalMoves = generateTacticalMoves(state);

            if (tacticalMoves.isEmpty()) {
                return standPat; // Quiet position
            }

            // Order tactical moves by potential gain
            orderTacticalMoves(tacticalMoves, state);

            int maxEval = standPat;
            Move bestMove = null;

            for (Move move : tacticalMoves) {
                // SEE pruning - skip obviously bad captures
                if (isCapture(move, state) && staticExchangeEvaluation(move, state) < 0) {
                    continue;
                }

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = quiesceInternal(copy, alpha, beta, false, qDepth + 1);

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    qCutoffs++;
                    break; // Beta cutoff
                }
            }

            // Store in quiescence table
            int flag = maxEval <= standPat ? TTEntry.UPPER_BOUND :
                    maxEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            qTable.put(hash, new TTEntry(maxEval, -qDepth, flag, bestMove));

            return maxEval;

        } else {
            if (standPat <= alpha) {
                standPatCutoffs++;
                return alpha; // Alpha cutoff
            }
            beta = Math.min(beta, standPat);

            List<Move> tacticalMoves = generateTacticalMoves(state);

            if (tacticalMoves.isEmpty()) {
                return standPat; // Quiet position
            }

            orderTacticalMoves(tacticalMoves, state);

            int minEval = standPat;
            Move bestMove = null;

            for (Move move : tacticalMoves) {
                if (isCapture(move, state) && staticExchangeEvaluation(move, state) < 0) {
                    continue;
                }

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = quiesceInternal(copy, alpha, beta, true, qDepth + 1);

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    qCutoffs++;
                    break; // Alpha cutoff
                }
            }

            // Store in quiescence table
            int flag = minEval <= alpha ? TTEntry.UPPER_BOUND :
                    minEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            qTable.put(hash, new TTEntry(minEval, -qDepth, flag, bestMove));

            return minEval;
        }
    }

    /**
     * Generate only tactical moves for quiescence search
     */
    private static List<Move> generateTacticalMoves(GameState state) {
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        List<Move> tacticalMoves = new ArrayList<>();

        for (Move move : allMoves) {
            if (isTacticalMove(move, state)) {
                tacticalMoves.add(move);
            }
        }

        return tacticalMoves;
    }

    /**
     * Check if a move is tactical (forcing)
     */
    private static boolean isTacticalMove(Move move, GameState state) {
        // Captures
        if (isCapture(move, state)) {
            return true;
        }

        // Checks (moves that put enemy guard in danger)
        if (givesCheck(move, state)) {
            return true;
        }

        // Guard moves to castle field
        if (isGuardMoveToWinning(move, state)) {
            return true;
        }

        // Promotion-like moves (high-value stacking)
        if (isHighValueStack(move, state)) {
            return true;
        }

        return false;
    }

    /**
     * Order tactical moves by potential value
     */
    private static void orderTacticalMoves(List<Move> moves, GameState state) {
        moves.sort((a, b) -> {
            int scoreA = scoreTacticalMove(a, state);
            int scoreB = scoreTacticalMove(b, state);
            return Integer.compare(scoreB, scoreA);
        });
    }

    /**
     * Score tactical moves for ordering
     */
    private static int scoreTacticalMove(Move move, GameState state) {
        int score = 0;

        // Capture scoring with MVV-LVA (Most Valuable Victim - Least Valuable Attacker)
        if (isCapture(move, state)) {
            score += getCaptureValue(move, state) * 100;
            score -= getAttackerValue(move, state);
        }

        // Check bonus
        if (givesCheck(move, state)) {
            score += 500;
        }

        // Winning move bonus
        if (isGuardMoveToWinning(move, state)) {
            score += 10000;
        }

        return score;
    }

    /**
     * Static Exchange Evaluation - estimates the value of a capture sequence
     */
    private static int staticExchangeEvaluation(Move move, GameState state) {
        if (!isCapture(move, state)) {
            return 0; // No capture
        }

        int targetValue = getCaptureValue(move, state);
        int attackerValue = getAttackerValue(move, state);

        // Simple SEE: if we capture something more valuable than our attacker, it's good
        // More sophisticated SEE would simulate the entire exchange sequence
        return targetValue - attackerValue;
    }

    // Helper methods
    private static boolean isCapture(Move move, GameState state) {
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

    private static boolean givesCheck(Move move, GameState state) {
        GameState copy = state.copy();
        copy.applyMove(move);

        // Check if enemy guard is in danger after this move
        boolean enemyIsRed = !state.redToMove;
        long enemyGuard = enemyIsRed ? copy.redGuard : copy.blueGuard;

        if (enemyGuard == 0) return false; // Guard already captured

        // Generate our moves in the new position to see if we can capture the guard
        copy.redToMove = state.redToMove; // Set to our turn again
        List<Move> ourMoves = MoveGenerator.generateAllMoves(copy);

        for (Move ourMove : ourMoves) {
            if (GameState.bit(ourMove.to) == enemyGuard) {
                return true; // We can capture the enemy guard
            }
        }

        return false;
    }

    private static boolean isGuardMoveToWinning(Move move, GameState state) {
        boolean isRed = state.redToMove;
        boolean isGuardMove = (move.amountMoved == 1) &&
                (((isRed && (state.redGuard & GameState.bit(move.from)) != 0)) ||
                        (!isRed && (state.blueGuard & GameState.bit(move.from)) != 0));

        if (!isGuardMove) return false;

        // Check if moving to enemy castle
        int targetCastle = isRed ? Minimax.BLUE_CASTLE_INDEX : Minimax.RED_CASTLE_INDEX;
        return move.to == targetCastle;
    }

    private static boolean isHighValueStack(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long ownTowers = isRed ? state.redTowers : state.blueTowers;

        // Check if stacking on own tower to create high stack
        if ((ownTowers & GameState.bit(move.to)) != 0) {
            int[] stackHeights = isRed ? state.redStackHeights : state.blueStackHeights;
            return stackHeights[move.to] + move.amountMoved >= 4; // Creates stack of 4+
        }

        return false;
    }

    private static int getCaptureValue(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Guard is most valuable
        if ((isRed && (state.blueGuard & toBit) != 0) ||
                (!isRed && (state.redGuard & toBit) != 0)) {
            return 10000;
        }

        // Tower value based on height
        if (isRed && (state.blueTowers & toBit) != 0) {
            return state.blueStackHeights[move.to] * 100;
        } else if (!isRed && (state.redTowers & toBit) != 0) {
            return state.redStackHeights[move.to] * 100;
        }

        return 0;
    }

    private static int getAttackerValue(Move move, GameState state) {
        // Guard move
        if (move.amountMoved == 1) {
            boolean isRed = state.redToMove;
            long guardBit = GameState.bit(move.from);
            if (((isRed && (state.redGuard & guardBit) != 0)) ||
                    (!isRed && (state.blueGuard & guardBit) != 0)) {
                return 10000; // Guard value
            }
        }

        // Tower move - value based on amount moved (partial stack)
        return move.amountMoved * 100;
    }

    /**
     * Get current quiescence search statistics
     */
    public static void printQuiescenceStats() {
        System.out.println("=== Quiescence Search Statistics ===");
        System.out.println("Q-Nodes searched: " + qNodes);
        System.out.println("Q-Cutoffs: " + qCutoffs);
        System.out.println("Stand-pat cutoffs: " + standPatCutoffs);
        System.out.println("Q-TT hits: " + qTTHits);
        if (qNodes > 0) {
            System.out.println("Q-Cutoff rate: " + (100.0 * qCutoffs / qNodes) + "%");
            System.out.println("Stand-pat rate: " + (100.0 * standPatCutoffs / qNodes) + "%");
        }
        System.out.println("=====================================");
    }

    /**
     * Reset quiescence statistics
     */
    public static void resetQuiescenceStats() {
        qNodes = 0;
        qCutoffs = 0;
        standPatCutoffs = 0;
        qTTHits = 0;
    }

    /**
     * Clear quiescence transposition table
     */
    public static void clearQuiescenceTable() {
        qTable.clear();
    }
}
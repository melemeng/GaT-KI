package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ENHANCED QUIESCENCE SEARCH - WITH HISTORY HEURISTIC INTEGRATION
 *
 * ENHANCEMENTS:
 * ✅ History Heuristic updates for capture sequences
 * ✅ Better move ordering in tactical positions
 * ✅ Enhanced statistics tracking
 * ✅ Optimized for single-threaded performance
 */
public class QuiescenceSearch {

    // === THREAD-SAFE RECURSION PROTECTION ===
    private static final ThreadLocal<AtomicInteger> recursionDepth =
            ThreadLocal.withInitial(() -> new AtomicInteger(0));
    private static final int MAX_Q_DEPTH = 12;
    private static final int MAX_TACTICAL_RECURSION = 2;

    // === STATISTICS ===
    public static long qNodes = 0;
    public static long qCutoffs = 0;
    public static long standPatCutoffs = 0;

    // === MOVE ORDERING ACCESS ===
    private static MoveOrdering moveOrdering = new MoveOrdering();

    /**
     * ENHANCED: Quiescence search with history heuristic integration
     */
    public static int quiesce(GameState state, int alpha, int beta, boolean maximizingPlayer, int qDepth) {
        qNodes++;

        if (qDepth >= MAX_Q_DEPTH) {
            return Minimax.evaluate(state, -qDepth);
        }

        // Stand pat evaluation
        int standPat = Minimax.evaluate(state, -qDepth);

        if (maximizingPlayer) {
            if (standPat >= beta) {
                standPatCutoffs++;
                return beta;
            }
            alpha = Math.max(alpha, standPat);

            // Enhanced tactical move generation with ordering
            List<Move> tacticalMoves = generateTacticalMovesEnhanced(state, qDepth);

            if (tacticalMoves.isEmpty()) {
                return standPat;
            }

            int maxEval = standPat;
            for (Move move : tacticalMoves) {
                // Enhanced delta pruning
                if (isCapture(move, state)) {
                    int captureValue = estimateCaptureValue(move, state);
                    if (standPat + captureValue + 150 < alpha) {
                        continue; // Skip bad captures
                    }
                }

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = quiesce(copy, alpha, beta, false, qDepth + 1);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    qCutoffs++;

                    // ENHANCED: Update history for good tactical sequences
                    if (qDepth <= 2 && !isCapture(move, state)) {
                        try {
                            moveOrdering.updateHistoryOnCutoff(move, state, Math.max(1, 8 - qDepth));
                        } catch (Exception e) {
                            // Silent fail - history is optimization
                        }
                    }
                    break;
                }
            }
            return maxEval;

        } else {
            if (standPat <= alpha) {
                standPatCutoffs++;
                return alpha;
            }
            beta = Math.min(beta, standPat);

            List<Move> tacticalMoves = generateTacticalMovesEnhanced(state, qDepth);

            if (tacticalMoves.isEmpty()) {
                return standPat;
            }

            int minEval = standPat;
            for (Move move : tacticalMoves) {
                if (isCapture(move, state)) {
                    int captureValue = estimateCaptureValue(move, state);
                    if (standPat - captureValue - 150 > beta) {
                        continue;
                    }
                }

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = quiesce(copy, alpha, beta, true, qDepth + 1);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);

                if (beta <= alpha) {
                    qCutoffs++;

                    // ENHANCED: Update history for good tactical sequences
                    if (qDepth <= 2 && !isCapture(move, state)) {
                        try {
                            moveOrdering.updateHistoryOnCutoff(move, state, Math.max(1, 8 - qDepth));
                        } catch (Exception e) {
                            // Silent fail - history is optimization
                        }
                    }
                    break;
                }
            }
            return minEval;
        }
    }

    /**
     * ENHANCED: Safe tactical move generation with better ordering
     */
    public static List<Move> generateTacticalMoves(GameState state) {
        return generateTacticalMovesEnhanced(state, 0);
    }

    private static List<Move> generateTacticalMovesEnhanced(GameState state, int qDepth) {
        AtomicInteger depth = recursionDepth.get();

        if (depth.get() >= MAX_TACTICAL_RECURSION) {
            return new ArrayList<>();
        }

        depth.incrementAndGet();
        try {
            List<Move> allMoves = MoveGenerator.generateAllMoves(state);
            List<Move> tacticalMoves = new ArrayList<>();

            for (Move move : allMoves) {
                if (isTacticalMoveSafe(move, state)) {
                    tacticalMoves.add(move);
                }
            }

            // ENHANCED: Order tactical moves for better cutoffs
            orderTacticalMoves(tacticalMoves, state, qDepth);

            return tacticalMoves;
        } finally {
            depth.decrementAndGet();
        }
    }

    /**
     * ENHANCED: Order tactical moves using history and capture values
     */
    private static void orderTacticalMoves(List<Move> moves, GameState state, int qDepth) {
        if (moves.size() <= 1) return;

        moves.sort((a, b) -> {
            int scoreA = scoreTacticalMove(a, state, qDepth);
            int scoreB = scoreTacticalMove(b, state, qDepth);
            return Integer.compare(scoreB, scoreA);
        });
    }

    /**
     * ENHANCED: Score tactical moves with history integration
     */
    private static int scoreTacticalMove(Move move, GameState state, int qDepth) {
        int score = 0;

        // Capture value (highest priority)
        if (isCapture(move, state)) {
            score += estimateCaptureValue(move, state) * 10;

            // MVV-LVA: subtract attacker value
            score -= getAttackerValue(move, state);
        }

        // Winning moves
        if (isWinningGuardMove(move, state)) {
            score += 50000;
        }

        // Check giving moves
        if (givesCheckSimple(move, state)) {
            score += 100;
        }

        // History heuristic for quiet tactical moves
        if (!isCapture(move, state) && qDepth <= 2) {
            try {
                if (moveOrdering.getHistoryHeuristic().isQuietMove(move, state)) {
                    boolean isRedMove = state.redToMove;
                    score += moveOrdering.getHistoryHeuristic().getScore(move, isRedMove) / 10;
                }
            } catch (Exception e) {
                // Silent fail
            }
        }

        // Activity bonus
        score += move.amountMoved * 5;

        return score;
    }

    /**
     * ENHANCED: Safe tactical move detection
     */
    private static boolean isTacticalMoveSafe(Move move, GameState state) {
        // 1. All captures are tactical
        if (isCapture(move, state)) {
            return true;
        }

        // 2. Winning guard moves
        if (isWinningGuardMove(move, state)) {
            return true;
        }

        // 3. Simple check detection
        if (givesCheckSimple(move, state)) {
            return true;
        }

        // 4. Promotion-like moves (high towers)
        if (move.amountMoved >= 3) {
            return true;
        }

        return false;
    }

    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        long pieces = state.redToMove ? (state.blueTowers | state.blueGuard) : (state.redTowers | state.redGuard);
        return (pieces & toBit) != 0;
    }

    private static boolean isWinningGuardMove(Move move, GameState state) {
        boolean isRed = state.redToMove;

        // Check if it's a guard move
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0 || move.from != Long.numberOfTrailingZeros(guardBit)) {
            return false;
        }

        // Check if moving to enemy castle
        int targetCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
        return move.to == targetCastle;
    }

    private static boolean givesCheckSimple(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;

        if (enemyGuard == 0) return false;

        int enemyGuardPos = Long.numberOfTrailingZeros(enemyGuard);
        int rankDiff = Math.abs(GameState.rank(move.to) - GameState.rank(enemyGuardPos));
        int fileDiff = Math.abs(GameState.file(move.to) - GameState.file(enemyGuardPos));

        // Simple adjacency or line attack check
        return (rankDiff + fileDiff == 1) ||
                (rankDiff == 0 && fileDiff <= move.amountMoved) ||
                (fileDiff == 0 && rankDiff <= move.amountMoved);
    }

    private static int estimateCaptureValue(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Guard capture
        if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
            return 1500;
        }

        // Tower capture
        if (((isRed ? state.blueTowers : state.redTowers) & toBit) != 0) {
            int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
            return height * 100;
        }

        return 0;
    }

    /**
     * ENHANCED: Get attacker value for MVV-LVA
     */
    private static int getAttackerValue(Move move, GameState state) {
        boolean isRed = state.redToMove;

        // Check if it's a guard move
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit)) {
            return 50; // Guard value
        }

        // Tower value based on height
        int height = isRed ? state.redStackHeights[move.from] : state.blueStackHeights[move.from];
        return height * 25;
    }

    // === ENHANCED INITIALIZATION ===

    /**
     * ENHANCED: Set move ordering instance for history access
     */
    public static void setMoveOrdering(MoveOrdering ordering) {
        moveOrdering = ordering;
    }

    // === STATISTICS AND UTILITY ===

    public static void resetQuiescenceStats() {
        qNodes = 0;
        qCutoffs = 0;
        standPatCutoffs = 0;
    }

    public static void setRemainingTime(long timeMs) {
        // Adjust quiescence depth based on time
        // Could implement adaptive depth here
    }

    /**
     * ENHANCED: Get quiescence statistics with history info
     */
    public static String getQuiescenceStatistics() {
        return String.format("Q-Search: %d nodes, %d cutoffs, %d standpat",
                qNodes, qCutoffs, standPatCutoffs);
    }
}
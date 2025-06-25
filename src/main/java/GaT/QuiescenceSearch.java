package GaT;

import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.Objects.TTEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * FIXED Quiescence Search implementation for Guard & Towers
 *
 * Fixes:
 * - Corrected parentheses in bitwise operations
 * - Fixed guard movement detection
 * - Proper SEE attacker value calculation
 * - Added missing helper methods
 */
public class QuiescenceSearch {

    private static final HashMap<Long, TTEntry> qTable = new HashMap<>();
    private static final int MAX_Q_DEPTH = 16; // INCREASED from 8

    // Adaptive depth based on time pressure
    private static long remainingTimeMs = 180000; // Updated from outside

    // Statistics for analysis
    public static long qNodes = 0;
    public static long qCutoffs = 0;
    public static long standPatCutoffs = 0;
    public static long qTTHits = 0;

    /**
     * Reset statistics
     */
    public static void resetQuiescenceStats() {
        qNodes = 0;
        qCutoffs = 0;
        standPatCutoffs = 0;
        qTTHits = 0;
    }

    /**
     * Set remaining time for adaptive depth
     */
    public static void setRemainingTime(long timeMs) {
        remainingTimeMs = timeMs;
    }

    /**
     * Public interface for quiescence search - called by Minimax
     */
    public static int quiesce(GameState state, int alpha, int beta, boolean maximizingPlayer, int qDepth) {
        return quiesceInternal(state, alpha, beta, maximizingPlayer, qDepth);
    }

    /**
     * OPTIMIZED Quiescence search
     */
    private static int quiesceInternal(GameState state, int alpha, int beta, boolean maximizingPlayer, int qDepth) {
        qNodes++;

        // Adaptive depth limit based on time pressure
        int maxDepth = remainingTimeMs > 30000 ? MAX_Q_DEPTH :
                remainingTimeMs > 10000 ? 12 : 8;

        if (qDepth >= maxDepth) {
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

            // Generate only CRITICAL tactical moves
            List<Move> tacticalMoves = generateCriticalTacticalMoves(state);

            if (tacticalMoves.isEmpty()) {
                return standPat; // Quiet position
            }

            // Order tactical moves by potential gain
            orderTacticalMoves(tacticalMoves, state);

            int maxEval = standPat;
            Move bestMove = null;

            for (Move move : tacticalMoves) {
                // IMPROVED SEE pruning - skip obviously bad captures
                if (isCapture(move, state) && fastSEE(move, state) < -50) {
                    continue; // Skip clearly losing captures
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

            List<Move> tacticalMoves = generateCriticalTacticalMoves(state);

            if (tacticalMoves.isEmpty()) {
                return standPat; // Quiet position
            }

            orderTacticalMoves(tacticalMoves, state);

            int minEval = standPat;
            Move bestMove = null;

            for (Move move : tacticalMoves) {
                if (isCapture(move, state) && fastSEE(move, state) < -50) {
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
     * OPTIMIZED: Generate only CRITICAL tactical moves (more selective)
     */
    public static List<Move> generateTacticalMoves(GameState state) {
        return generateCriticalTacticalMoves(state);
    }

    private static List<Move> generateCriticalTacticalMoves(GameState state) {
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        List<Move> tacticalMoves = new ArrayList<>();

        for (Move move : allMoves) {
            if (isCriticalTacticalMove(move, state)) {
                tacticalMoves.add(move);
            }
        }

        return tacticalMoves;
    }

    /**
     * OPTIMIZED: More selective tactical move detection
     */
    private static boolean isCriticalTacticalMove(Move move, GameState state) {
        // 1. All captures are tactical
        if (isCapture(move, state)) {
            return true;
        }

        // 2. Winning guard moves (guard to enemy castle)
        if (isWinningGuardMove(move, state)) {
            return true;
        }

        // 3. FAST check detection (much more efficient than before)
        if (fastGivesCheck(move, state)) {
            return true;
        }

        // 4. Guard escape moves when in danger
        if (isGuardEscapeMove(move, state)) {
            return true;
        }

        return false;
    }

    /**
     * FAST check detection - no expensive move generation
     */
    private static boolean fastGivesCheck(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;

        if (enemyGuard == 0) return false;

        int enemyGuardPos = Long.numberOfTrailingZeros(enemyGuard);

        // Check if our move destination can attack the enemy guard
        return canPositionAttackTarget(move.to, enemyGuardPos, move.amountMoved);
    }

    /**
     * FIXED: Fast position attack check with proper guard movement
     */
    private static boolean canPositionAttackTarget(int from, int target, int moveDistance) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(target));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(target));

        // Guard can attack adjacent squares (orthogonally only)
        if (moveDistance == 1) {
            return (rankDiff + fileDiff == 1) && (rankDiff <= 1 && fileDiff <= 1);
        }

        // Tower can attack along rank/file up to its movement distance
        boolean sameRank = rankDiff == 0;
        boolean sameFile = fileDiff == 0;
        int distance = Math.max(rankDiff, fileDiff);

        return (sameRank || sameFile) && distance <= moveDistance;
    }

    /**
     * FIXED: Check if move is a winning guard move
     */
    private static boolean isWinningGuardMove(Move move, GameState state) {
        boolean isRed = state.redToMove;

        // FIXED: Check if piece at from position is actually a guard
        boolean isGuardMove = ((isRed && (state.redGuard & GameState.bit(move.from)) != 0) ||
                (!isRed && (state.blueGuard & GameState.bit(move.from)) != 0));

        if (!isGuardMove) return false;

        // Check if moving to enemy castle
        int targetCastle = isRed ? Minimax.BLUE_CASTLE_INDEX : Minimax.RED_CASTLE_INDEX;
        return move.to == targetCastle;
    }

    /**
     * Check if move is a guard escape when in danger
     */
    private static boolean isGuardEscapeMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;

        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        if (move.from != guardPos) return false;

        // Only consider escape if guard is currently in danger
        return Minimax.isGuardInDangerImproved(state, isRed);
    }

    /**
     * IMPROVED tactical move ordering
     */
    private static void orderTacticalMoves(List<Move> moves, GameState state) {
        moves.sort((a, b) -> {
            int scoreA = scoreTacticalMove(a, state);
            int scoreB = scoreTacticalMove(b, state);
            return Integer.compare(scoreB, scoreA);
        });
    }

    /**
     * FIXED: Tactical move scoring with proper parentheses
     */
    private static int scoreTacticalMove(Move move, GameState state) {
        int score = 0;
        boolean isRed = state.redToMove;

        // Winning move gets highest priority
        if (isWinningGuardMove(move, state)) {
            score += 10000;
        }

        // Capture scoring with MVV-LVA
        if (isCapture(move, state)) {
            long toBit = GameState.bit(move.to);

            // FIXED: Proper parentheses for guard capture check
            if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
                score += 3000; // Guard capture
            } else {
                int victimHeight = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
                score += victimHeight * 100; // Tower capture by height
            }

            // Attacker value (subtract for MVV-LVA)
            score -= getAttackerValue(move, state);
        }

        // Check bonus
        if (fastGivesCheck(move, state)) {
            score += 500;
        }

        // Guard escape bonus
        if (isGuardEscapeMove(move, state)) {
            score += 800;
        }

        return score;
    }

    /**
     * FIXED: Static Exchange Evaluation with proper attacker calculation
     */
    private static int fastSEE(Move move, GameState state) {
        if (!isCapture(move, state)) {
            return 0;
        }

        boolean isRed = state.redToMove;
        long toBit = GameState.bit(move.to);

        // Calculate victim value
        int victimValue = 0;
        if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
            victimValue = 3000; // Guard value
        } else {
            int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
            victimValue = height * 100; // Tower value
        }

        // FIXED: Calculate proper attacker value
        int attackerValue = getAttackerValue(move, state);

        // Simple SEE: victim value minus attacker value
        return victimValue - attackerValue;
    }

    /**
     * NEW: Calculate the value of the piece making the attack
     */
    private static int getAttackerValue(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long fromBit = GameState.bit(move.from);

        // Check if attacker is a guard
        if (((isRed ? state.redGuard : state.blueGuard) & fromBit) != 0) {
            return 50; // Guard has low material value but high strategic value
        }

        // Attacker is a tower - value based on height
        int height = isRed ? state.redStackHeights[move.from] : state.blueStackHeights[move.from];
        return height * 25; // Towers worth 25 per height level for SEE purposes
    }

    // === HELPER METHODS ===

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

    /**
     * Clear quiescence table periodically to prevent memory issues
     */
    public static void clearQuiescenceTable() {
        if (qTable.size() > 100000) { // Clear when table gets too large
            qTable.clear();
        }
    }
}
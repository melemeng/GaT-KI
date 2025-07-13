package GaT.search;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * CRITICAL FIX: QuiescenceSearch with proper null-safe copy() exception handling
 *
 * FIXES:
 * ✅ state.copy() now inside try-catch blocks
 * ✅ Proper exception handling for all copy operations
 * ✅ Fixed validation order
 * ✅ Robust fallback mechanisms
 * ✅ Comprehensive error logging
 */
public class QuiescenceSearch {

    // === SIMPLE RECURSION PROTECTION ===
    private static int recursionDepth = 0;
    private static final int MAX_Q_DEPTH = 12;
    private static final int MAX_TACTICAL_RECURSION = 2;

    // === STATISTICS ===
    public static long qNodes = 0;
    public static long qCutoffs = 0;
    public static long standPatCutoffs = 0;

    // === MOVE ORDERING ACCESS ===
    private static MoveOrdering moveOrdering = new MoveOrdering();

    /**
     * CRITICAL FIX: Quiescence search with proper exception handling
     */
    public static int quiesce(GameState state, int alpha, int beta, boolean maximizingPlayer, int qDepth) {
        // CRITICAL NULL CHECK AT ENTRY
        if (state == null) {
            System.err.println("❌ ERROR: Null state in QuiescenceSearch.quiesce()");
            return 0; // Safe neutral value
        }

        // VALIDATION CHECK
        if (!state.isValid()) {
            System.err.println("❌ ERROR: Invalid state in QuiescenceSearch.quiesce()");
            return Minimax.evaluate(state, -qDepth);
        }

        qNodes++;

        if (qDepth >= MAX_Q_DEPTH) {
            return Minimax.evaluate(state, -qDepth);
        }

        // Stand pat evaluation with exception handling
        int standPat;
        try {
            standPat = Minimax.evaluate(state, -qDepth);
        } catch (Exception e) {
            System.err.println("❌ ERROR: Stand pat evaluation failed: " + e.getMessage());
            standPat = 0; // Neutral fallback
        }

        if (maximizingPlayer) {
            if (standPat >= beta) {
                standPatCutoffs++;
                return beta;
            }
            alpha = Math.max(alpha, standPat);

            // Enhanced tactical move generation with proper exception handling
            List<Move> tacticalMoves;
            try {
                tacticalMoves = generateTacticalMovesEnhanced(state, qDepth);
            } catch (Exception e) {
                System.err.println("❌ ERROR: Tactical move generation failed: " + e.getMessage());
                return standPat;
            }

            if (tacticalMoves.isEmpty()) {
                return standPat;
            }

            int maxEval = standPat;
            for (Move move : tacticalMoves) {
                if (move == null) {
                    System.err.println("❌ ERROR: Null move in tactical moves list");
                    continue;
                }

                // Enhanced delta pruning with exception handling
                try {
                    if (isCapture(move, state)) {
                        int captureValue = estimateCaptureValue(move, state);
                        if (standPat + captureValue + 150 < alpha) {
                            continue; // Skip bad captures
                        }
                    }
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Delta pruning check failed for move " + move + ": " + e.getMessage());
                    // Continue without delta pruning
                }

                // CRITICAL FIX: copy() AND applyMove() in try-catch!
                GameState copy = null;
                try {
                    copy = state.copy();

                    // NULL-CHECK AFTER copy()
                    if (copy == null) {
                        System.err.println("❌ ERROR: state.copy() returned null in QuiescenceSearch for move " + move);
                        continue; // Skip this move
                    }

                    // VALIDATION CHECK AFTER copy()
                    if (!copy.isValid()) {
                        System.err.println("❌ ERROR: state.copy() returned invalid state in QuiescenceSearch for move " + move);
                        continue; // Skip this move
                    }

                    // APPLY MOVE
                    copy.applyMove(move);

                    // VALIDATION AFTER applyMove()
                    if (!copy.isValid()) {
                        System.err.println("❌ ERROR: applyMove() corrupted state in QuiescenceSearch for move " + move);
                        continue; // Skip this move
                    }

                } catch (Exception e) {
                    System.err.println("❌ ERROR: Copy/ApplyMove failed in QuiescenceSearch for move " + move + ": " + e.getMessage());
                    continue; // Skip this move
                }

                int eval;
                try {
                    eval = quiesce(copy, alpha, beta, false, qDepth + 1);
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Recursive quiesce() failed for move " + move + ": " + e.getMessage());
                    eval = standPat; // Use conservative fallback
                }

                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);

                if (beta <= alpha) {
                    qCutoffs++;

                    // History update with exception handling
                    if (qDepth <= 2 && !isCapture(move, state)) {
                        try {
                            moveOrdering.updateHistory(move, Math.max(1, 8 - qDepth), state);
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

            List<Move> tacticalMoves;
            try {
                tacticalMoves = generateTacticalMovesEnhanced(state, qDepth);
            } catch (Exception e) {
                System.err.println("❌ ERROR: Tactical move generation failed: " + e.getMessage());
                return standPat;
            }

            if (tacticalMoves.isEmpty()) {
                return standPat;
            }

            int minEval = standPat;
            for (Move move : tacticalMoves) {
                if (move == null) {
                    System.err.println("❌ ERROR: Null move in tactical moves list");
                    continue;
                }

                // Delta pruning with exception handling
                try {
                    if (isCapture(move, state)) {
                        int captureValue = estimateCaptureValue(move, state);
                        if (standPat - captureValue - 150 > beta) {
                            continue;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Delta pruning check failed for move " + move + ": " + e.getMessage());
                    // Continue without delta pruning
                }

                // CRITICAL FIX: copy() AND applyMove() in try-catch!
                GameState copy = null;
                try {
                    copy = state.copy();

                    // NULL-CHECK AFTER copy()
                    if (copy == null) {
                        System.err.println("❌ ERROR: state.copy() returned null in QuiescenceSearch for move " + move);
                        continue; // Skip this move
                    }

                    // VALIDATION CHECK AFTER copy()
                    if (!copy.isValid()) {
                        System.err.println("❌ ERROR: state.copy() returned invalid state in QuiescenceSearch for move " + move);
                        continue; // Skip this move
                    }

                    // APPLY MOVE
                    copy.applyMove(move);

                    // VALIDATION AFTER applyMove()
                    if (!copy.isValid()) {
                        System.err.println("❌ ERROR: applyMove() corrupted state in QuiescenceSearch for move " + move);
                        continue; // Skip this move
                    }

                } catch (Exception e) {
                    System.err.println("❌ ERROR: Copy/ApplyMove failed in QuiescenceSearch for move " + move + ": " + e.getMessage());
                    continue; // Skip this move
                }

                int eval;
                try {
                    eval = quiesce(copy, alpha, beta, true, qDepth + 1);
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Recursive quiesce() failed for move " + move + ": " + e.getMessage());
                    eval = standPat; // Use conservative fallback
                }

                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);

                if (beta <= alpha) {
                    qCutoffs++;

                    // History update with exception handling
                    if (qDepth <= 2 && !isCapture(move, state)) {
                        try {
                            moveOrdering.updateHistory(move, Math.max(1, 8 - qDepth), state);
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
     * FIXED: Tactical move generation with proper exception handling
     */
    private static List<Move> generateTacticalMovesEnhanced(GameState state, int qDepth) {
        // CRITICAL NULL CHECK
        if (state == null) {
            System.err.println("❌ ERROR: Null state in generateTacticalMovesEnhanced");
            return new ArrayList<>();
        }

        if (!state.isValid()) {
            System.err.println("❌ ERROR: Invalid state in generateTacticalMovesEnhanced");
            return new ArrayList<>();
        }

        if (recursionDepth >= MAX_TACTICAL_RECURSION) {
            return new ArrayList<>();
        }

        recursionDepth++;
        try {
            List<Move> allMoves;
            try {
                allMoves = MoveGenerator.generateAllMoves(state);
                if (allMoves == null) {
                    return new ArrayList<>();
                }
            } catch (Exception e) {
                System.err.println("❌ ERROR: Move generation failed in generateTacticalMovesEnhanced: " + e.getMessage());
                return new ArrayList<>();
            }

            List<Move> tacticalMoves = new ArrayList<>();

            for (Move move : allMoves) {
                if (move != null && isTacticalMoveSafe(move, state)) {
                    tacticalMoves.add(move);
                }
            }

            // Order tactical moves with exception handling
            try {
                orderTacticalMoves(tacticalMoves, state, qDepth);
            } catch (Exception e) {
                System.err.println("❌ ERROR: Tactical move ordering failed: " + e.getMessage());
                // Continue with unordered moves
            }

            return tacticalMoves;

        } catch (Exception e) {
            System.err.println("❌ ERROR: generateTacticalMovesEnhanced failed: " + e.getMessage());
            return new ArrayList<>();
        } finally {
            recursionDepth--;
        }
    }

    /**
     * Order tactical moves with exception handling
     */
    private static void orderTacticalMoves(List<Move> moves, GameState state, int qDepth) {
        if (moves.size() <= 1) return;

        try {
            moves.sort((a, b) -> {
                try {
                    int scoreA = scoreTacticalMove(a, state, qDepth);
                    int scoreB = scoreTacticalMove(b, state, qDepth);
                    return Integer.compare(scoreB, scoreA);
                } catch (Exception e) {
                    System.err.println("❌ ERROR: Move comparison failed: " + e.getMessage());
                    return 0; // Equal if comparison fails
                }
            });
        } catch (Exception e) {
            System.err.println("❌ ERROR: Tactical move sorting failed: " + e.getMessage());
            // Continue with unsorted moves
        }
    }

    /**
     * Score tactical moves with exception handling
     */
    private static int scoreTacticalMove(Move move, GameState state, int qDepth) {
        if (move == null || state == null) return 0;

        int score = 0;

        try {
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

            // Activity bonus
            score += move.amountMoved * 5;

        } catch (Exception e) {
            System.err.println("❌ ERROR: Tactical move scoring failed for move " + move + ": " + e.getMessage());
            // Return conservative score
        }

        return score;
    }

    /**
     * Safe tactical move detection with exception handling
     */
    private static boolean isTacticalMoveSafe(Move move, GameState state) {
        if (move == null || state == null) return false;

        try {
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

        } catch (Exception e) {
            System.err.println("❌ ERROR: Tactical move detection failed for move " + move + ": " + e.getMessage());
            return false; // Conservative - don't consider tactical if error
        }

        return false;
    }

    // === SAFE HELPER METHODS ===

    private static boolean isCapture(Move move, GameState state) {
        try {
            if (move == null || state == null) return false;
            long toBit = GameState.bit(move.to);
            long pieces = state.redToMove ? (state.blueTowers | state.blueGuard) : (state.redTowers | state.redGuard);
            return (pieces & toBit) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isWinningGuardMove(Move move, GameState state) {
        try {
            if (move == null || state == null) return false;

            boolean isRed = state.redToMove;

            // Check if it's a guard move
            long guardBit = isRed ? state.redGuard : state.blueGuard;
            if (guardBit == 0 || move.from != Long.numberOfTrailingZeros(guardBit)) {
                return false;
            }

            // Check if moving to enemy castle
            int targetCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
            return move.to == targetCastle;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean givesCheckSimple(Move move, GameState state) {
        try {
            if (move == null || state == null) return false;

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
        } catch (Exception e) {
            return false;
        }
    }

    private static int estimateCaptureValue(Move move, GameState state) {
        try {
            if (move == null || state == null) return 0;

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
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get attacker value for MVV-LVA with exception handling
     */
    private static int getAttackerValue(Move move, GameState state) {
        try {
            if (move == null || state == null) return 0;

            boolean isRed = state.redToMove;

            // Check if it's a guard move
            long guardBit = isRed ? state.redGuard : state.blueGuard;
            if (guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit)) {
                return 50; // Guard value
            }

            // Tower value based on height
            int height = isRed ? state.redStackHeights[move.from] : state.blueStackHeights[move.from];
            return height * 25;
        } catch (Exception e) {
            return 0;
        }
    }

    // === INITIALIZATION AND STATISTICS ===

    /**
     * Set move ordering instance for history access
     */
    public static void setMoveOrdering(MoveOrdering ordering) {
        if (ordering != null) {
            moveOrdering = ordering;
        }
    }

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
     * Get quiescence statistics
     */
    public static String getQuiescenceStatistics() {
        return String.format("Q-Search: %d nodes, %d cutoffs, %d standpat",
                qNodes, qCutoffs, standPatCutoffs);
    }
}
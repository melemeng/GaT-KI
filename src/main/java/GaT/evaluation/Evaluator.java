package GaT.evaluation;

import GaT.model.GameState;
import GaT.search.MoveGenerator;
import GaT.model.Move;

import java.util.List;

/**
 * FIXED EVALUATOR - Balanced and Stable
 *
 * CRITICAL FIXES:
 * ✅ 1. Removed extreme aggressive bonuses that caused wild scores
 * ✅ 2. Fixed evaluation stability and consistency
 * ✅ 3. Proper material vs positional balance
 * ✅ 4. Fixed game-ending condition detection
 * ✅ 5. Robust error handling for edge cases
 * ✅ 6. Tournament-ready evaluation
 */
public class Evaluator {

    // === BALANCED EVALUATION CONSTANTS ===
    private static final int TOWER_BASE_VALUE = 100;
    private static final int GUARD_BASE_VALUE = 50;
    private static final int CASTLE_REACH_SCORE = 5000;  // Reduced from 10000
    private static final int GUARD_CAPTURE_SCORE = 4000; // Reduced from 8000

    // === REASONABLE BONUSES ===
    private static final int GUARD_ADVANCEMENT_BONUS = 25;  // Was 60
    private static final int CENTRAL_BONUS = 15;            // Was 40
    private static final int MOBILITY_BONUS = 8;            // Was 15
    private static final int COORDINATION_BONUS = 12;       // Was 25
    private static final int THREAT_BONUS = 30;             // Was 100

    // === STRATEGIC SQUARES ===
    private static final int[] CENTRAL_SQUARES = {
            GameState.getIndex(2, 3), GameState.getIndex(3, 3), GameState.getIndex(4, 3) // D3, D4, D5
    };

    // === CASTLE POSITIONS ===
    private static final int RED_CASTLE = GameState.getIndex(0, 3);  // D1
    private static final int BLUE_CASTLE = GameState.getIndex(6, 3); // D7

    // === TIME-ADAPTIVE EVALUATION ===
    private static volatile long remainingTimeMs = 180000;
    private static volatile boolean emergencyMode = false;

    /**
     * FIXED MAIN EVALUATION - Stable and Balanced
     */
    public int evaluate(GameState state, int depth) {
        if (state == null) return 0;

        // === TERMINAL POSITION CHECK ===
        int terminalScore = checkTerminalPositionFixed(state, depth);
        if (terminalScore != 0) {
            return terminalScore;
        }

        // === BALANCED EVALUATION STRATEGY ===
        emergencyMode = remainingTimeMs < 1000;

        if (emergencyMode) {
            return evaluateBasic(state);
        } else if (remainingTimeMs < 5000) {
            return evaluateStandard(state);
        } else {
            return evaluateComprehensive(state);
        }
    }

    // === TERMINAL POSITION EVALUATION - FIXED ===
    private int checkTerminalPositionFixed(GameState state, int depth) {
        // Guard captured
        if (state.redGuard == 0) return -GUARD_CAPTURE_SCORE - depth;
        if (state.blueGuard == 0) return GUARD_CAPTURE_SCORE + depth;

        // Guard reached enemy castle
        boolean redWins = (state.redGuard & GameState.bit(RED_CASTLE)) != 0;
        boolean blueWins = (state.blueGuard & GameState.bit(BLUE_CASTLE)) != 0;

        if (redWins) return CASTLE_REACH_SCORE + depth;
        if (blueWins) return -CASTLE_REACH_SCORE - depth;

        return 0;
    }

    // === BASIC EVALUATION (Emergency) ===
    private int evaluateBasic(GameState state) {
        int eval = 0;

        // Simple material count
        eval += evaluateMaterialBasic(state);

        // Basic guard advancement
        eval += evaluateGuardAdvancementBasic(state);

        return eval;
    }

    // === STANDARD EVALUATION ===
    private int evaluateStandard(GameState state) {
        int eval = 0;

        // Balanced weights
        eval += evaluateMaterialWithPosition(state) * 60 / 100;     // 60%
        eval += evaluateGuardAdvancement(state) * 25 / 100;         // 25%
        eval += evaluateTacticalThreats(state) * 10 / 100;          // 10%
        eval += evaluateMobility(state) * 5 / 100;                  // 5%

        return eval;
    }

    // === COMPREHENSIVE EVALUATION ===
    private int evaluateComprehensive(GameState state) {
        int eval = 0;

        // Comprehensive evaluation
        eval += evaluateMaterialWithPosition(state) * 50 / 100;     // 50%
        eval += evaluateGuardAdvancement(state) * 20 / 100;         // 20%
        eval += evaluateTacticalThreats(state) * 15 / 100;          // 15%
        eval += evaluateMobility(state) * 10 / 100;                 // 10%
        eval += evaluatePositionalFactors(state) * 5 / 100;         // 5%

        return eval;
    }

    // === MATERIAL EVALUATION - BALANCED ===
    private int evaluateMaterialBasic(GameState state) {
        int materialScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            materialScore += state.redStackHeights[i] * TOWER_BASE_VALUE;
            materialScore -= state.blueStackHeights[i] * TOWER_BASE_VALUE;
        }

        return materialScore;
    }

    private int evaluateMaterialWithPosition(GameState state) {
        int materialScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int redHeight = state.redStackHeights[i];
            int blueHeight = state.blueStackHeights[i];

            if (redHeight > 0) {
                int value = redHeight * TOWER_BASE_VALUE;
                value += getPositionalBonus(i, redHeight, true);
                materialScore += value;
            }

            if (blueHeight > 0) {
                int value = blueHeight * TOWER_BASE_VALUE;
                value += getPositionalBonus(i, blueHeight, false);
                materialScore -= value;
            }
        }

        return materialScore;
    }

    private int getPositionalBonus(int square, int height, boolean isRed) {
        int bonus = 0;
        int rank = GameState.rank(square);
        int file = GameState.file(square);

        // Moderate advancement bonus
        if (isRed && rank < 4) {
            bonus += (4 - rank) * 8;
        } else if (!isRed && rank > 2) {
            bonus += (rank - 2) * 8;
        }

        // Central control bonus
        if (isCentralSquare(square)) {
            bonus += CENTRAL_BONUS;
        }

        // D-file preference
        if (file == 3) {
            bonus += 10;
        }

        return bonus;
    }

    // === GUARD EVALUATION - BALANCED ===
    private int evaluateGuardAdvancementBasic(GameState state) {
        int guardScore = 0;

        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int rank = GameState.rank(guardPos);
            guardScore += (6 - rank) * GUARD_ADVANCEMENT_BONUS;
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int rank = GameState.rank(guardPos);
            guardScore -= rank * GUARD_ADVANCEMENT_BONUS;
        }

        return guardScore;
    }

    private int evaluateGuardAdvancement(GameState state) {
        int guardScore = 0;

        // Red guard
        if (state.redGuard != 0) {
            guardScore += evaluateGuardAdvancementForSide(state, true);
            guardScore += evaluateGuardSafety(state, true);
        }

        // Blue guard
        if (state.blueGuard != 0) {
            guardScore -= evaluateGuardAdvancementForSide(state, false);
            guardScore -= evaluateGuardSafety(state, false);
        }

        return guardScore;
    }

    private int evaluateGuardAdvancementForSide(GameState state, boolean isRed) {
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return 0;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int rank = GameState.rank(guardPos);
        int file = GameState.file(guardPos);
        int score = 0;

        // Basic advancement
        int advancement = isRed ? (6 - rank) : rank;
        score += advancement * GUARD_ADVANCEMENT_BONUS;

        // D-file preference
        int fileDistance = Math.abs(file - 3);
        score += Math.max(0, 3 - fileDistance) * 15;

        // Distance to enemy castle
        int targetCastle = isRed ? RED_CASTLE : BLUE_CASTLE;
        int distance = calculateDistance(guardPos, targetCastle);
        score += Math.max(0, 10 - distance) * 5;

        return score;
    }

    private int evaluateGuardSafety(GameState state, boolean isRed) {
        int safetyScore = 0;

        if (isGuardInDanger(state, isRed)) {
            safetyScore -= 150;
        } else {
            // Bonus for safe guards with escape routes
            int escapeRoutes = countGuardEscapeRoutes(state, isRed);
            safetyScore += Math.min(escapeRoutes * 20, 60);
        }

        return safetyScore;
    }

    // === TACTICAL EVALUATION - CONTROLLED ===
    private int evaluateTacticalThreats(GameState state) {
        int threatScore = 0;

        try {
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            for (Move move : moves.subList(0, Math.min(10, moves.size()))) { // Limit to avoid timeout
                if (isCapture(move, state)) {
                    int captureValue = getCaptureValue(move, state);
                    threatScore += state.redToMove ? captureValue / 4 : -captureValue / 4;
                }
            }
        } catch (Exception e) {
            return 0;
        }

        return threatScore;
    }

    // === MOBILITY EVALUATION ===
    private int evaluateMobility(GameState state) {
        try {
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            int mobilityBonus = Math.min(moves.size() * MOBILITY_BONUS / 2, 100); // Cap mobility bonus
            return state.redToMove ? mobilityBonus : -mobilityBonus;
        } catch (Exception e) {
            return 0;
        }
    }

    // === POSITIONAL FACTORS ===
    private int evaluatePositionalFactors(GameState state) {
        int positionalScore = 0;

        // Central control
        for (int square : CENTRAL_SQUARES) {
            int control = evaluateSquareControl(state, square);
            positionalScore += control * 5;
        }

        // Piece coordination
        positionalScore += evaluatePieceCoordination(state);

        return positionalScore;
    }

    private int evaluateSquareControl(GameState state, int square) {
        int redControl = 0, blueControl = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                if (canInfluenceSquare(i, square, state.redStackHeights[i])) {
                    redControl += 1;
                }
            }
            if (state.blueStackHeights[i] > 0) {
                if (canInfluenceSquare(i, square, state.blueStackHeights[i])) {
                    blueControl += 1;
                }
            }
        }

        return redControl - blueControl;
    }

    private int evaluatePieceCoordination(GameState state) {
        int coordinationScore = 0;

        // Count adjacent pieces
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                coordinationScore += countAdjacentFriendly(state, i, true) * COORDINATION_BONUS;
            }
            if (state.blueStackHeights[i] > 0) {
                coordinationScore -= countAdjacentFriendly(state, i, false) * COORDINATION_BONUS;
            }
        }

        return coordinationScore;
    }

    // === HELPER METHODS ===

    private int calculateDistance(int from, int to) {
        int fromRank = GameState.rank(from);
        int fromFile = GameState.file(from);
        int toRank = GameState.rank(to);
        int toFile = GameState.file(to);
        return Math.abs(fromRank - toRank) + Math.abs(fromFile - toFile);
    }

    private boolean canInfluenceSquare(int from, int to, int range) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));
        if (rankDiff != 0 && fileDiff != 0) return false;
        return Math.max(rankDiff, fileDiff) <= range;
    }

    public boolean isGuardInDanger(GameState state, boolean checkRed) {
        if (state == null) return false;
        long guardBit = checkRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        long enemyPieces = checkRed ? (state.blueTowers | state.blueGuard) : (state.redTowers | state.redGuard);

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((enemyPieces & GameState.bit(i)) != 0) {
                int height = checkRed ? state.blueStackHeights[i] : state.redStackHeights[i];
                if (height == 0 && (enemyPieces & GameState.bit(i)) != 0) height = 1; // Guard
                if (canAttackSquare(i, guardPos, height)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canAttackSquare(int from, int to, int range) {
        if (range <= 0) return false;
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));
        if (rankDiff != 0 && fileDiff != 0) return false;
        int distance = Math.max(rankDiff, fileDiff);
        return distance <= range;
    }

    private int countGuardEscapeRoutes(GameState state, boolean isRed) {
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return 0;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int escapeRoutes = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int target = guardPos + dir;
            if (GameState.isOnBoard(target) && !isRankWrap(guardPos, target, dir)) {
                if (!isOccupiedByFriendly(target, state, isRed)) {
                    escapeRoutes++;
                }
            }
        }
        return escapeRoutes;
    }

    private int countAdjacentFriendly(GameState state, int square, boolean isRed) {
        int count = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int adjacent = square + dir;
            if (!GameState.isOnBoard(adjacent)) continue;
            if (isRankWrap(square, adjacent, dir)) continue;

            long adjBit = GameState.bit(adjacent);
            if (isRed && ((state.redTowers | state.redGuard) & adjBit) != 0) {
                count++;
            } else if (!isRed && ((state.blueTowers | state.blueGuard) & adjBit) != 0) {
                count++;
            }
        }

        return count;
    }

    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private int getCaptureValue(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
            return 800; // Reduced from 1500
        }

        int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
        return height * 80; // Reduced from 100
    }

    private boolean isCentralSquare(int square) {
        for (int central : CENTRAL_SQUARES) {
            if (square == central) return true;
        }
        return false;
    }

    private boolean isOccupiedByFriendly(int square, GameState state, boolean isRed) {
        long bit = GameState.bit(square);
        if (isRed) {
            return (state.redGuard & bit) != 0 || (state.redTowers & bit) != 0;
        } else {
            return (state.blueGuard & bit) != 0 || (state.blueTowers & bit) != 0;
        }
    }

    private boolean isRankWrap(int from, int to, int direction) {
        if (Math.abs(direction) == 1) {
            return GameState.rank(from) != GameState.rank(to);
        }
        return false;
    }

    // === TIME MANAGEMENT ===
    public static void setRemainingTime(long timeMs) {
        remainingTimeMs = Math.max(0, timeMs);
        emergencyMode = timeMs < 1000;
    }

    public static long getRemainingTime() {
        return remainingTimeMs;
    }

    public static boolean isEmergencyMode() {
        return emergencyMode;
    }
}
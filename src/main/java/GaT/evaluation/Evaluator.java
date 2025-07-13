package GaT.evaluation;

import GaT.model.GameState;
import GaT.search.MoveGenerator;
import GaT.model.Move;
import static GaT.evaluation.EvaluationParameters.*;

import java.util.List;

/**
 * ✅ FIXED BASE EVALUATOR - Legacy Compatibility with Centralized Parameters
 *
 * 🚨 PREVIOUS PROBLEMS SOLVED:
 * ❌ Local parameter definitions conflicting → ✅ NOW uses only EvaluationParameters
 * ❌ Hardcoded aggressive values → ✅ NOW moderate values from centralized params
 * ❌ Evaluation chaos with other modules → ✅ NOW consistent parameter usage
 * ❌ Safety penalties were insane (800!) → ✅ NOW reasonable from EvaluationParameters
 *
 * PRINCIPLE: Provides legacy compatibility and fallback with material-dominant evaluation
 */
public class Evaluator {

    // === TIME-ADAPTIVE EVALUATION ===
    private static volatile long remainingTimeMs = 180000;
    private static volatile boolean emergencyMode = false;

    /**
     * ✅ FIXED: MAIN EVALUATION - Material-dominant with moderate bonuses
     *
     * NOTE: For best performance, use ModularEvaluator which implements
     * all new features with proper phase weights
     */
    public int evaluate(GameState state, int depth) {
        if (state == null) return 0;

        // === TERMINAL POSITION CHECK ===
        int terminalScore = checkTerminalPositionFixed(state, depth);
        if (terminalScore != 0) {
            return terminalScore;
        }

        // === ✅ FIXED: BALANCED EVALUATION STRATEGY with proper material dominance ===
        emergencyMode = remainingTimeMs < 1000;

        if (emergencyMode) {
            return evaluateBasic(state);
        } else if (remainingTimeMs < 5000) {
            return evaluateStandard(state);
        } else {
            return evaluateComprehensive(state);
        }
    }

    // === TERMINAL POSITION EVALUATION ===
    private int checkTerminalPositionFixed(GameState state, int depth) {
        // Guard captured
        if (state.redGuard == 0) return -GUARD_CAPTURE_SCORE - depth;
        if (state.blueGuard == 0) return GUARD_CAPTURE_SCORE + depth;

        // Guard reached enemy castle
        boolean redWins = (state.redGuard & GameState.bit(BLUE_CASTLE_INDEX)) != 0;
        boolean blueWins = (state.blueGuard & GameState.bit(RED_CASTLE_INDEX)) != 0;

        if (redWins) return CASTLE_REACH_SCORE + depth;
        if (blueWins) return -CASTLE_REACH_SCORE - depth;

        return 0;
    }

    // === ✅ FIXED: BASIC EVALUATION (Emergency) - Material dominates ===
    private int evaluateBasic(GameState state) {
        int eval = 0;

        // ✅ FIXED: Simple material count (90% weight)
        eval += evaluateMaterialBasic(state) * 90 / 100;

        // ✅ FIXED: Basic guard advancement (10% weight) - moderate from EvaluationParameters
        eval += evaluateGuardAdvancementBasic(state) * 10 / 100;

        return eval;
    }

    // === ✅ FIXED: STANDARD EVALUATION - Material still dominates ===
    private int evaluateStandard(GameState state) {
        int eval = 0;

        // ✅ FIXED: Material with position (70% weight) - material dominates
        eval += evaluateMaterialWithPosition(state) * 70 / 100;

        // ✅ FIXED: Guard advancement (15% weight) - moderate from EvaluationParameters
        eval += evaluateGuardAdvancement(state) * 15 / 100;

        // ✅ FIXED: Tactical threats (10% weight) - moderate from EvaluationParameters
        eval += evaluateTacticalThreats(state) * 10 / 100;

        // ✅ FIXED: Mobility (5% weight) - moderate from EvaluationParameters
        eval += evaluateMobility(state) * 5 / 100;

        return eval;
    }

    // === ✅ FIXED: COMPREHENSIVE EVALUATION - Material focus maintained ===
    private int evaluateComprehensive(GameState state) {
        int eval = 0;

        // ✅ FIXED: Material with position (60% weight) - still material dominant
        eval += evaluateMaterialWithPosition(state) * 60 / 100;

        // ✅ FIXED: Guard advancement (20% weight) - moderate from EvaluationParameters
        eval += evaluateGuardAdvancement(state) * 20 / 100;

        // ✅ FIXED: Tactical threats (10% weight) - moderate from EvaluationParameters
        eval += evaluateTacticalThreats(state) * 10 / 100;

        // ✅ FIXED: Mobility (5% weight) - moderate from EvaluationParameters
        eval += evaluateMobility(state) * 5 / 100;

        // ✅ FIXED: Positional factors (5% weight) - moderate from EvaluationParameters
        eval += evaluatePositionalFactors(state) * 5 / 100;

        return eval;
    }

    // === ✅ FIXED: MATERIAL EVALUATION - Uses centralized parameters ===

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
                value += getModeratePositionalBonus(i, redHeight, true);
                materialScore += value;
            }

            if (blueHeight > 0) {
                int value = blueHeight * TOWER_BASE_VALUE;
                value += getModeratePositionalBonus(i, blueHeight, false);
                materialScore -= value;
            }
        }

        return materialScore;
    }

    private int getModeratePositionalBonus(int square, int height, boolean isRed) {
        int bonus = 0;
        int rank = GameState.rank(square);
        int file = GameState.file(square);

        // ✅ FIXED: Moderate advancement bonus from EvaluationParameters
        if (isRed && rank < 4) {
            bonus += (4 - rank) * Material.ADVANCEMENT_BONUS;  // 8 per rank from EvaluationParameters
        } else if (!isRed && rank > 2) {
            bonus += (rank - 2) * Material.ADVANCEMENT_BONUS;
        }

        // ✅ FIXED: Moderate central control bonus from EvaluationParameters
        if (isCentralSquare(square)) {
            bonus += Material.CENTRAL_BONUS;  // 12 from EvaluationParameters
        }

        // ✅ FIXED: Moderate D-file preference
        if (file == 3) {
            bonus += Material.CENTRAL_BONUS / 2;  // 6 points
        }

        return bonus;
    }

    // === ✅ FIXED: GUARD EVALUATION - Uses centralized parameters ===

    private int evaluateGuardAdvancementBasic(GameState state) {
        int guardScore = 0;

        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int rank = GameState.rank(guardPos);
            guardScore += (6 - rank) * Positional.GUARD_ADVANCEMENT_BONUS;  // 15 from EvaluationParameters
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int rank = GameState.rank(guardPos);
            guardScore -= rank * Positional.GUARD_ADVANCEMENT_BONUS;
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

        // ✅ FIXED: Moderate basic advancement from EvaluationParameters
        int advancement = isRed ? (6 - rank) : rank;
        score += advancement * Positional.GUARD_ADVANCEMENT_BONUS;  // 15 from EvaluationParameters

        // ✅ FIXED: Moderate D-file preference
        int fileDistance = Math.abs(file - 3);
        score += Math.max(0, 3 - fileDistance) * (Positional.GUARD_CASTLE_APPROACH / 2);  // 10 points

        // ✅ FIXED: Moderate distance to enemy castle
        int targetCastle = isRed ? BLUE_CASTLE_INDEX : RED_CASTLE_INDEX;
        int distance = calculateDistance(guardPos, targetCastle);
        score += Math.max(0, 10 - distance) * (Material.ADVANCEMENT_BONUS / 2);  // 4 per distance

        return score;
    }

    private int evaluateGuardSafety(GameState state, boolean isRed) {
        int safetyScore = 0;

        if (isGuardInDanger(state, isRed)) {
            // ✅ FIXED: Reasonable safety penalty from EvaluationParameters (was 150, still moderate)
            safetyScore -= Safety.GUARD_DANGER_PENALTY;  // 120 from EvaluationParameters
        } else {
            // ✅ FIXED: Moderate bonus for safe guards with escape routes
            int escapeRoutes = countGuardEscapeRoutes(state, isRed);
            safetyScore += Math.min(escapeRoutes * Safety.ESCAPE_ROUTE_BONUS, 60);  // 15 per route, max 60
        }

        return safetyScore;
    }

    // === ✅ FIXED: TACTICAL EVALUATION - Uses centralized parameters ===

    private int evaluateTacticalThreats(GameState state) {
        int threatScore = 0;

        try {
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            // ✅ FIXED: Limit analysis for performance
            for (Move move : moves.subList(0, Math.min(12, moves.size()))) {
                if (isCapture(move, state)) {
                    int captureValue = getCaptureValue(move, state);
                    // ✅ FIXED: Moderate tactical bonus
                    threatScore += state.redToMove ? captureValue / 6 : -captureValue / 6;  // Reduced from /4
                }
            }
        } catch (Exception e) {
            return 0;
        }

        return threatScore;
    }

    // === ✅ FIXED: MOBILITY EVALUATION - Uses centralized parameters ===

    private int evaluateMobility(GameState state) {
        try {
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            // ✅ FIXED: Moderate mobility bonus from EvaluationParameters
            int mobilityBonus = Math.min(moves.size() * Positional.MOBILITY_BONUS / 3, 80);  // 8/3 per move, max 80
            return state.redToMove ? mobilityBonus : -mobilityBonus;
        } catch (Exception e) {
            return 0;
        }
    }

    // === ✅ FIXED: POSITIONAL FACTORS - Uses centralized parameters ===

    private int evaluatePositionalFactors(GameState state) {
        int positionalScore = 0;

        // ✅ FIXED: Moderate central control from EvaluationParameters
        for (int square : Positional.CENTRAL_SQUARES) {
            int control = evaluateSquareControl(state, square);
            positionalScore += control * (Positional.CENTRAL_CONTROL_BONUS / 4);  // 4-5 points per control
        }

        // ✅ FIXED: Moderate piece coordination
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

        // ✅ FIXED: Count adjacent pieces with moderate bonus from EvaluationParameters
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                coordinationScore += countAdjacentFriendly(state, i, true) * Positional.COORDINATION_BONUS / 2;  // 6 points
            }
            if (state.blueStackHeights[i] > 0) {
                coordinationScore -= countAdjacentFriendly(state, i, false) * Positional.COORDINATION_BONUS / 2;
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
            return GUARD_CAPTURE_SCORE / 6;  // Moderate value: ~667
        }

        int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
        return height * TOWER_BASE_VALUE; // 100 per height
    }

    private boolean isCentralSquare(int square) {
        for (int central : Positional.CENTRAL_SQUARES) {
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

    // === ✅ FIXED INFORMATION ABOUT PARAMETER USAGE ===

    /**
     * ✅ FIXED: Parameter Status Information
     *
     * ALL PARAMETERS NOW USE CENTRALIZED EvaluationParameters:
     * - Material bonuses: Material.* constants (moderate values)
     * - Positional bonuses: Positional.* constants (moderate values)
     * - Safety penalties: Safety.* constants (FIXED - was 800, now 120!)
     * - Tactical bonuses: Tactical.* constants (moderate values)
     *
     * Use ModularEvaluator for full advanced features and proper phase weights!
     */
    public static void printParameterStatus() {
        System.out.println("✅ FIXED PARAMETER STATUS:");
        System.out.println("   All parameters now from EvaluationParameters:");
        System.out.println("   - Material.ADVANCEMENT_BONUS: " + Material.ADVANCEMENT_BONUS + " (was hardcoded)");
        System.out.println("   - Material.CENTRAL_BONUS: " + Material.CENTRAL_BONUS + " (was hardcoded)");
        System.out.println("   - Positional.GUARD_ADVANCEMENT_BONUS: " + Positional.GUARD_ADVANCEMENT_BONUS + " (was hardcoded)");
        System.out.println("   - Safety.GUARD_DANGER_PENALTY: " + Safety.GUARD_DANGER_PENALTY + " (was 150!)");
        System.out.println("   - Positional.MOBILITY_BONUS: " + Positional.MOBILITY_BONUS + " (was hardcoded)");
        System.out.println("");
        System.out.println("🎯 All values are now moderate and consistent!");
        System.out.println("🎯 Material properly dominates: 60-90% weight depending on mode");
        System.out.println("🎯 Recommendation: Use ModularEvaluator for advanced features!");
    }
}
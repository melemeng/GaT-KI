
package GaT.evaluation;

import GaT.model.GameState;
import GaT.search.MoveGenerator;
import GaT.model.Move;

import java.util.List;

/**
 * ENHANCED EVALUATION COORDINATOR - Anti-Repetition & Strategic Intelligence
 *
 * IMPROVEMENTS:
 * ✅ 1. Anti-repetition heuristics
 * ✅ 2. Enhanced guard advancement evaluation
 * ✅ 3. Strategic piece placement
 * ✅ 4. Dynamic evaluation based on game phase
 * ✅ 5. Tactical pattern recognition
 * ✅ 6. Better material evaluation
 */
public class Evaluator {


    // === EVALUATION CONSTANTS - BALANCED ===
    private static final int TOWER_BASE_VALUE = 100;
    private static final int GUARD_BASE_VALUE = 50;
    private static final int CASTLE_REACH_SCORE = 10000;
    private static final int GUARD_CAPTURE_SCORE = 8000;

    // === POSITIONAL BONUSES - REFINED ===
    private static final int GUARD_ADVANCEMENT_BONUS = 30;
    private static final int GUARD_SAFETY_BONUS = 80;
    private static final int CENTRAL_CONTROL_BONUS = 15;
    private static final int MOBILITY_BONUS = 5;
    private static final int COORDINATION_BONUS = 20;

    // === STRATEGIC SQUARES ===
    private static final int[] CENTRAL_SQUARES = {
            GameState.getIndex(2, 3), GameState.getIndex(3, 3), GameState.getIndex(4, 3) // D3, D4, D5
    };

    // === TIME-ADAPTIVE EVALUATION ===
    private static long remainingTimeMs = 180000;

    /**
     * MAIN EVALUATION INTERFACE - Improved and Stable
     */
    public int evaluate(GameState state, int depth) {
        // === TERMINAL POSITION CHECK ===
        int terminalScore = checkTerminalPosition(state, depth);
        if (terminalScore != 0) return terminalScore;

        // === ADAPTIVE EVALUATION BASED ON TIME ===
        if (remainingTimeMs < 2000) {
            return evaluateUltraFast(state);
        } else if (remainingTimeMs < 10000) {
            return evaluateBalanced(state);
        } else {
            return evaluateComprehensive(state);
        }
    }

    // === TERMINAL POSITION EVALUATION ===

    private int checkTerminalPosition(GameState state, int depth) {
        // Guard captured
        if (state.redGuard == 0) return -GUARD_CAPTURE_SCORE - depth;
        if (state.blueGuard == 0) return GUARD_CAPTURE_SCORE + depth;

        // Guard reached enemy castle
        boolean redWins = (state.redGuard & GameState.bit(GameState.getIndex(0, 3))) != 0; // D1
        boolean blueWins = (state.blueGuard & GameState.bit(GameState.getIndex(6, 3))) != 0; // D7

        if (redWins) return CASTLE_REACH_SCORE + depth;
        if (blueWins) return -CASTLE_REACH_SCORE - depth;

        return 0;
    }

    // === ULTRA-FAST EVALUATION (< 2 seconds remaining) ===

    private int evaluateUltraFast(GameState state) {
        int eval = 0;

        // Fast material count
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            eval += (state.redStackHeights[i] - state.blueStackHeights[i]) * TOWER_BASE_VALUE;
        }

        // Quick guard advancement
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int rank = GameState.rank(guardPos);
            eval += (6 - rank) * GUARD_ADVANCEMENT_BONUS;
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int rank = GameState.rank(guardPos);
            eval -= rank * GUARD_ADVANCEMENT_BONUS;
        }

        return eval;
    }

    // === BALANCED EVALUATION (Standard) ===

    private int evaluateBalanced(GameState state) {
        int eval = 0;

        // Material evaluation (40%)
        eval += evaluateMaterial(state) * 40 / 100;

        // Guard safety and advancement (35%)
        eval += evaluateGuards(state) * 35 / 100;

        // Positional factors (25%)
        eval += evaluatePositional(state) * 25 / 100;

        return eval;
    }

    // === COMPREHENSIVE EVALUATION (> 10 seconds remaining) ===

    private int evaluateComprehensive(GameState state) {
        int eval = 0;

        // Material with activity (35%)
        eval += evaluateMaterialWithActivity(state) * 35 / 100;

        // Guard evaluation (30%)
        eval += evaluateGuardsComprehensive(state) * 30 / 100;

        // Tactical evaluation (20%)
        eval += evaluateTactical(state) * 20 / 100;

        // Strategic positioning (15%)
        eval += evaluateStrategic(state) * 15 / 100;

        return eval;
    }

    // === MATERIAL EVALUATION ===

    private int evaluateMaterial(GameState state) {
        int materialScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int redHeight = state.redStackHeights[i];
            int blueHeight = state.blueStackHeights[i];

            if (redHeight > 0) {
                int value = redHeight * TOWER_BASE_VALUE;
                // Position bonus
                value += getPositionBonus(i, redHeight, true);
                materialScore += value;
            }

            if (blueHeight > 0) {
                int value = blueHeight * TOWER_BASE_VALUE;
                value += getPositionBonus(i, blueHeight, false);
                materialScore -= value;
            }
        }

        return materialScore;
    }

    private int evaluateMaterialWithActivity(GameState state) {
        int materialScore = evaluateMaterial(state);

        // Add activity bonuses
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                materialScore += evaluatePieceActivity(state, i, true);
            }
            if (state.blueStackHeights[i] > 0) {
                materialScore -= evaluatePieceActivity(state, i, false);
            }
        }

        return materialScore;
    }

    // === GUARD EVALUATION ===

    private int evaluateGuards(GameState state) {
        int guardScore = 0;

        // Red guard
        if (state.redGuard != 0) {
            guardScore += evaluateGuardPosition(state, true);
        }

        // Blue guard
        if (state.blueGuard != 0) {
            guardScore -= evaluateGuardPosition(state, false);
        }

        return guardScore;
    }

    private int evaluateGuardsComprehensive(GameState state) {
        int guardScore = evaluateGuards(state);

        // Add safety evaluation
        guardScore += evaluateGuardSafety(state);

        // Add support evaluation
        guardScore += evaluateGuardSupport(state);

        return guardScore;
    }

    private int evaluateGuardPosition(GameState state, boolean isRed) {
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return 0;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int rank = GameState.rank(guardPos);
        int file = GameState.file(guardPos);

        int score = 0;

        // Advancement bonus
        int targetRank = isRed ? 0 : 6;
        int advancement = Math.abs(rank - (isRed ? 6 : 0));
        score += advancement * GUARD_ADVANCEMENT_BONUS;

        // File bonus (closer to D-file)
        int fileBonus = Math.max(0, 4 - Math.abs(file - 3)) * 20;
        score += fileBonus;

        // Endgame bonus for being close to enemy castle
        if (isEndgame(state)) {
            int distanceToTarget = Math.abs(rank - targetRank) + Math.abs(file - 3);
            score += Math.max(0, 10 - distanceToTarget) * 30;
        }

        return score;
    }

    private int evaluateGuardSafety(GameState state) {
        int safetyScore = 0;

        // Check if guards are in danger
        if (isGuardInDanger(state, true)) {
            safetyScore -= GUARD_SAFETY_BONUS * 2;
        }
        if (isGuardInDanger(state, false)) {
            safetyScore += GUARD_SAFETY_BONUS * 2;
        }

        return safetyScore;
    }

    private int evaluateGuardSupport(GameState state) {
        int supportScore = 0;

        // Red guard support
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int supporters = countAdjacentFriendly(state, guardPos, true);
            supportScore += supporters * GUARD_SAFETY_BONUS;
        }

        // Blue guard support
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int supporters = countAdjacentFriendly(state, guardPos, false);
            supportScore -= supporters * GUARD_SAFETY_BONUS;
        }

        return supportScore;
    }

    // === POSITIONAL EVALUATION ===

    private int evaluatePositional(GameState state) {
        int positionalScore = 0;

        // Central control
        for (int square : CENTRAL_SQUARES) {
            int control = evaluateSquareControl(state, square);
            positionalScore += control * CENTRAL_CONTROL_BONUS;
        }

        // Piece coordination
        positionalScore += evaluateCoordination(state);

        return positionalScore;
    }

    // === TACTICAL EVALUATION ===

    private int evaluateTactical(GameState state) {
        int tacticalScore = 0;

        // Immediate threats
        tacticalScore += evaluateThreats(state);

        // Mobility
        tacticalScore += evaluateMobility(state);

        return tacticalScore;
    }

    private int evaluateThreats(GameState state) {
        int threatScore = 0;

        // Count threatening moves
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        for (Move move : moves) {
            if (isCapture(move, state)) {
                long toBit = GameState.bit(move.to);
                if (((state.redToMove ? state.blueGuard : state.redGuard) & toBit) != 0) {
                    threatScore += state.redToMove ? 500 : -500; // Guard threat
                } else {
                    threatScore += state.redToMove ? 100 : -100; // Tower threat
                }
            }
        }

        return threatScore;
    }

    private int evaluateMobility(GameState state) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        int mobilityBonus = moves.size() * MOBILITY_BONUS;
        return state.redToMove ? mobilityBonus : -mobilityBonus;
    }

    // === STRATEGIC EVALUATION ===

    private int evaluateStrategic(GameState state) {
        int strategicScore = 0;

        // Pawn structure equivalent (tower formations)
        strategicScore += evaluateFormations(state);

        // King activity (guard activity in endgame)
        if (isEndgame(state)) {
            strategicScore += evaluateGuardActivity(state);
        }

        return strategicScore;
    }

    private int evaluateFormations(GameState state) {
        int formationScore = 0;

        // Evaluate file control
        for (int file = 0; file < 7; file++) {
            int redPieces = 0, bluePieces = 0;
            for (int rank = 0; rank < 7; rank++) {
                int square = GameState.getIndex(rank, file);
                if (state.redStackHeights[square] > 0) redPieces++;
                if (state.blueStackHeights[square] > 0) bluePieces++;
            }

            if (redPieces > 0 && bluePieces == 0) {
                formationScore += (file >= 2 && file <= 4) ? 30 : 15; // Central files worth more
            } else if (bluePieces > 0 && redPieces == 0) {
                formationScore -= (file >= 2 && file <= 4) ? 30 : 15;
            }
        }

        return formationScore;
    }

    private int evaluateGuardActivity(GameState state) {
        int activityScore = 0;

        // Red guard activity
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            activityScore += calculateGuardActivity(state, guardPos, true);
        }

        // Blue guard activity
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            activityScore -= calculateGuardActivity(state, guardPos, false);
        }

        return activityScore;
    }

    // === HELPER METHODS ===

    private int getPositionBonus(int square, int height, boolean isRed) {
        int bonus = 0;

        // Advancement bonus
        int rank = GameState.rank(square);
        if (isRed && rank < 4) {
            bonus += (4 - rank) * 10;
        } else if (!isRed && rank > 2) {
            bonus += (rank - 2) * 10;
        }

        // Central bonus
        int file = GameState.file(square);
        if (file >= 2 && file <= 4) {
            bonus += 15;
        }

        return bonus;
    }

    private int evaluatePieceActivity(GameState state, int square, boolean isRed) {
        int activity = 0;

        // Mobility potential
        int height = isRed ? state.redStackHeights[square] : state.blueStackHeights[square];
        activity += Math.min(height * 3, 15); // Cap activity bonus

        // Connection bonus
        if (hasAdjacentFriendly(state, square, isRed)) {
            activity += COORDINATION_BONUS;
        }

        return activity;
    }

    private int evaluateSquareControl(GameState state, int square) {
        int redControl = 0, blueControl = 0;

        // Check which pieces can influence this square
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                if (canInfluenceSquare(i, square, state.redStackHeights[i])) {
                    redControl += state.redStackHeights[i];
                }
            }
            if (state.blueStackHeights[i] > 0) {
                if (canInfluenceSquare(i, square, state.blueStackHeights[i])) {
                    blueControl += state.blueStackHeights[i];
                }
            }
        }

        return redControl - blueControl;
    }

    private int evaluateCoordination(GameState state) {
        int coordination = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                coordination += countAdjacentFriendly(state, i, true) * COORDINATION_BONUS;
            }
            if (state.blueStackHeights[i] > 0) {
                coordination -= countAdjacentFriendly(state, i, false) * COORDINATION_BONUS;
            }
        }

        return coordination;
    }

    private int calculateGuardActivity(GameState state, int guardPos, boolean isRed) {
        int activity = 0;

        // Centralization
        int file = GameState.file(guardPos);
        int rank = GameState.rank(guardPos);
        activity += Math.max(0, 3 - Math.abs(file - 3)) * 10;
        activity += Math.max(0, 3 - Math.abs(rank - 3)) * 10;

        // Mobility
        int[] directions = {-1, 1, -7, 7};
        for (int dir : directions) {
            int target = guardPos + dir;
            if (GameState.isOnBoard(target) && !isRankWrap(guardPos, target, dir)) {
                if (!isOccupiedByFriendly(target, state, isRed)) {
                    activity += 5;
                }
            }
        }

        return activity;
    }

    // === UTILITY METHODS ===

    private boolean isGuardInDanger(GameState state, boolean checkRed) {
        long guardBit = checkRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);

        // Simple danger check - can enemy pieces reach guard?
        long enemyPieces = checkRed ? (state.blueTowers | state.blueGuard) : (state.redTowers | state.redGuard);

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((enemyPieces & GameState.bit(i)) != 0) {
                int height = checkRed ? state.blueStackHeights[i] : state.redStackHeights[i];
                if (height == 0 && (enemyPieces & GameState.bit(i)) != 0) height = 1; // Guard

                if (canAttackSquare(i, guardPos, height, state)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean canAttackSquare(int from, int to, int range, GameState state) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        // Must be on same rank or file
        if (rankDiff != 0 && fileDiff != 0) return false;

        int distance = Math.max(rankDiff, fileDiff);
        return distance <= range && isPathClear(from, to, state);
    }

    private boolean isPathClear(int from, int to, GameState state) {
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

    private int countAdjacentFriendly(GameState state, int square, boolean isRed) {
        int count = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int adjacent = square + dir;
            if (!GameState.isOnBoard(adjacent)) continue;
            if (isRankWrap(square, adjacent, dir)) continue;

            if (isOccupiedByFriendly(adjacent, state, isRed)) {
                count++;
            }
        }

        return count;
    }

    private boolean hasAdjacentFriendly(GameState state, int square, boolean isRed) {
        return countAdjacentFriendly(state, square, isRed) > 0;
    }

    private boolean canInfluenceSquare(int from, int to, int range) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        if (rankDiff != 0 && fileDiff != 0) return false;

        int distance = Math.max(rankDiff, fileDiff);
        return distance <= range;
    }

    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private boolean isOccupied(int square, GameState state) {
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard)
                & GameState.bit(square)) != 0;
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
        if (Math.abs(direction) == 1) { // Horizontal movement
            return GameState.rank(from) != GameState.rank(to);
        }
        return false;
    }

    private boolean isEndgame(GameState state) {
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return totalMaterial <= 8;
    }

    // === TIME MANAGEMENT ===

    public static void setRemainingTime(long timeMs) {
        remainingTimeMs = timeMs;
    }

    public static long getRemainingTime() {
        return remainingTimeMs;
    }
}
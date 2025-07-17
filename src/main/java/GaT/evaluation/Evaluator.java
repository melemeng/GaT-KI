package GaT.evaluation;

import GaT.game.GameState;

/**
 * OPTIMIZED EVALUATOR for Guard & Towers
 *
 * Fixed version of your original evaluator:
 * ✅ Removed move generation performance bug
 * ✅ Simplified weighted calculations
 * ✅ Kept all strategic understanding
 * ✅ Fast single-pass evaluation
 * ✅ Game-specific optimizations
 *
 * Strategic Elements:
 * 1. Material (towers and position)
 * 2. Guard advancement toward enemy castle
 * 3. Guard safety (threat detection)
 * 4. Piece activity (mobility and coordination)
 */
public class Evaluator {

    // === CORE VALUES ===
    private static final int TOWER_VALUE = 100;
    private static final int GUARD_CAPTURE = 10000;
    private static final int CASTLE_REACH = 10000;

    // === CASTLE POSITIONS ===
    private static final int RED_CASTLE = 45;   // D7 (6*7 + 3)
    private static final int BLUE_CASTLE = 3;   // D1 (0*7 + 3)

    // === EVALUATION WEIGHTS ===
    private static final int ADVANCEMENT_BONUS = 8;       // Per rank advanced
    private static final int D_FILE_BONUS = 12;           // For controlling D-file
    private static final int CENTRAL_BONUS = 4;           // For central files
    private static final int GUARD_DISTANCE_BONUS = 60;   // Per step closer to castle
    private static final int D_FILE_GUARD_BONUS = 120;    // Guard on D-file
    private static final int CASTLE_PROXIMITY_BONUS = 250; // Very close to castle
    private static final int SAFETY_PENALTY = 600;        // When guard threatened
    private static final int ESCAPE_ROUTE_BONUS = 25;     // Per escape route
    private static final int MOBILITY_BONUS = 3;          // Per mobility direction
    private static final int COORDINATION_BONUS = 8;      // Per adjacent friendly
    private static final int HEIGHT_BONUS = 5;            // Bonus for tall towers

    /**
     * Main evaluation function - fast single-pass
     */
    public int evaluate(GameState state) {
        // Check for terminal positions first
        int terminalScore = checkTerminal(state);
        if (terminalScore != 0) return terminalScore;

        int score = 0;

        // Single-pass evaluation combining all factors
        for (int square = 0; square < 49; square++) {
            int rank = square / 7;
            int file = square % 7;

            // Red towers
            if (state.redStackHeights[square] > 0) {
                int height = state.redStackHeights[square];
                score += evaluateTower(height, rank, file, true);
            }

            // Blue towers
            if (state.blueStackHeights[square] > 0) {
                int height = state.blueStackHeights[square];
                score -= evaluateTower(height, rank, file, false);
            }
        }

        // Guard evaluation
        score += evaluateGuards(state);

        return score;
    }

    /**
     * Evaluate a single tower with all factors combined
     */
    private int evaluateTower(int height, int rank, int file, boolean isRed) {
        int score = height * TOWER_VALUE;

        // Position bonuses
        score += getPositionBonus(rank, file, height, isRed);

        // Height bonus (taller towers are more valuable)
        if (height >= 3) score += HEIGHT_BONUS * height;

        return score;
    }

    /**
     * Combined position bonus for towers
     */
    private int getPositionBonus(int rank, int file, int height, boolean isRed) {
        int bonus = 0;

        // Advancement bonus
        if (isRed && rank < 3) {
            bonus += (3 - rank) * height * ADVANCEMENT_BONUS;
        } else if (!isRed && rank > 3) {
            bonus += (rank - 3) * height * ADVANCEMENT_BONUS;
        }

        // D-file control (critical for guard advancement)
        if (file == 3) {
            bonus += height * D_FILE_BONUS;
        }

        // Central files (C, D, E)
        if (file >= 2 && file <= 4) {
            bonus += height * CENTRAL_BONUS;
        }

        return bonus;
    }

    /**
     * Evaluate both guards
     */
    private int evaluateGuards(GameState state) {
        int score = 0;

        // Red guard
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            score += evaluateGuardPosition(guardPos, BLUE_CASTLE, true);
            score += evaluateGuardSafety(state, guardPos, true);
        }

        // Blue guard
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            score -= evaluateGuardPosition(guardPos, RED_CASTLE, false);
            score -= evaluateGuardSafety(state, guardPos, false);
        }

        return score;
    }

    /**
     * Evaluate guard advancement toward enemy castle
     */
    private int evaluateGuardPosition(int guardPos, int targetCastle, boolean isRed) {
        int guardRank = guardPos / 7;
        int guardFile = guardPos % 7;
        int targetRank = targetCastle / 7;
        int targetFile = targetCastle % 7;

        // Manhattan distance to target
        int distance = Math.abs(guardRank - targetRank) + Math.abs(guardFile - targetFile);
        int maxDistance = 12;

        int score = (maxDistance - distance) * GUARD_DISTANCE_BONUS;

        // Big bonus for D-file (direct path to castle)
        if (guardFile == 3) {
            score += D_FILE_GUARD_BONUS;
        }

        // Extra bonus for being very close
        if (distance <= 2) {
            score += CASTLE_PROXIMITY_BONUS;
        }

        // Small penalty for being far from D-file
        int fileDistance = Math.abs(guardFile - 3);
        score -= fileDistance * 15;

        return score;
    }

    /**
     * Evaluate guard safety
     */
    private int evaluateGuardSafety(GameState state, int guardPos, boolean isRed) {
        int score = 0;

        // Check if guard is threatened
        if (isGuardThreatened(state, isRed)) {
            score -= SAFETY_PENALTY;
        }

        // Count escape routes
        score += countEscapeRoutes(state, guardPos, isRed) * ESCAPE_ROUTE_BONUS;

        // Basic mobility (approximate, no move generation!)
        score += approximateMobility(state, guardPos, isRed) * MOBILITY_BONUS;

        // Coordination with nearby towers
        score += countNearbyFriendlyTowers(state, guardPos, isRed) * COORDINATION_BONUS;

        return score;
    }

    /**
     * Check if guard is under immediate attack
     */
    public boolean isGuardThreatened(GameState state, boolean redGuard) {
        long guardBit = redGuard ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);

        // Check if any enemy tower can attack this square
        for (int square = 0; square < 49; square++) {
            int enemyHeight = redGuard ? state.blueStackHeights[square] : state.redStackHeights[square];
            if (enemyHeight > 0 && canTowerAttack(square, guardPos, enemyHeight)) {
                return true;
            }
        }

        // Check if enemy guard can attack (adjacent)
        long enemyGuard = redGuard ? state.blueGuard : state.redGuard;
        if (enemyGuard != 0) {
            int enemyGuardPos = Long.numberOfTrailingZeros(enemyGuard);
            if (isAdjacent(enemyGuardPos, guardPos)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Count available escape routes for guard
     */
    private int countEscapeRoutes(GameState state, int guardPos, boolean isRed) {
        int escapeRoutes = 0;
        int[] directions = {-1, 1, -7, 7}; // left, right, up, down

        for (int dir : directions) {
            int target = guardPos + dir;
            if (isValidSquare(guardPos, target, dir) && isSquareEmpty(state, target)) {
                escapeRoutes++;
            }
        }

        return escapeRoutes;
    }

    /**
     * Approximate mobility without generating moves
     */
    private int approximateMobility(GameState state, int guardPos, boolean isRed) {
        int mobility = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int target = guardPos + dir;
            if (isValidSquare(guardPos, target, dir)) {
                if (isSquareEmpty(state, target) || isEnemyPiece(state, target, isRed)) {
                    mobility++;
                }
            }
        }

        return mobility;
    }

    /**
     * Count nearby friendly towers for coordination
     */
    private int countNearbyFriendlyTowers(GameState state, int guardPos, boolean isRed) {
        int count = 0;
        int[] directions = {-1, 1, -7, 7, -8, -6, 6, 8}; // All 8 directions

        for (int dir : directions) {
            int adjacent = guardPos + dir;
            if (isValidSquare(guardPos, adjacent, dir)) {
                if (isRed) {
                    if (state.redStackHeights[adjacent] > 0) count++;
                } else {
                    if (state.blueStackHeights[adjacent] > 0) count++;
                }
            }
        }

        return count;
    }

    /**
     * Check for game-ending positions
     */
    public int checkTerminal(GameState state) {
        // Guard captured
        if (state.redGuard == 0) return -GUARD_CAPTURE;
        if (state.blueGuard == 0) return GUARD_CAPTURE;

        // Guard reached enemy castle
        if ((state.redGuard & (1L << BLUE_CASTLE)) != 0) return CASTLE_REACH;
        if ((state.blueGuard & (1L << RED_CASTLE)) != 0) return -CASTLE_REACH;

        return 0;
    }

    // === UTILITY METHODS ===

    /**
     * Check if a tower can attack a square
     */
    private boolean canTowerAttack(int fromSquare, int toSquare, int range) {
        int fromRank = fromSquare / 7;
        int fromFile = fromSquare % 7;
        int toRank = toSquare / 7;
        int toFile = toSquare % 7;

        // Must be on same rank or file (orthogonal)
        if (fromRank != toRank && fromFile != toFile) return false;

        // Must be within range
        int distance = Math.abs(fromRank - toRank) + Math.abs(fromFile - toFile);
        return distance <= range;
    }

    /**
     * Check if two squares are adjacent
     */
    private boolean isAdjacent(int square1, int square2) {
        int rank1 = square1 / 7, file1 = square1 % 7;
        int rank2 = square2 / 7, file2 = square2 % 7;
        int rankDiff = Math.abs(rank1 - rank2);
        int fileDiff = Math.abs(file1 - file2);
        return (rankDiff == 1 && fileDiff == 0) || (rankDiff == 0 && fileDiff == 1);
    }

    /**
     * Check if a move is within board bounds and valid
     */
    private boolean isValidSquare(int from, int to, int direction) {
        if (to < 0 || to >= 49) return false;

        // Check for rank wrapping on horizontal moves
        if (Math.abs(direction) == 1) {
            return (from / 7) == (to / 7); // Same rank
        }

        return true;
    }

    /**
     * Check if a square is empty
     */
    private boolean isSquareEmpty(GameState state, int square) {
        return state.redStackHeights[square] == 0 &&
                state.blueStackHeights[square] == 0 &&
                (state.redGuard & (1L << square)) == 0 &&
                (state.blueGuard & (1L << square)) == 0;
    }

    /**
     * Check if square contains enemy piece
     */
    private boolean isEnemyPiece(GameState state, int square, boolean weAreRed) {
        if (weAreRed) {
            return state.blueStackHeights[square] > 0 ||
                    (state.blueGuard & (1L << square)) != 0;
        } else {
            return state.redStackHeights[square] > 0 ||
                    (state.redGuard & (1L << square)) != 0;
        }
    }

    // === DEBUG METHODS ===

    /**
     * Get evaluation breakdown for debugging
     */
    public String getEvaluationBreakdown(GameState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== EVALUATION BREAKDOWN ===\n");

        int terminalScore = checkTerminal(state);
        if (terminalScore != 0) {
            sb.append("Terminal position: ").append(terminalScore).append("\n");
            return sb.toString();
        }

        int materialScore = 0;
        int positionScore = 0;
        int guardScore = 0;

        // Calculate components
        for (int square = 0; square < 49; square++) {
            if (state.redStackHeights[square] > 0) {
                materialScore += state.redStackHeights[square] * TOWER_VALUE;
                positionScore += getPositionBonus(square/7, square%7, state.redStackHeights[square], true);
            }
            if (state.blueStackHeights[square] > 0) {
                materialScore -= state.blueStackHeights[square] * TOWER_VALUE;
                positionScore -= getPositionBonus(square/7, square%7, state.blueStackHeights[square], false);
            }
        }

        guardScore = evaluateGuards(state);

        int total = materialScore + positionScore + guardScore;

        sb.append(String.format("Material:  %+6d\n", materialScore));
        sb.append(String.format("Position:  %+6d\n", positionScore));
        sb.append(String.format("Guards:    %+6d\n", guardScore));
        sb.append("==================\n");
        sb.append(String.format("Total:     %+6d\n", total));

        return sb.toString();
    }

    /**
     * Quick evaluation for performance testing
     */
    public int evaluateQuick(GameState state) {
        // Minimal evaluation for performance testing
        int terminalScore = checkTerminal(state);
        if (terminalScore != 0) return terminalScore;

        int score = 0;

        // Material only
        for (int square = 0; square < 49; square++) {
            score += (state.redStackHeights[square] - state.blueStackHeights[square]) * TOWER_VALUE;
        }

        // Basic guard distance
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int distance = Math.abs(guardPos/7 - BLUE_CASTLE/7) + Math.abs(guardPos%7 - BLUE_CASTLE%7);
            score += (12 - distance) * GUARD_DISTANCE_BONUS;
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int distance = Math.abs(guardPos/7 - RED_CASTLE/7) + Math.abs(guardPos%7 - RED_CASTLE%7);
            score -= (12 - distance) * GUARD_DISTANCE_BONUS;
        }

        return score;
    }
}
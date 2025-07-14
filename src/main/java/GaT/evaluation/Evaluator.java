package GaT.evaluation;

import GaT.model.GameState;
import GaT.search.MoveGenerator;
import GaT.model.Move;
import java.util.List;

/**
 * CLEAN EVALUATOR for Turm & Wächter
 *
 * Focused on the 4 strategic elements that actually matter:
 * 1. Material (60%) - Tower count, height, and position
 * 2. Guard Advancement (25%) - Progress toward enemy castle
 * 3. Guard Safety (10%) - Immediate threat detection
 * 4. Piece Activity (5%) - Mobility and coordination
 *
 * Design principles:
 * - Fast: <0.1ms per evaluation
 * - Simple: Easy to understand and tune
 * - Effective: Captures key strategic concepts
 * - Game-specific: Tailored for Turm & Wächter rules
 */
public class Evaluator {

    // === CORE GAME VALUES ===
    private static final int TOWER_VALUE = 100;           // Base value per tower piece
    private static final int GUARD_CAPTURE = 10000;       // Winning by capturing guard
    private static final int CASTLE_REACH = 10000;        // Winning by reaching castle

    // === CASTLE POSITIONS ===
    private static final int RED_CASTLE = GameState.getIndex(6, 3);  // D7
    private static final int BLUE_CASTLE = GameState.getIndex(0, 3); // D1

    // === EVALUATION WEIGHTS ===
    private static final int MATERIAL_WEIGHT = 60;
    private static final int ADVANCEMENT_WEIGHT = 25;
    private static final int SAFETY_WEIGHT = 10;
    private static final int ACTIVITY_WEIGHT = 5;

    // === TUNABLE PARAMETERS ===
    private static final int ADVANCEMENT_BONUS = 5;       // Bonus per rank advanced per tower height
    private static final int D_FILE_BONUS = 8;           // Bonus for controlling D-file
    private static final int CENTRAL_BONUS = 3;          // Bonus for central files (C,D,E)
    private static final int GUARD_DISTANCE_BONUS = 50;  // Bonus per step closer to enemy castle
    private static final int D_FILE_GUARD_BONUS = 100;   // Bonus for guard on D-file
    private static final int CASTLE_PROXIMITY_BONUS = 200; // Bonus when guard very close to castle
    private static final int SAFETY_PENALTY = 500;       // Penalty when guard threatened
    private static final int ESCAPE_ROUTE_BONUS = 20;    // Bonus per escape route
    private static final int MOBILITY_BONUS = 2;         // Bonus per legal move
    private static final int COORDINATION_BONUS = 5;     // Bonus per adjacent friendly piece

    /**
     * Main evaluation function
     */
    public int evaluate(GameState state) {
        // Check for terminal positions first
        int terminalScore = checkTerminalPosition(state);
        if (terminalScore != 0) {
            return terminalScore;
        }

        // Evaluate the 4 core components
        int materialScore = evaluateMaterial(state);
        int advancementScore = evaluateGuardAdvancement(state);
        int safetyScore = evaluateGuardSafety(state);
        int activityScore = evaluatePieceActivity(state);

        // Weighted combination
        return (materialScore * MATERIAL_WEIGHT +
                advancementScore * ADVANCEMENT_WEIGHT +
                safetyScore * SAFETY_WEIGHT +
                activityScore * ACTIVITY_WEIGHT) / 100;
    }

    /**
     * Check for game-ending positions
     */
    private int checkTerminalPosition(GameState state) {
        // Guard captured
        if (state.redGuard == 0) return -GUARD_CAPTURE;
        if (state.blueGuard == 0) return GUARD_CAPTURE;

        // Guard reached enemy castle
        boolean redWins = (state.redGuard & GameState.bit(BLUE_CASTLE)) != 0;
        boolean blueWins = (state.blueGuard & GameState.bit(RED_CASTLE)) != 0;

        if (redWins) return CASTLE_REACH;
        if (blueWins) return -CASTLE_REACH;

        return 0;
    }

    // ========================================
    // 1. MATERIAL EVALUATION (60% weight)
    // ========================================

    /**
     * Evaluate material with position-based bonuses
     */
    private int evaluateMaterial(GameState state) {
        int score = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // Red towers
            if (state.redStackHeights[i] > 0) {
                int height = state.redStackHeights[i];
                score += height * TOWER_VALUE;
                score += getMaterialPositionBonus(i, height, true);
            }

            // Blue towers
            if (state.blueStackHeights[i] > 0) {
                int height = state.blueStackHeights[i];
                score -= height * TOWER_VALUE;
                score -= getMaterialPositionBonus(i, height, false);
            }
        }

        return score;
    }

    /**
     * Position-based bonuses for towers
     */
    private int getMaterialPositionBonus(int square, int height, boolean isRed) {
        int rank = GameState.rank(square);
        int file = GameState.file(square);
        int bonus = 0;

        // Advancement bonus: towers are more valuable when advanced
        if (isRed && rank < 4) {
            bonus += (4 - rank) * height * ADVANCEMENT_BONUS;
        } else if (!isRed && rank > 2) {
            bonus += (rank - 2) * height * ADVANCEMENT_BONUS;
        }

        // D-file control: critical for guard advancement
        if (file == 3) {
            bonus += height * D_FILE_BONUS;
        }

        // Central files: generally more active
        if (file >= 2 && file <= 4) {
            bonus += height * CENTRAL_BONUS;
        }

        return bonus;
    }

    // ========================================
    // 2. GUARD ADVANCEMENT (25% weight)
    // ========================================

    /**
     * Evaluate guard progress toward enemy castle
     */
    private int evaluateGuardAdvancement(GameState state) {
        int score = 0;

        // Red guard advancement toward D1
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            score += evaluateGuardProgress(guardPos, BLUE_CASTLE);
        }

        // Blue guard advancement toward D7
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            score -= evaluateGuardProgress(guardPos, RED_CASTLE);
        }

        return score;
    }

    /**
     * Evaluate how well-positioned a guard is for advancing
     */
    private int evaluateGuardProgress(int guardPos, int targetCastle) {
        int guardRank = GameState.rank(guardPos);
        int guardFile = GameState.file(guardPos);
        int targetRank = GameState.rank(targetCastle);
        int targetFile = GameState.file(targetCastle);

        // Manhattan distance to target castle
        int distance = Math.abs(guardRank - targetRank) + Math.abs(guardFile - targetFile);
        int maxDistance = 12; // Maximum possible distance

        // Base score: closer is better
        int score = (maxDistance - distance) * GUARD_DISTANCE_BONUS;

        // Big bonus for being on D-file (direct path)
        if (guardFile == 3) {
            score += D_FILE_GUARD_BONUS;
        }

        // Extra bonus for being very close to castle
        if (distance <= 2) {
            score += CASTLE_PROXIMITY_BONUS;
        }

        // Small penalty for being too far from D-file
        int fileDistance = Math.abs(guardFile - 3);
        score -= fileDistance * 10;

        return score;
    }

    // ========================================
    // 3. GUARD SAFETY (10% weight)
    // ========================================

    /**
     * Evaluate guard safety from immediate threats
     */
    private int evaluateGuardSafety(GameState state) {
        int score = 0;

        // Check if guards are under immediate attack
        if (isGuardThreatened(state, true)) {
            score -= SAFETY_PENALTY;
        }

        if (isGuardThreatened(state, false)) {
            score += SAFETY_PENALTY; // Good for red if blue guard threatened
        }

        // Bonus for guards with escape routes
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            score += countEscapeRoutes(state, guardPos, true) * ESCAPE_ROUTE_BONUS;
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            score -= countEscapeRoutes(state, guardPos, false) * ESCAPE_ROUTE_BONUS;
        }

        return score;
    }

    /**
     * Check if a guard is under immediate attack
     */
    private boolean isGuardThreatened(GameState state, boolean redGuard) {
        long guardBit = redGuard ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);

        // Check if any enemy piece can attack the guard next move
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // Check enemy towers
            int enemyHeight = redGuard ? state.blueStackHeights[i] : state.redStackHeights[i];
            if (enemyHeight > 0 && canAttackSquare(i, guardPos, enemyHeight)) {
                return true;
            }

            // Check enemy guard (can attack adjacent squares)
            long enemyGuard = redGuard ? state.blueGuard : state.redGuard;
            if (enemyGuard != 0 && i == Long.numberOfTrailingZeros(enemyGuard)) {
                if (isAdjacent(i, guardPos)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Count available escape routes for a guard
     */
    private int countEscapeRoutes(GameState state, int guardPos, boolean isRed) {
        int escapeRoutes = 0;
        int[] directions = {-1, 1, -7, 7}; // left, right, up, down

        for (int dir : directions) {
            int target = guardPos + dir;

            // Check bounds and rank wrapping
            if (!isValidMove(guardPos, target, dir)) continue;

            // Check if square is empty
            if (isSquareEmpty(state, target)) {
                escapeRoutes++;
            }
        }

        return escapeRoutes;
    }

    // ========================================
    // 4. PIECE ACTIVITY (5% weight)
    // ========================================

    /**
     * Evaluate piece mobility and coordination
     */
    private int evaluatePieceActivity(GameState state) {
        int score = 0;

        // Mobility: count legal moves (simple approximation)
        score += evaluateMobility(state);

        // Coordination: pieces supporting each other
        score += evaluateCoordination(state);

        return score;
    }

    /**
     * Simple mobility evaluation
     */
    private int evaluateMobility(GameState state) {
        try {
            // Generate moves to get mobility count
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            int mobilityScore = Math.min(moves.size() * MOBILITY_BONUS, 100); // Cap at 100

            return state.redToMove ? mobilityScore : -mobilityScore;
        } catch (Exception e) {
            // Fallback if move generation fails
            return 0;
        }
    }

    /**
     * Evaluate piece coordination (adjacent friendly pieces)
     */
    private int evaluateCoordination(GameState state) {
        int score = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // Red pieces
            if (state.redStackHeights[i] > 0 || (state.redGuard & GameState.bit(i)) != 0) {
                score += countAdjacentFriendly(state, i, true) * COORDINATION_BONUS;
            }

            // Blue pieces
            if (state.blueStackHeights[i] > 0 || (state.blueGuard & GameState.bit(i)) != 0) {
                score -= countAdjacentFriendly(state, i, false) * COORDINATION_BONUS;
            }
        }

        return score;
    }

    /**
     * Count adjacent friendly pieces
     */
    private int countAdjacentFriendly(GameState state, int square, boolean isRed) {
        int count = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int adjacent = square + dir;
            if (!isValidMove(square, adjacent, dir)) continue;

            if (isRed) {
                if (state.redStackHeights[adjacent] > 0 ||
                        (state.redGuard & GameState.bit(adjacent)) != 0) {
                    count++;
                }
            } else {
                if (state.blueStackHeights[adjacent] > 0 ||
                        (state.blueGuard & GameState.bit(adjacent)) != 0) {
                    count++;
                }
            }
        }

        return count;
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Check if a piece can attack a square
     */
    private boolean canAttackSquare(int fromSquare, int toSquare, int range) {
        int rankDiff = Math.abs(GameState.rank(fromSquare) - GameState.rank(toSquare));
        int fileDiff = Math.abs(GameState.file(fromSquare) - GameState.file(toSquare));

        // Must be on same rank or file (orthogonal movement)
        if (rankDiff != 0 && fileDiff != 0) return false;

        // Must be within range
        int distance = Math.max(rankDiff, fileDiff);
        return distance <= range;
    }

    /**
     * Check if two squares are adjacent
     */
    private boolean isAdjacent(int square1, int square2) {
        int rankDiff = Math.abs(GameState.rank(square1) - GameState.rank(square2));
        int fileDiff = Math.abs(GameState.file(square1) - GameState.file(square2));
        return (rankDiff == 1 && fileDiff == 0) || (rankDiff == 0 && fileDiff == 1);
    }

    /**
     * Check if a move is valid (bounds and rank wrapping)
     */
    boolean isValidMove(int from, int to, int direction) {
        if (to < 0 || to >= GameState.NUM_SQUARES) return false;

        // Check for rank wrapping on horizontal moves
        if (Math.abs(direction) == 1) {
            return GameState.rank(from) == GameState.rank(to);
        }

        return true;
    }

    /**
     * Check if a square is empty
     */
    boolean isSquareEmpty(GameState state, int square) {
        return state.redStackHeights[square] == 0 &&
                state.blueStackHeights[square] == 0 &&
                (state.redGuard & GameState.bit(square)) == 0 &&
                (state.blueGuard & GameState.bit(square)) == 0;
    }

    // ========================================
    // DEBUGGING AND ANALYSIS
    // ========================================

    /**
     * Get detailed evaluation breakdown for position analysis
     */
    public String getEvaluationBreakdown(GameState state) {
        int material = evaluateMaterial(state);
        int advancement = evaluateGuardAdvancement(state);
        int safety = evaluateGuardSafety(state);
        int activity = evaluatePieceActivity(state);

        int total = (material * MATERIAL_WEIGHT +
                advancement * ADVANCEMENT_WEIGHT +
                safety * SAFETY_WEIGHT +
                activity * ACTIVITY_WEIGHT) / 100;

        StringBuilder sb = new StringBuilder();
        sb.append("=== EVALUATION BREAKDOWN ===\n");
        sb.append(String.format("Material:    %+5d × %2d%% = %+6d\n",
                material, MATERIAL_WEIGHT, material * MATERIAL_WEIGHT / 100));
        sb.append(String.format("Advancement: %+5d × %2d%% = %+6d\n",
                advancement, ADVANCEMENT_WEIGHT, advancement * ADVANCEMENT_WEIGHT / 100));
        sb.append(String.format("Safety:      %+5d × %2d%% = %+6d\n",
                safety, SAFETY_WEIGHT, safety * SAFETY_WEIGHT / 100));
        sb.append(String.format("Activity:    %+5d × %2d%% = %+6d\n",
                activity, ACTIVITY_WEIGHT, activity * ACTIVITY_WEIGHT / 100));
        sb.append("================================\n");
        sb.append(String.format("TOTAL:       %+6d\n", total));

        return sb.toString();
    }

    /**
     * Quick evaluation for compatibility with existing interfaces
     */
    public int evaluate(GameState state, int depth) {
        return evaluate(state);
    }

    /**
     * Check if guard is in danger (for compatibility)
     */
    public boolean isGuardInDanger(GameState state, boolean checkRed) {
        return isGuardThreatened(state, checkRed);
    }
}
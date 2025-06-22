package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.SearchConfig;

/**
 * MATERIAL EVALUATION COMPONENT
 * Handles all material-related evaluation (piece values, imbalances, activity)
 */
public class MaterialEval {

    // === MATERIAL VALUES ===
    public static final int TOWER_BASE_VALUE = 100;
    public static final int GUARD_BASE_VALUE = 50;

    // === PHASE-SPECIFIC MULTIPLIERS ===
    private static final double[] OPENING_MULTIPLIERS = {1.0, 0.9}; // [tower, guard]
    private static final double[] MIDDLEGAME_MULTIPLIERS = {1.1, 1.0};
    private static final double[] ENDGAME_MULTIPLIERS = {1.2, 1.5};

    // === ACTIVITY BONUSES ===
    private static final int ADVANCEMENT_BONUS = 15;
    private static final int CENTRAL_BONUS = 20;
    private static final int CONNECTED_BONUS = 25;
    private static final int MOBILITY_BONUS = 10;

    /**
     * SIMPLE MATERIAL EVALUATION - Ultra-fast for time pressure
     */
    public int evaluateMaterialSimple(GameState state) {
        int materialScore = 0;

        // Raw piece count difference
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            materialScore += (state.redStackHeights[i] - state.blueStackHeights[i]) * TOWER_BASE_VALUE;
        }

        return materialScore;
    }

    /**
     * BASIC MATERIAL EVALUATION - Standard material with basic bonuses
     */
    public int evaluateMaterialBasic(GameState state) {
        int materialScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int redHeight = state.redStackHeights[i];
            int blueHeight = state.blueStackHeights[i];

            if (redHeight > 0) {
                int value = redHeight * TOWER_BASE_VALUE;
                value += getBasicPositionalBonus(i, redHeight, true);
                materialScore += value;
            }

            if (blueHeight > 0) {
                int value = blueHeight * TOWER_BASE_VALUE;
                value += getBasicPositionalBonus(i, blueHeight, false);
                materialScore -= value;
            }
        }

        return materialScore;
    }

    /**
     * MATERIAL WITH ACTIVITY - Enhanced with mobility and positioning
     */
    public int evaluateMaterialWithActivity(GameState state) {
        int materialScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int redHeight = state.redStackHeights[i];
            int blueHeight = state.blueStackHeights[i];

            if (redHeight > 0) {
                int value = redHeight * TOWER_BASE_VALUE;
                value += getActivityBonus(state, i, redHeight, true);
                materialScore += value;
            }

            if (blueHeight > 0) {
                int value = blueHeight * TOWER_BASE_VALUE;
                value += getActivityBonus(state, i, blueHeight, false);
                materialScore -= value;
            }
        }

        return materialScore;
    }

    /**
     * ADVANCED MATERIAL EVALUATION - Deep positional understanding
     */
    public int evaluateMaterialAdvanced(GameState state) {
        int materialScore = 0;

        // Phase detection for appropriate values
        GamePhase phase = detectGamePhase(state);
        double[] multipliers = getPhaseMultipliers(phase);

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int redHeight = state.redStackHeights[i];
            int blueHeight = state.blueStackHeights[i];

            if (redHeight > 0) {
                int value = (int) (redHeight * TOWER_BASE_VALUE * multipliers[0]);
                value += getAdvancedPositionalBonus(state, i, redHeight, true, phase);
                materialScore += value;
            }

            if (blueHeight > 0) {
                int value = (int) (blueHeight * TOWER_BASE_VALUE * multipliers[0]);
                value += getAdvancedPositionalBonus(state, i, blueHeight, false, phase);
                materialScore -= value;
            }
        }

        // Add imbalance evaluation
        materialScore += evaluateMaterialImbalance(state, phase);

        return materialScore;
    }

    /**
     * ENDGAME MATERIAL EVALUATION - Optimized for few pieces
     */
    public int evaluateMaterialEndgame(GameState state) {
        int materialScore = 0;

        // In endgame, piece activity matters more than raw material
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int redHeight = state.redStackHeights[i];
            int blueHeight = state.blueStackHeights[i];

            if (redHeight > 0) {
                int value = redHeight * TOWER_BASE_VALUE;
                // Endgame bonuses: advancement and king support
                value += getEndgameBonus(state, i, redHeight, true);
                materialScore += value;
            }

            if (blueHeight > 0) {
                int value = blueHeight * TOWER_BASE_VALUE;
                value += getEndgameBonus(state, i, blueHeight, false);
                materialScore -= value;
            }
        }

        return materialScore;
    }

    // === POSITIONAL BONUS CALCULATION ===

    /**
     * Basic positional bonuses for material evaluation
     */
    private int getBasicPositionalBonus(int square, int height, boolean isRed) {
        int bonus = 0;

        // Advancement bonus
        int rank = GameState.rank(square);
        if (isRed && rank < 3) {
            bonus += height * ADVANCEMENT_BONUS;
        } else if (!isRed && rank > 3) {
            bonus += height * ADVANCEMENT_BONUS;
        }

        // Central files bonus
        int file = GameState.file(square);
        if (file >= 2 && file <= 4) {
            bonus += CENTRAL_BONUS;
        }

        return bonus;
    }

    /**
     * Activity bonuses including mobility and coordination
     */
    private int getActivityBonus(GameState state, int square, int height, boolean isRed) {
        int bonus = getBasicPositionalBonus(square, height, isRed);

        // Connected pieces bonus
        if (hasAdjacentFriendly(state, square, isRed)) {
            bonus += CONNECTED_BONUS;
        }

        // Mobility estimation (pieces that can move far are more valuable)
        bonus += estimateMobility(state, square, height, isRed);

        // Outpost bonus (pieces deep in enemy territory)
        bonus += getOutpostBonus(square, isRed);

        return bonus;
    }

    /**
     * Advanced positional bonuses with phase awareness
     */
    private int getAdvancedPositionalBonus(GameState state, int square, int height, boolean isRed, GamePhase phase) {
        int bonus = getActivityBonus(state, square, height, isRed);

        // Phase-specific bonuses
        switch (phase) {
            case OPENING:
                bonus += getDevelopmentBonus(square, isRed);
                break;
            case MIDDLEGAME:
                bonus += getTacticalBonus(state, square, height, isRed);
                break;
            case ENDGAME:
                bonus += getEndgameBonus(state, square, height, isRed);
                break;
            case TABLEBASE:
                bonus += getTablebaseBonus(state, square, height, isRed);
                break;
        }

        return bonus;
    }

    /**
     * Endgame-specific bonuses
     */
    private int getEndgameBonus(GameState state, int square, int height, boolean isRed) {
        int bonus = 0;

        // Distance to enemy guard matters in endgame
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        if (enemyGuard != 0) {
            int enemyGuardPos = Long.numberOfTrailingZeros(enemyGuard);
            int distance = calculateDistance(square, enemyGuardPos);

            // Closer pieces are more valuable
            bonus += Math.max(0, (7 - distance) * 10);
        }

        // Support for own guard
        long ownGuard = isRed ? state.redGuard : state.blueGuard;
        if (ownGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(ownGuard);
            int guardDistance = calculateDistance(square, guardPos);

            // Pieces near own guard provide support
            if (guardDistance <= 2) {
                bonus += 30;
            }
        }

        return bonus;
    }

    // === SPECIALIZED EVALUATION METHODS ===

    /**
     * Evaluate material imbalances and their strategic implications
     */
    private int evaluateMaterialImbalance(GameState state, GamePhase phase) {
        int redTotal = getTotalMaterial(state, true);
        int blueTotal = getTotalMaterial(state, false);
        int imbalance = redTotal - blueTotal;

        if (Math.abs(imbalance) <= 1) {
            return 0; // Equal material
        }

        // Small imbalances are often more significant than raw piece count suggests
        int imbalanceBonus = 0;

        if (phase == GamePhase.ENDGAME) {
            // In endgame, small material advantages are magnified
            imbalanceBonus = imbalance * 150;
        } else if (phase == GamePhase.MIDDLEGAME) {
            // In middlegame, material advantage helps in tactics
            imbalanceBonus = imbalance * 120;
        } else {
            // In opening, development matters more than material
            imbalanceBonus = imbalance * 80;
        }

        return imbalanceBonus;
    }

    /**
     * Calculate total material for a player
     */
    public int getTotalMaterial(GameState state, boolean isRed) {
        int total = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            total += isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
        }
        return total;
    }

    /**
     * Calculate total material for both players
     */
    public int getTotalMaterial(GameState state) {
        return getTotalMaterial(state, true) + getTotalMaterial(state, false);
    }

    /**
     * Evaluate piece activity and coordination
     */
    public int evaluatePieceActivity(GameState state) {
        int activityScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                activityScore += evaluatePieceActivityAt(state, i, true);
            }
            if (state.blueStackHeights[i] > 0) {
                activityScore -= evaluatePieceActivityAt(state, i, false);
            }
        }

        return activityScore;
    }

    private int evaluatePieceActivityAt(GameState state, int square, boolean isRed) {
        int activity = 0;
        int height = isRed ? state.redStackHeights[square] : state.blueStackHeights[square];

        // Mobility factor
        activity += estimateMobility(state, square, height, isRed);

        // Coordination with other pieces
        if (hasAdjacentFriendly(state, square, isRed)) {
            activity += 20;
        }

        // Control of important squares
        activity += evaluateSquareControl(state, square, height, isRed);

        return activity;
    }

    // === HELPER METHODS ===

    /**
     * Check if piece has adjacent friendly pieces
     */
    private boolean hasAdjacentFriendly(GameState state, int square, boolean isRed) {
        int[] directions = {-1, 1, -7, 7}; // Left, Right, Up, Down

        for (int dir : directions) {
            int adjacent = square + dir;
            if (!GameState.isOnBoard(adjacent)) continue;

            // Check for rank wrap on horizontal moves
            if (Math.abs(dir) == 1 && GameState.rank(square) != GameState.rank(adjacent)) continue;

            long adjBit = GameState.bit(adjacent);
            if (isRed && ((state.redTowers | state.redGuard) & adjBit) != 0) {
                return true;
            } else if (!isRed && ((state.blueTowers | state.blueGuard) & adjBit) != 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * Estimate piece mobility
     */
    private int estimateMobility(GameState state, int square, int height, boolean isRed) {
        int mobility = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            for (int dist = 1; dist <= height; dist++) {
                int target = square + dir * dist;
                if (!GameState.isOnBoard(target)) break;

                // Check for rank wrap
                if (Math.abs(dir) == 1 && GameState.rank(square) != GameState.rank(target)) break;

                // Check if path is blocked
                if (isOccupied(target, state)) {
                    // Can capture enemy pieces
                    if (isEnemyPiece(target, state, isRed)) {
                        mobility += MOBILITY_BONUS / 2;
                    }
                    break;
                } else {
                    mobility += MOBILITY_BONUS;
                }
            }
        }

        return mobility;
    }

    /**
     * Get outpost bonus for pieces in enemy territory
     */
    private int getOutpostBonus(int square, boolean isRed) {
        int rank = GameState.rank(square);

        if (isRed && rank <= 2) {
            return (2 - rank) * 30; // Deeper = better
        } else if (!isRed && rank >= 4) {
            return (rank - 4) * 30;
        }

        return 0;
    }

    /**
     * Get development bonus (pieces off back rank)
     */
    private int getDevelopmentBonus(int square, boolean isRed) {
        int rank = GameState.rank(square);

        if (isRed && rank != 6) {
            return 25; // Red pieces off rank 7
        } else if (!isRed && rank != 0) {
            return 25; // Blue pieces off rank 1
        }

        return 0;
    }

    /**
     * Get tactical bonus for pieces in active positions
     */
    private int getTacticalBonus(GameState state, int square, int height, boolean isRed) {
        int bonus = 0;

        // Pieces that can attack enemy guard
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        if (enemyGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(enemyGuard);
            if (canAttack(square, guardPos, height)) {
                bonus += 100; // Attacking enemy guard is valuable
            }
        }

        return bonus;
    }

    /**
     * Get tablebase bonus (perfect endgame knowledge)
     */
    private int getTablebaseBonus(GameState state, int square, int height, boolean isRed) {
        // TODO: Implement tablebase queries
        return getEndgameBonus(state, square, height, isRed) / 2;
    }

    /**
     * Evaluate control of strategic squares
     */
    private int evaluateSquareControl(GameState state, int square, int height, boolean isRed) {
        int control = 0;
        int[] directions = {-1, 1, -7, 7};

        // Check squares this piece controls
        for (int dir : directions) {
            for (int dist = 1; dist <= height; dist++) {
                int controlled = square + dir * dist;
                if (!GameState.isOnBoard(controlled)) break;

                if (Math.abs(dir) == 1 && GameState.rank(square) != GameState.rank(controlled)) break;

                if (isOccupied(controlled, state)) break;

                // Bonus for controlling central squares
                if (isCentralSquare(controlled)) {
                    control += 5;
                }

                // Bonus for controlling enemy territory
                if (isEnemyTerritory(controlled, isRed)) {
                    control += 3;
                }
            }
        }

        return control;
    }

    // === UTILITY METHODS ===

    private GamePhase detectGamePhase(GameState state) {
        int totalMaterial = getTotalMaterial(state);
        if (totalMaterial <= 6) return GamePhase.ENDGAME;
        if (totalMaterial <= 12) return GamePhase.MIDDLEGAME;
        return GamePhase.OPENING;
    }

    private double[] getPhaseMultipliers(GamePhase phase) {
        return switch (phase) {
            case OPENING -> OPENING_MULTIPLIERS;
            case MIDDLEGAME -> MIDDLEGAME_MULTIPLIERS;
            case ENDGAME, TABLEBASE -> ENDGAME_MULTIPLIERS;
        };
    }

    private int calculateDistance(int from, int to) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));
        return rankDiff + fileDiff; // Manhattan distance
    }

    private boolean isOccupied(int square, GameState state) {
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard)
                & GameState.bit(square)) != 0;
    }

    private boolean isEnemyPiece(int square, GameState state, boolean isRed) {
        long bit = GameState.bit(square);
        if (isRed) {
            return (state.blueGuard & bit) != 0 || (state.blueTowers & bit) != 0;
        } else {
            return (state.redGuard & bit) != 0 || (state.redTowers & bit) != 0;
        }
    }

    private boolean canAttack(int from, int to, int height) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        // Check if in range and on same rank/file
        boolean sameRank = rankDiff == 0;
        boolean sameFile = fileDiff == 0;
        int distance = Math.max(rankDiff, fileDiff);

        return (sameRank || sameFile) && distance <= height;
    }

    private boolean isCentralSquare(int square) {
        int file = GameState.file(square);
        int rank = GameState.rank(square);
        return file >= 2 && file <= 4 && rank >= 2 && rank <= 4;
    }

    private boolean isEnemyTerritory(int square, boolean isRed) {
        int rank = GameState.rank(square);
        return isRed ? rank < 3 : rank > 3;
    }

    // === PHASE ENUM ===
    public enum GamePhase {
        OPENING,
        MIDDLEGAME,
        ENDGAME,
        TABLEBASE
    }
}
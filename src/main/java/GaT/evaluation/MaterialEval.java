package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.SearchConfig;

/**
 * ENHANCED MATERIAL EVALUATION COMPONENT
 *
 * ✅ Enhanced with aggressive parameters
 * ✅ Better activity bonuses for coordinated play
 * ✅ Improved outpost evaluation for edge towers
 * ✅ Enhanced piece activity coordination
 *
 * Handles all material-related evaluation (piece values, imbalances, activity)
 */
public class MaterialEval {

    // === ENHANCED MATERIAL VALUES ===
    public static final int TOWER_BASE_VALUE = 100;
    public static final int GUARD_BASE_VALUE = 50;

    // === PHASE-SPECIFIC MULTIPLIERS ===
    private static final double[] OPENING_MULTIPLIERS = {1.0, 0.9}; // [tower, guard]
    private static final double[] MIDDLEGAME_MULTIPLIERS = {1.1, 1.0};
    private static final double[] ENDGAME_MULTIPLIERS = {1.2, 1.5};

    // === ✅ ENHANCED ACTIVITY BONUSES ===
    private static final int ADVANCEMENT_BONUS = 25;               // Erhöht von 15
    private static final int CENTRAL_BONUS = 30;                   // Erhöht von 20
    private static final int CONNECTED_BONUS = 40;                 // Erhöht von 25
    private static final int MOBILITY_BONUS = 15;                  // Erhöht von 10

    // === ✅ NEW: AGGRESSIVE COORDINATION BONUSES ===
    private static final int EDGE_TOWER_BONUS = 35;                // NEU! Aktive Randtürme
    private static final int DEEP_PENETRATION_BONUS = 50;          // NEU! Tiefe Vorstöße
    private static final int CLUSTER_SUPPORT_BONUS = 30;           // NEU! Turm-Cluster Support
    private static final int GUARD_ESCORT_BONUS = 45;              // NEU! Wächter-Begleitung

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
     * ✅ ENHANCED BASIC MATERIAL EVALUATION - Now with coordination awareness
     */
    public int evaluateMaterialBasic(GameState state) {
        int materialScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int redHeight = state.redStackHeights[i];
            int blueHeight = state.blueStackHeights[i];

            if (redHeight > 0) {
                int value = redHeight * TOWER_BASE_VALUE;
                value += getEnhancedPositionalBonus(state, i, redHeight, true);
                materialScore += value;
            }

            if (blueHeight > 0) {
                int value = blueHeight * TOWER_BASE_VALUE;
                value += getEnhancedPositionalBonus(state, i, blueHeight, false);
                materialScore -= value;
            }
        }

        return materialScore;
    }

    /**
     * ✅ ENHANCED MATERIAL WITH ACTIVITY - Better coordination detection
     */
    public int evaluateMaterialWithActivity(GameState state) {
        int materialScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int redHeight = state.redStackHeights[i];
            int blueHeight = state.blueStackHeights[i];

            if (redHeight > 0) {
                int value = redHeight * TOWER_BASE_VALUE;
                value += getAdvancedActivityBonus(state, i, redHeight, true);
                materialScore += value;
            }

            if (blueHeight > 0) {
                int value = blueHeight * TOWER_BASE_VALUE;
                value += getAdvancedActivityBonus(state, i, blueHeight, false);
                materialScore -= value;
            }
        }

        return materialScore;
    }

    /**
     * ✅ ENHANCED ADVANCED MATERIAL EVALUATION - Full coordination awareness
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

        // Add enhanced imbalance evaluation
        materialScore += evaluateEnhancedMaterialImbalance(state, phase);

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
                // Enhanced endgame bonuses
                value += getEnhancedEndgameBonus(state, i, redHeight, true);
                materialScore += value;
            }

            if (blueHeight > 0) {
                int value = blueHeight * TOWER_BASE_VALUE;
                value += getEnhancedEndgameBonus(state, i, blueHeight, false);
                materialScore -= value;
            }
        }

        return materialScore;
    }

    // === ✅ ENHANCED POSITIONAL BONUS CALCULATION ===

    /**
     * ✅ Enhanced basic positional bonuses
     */
    private int getEnhancedPositionalBonus(GameState state, int square, int height, boolean isRed) {
        int bonus = 0;

        // Enhanced advancement bonus
        int rank = GameState.rank(square);
        if (isRed && rank < 3) {
            bonus += height * ADVANCEMENT_BONUS;
        } else if (!isRed && rank > 3) {
            bonus += height * ADVANCEMENT_BONUS;
        }

        // Enhanced central files bonus
        int file = GameState.file(square);
        if (file >= 2 && file <= 4) {
            bonus += CENTRAL_BONUS;
        }

        // ✅ NEW: Edge tower activation bonus
        if (isEdgeFile(file) && canThreatenCenter(state, square, height)) {
            bonus += EDGE_TOWER_BONUS;
        }

        return bonus;
    }

    /**
     * ✅ Advanced activity bonuses with coordination
     */
    private int getAdvancedActivityBonus(GameState state, int square, int height, boolean isRed) {
        int bonus = getEnhancedPositionalBonus(state, square, height, isRed);

        // Enhanced connected pieces bonus
        if (hasAdjacentFriendly(state, square, isRed)) {
            bonus += CONNECTED_BONUS;

            // ✅ NEW: Cluster support bonus
            int nearbyFriendly = countNearbyFriendly(state, square, isRed, 2);
            if (nearbyFriendly >= 2) {
                bonus += CLUSTER_SUPPORT_BONUS;
            }
        }

        // Enhanced mobility estimation
        bonus += estimateEnhancedMobility(state, square, height, isRed);

        // Enhanced outpost bonus
        bonus += getEnhancedOutpostBonus(state, square, height, isRed);

        // ✅ NEW: Guard escort bonus
        if (isEscortingGuard(state, square, height, isRed)) {
            bonus += GUARD_ESCORT_BONUS;
        }

        return bonus;
    }

    /**
     * ✅ Advanced positional bonuses with phase awareness
     */
    private int getAdvancedPositionalBonus(GameState state, int square, int height, boolean isRed, GamePhase phase) {
        int bonus = getAdvancedActivityBonus(state, square, height, isRed);

        // Phase-specific bonuses
        switch (phase) {
            case OPENING:
                bonus += getDevelopmentBonus(square, isRed);
                // ✅ Enhanced edge activation in opening
                if (isEdgeFile(GameState.file(square))) {
                    bonus += EDGE_TOWER_BONUS / 2;
                }
                break;
            case MIDDLEGAME:
                bonus += getEnhancedTacticalBonus(state, square, height, isRed);
                break;
            case ENDGAME:
                bonus += getEnhancedEndgameBonus(state, square, height, isRed);
                break;
            case TABLEBASE:
                bonus += getTablebaseBonus(state, square, height, isRed);
                break;
        }

        return bonus;
    }

    /**
     * ✅ Enhanced endgame-specific bonuses
     */
    private int getEnhancedEndgameBonus(GameState state, int square, int height, boolean isRed) {
        int bonus = 0;

        // Enhanced distance to enemy guard
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        if (enemyGuard != 0) {
            int enemyGuardPos = Long.numberOfTrailingZeros(enemyGuard);
            int distance = calculateDistance(square, enemyGuardPos);

            // Closer pieces are more valuable (enhanced)
            bonus += Math.max(0, (7 - distance) * 15); // Erhöht von 10
        }

        // Enhanced support for own guard
        long ownGuard = isRed ? state.redGuard : state.blueGuard;
        if (ownGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(ownGuard);
            int guardDistance = calculateDistance(square, guardPos);

            // Pieces near own guard provide support (enhanced)
            if (guardDistance <= 2) {
                bonus += 45; // Erhöht von 30
            }

            // ✅ NEW: Deep penetration bonus for advanced guard support
            int guardRank = GameState.rank(guardPos);
            if ((isRed && guardRank <= 2) || (!isRed && guardRank >= 4)) {
                bonus += DEEP_PENETRATION_BONUS;
            }
        }

        return bonus;
    }

    // === ✅ NEW ENHANCED EVALUATION METHODS ===

    /**
     * ✅ Enhanced material imbalance evaluation
     */
    private int evaluateEnhancedMaterialImbalance(GameState state, GamePhase phase) {
        int redTotal = getTotalMaterial(state, true);
        int blueTotal = getTotalMaterial(state, false);
        int imbalance = redTotal - blueTotal;

        if (Math.abs(imbalance) <= 1) {
            return 0; // Equal material
        }

        // Enhanced imbalance bonuses
        int imbalanceBonus = 0;

        if (phase == GamePhase.ENDGAME) {
            // In endgame, material advantages are magnified (enhanced)
            imbalanceBonus = imbalance * 200; // Erhöht von 150
        } else if (phase == GamePhase.MIDDLEGAME) {
            // In middlegame, material advantage helps in tactics (enhanced)
            imbalanceBonus = imbalance * 150; // Erhöht von 120
        } else {
            // In opening, development matters but material still counts (enhanced)
            imbalanceBonus = imbalance * 100; // Erhöht von 80
        }

        return imbalanceBonus;
    }

    /**
     * ✅ Enhanced piece activity evaluation
     */
    public int evaluatePieceActivity(GameState state) {
        int activityScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                activityScore += evaluateEnhancedPieceActivityAt(state, i, true);
            }
            if (state.blueStackHeights[i] > 0) {
                activityScore -= evaluateEnhancedPieceActivityAt(state, i, false);
            }
        }

        return activityScore;
    }

    private int evaluateEnhancedPieceActivityAt(GameState state, int square, boolean isRed) {
        int activity = 0;
        int height = isRed ? state.redStackHeights[square] : state.blueStackHeights[square];

        // Enhanced mobility factor
        activity += estimateEnhancedMobility(state, square, height, isRed);

        // Enhanced coordination with other pieces
        if (hasAdjacentFriendly(state, square, isRed)) {
            activity += 30; // Erhöht von 20
        }

        // Enhanced control of important squares
        activity += evaluateEnhancedSquareControl(state, square, height, isRed);

        // ✅ NEW: Coordination bonuses
        int supporters = countSupportingPieces(state, square, isRed);
        if (supporters > 0) {
            activity += supporters * 25;
        }

        return activity;
    }

    // === ✅ NEW HELPER METHODS ===

    /**
     * ✅ Check if file is edge file
     */
    private boolean isEdgeFile(int file) {
        return file == 0 || file == 1 || file == 5 || file == 6;
    }

    /**
     * ✅ Check if tower can threaten center
     */
    private boolean canThreatenCenter(GameState state, int square, int height) {
        int[] centralSquares = {
                GameState.getIndex(2, 3), GameState.getIndex(3, 3), GameState.getIndex(4, 3),
                GameState.getIndex(3, 2), GameState.getIndex(3, 4)
        };

        for (int center : centralSquares) {
            int distance = calculateDistance(square, center);
            if (distance <= height) {
                return true;
            }
        }
        return false;
    }

    /**
     * ✅ Count nearby friendly pieces
     */
    private int countNearbyFriendly(GameState state, int square, boolean isRed, int radius) {
        int count = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (i == square) continue;

            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) {
                int distance = calculateDistance(square, i);
                if (distance <= radius) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * ✅ Check if piece is escorting guard
     */
    private boolean isEscortingGuard(GameState state, int square, int height, boolean isRed) {
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int distance = calculateDistance(square, guardPos);

        // Close escort (adjacent or 1 square away)
        return distance <= 2 && distance <= height;
    }

    /**
     * ✅ Enhanced mobility estimation
     */
    private int estimateEnhancedMobility(GameState state, int square, int height, boolean isRed) {
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
                    // Enhanced bonus for threatening enemy pieces
                    if (isEnemyPiece(target, state, isRed)) {
                        mobility += MOBILITY_BONUS; // Full bonus for threats
                    }
                    break;
                } else {
                    mobility += MOBILITY_BONUS / 2; // Half bonus for empty squares
                }
            }
        }

        return mobility;
    }

    /**
     * ✅ Enhanced outpost bonus
     */
    private int getEnhancedOutpostBonus(GameState state, int square, int height, boolean isRed) {
        int rank = GameState.rank(square);

        if (isRed && rank <= 2) {
            // Enhanced deep penetration bonus
            int bonus = (2 - rank) * 40; // Erhöht von 30

            // ✅ Extra bonus if supported
            if (hasAdjacentFriendly(state, square, isRed)) {
                bonus += DEEP_PENETRATION_BONUS;
            }

            return bonus;
        } else if (!isRed && rank >= 4) {
            int bonus = (rank - 4) * 40; // Erhöht von 30

            if (hasAdjacentFriendly(state, square, isRed)) {
                bonus += DEEP_PENETRATION_BONUS;
            }

            return bonus;
        }

        return 0;
    }

    /**
     * ✅ Enhanced tactical bonus
     */
    private int getEnhancedTacticalBonus(GameState state, int square, int height, boolean isRed) {
        int bonus = 0;

        // Enhanced bonus for pieces that can attack enemy guard
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        if (enemyGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(enemyGuard);
            if (canAttack(square, guardPos, height)) {
                bonus += 150; // Erhöht von 100
            }
        }

        // ✅ NEW: Bonus for supporting attacks
        int supportedAttacks = countSupportedAttacks(state, square, height, isRed);
        bonus += supportedAttacks * 25;

        return bonus;
    }

    /**
     * ✅ Count supported attacks
     */
    private int countSupportedAttacks(GameState state, int square, int height, boolean isRed) {
        int supportedAttacks = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            for (int dist = 1; dist <= height; dist++) {
                int target = square + dir * dist;
                if (!GameState.isOnBoard(target)) break;
                if (Math.abs(dir) == 1 && GameState.rank(square) != GameState.rank(target)) break;

                if (isEnemyPiece(target, state, isRed)) {
                    // Check if this attack is supported by other pieces
                    if (countSupportingPieces(state, target, isRed) > 0) {
                        supportedAttacks++;
                    }
                    break;
                }

                if (isOccupied(target, state)) break;
            }
        }

        return supportedAttacks;
    }

    /**
     * ✅ Count supporting pieces for a square
     */
    private int countSupportingPieces(GameState state, int target, boolean isRed) {
        int supporters = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0 && canAttack(i, target, height)) {
                supporters++;
            }
        }

        return supporters;
    }

    /**
     * ✅ Enhanced square control evaluation
     */
    private int evaluateEnhancedSquareControl(GameState state, int square, int height, boolean isRed) {
        int control = 0;
        int[] directions = {-1, 1, -7, 7};

        // Check squares this piece controls
        for (int dir : directions) {
            for (int dist = 1; dist <= height; dist++) {
                int controlled = square + dir * dist;
                if (!GameState.isOnBoard(controlled)) break;

                if (Math.abs(dir) == 1 && GameState.rank(square) != GameState.rank(controlled)) break;

                if (isOccupied(controlled, state)) break;

                // Enhanced bonus for controlling central squares
                if (isCentralSquare(controlled)) {
                    control += 8; // Erhöht von 5
                }

                // Enhanced bonus for controlling enemy territory
                if (isEnemyTerritory(controlled, isRed)) {
                    control += 5; // Erhöht von 3
                }
            }
        }

        return control;
    }

    // === LEGACY HELPER METHODS ===

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
     * Get development bonus (pieces off back rank)
     */
    private int getDevelopmentBonus(int square, boolean isRed) {
        int rank = GameState.rank(square);

        if (isRed && rank != 6) {
            return 35; // Erhöht von 25
        } else if (!isRed && rank != 0) {
            return 35; // Erhöht von 25
        }

        return 0;
    }

    /**
     * Get tablebase bonus (perfect endgame knowledge)
     */
    private int getTablebaseBonus(GameState state, int square, int height, boolean isRed) {
        // TODO: Implement tablebase queries
        return getEnhancedEndgameBonus(state, square, height, isRed) / 2;
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
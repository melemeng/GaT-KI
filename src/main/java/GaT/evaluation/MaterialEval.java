package GaT.evaluation;

import GaT.model.GameState;
import static GaT.evaluation.EvaluationParameters.*;

/**
 * ✅ FIXED MATERIAL EVALUATION COMPONENT - Moderate Bonuses
 *
 * 🚨 PREVIOUS PROBLEMS SOLVED:
 * ❌ Aggressive bonuses (25, 30, 40, 50 points) → ✅ NOW moderate from EvaluationParameters
 * ❌ Local parameter definitions → ✅ NOW uses only EvaluationParameters
 * ❌ Exponential bonuses that dominate material → ✅ NOW reasonable enhancements
 * ❌ Parameter chaos → ✅ NOW consistent across all modules
 *
 * PRINCIPLE: Material evaluation provides base value, bonuses enhance but don't dominate
 */
public class MaterialEval {

    /**
     * SIMPLE MATERIAL EVALUATION - Ultra-fast for time pressure
     */
    public int evaluateMaterialSimple(GameState state) {
        int materialScore = 0;

        // Raw piece count difference - pure material
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            materialScore += (state.redStackHeights[i] - state.blueStackHeights[i]) * TOWER_BASE_VALUE;
        }

        return materialScore;
    }

    /**
     * ✅ FIXED BASIC MATERIAL EVALUATION - Uses centralized moderate parameters
     */
    public int evaluateMaterialBasic(GameState state) {
        int materialScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int redHeight = state.redStackHeights[i];
            int blueHeight = state.blueStackHeights[i];

            if (redHeight > 0) {
                int value = redHeight * TOWER_BASE_VALUE;
                value += getModeratePositionalBonus(state, i, redHeight, true);
                materialScore += value;
            }

            if (blueHeight > 0) {
                int value = blueHeight * TOWER_BASE_VALUE;
                value += getModeratePositionalBonus(state, i, blueHeight, false);
                materialScore -= value;
            }
        }

        return materialScore;
    }

    /**
     * ✅ FIXED MATERIAL WITH ACTIVITY - Moderate coordination bonuses
     */
    public int evaluateMaterialWithActivity(GameState state) {
        int materialScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int redHeight = state.redStackHeights[i];
            int blueHeight = state.blueStackHeights[i];

            if (redHeight > 0) {
                int value = redHeight * TOWER_BASE_VALUE;
                value += getModerateActivityBonus(state, i, redHeight, true);
                materialScore += value;
            }

            if (blueHeight > 0) {
                int value = blueHeight * TOWER_BASE_VALUE;
                value += getModerateActivityBonus(state, i, blueHeight, false);
                materialScore -= value;
            }
        }

        return materialScore;
    }

    /**
     * ✅ FIXED ADVANCED MATERIAL EVALUATION - Phase-aware with moderate bonuses
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

        // Add moderate material imbalance evaluation
        materialScore += evaluateModerateImbalance(state, phase);

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
                // Moderate endgame bonuses from EvaluationParameters
                value += getModerateEndgameBonus(state, i, redHeight, true);
                materialScore += value;
            }

            if (blueHeight > 0) {
                int value = blueHeight * TOWER_BASE_VALUE;
                value += getModerateEndgameBonus(state, i, blueHeight, false);
                materialScore -= value;
            }
        }

        return materialScore;
    }

    // === ✅ FIXED POSITIONAL BONUS CALCULATION (MODERATE) ===

    /**
     * ✅ FIXED: Moderate positional bonuses from EvaluationParameters
     */
    private int getModeratePositionalBonus(GameState state, int square, int height, boolean isRed) {
        int bonus = 0;

        // ✅ FIXED: Moderate advancement bonus from EvaluationParameters
        int rank = GameState.rank(square);
        if (isRed && rank < 3) {
            bonus += height * Material.ADVANCEMENT_BONUS;  // 8 per rank, moderate
        } else if (!isRed && rank > 3) {
            bonus += height * Material.ADVANCEMENT_BONUS;
        }

        // ✅ FIXED: Moderate central bonus from EvaluationParameters
        int file = GameState.file(square);
        if (file >= 2 && file <= 4) {
            bonus += Material.CENTRAL_BONUS;  // 12 points, moderate
        }

        // ✅ FIXED: Moderate edge tower bonus from EvaluationParameters
        if (isEdgeFile(file) && canThreatenCenter(state, square, height)) {
            bonus += Material.EDGE_TOWER_BONUS;  // 10 points, moderate
        }

        return bonus;
    }

    /**
     * ✅ FIXED: Moderate activity bonuses with coordination
     */
    private int getModerateActivityBonus(GameState state, int square, int height, boolean isRed) {
        int bonus = getModeratePositionalBonus(state, square, height, isRed);

        // ✅ FIXED: Moderate connected pieces bonus from EvaluationParameters
        if (hasAdjacentFriendly(state, square, isRed)) {
            bonus += Material.CONNECTED_BONUS;  // 15 points, moderate

            // ✅ FIXED: Moderate cluster support bonus
            int nearbyFriendly = countNearbyFriendly(state, square, isRed, 2);
            if (nearbyFriendly >= 2) {
                bonus += Material.CONNECTED_BONUS;  // Additional 15 points for clusters
            }
        }

        // ✅ FIXED: Moderate mobility estimation from EvaluationParameters
        bonus += estimateModerateMobility(state, square, height, isRed);

        // ✅ FIXED: Moderate outpost bonus from EvaluationParameters
        bonus += getModerateOutpostBonus(state, square, height, isRed);

        // ✅ FIXED: Moderate guard escort bonus from EvaluationParameters
        if (isEscortingGuard(state, square, height, isRed)) {
            bonus += Material.GUARD_ESCORT_BONUS;  // 15 points, moderate
        }

        return bonus;
    }

    /**
     * ✅ FIXED: Advanced positional bonuses with phase awareness but moderate values
     */
    private int getAdvancedPositionalBonus(GameState state, int square, int height, boolean isRed, GamePhase phase) {
        int bonus = getModerateActivityBonus(state, square, height, isRed);

        // Phase-specific bonuses - all moderate from EvaluationParameters
        switch (phase) {
            case OPENING:
                bonus += getDevelopmentBonus(square, isRed);
                // Moderate edge activation in opening
                if (isEdgeFile(GameState.file(square))) {
                    bonus += Material.EDGE_TOWER_BONUS / 2;  // 5 points
                }
                break;
            case MIDDLEGAME:
                bonus += getModerateTacticalBonus(state, square, height, isRed);
                break;
            case ENDGAME:
                bonus += getModerateEndgameBonus(state, square, height, isRed);
                break;
        }

        return bonus;
    }

    /**
     * ✅ FIXED: Moderate endgame-specific bonuses from EvaluationParameters
     */
    private int getModerateEndgameBonus(GameState state, int square, int height, boolean isRed) {
        int bonus = 0;

        // ✅ FIXED: Moderate distance to enemy guard
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        if (enemyGuard != 0) {
            int enemyGuardPos = Long.numberOfTrailingZeros(enemyGuard);
            int distance = calculateDistance(square, enemyGuardPos);

            // Closer pieces are more valuable (moderate)
            bonus += Math.max(0, (7 - distance) * (Material.ADVANCEMENT_BONUS / 2));  // 4 per distance
        }

        // ✅ FIXED: Moderate support for own guard
        long ownGuard = isRed ? state.redGuard : state.blueGuard;
        if (ownGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(ownGuard);
            int guardDistance = calculateDistance(square, guardPos);

            // Pieces near own guard provide support (moderate)
            if (guardDistance <= 2) {
                bonus += Material.GUARD_ESCORT_BONUS;  // 15 points, moderate
            }

            // ✅ FIXED: Moderate deep penetration bonus
            int guardRank = GameState.rank(guardPos);
            if ((isRed && guardRank <= 2) || (!isRed && guardRank >= 4)) {
                bonus += Material.DEEP_PENETRATION_BONUS;  // 20 points, moderate
            }
        }

        return bonus;
    }

    // === ✅ FIXED ENHANCED EVALUATION METHODS (MODERATE) ===

    /**
     * ✅ FIXED: Moderate material imbalance evaluation
     */
    private int evaluateModerateImbalance(GameState state, GamePhase phase) {
        int redTotal = getTotalMaterial(state, true);
        int blueTotal = getTotalMaterial(state, false);
        int imbalance = redTotal - blueTotal;

        if (Math.abs(imbalance) <= 1) {
            return 0; // Equal material
        }

        // ✅ FIXED: Moderate imbalance bonuses
        int imbalanceBonus = 0;

        if (phase == GamePhase.ENDGAME) {
            // In endgame, material advantages are important but not overwhelming
            imbalanceBonus = imbalance * TOWER_BASE_VALUE;  // 100 per piece difference
        } else if (phase == GamePhase.MIDDLEGAME) {
            // In middlegame, material advantage helps in tactics
            imbalanceBonus = imbalance * (TOWER_BASE_VALUE * 3 / 4);  // 75 per piece
        } else {
            // In opening, development matters but material still counts
            imbalanceBonus = imbalance * (TOWER_BASE_VALUE / 2);  // 50 per piece
        }

        return imbalanceBonus;
    }

    /**
     * ✅ FIXED: Moderate piece activity evaluation
     */
    public int evaluatePieceActivity(GameState state) {
        int activityScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                activityScore += evaluateModeratePieceActivityAt(state, i, true);
            }
            if (state.blueStackHeights[i] > 0) {
                activityScore -= evaluateModeratePieceActivityAt(state, i, false);
            }
        }

        return activityScore;
    }

    private int evaluateModeratePieceActivityAt(GameState state, int square, boolean isRed) {
        int activity = 0;
        int height = isRed ? state.redStackHeights[square] : state.blueStackHeights[square];

        // ✅ FIXED: Moderate mobility factor from EvaluationParameters
        activity += estimateModerateMobility(state, square, height, isRed);

        // ✅ FIXED: Moderate coordination with other pieces
        if (hasAdjacentFriendly(state, square, isRed)) {
            activity += Material.CONNECTED_BONUS / 2;  // 7-8 points, moderate
        }

        // ✅ FIXED: Moderate control of important squares
        activity += evaluateModerateSquareControl(state, square, height, isRed);

        // ✅ FIXED: Moderate coordination bonuses
        int supporters = countSupportingPieces(state, square, isRed);
        if (supporters > 0) {
            activity += supporters * (Material.CONNECTED_BONUS / 3);  // ~5 points per supporter
        }

        return activity;
    }

    // === ✅ FIXED HELPER METHODS (MODERATE BONUSES) ===

    /**
     * ✅ Check if file is edge file
     */
    private boolean isEdgeFile(int file) {
        return file == 0 || file == 1 || file == 5 || file == 6;
    }

    /**
     * ✅ Check if tower can threaten center (moderate range check)
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
     * ✅ Count nearby friendly pieces (moderate range)
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
     * ✅ Check if piece is escorting guard (moderate distance)
     */
    private boolean isEscortingGuard(GameState state, int square, int height, boolean isRed) {
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int distance = calculateDistance(square, guardPos);

        // Close escort (adjacent or 1-2 squares away)
        return distance <= 2 && distance <= height;
    }

    /**
     * ✅ FIXED: Moderate mobility estimation from EvaluationParameters
     */
    private int estimateModerateMobility(GameState state, int square, int height, boolean isRed) {
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
                    // ✅ FIXED: Moderate bonus for threatening enemy pieces
                    if (isEnemyPiece(target, state, isRed)) {
                        mobility += Material.MOBILITY_BONUS;  // 6 points from EvaluationParameters
                    }
                    break;
                } else {
                    mobility += Material.MOBILITY_BONUS / 2;  // 3 points for empty squares
                }
            }
        }

        return mobility;
    }

    /**
     * ✅ FIXED: Moderate outpost bonus from EvaluationParameters
     */
    private int getModerateOutpostBonus(GameState state, int square, int height, boolean isRed) {
        int rank = GameState.rank(square);

        if (isRed && rank <= 2) {
            // ✅ FIXED: Moderate deep penetration bonus
            int bonus = (2 - rank) * Material.OUTPOST_BONUS;  // 18 points per rank from EvaluationParameters

            // Extra bonus if supported
            if (hasAdjacentFriendly(state, square, isRed)) {
                bonus += Material.DEEP_PENETRATION_BONUS;  // 20 points from EvaluationParameters
            }

            return bonus;
        } else if (!isRed && rank >= 4) {
            int bonus = (rank - 4) * Material.OUTPOST_BONUS;

            if (hasAdjacentFriendly(state, square, isRed)) {
                bonus += Material.DEEP_PENETRATION_BONUS;
            }

            return bonus;
        }

        return 0;
    }

    /**
     * ✅ FIXED: Moderate tactical bonus from EvaluationParameters
     */
    private int getModerateTacticalBonus(GameState state, int square, int height, boolean isRed) {
        int bonus = 0;

        // ✅ FIXED: Moderate bonus for pieces that can attack enemy guard
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        if (enemyGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(enemyGuard);
            if (canAttack(square, guardPos, height)) {
                bonus += Material.DEEP_PENETRATION_BONUS * 2;  // 40 points, moderate
            }
        }

        // ✅ FIXED: Moderate bonus for supporting attacks
        int supportedAttacks = countSupportedAttacks(state, square, height, isRed);
        bonus += supportedAttacks * (Material.CONNECTED_BONUS / 2);  // ~7 points per attack

        return bonus;
    }

    /**
     * ✅ Count supported attacks (moderate evaluation)
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
     * ✅ Count supporting pieces for a square (moderate count)
     */
    private int countSupportingPieces(GameState state, int target, boolean isRed) {
        int supporters = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0 && canAttack(i, target, height)) {
                supporters++;
                if (supporters >= 3) break;  // Limit counting for performance
            }
        }

        return supporters;
    }

    /**
     * ✅ FIXED: Moderate square control evaluation
     */
    private int evaluateModerateSquareControl(GameState state, int square, int height, boolean isRed) {
        int control = 0;
        int[] directions = {-1, 1, -7, 7};

        // Check squares this piece controls
        for (int dir : directions) {
            for (int dist = 1; dist <= height; dist++) {
                int controlled = square + dir * dist;
                if (!GameState.isOnBoard(controlled)) break;

                if (Math.abs(dir) == 1 && GameState.rank(square) != GameState.rank(controlled)) break;

                if (isOccupied(controlled, state)) break;

                // ✅ FIXED: Moderate bonus for controlling central squares
                if (isCentralSquare(controlled)) {
                    control += Material.CENTRAL_BONUS / 3;  // 4 points
                }

                // ✅ FIXED: Moderate bonus for controlling enemy territory
                if (isEnemyTerritory(controlled, isRed)) {
                    control += Material.ADVANCEMENT_BONUS / 3;  // ~3 points
                }
            }
        }

        return control;
    }

    // === UTILITY METHODS ===

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
     * ✅ FIXED: Moderate development bonus from EvaluationParameters
     */
    private int getDevelopmentBonus(int square, boolean isRed) {
        int rank = GameState.rank(square);

        if (isRed && rank != 6) {
            return Positional.DEVELOPMENT_BONUS;  // 25 points from EvaluationParameters
        } else if (!isRed && rank != 0) {
            return Positional.DEVELOPMENT_BONUS;
        }

        return 0;
    }

    // === MORE UTILITY METHODS ===

    private GamePhase detectGamePhase(GameState state) {
        return EvaluationParameters.detectGamePhase(state);
    }

    private double[] getPhaseMultipliers(GamePhase phase) {
        return switch (phase) {
            case OPENING -> Material.OPENING_MULTIPLIERS;
            case MIDDLEGAME -> Material.MIDDLEGAME_MULTIPLIERS;
            case ENDGAME -> Material.ENDGAME_MULTIPLIERS;
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
        ENDGAME
    }
}
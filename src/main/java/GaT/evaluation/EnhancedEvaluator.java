package GaT.evaluation;

import GaT.model.GameState;
import GaT.search.MoveGenerator;
import GaT.model.Move;
import java.util.List;

/**
 * ENHANCED EVALUATOR for Turm & Wächter
 *
 * Adds competitive features observed from strong opponents:
 * 1. Height-based mobility bonuses
 * 2. Tower cluster analysis
 * 3. Path blocking evaluation
 * 4. Castle approach control
 * 5. Guard escort bonuses
 */
public class EnhancedEvaluator extends Evaluator {

    // === ENHANCED PARAMETERS ===
    private static final int HEIGHT_MOBILITY_BONUS = 3;    // Bonus per height per possible direction
    private static final int CLUSTER_REACH_BONUS = 8;      // Bonus when towers can reach each other
    private static final int PATH_BLOCKING_BONUS = 15;     // Bonus for blocking enemy guard path
    private static final int CASTLE_APPROACH_CONTROL = 25; // Bonus for controlling squares near enemy castle
    private static final int GUARD_ESCORT_BONUS = 12;      // Bonus for towers escorting advancing guard
    private static final int STACK_EFFICIENCY_BONUS = 6;   // Bonus for optimal stack heights
    private static final int TEMPO_BONUS = 10;             // Bonus for forcing moves

    @Override
    public int evaluate(GameState state) {
        // Get base evaluation
        int baseScore = super.evaluate(state);

        // Add enhanced features
        int enhancedScore = 0;
        enhancedScore += evaluateHeightMobility(state);
        enhancedScore += evaluateTowerClusters(state);
        enhancedScore += evaluatePathBlocking(state);
        enhancedScore += evaluateCastleApproachControl(state);
        enhancedScore += evaluateGuardEscort(state);
        enhancedScore += evaluateStackEfficiency(state);

        // Enhanced features get 15% weight, base evaluation 85%
        return (baseScore * 85 + enhancedScore * 15) / 100;
    }

    /**
     * HEIGHT-BASED MOBILITY: Higher towers are more valuable due to increased mobility
     * (Inspired by competitor's "Zugpotential und Mobilität" feature)
     */
    private int evaluateHeightMobility(GameState state) {
        int score = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // Red towers
            if (state.redStackHeights[i] > 0) {
                int height = state.redStackHeights[i];
                int mobilityValue = height * countMobilityDirections(state, i, height) * HEIGHT_MOBILITY_BONUS;
                score += mobilityValue;
            }

            // Blue towers
            if (state.blueStackHeights[i] > 0) {
                int height = state.blueStackHeights[i];
                int mobilityValue = height * countMobilityDirections(state, i, height) * HEIGHT_MOBILITY_BONUS;
                score -= mobilityValue;
            }
        }

        return score;
    }

    /**
     * Count directions where a tower can move (not blocked immediately)
     */
    private int countMobilityDirections(GameState state, int square, int height) {
        int directions = 0;
        int[] dirArray = {-1, 1, -7, 7}; // left, right, up, down

        for (int dir : dirArray) {
            int target = square + dir;
            if (isValidMove(square, target, dir)) {
                // Check if can move at least one square in this direction
                if (isSquareEmpty(state, target) || isEnemyPiece(state, target, square)) {
                    directions++;
                }
            }
        }

        return directions;
    }

    /**
     * TOWER CLUSTERS: Bonus for towers that can reach each other
     * (Inspired by competitor's "Clusterbonus" feature)
     */
    private int evaluateTowerClusters(GameState state) {
        int score = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // Red clusters
            if (state.redStackHeights[i] > 0) {
                int reachableTowers = countReachableFriendlyTowers(state, i, true);
                score += reachableTowers * CLUSTER_REACH_BONUS;
            }

            // Blue clusters
            if (state.blueStackHeights[i] > 0) {
                int reachableTowers = countReachableFriendlyTowers(state, i, false);
                score -= reachableTowers * CLUSTER_REACH_BONUS;
            }
        }

        return score;
    }

    /**
     * Count friendly towers this piece can reach in one move
     */
    private int countReachableFriendlyTowers(GameState state, int square, boolean isRed) {
        int reachable = 0;
        int height = isRed ? state.redStackHeights[square] : state.blueStackHeights[square];
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            for (int dist = 1; dist <= height; dist++) {
                int target = square + dir * dist;
                if (!isValidMove(square, target, dir)) break;

                // Found friendly tower?
                if ((isRed && state.redStackHeights[target] > 0) ||
                        (!isRed && state.blueStackHeights[target] > 0)) {
                    reachable++;
                    break;
                }

                // Path blocked?
                if (!isSquareEmpty(state, target)) break;
            }
        }

        return reachable;
    }

    /**
     * PATH BLOCKING: Bonus for controlling squares that block enemy guard advancement
     */
    private int evaluatePathBlocking(GameState state) {
        int score = 0;

        // Red blocking blue guard path to D7
        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            score += evaluatePathBlockingForGuard(state, blueGuardPos, RED_CASTLE, true);
        }

        // Blue blocking red guard path to D1
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            score -= evaluatePathBlockingForGuard(state, redGuardPos, BLUE_CASTLE, false);
        }

        return score;
    }

    /**
     * Evaluate how well a player is blocking enemy guard's path to castle
     */
    private int evaluatePathBlockingForGuard(GameState state, int guardPos, int targetCastle, boolean blockingPlayer) {
        int blockingScore = 0;

        // Key squares on path to castle (simplified - D-file squares)
        int guardRank = GameState.rank(guardPos);
        int targetRank = GameState.rank(targetCastle);

        int startRank = Math.min(guardRank, targetRank);
        int endRank = Math.max(guardRank, targetRank);

        for (int rank = startRank; rank <= endRank; rank++) {
            int square = GameState.getIndex(rank, 3); // D-file squares

            // Check if blocking player controls this square
            if (blockingPlayer) {
                if (state.redStackHeights[square] > 0) {
                    blockingScore += PATH_BLOCKING_BONUS;
                }
            } else {
                if (state.blueStackHeights[square] > 0) {
                    blockingScore += PATH_BLOCKING_BONUS;
                }
            }
        }

        return blockingScore;
    }

    /**
     * CASTLE APPROACH CONTROL: Bonus for controlling squares near enemy castle
     */
    private int evaluateCastleApproachControl(GameState state) {
        int score = 0;

        // Squares around blue castle (D1)
        int[] blueCastleApproach = {
                GameState.getIndex(0, 2), GameState.getIndex(0, 4), // C1, E1
                GameState.getIndex(1, 3), // D2
                GameState.getIndex(1, 2), GameState.getIndex(1, 4)  // C2, E2
        };

        // Red controlling blue castle approach
        for (int square : blueCastleApproach) {
            if (square >= 0 && square < GameState.NUM_SQUARES) {
                if (state.redStackHeights[square] > 0) {
                    score += CASTLE_APPROACH_CONTROL;
                }
            }
        }

        // Squares around red castle (D7)
        int[] redCastleApproach = {
                GameState.getIndex(6, 2), GameState.getIndex(6, 4), // C7, E7
                GameState.getIndex(5, 3), // D6
                GameState.getIndex(5, 2), GameState.getIndex(5, 4)  // C6, E6
        };

        // Blue controlling red castle approach
        for (int square : redCastleApproach) {
            if (square >= 0 && square < GameState.NUM_SQUARES) {
                if (state.blueStackHeights[square] > 0) {
                    score -= CASTLE_APPROACH_CONTROL;
                }
            }
        }

        return score;
    }

    /**
     * GUARD ESCORT: Bonus for towers escorting advancing guard
     */
    private int evaluateGuardEscort(GameState state) {
        int score = 0;

        // Red guard escort
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int rank = GameState.rank(guardPos);

            // Only count escort if guard is advancing (past middle)
            if (rank <= 3) {
                int escortTowers = countEscortTowers(state, guardPos, true);
                score += escortTowers * GUARD_ESCORT_BONUS;
            }
        }

        // Blue guard escort
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int rank = GameState.rank(guardPos);

            // Only count escort if guard is advancing (past middle)
            if (rank >= 3) {
                int escortTowers = countEscortTowers(state, guardPos, false);
                score -= escortTowers * GUARD_ESCORT_BONUS;
            }
        }

        return score;
    }

    /**
     * Count towers providing escort to advancing guard
     */
    private int countEscortTowers(GameState state, int guardPos, boolean isRed) {
        int escorts = 0;

        // Check 2-square radius around guard
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) {
                int distance = calculateManhattanDistance(guardPos, i);
                if (distance <= 2) {
                    escorts++;
                }
            }
        }

        return escorts;
    }

    /**
     * STACK EFFICIENCY: Bonus for optimal stack heights (not too high, not too spread)
     */
    private int evaluateStackEfficiency(GameState state) {
        int score = 0;

        // Count stack distribution
        int redStacks = 0, blueStacks = 0;
        int redTotal = 0, blueTotal = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                redStacks++;
                redTotal += state.redStackHeights[i];

                // Bonus for medium-height stacks (2-4)
                int height = state.redStackHeights[i];
                if (height >= 2 && height <= 4) {
                    score += STACK_EFFICIENCY_BONUS;
                }
            }

            if (state.blueStackHeights[i] > 0) {
                blueStacks++;
                blueTotal += state.blueStackHeights[i];

                int height = state.blueStackHeights[i];
                if (height >= 2 && height <= 4) {
                    score -= STACK_EFFICIENCY_BONUS;
                }
            }
        }

        return score;
    }

    // === UTILITY METHODS ===

    private boolean isEnemyPiece(GameState state, int square, int fromSquare) {
        // Determine if fromSquare has red or blue piece
        boolean fromIsRed = state.redStackHeights[fromSquare] > 0 ||
                (state.redGuard & GameState.bit(fromSquare)) != 0;

        if (fromIsRed) {
            return state.blueStackHeights[square] > 0 ||
                    (state.blueGuard & GameState.bit(square)) != 0;
        } else {
            return state.redStackHeights[square] > 0 ||
                    (state.redGuard & GameState.bit(square)) != 0;
        }
    }

    private int calculateManhattanDistance(int from, int to) {
        return Math.abs(GameState.rank(from) - GameState.rank(to)) +
                Math.abs(GameState.file(from) - GameState.file(to));
    }

    // === CONSTANTS FOR CASTLE POSITIONS ===
    private static final int RED_CASTLE = GameState.getIndex(6, 3);   // D7
    private static final int BLUE_CASTLE = GameState.getIndex(0, 3);  // D1

    @Override
    public String getEvaluationBreakdown(GameState state) {
        int baseScore = super.evaluate(state);
        int heightMobility = evaluateHeightMobility(state);
        int clusters = evaluateTowerClusters(state);
        int pathBlocking = evaluatePathBlocking(state);
        int castleControl = evaluateCastleApproachControl(state);
        int guardEscort = evaluateGuardEscort(state);
        int stackEfficiency = evaluateStackEfficiency(state);

        int enhancedTotal = heightMobility + clusters + pathBlocking +
                castleControl + guardEscort + stackEfficiency;

        int finalScore = (baseScore * 85 + enhancedTotal * 15) / 100;

        StringBuilder sb = new StringBuilder();
        sb.append(super.getEvaluationBreakdown(state));
        sb.append("\n=== ENHANCED FEATURES ===\n");
        sb.append(String.format("Height Mobility:    %+5d\n", heightMobility));
        sb.append(String.format("Tower Clusters:     %+5d\n", clusters));
        sb.append(String.format("Path Blocking:      %+5d\n", pathBlocking));
        sb.append(String.format("Castle Control:     %+5d\n", castleControl));
        sb.append(String.format("Guard Escort:       %+5d\n", guardEscort));
        sb.append(String.format("Stack Efficiency:   %+5d\n", stackEfficiency));
        sb.append("================================\n");
        sb.append(String.format("Enhanced Total:     %+5d × 15%% = %+6d\n",
                enhancedTotal, enhancedTotal * 15 / 100));
        sb.append(String.format("FINAL SCORE:        %+6d\n", finalScore));

        return sb.toString();
    }
}
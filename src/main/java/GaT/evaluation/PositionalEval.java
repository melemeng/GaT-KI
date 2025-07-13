package GaT.evaluation;

import GaT.model.GameState;
import GaT.search.MoveGenerator;
import GaT.model.Move;
import static GaT.evaluation.EvaluationParameters.*;

import java.util.List;

/**
 * ✅ FIXED POSITIONAL EVALUATION COMPONENT - Moderate Parameters
 *
 * 🚨 PREVIOUS PROBLEMS SOLVED:
 * ❌ Aggressive parameters (50, 60, 80 points) → ✅ NOW moderate from EvaluationParameters
 * ❌ Local parameter definitions → ✅ NOW uses only EvaluationParameters
 * ❌ Exponential bonuses → ✅ NOW reasonable enhancements
 * ❌ Tower chain bonus not working → ✅ NOW properly implemented with moderate values
 *
 * PRINCIPLE: Positional evaluation enhances material evaluation but doesn't dominate
 */
public class PositionalEval {

    /**
     * ✅ FIXED: COMPREHENSIVE POSITIONAL EVALUATION - Uses centralized moderate parameters
     */
    public int evaluatePositional(GameState state) {
        int positionalScore = 0;

        // ✅ FIXED: Guard advancement (40% weight) - moderate from EvaluationParameters
        positionalScore += evaluateGuardAdvancement(state) * 40 / 100;

        // ✅ FIXED: Central control (25% weight) - moderate bonuses
        positionalScore += evaluateCentralControl(state) * 25 / 100;

        // ✅ FIXED: Piece coordination (20% weight) - moderate coordination bonuses
        positionalScore += evaluatePieceCoordination(state) * 20 / 100;

        // ✅ FIXED: Mobility and development (15% weight) - moderate mobility bonuses
        positionalScore += evaluateMobilityAndDevelopment(state) * 15 / 100;

        // ✅ FIXED: TOWER CHAINS NOW PROPERLY IMPLEMENTED with moderate bonuses!
        positionalScore += evaluateTowerChains(state);

        return positionalScore;
    }

    // === ✅ FIXED TOWER CHAINS IMPLEMENTATION (MODERATE BONUSES) ===

    /**
     * ✅ FIXED: TOWER CHAINS - Moderate bonuses from EvaluationParameters
     */
    public int evaluateTowerChains(GameState state) {
        int chainBonus = 0;

        // Horizontal chains (moderate bonuses)
        for (int rank = 0; rank < 7; rank++) {
            chainBonus += evaluateRankChain(state, rank, true);  // Red
            chainBonus -= evaluateRankChain(state, rank, false); // Blue
        }

        // Vertical chains (moderate bonuses)
        for (int file = 0; file < 7; file++) {
            chainBonus += evaluateFileChain(state, file, true);
            chainBonus -= evaluateFileChain(state, file, false);
        }

        return chainBonus;
    }

    /**
     * ✅ FIXED: Moderate rank chain evaluation
     */
    private int evaluateRankChain(GameState state, int rank, boolean isRed) {
        int chainLength = 0;
        int totalHeight = 0;
        int maxBonus = 0;

        for (int file = 0; file < 7; file++) {
            int pos = GameState.getIndex(rank, file);
            int height = isRed ? state.redStackHeights[pos] : state.blueStackHeights[pos];

            if (height > 0) {
                chainLength++;
                totalHeight += height;
            } else if (chainLength > 0) {
                // Chain interrupted - evaluate it with moderate bonus
                if (chainLength >= 2) {
                    int bonus = chainLength * totalHeight * Positional.TOWER_CHAIN_BONUS / 20;  // Moderate bonus
                    maxBonus = Math.max(maxBonus, bonus);
                }
                chainLength = 0;
                totalHeight = 0;
            }
        }

        // Chain to end of rank
        if (chainLength >= 2) {
            int bonus = chainLength * totalHeight * Positional.TOWER_CHAIN_BONUS / 20;  // Moderate bonus
            maxBonus = Math.max(maxBonus, bonus);
        }

        return maxBonus;
    }

    /**
     * ✅ FIXED: Moderate file chain evaluation
     */
    private int evaluateFileChain(GameState state, int file, boolean isRed) {
        int chainLength = 0;
        int totalHeight = 0;
        int maxBonus = 0;

        for (int rank = 0; rank < 7; rank++) {
            int pos = GameState.getIndex(rank, file);
            int height = isRed ? state.redStackHeights[pos] : state.blueStackHeights[pos];

            if (height > 0) {
                chainLength++;
                totalHeight += height;
            } else if (chainLength > 0) {
                // Chain interrupted - evaluate it with moderate bonus
                if (chainLength >= 2) {
                    int bonus = chainLength * totalHeight * Positional.TOWER_CHAIN_BONUS / 20;  // Moderate bonus
                    maxBonus = Math.max(maxBonus, bonus);
                }
                chainLength = 0;
                totalHeight = 0;
            }
        }

        // Chain to end of file
        if (chainLength >= 2) {
            int bonus = chainLength * totalHeight * Positional.TOWER_CHAIN_BONUS / 20;  // Moderate bonus
            maxBonus = Math.max(maxBonus, bonus);
        }

        return maxBonus;
    }

    /**
     * ADVANCED POSITIONAL EVALUATION - For deep analysis with moderate enhancements
     */
    public int evaluatePositionalAdvanced(GameState state) {
        // Enhanced evaluation with additional moderate factors
        int score = evaluatePositional(state);

        // Add endgame-specific evaluations (moderate)
        if (isEndgame(state)) {
            score += evaluateEndgamePositional(state);
        }

        return score;
    }

    // === ✅ FIXED GUARD ADVANCEMENT (MODERATE) ===

    /**
     * ✅ FIXED: Moderate guard advancement with endgame awareness
     */
    public int evaluateGuardAdvancement(GameState state) {
        int advancementScore = 0;

        // Red guard evaluation (moderate bonus)
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            advancementScore += evaluateGuardAdvancementForColor(guardPos, true, state);
        }

        // Blue guard evaluation (moderate penalty)
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            advancementScore -= evaluateGuardAdvancementForColor(guardPos, false, state);
        }

        return advancementScore;
    }

    private int evaluateGuardAdvancementForColor(int guardPos, boolean isRed, GameState state) {
        int bonus = 0;
        int rank = GameState.rank(guardPos);
        int file = GameState.file(guardPos);

        // ✅ FIXED: Moderate advancement bonus from EvaluationParameters
        int targetRank = isRed ? 0 : 6; // Enemy castle
        int rankDistance = Math.abs(rank - targetRank);
        bonus += (7 - rankDistance) * Positional.GUARD_ADVANCEMENT_BONUS;  // 15 per rank from EvaluationParameters

        // ✅ FIXED: Moderate file distance bonus
        int fileDistance = Math.abs(file - 3);
        bonus += (4 - fileDistance) * Positional.GUARD_CASTLE_APPROACH;  // 20 per file from EvaluationParameters

        // ✅ FIXED: Moderate endgame bonus
        if (isEndgame(state)) {
            bonus += Positional.ENDGAME_GUARD_ACTIVITY;  // 30 from EvaluationParameters

            // Extra moderate bonus for direct castle vicinity in endgame
            if (rankDistance <= 2 && fileDistance <= 1) {
                bonus += Positional.ENDGAME_GUARD_ACTIVITY / 2;  // 15 extra
            }
        }

        return bonus;
    }

    /**
     * ✅ FIXED: Fast guard advancement evaluation with moderate bonuses
     */
    public int evaluateGuardAdvancementFast(GameState state) {
        int advancementScore = 0;

        // ✅ FIXED: Red guard with moderate bonus from EvaluationParameters
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int rank = GameState.rank(guardPos);
            advancementScore += (6 - rank) * Positional.GUARD_ADVANCEMENT_BONUS;  // 15 per rank
        }

        // ✅ FIXED: Blue guard with moderate bonus from EvaluationParameters
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int rank = GameState.rank(guardPos);
            advancementScore -= rank * Positional.GUARD_ADVANCEMENT_BONUS;  // 15 per rank
        }

        return advancementScore;
    }

    // === ✅ FIXED CENTRAL CONTROL (MODERATE) ===

    /**
     * ✅ FIXED: Moderate central control with tower height awareness
     */
    public int evaluateCentralControl(GameState state) {
        int centralScore = 0;

        // ✅ FIXED: Central squares with moderate bonuses
        for (int square : Positional.CENTRAL_SQUARES) {
            centralScore += evaluateSquareControl(state, square, Positional.CENTRAL_CONTROL_BONUS);  // 18 from EvaluationParameters
        }

        // ✅ FIXED: Advanced strategic squares with moderate bonuses
        for (int square : Positional.ADVANCED_SQUARES) {
            centralScore += evaluateSquareControl(state, square, Positional.CENTRAL_CONTROL_BONUS / 2);  // 9 points
        }

        return centralScore;
    }

    private int evaluateSquareControl(GameState state, int square, int baseBonus) {
        int redHeight = state.redStackHeights[square];
        int blueHeight = state.blueStackHeights[square];

        if (redHeight > 0) {
            // ✅ FIXED: Moderate towers in center with height bonus from EvaluationParameters
            return baseBonus + (redHeight * Positional.TOWER_HEIGHT_BONUS);  // 8 per height from EvaluationParameters
        }

        if (blueHeight > 0) {
            return -(baseBonus + (blueHeight * Positional.TOWER_HEIGHT_BONUS));
        }

        return 0;
    }

    /**
     * Legacy strategic control evaluation (compatibility) - moderate bonuses
     */
    public int evaluateStrategicControl(GameState state) {
        int strategicScore = 0;

        // Use moderate strategic squares evaluation
        int[] strategicSquares = {
                GameState.getIndex(3, 3),  // D4 - Center
                GameState.getIndex(2, 3), GameState.getIndex(4, 3),  // D3, D5
                GameState.getIndex(3, 2), GameState.getIndex(3, 4),  // C4, E4
                GameState.getIndex(1, 3), GameState.getIndex(5, 3),  // D2, D6
                GameState.getIndex(0, 2), GameState.getIndex(0, 4),  // C1, E1
                GameState.getIndex(6, 2), GameState.getIndex(6, 4)   // C7, E7
        };

        for (int square : strategicSquares) {
            int control = evaluateSquareControlLegacy(state, square);
            strategicScore += control * (Positional.CENTRAL_CONTROL_BONUS / 2);  // 9 points
        }

        return strategicScore;
    }

    /**
     * Legacy square control evaluation (moderate assessment)
     */
    private int evaluateSquareControlLegacy(GameState state, int square) {
        int redControl = 0;
        int blueControl = 0;

        // Check which pieces can reach this square (moderate range)
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // Red pieces
            if (state.redStackHeights[i] > 0 || (state.redGuard & GameState.bit(i)) != 0) {
                if (canReachSquare(state, i, square, true)) {
                    redControl += getPieceControlValue(state, i, true);
                }
            }

            // Blue pieces
            if (state.blueStackHeights[i] > 0 || (state.blueGuard & GameState.bit(i)) != 0) {
                if (canReachSquare(state, i, square, false)) {
                    blueControl += getPieceControlValue(state, i, false);
                }
            }
        }

        return redControl - blueControl;
    }

    // === ✅ FIXED PIECE COORDINATION (MODERATE) ===

    /**
     * ✅ FIXED: Moderate coordination with tower chain detection
     */
    public int evaluatePieceCoordination(GameState state) {
        int coordinationScore = 0;

        // ✅ FIXED: Moderate towers coordination
        coordinationScore += evaluateTowerCoordination(state, true);  // Red
        coordinationScore -= evaluateTowerCoordination(state, false); // Blue

        // ✅ FIXED: Moderate guard protection through towers
        coordinationScore += evaluateGuardProtection(state, true);
        coordinationScore -= evaluateGuardProtection(state, false);

        return coordinationScore;
    }

    private int evaluateTowerCoordination(GameState state, boolean isRed) {
        int bonus = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height == 0) continue;

            // ✅ FIXED: Count supporting towers with moderate bonus
            int supporters = countSupportingTowers(state, i, isRed);
            if (supporters > 0) {
                bonus += supporters * Positional.COORDINATION_BONUS;  // 12 per supporter from EvaluationParameters

                // ✅ FIXED: Moderate bonus for high towers with support
                bonus += height * supporters * (Positional.COORDINATION_BONUS / 4);  // 3 per height per supporter
            }
        }

        return bonus;
    }

    private int evaluateGuardProtection(GameState state, boolean isRed) {
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return 0;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int protectors = countProtectingTowers(state, guardPos, isRed);

        return protectors * Positional.DEFENSIVE_FORMATION_BONUS;  // 12 per protector from EvaluationParameters
    }

    // === ✅ FIXED MOBILITY AND DEVELOPMENT (MODERATE) ===

    /**
     * ✅ FIXED: Moderate mobility and development evaluation
     */
    public int evaluateMobilityAndDevelopment(GameState state) {
        int mobilityScore = 0;

        // ✅ FIXED: Moderate piece mobility
        mobilityScore += evaluatePieceMobility(state);

        // ✅ FIXED: Moderate development evaluation
        mobilityScore += evaluateDevelopment(state);

        return mobilityScore;
    }

    /**
     * ✅ FIXED: Moderate piece mobility evaluation
     */
    private int evaluatePieceMobility(GameState state) {
        int mobilityScore = 0;

        // Count legal moves as mobility indicator (moderate bonus)
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        int moveCount = moves.size();

        // ✅ FIXED: Moderate moves bonus from EvaluationParameters
        mobilityScore = moveCount * Positional.MOBILITY_BONUS;  // 8 per move from EvaluationParameters

        // Adjust for turn
        if (!state.redToMove) {
            mobilityScore = -mobilityScore;
        }

        return mobilityScore;
    }

    /**
     * ✅ FIXED: Moderate development evaluation
     */
    public int evaluateDevelopment(GameState state) {
        int developmentScore = 0;

        // ✅ FIXED: Count undeveloped pieces with moderate penalty from EvaluationParameters
        for (int file = 0; file < 7; file++) {
            // Red development penalty (pieces still on rank 6)
            if (state.redStackHeights[GameState.getIndex(6, file)] > 0) {
                developmentScore -= Positional.DEVELOPMENT_BONUS;  // 25 from EvaluationParameters
            }

            // Blue development penalty (pieces still on rank 0)
            if (state.blueStackHeights[GameState.getIndex(0, file)] > 0) {
                developmentScore += Positional.DEVELOPMENT_BONUS;  // 25 from EvaluationParameters
            }
        }

        return developmentScore;
    }

    // === ✅ FIXED ENDGAME SPECIFIC EVALUATIONS (MODERATE) ===

    /**
     * ✅ FIXED: Moderate endgame positional evaluation
     */
    private int evaluateEndgamePositional(GameState state) {
        int endgameScore = 0;

        // ✅ FIXED: Moderate guard activity becomes important
        endgameScore += evaluateGuardActivity(state);

        // ✅ FIXED: Moderate king activity (guard centralization)
        endgameScore += evaluateKingActivity(state);

        return endgameScore;
    }

    /**
     * ✅ FIXED: Moderate guard activity evaluation
     */
    public int evaluateGuardActivity(GameState state) {
        int activityScore = 0;

        // Red guard activity (moderate)
        if (state.redGuard != 0) {
            activityScore += evaluateGuardActivityForSide(state, true);
        }

        // Blue guard activity (moderate)
        if (state.blueGuard != 0) {
            activityScore -= evaluateGuardActivityForSide(state, false);
        }

        return activityScore;
    }

    /**
     * ✅ FIXED: Moderate king activity (adapted for guards)
     */
    public int evaluateKingActivity(GameState state) {
        // In Guard & Towers, this evaluates guard activity in endgame (moderate)
        if (!isEndgame(state)) return 0;

        return evaluateGuardActivity(state);
    }

    private int evaluateGuardActivityForSide(GameState state, boolean isRed) {
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return 0;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int activity = 0;

        // ✅ FIXED: Moderate centralization bonus
        int file = GameState.file(guardPos);
        int rank = GameState.rank(guardPos);

        int centralityScore = 6 - Math.abs(file - 3) - Math.abs(rank - 3);
        activity += centralityScore * (Positional.ENDGAME_GUARD_ACTIVITY / 3);  // 10 per centrality

        // ✅ FIXED: Moderate mobility - count legal guard moves
        int[] directions = {-1, 1, -7, 7};
        for (int dir : directions) {
            int target = guardPos + dir;
            if (GameState.isOnBoard(target) &&
                    !isRankWrap(guardPos, target, dir) &&
                    !isOccupiedByFriendly(target, state, isRed)) {
                activity += Positional.MOBILITY_BONUS;  // 8 per move from EvaluationParameters
            }
        }

        return activity;
    }

    // === ✅ FIXED ADVANCED POSITIONAL CONCEPTS (MODERATE) ===

    /**
     * ✅ FIXED: Moderate outpost evaluation
     */
    public int evaluateOutposts(GameState state) {
        int outpostScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int rank = GameState.rank(i);

            // ✅ FIXED: Red outposts with moderate bonus
            if (state.redStackHeights[i] > 0 && rank <= 2) {
                outpostScore += (2 - rank) * Positional.TOWER_HEIGHT_BONUS * state.redStackHeights[i] * 2;  // Moderate
            }

            // ✅ FIXED: Blue outposts with moderate bonus
            if (state.blueStackHeights[i] > 0 && rank >= 4) {
                outpostScore -= (rank - 4) * Positional.TOWER_HEIGHT_BONUS * state.blueStackHeights[i] * 2;  // Moderate
            }
        }

        return outpostScore;
    }

    /**
     * ✅ FIXED: Moderate tower structure evaluation (tower formations)
     */
    public int evaluatePawnStructure(GameState state) {
        int structureScore = 0;

        // Evaluate tower chains and formations (moderate)
        for (int file = 0; file < 7; file++) {
            structureScore += evaluateFileStructure(state, file);
        }

        return structureScore;
    }

    /**
     * ✅ FIXED: Moderate file structure evaluation
     */
    private int evaluateFileStructure(GameState state, int file) {
        int fileScore = 0;

        // Count red and blue pieces on this file
        int redPieces = 0;
        int bluePieces = 0;

        for (int rank = 0; rank < 7; rank++) {
            int square = GameState.getIndex(rank, file);
            if (state.redStackHeights[square] > 0) redPieces++;
            if (state.blueStackHeights[square] > 0) bluePieces++;
        }

        // ✅ FIXED: Moderate bonus for controlling files
        if (redPieces > 0 && bluePieces == 0) {
            fileScore += Positional.CENTRAL_CONTROL_BONUS;  // 18 from EvaluationParameters
        } else if (bluePieces > 0 && redPieces == 0) {
            fileScore -= Positional.CENTRAL_CONTROL_BONUS;
        }

        // Central files are more valuable (moderate multiplier)
        if (file >= 2 && file <= 4) {
            fileScore = fileScore * 3 / 2;  // 1.5x multiplier instead of 2x
        }

        return fileScore;
    }

    // === HELPER METHODS ===

    /**
     * Check if guards are in advanced positions
     */
    public boolean areGuardsAdvanced(GameState state) {
        if (state.redGuard != 0) {
            int redRank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
            if (redRank <= 3) return true;
        }

        if (state.blueGuard != 0) {
            int blueRank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
            if (blueRank >= 3) return true;
        }

        return false;
    }

    private int countSupportingTowers(GameState state, int square, boolean isRed) {
        int supporters = 0;
        int rank = GameState.rank(square);
        int file = GameState.file(square);

        // Check same rank and file
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (i == square) continue;

            int iRank = GameState.rank(i);
            int iFile = GameState.file(i);

            // Same rank or file?
            if (iRank != rank && iFile != file) continue;

            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) {
                supporters++;
                if (supporters >= 3) break; // Cap for performance
            }
        }

        return supporters;
    }

    private int countProtectingTowers(GameState state, int guardPos, boolean isRed) {
        int protectors = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height == 0) continue;

            // Can tower reach the guard?
            int distance = calculateManhattanDistance(i, guardPos);
            if (distance <= height && canReachStraight(i, guardPos)) {
                protectors++;
                if (protectors >= 3) break; // Cap for performance
            }
        }

        return protectors;
    }

    /**
     * Count adjacent friendly pieces (moderate count)
     */
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

    /**
     * Calculate shortest path between two squares (moderate implementation)
     */
    private int[] calculateShortestPath(int from, int to) {
        // Simple implementation - just the direct line
        int rankDiff = GameState.rank(to) - GameState.rank(from);
        int fileDiff = GameState.file(to) - GameState.file(from);

        int steps = Math.max(Math.abs(rankDiff), Math.abs(fileDiff));
        int[] path = new int[steps];

        int rankStep = Integer.compare(rankDiff, 0);
        int fileStep = Integer.compare(fileDiff, 0);

        for (int i = 0; i < steps; i++) {
            int currentRank = GameState.rank(from) + (i + 1) * rankStep;
            int currentFile = GameState.file(from) + (i + 1) * fileStep;
            path[i] = GameState.getIndex(currentRank, currentFile);
        }

        return path;
    }

    /**
     * Check if a piece can reach a square (moderate range check)
     */
    private boolean canReachSquare(GameState state, int from, int to, boolean isRed) {
        // Get piece range
        int range = 1; // Default for guard

        if (isRed && state.redStackHeights[from] > 0) {
            range = state.redStackHeights[from];
        } else if (!isRed && state.blueStackHeights[from] > 0) {
            range = state.blueStackHeights[from];
        }

        // Check if in range and path is clear
        int distance = calculateManhattanDistance(from, to);
        if (distance > range) return false;

        // Simple path check
        return isPathClear(state, from, to);
    }

    /**
     * Get control value of a piece (moderate assessment)
     */
    private int getPieceControlValue(GameState state, int square, boolean isRed) {
        // Guard has control value 3, towers have control value equal to height
        if ((isRed && (state.redGuard & GameState.bit(square)) != 0) ||
                (!isRed && (state.blueGuard & GameState.bit(square)) != 0)) {
            return 3;
        }

        return isRed ? state.redStackHeights[square] : state.blueStackHeights[square];
    }

    // === UTILITY METHODS ===

    private boolean isEndgame(GameState state) {
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return totalMaterial <= 8; // Endgame when <= 8 towers total
    }

    private boolean canReachStraight(int from, int to) {
        return GameState.rank(from) == GameState.rank(to) ||
                GameState.file(from) == GameState.file(to);
    }

    private int calculateManhattanDistance(int from, int to) {
        return Math.abs(GameState.rank(from) - GameState.rank(to)) +
                Math.abs(GameState.file(from) - GameState.file(to));
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

    private boolean isPathClear(GameState state, int from, int to) {
        // Simple path checking
        int rankDiff = GameState.rank(to) - GameState.rank(from);
        int fileDiff = GameState.file(to) - GameState.file(from);

        if (rankDiff != 0 && fileDiff != 0) return false; // Not on same rank/file

        int step = rankDiff != 0 ? (rankDiff > 0 ? 7 : -7) : (fileDiff > 0 ? 1 : -1);
        int current = from + step;

        while (current != to) {
            if (isOccupied(current, state)) return false;
            current += step;
        }

        return true;
    }

    /**
     * Evaluate path to enemy castle (legacy compatibility) - moderate evaluation
     */
    private int evaluatePathToCastle(GameState state, int guardPos, boolean isRed) {
        int pathScore = 0;
        int targetSquare = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);

        // Simple path evaluation - count obstacles (moderate penalty)
        int[] path = calculateShortestPath(guardPos, targetSquare);
        int obstacles = 0;

        for (int square : path) {
            if (isOccupied(square, state)) {
                if (isEnemyPiece(square, state, isRed)) {
                    obstacles += 2; // Enemy pieces are bigger obstacles
                } else {
                    obstacles += 1; // Own pieces block path
                }
            }
        }

        // ✅ FIXED: Moderate path bonus
        pathScore = Math.max(0, (10 - obstacles) * Positional.GUARD_CASTLE_APPROACH);  // 20 base from EvaluationParameters

        return pathScore;
    }

    /**
     * Legacy guard advancement evaluation (for compatibility) - moderate bonuses
     */
    private int evaluateGuardAdvancementForSide(GameState state, boolean isRed) {
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return 0;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int rank = GameState.rank(guardPos);
        int file = GameState.file(guardPos);

        // Distance to enemy castle
        int targetRank = isRed ? 0 : 6; // D1 for red, D7 for blue
        int targetFile = 3; // D-file

        int rankDistance = Math.abs(rank - targetRank);
        int fileDistance = Math.abs(file - targetFile);
        int totalDistance = rankDistance + fileDistance;

        // ✅ FIXED: Moderate advancement score from EvaluationParameters
        int advancementScore = (12 - totalDistance) * Positional.GUARD_ADVANCEMENT_BONUS;  // 15 base

        // ✅ FIXED: Moderate bonus for being on the correct file
        if (file == 3) {
            advancementScore += Positional.GUARD_CASTLE_APPROACH * 3;  // 60 bonus
        }

        // ✅ FIXED: Moderate bonus for advanced positions
        if (isRed && rank <= 2) {
            advancementScore += (2 - rank) * Positional.GUARD_CASTLE_APPROACH * 2;  // 40 per rank
        } else if (!isRed && rank >= 4) {
            advancementScore += (rank - 4) * Positional.GUARD_CASTLE_APPROACH * 2;
        }

        // Path evaluation - is the path to castle clear? (moderate)
        advancementScore += evaluatePathToCastle(state, guardPos, isRed);

        return advancementScore;
    }

    /**
     * Legacy guard support evaluation (for compatibility) - moderate bonuses
     */
    private int evaluateGuardSupport(GameState state) {
        int supportScore = 0;

        // ✅ FIXED: Red guard support with moderate bonus
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int supporters = countAdjacentFriendly(state, guardPos, true);
            supportScore += supporters * Positional.DEFENSIVE_FORMATION_BONUS * 3;  // 36 per supporter
        }

        // ✅ FIXED: Blue guard support with moderate bonus
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int supporters = countAdjacentFriendly(state, guardPos, false);
            supportScore -= supporters * Positional.DEFENSIVE_FORMATION_BONUS * 3;
        }

        return supportScore;
    }
}
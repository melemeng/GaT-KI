package GaT.evaluation;

import GaT.model.GameState;
import GaT.search.MoveGenerator;
import GaT.model.Move;

import java.util.List;

/**
 * ENHANCED POSITIONAL EVALUATION COMPONENT - AGGRESSIVE PARAMETERS
 *
 * ENHANCEMENTS:
 * ✅ Aggressive parameter values for stronger play
 * ✅ Enhanced central control with tower height awareness
 * ✅ Improved guard advancement with castle approach bonus
 * ✅ Enhanced piece coordination with tower chain detection
 * ✅ Endgame-specific guard activity evaluation
 * ✅ Turm & Wächter specific strategic concepts
 */
public class PositionalEval {

    // === AGGRESSIVE POSITIONAL CONSTANTS ===
    private static final int GUARD_ADVANCEMENT_BONUS = 50;     // Was 40 -> +25%
    private static final int CENTRAL_CONTROL_BONUS = 50;       // Was 25 -> +100%
    private static final int MOBILITY_BONUS = 30;              // Was 15 -> +100%
    private static final int DEVELOPMENT_BONUS = 40;           // Was 30 -> +33%
    private static final int COORDINATION_BONUS = 35;          // Was 20 -> +75%

    // === NEW: TURM & WÄCHTER SPECIFIC BONUSES ===
    private static final int TOWER_HEIGHT_BONUS = 25;          // NEU! Pro Höhe im Zentrum
    private static final int GUARD_CASTLE_APPROACH = 60;       // NEU! Wächter nähert sich Schloss
    private static final int DEFENSIVE_FORMATION_BONUS = 30;   // NEU! Türme schützen Wächter
    private static final int ENDGAME_GUARD_ACTIVITY = 80;      // NEU! Wächter-Aktivität im Endspiel

    // === ERWEITERTE STRATEGISCHE QUADRATE ===
    private static final int[] CENTRAL_SQUARES = {
            GameState.getIndex(2, 3), GameState.getIndex(3, 3), GameState.getIndex(4, 3), // D3, D4, D5
            GameState.getIndex(3, 2), GameState.getIndex(3, 4)  // C4, E4
    };

    private static final int[] ADVANCED_SQUARES = {
            GameState.getIndex(1, 3), GameState.getIndex(5, 3),  // D2, D6 (vordere Linien)
            GameState.getIndex(2, 2), GameState.getIndex(2, 4),  // C3, E3
            GameState.getIndex(4, 2), GameState.getIndex(4, 4)   // C5, E5
    };

    private static final int[] CASTLE_APPROACH_SQUARES_RED = {
            GameState.getIndex(1, 3), GameState.getIndex(0, 3),  // D2, D1 (rotes Ziel)
            GameState.getIndex(1, 2), GameState.getIndex(1, 4),  // C2, E2
            GameState.getIndex(0, 2), GameState.getIndex(0, 4)   // C1, E1
    };

    private static final int[] CASTLE_APPROACH_SQUARES_BLUE = {
            GameState.getIndex(5, 3), GameState.getIndex(6, 3),  // D6, D7 (blaues Ziel)
            GameState.getIndex(5, 2), GameState.getIndex(5, 4),  // C6, E6
            GameState.getIndex(6, 2), GameState.getIndex(6, 4)   // C7, E7
    };

    // === Legacy strategic squares (for compatibility) ===
    private static final int[] STRATEGIC_SQUARES = {
            GameState.getIndex(3, 3),  // D4 - Center
            GameState.getIndex(2, 3), GameState.getIndex(4, 3),  // D3, D5
            GameState.getIndex(3, 2), GameState.getIndex(3, 4),  // C4, E4
            GameState.getIndex(1, 3), GameState.getIndex(5, 3),  // D2, D6
            GameState.getIndex(0, 2), GameState.getIndex(0, 4),  // C1, E1
            GameState.getIndex(6, 2), GameState.getIndex(6, 4)   // C7, E7
    };

    /**
     * COMPREHENSIVE POSITIONAL EVALUATION - ENHANCED
     */
    public int evaluatePositional(GameState state) {
        int positionalScore = 0;

        // Guard advancement (40% weight) - enhanced with castle approach
        positionalScore += evaluateGuardAdvancement(state) * 40 / 100;

        // Central control (25% weight) - enhanced with tower height
        positionalScore += evaluateCentralControl(state) * 25 / 100;

        // Piece coordination (20% weight) - enhanced with defensive formations
        positionalScore += evaluatePieceCoordination(state) * 20 / 100;

        // Mobility and development (15% weight)
        positionalScore += evaluateMobilityAndDevelopment(state) * 15 / 100;

        return positionalScore;
    }

    /**
     * ADVANCED POSITIONAL EVALUATION - For deep analysis
     */
    public int evaluatePositionalAdvanced(GameState state) {
        // Enhanced evaluation with additional factors
        int score = evaluatePositional(state);

        // Add endgame-specific evaluations
        if (isEndgame(state)) {
            score += evaluateEndgamePositional(state);
        }

        return score;
    }

    // === ENHANCED GUARD ADVANCEMENT ===

    /**
     * Enhanced guard advancement with endgame awareness
     */
    public int evaluateGuardAdvancement(GameState state) {
        int advancementScore = 0;

        // Roten Wächter bewerten
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            advancementScore += evaluateGuardAdvancementForColor(guardPos, true, state);
        }

        // Blauen Wächter bewerten
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

        // Basis-Vorstoß-Bonus
        int targetRank = isRed ? 0 : 6; // Gegnerisches Schloss
        int rankDistance = Math.abs(rank - targetRank);
        bonus += (7 - rankDistance) * GUARD_ADVANCEMENT_BONUS;

        // File-Distanz zum D-File (Schloss steht auf D1/D7)
        int fileDistance = Math.abs(file - 3);
        bonus += (4 - fileDistance) * GUARD_CASTLE_APPROACH;

        // Endspiel-Bonus: Wächter wird wichtiger
        if (isEndgame(state)) {
            bonus += ENDGAME_GUARD_ACTIVITY;

            // Extra Bonus für direkte Schlossnähe im Endspiel
            if (rankDistance <= 2 && fileDistance <= 1) {
                bonus += ENDGAME_GUARD_ACTIVITY / 2;
            }
        }

        return bonus;
    }

    /**
     * Fast guard advancement evaluation (compatibility)
     */
    public int evaluateGuardAdvancementFast(GameState state) {
        int advancementScore = 0;

        // Red guard
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int rank = GameState.rank(guardPos);
            advancementScore += (6 - rank) * GUARD_ADVANCEMENT_BONUS; // Enhanced bonus
        }

        // Blue guard
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int rank = GameState.rank(guardPos);
            advancementScore -= rank * GUARD_ADVANCEMENT_BONUS; // Enhanced bonus
        }

        return advancementScore;
    }

    // === ENHANCED CENTRAL CONTROL ===

    /**
     * Enhanced central control with tower height awareness
     */
    public int evaluateCentralControl(GameState state) {
        int centralScore = 0;

        // Zentrale Quadrate
        for (int square : CENTRAL_SQUARES) {
            centralScore += evaluateSquareControl(state, square, CENTRAL_CONTROL_BONUS);
        }

        // Erweiterte strategische Quadrate
        for (int square : ADVANCED_SQUARES) {
            centralScore += evaluateSquareControl(state, square, CENTRAL_CONTROL_BONUS / 2);
        }

        return centralScore;
    }

    private int evaluateSquareControl(GameState state, int square, int baseBonus) {
        int redHeight = state.redStackHeights[square];
        int blueHeight = state.blueStackHeights[square];

        if (redHeight > 0) {
            // Hohe Türme im Zentrum sind exponentiell wertvoller
            return baseBonus + (redHeight * TOWER_HEIGHT_BONUS);
        }

        if (blueHeight > 0) {
            return -(baseBonus + (blueHeight * TOWER_HEIGHT_BONUS));
        }

        return 0;
    }

    /**
     * Legacy strategic control evaluation (compatibility)
     */
    public int evaluateStrategicControl(GameState state) {
        int strategicScore = 0;

        for (int square : STRATEGIC_SQUARES) {
            int control = evaluateSquareControlLegacy(state, square);
            strategicScore += control * (CENTRAL_CONTROL_BONUS / 2);
        }

        return strategicScore;
    }

    /**
     * Legacy square control evaluation
     */
    private int evaluateSquareControlLegacy(GameState state, int square) {
        int redControl = 0;
        int blueControl = 0;

        // Check which pieces can reach this square
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

    // === ENHANCED PIECE COORDINATION ===

    /**
     * Enhanced coordination with tower chain detection
     */
    public int evaluatePieceCoordination(GameState state) {
        int coordinationScore = 0;

        // Türme, die sich gegenseitig unterstützen können
        coordinationScore += evaluateTowerCoordination(state, true);  // Rot
        coordinationScore -= evaluateTowerCoordination(state, false); // Blau

        // Wächter-Schutz durch Türme
        coordinationScore += evaluateGuardProtection(state, true);
        coordinationScore -= evaluateGuardProtection(state, false);

        return coordinationScore;
    }

    private int evaluateTowerCoordination(GameState state, boolean isRed) {
        int bonus = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height == 0) continue;

            // Zähle unterstützende Türme in derselben Reihe/Spalte
            int supporters = countSupportingTowers(state, i, isRed);
            if (supporters > 0) {
                bonus += supporters * COORDINATION_BONUS;

                // Bonus für hohe Türme mit Unterstützung
                bonus += height * supporters * 5;
            }
        }

        return bonus;
    }

    private int evaluateGuardProtection(GameState state, boolean isRed) {
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return 0;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int protectors = countProtectingTowers(state, guardPos, isRed);

        return protectors * DEFENSIVE_FORMATION_BONUS;
    }

    // === ENHANCED MOBILITY AND DEVELOPMENT ===

    /**
     * Enhanced mobility and development evaluation
     */
    public int evaluateMobilityAndDevelopment(GameState state) {
        int mobilityScore = 0;

        // Enhanced piece mobility
        mobilityScore += evaluatePieceMobility(state);

        // Enhanced development evaluation
        mobilityScore += evaluateDevelopment(state);

        return mobilityScore;
    }

    /**
     * Enhanced piece mobility evaluation
     */
    private int evaluatePieceMobility(GameState state) {
        int mobilityScore = 0;

        // Count legal moves as mobility indicator
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        int moveCount = moves.size();

        // More moves = better mobility (enhanced bonus)
        mobilityScore = moveCount * MOBILITY_BONUS;

        // Adjust for turn
        if (!state.redToMove) {
            mobilityScore = -mobilityScore;
        }

        return mobilityScore;
    }

    /**
     * Enhanced development evaluation
     */
    public int evaluateDevelopment(GameState state) {
        int developmentScore = 0;

        // Count undeveloped pieces (still on back rank)
        for (int file = 0; file < 7; file++) {
            // Red development penalty (pieces still on rank 6)
            if (state.redStackHeights[GameState.getIndex(6, file)] > 0) {
                developmentScore -= DEVELOPMENT_BONUS;
            }

            // Blue development penalty (pieces still on rank 0)
            if (state.blueStackHeights[GameState.getIndex(0, file)] > 0) {
                developmentScore += DEVELOPMENT_BONUS;
            }
        }

        return developmentScore;
    }

    // === ENDGAME SPECIFIC EVALUATIONS ===

    /**
     * Enhanced endgame positional evaluation
     */
    private int evaluateEndgamePositional(GameState state) {
        int endgameScore = 0;

        // Guard activity becomes crucial
        endgameScore += evaluateGuardActivity(state);

        // King activity (guard centralization)
        endgameScore += evaluateKingActivity(state);

        return endgameScore;
    }

    /**
     * Enhanced guard activity evaluation
     */
    public int evaluateGuardActivity(GameState state) {
        int activityScore = 0;

        // Red guard activity
        if (state.redGuard != 0) {
            activityScore += evaluateGuardActivityForSide(state, true);
        }

        // Blue guard activity
        if (state.blueGuard != 0) {
            activityScore -= evaluateGuardActivityForSide(state, false);
        }

        return activityScore;
    }

    /**
     * Enhanced king activity (adapted for guards)
     */
    public int evaluateKingActivity(GameState state) {
        // In Guard & Towers, this evaluates guard activity in endgame
        if (!isEndgame(state)) return 0;

        return evaluateGuardActivity(state);
    }

    private int evaluateGuardActivityForSide(GameState state, boolean isRed) {
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return 0;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int activity = 0;

        // Centralization bonus
        int file = GameState.file(guardPos);
        int rank = GameState.rank(guardPos);

        int centralityScore = 6 - Math.abs(file - 3) - Math.abs(rank - 3);
        activity += centralityScore * 15; // Enhanced from 10

        // Mobility - count legal guard moves
        int[] directions = {-1, 1, -7, 7};
        for (int dir : directions) {
            int target = guardPos + dir;
            if (GameState.isOnBoard(target) &&
                    !isRankWrap(guardPos, target, dir) &&
                    !isOccupiedByFriendly(target, state, isRed)) {
                activity += 20; // Enhanced from 15
            }
        }

        return activity;
    }

    // === ADVANCED POSITIONAL CONCEPTS ===

    /**
     * Enhanced outpost evaluation
     */
    public int evaluateOutposts(GameState state) {
        int outpostScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int rank = GameState.rank(i);

            // Red outposts (pieces deep in blue territory)
            if (state.redStackHeights[i] > 0 && rank <= 2) {
                outpostScore += (2 - rank) * 60 * state.redStackHeights[i]; // Enhanced from 50
            }

            // Blue outposts (pieces deep in red territory)
            if (state.blueStackHeights[i] > 0 && rank >= 4) {
                outpostScore -= (rank - 4) * 60 * state.blueStackHeights[i]; // Enhanced from 50
            }
        }

        return outpostScore;
    }

    /**
     * Enhanced pawn structure equivalent (tower formations)
     */
    public int evaluatePawnStructure(GameState state) {
        int structureScore = 0;

        // Evaluate tower chains and formations
        for (int file = 0; file < 7; file++) {
            structureScore += evaluateFileStructure(state, file);
        }

        return structureScore;
    }

    /**
     * Enhanced file structure evaluation
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

        // Enhanced bonus for controlling files
        if (redPieces > 0 && bluePieces == 0) {
            fileScore += 35; // Enhanced from 25
        } else if (bluePieces > 0 && redPieces == 0) {
            fileScore -= 35; // Enhanced from 25
        }

        // Central files are more valuable
        if (file >= 2 && file <= 4) {
            fileScore *= 2;
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

        // Prüfe Reihe und Spalte
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (i == square) continue;

            int iRank = GameState.rank(i);
            int iFile = GameState.file(i);

            // Gleiche Reihe oder Spalte?
            if (iRank != rank && iFile != file) continue;

            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) {
                supporters++;
            }
        }

        return supporters;
    }

    private int countProtectingTowers(GameState state, int guardPos, boolean isRed) {
        int protectors = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height == 0) continue;

            // Kann Turm den Wächter erreichen?
            int distance = calculateManhattanDistance(i, guardPos);
            if (distance <= height && canReachStraight(i, guardPos)) {
                protectors++;
            }
        }

        return protectors;
    }

    /**
     * Count adjacent friendly pieces
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
     * Calculate shortest path between two squares
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
     * Check if a piece can reach a square
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
     * Get control value of a piece
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
        return totalMaterial <= 8; // Endspiel wenn <= 8 Türme insgesamt
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
        // Simple path checking - can be improved
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
     * Evaluate path to enemy castle (legacy compatibility)
     */
    private int evaluatePathToCastle(GameState state, int guardPos, boolean isRed) {
        int pathScore = 0;
        int targetSquare = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);

        // Simple path evaluation - count obstacles
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

        // Fewer obstacles = better path
        pathScore = Math.max(0, (10 - obstacles) * 25);

        return pathScore;
    }

    /**
     * Legacy guard advancement evaluation (for compatibility)
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

        // Base advancement score (closer = better)
        int advancementScore = (12 - totalDistance) * GUARD_ADVANCEMENT_BONUS;

        // Bonus for being on the correct file (D-file)
        if (file == 3) {
            advancementScore += 150;
        }

        // Bonus for advanced positions
        if (isRed && rank <= 2) {
            advancementScore += (2 - rank) * 200; // Deep penetration bonus
        } else if (!isRed && rank >= 4) {
            advancementScore += (rank - 4) * 200;
        }

        // Path evaluation - is the path to castle clear?
        advancementScore += evaluatePathToCastle(state, guardPos, isRed);

        return advancementScore;
    }

    /**
     * Legacy guard support evaluation (for compatibility)
     */
    private int evaluateGuardSupport(GameState state) {
        int supportScore = 0;

        // Red guard support
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int supporters = countAdjacentFriendly(state, guardPos, true);
            supportScore += supporters * 100; // Guard support is very valuable
        }

        // Blue guard support
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int supporters = countAdjacentFriendly(state, guardPos, false);
            supportScore -= supporters * 100;
        }

        return supportScore;
    }
}
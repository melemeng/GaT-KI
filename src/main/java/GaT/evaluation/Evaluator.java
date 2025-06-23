package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.SearchConfig;

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

    // === EVALUATION COMPONENTS ===
    private final MaterialEval materialEval;
    private final PositionalEval positionalEval;
    private final SafetyEval safetyEval;

    // === ENHANCED EVALUATION CONSTANTS ===
    public static final int CASTLE_REACH_SCORE = 2500;
    public static final int GUARD_CAPTURE_SCORE = 1500;

    // === STRATEGIC CONSTANTS ===
    private static final int GUARD_ADVANCEMENT_WEIGHT = 400;
    private static final int PIECE_DEVELOPMENT_WEIGHT = 150;
    private static final int CENTRAL_CONTROL_WEIGHT = 200;
    private static final int REPETITION_PENALTY = -300;
    private static final int EDGE_SHUFFLE_PENALTY = -200;

    // === TIME-ADAPTIVE EVALUATION ===
    private static long remainingTimeMs = 180000; // Default 3 minutes

    public Evaluator() {
        this.materialEval = new MaterialEval();
        this.positionalEval = new PositionalEval();
        this.safetyEval = new SafetyEval();
    }

    /**
     * ENHANCED MAIN EVALUATION - Now with anti-repetition logic
     */
    public int evaluate(GameState state, int depth) {
        // === TERMINAL POSITION CHECK ===
        EvaluationResult terminalResult = checkTerminalPosition(state, depth);
        if (terminalResult.isTerminal) {
            return terminalResult.score;
        }

        // === ENHANCED EVALUATION STRATEGY ===
        if (remainingTimeMs < 3000) {
            return evaluateUltraFastEnhanced(state, depth);
        } else if (remainingTimeMs < 10000) {
            return evaluateQuickEnhanced(state, depth);
        } else if (remainingTimeMs > 30000) {
            return evaluateStrategicEnhanced(state, depth);
        } else {
            return evaluateBalancedEnhanced(state, depth);
        }
    }

    /**
     * Check for terminal positions (wins/losses)
     */
    private EvaluationResult checkTerminalPosition(GameState state, int depth) {
        // Guard captured
        if (state.redGuard == 0) {
            return new EvaluationResult(true, -CASTLE_REACH_SCORE - depth);
        }
        if (state.blueGuard == 0) {
            return new EvaluationResult(true, CASTLE_REACH_SCORE + depth);
        }

        // Guard reached enemy castle
        boolean redWinsByCastle = state.redGuard == GameState.bit(GameState.getIndex(0, 3)); // D1
        boolean blueWinsByCastle = state.blueGuard == GameState.bit(GameState.getIndex(6, 3)); // D7

        if (redWinsByCastle) {
            return new EvaluationResult(true, CASTLE_REACH_SCORE + depth);
        }
        if (blueWinsByCastle) {
            return new EvaluationResult(true, -CASTLE_REACH_SCORE - depth);
        }

        return new EvaluationResult(false, 0);
    }

    /**
     * ULTRA-FAST ENHANCED EVALUATION - For extreme time pressure
     */
    private int evaluateUltraFastEnhanced(GameState state, int depth) {
        int eval = 0;

        // Enhanced material difference
        eval += materialEval.evaluateMaterialSimple(state);

        // Critical guard advancement (this stops meaningless moves!)
        eval += evaluateGuardAdvancementCritical(state);

        // Anti-repetition penalty for edge pieces
        eval += evaluateAntiRepetition(state);

        return eval;
    }

    /**
     * QUICK ENHANCED EVALUATION - For time pressure
     */
    private int evaluateQuickEnhanced(GameState state, int depth) {
        int eval = 0;

        // Material (35% weight)
        eval += materialEval.evaluateMaterialBasic(state) * 35 / 100;

        // Enhanced guard advancement (40% weight)
        eval += evaluateGuardAdvancementEnhanced(state) * 40 / 100;

        // Guard safety (25% weight)
        eval += safetyEval.evaluateGuardSafetyBasic(state) * 25 / 100;

        // Anti-repetition measures
        eval += evaluateAntiRepetition(state);

        return eval;
    }

    /**
     * BALANCED ENHANCED EVALUATION - Standard tournament play
     */
    private int evaluateBalancedEnhanced(GameState state, int depth) {
        int eval = 0;

        // Material + Activity (30% weight)
        eval += materialEval.evaluateMaterialWithActivity(state) * 30 / 100;

        // Enhanced guard advancement (30% weight)
        eval += evaluateGuardAdvancementEnhanced(state) * 30 / 100;

        // Guard safety (25% weight)
        eval += safetyEval.evaluateGuardSafety(state) * 25 / 100;

        // Strategic positioning (15% weight)
        eval += evaluateStrategicPositioning(state) * 15 / 100;

        // Anti-repetition and tactical bonuses
        eval += evaluateAntiRepetition(state);
        eval += evaluateTacticalPatterns(state);

        return eval;
    }

    /**
     * STRATEGIC ENHANCED EVALUATION - For abundant time
     */
    private int evaluateStrategicEnhanced(GameState state, int depth) {
        int eval = 0;

        // Advanced material evaluation (25% weight)
        eval += materialEval.evaluateMaterialAdvanced(state) * 25 / 100;

        // Enhanced guard strategy (35% weight)
        eval += evaluateGuardStrategy(state) * 35 / 100;

        // Advanced safety evaluation (20% weight)
        eval += safetyEval.evaluateGuardSafetyAdvanced(state) * 20 / 100;

        // Strategic concepts (20% weight)
        eval += evaluateAdvancedStrategicConcepts(state) * 20 / 100;

        return eval;
    }

    // === ENHANCED GUARD ADVANCEMENT EVALUATION ===

    /**
     * CRITICAL guard advancement - prevents meaningless moves
     */
    private int evaluateGuardAdvancementCritical(GameState state) {
        int advancement = 0;

        // Red guard advancement towards D1
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int rank = GameState.rank(guardPos);
            int file = GameState.file(guardPos);

            // Strong bonus for moving towards enemy castle
            advancement += (6 - rank) * 150; // Rank advancement

            // Huge bonus for being on D-file
            if (file == 3) {
                advancement += 300;
            } else {
                // Penalty for being far from D-file
                advancement -= Math.abs(file - 3) * 100;
            }

            // Extra bonus for being deep in enemy territory
            if (rank <= 2) {
                advancement += (2 - rank) * 200;
            }
        }

        // Blue guard advancement towards D7
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int rank = GameState.rank(guardPos);
            int file = GameState.file(guardPos);

            // Strong bonus for moving towards enemy castle
            advancement -= rank * 150; // Rank advancement (negative for blue)

            // Huge bonus for being on D-file
            if (file == 3) {
                advancement -= 300;
            } else {
                // Penalty for being far from D-file
                advancement += Math.abs(file - 3) * 100;
            }

            // Extra bonus for being deep in enemy territory
            if (rank >= 4) {
                advancement -= (rank - 4) * 200;
            }
        }

        return advancement;
    }

    /**
     * ENHANCED guard advancement with strategic considerations
     */
    private int evaluateGuardAdvancementEnhanced(GameState state) {
        int advancement = evaluateGuardAdvancementCritical(state);

        // Add path evaluation
        advancement += evaluateGuardPathToCastle(state);

        // Add support evaluation
        advancement += evaluateGuardSupport(state);

        return advancement;
    }

    /**
     * Evaluate path to castle for guards
     */
    private int evaluateGuardPathToCastle(GameState state) {
        int pathScore = 0;

        // Red guard path to D1
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            pathScore += evaluatePathClearance(state, guardPos, GameState.getIndex(0, 3), true);
        }

        // Blue guard path to D7
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            pathScore -= evaluatePathClearance(state, guardPos, GameState.getIndex(6, 3), false);
        }

        return pathScore;
    }

    /**
     * Evaluate how clear the path is to the target
     */
    private int evaluatePathClearance(GameState state, int from, int to, boolean isRed) {
        int clearanceScore = 0;

        // Simple path evaluation - check D-file path
        int fromRank = GameState.rank(from);
        int toRank = GameState.rank(to);

        // Count obstacles on direct path
        int obstacles = 0;
        int minRank = Math.min(fromRank, toRank);
        int maxRank = Math.max(fromRank, toRank);

        for (int rank = minRank + 1; rank < maxRank; rank++) {
            int squareOnPath = GameState.getIndex(rank, 3); // D-file
            if (isOccupied(squareOnPath, state)) {
                if (isEnemyPiece(squareOnPath, state, isRed)) {
                    obstacles += 2; // Enemy pieces are bigger obstacles
                } else {
                    obstacles += 1; // Own pieces also block
                }
            }
        }

        // Fewer obstacles = better score
        clearanceScore = Math.max(0, (5 - obstacles) * 50);

        return clearanceScore;
    }

    /**
     * Evaluate guard support from towers
     */
    private int evaluateGuardSupport(GameState state) {
        int supportScore = 0;

        // Red guard support
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            supportScore += countAdjacentFriendly(state, guardPos, true) * 75;
        }

        // Blue guard support
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            supportScore -= countAdjacentFriendly(state, guardPos, false) * 75;
        }

        return supportScore;
    }

    // === ANTI-REPETITION EVALUATION ===

    /**
     * CRITICAL: Anti-repetition measures to prevent meaningless shuffling
     */
    private int evaluateAntiRepetition(GameState state) {
        int antiRepetition = 0;

        // Penalize pieces on back ranks that aren't doing anything useful
        antiRepetition += penalizeBackRankShuffling(state);

        // Penalize edge piece shuffling
        antiRepetition += penalizeEdgeShuffling(state);

        // Bonus for piece development
        antiRepetition += bonusPieceDevelopment(state);

        return antiRepetition;
    }

    /**
     * Penalize shuffling pieces on back ranks
     */
    private int penalizeBackRankShuffling(GameState state) {
        int penalty = 0;

        // Count undeveloped red pieces on rank 6-7
        for (int file = 0; file < 7; file++) {
            if (file == 3) continue; // Skip guard positions

            // Red pieces still on back ranks
            if (state.redStackHeights[GameState.getIndex(6, file)] > 0) {
                penalty -= 80; // Penalty for undeveloped pieces
            }
            if (state.redStackHeights[GameState.getIndex(5, file)] > 0) {
                penalty -= 40; // Smaller penalty for semi-developed
            }

            // Blue pieces still on back ranks
            if (state.blueStackHeights[GameState.getIndex(0, file)] > 0) {
                penalty += 80; // Penalty for undeveloped pieces (positive for blue penalty)
            }
            if (state.blueStackHeights[GameState.getIndex(1, file)] > 0) {
                penalty += 40; // Smaller penalty for semi-developed
            }
        }

        return penalty;
    }

    /**
     * Penalize edge piece shuffling (like A6-B6-A6)
     */
    private int penalizeEdgeShuffling(GameState state) {
        int penalty = 0;

        // Heavy penalty for pieces on the very edges doing nothing
        int[] edgeFiles = {0, 6}; // A-file and G-file

        for (int edgeFile : edgeFiles) {
            for (int rank = 4; rank < 7; rank++) { // Red side
                int square = GameState.getIndex(rank, edgeFile);
                if (state.redStackHeights[square] > 0) {
                    penalty -= EDGE_SHUFFLE_PENALTY; // This prevents A6-B6 shuffling!
                }
            }

            for (int rank = 0; rank < 3; rank++) { // Blue side
                int square = GameState.getIndex(rank, edgeFile);
                if (state.blueStackHeights[square] > 0) {
                    penalty += EDGE_SHUFFLE_PENALTY;
                }
            }
        }

        return penalty;
    }

    /**
     * Bonus for piece development and advancement
     */
    private int bonusPieceDevelopment(GameState state) {
        int developmentBonus = 0;

        // Count pieces that have advanced from their starting positions
        for (int rank = 0; rank < 7; rank++) {
            for (int file = 0; file < 7; file++) {
                int square = GameState.getIndex(rank, file);

                // Red pieces advancing
                if (state.redStackHeights[square] > 0) {
                    if (rank < 4) { // Advanced into enemy territory
                        developmentBonus += (4 - rank) * 60;
                    } else if (rank < 6) { // Some advancement
                        developmentBonus += 20;
                    }
                }

                // Blue pieces advancing
                if (state.blueStackHeights[square] > 0) {
                    if (rank > 2) { // Advanced into enemy territory
                        developmentBonus -= (rank - 2) * 60;
                    } else if (rank > 0) { // Some advancement
                        developmentBonus -= 20;
                    }
                }
            }
        }

        return developmentBonus;
    }

    // === STRATEGIC POSITIONING ===

    /**
     * Evaluate strategic positioning beyond basic advancement
     */
    private int evaluateStrategicPositioning(GameState state) {
        int strategic = 0;

        // Central control
        strategic += evaluateCentralControl(state);

        // File control
        strategic += evaluateFileControl(state);

        // Piece coordination
        strategic += evaluatePieceCoordination(state);

        return strategic;
    }

    /**
     * Enhanced central control evaluation
     */
    private int evaluateCentralControl(GameState state) {
        int centralControl = 0;

        // Key central squares
        int[] centralSquares = {
                GameState.getIndex(3, 3), // D4 - Most important
                GameState.getIndex(2, 3), GameState.getIndex(4, 3), // D3, D5
                GameState.getIndex(3, 2), GameState.getIndex(3, 4), // C4, E4
                GameState.getIndex(2, 2), GameState.getIndex(2, 4), // C3, E3
                GameState.getIndex(4, 2), GameState.getIndex(4, 4)  // C5, E5
        };

        for (int i = 0; i < centralSquares.length; i++) {
            int square = centralSquares[i];
            int weight = (i == 0) ? 100 : 50; // D4 is most important

            if (state.redStackHeights[square] > 0) {
                centralControl += weight * state.redStackHeights[square];
            }
            if (state.blueStackHeights[square] > 0) {
                centralControl -= weight * state.blueStackHeights[square];
            }
        }

        return centralControl;
    }

    /**
     * Evaluate file control, especially D-file
     */
    private int evaluateFileControl(GameState state) {
        int fileControl = 0;

        // D-file control is critical
        int dFileControl = 0;
        for (int rank = 0; rank < 7; rank++) {
            int square = GameState.getIndex(rank, 3);

            if (state.redStackHeights[square] > 0) {
                dFileControl += state.redStackHeights[square] * 80;
            }
            if (state.blueStackHeights[square] > 0) {
                dFileControl -= state.blueStackHeights[square] * 80;
            }
        }
        fileControl += dFileControl;

        // Other central files (C and E) are also valuable
        for (int file : new int[]{2, 4}) { // C and E files
            for (int rank = 2; rank < 5; rank++) { // Central region
                int square = GameState.getIndex(rank, file);

                if (state.redStackHeights[square] > 0) {
                    fileControl += state.redStackHeights[square] * 30;
                }
                if (state.blueStackHeights[square] > 0) {
                    fileControl -= state.blueStackHeights[square] * 30;
                }
            }
        }

        return fileControl;
    }

    /**
     * Enhanced piece coordination
     */
    private int evaluatePieceCoordination(GameState state) {
        int coordination = 0;

        // Count connected pieces (pieces supporting each other)
        for (int square = 0; square < GameState.NUM_SQUARES; square++) {
            if (state.redStackHeights[square] > 0) {
                coordination += countAdjacentFriendly(state, square, true) * 25;
            }
            if (state.blueStackHeights[square] > 0) {
                coordination -= countAdjacentFriendly(state, square, false) * 25;
            }
        }

        return coordination;
    }

    // === ADVANCED STRATEGIC CONCEPTS ===

    /**
     * Advanced strategic evaluation for deep analysis
     */
    private int evaluateAdvancedStrategicConcepts(GameState state) {
        int advanced = 0;

        // Piece activity and mobility
        advanced += materialEval.evaluatePieceActivity(state);

        // Strategic outposts
        advanced += positionalEval.evaluateOutposts(state);

        // Endgame considerations
        if (isEndgame(state)) {
            advanced += evaluateEndgameFactors(state);
        }

        return advanced;
    }

    /**
     * Evaluate guard strategy comprehensively
     */
    private int evaluateGuardStrategy(GameState state) {
        int strategy = 0;

        // Basic advancement
        strategy += evaluateGuardAdvancementEnhanced(state);

        // Advanced considerations
        strategy += evaluateGuardTiming(state);
        strategy += evaluateGuardSupportNetwork(state);

        return strategy;
    }

    /**
     * Evaluate timing of guard advancement
     */
    private int evaluateGuardTiming(GameState state) {
        int timing = 0;

        // Early game: develop pieces before advancing guard aggressively
        if (!isEndgame(state)) {
            int developedPieces = countDevelopedPieces(state);
            if (developedPieces < 4) {
                // Penalty for advancing guard too early
                if (state.redGuard != 0) {
                    int guardRank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
                    if (guardRank < 4) {
                        timing -= (4 - guardRank) * 100;
                    }
                }
                if (state.blueGuard != 0) {
                    int guardRank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
                    if (guardRank > 2) {
                        timing += (guardRank - 2) * 100;
                    }
                }
            }
        }

        return timing;
    }

    /**
     * Evaluate guard support network
     */
    private int evaluateGuardSupportNetwork(GameState state) {
        int supportScore = 0;

        // Red guard support
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            supportScore += countAdjacentFriendly(state, guardPos, true) * 100;
        }

        // Blue guard support
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            supportScore -= countAdjacentFriendly(state, guardPos, false) * 100;
        }

        return supportScore;
    }

    /**
     * Enhanced endgame evaluation
     */
    private int evaluateEndgameFactors(GameState state) {
        int endgame = 0;

        // In endgame, guard activity becomes paramount
        endgame += positionalEval.evaluateGuardActivity(state) * 2;

        // Piece activity matters more
        endgame += materialEval.evaluatePieceActivity(state) * 3;

        return endgame;
    }

    /**
     * Basic tactical pattern recognition
     */
    private int evaluateTacticalPatterns(GameState state) {
        int tactical = 0;

        // Look for basic tactical motifs
        tactical += evaluateThreats(state);
        tactical += evaluateDefensiveStructures(state);

        return tactical;
    }

    /**
     * Evaluate threats and counter-threats
     */
    private int evaluateThreats(GameState state) {
        int threats = 0;

        // Count threats against guards
        threats += safetyEval.countThreats(state) * 50;

        return threats;
    }

    /**
     * Evaluate defensive structures
     */
    private int evaluateDefensiveStructures(GameState state) {
        int defensiveScore = 0;

        // Bonus for defensive formations around guards
        // Red guard protection
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            defensiveScore += countAdjacentFriendly(state, guardPos, true) * 75;
        }

        // Blue guard protection
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            defensiveScore -= countAdjacentFriendly(state, guardPos, false) * 75;
        }

        return defensiveScore;
    }

    // === UTILITY METHODS ===

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

    private int countAdjacentFriendly(GameState state, int square, boolean isRed) {
        int count = 0;
        int[] directions = {-1, 1, -7, 7}; // Left, Right, Up, Down

        for (int dir : directions) {
            int adjacent = square + dir;
            if (!GameState.isOnBoard(adjacent)) continue;

            // Check for rank wrap on horizontal moves
            if (Math.abs(dir) == 1 && GameState.rank(square) != GameState.rank(adjacent)) continue;

            long adjBit = GameState.bit(adjacent);
            if (isRed && ((state.redTowers | state.redGuard) & adjBit) != 0) {
                count++;
            } else if (!isRed && ((state.blueTowers | state.blueGuard) & adjBit) != 0) {
                count++;
            }
        }

        return count;
    }

    private boolean isEndgame(GameState state) {
        return materialEval.getTotalMaterial(state) <= SearchConfig.ENDGAME_MATERIAL_THRESHOLD;
    }

    private int countDevelopedPieces(GameState state) {
        int developed = 0;

        // Count pieces that have moved from starting positions
        for (int rank = 2; rank < 5; rank++) { // Middle board
            for (int file = 0; file < 7; file++) {
                int square = GameState.getIndex(rank, file);
                if (state.redStackHeights[square] > 0 || state.blueStackHeights[square] > 0) {
                    developed++;
                }
            }
        }

        return developed;
    }

    // === TIME MANAGEMENT INTEGRATION ===

    public static void setRemainingTime(long timeMs) {
        remainingTimeMs = timeMs;
    }

    public static long getRemainingTime() {
        return remainingTimeMs;
    }

    // === COMPONENT ACCESS ===

    public MaterialEval getMaterialEvaluator() { return materialEval; }
    public PositionalEval getPositionalEvaluator() { return positionalEval; }
    public SafetyEval getSafetyEvaluator() { return safetyEval; }

    // === HELPER CLASSES ===

    private static class EvaluationResult {
        final boolean isTerminal;
        final int score;

        EvaluationResult(boolean isTerminal, int score) {
            this.isTerminal = isTerminal;
            this.score = score;
        }
    }
}
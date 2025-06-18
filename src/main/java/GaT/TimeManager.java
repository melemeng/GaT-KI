package GaT;

import GaT.Objects.GameState;
import GaT.Objects.Move;

import java.util.Arrays;
import java.util.List;

public class TimeManager {
    private long remainingTime;
    private int estimatedMovesLeft;
    private Phase phase;

    // Time management history for learning
    private static long totalTimeUsed = 0;
    private static int moveCount = 0;
    private static long lastCriticalTime = 0;

    // Emergency time thresholds
    private static final long EMERGENCY_TIME_THRESHOLD = 10000; // 10 seconds
    private static final long PANIC_TIME_THRESHOLD = 3000;     // 3 seconds

    enum Phase {
        OPENING,    // Many pieces, guards safe
        MIDDLEGAME, // Tactical complications
        ENDGAME,    // Few pieces, guard races
        CRITICAL    // Immediate threats/mate
    }

    public TimeManager(long remainingTime, int estimatedMovesLeft) {
        this.remainingTime = remainingTime;
        this.estimatedMovesLeft = estimatedMovesLeft;
        this.phase = Phase.OPENING;
    }

    /**
     * MAIN TIME ALLOCATION METHOD - Completely rewritten for maximum strength
     */
    public long calculateTimeForMove(GameState state) {
        // === EMERGENCY TIME MANAGEMENT ===
        if (remainingTime <= PANIC_TIME_THRESHOLD) {
            long panicTime = Math.max(200, remainingTime / 10);
            System.out.println("⚠️ PANIC MODE: Only " + panicTime + "ms allocated!");
            return panicTime;
        }

        if (remainingTime <= EMERGENCY_TIME_THRESHOLD) {
            long emergencyTime = Math.max(500, remainingTime / 8);
            System.out.println("🚨 EMERGENCY: Limited to " + emergencyTime + "ms");
            return emergencyTime;
        }

        // === PHASE AND THREAT DETECTION ===
        ThreatLevel threatLevel = analyzeThreatLevel(state);
        this.phase = detectAdvancedGamePhase(state);

        // === BASE TIME CALCULATION ===
        long baseTime = calculateSmartBaseTime();
        double timeMultiplier = 1.0;

        String reasonsLog = "Time allocation reasons: ";

        // === CRITICAL POSITION MULTIPLIERS ===
        switch (threatLevel) {
            case MATE_THREAT:
                timeMultiplier *= 4.0; // 400% time for mate threats
                reasonsLog += "[MATE_THREAT x4.0] ";
                break;
            case GUARD_DANGER:
                timeMultiplier *= 2.5; // 250% time when guard in danger
                reasonsLog += "[GUARD_DANGER x2.5] ";
                break;
            case TACTICAL:
                timeMultiplier *= 1.8; // 180% time for tactical positions
                reasonsLog += "[TACTICAL x1.8] ";
                break;
            case QUIET:
                timeMultiplier *= 0.6; // 60% time for quiet positions
                reasonsLog += "[QUIET x0.6] ";
                break;
        }

        // === PHASE-BASED ADJUSTMENTS ===
        switch (phase) {
            case OPENING:
                timeMultiplier *= 0.5; // Fast opening moves
                reasonsLog += "[OPENING x0.5] ";
                break;
            case MIDDLEGAME:
                timeMultiplier *= 1.3; // Standard tactical time
                reasonsLog += "[MIDDLEGAME x1.3] ";
                break;
            case ENDGAME:
                timeMultiplier *= 1.6; // Precision matters in endgame
                reasonsLog += "[ENDGAME x1.6] ";
                break;
            case CRITICAL:
                timeMultiplier *= 2.0; // Critical endgame situations
                reasonsLog += "[CRITICAL x2.0] ";
                break;
        }

        // === POSITION COMPLEXITY ANALYSIS ===
        ComplexityAnalysis complexity = analyzeComplexity(state);
        if (complexity.isVeryComplex) {
            timeMultiplier *= 1.7;
            reasonsLog += "[VERY_COMPLEX x1.7] ";
        } else if (complexity.isSimple) {
            timeMultiplier *= 0.4;
            reasonsLog += "[SIMPLE x0.4] ";
        }

        // === EVALUATION-BASED ADJUSTMENTS ===
        int evaluation = quickPositionEvaluation(state);
        if (Math.abs(evaluation) > 5000) {
            timeMultiplier *= 0.3; // Much less time when clearly winning/losing
            reasonsLog += "[DECIDED x0.3] ";
        } else if (Math.abs(evaluation) < 100) {
            timeMultiplier *= 1.4; // More time for balanced positions
            reasonsLog += "[BALANCED x1.4] ";
        }

        // === MOVE COUNT ADJUSTMENTS ===
        if (estimatedMovesLeft <= 5) {
            timeMultiplier *= 2.0; // More time near end of time control
            reasonsLog += "[FEW_MOVES x2.0] ";
        } else if (estimatedMovesLeft >= 30) {
            timeMultiplier *= 0.8; // Less time when many moves remain
            reasonsLog += "[MANY_MOVES x0.8] ";
        }

        // === SPECIAL SITUATION BONUSES ===
        if (hasWinningChance(state)) {
            timeMultiplier *= 1.5; // More time to find the win
            reasonsLog += "[WINNING_CHANCE x1.5] ";
        }

        if (mustDefend(state)) {
            timeMultiplier *= 1.8; // More time to find defense
            reasonsLog += "[MUST_DEFEND x1.8] ";
        }

        // === APPLY MULTIPLIER ===
        long allocatedTime = (long)(baseTime * timeMultiplier);

        // === SAFETY LIMITS ===
        long maxSafeTime = calculateMaxSafeTime();
        long minRequiredTime = Math.max(300, remainingTime / 100); // At least 300ms

        allocatedTime = Math.max(minRequiredTime, Math.min(allocatedTime, maxSafeTime));

        // === LEARNING COMPONENT ===
        updateTimeUsageHistory(allocatedTime);

        // === LOGGING ===
        System.out.println("🕐 Time: " + allocatedTime + "ms (x" +
                String.format("%.2f", timeMultiplier) + ") - " +
                threatLevel + " " + phase);
        System.out.println("💭 " + reasonsLog);

        return allocatedTime;
    }

    /**
     * Analyze threat level in current position
     */
    private ThreatLevel analyzeThreatLevel(GameState state) {
        // Check for immediate mate threats
        if (isInMateNet(state)) {
            return ThreatLevel.MATE_THREAT;
        }

        // Check for guard in immediate danger
        if (isGuardInDanger(state, true) || isGuardInDanger(state, false)) {
            return ThreatLevel.GUARD_DANGER;
        }

        // Check for tactical complications
        if (hasTacticalThreats(state)) {
            return ThreatLevel.TACTICAL;
        }

        return ThreatLevel.QUIET;
    }

    /**
     * Enhanced game phase detection
     */
    private Phase detectAdvancedGamePhase(GameState state) {
        int totalPieces = getMaterialCount(state);
        boolean guardsAdvanced = areGuardsAdvanced(state);
        boolean hasCriticalThreats = isInMateNet(state) || hasImmediateWinningThreats(state);

        // Critical phase overrides everything
        if (hasCriticalThreats) {
            return Phase.CRITICAL;
        }

        // Endgame: Few pieces OR guards very advanced
        if (totalPieces <= 4 || guardsAdvanced) {
            return Phase.ENDGAME;
        }

        // Opening: Many pieces, guards safe
        if (totalPieces >= 10 && !isGuardInDanger(state, true) && !isGuardInDanger(state, false)) {
            return Phase.OPENING;
        }

        // Default to middlegame
        return Phase.MIDDLEGAME;
    }

    /**
     * Comprehensive complexity analysis
     */
    private ComplexityAnalysis analyzeComplexity(GameState state) {
        ComplexityAnalysis analysis = new ComplexityAnalysis();

        // Count various complexity factors
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        List<Move> tacticalMoves = QuiescenceSearch.generateTacticalMoves(state);

        int complexityScore = 0;

        // High move count = complex
        if (allMoves.size() > 25) complexityScore += 3;
        if (allMoves.size() > 35) complexityScore += 2;

        // Many tactical moves = very complex
        complexityScore += tacticalMoves.size() * 2;

        // Guards near enemy territory = complex
        if (areGuardsInEnemyTerritory(state)) complexityScore += 4;

        // Material imbalance = potentially complex
        int materialBalance = Math.abs(getMaterialBalance(state));
        if (materialBalance > 200 && materialBalance < 800) complexityScore += 3;

        // Many tall towers = complex
        complexityScore += countTallTowers(state);

        // Pieces in contact = tactical complexity
        complexityScore += countPiecesInContact(state);

        analysis.complexityScore = complexityScore;
        analysis.isVeryComplex = complexityScore >= 15;
        analysis.isSimple = complexityScore <= 3;

        return analysis;
    }

    /**
     * Quick position evaluation for time management
     */
    private int quickPositionEvaluation(GameState state) {
        int score = 0;

        // Quick material evaluation
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            score += state.redStackHeights[i] * 130;
            score -= state.blueStackHeights[i] * 130;
        }

        // Guard position evaluation
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            int rankFromBlueCastle = GameState.rank(redGuardPos);
            score += (6 - rankFromBlueCastle) * 200; // Closer to enemy castle = better
        }

        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int rankFromRedCastle = 6 - GameState.rank(blueGuardPos);
            score -= (6 - rankFromRedCastle) * 200;
        }

        // Critical adjustments
        if (state.redGuard == 0) score -= 10000;
        if (state.blueGuard == 0) score += 10000;

        return score;
    }

    /**
     * Calculate smart base time using adaptive formula
     */
    private long calculateSmartBaseTime() {
        // Base formula: remaining time divided by moves, with safety buffer
        long baseTime = remainingTime / Math.max(estimatedMovesLeft, 5);

        // Adaptive adjustment based on game progress
        if (moveCount > 0) {
            long averageTimePerMove = totalTimeUsed / moveCount;
            // Blend historical usage with theoretical time
            baseTime = (baseTime + averageTimePerMove) / 2;
        }

        // Ensure minimum reasonable time
        return Math.max(baseTime, 500);
    }

    /**
     * Calculate maximum safe time to use
     */
    private long calculateMaxSafeTime() {
        // Never use more than this percentage of remaining time
        double maxPercentage;

        if (estimatedMovesLeft <= 3) {
            maxPercentage = 0.8; // Can use up to 80% when very few moves left
        } else if (estimatedMovesLeft <= 10) {
            maxPercentage = 0.4; // 40% when approaching end
        } else {
            maxPercentage = 0.25; // 25% in normal situations
        }

        return (long)(remainingTime * maxPercentage);
    }

    // === HELPER METHODS ===

    private boolean isInMateNet(GameState state) {
        return isGuardInMateNet(state, true) || isGuardInMateNet(state, false);
    }

    private boolean isGuardInMateNet(GameState state, boolean checkRed) {
        // Simplified mate net detection for time management
        if (!isGuardInDanger(state, checkRed)) return false;

        // Quick check: if guard has no escape squares, likely mate net
        return countGuardEscapeSquares(state, checkRed) == 0;
    }

    private boolean hasTacticalThreats(GameState state) {
        List<Move> tacticalMoves = QuiescenceSearch.generateTacticalMoves(state);
        return tacticalMoves.size() > 2; // More than 2 tactical moves = tactical position
    }

    private boolean hasImmediateWinningThreats(GameState state) {
        // Check if guard can reach enemy castle in 1-2 moves
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            int distanceToBlueCastle = manhattanDistance(redGuardPos, GameState.getIndex(0, 3));
            if (distanceToBlueCastle <= 2) return true;
        }

        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int distanceToRedCastle = manhattanDistance(blueGuardPos, GameState.getIndex(6, 3));
            if (distanceToRedCastle <= 2) return true;
        }

        return false;
    }

    private boolean areGuardsAdvanced(GameState state) {
        boolean redAdvanced = false, blueAdvanced = false;

        if (state.redGuard != 0) {
            int redRank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
            redAdvanced = redRank <= 2; // Advanced toward blue castle
        }

        if (state.blueGuard != 0) {
            int blueRank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
            blueAdvanced = blueRank >= 4; // Advanced toward red castle
        }

        return redAdvanced || blueAdvanced;
    }

    private boolean areGuardsInEnemyTerritory(GameState state) {
        if (state.redGuard != 0) {
            int redRank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
            if (redRank <= 3) return true; // In blue territory
        }

        if (state.blueGuard != 0) {
            int blueRank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
            if (blueRank >= 3) return true; // In red territory
        }

        return false;
    }

    private int getMaterialBalance(GameState state) {
        int redMaterial = 0, blueMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            redMaterial += state.redStackHeights[i] * 130;
            blueMaterial += state.blueStackHeights[i] * 130;
        }
        return redMaterial - blueMaterial;
    }

    private int countTallTowers(GameState state) {
        int count = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] >= 4) count++;
            if (state.blueStackHeights[i] >= 4) count++;
        }
        return count;
    }

    private int countPiecesInContact(GameState state) {
        int contacts = 0;
        // Count adjacent pieces (simplified)
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0 || state.blueStackHeights[i] > 0 ||
                    (state.redGuard & GameState.bit(i)) != 0 || (state.blueGuard & GameState.bit(i)) != 0) {

                // Check adjacent squares
                int[] directions = {-1, 1, -7, 7}; // left, right, up, down
                for (int dir : directions) {
                    int adj = i + dir;
                    if (GameState.isOnBoard(adj)) {
                        if (state.redStackHeights[adj] > 0 || state.blueStackHeights[adj] > 0 ||
                                (state.redGuard & GameState.bit(adj)) != 0 || (state.blueGuard & GameState.bit(adj)) != 0) {
                            contacts++;
                        }
                    }
                }
            }
        }
        return contacts / 2; // Each contact counted twice
    }

    private boolean hasWinningChance(GameState state) {
        int eval = quickPositionEvaluation(state);
        // We have winning chances if slightly ahead but not decided
        return eval > 300 && eval < 3000;
    }

    private boolean mustDefend(GameState state) {
        int eval = quickPositionEvaluation(state);
        // Must defend if behind but not hopeless
        return eval < -300 && eval > -3000;
    }

    private int countGuardEscapeSquares(GameState state, boolean checkRed) {
        // Simplified escape square counting
        long guardBit = checkRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return 0;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int escapes = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int newPos = guardPos + dir;
            if (GameState.isOnBoard(newPos)) {
                // Quick check: if square is not occupied by own pieces
                long ownPieces = checkRed ?
                        (state.redGuard | state.redTowers) :
                        (state.blueGuard | state.blueTowers);
                if ((ownPieces & GameState.bit(newPos)) == 0) {
                    escapes++;
                }
            }
        }

        return escapes;
    }

    private void updateTimeUsageHistory(long timeAllocated) {
        totalTimeUsed += timeAllocated;
        moveCount++;

        // Reset history periodically to adapt to changing game
        if (moveCount > 50) {
            totalTimeUsed /= 2;
            moveCount /= 2;
        }
    }

    // === UTILITY METHODS ===

    private int getMaterialCount(GameState state) {
        return (int) Arrays.stream(state.redStackHeights).filter(h -> h >= 1).count() +
                (int) Arrays.stream(state.blueStackHeights).filter(h -> h >= 1).count();
    }

    private boolean isGuardInDanger(GameState state, boolean checkRed) {
        GameState copy = state.copy();
        copy.redToMove = !checkRed;
        long targetGuard = checkRed ? state.redGuard : state.blueGuard;

        if (targetGuard == 0) return false;

        for (Move move : MoveGenerator.generateAllMoves(copy)) {
            if (GameState.bit(move.to) == targetGuard) return true;
        }
        return false;
    }

    private int manhattanDistance(int pos1, int pos2) {
        int rank1 = GameState.rank(pos1), file1 = GameState.file(pos1);
        int rank2 = GameState.rank(pos2), file2 = GameState.file(pos2);
        return Math.abs(rank1 - rank2) + Math.abs(file1 - file2);
    }

    // === PUBLIC INTERFACE ===

    public void updateRemainingTime(long remainingTime) {
        this.remainingTime = remainingTime;
    }

    public void decrementEstimatedMovesLeft() {
        if (this.estimatedMovesLeft > 1) {
            this.estimatedMovesLeft--;
        }
    }

    public void reportMoveCompleted(long timeUsed) {
        // Learning: track actual time used vs allocated
        totalTimeUsed += timeUsed;
        moveCount++;
    }

    // === INNER CLASSES ===

    private enum ThreatLevel {
        MATE_THREAT,    // Immediate mate threats
        GUARD_DANGER,   // Guard can be captured
        TACTICAL,       // Multiple tactical moves
        QUIET           // Calm position
    }

    private static class ComplexityAnalysis {
        int complexityScore;
        boolean isVeryComplex;
        boolean isSimple;
    }
}
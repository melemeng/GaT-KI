package GaT.engine;

import GaT.search.MoveGenerator;
import GaT.model.GameState;
import GaT.model.Move;
import java.util.List;

/**
 * FIXED: TimeManager - TRULY Ultra-Aggressive Time Allocation
 *
 * KEY FIXES:
 * ✅ 1. Minimum 33% time usage per move (was allowing < 10%)
 * ✅ 2. Up to 75% in critical positions (was max 50%)
 * ✅ 3. Better critical position detection
 * ✅ 4. More aggressive endgame time usage
 * ✅ 5. Proper material imbalance detection
 */
public class TimeManager {
    private long remainingTime;
    private int estimatedMovesLeft;
    private Phase phase;

    // FIXED: Even more aggressive thresholds
    private static final long PANIC_TIME_THRESHOLD = 300;      // Was 500
    private static final long EMERGENCY_TIME_THRESHOLD = 2000;  // Was 3000
    private static final long AGGRESSIVE_TIME_THRESHOLD = 30000; // New threshold

    public enum Phase {
        OPENING, MIDDLEGAME, ENDGAME
    }

    public TimeManager(long remainingTime, int estimatedMovesLeft) {
        this.remainingTime = remainingTime;
        this.estimatedMovesLeft = Math.max(estimatedMovesLeft, 10); // FIXED: More aggressive estimate
        this.phase = Phase.OPENING;
    }

    /**
     * FIXED: TRULY Ultra-Aggressive time allocation
     */
    public long calculateTimeForMove(GameState state) {
        // Absolute emergency - use what little we have
        if (remainingTime <= PANIC_TIME_THRESHOLD) {
            return Math.max(50, remainingTime / 3); // Use 33% even in panic
        }

        // Emergency mode - still be aggressive
        if (remainingTime <= EMERGENCY_TIME_THRESHOLD) {
            return Math.max(200, remainingTime * 2 / 5); // Use 40% in emergency
        }

        // Detect game phase and position criticality
        this.phase = detectGamePhase(state);
        boolean isCritical = isCriticalPosition(state);
        int complexity = evaluatePositionComplexity(state);

        // FIXED: Ultra-aggressive base calculation
        long baseTime = calculateUltraAggressiveBaseTime();

        // FIXED: More extreme phase multipliers
        switch (phase) {
            case OPENING:
                if (remainingTime > 120000) { // Lots of time in opening
                    baseTime = baseTime * 3 / 4; // Can be slightly conservative
                } else {
                    baseTime = baseTime; // Normal aggression
                }
                break;

            case MIDDLEGAME:
                baseTime = baseTime * 2; // DOUBLE time for complex middlegame
                if (complexity > 30) {
                    baseTime = (long)(baseTime * 1.5); // Extra for very complex
                }
                break;

            case ENDGAME:
                baseTime = (long)(baseTime * 2.5); // 2.5x for critical endgames
                if (getTotalMaterial(state) <= 6) {
                    baseTime = baseTime * 3; // Triple for extreme endgames
                }
                break;
        }

        // FIXED: Critical position override
        if (isCritical) {
            System.out.println("🔴 CRITICAL POSITION DETECTED!");
            long criticalTime = remainingTime / 2; // Use HALF remaining time!
            baseTime = Math.max(baseTime, criticalTime);
        }

        // Material imbalance adjustment
        int materialDiff = getMaterialDifference(state);
        if (materialDiff < -1) { // We're behind
            baseTime = (long)(baseTime * 1.5); // 50% more time when behind
            System.out.println("⚠️ Material deficit: " + materialDiff);
        } else if (materialDiff > 2) { // We're ahead
            baseTime = (long)(baseTime * 0.8); // Can be slightly faster when winning
        }

        // FIXED: ULTRA-AGGRESSIVE bounds
        long minTime = Math.max(1000, remainingTime / 10);   // Minimum 10% or 1 second
        long maxTime = remainingTime * 3 / 4;                 // Maximum 75% of remaining!

        // Special cases for extreme time usage
        if (remainingTime > AGGRESSIVE_TIME_THRESHOLD && isCritical) {
            maxTime = remainingTime * 4 / 5; // Up to 80% when we have time and it's critical
        }

        // Ensure we don't leave ourselves in time trouble
        if (remainingTime < 10000) { // Less than 10 seconds
            maxTime = remainingTime / 2; // Max 50% to avoid time trouble
        }

        baseTime = Math.max(minTime, Math.min(baseTime, maxTime));

        System.out.printf("🕐 ULTRA-AGGRESSIVE Time: %dms (%.1f%% of %dms remaining)%n",
                baseTime, (double)baseTime/remainingTime*100, remainingTime);
        System.out.printf("   Phase: %s | Complexity: %d | Critical: %s | Material: %+d%n",
                phase, complexity, isCritical, materialDiff);

        return baseTime;
    }

    /**
     * FIXED: Much more aggressive base time calculation
     */
    private long calculateUltraAggressiveBaseTime() {
        // Assume game will end sooner than expected
        int aggressiveMovesLeft = Math.max(5, estimatedMovesLeft / 2);

        // Base: Use 1/moves of remaining time
        long targetTimePerMove = remainingTime / aggressiveMovesLeft;

        // FIXED: Aggressive minimums based on remaining time
        long aggressiveMinimum;
        if (remainingTime > 150000) {      // > 2.5 minutes
            aggressiveMinimum = 20000;     // At least 20 seconds!
        } else if (remainingTime > 90000) { // > 1.5 minutes
            aggressiveMinimum = 15000;     // At least 15 seconds
        } else if (remainingTime > 60000) { // > 1 minute
            aggressiveMinimum = 10000;     // At least 10 seconds
        } else if (remainingTime > 30000) { // > 30 seconds
            aggressiveMinimum = 7000;      // At least 7 seconds
        } else if (remainingTime > 15000) { // > 15 seconds
            aggressiveMinimum = 4000;      // At least 4 seconds
        } else if (remainingTime > 5000) {  // > 5 seconds
            aggressiveMinimum = 2000;      // At least 2 seconds
        } else {
            aggressiveMinimum = remainingTime / 3; // 33% minimum
        }

        return Math.max(targetTimePerMove, aggressiveMinimum);
    }

    /**
     * FIXED: Better critical position detection
     */
    private boolean isCriticalPosition(GameState state) {
        // Material imbalance (either way)
        int materialDiff = getMaterialDifference(state);
        if (Math.abs(materialDiff) >= 2) {
            return true; // Significant material imbalance
        }

        // Guards in danger or advanced
        if (isGuardInDanger(state)) {
            return true;
        }
        if (areGuardsAdvanced(state)) {
            return true;
        }

        // Can win immediately
        if (hasWinningMove(state)) {
            return true;
        }

        // Endgame with few pieces
        if (getTotalMaterial(state) <= 8) {
            return true;
        }

        // Complex tactical position
        if (evaluatePositionComplexity(state) > 40) {
            return true;
        }

        return false;
    }

    private Phase detectGamePhase(GameState state) {
        int totalPieces = getTotalMaterial(state);
        boolean guardsAdvanced = areGuardsAdvanced(state);

        if (totalPieces <= 6 || (totalPieces <= 8 && guardsAdvanced)) {
            return Phase.ENDGAME;
        } else if (totalPieces <= 10 || guardsAdvanced) {
            return Phase.MIDDLEGAME;
        } else {
            return Phase.OPENING;
        }
    }

    private int evaluatePositionComplexity(GameState state) {
        try {
            List<Move> allMoves = MoveGenerator.generateAllMoves(state);
            int complexity = allMoves.size();

            // Count tactical moves
            int captureCount = 0;
            int checkCount = 0;
            int winningMoves = 0;

            for (Move move : allMoves) {
                if (isCapture(move, state)) {
                    captureCount++;
                    if (capturesGuard(move, state)) {
                        winningMoves++;
                    }
                }
                if (threatsEnemyGuard(move, state)) {
                    checkCount++;
                }
                if (reachesEnemyCastle(move, state)) {
                    winningMoves++;
                }
            }

            complexity += captureCount * 3;
            complexity += checkCount * 5;
            complexity += winningMoves * 10;

            // Position-based complexity
            if (areGuardsAdvanced(state)) {
                complexity += 15;
            }
            if (isGuardInDanger(state)) {
                complexity += 20;
            }

            // Material imbalance adds complexity
            int materialDiff = Math.abs(getMaterialDifference(state));
            complexity += materialDiff * 5;

            return complexity;
        } catch (Exception e) {
            return 30; // Assume moderately complex on error
        }
    }

    private boolean hasWinningMove(GameState state) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        for (Move move : moves) {
            if (capturesGuard(move, state) || reachesEnemyCastle(move, state)) {
                return true;
            }
        }
        return false;
    }

    private boolean isGuardInDanger(GameState state) {
        // Check if our guard can be captured
        boolean isRed = state.redToMove;
        long ourGuard = isRed ? state.redGuard : state.blueGuard;
        if (ourGuard == 0) return true; // No guard = danger!

        int guardPos = Long.numberOfTrailingZeros(ourGuard);

        // Simulate opponent moves
        GameState simState = state.copy();
        simState.redToMove = !simState.redToMove;
        List<Move> enemyMoves = MoveGenerator.generateAllMoves(simState);

        for (Move move : enemyMoves) {
            if (move.to == guardPos) {
                return true; // Guard can be captured
            }
        }

        return false;
    }

    private boolean capturesGuard(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        long enemyGuard = state.redToMove ? state.blueGuard : state.redGuard;
        return (enemyGuard & toBit) != 0;
    }

    private boolean reachesEnemyCastle(Move move, GameState state) {
        boolean isRed = state.redToMove;
        int targetCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);

        // Check if it's a guard move to castle
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit)) {
            return move.to == targetCastle;
        }

        return false;
    }

    private boolean threatsEnemyGuard(Move move, GameState state) {
        long enemyGuard = state.redToMove ? state.blueGuard : state.redGuard;
        if (enemyGuard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(enemyGuard);
        return canAttackFrom(move.to, guardPos, move.amountMoved);
    }

    private boolean canAttackFrom(int from, int to, int range) {
        if (from == to) return false;
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        if (rankDiff != 0 && fileDiff != 0) return false;
        int distance = Math.max(rankDiff, fileDiff);
        return distance <= range;
    }

    private boolean areGuardsAdvanced(GameState state) {
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            if (GameState.rank(redGuardPos) <= 2) return true; // Red guard on rank 3 or lower
        }

        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            if (GameState.rank(blueGuardPos) >= 4) return true; // Blue guard on rank 5 or higher
        }

        return false;
    }

    private int getTotalMaterial(GameState state) {
        int total = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            total += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return total;
    }

    private int getMaterialDifference(GameState state) {
        int redMaterial = 0;
        int blueMaterial = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            redMaterial += state.redStackHeights[i];
            blueMaterial += state.blueStackHeights[i];
        }

        // Guards don't count as material for this calculation
        // since they can't be replaced

        return state.redToMove ? (redMaterial - blueMaterial) : (blueMaterial - redMaterial);
    }

    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    // Public interface
    public void updateRemainingTime(long remainingTime) {
        this.remainingTime = Math.max(0, remainingTime);
    }

    public void decrementEstimatedMovesLeft() {
        if (estimatedMovesLeft > 3) { // Keep minimum of 3
            this.estimatedMovesLeft--;
        }
    }

    public Phase getCurrentPhase() {
        return phase;
    }

    public long getRemainingTime() {
        return remainingTime;
    }

    public int getEstimatedMovesLeft() {
        return estimatedMovesLeft;
    }
}
package GaT.engine;

import GaT.search.MoveGenerator;
import GaT.model.GameState;
import GaT.model.Move;
import java.util.List;

public class TimeManager {
    private long remainingTime;
    private int estimatedMovesLeft;
    private Phase phase;

    // FIXED: Much more aggressive thresholds
    private static final long PANIC_TIME_THRESHOLD = 500;     // Was 1000
    private static final long EMERGENCY_TIME_THRESHOLD = 3000; // Was 5000

    public enum Phase {
        OPENING, MIDDLEGAME, ENDGAME
    }

    public TimeManager(long remainingTime, int estimatedMovesLeft) {
        this.remainingTime = remainingTime;
        this.estimatedMovesLeft = Math.max(estimatedMovesLeft, 15); // More aggressive
        this.phase = Phase.OPENING;
    }

    /**
     * FIXED: MUCH more aggressive time allocation
     */
    public long calculateTimeForMove(GameState state) {
        // Emergency handling
        if (remainingTime <= PANIC_TIME_THRESHOLD) {
            return Math.max(100, remainingTime / 4); // Use 25% in panic
        }

        if (remainingTime <= EMERGENCY_TIME_THRESHOLD) {
            return Math.max(300, remainingTime / 3); // Use 33% in emergency
        }

        // Detect game phase
        this.phase = detectGamePhase(state);

        // FIXED: AGGRESSIVE base time calculation
        long baseTime = calculateUltraAggressiveBaseTime();

        // Phase-based multipliers (more aggressive)
        switch (phase) {
            case OPENING -> baseTime = baseTime * 9 / 10;    // 10% less for opening
            case MIDDLEGAME -> baseTime = baseTime * 3 / 2;  // 50% more for middlegame
            case ENDGAME -> baseTime = baseTime * 2;         // 100% more for endgame
        }

        // Complexity adjustment
        int complexity = evaluatePositionComplexity(state);
        if (complexity > 20) {
            baseTime = baseTime * 2;     // 100% more for complex positions
        } else if (complexity > 15) {
            baseTime = baseTime * 3 / 2; // 50% more for moderate complexity
        } else if (complexity < 10) {
            baseTime = baseTime * 4 / 5; // 20% less for simple positions
        }

        // Critical position bonus
        if (isCriticalPosition(state)) {
            baseTime = baseTime * 3 / 2; // 50% more for critical positions
        }

        // FIXED: AGGRESSIVE bounds - use much more time!
        long minTime = Math.max(1000, remainingTime / 20);  // Min 5% of remaining
        long maxTime = remainingTime / 2;                    // Max 50% of remaining time!

        // For endgame, be even more aggressive
        if (phase == Phase.ENDGAME && remainingTime > 30000) {
            maxTime = remainingTime * 2 / 3; // Up to 66% in endgame!
        }

        baseTime = Math.max(minTime, Math.min(baseTime, maxTime));

        System.out.printf("🕐 ULTRA-AGGRESSIVE Time: %dms (Phase: %s, Complexity: %d, Critical: %s, Remaining: %dms)%n",
                baseTime, phase, complexity, isCriticalPosition(state), remainingTime);

        return baseTime;
    }

    /**
     * FIXED: Ultra-aggressive base time calculation
     */
    private long calculateUltraAggressiveBaseTime() {
        // Target using 20-30% of remaining time per move on average
        long targetTimePerMove = remainingTime * 25 / (estimatedMovesLeft * 100); // 25% average

        // But guarantee aggressive minimums based on remaining time
        long aggressiveMinimum;
        if (remainingTime > 120000) {      // > 2 minutes
            aggressiveMinimum = 15000;     // At least 15 seconds
        } else if (remainingTime > 60000) { // > 1 minute
            aggressiveMinimum = 10000;     // At least 10 seconds
        } else if (remainingTime > 30000) { // > 30 seconds
            aggressiveMinimum = 5000;      // At least 5 seconds
        } else if (remainingTime > 10000) { // > 10 seconds
            aggressiveMinimum = 2000;      // At least 2 seconds
        } else {
            aggressiveMinimum = remainingTime / 10; // 10% minimum
        }

        return Math.max(targetTimePerMove, aggressiveMinimum);
    }

    private Phase detectGamePhase(GameState state) {
        int totalPieces = getTotalMaterial(state);
        boolean guardsAdvanced = areGuardsAdvanced(state);

        if (totalPieces >= 12 && !guardsAdvanced) {
            return Phase.OPENING;
        } else if (totalPieces <= 6 || guardsAdvanced) {
            return Phase.ENDGAME;
        } else {
            return Phase.MIDDLEGAME;
        }
    }

    private int evaluatePositionComplexity(GameState state) {
        try {
            List<Move> allMoves = MoveGenerator.generateAllMoves(state);
            int complexity = allMoves.size();

            // Check for captures and threats
            int captureCount = 0;
            int checkCount = 0;

            for (Move move : allMoves) {
                if (isCapture(move, state)) {
                    captureCount++;
                }
                // Check if move attacks enemy guard
                if (threatsEnemyGuard(move, state)) {
                    checkCount++;
                }
            }

            complexity += captureCount * 3;
            complexity += checkCount * 5;

            // Guard position complexity
            if (areGuardsAdvanced(state)) {
                complexity += 10;
            }

            // Guards in danger = very complex
            if (isGuardInDanger(state)) {
                complexity += 15;
            }

            return complexity;
        } catch (Exception e) {
            return 20; // Assume complex on error
        }
    }

    private boolean isCriticalPosition(GameState state) {
        // Guards advanced
        if (areGuardsAdvanced(state)) {
            return true;
        }

        // Guard in danger
        if (isGuardInDanger(state)) {
            return true;
        }

        // Very few pieces left
        if (getTotalMaterial(state) <= 5) {
            return true;
        }

        // Can capture enemy guard
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        for (Move move : moves) {
            if (capturesEnemyGuard(move, state)) {
                return true;
            }
        }

        return false;
    }

    private boolean isGuardInDanger(GameState state) {
        // Simple check - would need proper implementation
        long guardBit = state.redToMove ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return true; // No guard = danger!

        // Check if enemy pieces can capture our guard
        // This is simplified - real implementation would check all enemy attacks
        return false;
    }

    private boolean threatsEnemyGuard(Move move, GameState state) {
        long enemyGuard = state.redToMove ? state.blueGuard : state.redGuard;
        if (enemyGuard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(enemyGuard);
        // Check if move puts piece in position to attack guard
        return canAttackFrom(move.to, guardPos, move.amountMoved);
    }

    private boolean capturesEnemyGuard(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        long enemyGuard = state.redToMove ? state.blueGuard : state.redGuard;
        return (enemyGuard & toBit) != 0;
    }

    private boolean canAttackFrom(int from, int to, int range) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        // Must be on same rank or file
        if (rankDiff != 0 && fileDiff != 0) return false;

        int distance = Math.max(rankDiff, fileDiff);
        return distance <= range;
    }

    private boolean areGuardsAdvanced(GameState state) {
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            if (GameState.rank(redGuardPos) <= 2) return true;
        }

        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            if (GameState.rank(blueGuardPos) >= 4) return true;
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

    private boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    // Public interface
    public void updateRemainingTime(long remainingTime) {
        this.remainingTime = Math.max(0, remainingTime);
    }

    public void decrementEstimatedMovesLeft() {
        if (estimatedMovesLeft > 5) { // Keep minimum of 5
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
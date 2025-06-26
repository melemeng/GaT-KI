package GaT.engine;

import GaT.search.MoveGenerator;
import GaT.model.GameState;
import GaT.model.Move;
import java.util.List;

public class TimeManager {
    private long remainingTime;
    private int estimatedMovesLeft;
    private Phase phase;

    // FIXED: More aggressive thresholds
    private static final long PANIC_TIME_THRESHOLD = 1000;
    private static final long EMERGENCY_TIME_THRESHOLD = 5000;

    public enum Phase {
        OPENING, MIDDLEGAME, ENDGAME
    }

    public TimeManager(long remainingTime, int estimatedMovesLeft) {
        this.remainingTime = remainingTime;
        this.estimatedMovesLeft = Math.max(estimatedMovesLeft, 8); // More realistic
        this.phase = Phase.OPENING;
    }

    /**
     * FIXED: More aggressive time allocation
     */
    public long calculateTimeForMove(GameState state) {
        // Emergency handling
        if (remainingTime <= PANIC_TIME_THRESHOLD) {
            return Math.max(200, remainingTime / 3); // Use 1/3 of remaining in panic
        }

        if (remainingTime <= EMERGENCY_TIME_THRESHOLD) {
            return Math.max(500, remainingTime / 2); // Use 1/2 in emergency
        }

        // FIXED: Much more aggressive base time calculation
        long baseTime = calculateAggressiveBaseTime();

        // Phase adjustment
        this.phase = detectGamePhase(state);
        switch (phase) {
            case OPENING -> baseTime = baseTime * 4 / 5; // 20% less for opening
            case MIDDLEGAME -> baseTime = baseTime * 5 / 4; // 25% more for middlegame
            case ENDGAME -> baseTime = baseTime * 3 / 2; // 50% more for endgame
        }

        // Complexity adjustment
        int complexity = evaluatePositionComplexity(state);
        if (complexity > 25) {
            baseTime = baseTime * 3 / 2; // 50% more for complex positions
        } else if (complexity < 10) {
            baseTime = baseTime * 4 / 5; // 20% less for simple positions
        }

        // FIXED: Aggressive bounds
        long minTime = Math.max(500, remainingTime / 30); // Minimum 500ms or 3.3% of remaining
        long maxTime = remainingTime / 3; // Maximum 33% of remaining time

        baseTime = Math.max(minTime, Math.min(baseTime, maxTime));

        System.out.printf("🕐 AGGRESSIVE Time: %dms (Phase: %s, Complexity: %d, Remaining: %dms)%n",
                baseTime, phase, complexity, remainingTime);

        return baseTime;
    }

    /**
     * FIXED: More aggressive base time calculation
     */
    private long calculateAggressiveBaseTime() {
        // Use much more time per move
        long baseTimePerMove = remainingTime / Math.max(estimatedMovesLeft, 6); // Was much higher

        // Guarantee aggressive minimum
        long aggressiveMinimum = remainingTime / 10; // 10% of remaining time minimum

        return Math.max(baseTimePerMove, aggressiveMinimum);
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
            int complexity = allMoves.size() / 2;

            // Check for captures and threats
            int captureCount = 0;
            for (Move move : allMoves) {
                if (isCapture(move, state)) {
                    captureCount++;
                }
            }
            complexity += captureCount * 3;

            // Check guard positions
            if (areGuardsAdvanced(state)) {
                complexity += 10;
            }

            return complexity;
        } catch (Exception e) {
            return 15; // Safe default
        }
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
        if (estimatedMovesLeft > 3) {
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

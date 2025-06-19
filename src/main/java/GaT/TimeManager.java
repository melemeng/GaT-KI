package GaT;

import GaT.Objects.GameState;
import GaT.Objects.Move;

import java.util.Arrays;
import java.util.List;

public class TimeManager {
    private long remainingTime;
    private int estimatedMovesLeft;
    private Phase phase;

    // Emergency time thresholds
    private static final long PANIC_TIME_THRESHOLD = 3000;     // 3 seconds
    private static final long EMERGENCY_TIME_THRESHOLD = 10000; // 10 seconds

    enum Phase {
        START,
        MID,
        END,
    }

    public TimeManager(long remainingTime, int estimatedMovesLeft) {
        this.remainingTime = remainingTime;
        this.estimatedMovesLeft = estimatedMovesLeft;
        this.phase = Phase.START;
    }

    /**
     * Berechnet die Zeit für einen Zug basierend auf Spielphase und Komplexität.
     * FIXED: Adds emergency time management and safe limits
     */
    public long calculateTimeForMove(GameState state) {
        // === EMERGENCY TIME MANAGEMENT ===
        if (remainingTime <= PANIC_TIME_THRESHOLD) {
            long panicTime = Math.max(200, remainingTime / 10);
            System.out.println("⚠️ PANIC: Only " + panicTime + "ms!");
            return panicTime;
        }

        if (remainingTime <= EMERGENCY_TIME_THRESHOLD) {
            long emergencyTime = Math.max(500, remainingTime / 8);
            System.out.println("🚨 EMERGENCY: " + emergencyTime + "ms");
            return emergencyTime;
        }

        // === NORMAL TIME CALCULATION ===
        long baseTime = calculateTimePerMove();

        // Phase adjustment
        this.phase = detectGamePhase(state);
        if (phase.equals(Phase.START)) {
            baseTime = baseTime * 2 / 3; // 33% less time in opening
        } else if (phase.equals(Phase.END)) {
            baseTime = baseTime * 3 / 2; // 50% more time in endgame
        }

        // Complexity adjustment
        int complexity = evaluatePositionComplexity(state);
        if (complexity > 20) {
            baseTime = baseTime * 5 / 4; // 25% more for complex positions
        } else if (complexity < 5) {
            baseTime = baseTime * 3 / 4; // 25% less for simple positions
        }

        // === SAFE TIME LIMITS ===
        // FIXED: Much safer maximum time limit
        long maxTimeForMove = remainingTime / Math.max(estimatedMovesLeft, 6);
        long minTimeForMove = Math.max(300, remainingTime / 50); // Minimum 300ms

        baseTime = Math.max(minTimeForMove, Math.min(baseTime, maxTimeForMove));

        System.out.println("🕐 Allocated: " + baseTime + "ms (Phase: " + phase +
                ", Complexity: " + complexity + ", Remaining: " + remainingTime + "ms)");
        return baseTime;
    }

    /**
     * Berechnet die Grundzeit pro Zug basierend auf verbleibender Zeit und geschätzten Zügen.
     */
    private long calculateTimePerMove() {
        return remainingTime / Math.max(estimatedMovesLeft, 1);
    }

    /**
     * Erkennt die aktuelle Spielphase basierend auf dem GameState.
     * FIXED: Now considers guard advancement for better phase detection
     */
    private Phase detectGamePhase(GameState state) {
        int totalPieces = getMaterialCount(state);

        // Also consider guard positions for phase detection
        boolean guardsAdvanced = areGuardsAdvanced(state);

        if (totalPieces > 8 && !guardsAdvanced) {
            return Phase.START; // Many pieces, guards safe
        } else if (totalPieces <= 4 || guardsAdvanced) {
            return Phase.END; // Few pieces OR guards advanced
        } else {
            return Phase.MID; // Middle game
        }
    }

    /**
     * Helper method to check if guards are in advanced positions
     */
    private boolean areGuardsAdvanced(GameState state) {
        // Check if guards are in enemy territory (past center)
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            if (GameState.rank(redGuardPos) >= 4) return true; // Red guard advanced
        }

        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            if (GameState.rank(blueGuardPos) <= 2) return true; // Blue guard advanced
        }

        return false;
    }

    /**
     * FIXED: Now counts actual tower heights, not just squares with pieces
     * @apiNote ignores Guards for now. --> only counts towers
     * @param state
     * @return Material Count for the whole board
     */
    private int getMaterialCount(GameState state) {
        // Count actual tower heights, not just squares
        int redCount = Arrays.stream(state.redStackHeights).sum();
        int blueCount = Arrays.stream(state.blueStackHeights).sum();
        return redCount + blueCount;
    }

    /**
     * Bewertet die Komplexität der Position basierend auf möglichen Zügen.
     * FIXED: Enhanced complexity evaluation with tactical awareness
     */
    private int evaluatePositionComplexity(GameState state) {
        int totalMoves = MoveGenerator.generateAllMoves(state).size();
        List<Move> tacticalMoves = QuiescenceSearch.generateTacticalMoves(state);

        // Base complexity from move count
        int complexity = totalMoves / 2; // Normalize move count

        // Heavy weight for tactical moves (these need more thinking)
        complexity += tacticalMoves.size() * 3;

        // Add complexity for dangerous guard positions
        if (isGuardInDanger(state, tacticalMoves)) {
            complexity += 10;
        }

        // Add complexity for material imbalances
        int materialImbalance = Math.abs(
                Arrays.stream(state.redStackHeights).sum() -
                        Arrays.stream(state.blueStackHeights).sum()
        );
        if (materialImbalance > 2 && materialImbalance < 6) {
            complexity += 5; // Unclear material situations are complex
        }

        // Add complexity for guard advancement
        if (areGuardsAdvanced(state)) {
            complexity += 8;
        }

        return complexity;
    }

    /**
     * Helper method to check if guard is in immediate danger
     */
    private boolean isGuardInDanger(GameState state, List<Move> tacticalMoves) {
        // Quick check if guard can be captured next move
        return tacticalMoves.stream()
                .anyMatch(move -> isGuardCapture(move, state));
    }

    /**
     * Helper method to check if a move captures a guard
     */
    private boolean isGuardCapture(Move move, GameState state) {
        long targetBit = GameState.bit(move.to);

        // Check if move targets enemy guard position
        if (state.redToMove) {
            // Red to move, check if capturing blue guard
            return (state.blueGuard & targetBit) != 0;
        } else {
            // Blue to move, check if capturing red guard
            return (state.redGuard & targetBit) != 0;
        }
    }

    /**
     * Updates remaining time - called after each move
     */
    public void updateRemainingTime(long remainingTime) {
        this.remainingTime = remainingTime;
    }

    /**
     * Decrements estimated moves left - called after each move
     */
    public void decrementEstimatedMovesLeft() {
        if (estimatedMovesLeft > 1) {
            this.estimatedMovesLeft--;
        }
    }

    /**
     * Get current phase for debugging
     */
    public Phase getCurrentPhase() {
        return phase;
    }

    /**
     * Get remaining time for debugging
     */
    public long getRemainingTime() {
        return remainingTime;
    }

    /**
     * Get estimated moves left for debugging
     */
    public int getEstimatedMovesLeft() {
        return estimatedMovesLeft;
    }
}
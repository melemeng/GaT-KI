package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;

import java.util.*;

/**
 * THREAT MAP - Lightweight Threat Detection
 *
 * Efficient threat analysis that integrates with existing evaluation modules.
 * Focuses on immediate and 2-move threats for optimal performance.
 */
public class ThreatMap {

    // === THREAT TYPES ===
    public enum ThreatType {
        IMMEDIATE,    // Can be executed next turn
        DELAYED,      // Requires 2 moves
        CONDITIONAL   // Depends on response
    }

    // === THREAT DATA ===
    public static class Threat {
        public final int targetSquare;
        public final int attackerSquare;
        public final ThreatType type;
        public final int turnsToExecute;
        public final int threatValue;

        public Threat(int targetSquare, int attackerSquare, ThreatType type,
                      int turnsToExecute, int threatValue) {
            this.targetSquare = targetSquare;
            this.attackerSquare = attackerSquare;
            this.type = type;
            this.turnsToExecute = turnsToExecute;
            this.threatValue = threatValue;
        }
    }

    // Threat storage - using arrays for performance
    private final List<Threat>[] redThreats = new List[49];
    private final List<Threat>[] blueThreats = new List[49];

    // Threat values
    private static final int GUARD_THREAT_VALUE = 1000;
    private static final int TOWER_THREAT_VALUE = 100;

    public ThreatMap() {
        // Initialize threat arrays
        for (int i = 0; i < 49; i++) {
            redThreats[i] = new ArrayList<>();
            blueThreats[i] = new ArrayList<>();
        }
    }

    /**
     * Build threat map for current position - OPTIMIZED
     */
    public void buildThreatMap(GameState state) {
        clearThreats();

        // Immediate threats only (for performance)
        analyzeImmediateThreats(state);

        // 2-move threats only in critical positions
        if (isCriticalPosition(state)) {
            analyzeDelayedThreats(state);
        }
    }

    /**
     * Clear all threats
     */
    private void clearThreats() {
        for (int i = 0; i < 49; i++) {
            redThreats[i].clear();
            blueThreats[i].clear();
        }
    }

    /**
     * Analyze immediate threats - FAST
     */
    private void analyzeImmediateThreats(GameState state) {
        // Red's immediate threats
        if (!state.redToMove) {
            analyzeImmediateThreatsForSide(state, true);
        }

        // Blue's immediate threats
        if (state.redToMove) {
            analyzeImmediateThreatsForSide(state, false);
        }
    }

    private void analyzeImmediateThreatsForSide(GameState state, boolean isRed) {
        GameState simState = state.copy();
        simState.redToMove = isRed;

        List<Move> moves = MoveGenerator.generateAllMoves(simState);

        for (Move move : moves) {
            // Check what this move threatens
            int targetValue = evaluateMoveTarget(state, move, isRed);

            if (targetValue > 0) {
                List<Threat>[] threats = isRed ? redThreats : blueThreats;
                threats[move.to].add(new Threat(
                        move.to, move.from, ThreatType.IMMEDIATE, 1, targetValue
                ));
            }
        }
    }

    /**
     * Analyze 2-move threats - LIMITED
     */
    private void analyzeDelayedThreats(GameState state) {
        // Only analyze first 10 moves to avoid timeout
        analyzeDelayedThreatsForSide(state, true, 10);
        analyzeDelayedThreatsForSide(state, false, 10);
    }

    private void analyzeDelayedThreatsForSide(GameState state, boolean isRed, int limit) {
        GameState firstMove = state.copy();
        firstMove.redToMove = isRed;

        List<Move> firstMoves = MoveGenerator.generateAllMoves(firstMove);
        int analyzed = 0;

        for (Move move1 : firstMoves) {
            if (analyzed++ >= limit) break;

            GameState afterFirst = firstMove.copy();
            afterFirst.applyMove(move1);
            afterFirst.redToMove = isRed; // Same player

            List<Move> secondMoves = MoveGenerator.generateAllMoves(afterFirst);

            for (Move move2 : secondMoves) {
                int targetValue = evaluateMoveTarget(afterFirst, move2, isRed);

                if (targetValue > TOWER_THREAT_VALUE * 2) { // Only significant threats
                    List<Threat>[] threats = isRed ? redThreats : blueThreats;
                    threats[move2.to].add(new Threat(
                            move2.to, move1.from, ThreatType.DELAYED, 2, targetValue
                    ));
                }
            }
        }
    }

    /**
     * Evaluate what a move threatens
     */
    private int evaluateMoveTarget(GameState state, Move move, boolean byRed) {
        int targetSquare = move.to;
        int value = 0;

        // Check if threatens enemy guard
        long enemyGuard = byRed ? state.blueGuard : state.redGuard;
        if (enemyGuard != 0 && targetSquare == Long.numberOfTrailingZeros(enemyGuard)) {
            return GUARD_THREAT_VALUE;
        }

        // Check if threatens enemy piece
        if (byRed) {
            value = state.blueStackHeights[targetSquare] * TOWER_THREAT_VALUE;
        } else {
            value = state.redStackHeights[targetSquare] * TOWER_THREAT_VALUE;
        }

        // Check if threatens castle entry
        if (threatensCastle(state, move, byRed)) {
            value = Math.max(value, GUARD_THREAT_VALUE / 2);
        }

        return value;
    }

    /**
     * Get threats to a specific square
     */
    public List<Threat> getThreatsToSquare(int square, boolean byRed) {
        if (square < 0 || square >= 49) return Collections.emptyList();
        List<Threat>[] threats = byRed ? redThreats : blueThreats;
        return new ArrayList<>(threats[square]);
    }

    /**
     * Calculate threat score for evaluation - FAST
     */
    public int calculateThreatScore(GameState state) {
        int threatScore = 0;

        // Evaluate threats to important squares only
        for (int i = 0; i < 49; i++) {
            // Skip empty squares
            if (state.redStackHeights[i] == 0 && state.blueStackHeights[i] == 0) {
                continue;
            }

            // Red threats
            for (Threat threat : redThreats[i]) {
                int value = threat.threatValue;
                if (threat.type == ThreatType.DELAYED) value /= 2;
                threatScore += value;
            }

            // Blue threats
            for (Threat threat : blueThreats[i]) {
                int value = threat.threatValue;
                if (threat.type == ThreatType.DELAYED) value /= 2;
                threatScore -= value;
            }
        }

        // Special handling for guard threats
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            if (!blueThreats[guardPos].isEmpty()) {
                threatScore -= 300; // Extra penalty for threatened guard
            }
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            if (!redThreats[guardPos].isEmpty()) {
                threatScore += 300;
            }
        }

        return threatScore;
    }

    /**
     * Check if any immediate threats exist to a square
     */
    public boolean hasImmediateThreat(int square, boolean byRed) {
        List<Threat>[] threats = byRed ? redThreats : blueThreats;
        for (Threat threat : threats[square]) {
            if (threat.type == ThreatType.IMMEDIATE) {
                return true;
            }
        }
        return false;
    }

    // === HELPER METHODS ===

    private boolean isCriticalPosition(GameState state) {
        // Guards advanced
        if (state.redGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
            if (rank <= 2) return true;
        }
        if (state.blueGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
            if (rank >= 4) return true;
        }

        // Low material
        int totalMaterial = 0;
        for (int i = 0; i < 49; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        return totalMaterial <= 10;
    }

    private boolean threatensCastle(GameState state, Move move, boolean byRed) {
        // Check if guard move threatens castle
        long guardBit = byRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0 || move.from != Long.numberOfTrailingZeros(guardBit)) {
            return false;
        }

        int targetCastle = byRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);
        int distance = Math.abs(GameState.rank(move.to) - GameState.rank(targetCastle));

        return distance <= 1;
    }
}
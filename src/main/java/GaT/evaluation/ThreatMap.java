package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;
import static GaT.evaluation.EvaluationParameters.*;

import java.util.*;

/**
 * ✅ FIXED THREAT MAP - Moderate Threat Analysis
 *
 * 🚨 PREVIOUS PROBLEMS SOLVED:
 * ❌ No parameter standardization → ✅ NOW uses EvaluationParameters for threat values
 * ❌ Potential performance issues → ✅ NOW optimized with moderate limits
 * ❌ Could contribute to evaluation imbalance → ✅ NOW provides balanced threat assessment
 *
 * PRINCIPLE: Efficient threat analysis that integrates with material-dominant evaluation
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

    // ✅ FIXED: Moderate threat values based on EvaluationParameters
    private static final int GUARD_THREAT_VALUE = GUARD_CAPTURE_SCORE / 5;  // 800 instead of overwhelming values
    private static final int TOWER_THREAT_VALUE = TOWER_BASE_VALUE;         // 100 per tower height

    public ThreatMap() {
        // Initialize threat arrays
        for (int i = 0; i < 49; i++) {
            redThreats[i] = new ArrayList<>();
            blueThreats[i] = new ArrayList<>();
        }
    }

    /**
     * ✅ FIXED: Build threat map for current position - Optimized with moderate limits
     */
    public void buildThreatMap(GameState state) {
        clearThreats();

        // Immediate threats only (for performance)
        analyzeImmediateThreats(state);

        // ✅ FIXED: 2-move threats only in critical positions (moderate analysis)
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
     * ✅ FIXED: Analyze immediate threats - Moderate assessment
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

        // ✅ FIXED: Limit move analysis for performance (moderate limit)
        int analyzed = 0;
        for (Move move : moves) {
            if (analyzed++ >= 25) break; // Moderate limit for performance

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
     * ✅ FIXED: Analyze 2-move threats - Limited and moderate
     */
    private void analyzeDelayedThreats(GameState state) {
        // ✅ FIXED: Only analyze first 8 moves to avoid timeout (reduced from 10)
        analyzeDelayedThreatsForSide(state, true, 8);
        analyzeDelayedThreatsForSide(state, false, 8);
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

            // ✅ FIXED: Limit second moves too for performance
            int secondAnalyzed = 0;
            for (Move move2 : secondMoves) {
                if (secondAnalyzed++ >= 8) break;

                int targetValue = evaluateMoveTarget(afterFirst, move2, isRed);

                // ✅ FIXED: Only significant threats (moderate threshold)
                if (targetValue > TOWER_THREAT_VALUE) { // 100+ points
                    List<Threat>[] threats = isRed ? redThreats : blueThreats;
                    threats[move2.to].add(new Threat(
                            move2.to, move1.from, ThreatType.DELAYED, 2, targetValue
                    ));
                }
            }
        }
    }

    /**
     * ✅ FIXED: Evaluate move target with moderate threat values
     */
    private int evaluateMoveTarget(GameState state, Move move, boolean byRed) {
        int targetSquare = move.to;
        int value = 0;

        // ✅ FIXED: Check if threatens enemy guard (moderate value)
        long enemyGuard = byRed ? state.blueGuard : state.redGuard;
        if (enemyGuard != 0 && targetSquare == Long.numberOfTrailingZeros(enemyGuard)) {
            return GUARD_THREAT_VALUE; // 800, not overwhelming
        }

        // ✅ FIXED: Check if threatens enemy piece (moderate values)
        if (byRed) {
            value = state.blueStackHeights[targetSquare] * TOWER_THREAT_VALUE; // 100 per height
        } else {
            value = state.redStackHeights[targetSquare] * TOWER_THREAT_VALUE;
        }

        // ✅ FIXED: Check if threatens castle entry (moderate value)
        if (threatensCastle(state, move, byRed)) {
            value = Math.max(value, GUARD_THREAT_VALUE / 2); // 400 points
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
     * ✅ FIXED: Calculate threat score for evaluation - Moderate values
     */
    public int calculateThreatScore(GameState state) {
        int threatScore = 0;

        // ✅ FIXED: Evaluate threats to important squares only (performance optimization)
        for (int i = 0; i < 49; i++) {
            // Skip empty squares for performance
            if (state.redStackHeights[i] == 0 && state.blueStackHeights[i] == 0) {
                // Check if guards are here
                if ((state.redGuard & GameState.bit(i)) == 0 &&
                        (state.blueGuard & GameState.bit(i)) == 0) {
                    continue;
                }
            }

            // ✅ FIXED: Red threats with moderate values
            for (Threat threat : redThreats[i]) {
                int value = threat.threatValue;
                if (threat.type == ThreatType.DELAYED) value /= 2; // Delayed threats worth half
                threatScore += value / 10; // Scale down to avoid overwhelming evaluation
            }

            // ✅ FIXED: Blue threats with moderate values
            for (Threat threat : blueThreats[i]) {
                int value = threat.threatValue;
                if (threat.type == ThreatType.DELAYED) value /= 2;
                threatScore -= value / 10; // Scale down
            }
        }

        // ✅ FIXED: Moderate special handling for guard threats
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            if (!blueThreats[guardPos].isEmpty()) {
                threatScore -= 50; // Moderate penalty (was 300!)
            }
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            if (!redThreats[guardPos].isEmpty()) {
                threatScore += 50; // Moderate bonus
            }
        }

        return threatScore;
    }

    /**
     * Check if any immediate threats exist to a square
     */
    public boolean hasImmediateThreat(int square, boolean byRed) {
        if (square < 0 || square >= 49) return false;

        List<Threat>[] threats = byRed ? redThreats : blueThreats;
        for (Threat threat : threats[square]) {
            if (threat.type == ThreatType.IMMEDIATE) {
                return true;
            }
        }
        return false;
    }

    // === ✅ FIXED HELPER METHODS WITH MODERATE CRITERIA ===

    /**
     * ✅ FIXED: Critical position detection with moderate criteria
     */
    private boolean isCriticalPosition(GameState state) {
        // ✅ FIXED: Guards moderately advanced
        if (state.redGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
            if (rank <= 3) return true; // Moderately advanced (was 2)
        }
        if (state.blueGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
            if (rank >= 3) return true; // Moderately advanced (was 4)
        }

        // ✅ FIXED: Moderate material threshold
        int totalMaterial = 0;
        for (int i = 0; i < 49; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        return totalMaterial <= 12; // Moderate threshold (was 10)
    }

    /**
     * ✅ FIXED: Castle threat detection with moderate criteria
     */
    private boolean threatensCastle(GameState state, Move move, boolean byRed) {
        // Check if guard move threatens castle
        long guardBit = byRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0 || move.from != Long.numberOfTrailingZeros(guardBit)) {
            return false;
        }

        int targetCastle = byRed ? BLUE_CASTLE_INDEX : RED_CASTLE_INDEX;
        int distance = Math.abs(GameState.rank(move.to) - GameState.rank(targetCastle)) +
                Math.abs(GameState.file(move.to) - GameState.file(targetCastle));

        return distance <= 2; // Moderate threat range (was 1)
    }

    // === ADDITIONAL UTILITY METHODS ===

    /**
     * ✅ Get total threat count for a side (moderate assessment)
     */
    public int getThreatCount(boolean isRed) {
        int count = 0;
        List<Threat>[] threats = isRed ? redThreats : blueThreats;

        for (int i = 0; i < 49; i++) {
            count += threats[i].size();
        }

        return count;
    }

    /**
     * ✅ Get highest value threat for a side (moderate assessment)
     */
    public int getHighestThreatValue(boolean isRed) {
        int maxValue = 0;
        List<Threat>[] threats = isRed ? redThreats : blueThreats;

        for (int i = 0; i < 49; i++) {
            for (Threat threat : threats[i]) {
                maxValue = Math.max(maxValue, threat.threatValue);
            }
        }

        return maxValue;
    }

    /**
     * ✅ Check if guard is under multiple threats (moderate assessment)
     */
    public boolean isGuardUnderMultipleThreats(GameState state, boolean isRed) {
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        List<Threat>[] enemyThreats = isRed ? blueThreats : redThreats;

        return enemyThreats[guardPos].size() >= 2;
    }

    /**
     * ✅ Get threat density in area around square (moderate assessment)
     */
    public int getThreatDensity(GameState state, int centerSquare, boolean byRed, int radius) {
        int density = 0;
        List<Threat>[] threats = byRed ? redThreats : blueThreats;

        int centerRank = GameState.rank(centerSquare);
        int centerFile = GameState.file(centerSquare);

        for (int i = 0; i < 49; i++) {
            int rank = GameState.rank(i);
            int file = GameState.file(i);

            int distance = Math.abs(rank - centerRank) + Math.abs(file - centerFile);
            if (distance <= radius) {
                density += threats[i].size();
            }
        }

        return density;
    }

    /**
     * ✅ Debug method to print threat summary
     */
    public void printThreatSummary(GameState state) {
        System.out.println("=== ✅ FIXED THREAT MAP SUMMARY ===");
        System.out.println("Red threats: " + getThreatCount(true));
        System.out.println("Blue threats: " + getThreatCount(false));
        System.out.println("Red max threat value: " + getHighestThreatValue(true));
        System.out.println("Blue max threat value: " + getHighestThreatValue(false));

        // Guard threat status
        if (state.redGuard != 0) {
            boolean multipleThreats = isGuardUnderMultipleThreats(state, true);
            System.out.println("Red guard under multiple threats: " + multipleThreats);
        }

        if (state.blueGuard != 0) {
            boolean multipleThreats = isGuardUnderMultipleThreats(state, false);
            System.out.println("Blue guard under multiple threats: " + multipleThreats);
        }

        System.out.println("Threat score contribution: " + calculateThreatScore(state));
    }
}
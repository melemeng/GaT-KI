package GaT.engine;

import GaT.search.MoveGenerator;
import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.QuiescenceSearch;

import java.util.Arrays;
import java.util.List;

/**
 * FIXED TIME MANAGER - Viel aggressiveres Zeitmanagement
 *
 * KRITISCHE FIXES:
 * ✅ 1. Viel höhere Zeitallokation (60-80% statt 5%)
 * ✅ 2. Angepasste Sicherheitsmargen
 * ✅ 3. Bessere Phasenerkennug
 * ✅ 4. Tournament-ready Zeitverteilung
 */
public class TimeManager {
    private long remainingTime;
    private int estimatedMovesLeft;
    private Phase phase;

    // FIXED: Viel weniger konservative Schwellenwerte
    private static final long PANIC_TIME_THRESHOLD = 1000;     // 1 Sekunde (war 3)
    private static final long EMERGENCY_TIME_THRESHOLD = 5000; // 5 Sekunden (war 10)

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
     * FIXED: Viel aggressivere Zeitallokation
     */
    public long calculateTimeForMove(GameState state) {
        // === EMERGENCY TIME MANAGEMENT ===
        if (remainingTime <= PANIC_TIME_THRESHOLD) {
            long panicTime = Math.max(300, remainingTime / 5); // Weniger konservativ
            System.out.println("⚠️ PANIC: " + panicTime + "ms");
            return panicTime;
        }

        if (remainingTime <= EMERGENCY_TIME_THRESHOLD) {
            long emergencyTime = Math.max(800, remainingTime / 4); // Weniger konservativ
            System.out.println("🚨 EMERGENCY: " + emergencyTime + "ms");
            return emergencyTime;
        }

        // === AGGRESSIVE TIME CALCULATION ===
        long baseTime = calculateAggressiveTimePerMove();

        // Phase adjustment
        this.phase = detectGamePhase(state);
        if (phase.equals(Phase.START)) {
            baseTime = baseTime * 4 / 5; // 20% weniger für Eröffnung
        } else if (phase.equals(Phase.END)) {
            baseTime = baseTime * 6 / 4; // 50% mehr für Endspiel
        }

        // Complexity adjustment - viel aggressiver
        int complexity = evaluatePositionComplexity(state);
        if (complexity > 20) {
            baseTime = baseTime * 3 / 2; // 50% mehr für komplexe Positionen
        } else if (complexity < 5) {
            baseTime = baseTime * 3 / 4; // Nur 25% weniger für einfache
        }

        // === MUCH MORE AGGRESSIVE TIME LIMITS ===
        // FIXED: Verwende viel mehr Zeit!
        long maxTimeForMove = remainingTime / Math.max(estimatedMovesLeft, 3); // War 6!
        long minTimeForMove = Math.max(1000, remainingTime / 20); // Minimum 1s, war 300ms

        // CRITICAL: Verwende mindestens 30% der Grundzeit
        long aggressiveMinimum = remainingTime / 10; // 10% der verbleibenden Zeit
        baseTime = Math.max(baseTime, aggressiveMinimum);

        baseTime = Math.max(minTimeForMove, Math.min(baseTime, maxTimeForMove));

        System.out.println("🕐 AGGRESSIVE Allocated: " + baseTime + "ms (Phase: " + phase +
                ", Complexity: " + complexity + ", Remaining: " + remainingTime + "ms)");
        return baseTime;
    }

    /**
     * FIXED: Viel aggressivere Grundzeit-Berechnung
     */
    private long calculateAggressiveTimePerMove() {
        // FIXED: Verwende viel mehr Zeit pro Zug
        long baseTimePerMove = remainingTime / Math.max(estimatedMovesLeft, 8); // War viel höher

        // Garantiere minimum aggressive Zeit
        long aggressiveBase = remainingTime / 15; // 6.7% der verbleibenden Zeit

        return Math.max(baseTimePerMove, aggressiveBase);
    }

    /**
     * FIXED: Bessere Phasenerkennug
     */
    private Phase detectGamePhase(GameState state) {
        int totalPieces = getMaterialCount(state);
        boolean guardsAdvanced = areGuardsAdvanced(state);

        // Mehr nuancierte Phasenerkennung
        if (totalPieces >= 12 && !guardsAdvanced) {
            return Phase.START;
        } else if (totalPieces <= 6 || guardsAdvanced) {
            return Phase.END;
        } else {
            return Phase.MID;
        }
    }

    /**
     * FIXED: Aggressivere Komplexitätsbewertung
     */
    private int evaluatePositionComplexity(GameState state) {
        int totalMoves = MoveGenerator.generateAllMoves(state).size();
        List<Move> tacticalMoves = QuiescenceSearch.generateTacticalMoves(state);

        // Basis-Komplexität
        int complexity = totalMoves / 2;

        // FIXED: Taktische Züge bekommen noch mehr Gewicht
        complexity += tacticalMoves.size() * 5; // War 3

        // Gefährdete Wächter = sehr komplex
        if (isGuardInDanger(state, tacticalMoves)) {
            complexity += 15; // War 10
        }

        // Material-Ungleichgewicht
        int materialImbalance = Math.abs(
                Arrays.stream(state.redStackHeights).sum() -
                        Arrays.stream(state.blueStackHeights).sum()
        );
        if (materialImbalance > 1 && materialImbalance < 8) {
            complexity += 8; // War 5
        }

        // Fortgeschrittene Wächter
        if (areGuardsAdvanced(state)) {
            complexity += 12; // War 8
        }

        return complexity;
    }

    // Rest der Methoden bleiben gleich...
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

    private int getMaterialCount(GameState state) {
        int redCount = Arrays.stream(state.redStackHeights).sum();
        int blueCount = Arrays.stream(state.blueStackHeights).sum();
        return redCount + blueCount;
    }

    private boolean isGuardInDanger(GameState state, List<Move> tacticalMoves) {
        return tacticalMoves.stream()
                .anyMatch(move -> isGuardCapture(move, state));
    }

    private boolean isGuardCapture(Move move, GameState state) {
        long targetBit = GameState.bit(move.to);
        if (state.redToMove) {
            return (state.blueGuard & targetBit) != 0;
        } else {
            return (state.redGuard & targetBit) != 0;
        }
    }

    // Public interface methods
    public void updateRemainingTime(long remainingTime) {
        this.remainingTime = remainingTime;
    }

    public void decrementEstimatedMovesLeft() {
        if (estimatedMovesLeft > 2) { // FIXED: War 1
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
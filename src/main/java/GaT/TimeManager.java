package GaT;

import GaT.Objects.GameState;

import java.util.Arrays;

public class TimeManager {
    private long remainingTime;
    private int estimatedMovesLeft;
    private Phase phase;

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
     */
    public long calculateTimeForMove(GameState state) {
        // Grundzeit pro Zug berechnen
        long baseTime = calculateTimePerMove();

        // Dynamische Anpassung basierend auf Spielphase
        this.phase = detectGamePhase(state);
        if (phase.equals(Phase.START)) {
            baseTime /= 2; // Weniger Zeit in der Eröffnung
        } else if (phase.equals(Phase.END)) {
            baseTime *= 2; // Mehr Zeit im Endspiel
        }

        // Komplexität der Position bewerten
        int complexity = evaluatePositionComplexity(state);
        if (complexity > 20) {
            baseTime *= 1.5; // Mehr Zeit für komplexe Positionen
        } else if (complexity < 5) {
            baseTime /= 2; // Weniger Zeit für einfache Positionen
        }

        // Hard-Limit
        long maxTimeForMove = remainingTime / 2;
        return Math.min(baseTime, maxTimeForMove);
    }

    /**
     * Berechnet die Grundzeit pro Zug basierend auf verbleibender Zeit und geschätzten Zügen.
     */
    private long calculateTimePerMove() {
        return remainingTime / estimatedMovesLeft;
    }

    /**
     * Erkennt die aktuelle Spielphase basierend auf dem GameState.
     */
    private Phase detectGamePhase(GameState state) {
        int totalPieces = getMaterialCount(state); // Beispiel: Anzahl der verbleibenden Figuren

        if (totalPieces > 6) {
            return Phase.START; // Viele Figuren -> Eröffnung
        } else if (totalPieces > 3) {
            return Phase.MID; // Mittlere Anzahl -> Mittelspiel
        } else {
            return Phase.END; // Wenige Figuren -> Endspiel
        }
    }

    /**
     * @apiNote ignores Guards for now. --> only counts towers
     * @param state
     * @return Material Count for the whole board
     */
    private int getMaterialCount(GameState state) {
        int redCount = (int) Arrays.stream(state.redStackHeights)
                .filter(height -> height >= 1)
                .count();

        int blueCount = (int) Arrays.stream(state.blueStackHeights)
                .filter(height -> height >= 1)
                .count();
        return redCount + blueCount;
    }

    /**
     * Bewertet die Komplexität der Position basierend auf möglichen Zügen.
     */
    private int evaluatePositionComplexity(GameState state) {
        int totalMoves = MoveGenerator.generateAllMoves(state).size();
        int tacticalMoves = QuiescenceSearch.generateTacticalMoves(state).size();

        // Gewichtung: Taktische Züge sind wichtiger als normale Züge
        return totalMoves + 2 * tacticalMoves;
    }


    public void updateRemainingTime(long remainingTime) {
        this.remainingTime = remainingTime;
    }

    public void decrementEstimatedMovesLeft() {
        this.estimatedMovesLeft--;
    }
}
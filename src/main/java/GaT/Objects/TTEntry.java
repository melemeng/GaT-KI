package GaT.Objects;

public class TTEntry {
    public int score;
    public int depth;
    public int flag; // EXACT, LOWER_BOUND, UPPER_BOUND
    public Move bestMove; // Bester Zug für diese Position

    // Konstanten für Transposition Table Flags
    public static final int EXACT = 0;
    public static final int LOWER_BOUND = 1;
    public static final int UPPER_BOUND = 2;

    public TTEntry(int score, int depth, int flag, Move bestMove) {
        this.score = score;
        this.depth = depth;
        this.flag = flag;
        this.bestMove = bestMove;
    }

    // Alter Konstruktor für Kompatibilität - FEHLT!
    public TTEntry(int score, int depth, int alpha, int beta) {
        this.score = score;
        this.depth = depth;
        this.flag = EXACT; // Default
        this.bestMove = null;
    }
}
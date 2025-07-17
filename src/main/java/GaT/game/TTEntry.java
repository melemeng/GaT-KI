package GaT.game;

public class TTEntry {
    public int score;
    public int depth;
    public int flag;
    public Move bestMove;
    public long lastAccessed; // NEW: For LRU eviction

    public static final int EXACT = 0;
    public static final int LOWER_BOUND = 1;
    public static final int UPPER_BOUND = 2;

    public TTEntry(int score, int depth, int flag, Move bestMove) {
        this.score = score;
        this.depth = depth;
        this.flag = flag;
        this.bestMove = bestMove;
        this.lastAccessed = 0; // Will be set when added to TT
    }

    // Keep old constructor for compatibility
    public TTEntry(int score, int depth, int alpha, int beta) {
        this(score, depth, EXACT, null);
    }
}
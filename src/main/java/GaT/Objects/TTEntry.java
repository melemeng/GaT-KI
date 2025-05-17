package GaT.Objects;

public class TTEntry {
    public int score;
    public int depth;
    public int alpha;
    public int beta;

    public TTEntry(int score, int depth, int alpha, int beta) {
        this.score = score;
        this.depth = depth;
        this.alpha = alpha;
        this.beta = beta;
    }
}

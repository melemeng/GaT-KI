package GaT.model;

import java.util.Objects;

public class Move {
    public final int from;
    public final int to;
    public final int amountMoved; // 1 for guard, N for towers

    public Move(int from, int to, int amountMoved) {
        this.from = from;
        this.to = to;
        this.amountMoved = amountMoved;
    }

    @Override
    public String toString() {
        return squareName(from) + "-" + squareName(to) + "-" + amountMoved;
    }

    private static String squareName(int index) {
        char file = (char) ('A' + GameState.file(index));
        int rank = GameState.rank(index) + 1;
        return "" + file + rank;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Move move = (Move) o;
        return from == move.from && to == move.to && amountMoved == move.amountMoved;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, amountMoved);
    }
}


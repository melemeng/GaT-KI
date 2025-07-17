package GaT.game;

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



    // Add these methods to your existing Move.java class

    /**
     * Create move from string notation (for opening book)
     * Format: "d2-d4" or "G-d2" for guard moves
     */
    public static Move fromString(String moveStr) {
        if (moveStr == null || moveStr.length() < 5) {
            throw new IllegalArgumentException("Invalid move string: " + moveStr);
        }

        String[] parts = moveStr.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid move format: " + moveStr);
        }

        int from = parseSquare(parts[0]);
        int to = parseSquare(parts[1]);

        // Calculate amount moved (will be validated when applied)
        int fromRank = from / 7, fromFile = from % 7;
        int toRank = to / 7, toFile = to % 7;

        int amountMoved;
        if (fromRank == toRank) {
            amountMoved = Math.abs(toFile - fromFile);
        } else if (fromFile == toFile) {
            amountMoved = Math.abs(toRank - fromRank);
        } else {
            throw new IllegalArgumentException("Not orthogonal move: " + moveStr);
        }

        return new Move(from, to, amountMoved);
    }

    /**
     * Parse square notation like "d2" to index
     */
    private static int parseSquare(String square) {
        if (square.length() != 2) {
            throw new IllegalArgumentException("Invalid square: " + square);
        }

        char fileChar = Character.toLowerCase(square.charAt(0));
        char rankChar = square.charAt(1);

        if (fileChar < 'a' || fileChar > 'g') {
            throw new IllegalArgumentException("Invalid file: " + fileChar);
        }
        if (rankChar < '1' || rankChar > '7') {
            throw new IllegalArgumentException("Invalid rank: " + rankChar);
        }

        int file = fileChar - 'a';
        int rank = rankChar - '1';

        return rank * 7 + file;
    }

    /**
     * Convert move to string notation
     */
    public String toSquareNotation() {
        return squareToString(from) + "-" + squareToString(to);
    }

    /**
     * Convert square index to notation like "d2"
     */
    private static String squareToString(int square) {
        int rank = square / 7;
        int file = square % 7;
        return "" + (char)('a' + file) + (char)('1' + rank);
    }


}


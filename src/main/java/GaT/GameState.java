package GaT;

import java.util.Arrays;
import java.util.Objects;

public class GameState {
    public static final int BOARD_SIZE = 7;
    public static final int NUM_SQUARES = BOARD_SIZE * BOARD_SIZE;
    public static final long BOARD_MASK = (1L << NUM_SQUARES) - 1;

    public long redGuard;
    public long redTowers;
    public int[] redStackHeights = new int[NUM_SQUARES];

    public long blueGuard;
    public long blueTowers;
    public int[] blueStackHeights = new int[NUM_SQUARES];

    public boolean redToMove = true;

    // Utilities
    public static long bit(int index) {
        return 1L << index;
    }

    public static int getIndex(int rank, int file) {
        return rank * BOARD_SIZE + file;
    }

    public static boolean isOnBoard(int index) {
        return index >= 0 && index < NUM_SQUARES;
    }

    public static int rank(int index) {
        return index / BOARD_SIZE;
    }

    public static int file(int index) {
        return index % BOARD_SIZE;
    }

    public GameState() {
        // White guard on D1
        int blueGuardIndex = getIndex(0, 3);
        blueGuard = bit(blueGuardIndex);

        // White towers (pyramid layout)
        int[] blueTowerSquares = {
                getIndex(0, 0), getIndex(0, 1),
                getIndex(0, 5), getIndex(0, 6),
                getIndex(1, 2), getIndex(1, 4),
                getIndex(2, 3)
        };
        for (int index : blueTowerSquares) {
            blueTowers |= bit(index);
            blueStackHeights[index] = 1;
        }

        // Black guard on D7
        int redGuardIndex = getIndex(6, 3);
        redGuard = bit(redGuardIndex);

        // Black towers (mirror of white's)
        int[] redTowerSquares = {
                getIndex(6, 0), getIndex(6, 1),
                getIndex(6, 5), getIndex(6, 6),
                getIndex(5, 2), getIndex(5, 4),
                getIndex(4, 3)
        };
        for (int index : redTowerSquares) {
            redTowers |= bit(index);
            redStackHeights[index] = 1;
        }

        redToMove = true;
    }



    public GameState(long redGuard, long redTowers, int[] redHeights,
                     long blueGuard, long blueTowers, int[] blackHeights,
                     boolean redToMove) {
        this.redGuard = redGuard;
        this.redTowers = redTowers;
        this.redStackHeights = Arrays.copyOf(redHeights, NUM_SQUARES);

        this.blueGuard = blueGuard;
        this.blueTowers = blueTowers;
        this.blueStackHeights = Arrays.copyOf(blackHeights, NUM_SQUARES);

        this.redToMove = redToMove;
    }

    public void printBoard() {
        System.out.println("  +---------------------------+");

        for (int rank = BOARD_SIZE - 1; rank >= 0; rank--) {
            System.out.print((rank + 1) + " | ");

            for (int file = 0; file < BOARD_SIZE; file++) {
                int index = getIndex(rank, file);
                char symbol = '.';

                if (((redGuard >>> index) & 1) == 1) symbol = 'G';
                else if (((blueGuard >>> index) & 1) == 1) symbol = 'g';
                else if (((redTowers >>> index) & 1) == 1)
                    symbol = (char) ('0' + redStackHeights[index]); // show tower height
                else if (((blueTowers >>> index) & 1) == 1)
                    symbol = (char) ('0' + blueStackHeights[index]);

                System.out.print(symbol + " ");
            }

            System.out.println("|");
        }

        System.out.println("  +---------------------------+");
        System.out.println("    A B C D E F G\n");
    }


    public static GameState fromFen(String fen) {
        String[] parts = fen.trim().split(" ");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid FEN: expected board and turn");

        String boardPart = parts[0];
        String turnPart = parts[1];
        String[] ranks = boardPart.split("/");

        if (ranks.length != BOARD_SIZE)
            throw new IllegalArgumentException("Invalid FEN: expected " + BOARD_SIZE + " ranks");

        GameState state = new GameState();

        // Reset everything
        state.redGuard = 0;
        state.blueGuard = 0;
        state.redTowers = 0;
        state.blueTowers = 0;
        Arrays.fill(state.redStackHeights, 0);
        Arrays.fill(state.blueStackHeights, 0);

        for (int rank = 0; rank < BOARD_SIZE; rank++) {
            String row = ranks[rank];
            int file = 0;
            for (int i = 0; i < row.length(); ) {
                char ch = row.charAt(i);

                // Empty squares
                if (Character.isDigit(ch)) {
                    int count = ch - '0';
                    file += count;
                    i++;
                    continue;
                }

                int index = getIndex(BOARD_SIZE - 1 - rank, file); // ranks from top down

                if (ch == 'r' || ch == 'b') {
                    boolean isBlue = ch == 'b';
                    i++;

                    int height = 1;
                    // Look ahead for ONE digit only (0–7)
                    if (i < row.length() && Character.isDigit(row.charAt(i))) {
                        height = row.charAt(i) - '0';
                        i++;
                    }

                    if (isBlue) {
                        state.blueTowers |= bit(index);
                        state.blueStackHeights[index] = height;
                    } else {
                        state.redTowers |= bit(index);
                        state.redStackHeights[index] = height;
                    }

                    file++;
                } else if (ch == 'R' || ch == 'B') {
                    boolean isBlue = ch == 'B';
                    i +=2;

                    if (isBlue) {
                        state.blueGuard = bit(index);
                    } else {
                        state.redGuard = bit(index);
                    }

                    file++;
                } else {
                    throw new IllegalArgumentException("Unexpected character in FEN: " + ch);
                }
            }
        }

        // Turn
        state.redToMove = turnPart.equals("r");

        return state;
    }


    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GameState gameState)) return false;
        return redGuard == gameState.redGuard && redTowers == gameState.redTowers && blueGuard == gameState.blueGuard && blueTowers == gameState.blueTowers && redToMove == gameState.redToMove && Objects.deepEquals(redStackHeights, gameState.redStackHeights) && Objects.deepEquals(blueStackHeights, gameState.blueStackHeights);
    }

    @Override
    public int hashCode() {
        return Objects.hash(redGuard, redTowers, Arrays.hashCode(redStackHeights), blueGuard, blueTowers, Arrays.hashCode(blueStackHeights), redToMove);
    }
}


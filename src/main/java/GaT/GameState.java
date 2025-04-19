package GaT;

import java.util.Arrays;

public class GameState {
    public static final int BOARD_SIZE = 7;
    public static final int NUM_SQUARES = BOARD_SIZE * BOARD_SIZE;
    public static final long BOARD_MASK = (1L << NUM_SQUARES) - 1;

    public long whiteGuard;
    public long whiteTowers;
    public int[] whiteStackHeights = new int[NUM_SQUARES];

    public long blackGuard;
    public long blackTowers;
    public int[] blackStackHeights = new int[NUM_SQUARES];

    public boolean whiteToMove = true;

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
        int whiteGuardIndex = getIndex(0, 3);
        whiteGuard = bit(whiteGuardIndex);

        // White towers (pyramid layout)
        int[] whiteTowerSquares = {
                getIndex(0, 0), getIndex(0, 1),
                getIndex(0, 5), getIndex(0, 6),
                getIndex(1, 2), getIndex(1, 4),
                getIndex(2, 3)
        };
        for (int index : whiteTowerSquares) {
            whiteTowers |= bit(index);
            whiteStackHeights[index] = 1;
        }

        // Black guard on D7
        int blackGuardIndex = getIndex(6, 3);
        blackGuard = bit(blackGuardIndex);

        // Black towers (mirror of white's)
        int[] blackTowerSquares = {
                getIndex(6, 0), getIndex(6, 1),
                getIndex(6, 5), getIndex(6, 6),
                getIndex(5, 2), getIndex(5, 4),
                getIndex(4, 3)
        };
        for (int index : blackTowerSquares) {
            blackTowers |= bit(index);
            blackStackHeights[index] = 1;
        }

        whiteToMove = true;
    }



    public GameState(long whiteGuard, long whiteTowers, int[] whiteHeights,
                     long blackGuard, long blackTowers, int[] blackHeights,
                     boolean whiteToMove) {
        this.whiteGuard = whiteGuard;
        this.whiteTowers = whiteTowers;
        this.whiteStackHeights = Arrays.copyOf(whiteHeights, NUM_SQUARES);

        this.blackGuard = blackGuard;
        this.blackTowers = blackTowers;
        this.blackStackHeights = Arrays.copyOf(blackHeights, NUM_SQUARES);

        this.whiteToMove = whiteToMove;
    }

    public void printBoard() {
        System.out.println("  +---------------------------+");

        for (int rank = BOARD_SIZE - 1; rank >= 0; rank--) {
            System.out.print((rank + 1) + " | ");

            for (int file = 0; file < BOARD_SIZE; file++) {
                int index = getIndex(rank, file);
                char symbol = '.';

                if (((whiteGuard >>> index) & 1) == 1) symbol = 'G';
                else if (((blackGuard >>> index) & 1) == 1) symbol = 'g';
                else if (((whiteTowers >>> index) & 1) == 1)
                    symbol = (char) ('0' + whiteStackHeights[index]); // show tower height
                else if (((blackTowers >>> index) & 1) == 1)
                    symbol = (char) ('0' + blackStackHeights[index]);

                System.out.print(symbol + " ");
            }

            System.out.println("|");
        }

        System.out.println("  +---------------------------+");
        System.out.println("    A B C D E F G\n");
    }



}


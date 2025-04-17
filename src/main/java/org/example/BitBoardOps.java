package org.example;

public class BitBoardOps {

    // File masks to prevent wraparound
    private static final long FILE_A = 0x0101010101010101L;
    private static final long FILE_H = 0x8080808080808080L;

    public static long dilateOrthogonal(long bits) {
        long left  = (bits >>> 1) & ~FILE_H; // shift right, block H->G wrap
        long right = (bits << 1) & ~FILE_A  ; // shift left, block A->B wrap
        long up    = (bits << 8);
        long down  = (bits >>> 8);

        return bits | left | right | up | down;
    }

    public static long erodeOrthogonal(long bits){
        long left= (bits << 1) & ~FILE_H;
        long right = (bits >>> 1) & ~FILE_A;
        long down = bits >>> 8;
        long up = bits << 8;

        return bits & left & right & down & up;
    }

    /**
     * @return the empty neighbours of a given board
     **/
    public static long neighbourFilter(long bits){
        long dilation = dilateOrthogonal(bits); //equal to full because all pieces are on this board
        long empty = ~bits;
        return dilation & empty;
    }

    /**
     * @param bits the board
     * @param dir the direction
     * @return the empty neighbours in a given direction
     */
    public static long neighbourInDirection(long bits, Direction dir){
        switch (dir){
            case Left ->{return (bits >>> 1) & ~FILE_H & ~bits;}
            case Right -> {return (bits << 1) & ~FILE_A & ~bits;}
            case Down -> {return bits >>> 8 & ~bits;}
            case Up -> {return bits << 8 & ~bits;}
            default -> {return 0x0;}
        }
    }

    public enum Direction{
        Up, Down, Left, Right;
    }

    public static long enemyNeighboursInDirection(long player1, long player2, Direction dir){
        switch (dir){
            case Left -> {return (player1 >>> 1) & ~FILE_H & player2;}
            case Right -> {return (player1 << 1) & ~FILE_A & player2;}
            case Down -> {return player1 >>> 8 & player2;}
            case Up -> {return player1 << 8 & player2;}
            default -> {return 0x0;}
        }
    }

    public static void printBitboard(long bits) {
        System.out.println("  +------------------------+");
        for (int rank = 7; rank >= 0; rank--) {
            System.out.print((rank + 1) + " | ");
            for (int file = 0; file < 8; file++) {
                int index = rank * 8 + file;
                boolean set = ((bits >>> index) & 1) == 1;
                System.out.print(set ? "# " : ". ");
            }
            System.out.println("|");
        }
        System.out.println("  +------------------------+");
        System.out.println("    A B C D E F G H\n");
    }


    public static void main(String[] args) {
        // Example: vertical line in the center (file D)
        long input = 1L << 28 | 1L << 20 | 1L << 36 | 1L << 27 | 1L << 29; // Cross pattern

//        System.out.println("Original:");
//        printBitboard(input);

//        long dilated = dilateOrthogonal(input);
//        System.out.println("Dilated:");
//        printBitboard(dilated);
//
//
//        long eroded = erodeOrthogonal(input);
//        System.out.println("Eroded:");
//        printBitboard(eroded);
//
//        System.out.println("Neighbours:");
//        printBitboard(neighbourFilter(input));
//
//        printBitboard(neighbourInDirection(input, Direction.Up));

        long player1= 1L << 4 | 1L << 5 | 1L << 6;
        long player2= 1L << 20 | 1L << 13 | 1L << 14 | 1L << 3;
        System.out.println("Board with both Players:");
        printBitboard(player1 | player2);

        System.out.println("Enemy neighbours:");
        printBitboard(enemyNeighboursInDirection(player1, player2, Direction.Left));
    }
}


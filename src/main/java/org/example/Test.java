package org.example;

public class Test {
    public static void main(String[] args) {
        long bits =0xFFFFFFFFFFFFFFFFL;
        long FILE_A = 0x0101010101010101L;
        long FILE_H = 0x8080808080808080L;
        long test = 0x80_80_80_80_80_80_80_80L | 0x0101010101010101L & ~0x1;

        long shiftLeft = (bits << 1) & ~FILE_A;
        long shiftRight = (bits >>> 1) & ~FILE_H;
        long shiftUp = bits << 8;
        long shiftDown = bits >>> 8;

        printBitboard(bits);
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
}

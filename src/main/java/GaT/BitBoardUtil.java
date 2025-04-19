package GaT;

public interface BitBoardUtil {
    static void printBitboard(long bits) {
        System.out.println("  +------------------------+");
        for (int rank = 6; rank >= 0; rank--) {
            System.out.print((rank + 1) + " | ");
            for (int file = 0; file < 7; file++) {
                int index = rank * 7 + file;
                boolean set = ((bits >>> index) & 1) == 1;
                System.out.print(set ? "# " : ". ");
            }
            System.out.println("|");
        }
        System.out.println("  +------------------------+");
        System.out.println("    A B C D E F G \n");
    }
}

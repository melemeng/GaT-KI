package GaT;

import java.util.ArrayList;
import java.util.List;

public class Main implements BitBoardUtil {


    public static void main(String[] args) {
//        long board =133739L;
        GameState board = new GameState();
        long piece =1 | 1<<6 | 1L<<42 | 1L<< 48 ;
        int[] arr = new int[49];
        arr[24]= 2;
        int[] height= new int[7];
        height[0] = 2;
        height[6] = 2;
        GameState custom = new GameState(0L, piece, height, 0L, 0L, new int[0], true);
        custom.printBoard();
        List<Move> moves = MoveGenerator.generateAllMoves(custom);
        System.out.println(moves);

    }




}

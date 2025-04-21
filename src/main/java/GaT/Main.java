package GaT;

import java.util.ArrayList;
import java.util.List;

public class Main implements BitBoardUtil {


    public static void main(String[] args) {
//        long board =133739L;
        GameState board = new GameState();
        long towers= 1 | 2 | 1<<14;
        int[] weight = new int[49];
        weight[0] = 2;
        weight[1] = 1;
        weight[14]= 1;

        GameState state = new GameState(0, towers,weight,0L, 0L,new int[0], true);
        state.printBoard();
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        System.out.println(moves);

//        GameState state = GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r");
//        state.printBoard();

    }




}

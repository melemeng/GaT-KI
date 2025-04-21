package GaT;

import java.util.ArrayList;
import java.util.List;

import static GaT.Minimax.scoreMove;

public class Main implements BitBoardUtil {


    public static void main(String[] args) {
//        long board =133739L;
        long towers= 1 | 2 | 1<<14;
        int[] weight = new int[49];
        weight[0] = 2;
        weight[1] = 1;
        weight[14]= 1;

//        GameState state = new GameState(0, towers,weight,0L, 0L,new int[0], true);
//        GameState state = GameState.fromFen("7/7/7/7/7/r16/RGr1r34 r");
//        state.printBoard();
//        List<Move> moves = MoveGenerator.generateAllMoves(state);
//        System.out.println(moves);
//        Move toExecute = new Move(GameState.getIndex(1,0), GameState.getIndex(2,0),1);
//        state.applyMove(toExecute);
//        state.printBoard();
//        System.out.println("Executed: " + toExecute);

        GameState state = GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 b");
        List<Move> moves = MoveGenerator.generateAllMoves(state);
//        Move best = Minimax.findBestMove(state, 10); // try depth 3
//        System.out.println("Best move: " + best);

        moves.sort((a, b) -> Integer.compare(
                scoreMove(state, b),
                scoreMove(state, a)
        ));
        System.out.println("Ordered moves:");
        for (Move m : moves) {
            System.out.println("  " + m + " [score=" + scoreMove(state, m) + "]");
        }








    }




}

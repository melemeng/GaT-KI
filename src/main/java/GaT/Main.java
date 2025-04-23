package GaT;

import java.util.ArrayList;
import java.util.List;

import static GaT.GameState.getIndex;
import static GaT.Minimax.evaluate;
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

        GameState state = GameState.fromFen("7/7/BG6/RG6/6r1/7/7 b");
        state.printBoard();
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        System.out.println(moves);
        Move best = Minimax.findBestMove(state, 2); // try depth 3
        System.out.println("Best move: " + best);
        System.out.println("Evalutation: "+ evaluate(state));








    }




}

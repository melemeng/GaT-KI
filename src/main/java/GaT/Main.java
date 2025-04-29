package GaT;

import java.util.ArrayList;
import java.util.List;

import static GaT.GameState.getIndex;
import static GaT.Minimax.evaluate;
import static GaT.Minimax.scoreMove;

public class Main implements BitBoardUtil {


    public static void main(String[] args) {
        GameState state = GameState.fromFen("3RG3/7/7/7/7/3r42BG/7 r");

        state.printBoard();
        GameState copy = state.copy();
        Move best = Minimax.findBestMove(state, 9);
        copy.applyMove(best);
        System.out.println("Best move: " + best);
        System.out.println("Evaluation: "+ evaluate(copy));
        copy.printBoard();

//        Move best1 = TimedMinimax.findBestMoveWithTime(state, 99, 5000); // depth cap 99, time limit 5s
//        GameState copy1 = state.copy();
//        copy.applyMove(best1);
//        System.out.println("Best move: " + best1);
//        System.out.println("Evalutation: "+ evaluate(copy1));


    }




}

package GaT;

import GaT.Objects.GameState;
import GaT.Objects.Move;

import static GaT.Minimax.evaluate;

public class Main {


    public static void main(String[] args) {
        GameState state = GameState.fromFen("3RG3/7/7/1b25/7/6r3/5BG1 r");
//        GameState state = new GameState();

//        state.printBoard();
//        GameState copy = state.copy();
//
//        long startTime = System.currentTimeMillis();
//        Move best = Minimax.findBestMove(state, 5);
//        long endTime = System.currentTimeMillis();
//        copy.applyMove(best);
//        System.out.println("Best move: " + best);
//        System.out.println("Evaluation: "+ evaluate(copy, 0));
//        copy.printBoard();
//        System.out.println("Time taken: "+ (endTime -startTime) + "ms");
//        System.out.println(MoveGenerator.generateAllMoves(state).size());

        TimeManager timeManager = new TimeManager(180*1000, 55);
        System.out.println(timeManager.calculateTimeForMove(state));







    }





}

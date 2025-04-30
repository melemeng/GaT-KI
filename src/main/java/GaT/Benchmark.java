package GaT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class Benchmark {
    public static void main(String[] args) {
        List<GameState> states = Arrays.asList(getStart(), getMid(), getEnd());

       for (GameState state : states){
           List<Long> benchmarks = new ArrayList<>();
           for (int i=0; i< 30; i++){

               long startTime = System.currentTimeMillis();

               for (int j = 0; j < 100_000; j++) {
                   MoveGenerator.generateAllMoves(state);
               }

               long endTime = System.currentTimeMillis();
               long duration = endTime - startTime;
               benchmarks.add(duration);
           }

           long avg  = benchmarks.stream().reduce(0L, (x,y)-> x+y) / benchmarks.size();
           float avgSeconds = (float) avg / 1000;
           System.out.println("Raw Values: " + benchmarks);
           System.out.println("Average Execution Time: " + avg + " milliseconds");
           System.out.println("Average Execution Time: " + avgSeconds + " seconds");
           System.out.println("Number of Moves: " + MoveGenerator.generateAllMoves(state).size()+ "\n");
       }
    }

    private static GameState getStart(){
        return GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r");
    }

    private static GameState getMid(){
        return GameState.fromFen("r1r11RG3/6r1/3r11r21/7/3b23/1b15/b12BG1b1b1 b");
    }

    private static GameState getEnd(){
        return GameState.fromFen("3RG3/3r33/3b33/7/7/7/3BG3 r");
    }
}

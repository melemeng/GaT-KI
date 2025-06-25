package GaT.Benchmark;

import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.TimedMinimax;

import java.util.Arrays;
import java.util.List;

public class AITechBenchmark {
    public static void main(String[] args) {
        List<StateType> types = Arrays.stream(StateType.values()).toList();
        final long runTime = 2000; //2s

        for (StateType type : types){
            GameState state = getState(type);
            System.out.println("################################# State-Type: "+ type + " #########################################");
            Move bestMove = TimedMinimax.findBestMoveUltimate(state, 99, runTime);
            if (type != StateType.TACTICAL){
                System.out.println();
                System.out.println("################################# Switching StateType #########################################");
                System.out.println();
            }else{
                System.out.println();
                System.out.println("################################# Finished #########################################");
                System.out.println();
            }
        }

    }

    enum StateType{
        START,
        MID,
        END,
        TACTICAL
    }

    private static GameState getState(StateType type){
        switch (type){
            case START ->{ return new GameState();}
            case MID -> { return GameState.fromFen("3RG3/r14r11/2r34/7/7/2b12b11/1b11BG3 r");}
            case END -> { return GameState.fromFen("1r23r21/2RGBG3/7/7/7/7/7 b");}
            case TACTICAL -> { return GameState.fromFen("7/3RG3/7/2b24/7/6r3/5BG1 r");}
            default -> {return null;}
        }
    }

}

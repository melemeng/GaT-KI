package GaT;

import GaT.Objects.GameState;
import GaT.Objects.Move;

import static GaT.Minimax.evaluate;

public class Main {

    public static void main(String[] args) {
        GameState state = GameState.fromFen("7/7/7/BG6/3b33/3RG3/7 r");

        state.printBoard();
        GameState copy = state.copy();

        // Test 1: Alpha-Beta (your original)
        long startTime = System.currentTimeMillis();
        Move best = Minimax.findBestMove(state, 5);
        long endTime = System.currentTimeMillis();
        copy.applyMove(best);
        System.out.println("Alpha-Beta: " + best);
        System.out.println("Evaluation: "+ evaluate(copy, 0));
        copy.printBoard();
        System.out.println("Time taken: "+ (endTime -startTime) + "ms");
        System.out.println("Legal moves: " + MoveGenerator.generateAllMoves(state).size());

        // Test 2: Alpha-Beta + Quiescence
        System.out.println("\n=== With Quiescence ===");
        Minimax.counter = 0;
        QuiescenceSearch.resetQuiescenceStats();

        startTime = System.currentTimeMillis();
        Move bestQ = Minimax.findBestMoveWithQuiescence(state, 5);
        endTime = System.currentTimeMillis();

        GameState copyQ = state.copy();
        copyQ.applyMove(bestQ);
        System.out.println("Alpha-Beta + Q: " + bestQ);
        System.out.println("Evaluation: "+ evaluate(copyQ, 0));
        System.out.println("Time taken: "+ (endTime - startTime) + "ms");
        System.out.println("Regular nodes: " + Minimax.counter);
        System.out.println("Q-nodes: " + QuiescenceSearch.qNodes);
        System.out.println("Moves different: " + !best.equals(bestQ));

        // Test 3: PVS (new)
        System.out.println("\n=== With PVS ===");
        Minimax.counter = 0;

        startTime = System.currentTimeMillis();
        Move bestPVS = Minimax.findBestMoveWithPVS(state, 5);
        endTime = System.currentTimeMillis();

        GameState copyPVS = state.copy();
        copyPVS.applyMove(bestPVS);
        System.out.println("PVS: " + bestPVS);
        System.out.println("Evaluation: "+ evaluate(copyPVS, 0));
        System.out.println("Time taken: "+ (endTime - startTime) + "ms");
        System.out.println("Regular nodes: " + Minimax.counter);

        // Test 4: Ultimate AI (PVS + Quiescence) - your strongest
        System.out.println("\n=== ULTIMATE AI (PVS + Q) ===");
        Minimax.counter = 0;
        QuiescenceSearch.resetQuiescenceStats();

        startTime = System.currentTimeMillis();
        Move bestUltimate = Minimax.findBestMoveUltimate(state, 5);
        endTime = System.currentTimeMillis();

        GameState copyUltimate = state.copy();
        copyUltimate.applyMove(bestUltimate);
        System.out.println("ULTIMATE: " + bestUltimate);
        System.out.println("Evaluation: "+ evaluate(copyUltimate, 0));
        System.out.println("Time taken: "+ (endTime - startTime) + "ms");
        System.out.println("Regular nodes: " + Minimax.counter);
        System.out.println("Q-nodes: " + QuiescenceSearch.qNodes);

        // Quick comparison
        System.out.println("\n=== QUICK COMPARISON ===");
        System.out.println("Alpha-Beta:     " + best + " (eval: " + evaluate(copy, 0) + ")");
        System.out.println("Alpha-Beta + Q: " + bestQ + " (eval: " + evaluate(copyQ, 0) + ")");
        System.out.println("PVS:            " + bestPVS + " (eval: " + evaluate(copyPVS, 0) + ")");
        System.out.println("ULTIMATE:       " + bestUltimate + " (eval: " + evaluate(copyUltimate, 0) + ")");

        // Test tactical position where quiescence should activate
        System.out.println("\n=== Tactical Position Test ===");
        GameState tactical = GameState.fromFen("7/7/3b33/BG1r43/3RG3/7/7 r");
        tactical.printBoard();

        Minimax.counter = 0;
        QuiescenceSearch.resetQuiescenceStats();

        startTime = System.currentTimeMillis();
        Move tacticalMove = Minimax.findBestMoveUltimate(tactical, 4);
        endTime = System.currentTimeMillis();

        System.out.println("ULTIMATE tactical: " + tacticalMove);
        System.out.println("Time: " + (endTime - startTime) + "ms");
        System.out.println("Regular nodes: " + Minimax.counter);
        System.out.println("Q-nodes: " + QuiescenceSearch.qNodes);

        if (QuiescenceSearch.qNodes > 0) {
            System.out.println("✅ Quiescence activated on tactical position");
        } else {
            System.out.println("⚠️ Quiescence not activated");
        }
    }
}
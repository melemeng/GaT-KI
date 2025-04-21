import GaT.GameState;
import GaT.Move;
import GaT.MoveGenerator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;


public class MoveGeneratorUnitTests {

    @Test
    public void testInitialWhiteMovesCount() {
        GameState state = new GameState();
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        assertFalse("Expected some legal moves for white in initial position", moves.isEmpty());
    }

    @Test
    public void testWhiteGuardMoves() {
        GameState state = new GameState();
        state.redToMove = false;
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        Move D1toD2 = new Move(GameState.getIndex(0, 3), GameState.getIndex(1, 3),1);
        Move D1toC1 = new Move(GameState.getIndex(0, 3), GameState.getIndex(0, 2),1);
        Move D1toE1 = new Move(GameState.getIndex(0, 3), GameState.getIndex(0, 4),1);

        List<Move> expectedMoves = new ArrayList<>(Arrays.asList(D1toD2, D1toC1, D1toE1));

        assertTrue("White guard should make these moves", moves.containsAll(expectedMoves));

        int count = 0;
        for (Move m: moves){
            if(m.from == GameState.getIndex(0,3)){
                count++;
            }
        }
        assertEquals("Guard should not be able to do more than 3 moves from this position:", 3, count);
    }

    @Test
    public void testTowerMoves() {
        long tower= GameState.bit(GameState.getIndex(3,3)); //tower on D4
        int [] height= new int[49];
        height[GameState.getIndex(3,3)]= 1;

        //New state with only the tower
        GameState state = new GameState(0, tower, height,0,0,new int[0], true);
        List<Move> movesH1= MoveGenerator.generateAllMoves(state);
        Move D4toD5 = new Move(GameState.getIndex(3, 3), GameState.getIndex(4,3),1);
        Move D4toD3 = new Move(GameState.getIndex(3, 3), GameState.getIndex(2,3),1);
        Move D4toC4 = new Move(GameState.getIndex(3, 3), GameState.getIndex(3,2),1);
        Move D4toE4 = new Move(GameState.getIndex(3, 3), GameState.getIndex(3,4),1);

        List<Move> expectedMoves= new ArrayList<>(Arrays.asList(D4toC4, D4toD3, D4toD5, D4toE4));

        assertTrue("With height of 1 tower should move in all four directions", movesH1.containsAll(expectedMoves));

        //Change tower height to 2
        state.redStackHeights[24]= 2;
        List<Move> movesH2 = MoveGenerator.generateAllMoves(state);
        Move D4toD6 = new Move(GameState.getIndex(3, 3), GameState.getIndex(5,3),2);
        Move D4toD1 = new Move(GameState.getIndex(3, 3), GameState.getIndex(1,3),2);
        Move D4toB4 = new Move(GameState.getIndex(3, 3), GameState.getIndex(3,1),2);
        Move D4toF4 = new Move(GameState.getIndex(3, 3), GameState.getIndex(3,5),2);

        expectedMoves.addAll(Arrays.asList(D4toB4,D4toD1, D4toD6, D4toF4));

        assertTrue("With height of 2 tower should move in all four directions by 2", movesH2.containsAll(expectedMoves));
    }

    @Test
    public void testBoardBoundaries() {
        long towers= 1 | 1<<6 | 1L<<42 | 1L<< 48;
        int[] height= new int[49];
        height[0] = 2;
        height[6] = 2;
        height[42]= 2;
        height[48] = 2;

        GameState state = new GameState(0, towers, height, 0, 0, new int[1],true);
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        assertEquals("Should only be able to move inside boundaries", 16, moves.size());
    }

    @Test
    public void testPiecesCantBeJumpedOver(){
        long wTowers = 1 | 1<<7;
        long bTowers = 2;

        int[] wHeight= new int[8];
        wHeight[0]= 2;
        wHeight[7]= 2;
        int[] bHeight= new int[2];
        bHeight[1]= 1;

        GameState state = new GameState(0L, wTowers,wHeight,0L, bTowers,bHeight, true);
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        Move A1toA3= new Move(GameState.getIndex(0,0), GameState.getIndex(2,0),2);
        Move A1toC1= new Move(GameState.getIndex(0,0), GameState.getIndex(0,2),2);

        List<Move> notExpected = new ArrayList<>(Arrays.asList(A1toA3, A1toC1));

        assertFalse("Tower should not be able to jump own or enemy pieces", notExpected.stream().anyMatch(x->moves.contains(x)));
    }

    @Test
    public void testTowerStacking(){
        long towers= 1 | 2 | 1<<14;
        int[] weight = new int[49];
        weight[0] = 2;
        weight[1] = 1;
        weight[14]= 1;
        GameState state = new GameState(0, towers,weight,0L, 0L,new int[0], true);
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        Move stack1 = new Move(0, GameState.getIndex(2,0), 2);
        Move stack2= new Move(0, GameState.getIndex(0,1),1);
        List<Move> expectedMoves= Arrays.asList(stack1,stack2);

        assertTrue("Pieces should stack correctly according to the path length", moves.containsAll(expectedMoves));
    }

}

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
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        int guardIndex = GameState.getIndex(0, 3); // D1
        int expectedTarget = GameState.getIndex(1, 3); // D2

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
        List<Move> expectedMoves= MoveGenerator.generateAllMoves(state);
        Move D4toD5 = new Move(GameState.getIndex(3, 3), GameState.getIndex(4,3),1);
        Move D4toD3 = new Move(GameState.getIndex(3, 3), GameState.getIndex(2,3),1);
        Move D4toC4 = new Move(GameState.getIndex(3, 3), GameState.getIndex(3,2),1);
        Move D4toE4 = new Move(GameState.getIndex(3, 3), GameState.getIndex(3,4),1);
        List<Move> height1Moves= new ArrayList<>(Arrays.asList(D4toC4, D4toD3, D4toD5, D4toE4));

        assertTrue("With height of 1 tower should move in all four directions", movesH1.containsAll(expectedMoves));

        //Change tower height to 2
        state.whiteStackHeights[24]= 2;
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

    }

}

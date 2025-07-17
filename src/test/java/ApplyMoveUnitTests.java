import GaT.game.GameState;
import GaT.game.Move;
import org.junit.Test;
import static org.junit.Assert.*;

public class ApplyMoveUnitTests {

    @Test
    public void testMoveFullLength(){
        GameState state = GameState.fromFen("7/7/7/7/7/r16/RGr1r34 r");
        Move toExecute = new Move(GameState.getIndex(1,0), GameState.getIndex(2,0),1);
        state.applyMove(toExecute);

        boolean hasMovedCorrect = (state.redTowers & GameState.bit(toExecute.to)) != 0;
        assertTrue("Piece should be at the updated position", hasMovedCorrect);

        boolean oldSpotCleared = (state.redTowers & GameState.bit(toExecute.from)) == 0;
        assertTrue("Tower should give up his spot", oldSpotCleared);
    }

    @Test
    public void testMoveSplit(){
        GameState state = GameState.fromFen("7/7/7/7/7/r16/RGr1r34 r");
        Move toExecute = new Move(GameState.getIndex(0,2), GameState.getIndex(1,2),1);
        state.applyMove(toExecute);

        boolean hasMovedCorrect = (state.redTowers & GameState.bit(toExecute.to)) != 0;
        assertTrue("Piece should be at the updated position", hasMovedCorrect);

        boolean oldSpotCleared = (state.redTowers & GameState.bit(toExecute.from)) != 0;
        assertTrue("Tower should not give up his spot", oldSpotCleared);

        assertEquals("Tower height should be split at destination",2, state.redStackHeights[toExecute.from]);
        assertEquals("Tower height should be split at start",1, state.redStackHeights[toExecute.to]);
    }

    @Test
    public void testStacking(){
        GameState state = GameState.fromFen("7/7/7/7/7/r16/RGr1r34 r");
        Move toExecute = new Move(GameState.getIndex(0,1), GameState.getIndex(0,2),1);
        state.applyMove(toExecute);

        assertEquals("Tower should stack on top of the other", 4, state.redStackHeights[toExecute.to]);
    }

    @Test
    public void testSplitting(){
        GameState state = GameState.fromFen("7/7/7/7/7/r16/RGr1r34 r");
        Move toExecute = new Move(GameState.getIndex(0,2), GameState.getIndex(2,2),2);
        state.applyMove(toExecute);
        assertEquals("Tower should only move the correct amount", 2, state.redStackHeights[toExecute.to]);
    }

}

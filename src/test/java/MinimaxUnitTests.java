import GaT.GameState;
import GaT.Minimax;
import org.junit.Test;
import static org.junit.Assert.*;

public class MinimaxUnitTests {

    @Test
    public void testIsGameOver(){
        GameState state1 = GameState.fromFen("3RG3/7/7/7/7/7/7 r");
        assertTrue("Only one Guard left, so the game should be over", Minimax.isGameOver(state1));

        GameState state2 = GameState.fromFen("BG6/7/7/7/7/7/3RG3 r");
        assertTrue("Red guard on the midfield so game should be over", Minimax.isGameOver(state2));

        GameState state3 = GameState.fromFen("3b53/BG6/7/7/7/7/RG6 r");
        assertFalse("Game should not be over because only the tower is on the midfield", Minimax.isGameOver(state3));
    }
}

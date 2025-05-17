import GaT.Objects.GameState;
import org.junit.Test;
import static org.junit.Assert.*;

public class FenConverterUnitTests {

    @Test
    public void testCompareToStandardLayout(){
        GameState expected = new GameState();
        GameState fenConversion = GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r");

        assertEquals("Gamestate should be equal to the standard layout", expected, fenConversion);
    }

    @Test
    public void testEmptyBoard(){
        String fen= "7/7/7/7/7/7/7 r";
        GameState state = GameState.fromFen(fen);
        assertEquals("There should be no Guard or Towers", 0, state.redGuard + state.redTowers + state.blueGuard + state.blueTowers);
    }

    @Test
    public void testTowerParsing(){
        GameState parsed = GameState.fromFen("7/7/7/3r3/7/7/7 r");
        int center = GameState.getIndex(3, 3);
        assertEquals("Expected red tower at D4", 1, ((parsed.redTowers >>> center) & 1));       //Expected moves the piece from the center to index 1 and checks if the bit is on
        assertEquals("Expected height 3", 3, parsed.redStackHeights[center]);
    }

    @Test
    public void testTurnParsing() {
        GameState parsedB = GameState.fromFen("7/7/7/7/7/7/7 b");
        assertFalse("Expected blue to move", parsedB.redToMove);

        GameState parsedR = GameState.fromFen("7/7/7/7/7/7/7 r");
        assertTrue("Expected red to move", parsedR.redToMove);
    }


}

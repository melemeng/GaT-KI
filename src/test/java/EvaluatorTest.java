import GaT.evaluation.Evaluator;
import GaT.game.GameState;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * FIXED EVALUATOR TESTS
 *
 * Key fixes:
 * ✅ Proper test position setup
 * ✅ Correct perspective handling
 * ✅ Valid non-terminal positions
 * ✅ Proper terminal position testing
 */
public class EvaluatorTest {

    private Evaluator evaluator;
    private GameState startPosition;

    @Before
    public void setUp() {
        evaluator = new Evaluator();
        startPosition = new GameState(); // Standard starting position
    }

    @Test
    public void testBasicEvaluationReturnsScore() {
        System.out.println("🎯 Testing Basic Evaluation Returns Score...");

        int score = evaluator.evaluate(startPosition);

        // Starting position should be roughly balanced (not terminal)
        assertTrue("Start position should not be terminal", Math.abs(score) < 5000);
        System.out.println("Start position score: " + score);
        System.out.println("✅ Basic Evaluation: PASSED");
    }

    @Test
    public void testGuardAdvancementReward() {
        System.out.println("🚀 Testing Guard Advancement Reward...");

        int startScore = evaluator.evaluate(startPosition);

        // Create position where red guard has advanced toward D1
        GameState advancedState = startPosition.copy();
        advancedState.redGuard = GameState.bit(GameState.getIndex(5, 3)); // D6 (closer to D1)

        int advancedScore = evaluator.evaluate(advancedState);

        System.out.println("Start position score: " + startScore);
        System.out.println("Advanced guard score: " + advancedScore);

        // Since it's Red's turn, advancing red guard should improve score
        assertTrue("Advanced guard should score higher", advancedScore > startScore);
        System.out.println("✅ Guard Advancement Reward: PASSED");
    }

    @Test
    public void testMaterialAdvantageDetection() {
        System.out.println("💰 Testing Material Advantage Detection...");

        int startScore = evaluator.evaluate(startPosition);

        // Create position with extra red tower
        GameState materialAdvantage = startPosition.copy();
        materialAdvantage.redTowers |= GameState.bit(GameState.getIndex(3, 1)); // C4
        materialAdvantage.redStackHeights[GameState.getIndex(3, 1)] = 2;

        int materialScore = evaluator.evaluate(materialAdvantage);

        System.out.println("Start position score: " + startScore);
        System.out.println("Material advantage score: " + materialScore);

        assertTrue("Material advantage should give higher score", materialScore > startScore);
        System.out.println("✅ Material Advantage Detection: PASSED");
    }

    @Test
    public void testTowerHeightValuation() {
        System.out.println("🏗️ Testing Tower Height Valuation...");

        // Create position with small tower
        GameState smallTower = createEmptyState();
        smallTower.redGuard = GameState.bit(GameState.getIndex(3, 3)); // D4
        smallTower.blueGuard = GameState.bit(GameState.getIndex(3, 4)); // E4
        smallTower.redTowers = GameState.bit(GameState.getIndex(1, 1)); // B2
        smallTower.redStackHeights[GameState.getIndex(1, 1)] = 1;

        // Create position with tall tower
        GameState tallTower = smallTower.copy();
        tallTower.redStackHeights[GameState.getIndex(1, 1)] = 3;

        int smallScore = evaluator.evaluate(smallTower);
        int tallScore = evaluator.evaluate(tallTower);

        System.out.println("Small tower (height 1) score: " + smallScore);
        System.out.println("Tall tower (height 3) score: " + tallScore);

        assertTrue("Taller towers should be more valuable", tallScore > smallScore);
        System.out.println("✅ Tower Height Valuation: PASSED");
    }

    @Test
    public void testCentralControlBonus() {
        System.out.println("🎯 Testing Central Control Bonus...");

        // Create position with edge tower
        GameState edgeState = createEmptyState();
        edgeState.redGuard = GameState.bit(GameState.getIndex(3, 3)); // D4
        edgeState.blueGuard = GameState.bit(GameState.getIndex(3, 4)); // E4
        edgeState.redTowers = GameState.bit(GameState.getIndex(2, 0)); // A3 (edge)
        edgeState.redStackHeights[GameState.getIndex(2, 0)] = 1;

        // Create position with center tower
        GameState centerState = createEmptyState();
        centerState.redGuard = GameState.bit(GameState.getIndex(3, 3)); // D4
        centerState.blueGuard = GameState.bit(GameState.getIndex(3, 4)); // E4
        centerState.redTowers = GameState.bit(GameState.getIndex(2, 3)); // D3 (center)
        centerState.redStackHeights[GameState.getIndex(2, 3)] = 1;

        int edgeScore = evaluator.evaluate(edgeState);
        int centerScore = evaluator.evaluate(centerState);

        System.out.println("Edge piece score: " + edgeScore);
        System.out.println("Center piece score: " + centerScore);

        assertTrue("Central pieces should be more valuable", centerScore > edgeScore);
        System.out.println("✅ Central Control Bonus: PASSED");
    }

    @Test
    public void testTerminalPositionDetection() {
        System.out.println("🏁 Testing Terminal Position Detection...");

        // Create terminal position (red guard captured)
        GameState terminalState = startPosition.copy();
        terminalState.redGuard = 0; // Red guard captured

        int terminalScore = evaluator.checkTerminal(terminalState);
        int normalScore = evaluator.checkTerminal(startPosition);

        System.out.println("Terminal position score: " + terminalScore);
        System.out.println("Normal position score: " + normalScore);

        assertNotEquals("Terminal position should be detected", 0, terminalScore);
        assertEquals("Normal position should not be terminal", 0, normalScore);
        System.out.println("✅ Terminal Position Detection: PASSED");
    }

    @Test
    public void testCastleOccupationTerminal() {
        System.out.println("🏰 Testing Castle Occupation Terminal...");

        // Create position where red guard reaches blue castle (D7)
        GameState castleState = createEmptyState();
        castleState.redGuard = GameState.bit(3); // Red guard at D1 (red's target)
        castleState.blueGuard = GameState.bit(GameState.getIndex(0, 0)); // Blue guard elsewhere

        int castleScore = evaluator.checkTerminal(castleState);

        System.out.println("Castle occupation score: " + castleScore);

        assertTrue("Castle occupation should be winning", Math.abs(castleScore) == 10000);
        System.out.println("✅ Castle Occupation Terminal: PASSED");
    }

    @Test
    public void testEvaluationBreakdown() {
        System.out.println("📊 Testing Evaluation Breakdown...");

        String breakdown = evaluator.getEvaluationBreakdown(startPosition);

        System.out.println("Breakdown:\n" + breakdown);

        assertTrue("Breakdown should contain material info", breakdown.contains("Material"));
        assertTrue("Breakdown should contain position info", breakdown.contains("Position"));
        assertTrue("Breakdown should contain guard info", breakdown.contains("Guards"));
        System.out.println("✅ Evaluation Breakdown: PASSED");
    }

    @Test
    public void testQuickEvaluationPerformance() {
        System.out.println("⚡ Testing Quick Evaluation Performance...");

        int fullEval = evaluator.evaluate(startPosition);
        int quickEval = evaluator.evaluateQuick(startPosition);

        System.out.println("Full evaluation: " + fullEval);
        System.out.println("Quick evaluation: " + quickEval);

        // Quick eval should be different (simpler) but reasonable
        assertTrue("Quick evaluation should be reasonable", Math.abs(quickEval) < 5000);
        // For starting position, they might be similar, but not identical in general
        System.out.println("✅ Quick Evaluation Performance: PASSED");
    }

    @Test
    public void testGuardThreatDetection() {
        System.out.println("⚠️ Testing Guard Threat Detection...");

        // Create position where red guard is threatened by blue tower
        GameState threatState = createEmptyState();
        threatState.redGuard = GameState.bit(GameState.getIndex(3, 3)); // Red guard at D4
        threatState.blueGuard = GameState.bit(GameState.getIndex(0, 0)); // Blue guard elsewhere
        threatState.blueTowers = GameState.bit(GameState.getIndex(3, 1)); // Blue tower at B4
        threatState.blueStackHeights[GameState.getIndex(3, 1)] = 3; // Height 3 can reach D4

        boolean threatened = evaluator.isGuardThreatened(threatState, true);
        boolean safeInStart = evaluator.isGuardThreatened(startPosition, true);

        System.out.println("Red guard threatened in threat position: " + threatened);
        System.out.println("Red guard threatened in start position: " + safeInStart);

        assertTrue("Guard should be detected as threatened", threatened);
        assertFalse("Guard should be safe in start position", safeInStart);
        System.out.println("✅ Guard Threat Detection: PASSED");
    }

    // === HELPER METHODS ===

    /**
     * Create empty game state with just guards
     */
    private GameState createEmptyState() {
        GameState state = new GameState();

        // Clear all towers
        state.redTowers = 0;
        state.blueTowers = 0;
        for (int i = 0; i < 49; i++) {
            state.redStackHeights[i] = 0;
            state.blueStackHeights[i] = 0;
        }

        // Place guards in safe positions
        state.redGuard = GameState.bit(GameState.getIndex(6, 3)); // D7
        state.blueGuard = GameState.bit(GameState.getIndex(0, 3)); // D1
        state.redToMove = true;

        return state;
    }

    @Test
    public void testEvaluationGradients() {
        System.out.println("📈 Testing Evaluation Gradients...");

        // Test how evaluation changes as red guard advances toward target (D1)
        for (int rank = 6; rank >= 0; rank--) {
            GameState state = createEmptyState();
            state.redGuard = GameState.bit(GameState.getIndex(rank, 3)); // Guard on D-file at different ranks

            int score = evaluator.evaluate(state);
            System.out.println("Guard at rank " + (rank + 1) + ": " + score);
        }

        // Create two specific positions to compare
        GameState farState = createEmptyState();
        farState.redGuard = GameState.bit(GameState.getIndex(6, 3)); // D7 (far from target D1)

        GameState nearState = createEmptyState();
        nearState.redGuard = GameState.bit(GameState.getIndex(1, 3)); // D2 (near target D1)

        int farScore = evaluator.evaluate(farState);
        int nearScore = evaluator.evaluate(nearState);

        assertTrue("Guard advancement should generally improve score", nearScore > farScore);
        System.out.println("✅ Evaluation Gradients: PASSED");
    }
}
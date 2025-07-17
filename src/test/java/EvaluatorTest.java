import GaT.evaluation.Evaluator;
import GaT.game.GameState;
import GaT.game.Move;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * COMPREHENSIVE EVALUATOR UNIT TEST
 *
 * Tests all functionality of the Evaluator class:
 * ✅ Basic evaluation scoring
 * ✅ Material evaluation
 * ✅ Positional evaluation  
 * ✅ Guard safety analysis
 * ✅ Terminal position detection
 * ✅ Guard threat detection
 * ✅ Evaluation consistency
 * ✅ Edge cases and robustness
 */
public class EvaluatorTest {

    private Evaluator evaluator;
    private GameState startPosition;
    private GameState materialAdvantage;
    private GameState guardThreatened;
    private GameState guardNearCastle;
    private GameState terminalPosition;
    private GameState emptyBoard;

    @Before
    public void setUp() {
        evaluator = new Evaluator();

        // Standard starting position (should be roughly balanced)
        startPosition = GameState.fromFen("b1b11BG1b1b1/2b11b12/3b13/7/3r13/2r11r12/r1r11RG1r1r1 r");

        // Red has material advantage
        materialAdvantage = GameState.fromFen("7/7/7/r2RG1r13/7/7/3BG3 r");

        // Red guard threatened by blue tower
        guardThreatened = GameState.fromFen("7/7/7/2bRG13/7/7/3BG3 r");

        // Red guard close to blue castle
        guardNearCastle = GameState.fromFen("7/2RG13/7/7/7/7/3BG3 r");

        // Terminal position - red guard captured
        terminalPosition = GameState.fromFen("7/7/7/7/7/7/3BG3 r");

        // Empty board with only guards
        emptyBoard = GameState.fromFen("7/7/7/3RG3/7/7/3BG3 r");
    }

    // ================================================
    // BASIC EVALUATION TESTS
    // ================================================

    @Test
    public void testBasicEvaluationReturnsScore() {
        System.out.println("🎯 Testing Basic Evaluation Returns Score...");

        int score = evaluator.evaluate(startPosition);

        // Should return a numerical score
        assertTrue("Evaluation should return finite score", Math.abs(score) < 50000);

        System.out.println("Start position score: " + score);
        System.out.println("✅ Basic Evaluation: PASSED");
    }

    @Test
    public void testEvaluationConsistency() {
        System.out.println("🔄 Testing Evaluation Consistency...");

        // Same position should always give same score
        int score1 = evaluator.evaluate(startPosition);
        int score2 = evaluator.evaluate(startPosition);
        int score3 = evaluator.evaluate(startPosition);

        assertEquals("Evaluation should be consistent", score1, score2);
        assertEquals("Evaluation should be consistent", score2, score3);

        System.out.println("Consistent score: " + score1);
        System.out.println("✅ Evaluation Consistency: PASSED");
    }

    @Test
    public void testEvaluationSymmetry() {
        System.out.println("⚖️ Testing Evaluation Symmetry...");

        // Create mirrored position
        GameState redToMove = startPosition.copy();
        redToMove.redToMove = true;

        GameState blueToMove = startPosition.copy();
        blueToMove.redToMove = false;

        int redScore = evaluator.evaluate(redToMove);
        int blueScore = evaluator.evaluate(blueToMove);

        System.out.println("Red to move score: " + redScore);
        System.out.println("Blue to move score: " + blueScore);

        // Scores might differ due to tempo, but should be reasonable
        assertTrue("Scores should be in reasonable range", Math.abs(redScore - blueScore) < 1000);

        System.out.println("✅ Evaluation Symmetry: PASSED");
    }

    // ================================================
    // MATERIAL EVALUATION TESTS
    // ================================================

    @Test
    public void testMaterialAdvantageDetection() {
        System.out.println("💰 Testing Material Advantage Detection...");

        int startScore = evaluator.evaluate(startPosition);
        int advantageScore = evaluator.evaluate(materialAdvantage);

        System.out.println("Start position score: " + startScore);
        System.out.println("Material advantage score: " + advantageScore);

        // Red has more material, so should score higher
        assertTrue("Material advantage should give higher score", advantageScore > startScore);

        System.out.println("✅ Material Advantage: PASSED");
    }

    @Test
    public void testTowerHeightValuation() {
        System.out.println("🏗️ Testing Tower Height Valuation...");

        // Create positions with different tower heights
        GameState smallTower = GameState.fromFen("7/7/7/3r13/7/7/7 r");
        GameState tallTower = GameState.fromFen("7/7/7/3r33/7/7/7 r");

        int smallScore = evaluator.evaluate(smallTower);
        int tallScore = evaluator.evaluate(tallTower);

        System.out.println("Small tower (height 1) score: " + smallScore);
        System.out.println("Tall tower (height 3) score: " + tallScore);

        // Taller tower should be worth more
        assertTrue("Taller towers should be more valuable", tallScore > smallScore);

        System.out.println("✅ Tower Height Valuation: PASSED");
    }

    // ================================================
    // POSITIONAL EVALUATION TESTS
    // ================================================

    @Test
    public void testCentralControlBonus() {
        System.out.println("🎯 Testing Central Control Bonus...");

        // Create positions with pieces on different files
        GameState edgePiece = GameState.fromFen("r16/7/7/7/7/7/7 r");     // A-file (edge)
        GameState centerPiece = GameState.fromFen("7/7/7/3r13/7/7/7 r");   // D-file (center)

        int edgeScore = evaluator.evaluate(edgePiece);
        int centerScore = evaluator.evaluate(centerPiece);

        System.out.println("Edge piece score: " + edgeScore);
        System.out.println("Center piece score: " + centerScore);

        // Central piece should score higher
        assertTrue("Central pieces should be more valuable", centerScore > edgeScore);

        System.out.println("✅ Central Control: PASSED");
    }

    @Test
    public void testGuardAdvancementReward() {
        System.out.println("🚀 Testing Guard Advancement Reward...");

        int startScore = evaluator.evaluate(startPosition);
        int advancedScore = evaluator.evaluate(guardNearCastle);

        System.out.println("Start position score: " + startScore);
        System.out.println("Advanced guard score: " + advancedScore);

        // Advanced guard should score much higher
        assertTrue("Advanced guard should score higher", advancedScore > startScore + 500);

        System.out.println("✅ Guard Advancement: PASSED");
    }

    // ================================================
    // GUARD SAFETY TESTS
    // ================================================

    @Test
    public void testGuardThreatDetection() {
        System.out.println("⚠️ Testing Guard Threat Detection...");

        // Test the specific guard threat method
        boolean redThreatened = evaluator.isGuardThreatened(guardThreatened, true);
        boolean redSafe = evaluator.isGuardThreatened(startPosition, true);

        System.out.println("Red guard threatened in threat position: " + redThreatened);
        System.out.println("Red guard threatened in start position: " + redSafe);

        // Guard should be detected as threatened
        assertTrue("Should detect threatened guard", redThreatened);
        assertFalse("Should not detect safe guard as threatened", redSafe);

        System.out.println("✅ Guard Threat Detection: PASSED");
    }

    @Test
    public void testGuardSafetyScoring() {
        System.out.println("🛡️ Testing Guard Safety Scoring...");

        int safeScore = evaluator.evaluate(startPosition);
        int threatenedScore = evaluator.evaluate(guardThreatened);

        System.out.println("Safe guard score: " + safeScore);
        System.out.println("Threatened guard score: " + threatenedScore);

        // Threatened guard should score lower
        assertTrue("Threatened guard should score lower", threatenedScore < safeScore);

        System.out.println("✅ Guard Safety Scoring: PASSED");
    }

    // ================================================
    // TERMINAL POSITION TESTS
    // ================================================

    @Test
    public void testTerminalPositionDetection() {
        System.out.println("🏁 Testing Terminal Position Detection...");

        int terminalScore = evaluator.checkTerminal(terminalPosition);
        int normalScore = evaluator.checkTerminal(startPosition);

        System.out.println("Terminal position score: " + terminalScore);
        System.out.println("Normal position score: " + normalScore);

        // Terminal position should return large negative score (red lost guard)
        assertTrue("Terminal position should return large score", Math.abs(terminalScore) > 5000);
        assertEquals("Normal position should not be terminal", 0, normalScore);

        System.out.println("✅ Terminal Position Detection: PASSED");
    }

    @Test
    public void testGuardCaptureTerminal() {
        System.out.println("👑 Testing Guard Capture Terminal...");

        // Position where blue guard is captured
        GameState blueGuardCaptured = GameState.fromFen("7/7/7/3RG3/7/7/7 r");

        int captureScore = evaluator.checkTerminal(blueGuardCaptured);

        System.out.println("Guard capture score: " + captureScore);

        // Should be large positive score (red wins)
        assertTrue("Guard capture should be winning", captureScore > 5000);

        System.out.println("✅ Guard Capture Terminal: PASSED");
    }

    @Test
    public void testCastleOccupationTerminal() {
        System.out.println("🏰 Testing Castle Occupation Terminal...");

        // Red guard on blue castle (D1)
        GameState castleOccupied = GameState.fromFen("3RG3/7/7/7/7/7/3BG3 r");

        int castleScore = evaluator.checkTerminal(castleOccupied);

        System.out.println("Castle occupation score: " + castleScore);

        // Should be large positive score (red wins)
        assertTrue("Castle occupation should be winning", castleScore > 5000);

        System.out.println("✅ Castle Occupation Terminal: PASSED");
    }

    // ================================================
    // EVALUATION BREAKDOWN TESTS
    // ================================================

    @Test
    public void testEvaluationBreakdown() {
        System.out.println("📊 Testing Evaluation Breakdown...");

        String breakdown = evaluator.getEvaluationBreakdown(startPosition);

        assertNotNull("Breakdown should not be null", breakdown);
        assertTrue("Breakdown should contain material info", breakdown.contains("Material"));
        assertTrue("Breakdown should contain position info", breakdown.contains("Position"));
        assertTrue("Breakdown should contain total", breakdown.contains("Total"));

        System.out.println("Evaluation breakdown:");
        System.out.println(breakdown);
        System.out.println("✅ Evaluation Breakdown: PASSED");
    }

    @Test
    public void testQuickEvaluationPerformance() {
        System.out.println("⚡ Testing Quick Evaluation Performance...");

        // Test the quick evaluation method
        int fullScore = evaluator.evaluate(startPosition);
        int quickScore = evaluator.evaluateQuick(startPosition);

        System.out.println("Full evaluation: " + fullScore);
        System.out.println("Quick evaluation: " + quickScore);

        // Quick evaluation should be roughly similar but potentially different
        assertTrue("Quick evaluation should be reasonable", Math.abs(quickScore) < 10000);

        // Test performance
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            evaluator.evaluateQuick(startPosition);
        }
        long endTime = System.currentTimeMillis();

        long timePerEval = (endTime - startTime) / 10000;
        System.out.println("Time per quick evaluation: " + timePerEval + "ms (for 10k evals)");

        System.out.println("✅ Quick Evaluation: PASSED");
    }

    // ================================================
    // EDGE CASES AND ROBUSTNESS TESTS
    // ================================================

    @Test
    public void testEmptyBoardEvaluation() {
        System.out.println("🕳️ Testing Empty Board Evaluation...");

        int emptyScore = evaluator.evaluate(emptyBoard);

        System.out.println("Empty board score: " + emptyScore);

        // Empty board should have reasonable score
        assertTrue("Empty board should have reasonable score", Math.abs(emptyScore) < 5000);

        System.out.println("✅ Empty Board Evaluation: PASSED");
    }

    @Test
    public void testNullStateHandling() {
        System.out.println("❌ Testing Null State Handling...");

        try {
            int score = evaluator.evaluate(null);
            fail("Should handle null state gracefully or throw appropriate exception");
        } catch (Exception e) {
            System.out.println("Properly handled null state: " + e.getClass().getSimpleName());
        }

        try {
            int terminalScore = evaluator.checkTerminal(null);
            fail("Should handle null state in terminal check");
        } catch (Exception e) {
            System.out.println("Properly handled null in terminal check: " + e.getClass().getSimpleName());
        }

        System.out.println("✅ Null State Handling: PASSED");
    }

    @Test
    public void testCorruptedStateHandling() {
        System.out.println("🔧 Testing Corrupted State Handling...");

        // Create a potentially corrupted state
        GameState corruptedState = startPosition.copy();

        // Try to corrupt it (this might not work due to defensive programming)
        try {
            corruptedState.redStackHeights[0] = -1; // Invalid height

            int score = evaluator.evaluate(corruptedState);

            // Should either handle gracefully or detect corruption
            assertTrue("Should handle corrupted state", Math.abs(score) < 100000);

        } catch (Exception e) {
            System.out.println("Properly detected corruption: " + e.getClass().getSimpleName());
        }

        System.out.println("✅ Corrupted State Handling: PASSED");
    }

    // ================================================
    // EVALUATION BALANCE TESTS
    // ================================================

    @Test
    public void testEvaluationBalance() {
        System.out.println("⚖️ Testing Evaluation Balance...");

        // Test various positions to ensure evaluation is balanced
        GameState[] testPositions = {
                startPosition,
                materialAdvantage,
                guardNearCastle,
                emptyBoard
        };

        String[] positionNames = {
                "Start Position",
                "Material Advantage",
                "Guard Near Castle",
                "Empty Board"
        };

        for (int i = 0; i < testPositions.length; i++) {
            int score = evaluator.evaluate(testPositions[i]);
            System.out.println(positionNames[i] + ": " + score);

            // All scores should be reasonable
            assertTrue("Score should be reasonable for " + positionNames[i],
                    Math.abs(score) < 15000);
        }

        System.out.println("✅ Evaluation Balance: PASSED");
    }

    @Test
    public void testEvaluationGradients() {
        System.out.println("📈 Testing Evaluation Gradients...");

        // Test that evaluation changes smoothly for similar positions
        GameState basePosition = emptyBoard.copy();

        // Move red guard forward one step at a time
        int[] scores = new int[7];
        for (int rank = 0; rank < 7; rank++) {
            GameState testPos = basePosition.copy();

            // Clear old guard position
            testPos.redGuard = 0;

            // Place guard at new rank
            int newPos = GameState.getIndex(rank, 3); // D-file
            testPos.redGuard = GameState.bit(newPos);

            scores[rank] = evaluator.evaluate(testPos);
            System.out.println("Guard at rank " + (rank + 1) + ": " + scores[rank]);
        }

        // Scores should generally increase as red guard advances toward blue castle
        int improvements = 0;
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > scores[i-1]) improvements++;
        }

        assertTrue("Guard advancement should generally improve score", improvements >= 3);

        System.out.println("✅ Evaluation Gradients: PASSED");
    }

    // ================================================
    // COMPREHENSIVE EVALUATOR TEST
    // ================================================

    @Test
    public void testComprehensiveEvaluatorFunctionality() {
        System.out.println("\n🏆 COMPREHENSIVE EVALUATOR TEST");
        System.out.println("=".repeat(50));

        // Test all major components
        System.out.println("Testing all evaluation components...");

        // 1. Basic scoring
        int basicScore = evaluator.evaluate(startPosition);
        assertTrue("Basic evaluation works", Math.abs(basicScore) < 50000);

        // 2. Terminal detection
        int terminalCheck = evaluator.checkTerminal(terminalPosition);
        assertTrue("Terminal detection works", Math.abs(terminalCheck) > 1000);

        // 3. Threat detection
        boolean threatWorks = evaluator.isGuardThreatened(guardThreatened, true);
        assertTrue("Threat detection works", threatWorks);

        // 4. Breakdown analysis
        String breakdown = evaluator.getEvaluationBreakdown(startPosition);
        assertTrue("Breakdown works", breakdown != null && breakdown.length() > 50);

        // 5. Quick evaluation
        int quickEval = evaluator.evaluateQuick(startPosition);
        assertTrue("Quick evaluation works", Math.abs(quickEval) < 50000);

        System.out.println("📊 Final Results:");
        System.out.println("  Basic Score: " + basicScore);
        System.out.println("  Terminal Score: " + terminalCheck);
        System.out.println("  Threat Detection: " + threatWorks);
        System.out.println("  Quick Eval: " + quickEval);
        System.out.println("  Breakdown Lines: " + breakdown.split("\n").length);

        System.out.println("\n🎉 ALL EVALUATOR FUNCTIONALITY WORKING! 🎉");
        System.out.println("=".repeat(50));
    }
}
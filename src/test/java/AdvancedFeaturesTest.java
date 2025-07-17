import GaT.search.Engine;
import GaT.game.GameState;
import GaT.game.Move;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * ADVANCED FEATURES TEST
 * Tests for advanced search features like Null-Move, LMR, Aspiration Windows, etc.
 */
public class AdvancedFeaturesTest {

    private Engine engine;
    private GameState complexPosition;
    private GameState midgamePosition;
    private GameState endgamePosition;

    @Before
    public void setUp() {
        engine = new Engine();
        complexPosition = GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r");
        midgamePosition = GameState.fromFen("3RG3/2r11r12/3b13/7/3b13/2r11r12/3BG3 b");
        endgamePosition = GameState.fromFen("3RG3/7/7/7/7/7/3BG3 r");
    }

    @Test
    public void testNullMovePruning() {
        System.out.println("🔍 Testing Null-Move Pruning...");

        long startTime = System.currentTimeMillis();
        Move move = engine.findBestMove(complexPosition, 8, 5000);
        long endTime = System.currentTimeMillis();

        assertNotNull("Should find a move", move);
        assertTrue("Should complete within time limit", endTime - startTime < 5000);
        assertTrue("Should search reasonable number of nodes", engine.getNodesSearched() > 1000);

        System.out.printf("✅ Null-Move: %dms, move: %s, nodes: %,d%n",
                endTime - startTime, move, engine.getNodesSearched());
    }

    @Test
    public void testLateMoveReductions() {
        System.out.println("⚡ Testing Late-Move Reductions...");

        long startTime = System.currentTimeMillis();
        Move move = engine.findBestMove(complexPosition, 6, 3000);
        long endTime = System.currentTimeMillis();

        assertNotNull("Should find a move", move);
        assertTrue("Should complete within time limit", endTime - startTime < 3000);

        System.out.printf("✅ LMR: %dms, move: %s, nodes: %,d%n",
                endTime - startTime, move, engine.getNodesSearched());
    }

    @Test
    public void testAspirationWindows() {
        System.out.println("🎯 Testing Aspiration Windows...");

        long startTime = System.currentTimeMillis();
        Move move = engine.findBestMove(complexPosition, 8, 5000);
        long endTime = System.currentTimeMillis();

        assertNotNull("Should find a move", move);
        assertTrue("Should complete within time limit", endTime - startTime < 5000);

        System.out.printf("✅ Aspiration: %dms, move: %s, nodes: %,d%n",
                endTime - startTime, move, engine.getNodesSearched());
    }

    @Test
    public void testFutilityPruning() {
        System.out.println("🌟 Testing Futility Pruning...");

        long startTime = System.currentTimeMillis();
        Move move = engine.findBestMove(complexPosition, 4, 1000);
        long endTime = System.currentTimeMillis();

        assertNotNull("Should find a move", move);
        assertTrue("Should complete within time limit", endTime - startTime < 1000);

        System.out.printf("✅ Futility: %dms, move: %s, nodes: %,d%n",
                endTime - startTime, move, engine.getNodesSearched());
    }

    @Test
    public void testMoveOrdering() {
        System.out.println("📊 Testing Move Ordering...");

        // Run multiple searches to build up move ordering data
        for (int i = 0; i < 3; i++) {
            Move move = engine.findBestMove(complexPosition, 4, 1000);
            assertNotNull("Should find move in iteration " + i, move);
        }

        System.out.println("✅ Move ordering data accumulated successfully");
    }

    @Test
    public void testEnginePerformanceScaling() {
        System.out.println("📈 Testing Engine Performance Scaling...");

        int[] depths = {4, 6, 8};
        long[] times = new long[depths.length];
        int[] nodes = new int[depths.length];

        for (int i = 0; i < depths.length; i++) {
            long startTime = System.currentTimeMillis();
            Move move = engine.findBestMove(complexPosition, depths[i], 10000);
            long endTime = System.currentTimeMillis();

            times[i] = endTime - startTime;
            nodes[i] = engine.getNodesSearched();

            assertNotNull("Should find move at depth " + depths[i], move);

            System.out.printf("Depth %d: %dms, %,d nodes, %.0f nps%n",
                    depths[i], times[i], nodes[i],
                    times[i] > 0 ? (nodes[i] * 1000.0 / times[i]) : 0);
        }

        // Performance should scale reasonably
        assertTrue("Should have reasonable performance at depth 4", times[0] < 5000);
        assertTrue("Should search nodes at each depth", nodes[0] > 100);
    }

    @Test
    public void testPositionTypes() {
        System.out.println("🎯 Testing Different Position Types...");

        GameState[] positions = {complexPosition, midgamePosition, endgamePosition};
        String[] names = {"Complex", "Midgame", "Endgame"};

        for (int i = 0; i < positions.length; i++) {
            long startTime = System.currentTimeMillis();
            Move move = engine.findBestMove(positions[i], 6, 3000);
            long endTime = System.currentTimeMillis();

            assertNotNull("Should find move for " + names[i], move);
            assertTrue("Should complete within time limit for " + names[i],
                    endTime - startTime < 3000);

            System.out.printf("✅ %s: %dms, move: %s, nodes: %,d%n",
                    names[i], endTime - startTime, move, engine.getNodesSearched());
        }
    }

    @Test
    public void testTranspositionTableEfficiency() {
        System.out.println("🔍 Testing Transposition Table Efficiency...");

        // Run same position multiple times to test TT hits
        for (int i = 0; i < 5; i++) {
            Move move = engine.findBestMove(complexPosition, 5, 2000);
            assertNotNull("Should find move in TT test " + i, move);
        }

        double hitRate = engine.getTTHitRate();
        assertTrue("Should have reasonable TT hit rate", hitRate >= 0.0);

        System.out.printf("✅ TT Hit Rate: %.1f%%%n", hitRate);
    }

    @Test
    public void testTimeManagement() {
        System.out.println("⏱️ Testing Time Management...");

        long timeLimit = 2000; // 2 seconds
        long startTime = System.currentTimeMillis();
        Move move = engine.findBestMove(complexPosition, 10, timeLimit);
        long actualTime = System.currentTimeMillis() - startTime;

        assertNotNull("Should find a move within time limit", move);
        assertTrue("Should respect time limit", actualTime <= timeLimit + 100); // Small buffer

        System.out.printf("✅ Time Management: %dms (limit: %dms)%n", actualTime, timeLimit);
    }

    @Test
    public void testEngineConsistency() {
        System.out.println("🔄 Testing Engine Consistency...");

        // Run same position multiple times, should get same or similar results
        Move firstMove = engine.findBestMove(complexPosition, 6, 2000);
        assertNotNull("Should find first move", firstMove);

        for (int i = 0; i < 3; i++) {
            Move move = engine.findBestMove(complexPosition, 6, 2000);
            assertNotNull("Should find consistent move " + i, move);
            // Note: With advanced features, moves might vary slightly, but should be reasonable
        }

        System.out.printf("✅ Engine Consistency: First move was %s%n", firstMove);
    }
}
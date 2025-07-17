import GaT.search.Engine;
import GaT.game.GameState;
import GaT.game.Move;
import GaT.benchmark.EngineBenchmark;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * ENGINE BENCHMARK TEST
 * Performance and benchmarking tests for the engine
 */
public class EngineBenchmarkTest {

    private Engine engine;
    private GameState startPosition;
    private GameState complexPosition;
    private GameState midgamePosition;
    private GameState endgamePosition;

    @Before
    public void setUp() {
        engine = new Engine();
        startPosition = GameState.fromFen("b1b11BG1b1b1/2b11b12/3b13/7/3r13/2r11r12/r1r11RG1r1r1 r");
        complexPosition = GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r");
        midgamePosition = GameState.fromFen("3RG3/2r11r12/3b13/7/3b13/2r11r12/3BG3 b");
        endgamePosition = GameState.fromFen("3RG3/7/7/7/7/7/3BG3 r");
    }

    @Test
    public void testBenchmarkPosition() {
        System.out.println("📊 Testing Benchmark Position Analysis...");

        GameState[] positions = {startPosition, complexPosition, midgamePosition, endgamePosition};
        String[] names = {"Start", "Complex", "Midgame", "Endgame"};

        for (int i = 0; i < positions.length; i++) {
            System.out.println("\n🎯 Testing position: " + names[i]);
            benchmarkSinglePosition(positions[i], names[i]);
        }
    }

    private void benchmarkSinglePosition(GameState position, String name) {
        System.out.printf("%-8s%-12s%-15s%-12s%-15s%n",
                "Depth", "Time(ms)", "Nodes", "NPS", "Best Move");
        System.out.println("-".repeat(60));

        for (int depth = 4; depth <= 8; depth += 2) {
            try {
                long startTime = System.currentTimeMillis();
                Move bestMove = engine.findBestMove(position, depth, 5000);
                long endTime = System.currentTimeMillis();

                long searchTime = endTime - startTime;
                int nodes = engine.getNodesSearched();
                double nps = searchTime > 0 ? (nodes * 1000.0 / searchTime) : 0;

                assertNotNull("Should find move at depth " + depth, bestMove);
                assertTrue("Should complete within time limit", searchTime < 5000);

                System.out.printf("%-8d%-12d%-15,d%-12,.0f%-15s%n",
                        depth, searchTime, nodes, nps, bestMove.toString());

                // Performance assertions
                assertTrue("Should have reasonable node count", nodes > 100);
                assertTrue("Should have reasonable NPS", nps > 1000);

                if (searchTime > 3000) break; // Don't go too deep

            } catch (Exception e) {
                System.out.printf("%-8d%-12s%-15s%-12s%-15s%n",
                        depth, "ERROR", "-", "-", e.getMessage());
                fail("Benchmark failed at depth " + depth + ": " + e.getMessage());
            }
        }
    }

    @Test
    public void testOpeningBookPerformance() {
        System.out.println("📚 Testing Opening Book Performance...");

        // Test book lookup performance
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            Move bookMove = engine.findBestMove(startPosition, 100); // Quick lookup
            assertNotNull("Should find move in book lookup " + i, bookMove);
        }
        long endTime = System.currentTimeMillis();

        long totalTime = endTime - startTime;
        double avgTime = totalTime / 100.0;

        System.out.printf("100 book lookups: %dms (%.2fms average)%n", totalTime, avgTime);

        // Performance assertion
        assertTrue("Book lookup should be fast", avgTime < 50); // Less than 50ms average

        System.out.println("✅ Opening book performance test passed");
    }

    @Test
    public void testDepthScaling() {
        System.out.println("📈 Testing Depth Scaling Performance...");

        int[] depths = {3, 4, 5, 6, 7};
        long[] times = new long[depths.length];
        int[] nodeCounts = new int[depths.length];

        for (int i = 0; i < depths.length; i++) {
            try {
                long startTime = System.currentTimeMillis();
                Move move = engine.findBestMove(complexPosition, depths[i], 10000);
                long endTime = System.currentTimeMillis();

                times[i] = endTime - startTime;
                nodeCounts[i] = engine.getNodesSearched();

                assertNotNull("Should find move at depth " + depths[i], move);

                System.out.printf("Depth %d: %dms, %,d nodes%n",
                        depths[i], times[i], nodeCounts[i]);

                if (times[i] > 8000) break; // Stop if getting too slow

            } catch (Exception e) {
                System.out.println("Error at depth " + depths[i] + ": " + e.getMessage());
                break;
            }
        }

        // Analyze scaling
        if (nodeCounts.length >= 2) {
            System.out.println("\n📊 Scaling Analysis:");
            for (int i = 1; i < Math.min(depths.length, times.length); i++) {
                if (times[i-1] > 0 && nodeCounts[i-1] > 0) {
                    double timeRatio = (double) times[i] / times[i-1];
                    double nodeRatio = (double) nodeCounts[i] / nodeCounts[i-1];

                    System.out.printf("Depth %d→%d: Time×%.1f, Nodes×%.1f%n",
                            depths[i-1], depths[i], timeRatio, nodeRatio);

                    // Performance assertions
                    assertTrue("Time scaling should be reasonable", timeRatio < 50);
                    assertTrue("Node scaling should be reasonable", nodeRatio < 50);
                }
            }
        }
    }

    @Test
    public void testTimeConstrainedSearch() {
        System.out.println("⏱️ Testing Time-Constrained Search...");

        int[] timeLimits = {500, 1000, 2000, 5000}; // 0.5s, 1s, 2s, 5s

        System.out.printf("%-12s%-10s%-12s%-15s%n", "Time Limit", "Actual", "Nodes", "NPS");
        System.out.println("-".repeat(50));

        for (int timeLimit : timeLimits) {
            try {
                long startTime = System.currentTimeMillis();
                Move bestMove = engine.findBestMove(complexPosition, timeLimit);
                long actualTime = System.currentTimeMillis() - startTime;

                int nodes = engine.getNodesSearched();
                double nps = actualTime > 0 ? (nodes * 1000.0 / actualTime) : 0;

                assertNotNull("Should find move within " + timeLimit + "ms", bestMove);
                assertTrue("Should respect time limit", actualTime <= timeLimit + 200); // Small buffer

                System.out.printf("%-12s%-10d%-12,d%-15,.0f%n",
                        timeLimit + "ms", actualTime, nodes, nps);

            } catch (Exception e) {
                System.out.printf("%-12s%-10s%-12s%-15s%n",
                        timeLimit + "ms", "ERROR", "-", e.getMessage());
                fail("Time-constrained search failed: " + e.getMessage());
            }
        }
    }

    @Test
    public void testMemoryUsage() {
        System.out.println("💾 Testing Memory Usage...");

        Runtime runtime = Runtime.getRuntime();

        // Initial memory state
        System.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // Run multiple searches
        for (int i = 1; i <= 10; i++) {
            engine.findBestMove(complexPosition, 5, 2000);

            if (i % 3 == 0) {
                System.gc();
                long currentMemory = runtime.totalMemory() - runtime.freeMemory();
                long memoryIncrease = currentMemory - initialMemory;

                System.out.printf("After search %d: Memory usage +%.1f MB%n",
                        i, memoryIncrease / (1024.0 * 1024.0));
            }
        }

        // Final memory check
        System.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long totalIncrease = finalMemory - initialMemory;

        System.out.printf("Memory increase: %.1f MB%n", totalIncrease / (1024.0 * 1024.0));

        // Memory assertion (should not leak excessively)
        assertTrue("Memory usage should be reasonable",
                totalIncrease < 100 * 1024 * 1024); // Less than 100MB increase
    }

    @Test
    public void testFullBenchmark() {
        System.out.println("🚀 Testing Full Benchmark Suite...");

        try {
            // This will run the full benchmark suite
            EngineBenchmark.runFullBenchmark();

            System.out.println("✅ Full benchmark completed successfully");

        } catch (Exception e) {
            fail("Full benchmark failed: " + e.getMessage());
        }
    }

    @Test
    public void testEngineStatistics() {
        System.out.println("📊 Testing Engine Statistics...");

        // Run a search to generate statistics
        Move move = engine.findBestMove(complexPosition, 6, 3000);
        assertNotNull("Should find a move", move);

        // Check statistics
        int nodesSearched = engine.getNodesSearched();
        double ttHitRate = engine.getTTHitRate();

        assertTrue("Should have searched nodes", nodesSearched > 0);
        assertTrue("TT hit rate should be valid", ttHitRate >= 0.0 && ttHitRate <= 100.0);

        System.out.printf("Statistics: %,d nodes, %.1f%% TT hit rate%n",
                nodesSearched, ttHitRate);

        System.out.println("✅ Engine statistics test passed");
    }
}
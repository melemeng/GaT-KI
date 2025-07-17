package GaT.benchmark;

import GaT.search.Engine;
import GaT.game.GameState;
import GaT.game.Move;
import GaT.game.MoveGenerator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * UPDATED ENGINE BENCHMARK for Guard & Towers
 *
 * Comprehensive performance testing for the new Engine architecture:
 * ✅ Move generation benchmark (updated from original Benchmark.java)
 * ✅ Engine search performance at various depths
 * ✅ Position-specific performance analysis
 * ✅ Time-based benchmarks
 * ✅ Memory usage analysis
 * ✅ Comparison with performance targets
 *
 * Based on your original Benchmark.java but optimized for new architecture.
 */
public class EngineBenchmark {

    // === TEST POSITIONS (from your original Benchmark.java) ===
    private static GameState getStartPosition() {
        return GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r");
    }

    private static GameState getMidPosition() {
        return GameState.fromFen("r1r11RG3/6r1/3r11r21/7/3b23/1b15/b12BG1b1b1 b");
    }

    private static GameState getEndPosition() {
        return GameState.fromFen("3RG3/3r33/3b33/7/7/7/3BG3 r");
    }

    private static GameState getSimpleStart() {
        return new GameState(); // Clean starting position
    }

    // === MAIN BENCHMARK RUNNER ===

    public static void main(String[] args) {
        System.out.println("🚀 GUARD & TOWERS ENGINE BENCHMARK");
        System.out.println("🎯 Testing new Engine architecture performance");
        System.out.println("=" .repeat(60));

        runFullBenchmark();
    }

    public static void runFullBenchmark() {
        try {
            // 1. Move generation benchmark (from your original)
            runMoveGenerationBenchmark();

            // 2. Engine search benchmark
            runEngineSearchBenchmark();

            // 3. Depth scaling analysis
            runDepthScalingBenchmark();

            // 4. Time-constrained benchmark
            runTimeConstrainedBenchmark();

            // 5. Memory benchmark
            runMemoryBenchmark();

            // 6. Performance summary
            printPerformanceSummary();

        } catch (Exception e) {
            System.err.println("❌ Benchmark failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // === 1. MOVE GENERATION BENCHMARK (Updated from your original) ===

    private static void runMoveGenerationBenchmark() {
        System.out.println("\n📋 MOVE GENERATION BENCHMARK");
        System.out.println("Testing move generation performance (updated from original Benchmark.java)");
        System.out.println("-".repeat(50));

        List<GameState> states = Arrays.asList(
                getSimpleStart(),
                getStartPosition(),
                getMidPosition(),
                getEndPosition()
        );

        String[] positionNames = {"Simple Start", "Complex Start", "Midgame", "Endgame"};

        for (int i = 0; i < states.size(); i++) {
            benchmarkMoveGeneration(states.get(i), positionNames[i]);
        }
    }

    private static void benchmarkMoveGeneration(GameState state, String positionName) {
        System.out.println("\nPosition: " + positionName);

        if (state == null) {
            System.out.println("  ❌ Invalid position, skipping");
            return;
        }

        // Warmup
        for (int i = 0; i < 1000; i++) {
            try {
                MoveGenerator.generateAllMoves(state);
            } catch (Exception e) {
                System.out.println("  ❌ Move generation failed: " + e.getMessage());
                return;
            }
        }

        // Benchmark (30 iterations of 100,000 generations like original)
        List<Long> benchmarks = new ArrayList<>();
        int iterations = 30;
        int generationsPerIteration = 100_000;

        for (int i = 0; i < iterations; i++) {
            long startTime = System.currentTimeMillis();

            for (int j = 0; j < generationsPerIteration; j++) {
                MoveGenerator.generateAllMoves(state);
            }

            long endTime = System.currentTimeMillis();
            benchmarks.add(endTime - startTime);
        }

        // Calculate statistics
        long avgTime = benchmarks.stream().mapToLong(Long::longValue).sum() / benchmarks.size();
        float avgSeconds = avgTime / 1000.0f;
        int numMoves = MoveGenerator.generateAllMoves(state).size();

        // Performance metrics
        long totalGenerations = (long) iterations * generationsPerIteration;
        double generationsPerSecond = totalGenerations / (avgTime / 1000.0 * iterations);

        System.out.println("  📊 Results:");
        System.out.printf("    Iterations: %d × %,d generations%n", iterations, generationsPerIteration);
        System.out.printf("    Average time: %d ms (%.3f seconds)%n", avgTime, avgSeconds);
        System.out.printf("    Legal moves: %d%n", numMoves);
        System.out.printf("    Generations/sec: %,.0f%n", generationsPerSecond);

        // Performance rating
        if (generationsPerSecond > 1_000_000) {
            System.out.println("    ✅ Excellent performance");
        } else if (generationsPerSecond > 500_000) {
            System.out.println("    ✅ Good performance");
        } else if (generationsPerSecond > 100_000) {
            System.out.println("    ⚠️  Acceptable performance");
        } else {
            System.out.println("    ❌ Poor performance - needs optimization");
        }
    }

    // === 2. ENGINE SEARCH BENCHMARK ===

    private static void runEngineSearchBenchmark() {
        System.out.println("\n🔍 ENGINE SEARCH BENCHMARK");
        System.out.println("Testing new Engine performance at various depths");
        System.out.println("-".repeat(50));

        Engine engine = new Engine();
        GameState testPosition = getSimpleStart();

        System.out.printf("%-8s%-12s%-12s%-15s%-12s%s%n",
                "Depth", "Time(ms)", "Nodes", "NPS", "TT Hit%", "Best Move");
        System.out.println("-".repeat(70));

        for (int depth = 4; depth <= 10; depth++) {
            benchmarkEngineAtDepth(engine, testPosition, depth);
        }
    }

    private static void benchmarkEngineAtDepth(Engine engine, GameState state, int depth) {
        try {
            long startTime = System.currentTimeMillis();
            Move bestMove = engine.findBestMove(state, depth, 30000); // 30 second limit
            long endTime = System.currentTimeMillis();

            long searchTime = endTime - startTime;
            int nodes = engine.getNodesSearched();
            double nps = searchTime > 0 ? (nodes * 1000.0 / searchTime) : 0;
            double ttHitRate = engine.getTTHitRate();

            System.out.printf("%-8d%-12d%-12s%-15s%-12.1f%s%n",
                    depth,
                    searchTime,
                    String.format("%,d", nodes),
                    String.format("%.0fk", nps / 1000),
                    ttHitRate,
                    bestMove != null ? bestMove.toString() : "null"
            );

            // Stop if search takes too long
            if (searchTime > 15000) {
                System.out.println("  (Stopping benchmark - search taking too long)");
                return;
            }

        } catch (Exception e) {
            System.out.printf("%-8d%-12s%-12s%-15s%-12s%s%n",
                    depth, "ERROR", "-", "-", "-", e.getMessage());
        }
    }

    // === 3. DEPTH SCALING ANALYSIS ===

    private static void runDepthScalingBenchmark() {
        System.out.println("\n📈 DEPTH SCALING ANALYSIS");
        System.out.println("Analyzing how performance scales with search depth");
        System.out.println("-".repeat(50));

        Engine engine = new Engine();
        GameState position = getSimpleStart();

        List<Integer> depths = Arrays.asList(3, 4, 5, 6, 7, 8);
        List<Long> times = new ArrayList<>();
        List<Integer> nodeCounts = new ArrayList<>();

        for (int depth : depths) {
            try {
                long startTime = System.currentTimeMillis();
                Move move = engine.findBestMove(position, depth, 10000); // 10 second limit
                long endTime = System.currentTimeMillis();

                times.add(endTime - startTime);
                nodeCounts.add(engine.getNodesSearched());

                if (endTime - startTime > 8000) break; // Stop if getting too slow

            } catch (Exception e) {
                System.out.println("  Error at depth " + depth + ": " + e.getMessage());
                break;
            }
        }

        // Calculate branching factor
        System.out.println("\n📊 Scaling Analysis:");
        for (int i = 1; i < Math.min(depths.size(), times.size()); i++) {
            double timeRatio = (double) times.get(i) / times.get(i-1);
            double nodeRatio = (double) nodeCounts.get(i) / nodeCounts.get(i-1);

            System.out.printf("  Depth %d→%d: Time×%.1f, Nodes×%.1f%n",
                    depths.get(i-1), depths.get(i), timeRatio, nodeRatio);
        }

        if (nodeCounts.size() >= 2) {
            double avgBranchingFactor = 0;
            int count = 0;
            for (int i = 1; i < nodeCounts.size(); i++) {
                double ratio = (double) nodeCounts.get(i) / nodeCounts.get(i-1);
                if (ratio > 1 && ratio < 20) { // Reasonable range
                    avgBranchingFactor += ratio;
                    count++;
                }
            }
            if (count > 0) {
                avgBranchingFactor /= count;
                System.out.printf("  Average effective branching factor: %.1f%n", avgBranchingFactor);
            }
        }
    }

    // === 4. TIME-CONSTRAINED BENCHMARK ===

    private static void runTimeConstrainedBenchmark() {
        System.out.println("\n⏱️  TIME-CONSTRAINED BENCHMARK");
        System.out.println("Testing search performance with various time limits");
        System.out.println("-".repeat(50));

        Engine engine = new Engine();
        GameState position = getStartPosition();

        int[] timeLimits = {1000, 3000, 5000, 10000, 30000}; // 1s, 3s, 5s, 10s, 30s

        System.out.printf("%-12s%-10s%-12s%-15s%s%n",
                "Time Limit", "Actual", "Nodes", "NPS", "Best Move");
        System.out.println("-".repeat(60));

        for (int timeLimit : timeLimits) {
            try {
                long startTime = System.currentTimeMillis();
                Move bestMove = engine.findBestMove(position, timeLimit);
                long actualTime = System.currentTimeMillis() - startTime;

                int nodes = engine.getNodesSearched();
                double nps = actualTime > 0 ? (nodes * 1000.0 / actualTime) : 0;

                System.out.printf("%-12s%-10d%-12s%-15s%s%n",
                        timeLimit + "ms",
                        actualTime,
                        String.format("%,d", nodes),
                        String.format("%.0fk", nps / 1000),
                        bestMove != null ? bestMove.toString() : "null"
                );

            } catch (Exception e) {
                System.out.printf("%-12s%-10s%-12s%-15s%s%n",
                        timeLimit + "ms", "ERROR", "-", "-", e.getMessage());
            }
        }
    }

    // === 5. MEMORY BENCHMARK ===

    private static void runMemoryBenchmark() {
        System.out.println("\n💾 MEMORY BENCHMARK");
        System.out.println("Testing memory usage and garbage collection");
        System.out.println("-".repeat(50));

        Runtime runtime = Runtime.getRuntime();

        // Initial memory state
        System.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        Engine engine = new Engine();
        GameState position = getSimpleStart();

        // Run multiple searches
        System.out.println("Running 10 searches to test memory stability...");

        for (int i = 1; i <= 10; i++) {
            engine.findBestMove(position, 5, 2000); // 2 second searches

            if (i % 3 == 0) {
                System.gc();
                long currentMemory = runtime.totalMemory() - runtime.freeMemory();
                long memoryIncrease = currentMemory - initialMemory;

                System.out.printf("  After search %d: Memory usage +%.1f MB%n",
                        i, memoryIncrease / (1024.0 * 1024.0));
            }
        }

        // Final memory check
        System.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long totalIncrease = finalMemory - initialMemory;

        System.out.printf("\n📊 Memory Analysis:%n");
        System.out.printf("  Initial memory: %.1f MB%n", initialMemory / (1024.0 * 1024.0));
        System.out.printf("  Final memory: %.1f MB%n", finalMemory / (1024.0 * 1024.0));
        System.out.printf("  Total increase: %.1f MB%n", totalIncrease / (1024.0 * 1024.0));

        if (totalIncrease < 50 * 1024 * 1024) { // Less than 50MB increase
            System.out.println("  ✅ Good memory management");
        } else {
            System.out.println("  ⚠️  High memory usage - possible memory leak");
        }
    }

    // === 6. PERFORMANCE SUMMARY ===

    private static void printPerformanceSummary() {
        System.out.println("\n🏆 PERFORMANCE SUMMARY");
        System.out.println("=" .repeat(60));

        // Run quick benchmark for summary
        Engine engine = new Engine();
        GameState position = getSimpleStart();

        try {
            long startTime = System.currentTimeMillis();
            Move bestMove = engine.findBestMove(position, 8, 10000); // Depth 8, 10 seconds
            long endTime = System.currentTimeMillis();

            long searchTime = endTime - startTime;
            int nodes = engine.getNodesSearched();
            double nps = searchTime > 0 ? (nodes * 1000.0 / searchTime) : 0;

            System.out.println("📊 FINAL BENCHMARK RESULTS:");
            System.out.printf("  Search depth: 8%n");
            System.out.printf("  Search time: %d ms%n", searchTime);
            System.out.printf("  Nodes searched: %,d%n", nodes);
            System.out.printf("  Nodes per second: %,.0f%n", nps);
            System.out.printf("  Transposition table hit rate: %.1f%%%n", engine.getTTHitRate());
            System.out.printf("  Best move: %s%n", bestMove);

            System.out.println("\n🎯 PERFORMANCE TARGETS:");
            System.out.println("  • 100k+ NPS = Excellent Java performance ✅");
            System.out.println("  • Depth 8 in <2s = Good baseline ✅");
            System.out.println("  • Depth 12 in <10s = Competitive ⏳");
            System.out.println("  • Depth 15+ in 30s = Tournament ready 🏆");

            // Performance rating
            if (nps > 200_000 && searchTime < 2000) {
                System.out.println("\n🚀 EXCELLENT PERFORMANCE - Tournament ready!");
            } else if (nps > 100_000 && searchTime < 5000) {
                System.out.println("\n✅ GOOD PERFORMANCE - Competitive level");
            } else if (nps > 50_000) {
                System.out.println("\n⚠️  ACCEPTABLE PERFORMANCE - Needs optimization");
            } else {
                System.out.println("\n❌ POOR PERFORMANCE - Major optimization needed");
            }

        } catch (Exception e) {
            System.out.println("❌ Summary benchmark failed: " + e.getMessage());
        }

        System.out.println("\n✅ Benchmark complete!");
    }
}
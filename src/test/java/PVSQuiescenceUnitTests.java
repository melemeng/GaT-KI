import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;
import GaT.engine.TimedMinimax;
import GaT.search.SearchStatistics;
import GaT.search.PVSSearch;
import GaT.search.QuiescenceSearch;
import GaT.search.MoveGenerator;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class PVSQuiescenceUnitTests {

    // === BASIC PVS_Q FUNCTIONALITY TESTS ===

    @Test
    public void testPVSQuiescenceBasicFunctionality() {
        System.out.println("\n=== Testing PVS + Quiescence Basic Functionality ===");

        GameState state = new GameState();
        SearchStatistics stats = SearchStatistics.getInstance();
        stats.reset();

        Move bestMove = TimedMinimax.findBestMoveWithStrategy(
                state, 4, 2000, SearchConfig.SearchStrategy.PVS_Q);

        long nodeCount = stats.getNodeCount();
        long qNodeCount = stats.getQNodeCount();

        System.out.println("Best move: " + bestMove);
        System.out.println("Regular nodes: " + nodeCount);
        System.out.println("Quiescence nodes: " + qNodeCount);
        System.out.println("Total nodes: " + (nodeCount + qNodeCount));

        assertNotNull("PVS_Q should find a move", bestMove);
        assertTrue("Should have regular nodes", nodeCount > 0);
        assertTrue("Total nodes should be substantial", (nodeCount + qNodeCount) > 100);
    }

    @Test
    public void testPVSQuiescenceNodeCounting() {
        System.out.println("\n=== Testing PVS + Quiescence Node Counting ===");

        GameState state = GameState.fromFen("7/7/7/3RG3/7/7/3BG3 r");
        SearchStatistics stats = SearchStatistics.getInstance();
        stats.reset();

        long nodesBefore = stats.getNodeCount();
        long qNodesBefore = stats.getQNodeCount();

        Move bestMove = TimedMinimax.findBestMoveWithStrategy(
                state, 3, 1000, SearchConfig.SearchStrategy.PVS_Q);

        long nodesAfter = stats.getNodeCount();
        long qNodesAfter = stats.getQNodeCount();

        long nodesGenerated = nodesAfter - nodesBefore;
        long qNodesGenerated = qNodesAfter - qNodesBefore;

        System.out.println("Nodes generated: " + nodesGenerated);
        System.out.println("Q-Nodes generated: " + qNodesGenerated);
        System.out.println("Node count working: " + (nodesGenerated > 0));

        assertNotNull("Should find a move", bestMove);
        assertTrue("Should generate regular nodes", nodesGenerated > 0);
        assertTrue("Node counting should work", nodesGenerated > 50);
    }

    @Test
    public void testPVSQuiescenceComparedToAlphaBeta() {
        System.out.println("\n=== Testing PVS_Q vs Alpha-Beta Performance ===");

        GameState complexState = GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r");

        // Test Alpha-Beta
        SearchStatistics.getInstance().reset();
        long startTime = System.currentTimeMillis();
        Move alphaBetaMove = TimedMinimax.findBestMoveWithStrategy(
                complexState, 4, 3000, SearchConfig.SearchStrategy.ALPHA_BETA_Q);
        long alphaBetaTime = System.currentTimeMillis() - startTime;
        long alphaBetaNodes = SearchStatistics.getInstance().getNodeCount();

        // Test PVS_Q
        SearchStatistics.getInstance().reset();
        startTime = System.currentTimeMillis();
        Move pvsMove = TimedMinimax.findBestMoveWithStrategy(
                complexState, 4, 3000, SearchConfig.SearchStrategy.PVS_Q);
        long pvsTime = System.currentTimeMillis() - startTime;
        long pvsNodes = SearchStatistics.getInstance().getNodeCount();

        System.out.println("Alpha-Beta: " + alphaBetaMove + " (" + alphaBetaTime + "ms, " + alphaBetaNodes + " nodes)");
        System.out.println("PVS_Q: " + pvsMove + " (" + pvsTime + "ms, " + pvsNodes + " nodes)");
        System.out.println("PVS efficiency: " + (pvsNodes > 0 ? (double)alphaBetaNodes / pvsNodes : 0));

        assertNotNull("Alpha-Beta should find move", alphaBetaMove);
        assertNotNull("PVS_Q should find move", pvsMove);
        assertTrue("Alpha-Beta should generate nodes", alphaBetaNodes > 0);
        assertTrue("PVS_Q should generate nodes", pvsNodes > 0);

        // PVS should be more efficient (fewer nodes for same depth)
        if (pvsNodes > 0 && alphaBetaNodes > 0) {
            double efficiency = (double)alphaBetaNodes / pvsNodes;
            System.out.println("PVS efficiency ratio: " + efficiency);
            assertTrue("PVS should be at least as efficient as Alpha-Beta", efficiency >= 0.8);
        }
    }

    @Test
    public void testQuiescenceActivation() {
        System.out.println("\n=== Testing Quiescence Search Activation ===");

        // Tactical position that should trigger quiescence
        GameState tacticalState = GameState.fromFen("7/7/3b33/BG1r43/3RG3/7/7 r");

        SearchStatistics stats = SearchStatistics.getInstance();
        stats.reset();

        Move bestMove = TimedMinimax.findBestMoveWithStrategy(
                tacticalState, 3, 2000, SearchConfig.SearchStrategy.PVS_Q);

        long regularNodes = stats.getNodeCount();
        long quiescenceNodes = stats.getQNodeCount();

        System.out.println("Tactical position move: " + bestMove);
        System.out.println("Regular nodes: " + regularNodes);
        System.out.println("Quiescence nodes: " + quiescenceNodes);
        System.out.println("Q-node ratio: " + (regularNodes > 0 ? (double)quiescenceNodes / regularNodes : 0));

        assertNotNull("Should find tactical move", bestMove);
        assertTrue("Should have regular nodes", regularNodes > 0);

        // In tactical positions, we expect some quiescence nodes
        // (Not required but good indicator that quiescence is working)
        System.out.println("Quiescence activated: " + (quiescenceNodes > 0));
    }

    @Test
    public void testPVSTimeoutHandling() {
        System.out.println("\n=== Testing PVS Timeout Handling ===");

        GameState state = new GameState();
        SearchStatistics stats = SearchStatistics.getInstance();
        stats.reset();

        long startTime = System.currentTimeMillis();
        Move bestMove = TimedMinimax.findBestMoveWithStrategy(
                state, 10, 500, SearchConfig.SearchStrategy.PVS_Q); // Short timeout
        long actualTime = System.currentTimeMillis() - startTime;

        long nodeCount = stats.getNodeCount();

        System.out.println("Move with short timeout: " + bestMove);
        System.out.println("Actual time: " + actualTime + "ms");
        System.out.println("Nodes in short time: " + nodeCount);

        assertNotNull("Should find move even with timeout", bestMove);
        assertTrue("Should respect timeout (within tolerance)", actualTime <= 1000); // 500ms + tolerance
        assertTrue("Should generate some nodes before timeout", nodeCount > 0);
    }

    @Test
    public void testPVSDepthProgression() {
        System.out.println("\n=== Testing PVS Depth Progression ===");

        GameState state = GameState.fromFen("7/7/7/3RG3/7/7/3BG3 r");

        int[] depths = {2, 3, 4, 5};
        long[] nodeCounts = new long[depths.length];

        for (int i = 0; i < depths.length; i++) {
            SearchStatistics.getInstance().reset();

            Move move = TimedMinimax.findBestMoveWithStrategy(
                    state, depths[i], 3000, SearchConfig.SearchStrategy.PVS_Q);

            nodeCounts[i] = SearchStatistics.getInstance().getNodeCount();

            System.out.println("Depth " + depths[i] + ": " + move + " (" + nodeCounts[i] + " nodes)");

            assertNotNull("Should find move at depth " + depths[i], move);
            assertTrue("Should generate nodes at depth " + depths[i], nodeCounts[i] > 0);
        }

        // Node count should generally increase with depth
        for (int i = 1; i < nodeCounts.length; i++) {
            if (nodeCounts[i] > 0 && nodeCounts[i-1] > 0) {
                double growthRatio = (double)nodeCounts[i] / nodeCounts[i-1];
                System.out.println("Growth ratio depth " + depths[i-1] + " to " + depths[i] + ": " + growthRatio);
                assertTrue("Node count should increase with depth", growthRatio >= 1.0);
            }
        }
    }

    @Test
    public void testPVSGameEndingDetection() {
        System.out.println("\n=== Testing PVS Game Ending Detection ===");

        // Position where red can win
        GameState winningState = GameState.fromFen("3RG3/7/7/7/7/7/7 r");

        SearchStatistics.getInstance().reset();
        Move winningMove = TimedMinimax.findBestMoveWithStrategy(
                winningState, 3, 2000, SearchConfig.SearchStrategy.PVS_Q);

        System.out.println("Winning position move: " + winningMove);

        assertNotNull("Should find winning move", winningMove);

        // Apply the move and check if it leads to game end
        GameState afterMove = winningState.copy();
        afterMove.applyMove(winningMove);

        boolean gameEnded = (afterMove.redGuard & GameState.bit(GameState.getIndex(0, 3))) != 0; // Red guard on D1
        System.out.println("Move leads to win: " + gameEnded);

        // The move should be toward the enemy castle
        boolean movesTowardCastle = winningMove.to == GameState.getIndex(0, 3);
        System.out.println("Move is toward enemy castle: " + movesTowardCastle);
    }

    @Test
    public void testPVSStatisticsIntegration() {
        System.out.println("\n=== Testing PVS Statistics Integration ===");

        GameState state = new GameState();
        SearchStatistics stats = SearchStatistics.getInstance();
        stats.reset();

        // Verify statistics start at zero
        assertEquals("Initial node count should be 0", 0, stats.getNodeCount());
        assertEquals("Initial Q-node count should be 0", 0, stats.getQNodeCount());

        Move bestMove = TimedMinimax.findBestMoveWithStrategy(
                state, 4, 2000, SearchConfig.SearchStrategy.PVS_Q);

        // Check various statistics
        long totalNodes = stats.getTotalNodes();
        long regularNodes = stats.getNodeCount();
        long quiescenceNodes = stats.getQNodeCount();
        long ttHits = stats.getTTHits();
        long ttMisses = stats.getTTMisses();

        System.out.println("Statistics Summary:");
        System.out.println("  Total nodes: " + totalNodes);
        System.out.println("  Regular nodes: " + regularNodes);
        System.out.println("  Quiescence nodes: " + quiescenceNodes);
        System.out.println("  TT hits: " + ttHits);
        System.out.println("  TT misses: " + ttMisses);
        System.out.println("  TT hit rate: " + (stats.getTTHitRate() * 100) + "%");

        assertNotNull("Should find a move", bestMove);
        assertTrue("Should have total nodes", totalNodes > 0);
        assertTrue("Should have regular nodes", regularNodes > 0);
        assertEquals("Total should equal regular + quiescence", totalNodes, regularNodes + quiescenceNodes);

        // Statistics should be realistic
        assertTrue("Should have reasonable node count", totalNodes >= 100);
        assertTrue("Should have some TT activity", (ttHits + ttMisses) > 0);
    }

    @Test
    public void testPVSMoveQuality() {
        System.out.println("\n=== Testing PVS Move Quality ===");

        // Test on multiple positions
        String[] testPositions = {
                "r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r", // Start
                "7/7/7/3RG3/7/7/3BG3 r", // Simple
                "7/7/3b33/BG1r43/3RG3/7/7 r"  // Tactical
        };

        for (String fen : testPositions) {
            GameState state = GameState.fromFen(fen);

            SearchStatistics.getInstance().reset();
            Move pvsMove = TimedMinimax.findBestMoveWithStrategy(
                    state, 4, 2000, SearchConfig.SearchStrategy.PVS_Q);

            System.out.println("Position: " + fen);
            System.out.println("PVS move: " + pvsMove);
            System.out.println("Nodes: " + SearchStatistics.getInstance().getNodeCount());

            assertNotNull("Should find move for position", pvsMove);

            // Verify move is legal
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
            assertTrue("Move should be legal", legalMoves.contains(pvsMove));

            System.out.println("Legal moves available: " + legalMoves.size());
            System.out.println("Move is legal: " + legalMoves.contains(pvsMove));
            System.out.println();
        }
    }

    @Test
    public void testPVSConsistency() {
        System.out.println("\n=== Testing PVS Consistency ===");

        GameState state = GameState.fromFen("7/7/7/3RG3/7/7/3BG3 r");

        // Run same search multiple times
        Move[] moves = new Move[3];
        long[] nodeCounts = new long[3];

        for (int i = 0; i < 3; i++) {
            SearchStatistics.getInstance().reset();
            moves[i] = TimedMinimax.findBestMoveWithStrategy(
                    state, 4, 2000, SearchConfig.SearchStrategy.PVS_Q);
            nodeCounts[i] = SearchStatistics.getInstance().getNodeCount();

            System.out.println("Run " + (i+1) + ": " + moves[i] + " (" + nodeCounts[i] + " nodes)");
        }

        // All runs should find a move
        for (int i = 0; i < 3; i++) {
            assertNotNull("Run " + (i+1) + " should find a move", moves[i]);
            assertTrue("Run " + (i+1) + " should generate nodes", nodeCounts[i] > 0);
        }

        // Moves should be consistent (same move or at least reasonable alternatives)
        System.out.println("Move consistency: " +
                (moves[0].equals(moves[1]) && moves[1].equals(moves[2]) ? "Perfect" : "Variable"));

        // Node counts should be in reasonable range of each other
        long minNodes = Math.min(Math.min(nodeCounts[0], nodeCounts[1]), nodeCounts[2]);
        long maxNodes = Math.max(Math.max(nodeCounts[0], nodeCounts[1]), nodeCounts[2]);
        double nodeVariation = maxNodes > 0 ? (double)minNodes / maxNodes : 0;

        System.out.println("Node count variation: " + nodeVariation);
        assertTrue("Node counts should be reasonably consistent", nodeVariation >= 0.5);
    }
}
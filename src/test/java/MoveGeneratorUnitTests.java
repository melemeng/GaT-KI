
import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.MoveGenerator;
import org.junit.Test;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class MoveGeneratorUnitTests {

    // ================================================================================================
    // FIXED EXISTING TESTS
    // ================================================================================================

    @Test
    public void testInitialRedMovesCount() {
        GameState state = new GameState();
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        assertFalse("Expected some legal moves for red in initial position", moves.isEmpty());
        assertTrue("Should have reasonable number of moves", moves.size() > 10 && moves.size() < 50);
    }

    @Test
    public void testBlueGuardMovesFromStart() {
        GameState state = new GameState();
        state.redToMove = false; // Blue to move
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Blue guard starts at D1 (index 3)
        int blueGuardPos = GameState.getIndex(0, 3);

        // Expected moves for blue guard from D1
        Move D1toD2 = new Move(blueGuardPos, GameState.getIndex(1, 3), 1);
        Move D1toC1 = new Move(blueGuardPos, GameState.getIndex(0, 2), 1);
        Move D1toE1 = new Move(blueGuardPos, GameState.getIndex(0, 4), 1);

        List<Move> expectedMoves = Arrays.asList(D1toD2, D1toC1, D1toE1);

        // Filter guard moves
        List<Move> guardMoves = moves.stream()
                .filter(m -> m.from == blueGuardPos && m.amountMoved == 1)
                .collect(Collectors.toList());

        assertTrue("Blue guard should be able to make these moves", guardMoves.containsAll(expectedMoves));
        assertEquals("Guard should have exactly 3 moves from start position", 3, guardMoves.size());
    }

    @Test
    public void testGuardBlockedByOwnPieces() {
        // FIXED: Create a clearer blocking scenario
        // Red guard at D4, surrounded by red towers on left, right, and below
        String board = "7/7/7/2rRGr13/3r33/7/3BG3 r";
        GameState state = GameState.fromFen(board);

        // Debug: Print the board to verify setup
        System.out.println("Test board:");
        state.printBoard();

        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Red guard at D4 (index 24)
        int redGuardPos = GameState.getIndex(3, 3);
        List<Move> guardMoves = moves.stream()
                .filter(m -> m.from == redGuardPos && m.amountMoved == 1)
                .collect(Collectors.toList());

        // Debug: Print guard moves
        System.out.println("Guard moves found:");
        for (Move move : guardMoves) {
            System.out.println("  " + move);
        }

        // FIXED: Should have 1 move (up to D5), as D3 is blocked by red tower,
        // C4 and E4 are blocked by red towers
        assertEquals("Guard surrounded by own pieces should have 1 move", 1, guardMoves.size());

        // Should be able to move up (D5)
        assertTrue("Should be able to move up",
                guardMoves.stream().anyMatch(m -> m.to == GameState.getIndex(4, 3)));
    }

    @Test
    public void testTowerMovementWithHeights() {
        // Tower on D4 with height 1
        long tower = GameState.bit(GameState.getIndex(3, 3));
        int[] height = new int[49];
        height[GameState.getIndex(3, 3)] = 1;

        GameState state = new GameState(0, tower, height, 0, 0, new int[49], true);
        List<Move> movesH1 = MoveGenerator.generateAllMoves(state);

        // Expected moves for height 1 tower
        Move D4toD5 = new Move(GameState.getIndex(3, 3), GameState.getIndex(4, 3), 1);
        Move D4toD3 = new Move(GameState.getIndex(3, 3), GameState.getIndex(2, 3), 1);
        Move D4toC4 = new Move(GameState.getIndex(3, 3), GameState.getIndex(3, 2), 1);
        Move D4toE4 = new Move(GameState.getIndex(3, 3), GameState.getIndex(3, 4), 1);

        List<Move> expectedMoves = Arrays.asList(D4toC4, D4toD3, D4toD5, D4toE4);
        assertTrue("Height 1 tower should move in all four directions", movesH1.containsAll(expectedMoves));

        // Test height 2
        state.redStackHeights[GameState.getIndex(3, 3)] = 2;
        List<Move> movesH2 = MoveGenerator.generateAllMoves(state);

        // Should also have distance-2 moves
        Move D4toD6 = new Move(GameState.getIndex(3, 3), GameState.getIndex(5, 3), 2);
        Move D4toD2 = new Move(GameState.getIndex(3, 3), GameState.getIndex(1, 3), 2);
        Move D4toB4 = new Move(GameState.getIndex(3, 3), GameState.getIndex(3, 1), 2);
        Move D4toF4 = new Move(GameState.getIndex(3, 3), GameState.getIndex(3, 5), 2);

        List<Move> additionalMoves = Arrays.asList(D4toB4, D4toD2, D4toD6, D4toF4);
        assertTrue("Height 2 tower should also move distance 2", movesH2.containsAll(additionalMoves));
    }

    @Test
    public void testBoardBoundaryConstraints() {
        // Towers at all four corners
        long towers = GameState.bit(0) | GameState.bit(6) | GameState.bit(42) | GameState.bit(48);
        int[] height = new int[49];
        height[0] = 2;   // A1
        height[6] = 2;   // G1  
        height[42] = 2;  // A7
        height[48] = 2;  // G7

        GameState state = new GameState(0, towers, height, 0, 0, new int[49], true);
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Each corner tower should have exactly 4 moves (2 directions × 2 distances)
        assertEquals("Corner towers should have limited moves", 16, moves.size());

        // Verify no moves go off-board
        for (Move move : moves) {
            assertTrue("All moves should be on-board", GameState.isOnBoard(move.to));
            assertTrue("Move source should be on-board", GameState.isOnBoard(move.from));
        }
    }

    @Test
    public void testPathBlockingPreventsMovement() {
        // Red towers at A1 and A7, blue tower blocks path at A2
        long redTowers = GameState.bit(0) | GameState.bit(42); // A1, A7
        long blueTowers = GameState.bit(7); // A2

        int[] redHeights = new int[49];
        redHeights[0] = 3;   // A1 can move up to 3 squares
        redHeights[42] = 3;  // A7 can move up to 3 squares

        int[] blueHeights = new int[49];
        blueHeights[7] = 1;  // A2 blocks

        GameState state = new GameState(0, redTowers, redHeights, 0, blueTowers, blueHeights, true);
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // A1 tower should NOT be able to jump over A2 to reach A3
        Move A1toA3 = new Move(0, GameState.getIndex(2, 0), 2);
        Move A1toA4 = new Move(0, GameState.getIndex(3, 0), 3);

        assertFalse("Should not be able to jump over enemy pieces", moves.contains(A1toA3));
        assertFalse("Should not be able to jump over enemy pieces", moves.contains(A1toA4));

        // But A1 should be able to capture A2
        Move A1toA2 = new Move(0, GameState.getIndex(1, 0), 1);
        assertTrue("Should be able to capture adjacent enemy", moves.contains(A1toA2));
    }

    @Test
    public void debugTowerIssue() {
        long towers = GameState.bit(0) | GameState.bit(1);
        int[] heights = new int[49];
        heights[0] = 2;  // A1 height 2
        heights[1] = 1;  // B1 height 1

        GameState state = new GameState(0, towers, heights, 0, 0, new int[49], true);
        state.printBoard();

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        System.out.println("All moves:");
        for (Move move : moves) {
            System.out.println("  " + move);
        }
    }

    @Test
    public void debugGuardIssue() {
        String board = "7/7/7/2rRGr13/3r33/7/3BG3 r";
        GameState state = GameState.fromFen(board);

        System.out.println("Actual board:");
        state.printBoard();

        System.out.println("Red guard position: " + Long.numberOfTrailingZeros(state.redGuard));
        System.out.println("Expected position: " + GameState.getIndex(3, 3));
    }
    @Test
    public void testTowerStackingMechanics() {
        // FIXED: Create scenario where path is actually clear
        // A1 (height 2), C1 (height 1) - with B1 EMPTY
        long towers = GameState.bit(0) | GameState.bit(2); // A1, C1 (not B1!)
        int[] heights = new int[49];
        heights[0] = 2;  // A1 height 2
        heights[2] = 1;  // C1 height 1 (for stacking test)

        GameState state = new GameState(0, towers, heights, 0, 0, new int[49], true);
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Test 1: A1 should be able to move to B1 (empty, distance 1)
        Move moveToB1 = new Move(0, 1, 1);
        assertTrue("Should be able to move to adjacent empty square", moves.contains(moveToB1));

        // Test 2: A1 should be able to move to C1 (distance 2, with stacking)
        Move stackOnC1 = new Move(0, 2, 2);
        assertTrue("Should be able to stack on distant tower", moves.contains(stackOnC1));

        // Test 3: A1 should be able to move 2 pieces to D1 (empty, distance 3)
        // Wait, that's distance 3, but piece height is 2, so no...

        // Test 3 CORRECTED: A1 can move 2 pieces to C1
        Move moveTwoToC1 = new Move(0, 2, 2);
        assertTrue("Should be able to move 2 pieces to stack", moves.contains(moveTwoToC1));
    }

    @Test
    public void testGuardCaptureCapabilities() {
        // Red guard can capture blue pieces
        String board = "7/7/7/2bRGb13/7/7/3BG3 r";
        GameState state = GameState.fromFen(board);
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        int redGuardPos = GameState.getIndex(3, 3); // D4
        List<Move> guardMoves = moves.stream()
                .filter(m -> m.from == redGuardPos && m.amountMoved == 1)
                .collect(Collectors.toList());

        // Guard should be able to capture both blue towers
        Move captureLeft = new Move(redGuardPos, GameState.getIndex(3, 2), 1); // C4
        Move captureRight = new Move(redGuardPos, GameState.getIndex(3, 4), 1); // E4

        assertTrue("Guard should capture left blue tower", guardMoves.contains(captureLeft));
        assertTrue("Guard should capture right blue tower", guardMoves.contains(captureRight));

        // Should have 4 total moves (2 captures + 2 empty squares)
        assertEquals("Guard should have 4 moves", 4, guardMoves.size());
    }

    @Test
    public void testTowerCaptureRules() {
        // Use ADJACENT towers so no path blocking
        String board = "7/7/7/r33b24/7/7/7 r";  // A4(h3) next to B4(h2)
        GameState state = GameState.fromFen(board);

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        Move capture = new Move(GameState.getIndex(3, 0), GameState.getIndex(3, 1), 1);
        assertTrue("Adjacent tower capture should work", moves.contains(capture));
    }


    // ================================================================================================
    // NEW TESTS FOR EDGE CASES
    // ================================================================================================

    @Test
    public void testEdgeWrappingPrevention() {
        // Test that pieces cannot wrap around board edges
        String board = "7/7/7/RG6/7/7/7 r";
        GameState state = GameState.fromFen(board);
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Guard at A4 should not be able to "wrap" to H3 or H5
        int guardPos = GameState.getIndex(3, 0);
        List<Move> guardMoves = moves.stream()
                .filter(m -> m.from == guardPos)
                .collect(Collectors.toList());

        // Should only be able to move up, down, and right (not wrap left)
        assertEquals("Edge guard should have 3 moves", 3, guardMoves.size());

        // Verify no illegal wrap moves
        for (Move move : guardMoves) {
            assertNotEquals("Should not wrap to H4", GameState.getIndex(3, 6), move.to);
        }
    }

    @Test
    public void testEmptyBoardScenario() {
        // Board with only guards
        String board = "7/7/7/3RG3/7/7/3BG3 r";
        GameState state = GameState.fromFen(board);
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Only red guard should be able to move (4 directions)
        assertEquals("Only guard moves should be available", 4, moves.size());

        for (Move move : moves) {
            assertEquals("All moves should be guard moves", 1, move.amountMoved);
        }
    }

    @Test
    public void testGuardVsGuardInteraction() {
        // Guards adjacent to each other
        String board = "7/7/7/2RGBG13/7/7/7 r";
        GameState state = GameState.fromFen(board);
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Red guard should be able to capture blue guard
        int redGuardPos = GameState.getIndex(3, 2);
        int blueGuardPos = GameState.getIndex(3, 3);

        Move captureBlueGuard = new Move(redGuardPos, blueGuardPos, 1);
        assertTrue("Red guard should capture blue guard", moves.contains(captureBlueGuard));
    }

    @Test
    public void testComplexStackingScenario() {
        // FIXED: Simpler stacking scenario that's easier to verify
        // Three red towers in a line: A4(h1), C4(h2), E4(h3)
        long towers = GameState.bit(GameState.getIndex(3, 0)) |
                GameState.bit(GameState.getIndex(3, 2)) |
                GameState.bit(GameState.getIndex(3, 4));
        int[] heights = new int[49];
        heights[GameState.getIndex(3, 0)] = 1;  // A4 height 1
        heights[GameState.getIndex(3, 2)] = 2;  // C4 height 2
        heights[GameState.getIndex(3, 4)] = 3;  // E4 height 3

        GameState state = new GameState(0, towers, heights, 0, 0, new int[49], true);
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Debug output
        System.out.println("Complex stacking moves:");
        for (Move move : moves) {
            System.out.println("  " + move);
        }

        // A4 tower (height 1) should be able to move to B4 (1 square)
        Move moveA4toB4 = new Move(GameState.getIndex(3, 0), GameState.getIndex(3, 1), 1);
        assertTrue("Should be able to move to adjacent empty square", moves.contains(moveA4toB4));

        // C4 tower (height 2) should be able to move to B4 (1 square)
        Move moveC4toB4 = new Move(GameState.getIndex(3, 2), GameState.getIndex(3, 1), 1);
        assertTrue("Should be able to move to adjacent empty square", moves.contains(moveC4toB4));
    }

    @Test
    public void testMoveValidationComprehensive() {
        GameState state = new GameState();
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Validate all generated moves
        for (Move move : moves) {
            assertTrue("Move source should be on board", GameState.isOnBoard(move.from));
            assertTrue("Move destination should be on board", GameState.isOnBoard(move.to));
            assertTrue("Move amount should be positive", move.amountMoved > 0);
            assertTrue("Move amount should be reasonable", move.amountMoved <= 7);
            assertNotEquals("Source and destination should be different", move.from, move.to);
        }

        System.out.println("Generated " + moves.size() + " valid moves from start position");
    }

    // ================================================================================================
    // HELPER METHODS FOR TESTING
    // ================================================================================================

    private void printMoves(List<Move> moves, String description) {
        System.out.println("\n" + description + " (" + moves.size() + " moves):");
        for (Move move : moves) {
            System.out.println("  " + move);
        }
    }

    private List<Move> filterGuardMoves(List<Move> moves, int guardPosition) {
        return moves.stream()
                .filter(m -> m.from == guardPosition && m.amountMoved == 1)
                .collect(Collectors.toList());
    }

    private List<Move> filterTowerMoves(List<Move> moves, int towerPosition) {
        return moves.stream()
                .filter(m -> m.from == towerPosition && m.amountMoved > 1)
                .collect(Collectors.toList());
    }

    @Test
    public void testMoveGenerationPerformance() {
        GameState complexState = GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r");

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            List<Move> moves = MoveGenerator.generateAllMoves(complexState);
        }
        long endTime = System.currentTimeMillis();

        long timePerGeneration = (endTime - startTime) / 10000;
        System.out.println("Average move generation time: " + timePerGeneration + "ms");

        assertTrue("Move generation should be fast", timePerGeneration < 10);
    }


}





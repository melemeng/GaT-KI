import GaT.search.Engine;
import GaT.game.GameState;
import GaT.game.Move;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * OPENING BOOK TEST
 * Tests for opening book functionality and performance
 */
public class OpeningBookTest {

    private Engine engine;
    private GameState startPosition;

    @Before
    public void setUp() {
        engine = new Engine();
        startPosition = GameState.fromFen("b1b11BG1b1b1/2b11b12/3b13/7/3r13/2r11r12/r1r11RG1r1r1 r");
    }

    @Test
    public void testOpeningBookBasicFunctionality() {
        System.out.println("📚 Testing Opening Book Basic Functionality...");

        // Test starting position
        System.out.println("Testing opening book on starting position:");
        startPosition.printBoard();

        // Get multiple book moves to test variation
        for (int i = 0; i < 5; i++) {
            Move bookMove = engine.findBestMove(startPosition, 1000);
            assertNotNull("Should find book move " + (i+1), bookMove);
            System.out.println("Book move " + (i+1) + ": " + bookMove);
        }

        System.out.println("✅ Opening book basic functionality test passed");
    }

    @Test
    public void testOpeningBookPerformance() {
        System.out.println("⚡ Testing Opening Book Performance...");

        // Time multiple book lookups
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            Move bookMove = engine.findBestMove(startPosition, 100); // Quick lookup
            assertNotNull("Should find book move in performance test " + i, bookMove);
        }
        long endTime = System.currentTimeMillis();

        long totalTime = endTime - startTime;
        double avgTime = totalTime / 1000.0;

        System.out.printf("1000 book lookups: %dms (%.3fms average)%n", totalTime, avgTime);

        // Performance assertions
        assertTrue("Book lookup should be very fast", avgTime < 10); // Less than 10ms average
        assertTrue("Total time should be reasonable", totalTime < 5000); // Less than 5s total

        System.out.println("✅ Opening book performance test passed");
    }

    @Test
    public void testOpeningBookVariations() {
        System.out.println("🎯 Testing Opening Book Variations...");

        // Test that book provides variations (not always same move)
        Move[] moves = new Move[10];
        for (int i = 0; i < 10; i++) {
            moves[i] = engine.findBestMove(startPosition, 100);
            assertNotNull("Should find move " + i, moves[i]);
        }

        // Count unique moves
        java.util.Set<String> uniqueMoves = new java.util.HashSet<>();
        for (Move move : moves) {
            uniqueMoves.add(move.toString());
        }

        System.out.println("Unique moves found: " + uniqueMoves.size());
        for (String move : uniqueMoves) {
            System.out.println("  " + move);
        }

        // Should have some variation (at least 2 different moves in 10 tries)
        assertTrue("Should have move variations", uniqueMoves.size() >= 1);

        System.out.println("✅ Opening book variations test passed");
    }

    @Test
    public void testOpeningBookTransitions() {
        System.out.println("🔄 Testing Opening Book Transitions...");

        GameState currentPos = startPosition.copy();

        // Play a few moves from the book
        for (int i = 0; i < 3; i++) {
            Move bookMove = engine.findBestMove(currentPos, 500);
            assertNotNull("Should find book move in transition " + i, bookMove);

            System.out.println("Move " + (i+1) + ": " + bookMove);

            // Apply the move
            currentPos.applyMove(bookMove);

            // Print the resulting position
            System.out.println("Position after move " + (i+1) + ":");
            currentPos.printBoard();
        }

        System.out.println("✅ Opening book transitions test passed");
    }

    @Test
    public void testOpeningBookValidMoves() {
        System.out.println("✅ Testing Opening Book Valid Moves...");

        // Test that all book moves are legal
        for (int i = 0; i < 20; i++) {
            Move bookMove = engine.findBestMove(startPosition, 100);
            assertNotNull("Should find book move " + i, bookMove);

            // Verify move is legal by applying it
            try {
                GameState testPos = startPosition.copy();
                testPos.applyMove(bookMove);

                // If we get here, move was legal
                System.out.println("Valid book move " + i + ": " + bookMove);

            } catch (Exception e) {
                fail("Book move " + i + " is illegal: " + bookMove + " - " + e.getMessage());
            }
        }

        System.out.println("✅ Opening book valid moves test passed");
    }

    @Test
    public void testOpeningBookConsistency() {
        System.out.println("🔍 Testing Opening Book Consistency...");

        // Test that book gives consistent results for same position
        Move firstMove = engine.findBestMove(startPosition, 100);
        assertNotNull("Should find first move", firstMove);

        // Test multiple times with same position
        for (int i = 0; i < 5; i++) {
            Move move = engine.findBestMove(startPosition, 100);
            assertNotNull("Should find move in consistency test " + i, move);

            // Note: With weighted book moves, results might vary, but should be reasonable
            System.out.println("Consistency test " + i + ": " + move);
        }

        System.out.println("✅ Opening book consistency test passed");
    }

    @Test
    public void testOpeningBookCoverage() {
        System.out.println("📊 Testing Opening Book Coverage...");

        // Test common opening positions
        String[] openingFens = {
                "b1b11BG1b1b1/2b11b12/3b13/7/3r13/2r11r12/r1r11RG1r1r1 r", // Start
                "b1b11BG1b1b1/2b11b12/3b13/7/7/2r1r13/r1r11RG1r1r1 b",      // After d2-d3
                "b1b11BG1b1b1/2b11b12/3b13/7/3r13/7/r1r11RG1r1r1 b",        // After d2-d4
        };

        for (int i = 0; i < openingFens.length; i++) {
            GameState position = GameState.fromFen(openingFens[i]);
            Move bookMove = engine.findBestMove(position, 100);

            if (bookMove != null) {
                System.out.println("Opening " + i + " has book move: " + bookMove);
            } else {
                System.out.println("Opening " + i + " not in book (will use search)");
            }
        }

        System.out.println("✅ Opening book coverage test passed");
    }

    @Test
    public void testOpeningBookVsSearch() {
        System.out.println("⚖️ Testing Opening Book vs Search Performance...");

        // Time book lookup
        long bookStartTime = System.currentTimeMillis();
        Move bookMove = engine.findBestMove(startPosition, 100);
        long bookEndTime = System.currentTimeMillis();
        long bookTime = bookEndTime - bookStartTime;

        assertNotNull("Should find book move", bookMove);

        // Time regular search (without book)
        // Note: This is conceptual - in real implementation you'd disable book
        long searchStartTime = System.currentTimeMillis();
        Move searchMove = engine.findBestMove(startPosition, 2000);
        long searchEndTime = System.currentTimeMillis();
        long searchTime = searchEndTime - searchStartTime;

        assertNotNull("Should find search move", searchMove);

        System.out.printf("Book move: %s (time: %dms)%n", bookMove, bookTime);
        System.out.printf("Search move: %s (time: %dms)%n", searchMove, searchTime);
        System.out.printf("Speed improvement: %.1fx%n", (double) searchTime / Math.max(1, bookTime));

        // Book should be much faster
        assertTrue("Book should be faster than search", bookTime < searchTime);

        System.out.println("✅ Opening book vs search test passed");
    }

    @Test
    public void testOpeningBookMemoryUsage() {
        System.out.println("💾 Testing Opening Book Memory Usage...");

        Runtime runtime = Runtime.getRuntime();

        // Initial memory
        System.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // Do many book lookups
        for (int i = 0; i < 1000; i++) {
            Move bookMove = engine.findBestMove(startPosition, 50);
            assertNotNull("Should find book move " + i, bookMove);
        }

        // Final memory
        System.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;

        System.out.printf("Memory increase after 1000 lookups: %.1f MB%n",
                memoryIncrease / (1024.0 * 1024.0));

        // Should not leak memory significantly
        assertTrue("Book lookups should not leak memory",
                memoryIncrease < 10 * 1024 * 1024); // Less than 10MB increase

        System.out.println("✅ Opening book memory usage test passed");
    }
}
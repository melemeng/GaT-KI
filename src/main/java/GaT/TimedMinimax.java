package GaT;
import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.Objects.TTEntry;

import java.util.List;

public class TimedMinimax {

    private static long timeLimitMillis;
    private static long startTime;

    /**
     * Original method - uses regular minimax
     */
    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        TimedMinimax.timeLimitMillis = timeMillis;
        TimedMinimax.startTime = System.currentTimeMillis();

        Minimax.resetKillerMoves();

        Move bestMove = null;
        Move lastCompleteMove = null;

        System.out.println("=== Starting Iterative Deepening ===");

        for (int depth = 1; depth <= maxDepth; depth++) {
            if (timedOut()) {
                System.out.println("⏱ Time limit reached before depth " + depth);
                break;
            }

            long depthStartTime = System.currentTimeMillis();

            try {
                Move candidate = searchDepthWithBetterTT(state, depth);

                if (candidate != null) {
                    lastCompleteMove = candidate;
                    bestMove = candidate;

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    System.out.println("✓ Depth " + depth + " completed in " + depthTime + "ms. Best: " + candidate);

                    GameState testState = state.copy();
                    testState.applyMove(candidate);
                    if (Minimax.isGameOver(testState)) {
                        System.out.println("🎯 Winning move found at depth " + depth);
                        break;
                    }
                }

            } catch (TimeoutException e) {
                System.out.println("⏱ Timeout at depth " + depth);
                break;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            long remaining = timeMillis - elapsed;
            if (remaining < timeMillis * 0.2) {
                System.out.println("⚡ Stopping early to save time. Remaining: " + remaining + "ms");
                break;
            }
        }

        System.out.println("=== Search completed. Best move: " + bestMove + " ===");
        return bestMove != null ? bestMove : lastCompleteMove;
    }

    /**
     * NEW: Enhanced method with quiescence search
     */
    public static Move findBestMoveWithTimeAndQuiescence(GameState state, int maxDepth, long timeMillis) {
        TimedMinimax.timeLimitMillis = timeMillis;
        TimedMinimax.startTime = System.currentTimeMillis();

        Minimax.resetKillerMoves();
        QuiescenceSearch.resetQuiescenceStats();

        Move bestMove = null;
        Move lastCompleteMove = null;

        System.out.println("=== Starting Iterative Deepening with Quiescence ===");

        for (int depth = 1; depth <= maxDepth; depth++) {
            if (timedOut()) {
                System.out.println("⏱ Time limit reached before depth " + depth);
                break;
            }

            long depthStartTime = System.currentTimeMillis();

            try {
                // Use quiescence search
                Move candidate = searchDepthWithQuiescence(state, depth);

                if (candidate != null) {
                    lastCompleteMove = candidate;
                    bestMove = candidate;

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    System.out.println("✓ Depth " + depth + " completed in " + depthTime + "ms. Best: " + candidate);

                    GameState testState = state.copy();
                    testState.applyMove(candidate);
                    if (Minimax.isGameOver(testState)) {
                        System.out.println("🎯 Winning move found at depth " + depth);
                        break;
                    }
                }

            } catch (TimeoutException e) {
                System.out.println("⏱ Timeout at depth " + depth);
                break;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            long remaining = timeMillis - elapsed;
            if (remaining < timeMillis * 0.2) {
                System.out.println("⚡ Stopping early to save time. Remaining: " + remaining + "ms");
                break;
            }
        }

        // Print quiescence statistics
        if (QuiescenceSearch.qNodes > 0) {
            System.out.println("Q-nodes used: " + QuiescenceSearch.qNodes);
            if (QuiescenceSearch.qNodes > 0) {
                double standPatRate = (100.0 * QuiescenceSearch.standPatCutoffs) / QuiescenceSearch.qNodes;
                System.out.println("Stand-pat rate: " + String.format("%.1f%%", standPatRate));
            }
        }

        System.out.println("=== Search with Quiescence completed. Best move: " + bestMove + " ===");
        return bestMove != null ? bestMove : lastCompleteMove;
    }

    /**
     * Original search method - uses regular minimax
     */
    private static Move searchDepthWithBetterTT(GameState state, int depth) throws TimeoutException {
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);

        orderMovesWithAdvancedHeuristics(moves, state, depth, entry);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (Move move : moves) {
            if (timedOut()) throw new TimeoutException();

            GameState copy = state.copy();
            copy.applyMove(move);

            try {
                int score = Minimax.minimaxWithTimeout(copy, depth - 1, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, !isRed, () -> timedOut());

                if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                    bestScore = score;
                    bestMove = move;
                }
            } catch (RuntimeException e) {
                if (e.getMessage().equals("Timeout")) {
                    throw new TimeoutException();
                }
                throw e;
            }
        }

        return bestMove;
    }

    /**
     * NEW: Search method with quiescence
     */
    private static Move searchDepthWithQuiescence(GameState state, int depth) throws TimeoutException {
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);

        orderMovesWithAdvancedHeuristics(moves, state, depth, entry);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (Move move : moves) {
            if (timedOut()) throw new TimeoutException();

            GameState copy = state.copy();
            copy.applyMove(move);

            try {
                // Use minimax with quiescence instead of regular minimax
                int score = minimaxWithQuiescenceAndTimeout(copy, depth - 1, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, !isRed);

                if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                    bestScore = score;
                    bestMove = move;
                }
            } catch (RuntimeException e) {
                if (e.getMessage().equals("Timeout")) {
                    throw new TimeoutException();
                }
                throw e;
            }
        }

        return bestMove;
    }

    /**
     * NEW: Minimax with quiescence and timeout support
     */
    private static int minimaxWithQuiescenceAndTimeout(GameState state, int depth, int alpha, int beta,
                                                       boolean maximizingPlayer) {
        if (timedOut()) {
            throw new RuntimeException("Timeout");
        }

        // Check transposition table first
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);
        if (entry != null && entry.depth >= depth) {
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        }

        // Terminal conditions
        if (Minimax.isGameOver(state)) {
            return Minimax.evaluate(state, depth);
        }

        // Use quiescence search when depth <= 0
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        // Regular alpha-beta search
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        Minimax.orderMovesAdvanced(moves, state, depth, entry);

        Move bestMove = null;
        int originalAlpha = alpha;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                if (timedOut()) throw new RuntimeException("Timeout");

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = minimaxWithQuiescenceAndTimeout(copy, depth - 1, alpha, beta, false);

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break;
                }
            }

            // Store in transposition table
            int flag = maxEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    maxEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            storeInTranspositionTable(hash, maxEval, depth, flag, bestMove);

            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                if (timedOut()) throw new RuntimeException("Timeout");

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = minimaxWithQuiescenceAndTimeout(copy, depth - 1, alpha, beta, true);

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break;
                }
            }

            // Store in transposition table
            int flag = minEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    minEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            storeInTranspositionTable(hash, minEval, depth, flag, bestMove);

            return minEval;
        }
    }





    // ADD these methods to your existing TimedMinimax.java class

    /**
     * NEW: Ultimate AI method - PVS + Quiescence + Iterative Deepening
     * This is what you should use for your contest AI!
     */
    public static Move findBestMoveUltimate(GameState state, int maxDepth, long timeMillis) {
        TimedMinimax.timeLimitMillis = timeMillis;
        TimedMinimax.startTime = System.currentTimeMillis();

        // Setup timeout for PVS
        PVSSearch.setTimeoutChecker(() -> timedOut());

        Minimax.resetKillerMoves();
        QuiescenceSearch.resetQuiescenceStats();

        Move bestMove = null;
        Move lastCompleteMove = null;

        System.out.println("=== Starting Ultimate AI (PVS + Quiescence + Iterative Deepening) ===");

        for (int depth = 1; depth <= maxDepth; depth++) {
            if (timedOut()) {
                System.out.println("⏱ Time limit reached before depth " + depth);
                break;
            }

            long depthStartTime = System.currentTimeMillis();

            try {
                // Use Ultimate search: PVS + YOUR Quiescence
                Move candidate = Minimax.findBestMoveUltimate(state, depth);

                if (candidate != null) {
                    lastCompleteMove = candidate;
                    bestMove = candidate;

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    System.out.println("✓ Depth " + depth + " completed in " + depthTime + "ms. Best: " + candidate);

                    // Check for winning move
                    GameState testState = state.copy();
                    testState.applyMove(candidate);
                    if (Minimax.isGameOver(testState)) {
                        System.out.println("🎯 Winning move found at depth " + depth);
                        break;
                    }
                }

            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("Timeout")) {
                    System.out.println("⏱ Timeout at depth " + depth);
                } else {
                    System.out.println("❌ Error at depth " + depth + ": " + e.getMessage());
                }
                break;
            }

            // Time management
            long elapsed = System.currentTimeMillis() - startTime;
            long remaining = timeMillis - elapsed;
            if (remaining < timeMillis * 0.2) {
                System.out.println("⚡ Stopping early to save time. Remaining: " + remaining + "ms");
                break;
            }
        }

        // Print final stats
        if (QuiescenceSearch.qNodes > 0) {
            System.out.println("Final Q-nodes: " + QuiescenceSearch.qNodes);
            System.out.println("Final Stand-pat rate: " + (100.0 * QuiescenceSearch.standPatCutoffs / QuiescenceSearch.qNodes) + "%");
        }

        System.out.println("=== Ultimate AI Search completed. Best move: " + bestMove + " ===");
        return bestMove != null ? bestMove : lastCompleteMove;
    }

    /**
     * NEW: PVS only (without Quiescence) for comparison
     */
    public static Move findBestMoveWithPVS(GameState state, int maxDepth, long timeMillis) {
        TimedMinimax.timeLimitMillis = timeMillis;
        TimedMinimax.startTime = System.currentTimeMillis();

        PVSSearch.setTimeoutChecker(() -> timedOut());
        Minimax.resetKillerMoves();

        Move bestMove = null;
        Move lastCompleteMove = null;

        System.out.println("=== Starting PVS Search (without Quiescence) ===");

        for (int depth = 1; depth <= maxDepth; depth++) {
            if (timedOut()) {
                System.out.println("⏱ Time limit reached before depth " + depth);
                break;
            }

            try {
                Move candidate = Minimax.findBestMoveWithPVS(state, depth);

                if (candidate != null) {
                    lastCompleteMove = candidate;
                    bestMove = candidate;

                    long depthTime = System.currentTimeMillis() - startTime;
                    System.out.println("✓ Depth " + depth + " completed. Best: " + candidate);

                    GameState testState = state.copy();
                    testState.applyMove(candidate);
                    if (Minimax.isGameOver(testState)) {
                        System.out.println("🎯 Winning move found");
                        break;
                    }
                }

            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("Timeout")) {
                    System.out.println("⏱ Timeout at depth " + depth);
                }
                break;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            long remaining = timeMillis - elapsed;
            if (remaining < timeMillis * 0.2) {
                break;
            }
        }

        return bestMove != null ? bestMove : lastCompleteMove;
    }

    /**
     * ENHANCED: Strategy-based search method
     */
    public static Move findBestMoveWithStrategy(GameState state, int maxDepth, long timeMillis,
                                                Minimax.SearchStrategy strategy) {
        TimedMinimax.timeLimitMillis = timeMillis;
        TimedMinimax.startTime = System.currentTimeMillis();

        // Setup appropriate components based on strategy
        if (strategy == Minimax.SearchStrategy.PVS || strategy == Minimax.SearchStrategy.PVS_Q) {
            PVSSearch.setTimeoutChecker(() -> timedOut());
        }

        Minimax.resetKillerMoves();

        if (strategy == Minimax.SearchStrategy.ALPHA_BETA_Q || strategy == Minimax.SearchStrategy.PVS_Q) {
            QuiescenceSearch.setRemainingTime(timeMillis); // Sync time
            QuiescenceSearch.resetQuiescenceStats();
        }

        Move bestMove = null;
        Move lastCompleteMove = null;

        System.out.println("=== Starting " + strategy + " with Iterative Deepening ===");

        for (int depth = 1; depth <= maxDepth; depth++) {
            if (timedOut()) {
                System.out.println("⏱ Time limit reached before depth " + depth);
                break;
            }

            long depthStartTime = System.currentTimeMillis();

            try {
                // Use the unified strategy interface from Minimax
                Move candidate = Minimax.findBestMoveWithStrategy(state, depth, strategy);

                if (candidate != null) {
                    lastCompleteMove = candidate;
                    bestMove = candidate;

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    System.out.println("✓ Depth " + depth + " completed in " + depthTime + "ms. Best: " + candidate);

                    // Check for immediate win
                    GameState testState = state.copy();
                    testState.applyMove(candidate);
                    if (Minimax.isGameOver(testState)) {
                        System.out.println("🎯 Winning move found at depth " + depth);
                        break;
                    }
                }

            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("Timeout")) {
                    System.out.println("⏱ Timeout at depth " + depth);
                } else {
                    System.out.println("❌ Error at depth " + depth + ": " + e.getMessage());
                }
                break;
            }

            // Time management: stop early if running low on time
            long elapsed = System.currentTimeMillis() - startTime;
            long remaining = timeMillis - elapsed;
            if (remaining < timeMillis * 0.2) {
                System.out.println("⚡ Stopping early to save time. Remaining: " + remaining + "ms");
                break;
            }
        }

        // Print final statistics for quiescence strategies
        if (strategy == Minimax.SearchStrategy.ALPHA_BETA_Q || strategy == Minimax.SearchStrategy.PVS_Q) {
            if (QuiescenceSearch.qNodes > 0) {
                System.out.println("Q-nodes: " + QuiescenceSearch.qNodes);
                double standPatRate = (100.0 * QuiescenceSearch.standPatCutoffs) / QuiescenceSearch.qNodes;
                System.out.println("Stand-pat rate: " + String.format("%.1f%%", standPatRate));
            }
        }

        System.out.println("=== Search completed. Best move: " + bestMove + " ===");
        return bestMove != null ? bestMove : lastCompleteMove;
    }

    // Helper method (add if not already present)
    private static boolean timedOut() {
        return System.currentTimeMillis() - startTime >= timeLimitMillis;
    }

    /**
     * Helper method to print final statistics
     */
    private static void printFinalStats(Minimax.SearchStrategy strategy) {
        if (strategy == Minimax.SearchStrategy.ALPHA_BETA_Q || strategy == Minimax.SearchStrategy.PVS_Q) {
            if (QuiescenceSearch.qNodes > 0) {
                System.out.println("Q-nodes: " + QuiescenceSearch.qNodes);
                double standPatRate = (100.0 * QuiescenceSearch.standPatCutoffs) / QuiescenceSearch.qNodes;
                System.out.println("Stand-pat rate: " + String.format("%.1f%%", standPatRate));
            }
        }
    }




    /**
     * Helper method to store in transposition table
     */
    private static void storeInTranspositionTable(long hash, int score, int depth, int flag, Move bestMove) {
        // This would need to access Minimax's transposition table
        // For now, we'll skip storing to avoid complex access issues
        // The search will still work, just without some TT benefits in the quiescence version
    }

    /**
     * Move ordering with advanced heuristics
     */
    private static void orderMovesWithAdvancedHeuristics(List<Move> moves, GameState state, int depth, TTEntry entry) {
        Minimax.orderMovesAdvanced(moves, state, depth, entry);
    }

    /*private static boolean timedOut() {
        return System.currentTimeMillis() - startTime >= timeLimitMillis;
    }*/

    private static class TimeoutException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
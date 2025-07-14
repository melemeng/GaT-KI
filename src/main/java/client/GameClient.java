package client;

import java.util.List;

import GaT.evaluation.Evaluator;
import GaT.search.MoveGenerator;
import GaT.search.PVSSearch;
import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;
import GaT.engine.TimeManager;
import GaT.engine.TimedMinimax;
import GaT.search.Minimax;
import GaT.search.QuiescenceSearch;
import GaT.search.SearchStatistics;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * GAME CLIENT - UNIFIED EVALUATOR INTEGRATION
 *
 * CHANGES:
 * ✅ Removed separate evaluator instance
 * ✅ Uses shared evaluator from Minimax
 * ✅ All evaluation calls route through unified evaluator
 * ✅ Simplified evaluation validation
 * ✅ Maintains all existing functionality
 */
public class GameClient {
    private static final Gson gson = new Gson();

    // Game statistics tracking
    private static int moveNumber = 0;
    private static long lastMoveStartTime = 0;
    private static TimeManager timeManager = new TimeManager(180000, 20); // Adjusted estimate

    // === USE SHARED UNIFIED EVALUATOR ===
    private static final Evaluator evaluator = Minimax.getEvaluator();

    private static void validateEvaluation(GameState state) {
        // Quick sanity check for evaluation consistency
        int materialScore = 0;
        for (int i = 0; i < 49; i++) {
            materialScore += (state.redStackHeights[i] - state.blueStackHeights[i]) * 100;
        }

        // Use unified evaluator
        int fullScore = evaluator.evaluate(state);
        int posBonus = fullScore - materialScore;

        System.out.println("🔍 EVAL CHECK: Material=" + materialScore +
                ", Full=" + fullScore +
                ", Positional=" + posBonus);

        // Warning if positional bonus is larger than material
        if (materialScore != 0 && Math.abs(posBonus) > Math.abs(materialScore)) {
            System.out.println("⚠️ WARNING: Positional bonus larger than material!");
        }
    }

    public static void main(String[] args) {
        boolean running = true;
        Network network = new Network();
        int player = Integer.parseInt(network.getP());
        System.out.println("🎮 You are player " + player + " (" + (player == 0 ? "RED" : "BLUE") + ")");

        System.out.println("🧠 Using Unified Evaluator: " + evaluator.getClass().getSimpleName());

        while (running) {
            try {
                // Get game state from server - using existing send() method
                String response = network.send("GET_STATE"); // Send request for game state
                if (response == null) {
                    // Try alternative communication method
                    response = network.send("");
                }
                if (response == null || response.trim().isEmpty()) {
                    System.out.println("⏸️ Empty response, waiting...");
                    Thread.sleep(100);
                    continue;
                }

                JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

                if (jsonResponse.has("gameState")) {
                    String fenString = jsonResponse.get("gameState").getAsString();
                    GameState currentState = GameState.fromFen(fenString);

                    if (currentState == null) {
                        System.err.println("❌ Invalid FEN string received: " + fenString);
                        continue;
                    }

                    moveNumber++;
                    lastMoveStartTime = System.currentTimeMillis();

                    System.out.println("\n" + "=".repeat(60));
                    System.out.println("🎯 MOVE " + moveNumber + " - " +
                            (currentState.redToMove ? "RED" : "BLUE") + " TO MOVE");
                    System.out.println("=".repeat(60));

                    currentState.printBoard();

                    // Quick evaluation validation
                    validateEvaluation(currentState);

                    // Check if it's our turn
                    boolean isOurTurn = (player == 0 && currentState.redToMove) ||
                            (player == 1 && !currentState.redToMove);

                    if (!isOurTurn) {
                        System.out.println("⏳ Waiting for opponent's move...");
                        Thread.sleep(200);
                        continue;
                    }

                    if (Minimax.isGameOver(currentState)) {
                        System.out.println("🏁 Game Over!");
                        running = false;
                        continue;
                    }

                    // Calculate time for this move using TimeManager
                    long moveTime = timeManager.calculateTimeForMove(currentState);

                    System.out.println("⏱️ Time allocated: " + moveTime + "ms (Remaining: " + timeManager.getRemainingTime() + "ms)");

                    // Reset search components
                    Minimax.reset();

                    Move bestMove = null;
                    long searchStartTime = System.currentTimeMillis();

                    try {
                        // Use progressive deepening with time management
                        bestMove = findBestMoveWithTimeout(currentState, moveTime);

                        if (bestMove == null) {
                            System.out.println("⚠️ Primary search failed, using emergency fallback");
                            bestMove = emergencyFallback(currentState);
                        }

                    } catch (Exception e) {
                        System.err.println("❌ Search error: " + e.getMessage());
                        e.printStackTrace();
                        bestMove = emergencyFallback(currentState);
                    }

                    long searchTime = System.currentTimeMillis() - searchStartTime;

                    if (bestMove != null) {
                        // Evaluate the move
                        GameState resultState = currentState.copy();
                        resultState.applyMove(bestMove);
                        int evaluation = evaluator.evaluate(resultState);

                        System.out.println("🎯 SELECTED MOVE: " + bestMove);
                        System.out.println("📊 Evaluation: " + evaluation);
                        System.out.println("⏱️ Search time: " + searchTime + "ms");

                        // Send move to server
                        String moveJson = gson.toJson(bestMove);
                        network.send(moveJson);

                        // Update time manager with actual search time
                        timeManager.updateRemainingTime(timeManager.getRemainingTime() - searchTime);
                        timeManager.decrementEstimatedMovesLeft();

                    } else {
                        System.err.println("❌ CRITICAL: No move found! This should never happen!");
                        running = false;
                    }

                } else if (jsonResponse.has("error")) {
                    String error = jsonResponse.get("error").getAsString();
                    System.err.println("❌ Server error: " + error);

                    if (error.toLowerCase().contains("game") && error.toLowerCase().contains("over")) {
                        System.out.println("🏁 Game ended by server");
                        running = false;
                    }

                } else {
                    System.out.println("📨 Unknown response: " + response);
                }

            } catch (Exception e) {
                System.err.println("❌ CRITICAL ERROR: " + e.getMessage());
                e.printStackTrace();

                // Emergency pause before retry
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        }

        System.out.println("🎮 Game Client shutting down...");

        // Print final statistics
        printFinalStatistics();
    }

    /**
     * Find best move with time management using unified evaluator
     */
    private static Move findBestMoveWithTimeout(GameState state, long timeMillis) {
        try {
            // Use TimedMinimax for time-managed search
            return TimedMinimax.findBestMoveUltimate(state, 8, timeMillis - 100); // Safety buffer

        } catch (Exception e) {
            System.err.println("❌ TimedMinimax failed: " + e.getMessage());

            // Fallback to regular search with lower depth
            try {
                return Minimax.findBestMove(state, 4);
            } catch (Exception e2) {
                System.err.println("❌ Regular Minimax failed: " + e2.getMessage());
                return null;
            }
        }
    }

    /**
     * Emergency fallback when all else fails
     */
    private static Move emergencyFallback(GameState state) {
        try {
            System.out.println("🚨 EMERGENCY: Using depth-1 search");

            List<Move> moves = MoveGenerator.generateAllMoves(state);
            if (moves.isEmpty()) {
                return null;
            }

            Move bestMove = moves.get(0);
            int bestScore = Integer.MIN_VALUE;

            // Quick evaluation of first few moves
            for (int i = 0; i < Math.min(moves.size(), 5); i++) {
                Move move = moves.get(i);
                try {
                    GameState testState = state.copy();
                    testState.applyMove(move);
                    int score = evaluator.evaluate(testState);

                    if (score > bestScore) {
                        bestScore = score;
                        bestMove = move;
                    }
                } catch (Exception e) {
                    continue; // Skip problematic moves
                }
            }

            return bestMove;

        } catch (Exception e) {
            System.err.println("❌ CRITICAL: Emergency fallback failed: " + e.getMessage());

            // Absolute last resort - return first legal move
            try {
                List<Move> moves = MoveGenerator.generateAllMoves(state);
                return moves.isEmpty() ? null : moves.get(0);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /**
     * Print final statistics using unified evaluator
     */
    private static void printFinalStatistics() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🏁 FINAL GAME STATISTICS");
        System.out.println("=".repeat(60));
        System.out.println("Total moves played: " + moveNumber);
        System.out.println("Evaluator used: " + evaluator.getClass().getSimpleName());

        // Get search statistics
        SearchStatistics stats = SearchStatistics.getInstance();
        System.out.println("Total nodes searched: " + stats.getNodeCount());
        System.out.println("Transposition table hits: " + stats.getTTHits());

        // Time management statistics
        System.out.println(timeManager.getTimeManagementStatistics());

        System.out.println("=".repeat(60));
    }
}
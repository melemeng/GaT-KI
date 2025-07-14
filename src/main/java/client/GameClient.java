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
 * CORRECTED GAME CLIENT - RESTORED WORKING PROTOCOL
 *
 * FIXES:
 * ✅ Restored original working server protocol
 * ✅ Correct JSON requests: gson.toJson("get")
 * ✅ Correct response parsing: game.get("board"), game.get("turn")
 * ✅ Correct move sending: gson.toJson(move.toString())
 * ✅ Server-side game over detection only (no local checks)
 * ✅ Enhanced error handling and debugging
 */
public class GameClient {
    private static final Gson gson = new Gson();

    // Game statistics tracking
    private static int moveNumber = 0;
    private static long lastMoveStartTime = 0;
    private static TimeManager timeManager = new TimeManager(180000, 20);

    // Use shared unified evaluator
    private static final Evaluator evaluator = Minimax.getEvaluator();

    private static void validateEvaluation(GameState state) {
        // Quick sanity check for evaluation consistency
        int materialScore = 0;
        for (int i = 0; i < 49; i++) {
            materialScore += (state.redStackHeights[i] - state.blueStackHeights[i]) * 100;
        }

        int fullScore = evaluator.evaluate(state);
        int posBonus = fullScore - materialScore;

        System.out.println("🔍 EVAL CHECK: Material=" + materialScore +
                ", Full=" + fullScore +
                ", Positional=" + posBonus);

        if (materialScore != 0 && Math.abs(posBonus) > Math.abs(materialScore)) {
            System.out.println("⚠️ WARNING: Positional bonus larger than material!");
        }
    }

    public static void main(String[] args) {
        boolean running = true;
        Network network = new Network();

        if (network.getP() == null) {
            System.err.println("❌ Failed to connect to server!");
            return;
        }

        int player = Integer.parseInt(network.getP());
        System.out.println("🎮 You are player " + player + " (" + (player == 0 ? "RED" : "BLUE") + ")");
        System.out.println("🧠 Using Unified Evaluator: " + evaluator.getClass().getSimpleName());

        while (running) {
            try {
                // CORRECTED: Use original working protocol
                System.out.println("📡 Requesting game state...");
                String gameData = network.send(gson.toJson("get"));

                if (gameData == null) {
                    System.out.println("❌ Couldn't get game data");
                    Thread.sleep(100);
                    continue;
                }

                System.out.println("📨 Server response: " + gameData);

                JsonObject game = gson.fromJson(gameData, JsonObject.class);

                // Check if both players are connected
                if (!game.has("bothConnected") || !game.get("bothConnected").getAsBoolean()) {
                    System.out.println("⏳ Waiting for both players to connect...");
                    Thread.sleep(1000);
                    continue;
                }

                // CORRECTED: Use original field names
                String turn = game.get("turn").getAsString();
                String board = game.get("board").getAsString();
                long timeRemaining = game.get("time").getAsLong();

                System.out.println("🎯 Turn: " + turn + ", Time: " + formatTime(timeRemaining));
                System.out.println("📋 Board: " + board);

                // Check if it's our turn (original logic)
                if ((player == 0 && turn.equals("r")) || (player == 1 && turn.equals("b"))) {
                    moveNumber++;
                    lastMoveStartTime = System.currentTimeMillis();

                    System.out.println("\n" + "=".repeat(60));
                    System.out.println("🎯 MOVE " + moveNumber + " - " + (player == 0 ? "RED" : "BLUE"));
                    System.out.println("=".repeat(60));

                    // Parse board state
                    GameState currentState = GameState.fromFen(board);
                    if (currentState == null) {
                        System.err.println("❌ Invalid board FEN: " + board);
                        continue;
                    }

                    currentState.printBoard();
                    validateEvaluation(currentState);

                    // No local game over check needed - server handles this!
                    // The server will send "end": true when game is actually over

                    // Calculate our move
                    timeManager.updateRemainingTime(timeRemaining);
                    long moveTime = timeManager.calculateTimeForMove(currentState);
                    System.out.println("⏱️ Time allocated: " + moveTime + "ms (Remaining: " + timeRemaining + "ms)");

                    Minimax.reset();
                    Move bestMove = null;
                    long searchStartTime = System.currentTimeMillis();

                    try {
                        bestMove = findBestMoveWithTimeout(currentState, moveTime);
                        if (bestMove == null) {
                            bestMove = emergencyFallback(currentState);
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Search error: " + e.getMessage());
                        bestMove = emergencyFallback(currentState);
                    }

                    long searchTime = System.currentTimeMillis() - searchStartTime;

                    if (bestMove != null) {
                        GameState resultState = currentState.copy();
                        resultState.applyMove(bestMove);
                        int evaluation = evaluator.evaluate(resultState);

                        System.out.println("🎯 SELECTED MOVE: " + bestMove);
                        System.out.println("📊 Evaluation: " + evaluation);
                        System.out.println("⏱️ Search time: " + searchTime + "ms");

                        // CORRECTED: Send move in original working format
                        String moveString = bestMove.toString();  // "A1-B2-1"
                        String moveResponse = network.send(gson.toJson(moveString));
                        System.out.println("📤 Move sent: " + moveString);
                        System.out.println("📥 Move response: " + moveResponse);

                        // Show search statistics
                        SearchStatistics stats = SearchStatistics.getInstance();
                        System.out.printf("📊 Nodes: %,d (regular: %,d, quiescence: %,d)%n",
                                stats.getTotalNodes(), stats.getNodeCount(), stats.getQNodeCount());

                        timeManager.updateRemainingTime(timeManager.getRemainingTime() - searchTime);
                        timeManager.decrementEstimatedMovesLeft();

                    } else {
                        System.err.println("❌ CRITICAL: No move found!");
                        running = false;
                    }
                } else {
                    System.out.println("⏳ Waiting for our turn...");
                }

                // Check for game end (server-side detection)
                if (game.has("end") && game.get("end").getAsBoolean()) {
                    System.out.println("🏁 Game has ended");
                    String result = game.has("winner") ?
                            ("Winner: " + game.get("winner").getAsString()) :
                            "Game finished";
                    System.out.println("🎯 " + result);

                    long finalTimeRemaining = game.has("time") ? game.get("time").getAsLong() : 0;
                    printGameStatistics(finalTimeRemaining);
                    running = false;
                }

                Thread.sleep(100);

            } catch (Exception e) {
                System.err.println("❌ CRITICAL ERROR: " + e.getMessage());
                e.printStackTrace();

                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        }

        System.out.println("🎮 Game Client shutting down...");
        network.close();
        printFinalStatistics();
    }

    private static Move findBestMoveWithTimeout(GameState state, long timeMillis) {
        try {
            return TimedMinimax.findBestMoveUltimate(state, 8, timeMillis - 100);
        } catch (Exception e) {
            System.err.println("❌ TimedMinimax failed: " + e.getMessage());
            try {
                return Minimax.findBestMove(state, 4);
            } catch (Exception e2) {
                System.err.println("❌ Regular Minimax failed: " + e2.getMessage());
                return null;
            }
        }
    }

    private static Move emergencyFallback(GameState state) {
        try {
            System.out.println("🚨 EMERGENCY: Using depth-1 search");
            List<Move> moves = MoveGenerator.generateAllMoves(state);
            if (moves.isEmpty()) return null;

            // Find first legal move
            return moves.get(0);
        } catch (Exception e) {
            System.err.println("❌ Emergency fallback failed: " + e.getMessage());
            return null;
        }
    }

    private static void printGameStatistics(long finalTimeRemaining) {
        System.out.println("\n📊 GAME STATISTICS:");
        System.out.println("   🎮 Total moves: " + moveNumber);
        System.out.println("   ⏱️ Final time: " + formatTime(finalTimeRemaining));

        if (moveNumber > 0) {
            long totalTimeUsed = 180_000 - finalTimeRemaining;
            long averageTimePerMove = totalTimeUsed / moveNumber;
            double timeUtilization = 100.0 * totalTimeUsed / 180_000;

            System.out.printf("   ⚡ Average time/move: %dms%n", averageTimePerMove);
            System.out.printf("   📊 Time utilization: %.1f%%%n", timeUtilization);

            if (timeUtilization < 60) {
                System.out.println("   📈 Could use more time!");
            } else if (timeUtilization < 80) {
                System.out.println("   ✅ Good time management!");
            } else if (timeUtilization < 95) {
                System.out.println("   💪 Excellent aggressive time usage!");
            } else {
                System.out.println("   ⏰ Very close to time limit!");
            }
        }

        System.out.println("   🧠 AI Engine: Unified Evaluator");
        System.out.println("   📊 Evaluator: " + evaluator.getClass().getSimpleName());
        System.out.println("   🎯 Strategy: TimedMinimax");
    }

    private static void printFinalStatistics() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 FINAL GAME STATISTICS");
        System.out.println("=".repeat(60));
        System.out.println("Total moves played: " + moveNumber);
        System.out.println("Remaining time: " + timeManager.getRemainingTime() + "ms");

        // Print search statistics if available
        try {
            SearchStatistics stats = SearchStatistics.getInstance();
            if (stats != null) {
                System.out.println(stats.getComprehensiveSummaryWithConfig());
            }
        } catch (Exception e) {
            System.out.println("Statistics unavailable: " + e.getMessage());
        }

        System.out.println("=".repeat(60));
    }

    private static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return String.format("%.1fs", milliseconds / 1000.0);
        }
    }
}
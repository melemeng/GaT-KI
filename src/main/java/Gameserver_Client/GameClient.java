package Gameserver_Client;

import java.util.List;

import GaT.MoveGenerator;
import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.TimeManager;
import GaT.TimedMinimax;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class GameClient {
    private static final Gson gson = new Gson();
    private static TimeManager timeManager;
    private static int moveNumber = 0;

    // Track time usage for learning
    private static long lastMoveStartTime = 0;

    public static void main(String[] args) {
        boolean running = true;
        Network network = new Network();
        int player = Integer.parseInt(network.getP());
        System.out.println("🎮 You are player " + player);

        // Initialize with conservative estimates
        // Adjust these based on your typical game length
        timeManager = new TimeManager(180_000, 50); // 3 minutes, ~50 moves

        while (running) {
            try {
                // Request game state
                String gameData = network.send(gson.toJson("get"));
                if (gameData == null) {
                    System.out.println("❌ Couldn't get game");
                    break;
                }

                // Parse game state
                JsonObject game = gson.fromJson(gameData, JsonObject.class);

                // Check if both players are connected
                if (game.get("bothConnected").getAsBoolean()) {
                    String turn = game.get("turn").getAsString();
                    String board = game.get("board").getAsString();
                    long timeRemaining = game.get("time").getAsLong();

                    // Update time manager with current remaining time
                    timeManager.updateRemainingTime(timeRemaining);

                    // Only act when it's our turn
                    if ((player == 0 && turn.equals("r")) || (player == 1 && turn.equals("b"))) {
                        moveNumber++;
                        System.out.println("\n" + "=".repeat(50));
                        System.out.println("🔄 Move " + moveNumber + " - " + (player == 0 ? "RED" : "BLUE"));
                        System.out.println("📋 Board: " + board);
                        System.out.println("⏱️ Time Remaining: " + formatTime(timeRemaining));

                        // Record move start time
                        lastMoveStartTime = System.currentTimeMillis();

                        // Get AI move using advanced time management
                        String move = getAIMove(board, player, timeRemaining);

                        // Calculate actual time used
                        long actualTimeUsed = System.currentTimeMillis() - lastMoveStartTime;

                        // Send move to server
                        network.send(gson.toJson(move));
                        System.out.println("📤 Sent move: " + move);
                        System.out.println("⏱️ Actual time used: " + actualTimeUsed + "ms");

                        // Update time manager with feedback
                        timeManager.reportMoveCompleted(actualTimeUsed);
                        timeManager.decrementEstimatedMovesLeft();

                        System.out.println("=".repeat(50));
                    }
                }

                // Check if game has ended
                if (game.has("end") && game.get("end").getAsBoolean()) {
                    System.out.println("🏁 Game has ended");
                    String result = game.has("winner") ?
                            ("Winner: " + game.get("winner").getAsString()) :
                            "Game finished";
                    System.out.println("🎯 " + result);

                    // FIXED: Get the final time remaining for statistics
                    long finalTimeRemaining = game.has("time") ? game.get("time").getAsLong() : 0;
                    printGameStatistics(finalTimeRemaining);
                    running = false;
                }

                // Small delay to avoid busy-waiting
                Thread.sleep(100);

            } catch (Exception e) {
                System.out.println("❌ Error: " + e.getMessage());
                e.printStackTrace();
                running = false;
                break;
            }
        }
    }

    /**
     * Enhanced AI move calculation with advanced time management
     */
    private static String getAIMove(String board, int player, long timeLeft) {
        try {
            GameState state = GameState.fromFen(board);

            // Get sophisticated time allocation
            long timeForMove = timeManager.calculateTimeForMove(state);

            System.out.println("🧠 AI Analysis:");
            System.out.println("   ⏰ Time allocated: " + timeForMove + "ms");
            System.out.println("   🎯 Strategy: ULTIMATE (PVS + Quiescence)");

            // Use the ultimate AI strategy with precise time control
            long searchStartTime = System.currentTimeMillis();

            Move bestMove = TimedMinimax.findBestMoveUltimate(state, 99, timeForMove);

            long searchTime = System.currentTimeMillis() - searchStartTime;

            // Log search efficiency
            System.out.println("   ✅ Search completed in: " + searchTime + "ms");
            if (searchTime < timeForMove * 0.5) {
                System.out.println("   ⚡ Efficient search (used " +
                        String.format("%.1f%%", 100.0 * searchTime / timeForMove) + " of allocated time)");
            } else if (searchTime > timeForMove * 0.95) {
                System.out.println("   ⏳ Deep search (used full allocated time)");
            }

            return bestMove.toString();

        } catch (Exception e) {
            System.err.println("❌ Error in AI move calculation: " + e.getMessage());
            e.printStackTrace();

            // Emergency fallback: find any legal move quickly
            try {
                GameState state = GameState.fromFen(board);
                List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
                if (!legalMoves.isEmpty()) {
                    Move fallbackMove = legalMoves.get(0);
                    System.out.println("🚨 Using emergency fallback move: " + fallbackMove);
                    return fallbackMove.toString();
                }
            } catch (Exception fallbackError) {
                System.err.println("❌ Even fallback failed: " + fallbackError.getMessage());
            }

            // Last resort
            return "A1-A2-1"; // Hopefully this won't happen!
        }
    }

    /**
     * Format time in human-readable way
     */
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

    /**
     * Print game statistics at the end
     * FIXED: Use correct parameter name throughout
     */
    private static void printGameStatistics(long finalTimeRemaining) {
        System.out.println("\n📊 GAME STATISTICS:");
        System.out.println("   🎮 Total moves played: " + moveNumber);
        System.out.println("   ⏱️ Final time remaining: " + formatTime(finalTimeRemaining));

        if (moveNumber > 0) {
            long totalTimeUsed = 180_000 - finalTimeRemaining; // Assuming 3 minute start
            long averageTimePerMove = totalTimeUsed / moveNumber;
            System.out.println("   ⚡ Average time per move: " + averageTimePerMove + "ms");

            if (finalTimeRemaining < 10_000) {
                System.out.println("   ⚠️ Game ended in time pressure!");
            } else if (finalTimeRemaining > 60_000) {
                System.out.println("   😎 Comfortable time management");
            }
        }

        System.out.println("   🧠 AI Strategy: ULTIMATE (PVS + Quiescence + Advanced Time Management)");
    }
}
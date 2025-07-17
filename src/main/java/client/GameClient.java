package client;

import GaT.search.Engine;
import GaT.game.GameState;
import GaT.game.Move;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * OPTIMIZED TOURNAMENT CLIENT for Guard & Towers
 *
 * Simplified version that keeps all essential functionality:
 * ✅ Adaptive polling (fast on our turn, slow on opponent's turn)
 * ✅ Smart timeout handling
 * ✅ JSON game state parsing
 * ✅ Move generation and sending
 * ✅ Time management
 * ✅ Error recovery
 * ✅ Integrated with new Engine.java
 *
 * Removed complexity:
 * ❌ Excessive statistics tracking
 * ❌ Complex evaluation validation
 * ❌ Over-engineered error handling
 * ❌ Unnecessary logging spam
 */
public class GameClient {

    // === CORE CONFIGURATION ===
    private static final Gson gson = new Gson();
    private static final Engine engine = new Engine();

    // === TIMING CONFIGURATION ===
    private static final int OUR_TURN_POLL_MS = 50;      // Fast polling when our turn
    private static final int OPPONENT_POLL_MS = 1000;    // Slower when opponent's turn
    private static final int SOCKET_TIMEOUT_OUR_TURN = 5000;    // 5s when our turn
    private static final int SOCKET_TIMEOUT_OPPONENT = 1000;    // 1s when opponent's turn
    private static final int MAX_CONSECUTIVE_ERRORS = 10;

    // === GAME STATE ===
    private static int player;
    private static boolean isOurTurn = false;
    private static int moveNumber = 0;
    private static long gameStartTime;
    private static long totalThinkingTime = 0;

    // === ERROR TRACKING ===
    private static int consecutiveErrors = 0;
    private static long lastSuccessfulPoll = 0;

    public static void main(String[] args) {
        System.out.println("🎯 GUARD & TOWERS TOURNAMENT CLIENT");
        System.out.println("🚀 Optimized version with new Engine");

        Network network = new Network();

        // Connect to server
        if (network.getP() == null) {
            System.err.println("❌ Failed to connect to server!");
            return;
        }

        player = Integer.parseInt(network.getP());
        gameStartTime = System.currentTimeMillis();
        lastSuccessfulPoll = gameStartTime;

        System.out.println("🎮 Connected as player " + player + " (" + (player == 0 ? "RED" : "BLUE") + ")");
        System.out.println("⚡ Polling: " + OUR_TURN_POLL_MS + "ms (our turn) / " + OPPONENT_POLL_MS + "ms (opponent)");

        // Main game loop
        runGameLoop(network);

        // Cleanup
        network.close();
        printGameSummary();
    }

    /**
     * Main game loop - simplified and efficient
     */
    private static void runGameLoop(Network network) {
        boolean gameRunning = true;
        int pollCount = 0;

        while (gameRunning) {
            try {
                pollCount++;

                // Adaptive timeout based on turn
                setAdaptiveTimeout(network);

                // Get game state from server
                String gameData = network.send(gson.toJson("get"));

                if (gameData == null) {
                    handleNetworkError();
                    Thread.sleep(isOurTurn ? OUR_TURN_POLL_MS : OPPONENT_POLL_MS);
                    continue;
                }

                // Reset error counter on success
                consecutiveErrors = 0;
                lastSuccessfulPoll = System.currentTimeMillis();

                // Parse game state
                JsonObject game = gson.fromJson(gameData, JsonObject.class);

                // Wait for both players
                if (!game.has("bothConnected") || !game.get("bothConnected").getAsBoolean()) {
                    System.out.println("⏳ Waiting for opponent to connect...");
                    Thread.sleep(1000);
                    continue;
                }

                // Check for game end
                if (game.has("end") && game.get("end").getAsBoolean()) {
                    handleGameEnd(game);
                    gameRunning = false;
                    break;
                }

                // Update turn state
                String turn = game.get("turn").getAsString();
                boolean wasOurTurn = isOurTurn;
                isOurTurn = (player == 0 && turn.equals("r")) || (player == 1 && turn.equals("b"));

                // Handle turn changes
                if (isOurTurn && !wasOurTurn) {
                    handleOurTurn(game, network);
                } else if (!isOurTurn) {
                    handleOpponentTurn(game, pollCount);
                }

                // Adaptive sleep
                Thread.sleep(isOurTurn ? OUR_TURN_POLL_MS : OPPONENT_POLL_MS);

            } catch (Exception e) {
                handleCriticalError(e);
                try {
                    Thread.sleep(OPPONENT_POLL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Handle our turn - make a move
     */
    private static void handleOurTurn(JsonObject game, Network network) {
        try {
            moveNumber++;
            long timeRemaining = game.get("time").getAsLong();
            String board = game.get("board").getAsString();

            System.out.println("\n🔔 YOUR TURN #" + moveNumber);
            System.out.println("⏰ Time remaining: " + formatTime(timeRemaining));

            // Parse board state
            GameState gameState = GameState.fromFen(board);
            if (gameState == null) {
                System.err.println("❌ Failed to parse board: " + board);
                return;
            }

            // Calculate time to use for this move
            long thinkTime = calculateThinkTime(timeRemaining, moveNumber);
            System.out.println("🧠 Thinking for " + thinkTime + "ms...");

            // Find best move with new engine
            long moveStart = System.currentTimeMillis();
            Move bestMove = engine.findBestMove(gameState, thinkTime);
            long actualThinkTime = System.currentTimeMillis() - moveStart;

            if (bestMove == null) {
                System.err.println("❌ No move found!");
                return;
            }

            // Send move to server
            String moveStr = bestMove.toString();
            String response = network.send(gson.toJson(moveStr));

            totalThinkingTime += actualThinkTime;

            // Log move
            System.out.println("🎯 Played: " + moveStr + " (took " + actualThinkTime + "ms)");
            System.out.println("📊 Search: " + engine.getNodesSearched() + " nodes, " +
                    String.format("%.0f", engine.getNodesSearched() * 1000.0 / Math.max(1, actualThinkTime)) + " nps");
            System.out.println("📊 Engine: TT hit rate " + String.format("%.1f%%", engine.getTTHitRate()));

            if (response == null || response.contains("error")) {
                System.err.println("⚠️ Server response: " + response);
            }

        } catch (Exception e) {
            System.err.println("❌ Error making move: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle opponent's turn - wait and show status
     */
    private static void handleOpponentTurn(JsonObject game, int pollCount) {
        long timeRemaining = game.has("time") ? game.get("time").getAsLong() : 0;

        // Show status periodically (every 10 polls or when time is low)
        if (pollCount % 10 == 0 || timeRemaining < 30000) {
            System.out.printf("💭 Opponent thinking... (poll #%d, time left: %s)%n",
                    pollCount, formatTime(timeRemaining));
        }
    }

    /**
     * Calculate thinking time for this move
     */
    private static long calculateThinkTime(long timeRemaining, int moveNumber) {
        // Simple time management strategy
        if (timeRemaining > 120000) {  // More than 2 minutes
            return Math.min(30000, timeRemaining / 15);  // Use up to 30s, target 15 moves
        } else if (timeRemaining > 60000) {  // 1-2 minutes
            return Math.min(15000, timeRemaining / 10);  // Use up to 15s, target 10 moves
        } else if (timeRemaining > 30000) {  // 30s-1min
            return Math.min(8000, timeRemaining / 6);    // Use up to 8s, target 6 moves
        } else if (timeRemaining > 10000) {  // 10-30s
            return Math.min(4000, timeRemaining / 4);    // Use up to 4s, target 4 moves
        } else {  // Less than 10s
            return Math.min(2000, timeRemaining / 2);    // Use up to 2s, target 2 moves
        }
    }

    /**
     * Set adaptive timeout based on whose turn it is
     */
    private static void setAdaptiveTimeout(Network network) {
        try {
            int timeout = isOurTurn ? SOCKET_TIMEOUT_OUR_TURN : SOCKET_TIMEOUT_OPPONENT;
            network.setSocketTimeout(timeout);
        } catch (Exception e) {
            // Ignore timeout setting errors
        }
    }

    /**
     * Handle network errors gracefully
     */
    private static void handleNetworkError() {
        consecutiveErrors++;

        if (isOurTurn) {
            System.err.println("⚠️ Network timeout during our turn (error #" + consecutiveErrors + ")");
        } else {
            // During opponent's turn, timeouts are normal
            if (consecutiveErrors == 1) {
                System.out.println("⏳ Network timeout (opponent thinking)");
            }
        }

        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            System.err.println("🚨 Many consecutive errors (" + consecutiveErrors + ") - connection issues?");
            long timeSinceSuccess = System.currentTimeMillis() - lastSuccessfulPoll;
            if (timeSinceSuccess > 60000) {  // 1 minute
                System.err.println("🚨 No successful poll for " + (timeSinceSuccess/1000) + " seconds!");
            }
        }
    }

    /**
     * Handle critical errors
     */
    private static void handleCriticalError(Exception e) {
        consecutiveErrors++;
        System.err.println("❌ Critical error #" + consecutiveErrors + ": " + e.getMessage());

        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            System.err.println("🚨 Too many errors, but continuing...");
            consecutiveErrors = 0;  // Reset to continue
        }
    }

    /**
     * Handle game end
     */
    private static void handleGameEnd(JsonObject game) {
        System.out.println("\n🏁 GAME FINISHED!");

        if (game.has("winner")) {
            String winner = game.get("winner").getAsString();
            boolean weWon = (player == 0 && winner.equals("r")) || (player == 1 && winner.equals("b"));

            if (weWon) {
                System.out.println("🎉 WE WON!");
            } else {
                System.out.println("😞 We lost.");
            }
            System.out.println("🏆 Winner: " + (winner.equals("r") ? "RED" : "BLUE"));
        } else {
            System.out.println("🤝 Game ended (possibly draw/timeout)");
        }

        if (game.has("time")) {
            long finalTime = game.get("time").getAsLong();
            System.out.println("⏰ Final time remaining: " + formatTime(finalTime));
        }
    }

    /**
     * Print game summary at the end
     */
    private static void printGameSummary() {
        long totalGameTime = System.currentTimeMillis() - gameStartTime;

        System.out.println("\n" + "=".repeat(50));
        System.out.println("📊 GAME SUMMARY");
        System.out.println("=".repeat(50));
        System.out.println("Moves played: " + moveNumber);
        System.out.println("Total game time: " + formatTime(totalGameTime));
        System.out.println("Total thinking time: " + formatTime(totalThinkingTime));

        if (moveNumber > 0) {
            System.out.println("Average thinking time: " + formatTime(totalThinkingTime / moveNumber));
            double thinkingPercentage = 100.0 * totalThinkingTime / totalGameTime;
            System.out.println("Thinking percentage: " + String.format("%.1f%%", thinkingPercentage));
        }

        System.out.println("🧠 Engine: " + engine.getClass().getSimpleName());
        System.out.println("=".repeat(50));
    }

    /**
     * Format time in a readable way
     */
    private static String formatTime(long millis) {
        if (millis < 0) return "0s";

        long seconds = millis / 1000;
        if (seconds >= 60) {
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%dm%02ds", minutes, seconds);
        } else {
            return String.format("%.1fs", millis / 1000.0);
        }
    }
}
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
 * FIXED OPTIMIZED GAME CLIENT with Socket Timeout & Adaptive Polling
 *
 * FIXES:
 * ✅ Removed duplicate method definitions
 * ✅ Fixed main() method structure
 * ✅ Added missing configureNetworkTimeouts() method
 * ✅ Fixed while loop structure
 * ✅ Corrected method visibility and organization
 */
public class GameClient {
    private static final Gson gson = new Gson();

    // === ADAPTIVE POLLING CONFIGURATION ===
    private static final int OUR_TURN_POLL_MS = 50;        // Fast polling when it's our turn
    private static final int OPPONENT_TURN_POLL_MS = 1000;  // Slower polling when opponent's turn
    private static final int SOCKET_TIMEOUT_MS = 3000;      // Socket timeout for opponent turns
    private static final int MAX_CONSECUTIVE_ERRORS = 5;    // Max errors before concern

    // Game statistics tracking
    private static int moveNumber = 0;
    private static long lastMoveStartTime = 0;
    private static TimeManager timeManager = new TimeManager(180000, 20);
    private static final Evaluator evaluator = Minimax.getEvaluator();

    // Network monitoring
    private static int consecutiveErrors = 0;
    private static long lastSuccessfulPoll = System.currentTimeMillis();
    private static boolean isOurTurn = false;
    private static int pollCount = 0;

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
        System.out.println("⚡ OPTIMIZED CLIENT: Adaptive polling + Socket timeout");
        System.out.println("📡 Polling: " + OUR_TURN_POLL_MS + "ms (our turn) / " + OPPONENT_TURN_POLL_MS + "ms (opponent)");
        System.out.println("⏰ Socket timeout: " + SOCKET_TIMEOUT_MS + "ms (opponent's turn)");

        // Configure initial network settings
        configureNetworkTimeouts(network);
        System.out.println("📊 Initial connection quality: " + network.getConnectionStats());

        while (running) {
            try {
                pollCount++;
                long pollStart = System.currentTimeMillis();

                // OPTIMIZATION: Adaptive socket timeout based on whose turn it is
                String gameData = getGameDataWithAdaptiveTimeout(network);

                if (gameData == null) {
                    handleNetworkTimeout();

                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        System.out.println("❌ Too many consecutive errors (" + consecutiveErrors + "), but continuing...");
                        consecutiveErrors = 0; // Reset to continue trying
                    }

                    // Use longer sleep on errors
                    Thread.sleep(OPPONENT_TURN_POLL_MS);
                    continue;
                }

                // Reset error counter on successful poll
                consecutiveErrors = 0;
                lastSuccessfulPoll = System.currentTimeMillis();
                long pollTime = System.currentTimeMillis() - pollStart;

                JsonObject game = gson.fromJson(gameData, JsonObject.class);

                // Check if both players are connected
                if (!game.has("bothConnected") || !game.get("bothConnected").getAsBoolean()) {
                    System.out.println("⏳ Waiting for both players to connect...");
                    Thread.sleep(1000);
                    continue;
                }

                String turn = game.get("turn").getAsString();
                String board = game.get("board").getAsString();
                long timeRemaining = game.get("time").getAsLong();

                // Determine if it's our turn
                boolean wasOurTurn = isOurTurn;
                isOurTurn = (player == 0 && turn.equals("r")) || (player == 1 && turn.equals("b"));

                // OPTIMIZATION: Show turn transition messages and update network settings
                if (isOurTurn && !wasOurTurn) {
                    System.out.println("\n🔔 IT'S YOUR TURN! Switching to fast polling (" + OUR_TURN_POLL_MS + "ms)");
                    System.out.println("🎯 Turn: " + turn + ", Time: " + formatTime(timeRemaining));
                    System.out.println("📋 Board: " + board);

                    // Update network timeout for our turn
                    try {
                        network.setSocketTimeout(1000);
                        System.out.println("⚡ Network timeout reduced to 1000ms (our turn)");
                    } catch (Exception e) {
                        System.out.println("⚠️ Could not update timeout: " + e.getMessage());
                    }

                } else if (!isOurTurn && wasOurTurn) {
                    System.out.println("💤 Opponent's turn - switching to slow polling (" + OPPONENT_TURN_POLL_MS + "ms)");
                    System.out.println("⏰ Socket timeout: " + SOCKET_TIMEOUT_MS + "ms");

                    // Update network timeout for opponent's turn
                    try {
                        network.setSocketTimeout(SOCKET_TIMEOUT_MS);
                        System.out.println("🕒 Network timeout increased to " + SOCKET_TIMEOUT_MS + "ms (opponent's turn)");
                    } catch (Exception e) {
                        System.out.println("⚠️ Could not update timeout: " + e.getMessage());
                    }

                    // Show network health status during transition
                    if (!network.isConnectionHealthy()) {
                        System.out.println("⚠️ Network connection quality degraded: " + network.getConnectionStats());
                    }
                }

                // Check if it's our turn (original logic)
                if (isOurTurn) {
                    moveNumber++;
                    lastMoveStartTime = System.currentTimeMillis();

                    System.out.println("\n" + "=".repeat(60));
                    System.out.println("🎯 MOVE " + moveNumber + " - " + (player == 0 ? "RED" : "BLUE"));
                    System.out.printf("📡 Network response time: %dms%n", pollTime);
                    System.out.println("=".repeat(60));

                    // Parse board state
                    GameState currentState = GameState.fromFen(board);
                    if (currentState == null) {
                        System.err.println("❌ Invalid board FEN: " + board);
                        continue;
                    }

                    currentState.printBoard();
                    validateEvaluation(currentState);

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

                        // Send move in original working format
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
                    // OPTIMIZATION: Reduced opponent turn messages
                    if (pollCount % 20 == 0 || pollTime > 1000) { // Only show every 20th poll or slow polls
                        System.out.printf("💭 Waiting for opponent... (poll #%d, response: %dms, time left: %s)%n",
                                pollCount, pollTime, formatTime(timeRemaining));

                        // Periodically check and report network health
                        if (pollCount % 100 == 0 && !network.isConnectionHealthy()) {
                            System.out.println("📊 Network status: " + network.getConnectionStats());

                            // Auto-adjust timeout if connection quality is poor
                            network.autoAdjustTimeout();
                        }
                    }
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

                // OPTIMIZATION: Adaptive sleep based on whose turn it is
                int sleepTime = isOurTurn ? OUR_TURN_POLL_MS : OPPONENT_TURN_POLL_MS;
                Thread.sleep(sleepTime);

            } catch (Exception e) {
                handleCriticalError("Critical error", e, network);

                try {
                    Thread.sleep(OPPONENT_TURN_POLL_MS); // Conservative sleep on errors
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        }

        System.out.println("🎮 Game Client shutting down...");
        network.close();
        printFinalStatistics();
        printNetworkStatistics();
    }

    /**
     * ENHANCED: Configure network timeouts with new Network.java features
     */
    private static void configureNetworkTimeouts(Network network) {
        try {
            // Set initial timeout
            int initialTimeout = isOurTurn ? 1000 : SOCKET_TIMEOUT_MS;
            network.setSocketTimeout(initialTimeout);
            System.out.println("⚙️ Network configured - timeout: " + initialTimeout + "ms");
            System.out.println("📊 Connection stats: " + network.getConnectionStats());
        } catch (Exception e) {
            System.out.println("⚠️ Could not configure network timeouts: " + e.getMessage());
        }
    }

    /**
     * ENHANCED: Get game data with adaptive timeout handling using new Network.java
     */
    private static String getGameDataWithAdaptiveTimeout(Network network) {
        try {
            // Update socket timeout based on whose turn it is
            int adaptiveTimeout = isOurTurn ? 1000 : SOCKET_TIMEOUT_MS;
            network.setSocketTimeout(adaptiveTimeout);

            return network.send(gson.toJson("get"));

        } catch (IllegalArgumentException e) {
            System.err.println("❌ Invalid timeout value: " + e.getMessage());
            // Fallback to safe timeout
            try {
                network.setSocketTimeout(2000);
                return network.send(gson.toJson("get"));
            } catch (Exception fallbackE) {
                System.err.println("❌ Fallback network call failed: " + fallbackE.getMessage());
                return null;
            }
        } catch (Exception e) {
            System.err.println("❌ Network error: " + e.getMessage());
            consecutiveErrors++;
            return null;
        }
    }

    /**
     * ENHANCED: Handle network timeout gracefully with adaptive response
     */
    private static void handleNetworkTimeout() {
        consecutiveErrors++;

        if (!isOurTurn) {
            // During opponent's turn, timeouts are normal and expected
            if (consecutiveErrors == 1) {
                System.out.println("⏳ Network timeout (opponent thinking) - this is normal");
            } else if (consecutiveErrors % 10 == 0) {
                // Show status every 10 timeouts during opponent's turn
                System.out.printf("💤 Extended opponent thinking... (timeout #%d)%n", consecutiveErrors);
            }
        } else {
            // During our turn, timeouts are concerning
            System.out.printf("⚠️ Network timeout during our turn (error #%d) - this may indicate server issues%n", consecutiveErrors);
        }
    }

    /**
     * ENHANCED: Handle critical errors with network diagnostics
     */
    private static void handleCriticalError(String message, Exception e, Network network) {
        consecutiveErrors++;
        System.err.println("❌ " + message + " (error #" + consecutiveErrors + "): " + e.getMessage());

        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            System.err.println("⚠️ Multiple consecutive errors - checking connection quality");
            long timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessfulPoll;
            if (timeSinceLastSuccess > 30000) { // 30 seconds
                System.err.println("🚨 No successful poll for " + (timeSinceLastSuccess/1000) + " seconds!");
            }

            // Show detailed network diagnostics
            System.err.println("📊 Network diagnostics: " + network.getConnectionStats());

            if (!network.isConnectionHealthy()) {
                System.err.println("🩺 Connection health check FAILED");

                // Try to auto-adjust timeout
                try {
                    System.err.println("🔧 Attempting network auto-adjustment...");
                    network.autoAdjustTimeout();
                } catch (Exception adjustE) {
                    System.err.println("❌ Auto-adjustment failed: " + adjustE.getMessage());
                }
            }
        }
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

        System.out.println("   🧠 AI Engine: Unified Evaluator (OPTIMIZED)");
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

    /**
     * ENHANCED: Print network performance statistics using new Network.java features
     */
    private static void printNetworkStatistics() {
        System.out.println("\n📡 NETWORK STATISTICS:");
        System.out.println("   📊 GameClient polls: " + pollCount);
        System.out.println("   ❌ GameClient errors: " + consecutiveErrors);

        if (pollCount > 0) {
            double errorRate = 100.0 * consecutiveErrors / pollCount;
            System.out.printf("   📈 GameClient error rate: %.1f%%%n", errorRate);

            if (errorRate < 5) {
                System.out.println("   ✅ Excellent client-side stability!");
            } else if (errorRate < 15) {
                System.out.println("   ⚠️ Some client-side issues occurred");
            } else {
                System.out.println("   ❌ Significant client-side problems");
            }
        }

        long totalTime = System.currentTimeMillis() - (lastSuccessfulPoll - 180_000);
        if (totalTime > 0 && pollCount > 0) {
            double avgPollInterval = (double) totalTime / pollCount;
            System.out.printf("   ⏱️ Average poll interval: %.1fms%n", avgPollInterval);
        }

        System.out.println("   🔌 Network layer statistics:");
        System.out.println("      (Network layer stats printed during shutdown)");
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
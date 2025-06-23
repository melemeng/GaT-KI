
package client;

import java.util.List;

import GaT.search.MoveGenerator;
import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;
import GaT.engine.TimeManager;
import GaT.engine.TimedMinimax; // FIXED: Use new implementation
import GaT.search.Minimax;
import GaT.search.QuiescenceSearch;
import GaT.evaluation.Evaluator; // FIXED: Use improved evaluator
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class GameClient {
    private static final Gson gson = new Gson();

    // Game statistics tracking
    private static int moveNumber = 0;
    private static long lastMoveStartTime = 0;
    private static TimeManager timeManager = new TimeManager(180000, 50);

    // FIXED: Use improved evaluator
    private static Evaluator evaluator = new Evaluator();

    public static void main(String[] args) {
        boolean running = true;
        Network network = new Network();
        int player = Integer.parseInt(network.getP());
        System.out.println("🎮 You are player " + player + " (" + (player == 0 ? "RED" : "BLUE") + ")");
        System.out.println("🚀 Using FIXED AI Engine with improved evaluation");

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

                    // Only act when it's our turn
                    if ((player == 0 && turn.equals("r")) || (player == 1 && turn.equals("b"))) {
                        moveNumber++;
                        System.out.println("\n" + "=".repeat(50));
                        System.out.println("🔄 Move " + moveNumber + " - " + (player == 0 ? "RED" : "BLUE"));
                        System.out.println("📋 Board: " + board);
                        System.out.println("⏱️ Time Remaining: " + formatTime(timeRemaining));

                        // Record move start time
                        lastMoveStartTime = System.currentTimeMillis();

                        // FIXED: Get AI move using fixed implementation
                        String move = getFixedAIMove(board, player, timeRemaining);

                        // Calculate actual time used
                        long actualTimeUsed = System.currentTimeMillis() - lastMoveStartTime;

                        // Send move to server
                        network.send(gson.toJson(move));
                        System.out.println("📤 Sent move: " + move);
                        System.out.println("⏱️ Actual time used: " + actualTimeUsed + "ms");

                        // Update time manager
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

                    // Print final game statistics
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
     * FIXED AI move calculation with all improvements
     */
    private static String getFixedAIMove(String board, int player, long timeLeft) {
        try {
            GameState state = GameState.fromFen(board);

            // FIXED: Update all time-aware components with improved evaluator
            timeManager.updateRemainingTime(timeLeft);
            Minimax.setRemainingTime(timeLeft);
            QuiescenceSearch.setRemainingTime(timeLeft);
            Evaluator.setRemainingTime(timeLeft); // FIXED: Use improved evaluator

            // Get sophisticated time allocation with better constraints
            long rawTimeForMove = timeManager.calculateTimeForMove(state);

            // FIXED: Better time management - more conservative
            long timeForMove = Math.min(rawTimeForMove, timeLeft / 8); // Never use more than 1/8 of remaining time
            timeForMove = Math.max(timeForMove, 500); // Minimum 500ms
            timeForMove = Math.min(timeForMove, 8000); // Maximum 8 seconds per move

            System.out.println("🧠 FIXED AI Analysis:");
            System.out.println("   ⏰ Time allocated: " + timeForMove + "ms (conservative)");
            System.out.println("   🎯 Strategy: ULTIMATE FIXED (PVS + Quiescence + Stable Evaluation)");
            System.out.println("   🎮 Phase: " + timeManager.getCurrentPhase());
            System.out.println("   🔧 Engine: FixedTimedMinimax with ImprovedEvaluator");

            // FIXED: Use the fixed search implementation
            long searchStartTime = System.currentTimeMillis();

            Move bestMove = TimedMinimax.findBestMoveUltimate(state, 99, timeForMove);

            long searchTime = System.currentTimeMillis() - searchStartTime;

            // FIXED: Better validation and fallback
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);

            if (bestMove == null || !legalMoves.contains(bestMove)) {
                System.out.println("⚠️ WARNING: AI returned invalid move! Using intelligent fallback...");
                bestMove = findIntelligentFallback(state, legalMoves);
            }

            // Log search efficiency
            System.out.println("   ✅ FIXED Search completed in: " + searchTime + "ms");
            System.out.printf("   📊 Efficiency: %.1f%% of allocated time | Nodes: %,d%n",
                    100.0 * searchTime / timeForMove, TimedMinimax.getStatistics().getTotalNodes());

            if (searchTime < timeForMove * 0.5) {
                System.out.println("   ⚡ Efficient search - good time management");
            } else if (searchTime > timeForMove * 0.95) {
                System.out.println("   ⏳ Full search - used available time well");
            }

            return bestMove.toString();

        } catch (Exception e) {
            System.err.println("❌ Error in FIXED AI move calculation: " + e.getMessage());
            e.printStackTrace();

            // FIXED: Robust emergency fallback
            return getEmergencyFallback(board);
        }
    }

    /**
     * FIXED: Intelligent fallback move selection
     */
    private static Move findIntelligentFallback(GameState state, List<Move> legalMoves) {
        if (legalMoves.isEmpty()) {
            throw new IllegalStateException("No legal moves available!");
        }

        System.out.println("🔍 Finding intelligent fallback from " + legalMoves.size() + " moves");

        // Priority 1: Winning moves (guard to enemy castle)
        boolean isRed = state.redToMove;
        int enemyCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);

        for (Move move : legalMoves) {
            if (move.to == enemyCastle && isGuardMove(move, state)) {
                System.out.println("🎯 Fallback: Found winning move " + move);
                return move;
            }
        }

        // Priority 2: Guard captures
        for (Move move : legalMoves) {
            if (capturesEnemyGuard(move, state)) {
                System.out.println("🎯 Fallback: Found guard capture " + move);
                return move;
            }
        }

        // Priority 3: Any captures
        for (Move move : legalMoves) {
            if (isCapture(move, state)) {
                System.out.println("🎯 Fallback: Found capture " + move);
                return move;
            }
        }

        // Priority 4: Guard moves (usually good)
        for (Move move : legalMoves) {
            if (isGuardMove(move, state)) {
                System.out.println("🛡️ Fallback: Found guard move " + move);
                return move;
            }
        }

        // Priority 5: Central moves
        for (Move move : legalMoves) {
            if (isCentralMove(move)) {
                System.out.println("🎯 Fallback: Found central move " + move);
                return move;
            }
        }

        // Priority 6: Forward moves (advancement)
        for (Move move : legalMoves) {
            if (isAdvancementMove(move, state)) {
                System.out.println("⬆️ Fallback: Found advancement move " + move);
                return move;
            }
        }

        // Last resort: first legal move
        Move fallback = legalMoves.get(0);
        System.out.println("🎲 Fallback: Using first legal move " + fallback);
        return fallback;
    }

    /**
     * FIXED: Emergency fallback when everything fails
     */
    private static String getEmergencyFallback(String board) {
        try {
            System.out.println("🚨 EMERGENCY: Using basic move generation");
            GameState state = GameState.fromFen(board);
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);

            if (!legalMoves.isEmpty()) {
                Move emergencyMove = findIntelligentFallback(state, legalMoves);
                System.out.println("🚨 Emergency move selected: " + emergencyMove);
                return emergencyMove.toString();
            }
        } catch (Exception fallbackError) {
            System.err.println("❌ Even emergency fallback failed: " + fallbackError.getMessage());
        }

        // Absolute last resort
        System.err.println("🆘 CRITICAL: Using hardcoded emergency move");
        return "A1-A2-1";
    }

    // === HELPER METHODS FOR MOVE CLASSIFICATION ===

    private static boolean isGuardMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
    }

    private static boolean capturesEnemyGuard(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        return (enemyGuard & GameState.bit(move.to)) != 0;
    }

    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    private static boolean isCentralMove(Move move) {
        int file = GameState.file(move.to);
        int rank = GameState.rank(move.to);
        return file >= 2 && file <= 4 && rank >= 2 && rank <= 4;
    }

    private static boolean isAdvancementMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        int fromRank = GameState.rank(move.from);
        int toRank = GameState.rank(move.to);

        // Moving forward
        return isRed ? (toRank < fromRank) : (toRank > fromRank);
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
     * FIXED: Print comprehensive game statistics
     */
    private static void printGameStatistics(long finalTimeRemaining) {
        System.out.println("\n📊 GAME STATISTICS (FIXED ENGINE):");
        System.out.println("   🎮 Total moves played: " + moveNumber);
        System.out.println("   ⏱️ Final time remaining: " + formatTime(finalTimeRemaining));

        if (moveNumber > 0) {
            long totalTimeUsed = 180_000 - finalTimeRemaining;
            long averageTimePerMove = totalTimeUsed / moveNumber;
            System.out.println("   ⚡ Average time per move: " + averageTimePerMove + "ms");

            if (finalTimeRemaining < 10_000) {
                System.out.println("   ⚠️ Game ended in time pressure!");
            } else if (finalTimeRemaining > 60_000) {
                System.out.println("   😎 Excellent time management");
            } else {
                System.out.println("   ✅ Good time management");
            }
        }

        System.out.println("   🧠 AI Engine: FIXED FixedTimedMinimax");
        System.out.println("   📊 Evaluator: ImprovedEvaluator (stable & strong)");
        System.out.println("   🎯 Strategy: PVS + Quiescence + Time-Aware");
        System.out.println("   🔧 Features: Robust search, intelligent fallbacks, conservative time management");

        // Display search statistics if available
        try {
            var stats = TimedMinimax.getStatistics();
            if (stats.getTotalNodes() > 0) {
                System.out.printf("   📈 Total nodes searched: %,d%n", stats.getTotalNodes());
                System.out.printf("   🚀 Average nodes per second: %.0f%n", stats.getNodesPerSecond());
            }
        } catch (Exception e) {
            // Ignore statistics errors
        }
    }
}
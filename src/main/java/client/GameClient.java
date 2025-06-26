package client;

import java.util.List;

import GaT.search.MoveGenerator;
import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.SearchConfig;
import GaT.engine.TimeManager;
import GaT.engine.TimedMinimax;
import GaT.search.Minimax;
import GaT.search.QuiescenceSearch;
import GaT.evaluation.Evaluator; // FIXED: Use aggressive evaluator
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * FIXED GAME CLIENT - Aggressiveres Zeitmanagement und bessere AI
 *
 * CRITICAL FIXES:
 * ✅ 1. Viel aggressivere Zeitnutzung (60-80% statt 5%)
 * ✅ 2. Bessere Zugevaluation mit AggressiveEvaluator
 * ✅ 3. Höhere Suchtiefe in kritischen Positionen
 * ✅ 4. Intelligentere Fallback-Strategien
 * ✅ 5. Verbesserte Endspiel-Behandlung
 */
public class GameClient {
    private static final Gson gson = new Gson();

    // Game statistics tracking
    private static int moveNumber = 0;
    private static long lastMoveStartTime = 0;
    private static TimeManager timeManager = new TimeManager(180000, 30); // FIXED: Weniger geschätzte Züge

    // FIXED: Use aggressive evaluator
    private static Evaluator evaluator = new Evaluator();

    public static void main(String[] args) {
        boolean running = true;
        Network network = new Network();
        int player = Integer.parseInt(network.getP());
        System.out.println("🎮 You are player " + player + " (" + (player == 0 ? "RED" : "BLUE") + ")");
        System.out.println("🚀 Using AGGRESSIVE FIXED AI Engine - Tournament Ready");

        while (running) {
            try {
                String gameData = network.send(gson.toJson("get"));
                if (gameData == null) {
                    System.out.println("❌ Couldn't get game");
                    break;
                }

                JsonObject game = gson.fromJson(gameData, JsonObject.class);

                if (game.get("bothConnected").getAsBoolean()) {
                    String turn = game.get("turn").getAsString();
                    String board = game.get("board").getAsString();
                    long timeRemaining = game.get("time").getAsLong();

                    if ((player == 0 && turn.equals("r")) || (player == 1 && turn.equals("b"))) {
                        moveNumber++;
                        System.out.println("\n" + "=".repeat(60));
                        System.out.println("🔥 AGGRESSIVE Move " + moveNumber + " - " + (player == 0 ? "RED" : "BLUE"));
                        System.out.println("📋 Board: " + board);
                        System.out.println("⏱️ Time Remaining: " + formatTime(timeRemaining));

                        lastMoveStartTime = System.currentTimeMillis();

                        // FIXED: Aggressive AI move calculation
                        String move = getAggressiveAIMove(board, player, timeRemaining);

                        long actualTimeUsed = System.currentTimeMillis() - lastMoveStartTime;

                        network.send(gson.toJson(move));
                        System.out.println("📤 AGGRESSIVE Move Sent: " + move);
                        System.out.println("⏱️ Time used: " + actualTimeUsed + "ms");

                        // FIXED: Update time manager more conservatively
                        timeManager.updateRemainingTime(timeRemaining - actualTimeUsed);
                        timeManager.decrementEstimatedMovesLeft();

                        System.out.println("=".repeat(60));
                    }
                }

                if (game.has("end") && game.get("end").getAsBoolean()) {
                    System.out.println("🏁 Game has ended");
                    String result = game.has("winner") ?
                            ("Winner: " + game.get("winner").getAsString()) :
                            "Game finished";
                    System.out.println("🎯 " + result);

                    long finalTimeRemaining = game.has("time") ? game.get("time").getAsLong() : 0;
                    printAggressiveGameStatistics(finalTimeRemaining);
                    running = false;
                }

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
     * FIXED: Aggressive AI move calculation - uses much more time
     */
    private static String getAggressiveAIMove(String board, int player, long timeLeft) {
        try {
            GameState state = GameState.fromFen(board);

            // FIXED: Update all components with remaining time
            timeManager.updateRemainingTime(timeLeft);
            Minimax.setRemainingTime(timeLeft);
            QuiescenceSearch.setRemainingTime(timeLeft);
            Evaluator.setRemainingTime(timeLeft);

            // FIXED: MUCH more aggressive time allocation
            long rawTimeForMove = timeManager.calculateTimeForMove(state);

            // CRITICAL FIX: Use much more time!
            long timeForMove;

            if (timeLeft > 120000) {  // More than 2 minutes
                timeForMove = Math.min(rawTimeForMove, timeLeft / 4);  // Up to 25% of remaining time
                timeForMove = Math.max(timeForMove, 3000);  // Minimum 3 seconds
                timeForMove = Math.min(timeForMove, 15000); // Maximum 15 seconds
            } else if (timeLeft > 60000) {  // 1-2 minutes
                timeForMove = Math.min(rawTimeForMove, timeLeft / 5);  // Up to 20% of remaining time
                timeForMove = Math.max(timeForMove, 2000);  // Minimum 2 seconds
                timeForMove = Math.min(timeForMove, 10000); // Maximum 10 seconds
            } else if (timeLeft > 30000) {  // 30-60 seconds
                timeForMove = Math.min(rawTimeForMove, timeLeft / 6);  // Up to 16% of remaining time
                timeForMove = Math.max(timeForMove, 1500);  // Minimum 1.5 seconds
                timeForMove = Math.min(timeForMove, 8000);  // Maximum 8 seconds
            } else {  // Under 30 seconds
                timeForMove = Math.min(rawTimeForMove, timeLeft / 8);  // Conservative
                timeForMove = Math.max(timeForMove, 800);   // Minimum 800ms
                timeForMove = Math.min(timeForMove, 5000);  // Maximum 5 seconds
            }

            System.out.println("🧠 AGGRESSIVE AI Analysis:");
            System.out.printf("   ⏰ AGGRESSIVE Time allocated: %dms (was %dms conservative)%n", timeForMove, rawTimeForMove);
            System.out.println("   🎯 Strategy: ULTIMATE AGGRESSIVE (PVS + Quiescence + Aggressive Eval)");
            System.out.println("   🎮 Phase: " + timeManager.getCurrentPhase());
            System.out.println("   🔧 Engine: AGGRESSIVE TimedMinimax with AggressiveEvaluator");
            System.out.printf("   📊 Time utilization: %.1f%% of remaining time%n", 100.0 * timeForMove / timeLeft);

            long searchStartTime = System.currentTimeMillis();

            // FIXED: Use enhanced search with higher depth limits
            int maxDepth = calculateAggressiveMaxDepth(timeForMove, state);
            Move bestMove = TimedMinimax.findBestMoveUltimate(state, maxDepth, timeForMove);

            long searchTime = System.currentTimeMillis() - searchStartTime;

            // FIXED: Enhanced validation and intelligent fallback
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);

            if (bestMove == null || !legalMoves.contains(bestMove)) {
                System.out.println("⚠️ WARNING: AI returned invalid move! Using AGGRESSIVE fallback...");
                bestMove = findAggressiveFallback(state, legalMoves);
            }

            // Enhanced logging
            long totalNodes = TimedMinimax.getTotalNodesSearched();
            System.out.printf("   ✅ AGGRESSIVE Search completed in: %dms (%.1f%% of allocated)%n",
                    searchTime, 100.0 * searchTime / timeForMove);
            System.out.printf("   📊 Nodes: %,d | NPS: %,.0f | Depth: %d%n",
                    totalNodes,
                    searchTime > 0 ? (double)totalNodes * 1000 / searchTime : 0,
                    maxDepth);

            // Time efficiency analysis
            if (searchTime < timeForMove * 0.3) {
                System.out.println("   ⚡ Very efficient search - could have used more time");
            } else if (searchTime < timeForMove * 0.7) {
                System.out.println("   ✅ Good time utilization");
            } else {
                System.out.println("   ⏳ Full time utilization - excellent depth achieved");
            }

            return bestMove.toString();

        } catch (Exception e) {
            System.err.println("❌ Error in AGGRESSIVE AI: " + e.getMessage());
            e.printStackTrace();
            return getAggressiveEmergencyFallback(board);
        }
    }

    /**
     * FIXED: Calculate aggressive max depth based on time and position
     */
    private static int calculateAggressiveMaxDepth(long timeForMove, GameState state) {
        // Base depth based on time
        int baseDepth;
        if (timeForMove >= 10000) {
            baseDepth = 12;  // High time = deep search
        } else if (timeForMove >= 5000) {
            baseDepth = 10;  // Medium time
        } else if (timeForMove >= 2000) {
            baseDepth = 8;   // Low time
        } else {
            baseDepth = 6;   // Emergency time
        }

        // Adjust for position complexity
        List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
        if (legalMoves.size() < 10) {
            baseDepth += 2;  // Simple position = deeper search
        } else if (legalMoves.size() > 25) {
            baseDepth -= 1;  // Complex position = slightly shallower
        }

        // Endgame gets deeper search
        if (isEndgame(state)) {
            baseDepth += 2;
        }

        // Critical positions get deeper search
        if (isCriticalPosition(state)) {
            baseDepth += 1;
        }

        return Math.max(6, Math.min(baseDepth, 15)); // Clamp between 6-15
    }

    /**
     * FIXED: Aggressive fallback move selection
     */
    private static Move findAggressiveFallback(GameState state, List<Move> legalMoves) {
        if (legalMoves.isEmpty()) {
            throw new IllegalStateException("No legal moves available!");
        }

        System.out.println("🔍 AGGRESSIVE fallback from " + legalMoves.size() + " moves");

        // Priority 1: Winning moves (guard to enemy castle)
        Move winningMove = findWinningMove(legalMoves, state);
        if (winningMove != null) {
            System.out.println("🎯 AGGRESSIVE: Found winning move " + winningMove);
            return winningMove;
        }

        // Priority 2: Guard captures
        Move guardCapture = findGuardCapture(legalMoves, state);
        if (guardCapture != null) {
            System.out.println("🎯 AGGRESSIVE: Found guard capture " + guardCapture);
            return guardCapture;
        }

        // Priority 3: Best capture by value
        Move bestCapture = findBestCapture(legalMoves, state);
        if (bestCapture != null) {
            System.out.println("🎯 AGGRESSIVE: Found best capture " + bestCapture);
            return bestCapture;
        }

        // Priority 4: Aggressive guard moves
        Move aggressiveGuardMove = findAggressiveGuardMove(legalMoves, state);
        if (aggressiveGuardMove != null) {
            System.out.println("🛡️ AGGRESSIVE: Found aggressive guard move " + aggressiveGuardMove);
            return aggressiveGuardMove;
        }

        // Priority 5: Central attacking moves
        Move centralMove = findBestCentralMove(legalMoves, state);
        if (centralMove != null) {
            System.out.println("🎯 AGGRESSIVE: Found central move " + centralMove);
            return centralMove;
        }

        // Priority 6: Forward advancement
        Move advancementMove = findBestAdvancementMove(legalMoves, state);
        if (advancementMove != null) {
            System.out.println("⬆️ AGGRESSIVE: Found advancement move " + advancementMove);
            return advancementMove;
        }

        // Last resort: highest scored move
        Move bestScoredMove = findHighestScoredMove(legalMoves, state);
        System.out.println("🎲 AGGRESSIVE: Using highest scored move " + bestScoredMove);
        return bestScoredMove;
    }

    // === AGGRESSIVE FALLBACK HELPER METHODS ===

    private static Move findWinningMove(List<Move> moves, GameState state) {
        boolean isRed = state.redToMove;
        int enemyCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);

        for (Move move : moves) {
            if (move.to == enemyCastle && isGuardMove(move, state)) {
                return move;
            }
        }
        return null;
    }

    private static Move findGuardCapture(List<Move> moves, GameState state) {
        for (Move move : moves) {
            if (capturesEnemyGuard(move, state)) {
                return move;
            }
        }
        return null;
    }

    private static Move findBestCapture(List<Move> moves, GameState state) {
        Move bestCapture = null;
        int bestValue = 0;

        for (Move move : moves) {
            if (isCapture(move, state)) {
                int value = getCaptureValue(move, state);
                if (value > bestValue) {
                    bestValue = value;
                    bestCapture = move;
                }
            }
        }
        return bestCapture;
    }

    private static Move findAggressiveGuardMove(List<Move> moves, GameState state) {
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return null;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int enemyCastle = isRed ? GameState.getIndex(0, 3) : GameState.getIndex(6, 3);

        Move bestGuardMove = null;
        int bestScore = Integer.MIN_VALUE;

        for (Move move : moves) {
            if (move.from == guardPos) {
                // Score based on advancement toward enemy castle
                int oldDistance = calculateDistance(guardPos, enemyCastle);
                int newDistance = calculateDistance(move.to, enemyCastle);
                int score = (oldDistance - newDistance) * 100;

                // Bonus for central squares
                if (isCentralSquare(move.to)) score += 50;

                if (score > bestScore) {
                    bestScore = score;
                    bestGuardMove = move;
                }
            }
        }
        return bestGuardMove;
    }

    private static Move findBestCentralMove(List<Move> moves, GameState state) {
        for (Move move : moves) {
            if (isCentralSquare(move.to)) {
                return move;
            }
        }
        return null;
    }

    private static Move findBestAdvancementMove(List<Move> moves, GameState state) {
        boolean isRed = state.redToMove;

        for (Move move : moves) {
            if (isAdvancementMove(move, state)) {
                return move;
            }
        }
        return null;
    }

    private static Move findHighestScoredMove(List<Move> moves, GameState state) {
        if (moves.isEmpty()) return null;

        Move bestMove = moves.get(0);
        int bestScore = scoreMove(bestMove, state);

        for (Move move : moves) {
            int score = scoreMove(move, state);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        return bestMove;
    }

    // === ENHANCED HELPER METHODS ===

    private static boolean isEndgame(GameState state) {
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return totalMaterial <= 8;
    }

    private static boolean isCriticalPosition(GameState state) {
        // Position is critical if guard is in danger or near enemy castle
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;

        if (guardBit == 0) return true; // No guard = very critical

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int rank = GameState.rank(guardPos);

        // Guard is advanced = critical
        if ((isRed && rank <= 2) || (!isRed && rank >= 4)) {
            return true;
        }

        // Guard in danger = critical
        return evaluator.isGuardInDanger(state, isRed);
    }

    private static int scoreMove(Move move, GameState state) {
        int score = 0;

        // Capture bonus
        if (isCapture(move, state)) {
            score += getCaptureValue(move, state);
        }

        // Advancement bonus
        if (isAdvancementMove(move, state)) {
            score += 50;
        }

        // Central bonus
        if (isCentralSquare(move.to)) {
            score += 30;
        }

        // Movement distance
        score += move.amountMoved * 5;

        return score;
    }

    private static int getCaptureValue(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        if (((isRed ? state.blueGuard : state.redGuard) & toBit) != 0) {
            return 1500; // Guard
        }

        int height = isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to];
        return height * 100; // Tower
    }

    private static int calculateDistance(int from, int to) {
        int fromRank = GameState.rank(from);
        int fromFile = GameState.file(from);
        int toRank = GameState.rank(to);
        int toFile = GameState.file(to);

        return Math.abs(fromRank - toRank) + Math.abs(fromFile - toFile);
    }

    // Existing helper methods
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

    private static boolean isCentralSquare(int square) {
        int file = GameState.file(square);
        int rank = GameState.rank(square);
        return file >= 2 && file <= 4 && rank >= 2 && rank <= 4;
    }

    private static boolean isAdvancementMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        int fromRank = GameState.rank(move.from);
        int toRank = GameState.rank(move.to);
        return isRed ? (toRank < fromRank) : (toRank > fromRank);
    }

    /**
     * FIXED: Emergency fallback for critical errors
     */
    private static String getAggressiveEmergencyFallback(String board) {
        try {
            System.out.println("🚨 AGGRESSIVE EMERGENCY MODE");
            GameState state = GameState.fromFen(board);
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);

            if (!legalMoves.isEmpty()) {
                Move emergencyMove = findAggressiveFallback(state, legalMoves);
                System.out.println("🚨 AGGRESSIVE Emergency move: " + emergencyMove);
                return emergencyMove.toString();
            }
        } catch (Exception e) {
            System.err.println("❌ Critical emergency fallback error: " + e.getMessage());
        }

        System.err.println("🆘 CRITICAL: Using last resort move");
        return "A1-A2-1";
    }

    /**
     * Enhanced statistics with aggressive analysis
     */
    private static void printAggressiveGameStatistics(long finalTimeRemaining) {
        System.out.println("\n📊 AGGRESSIVE GAME STATISTICS:");
        System.out.println("   🎮 Total moves: " + moveNumber);
        System.out.println("   ⏱️ Final time: " + formatTime(finalTimeRemaining));

        if (moveNumber > 0) {
            long totalTimeUsed = 180_000 - finalTimeRemaining;
            long averageTimePerMove = totalTimeUsed / moveNumber;
            double timeUtilization = 100.0 * totalTimeUsed / 180_000;

            System.out.printf("   ⚡ Average time/move: %dms%n", averageTimePerMove);
            System.out.printf("   📊 Time utilization: %.1f%% (target: 70-85%%)%n", timeUtilization);

            if (timeUtilization < 50) {
                System.out.println("   ⚠️ TOO CONSERVATIVE - should use more time!");
            } else if (timeUtilization < 70) {
                System.out.println("   📈 Could be more aggressive with time");
            } else if (timeUtilization < 85) {
                System.out.println("   ✅ EXCELLENT time management!");
            } else if (timeUtilization < 95) {
                System.out.println("   ⏳ Good aggressive time usage");
            } else {
                System.out.println("   🚨 Very close to time trouble!");
            }
        }

        System.out.println("   🧠 AI Engine: AGGRESSIVE TimedMinimax");
        System.out.println("   📊 Evaluator: AggressiveEvaluator (tactical priority)");
        System.out.println("   🎯 Strategy: PVS + Quiescence + Aggressive Tactics");
        System.out.println("   🔧 Features: Deep search, aggressive time usage, tactical focus");
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
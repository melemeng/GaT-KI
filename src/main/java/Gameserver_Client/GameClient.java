package Gameserver_Client;

import java.util.Scanner;

import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.TimeManager;
import GaT.TimedMinimax;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class GameClient {
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        boolean running = true;
        Network network = new Network();
        int player = Integer.parseInt(network.getP());
        System.out.println("You are player " + player);


        while (running) {
            try {
                // Request game state
                String gameData = network.send(gson.toJson("get"));
                if (gameData == null) {
                    System.out.println("Couldn't get game");
                    break;
                }

                // Parse game state
                JsonObject game = gson.fromJson(gameData, JsonObject.class);

                // Check if both players are connected
                if (game.get("bothConnected").getAsBoolean()) {
                    String turn = game.get("turn").getAsString();
                    String board = game.get("board").getAsString();
                    long time = game.get("time").getAsLong();

                    // Only act when it's our turn
                    if ((player == 0 && turn.equals("r")) || (player == 1 && turn.equals("b"))) {
                        System.out.println("New Board: " + board);
                        System.out.println("New Time: " + time);

                        String move = getAIMove(board, player, time);

                        // Send move to server
                        network.send(gson.toJson(move));
                        System.out.println("Sent move: "+ move);
                        timeManager.decrementEstimatedMovesLeft();
                    }
                }

                // Check if game has ended
                if (game.has("end") && game.get("end").getAsBoolean()) {
                    System.out.println("Game has ended");
                    running = false;
                }

                // Small delay to avoid busy-waiting
                Thread.sleep(100);

            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
                running = false;
                break;
            }
        }

    }

    static TimeManager timeManager = new TimeManager(180, 60);

    // Method to integrate with your AI
    private static String getAIMove(String board, int player, long timeLeft) {
        GameState state = GameState.fromFen(board);
        timeManager.updateRemainingTime(timeLeft);
        long timeForMove = timeManager.calculateTimeForMove(state);
        System.out.println("Time Limit for this search: "+ timeForMove + " ms");
        Move bestMove = TimedMinimax.findBestMoveUltimate(state,99, timeForMove);
        return bestMove.toString();
    }
}





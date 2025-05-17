package Gameserver_Client;

import java.util.Scanner;

import GaT.Objects.GameState;
import GaT.Objects.Move;
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
                    int time = game.get("time").getAsInt();

                    // Only act when it's our turn
                    if ((player == 0 && turn.equals("r")) || (player == 1 && turn.equals("b"))) {
                        System.out.println("New Board: " + board);
                        System.out.println("New Time: " + time);

                        String move = getAIMove(board, player, time);

                        // Send move to server
                        network.send(gson.toJson(move));
                        System.out.println("Sent move: "+ move);
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

    // Method to integrate with your AI
    private static String getAIMove(String board, int player, int timeLeft) {
        GameState state = GameState.fromFen(board);
        Move bestMove = TimedMinimax.findBestMoveWithTime(state,99, 2000);
        return bestMove.toString();
    }
}





package Gameserver_Client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Network {
    private Socket client;
    private String server;
    private int port;
    private String playerNumber;

    public Network() {
        this.server = "game.guard-and-towers.com";
        this.port = 35000;
        this.playerNumber = connect();
    }

    public String getP() {
        return playerNumber;
    }

    private String connect() {
        try {
            client = new Socket(server, port);
            byte[] buffer = new byte[2048];
            InputStream in = client.getInputStream();
            int bytesRead = in.read(buffer);
            return new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String send(String data) {
        try {
            // Send data to server
            OutputStream out = client.getOutputStream();
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            out.write(dataBytes);
            out.flush();

            // Receive response
            InputStream in = client.getInputStream();
            byte[] buffer = new byte[4096];
            int bytesRead = in.read(buffer);
            return new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}


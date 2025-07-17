package client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * OPTIMIZED NETWORK CLIENT for Guard & Towers Tournament
 *
 * Simplified version that keeps essential functionality:
 * ✅ Reliable socket connection
 * ✅ Adaptive timeout management
 * ✅ Basic error handling and recovery
 * ✅ Connection quality monitoring
 * ✅ Clean send/receive interface
 *
 * Removed complexity:
 * ❌ Complex buffer management
 * ❌ Excessive logging and statistics
 * ❌ Over-engineered timeout algorithms
 * ❌ Unnecessary connection diagnostics
 */
public class Network {

    // === CONNECTION CONFIGURATION ===
    private static final String SERVER = "game.guard-and-towers.com";
    private static final int PORT = 35000;

    // === TIMEOUT CONFIGURATION ===
    private static final int CONNECT_TIMEOUT = 10000;    // 10s for initial connect
    private static final int DEFAULT_TIMEOUT = 3000;     // 3s default
    private static final int MIN_TIMEOUT = 500;          // 0.5s minimum
    private static final int MAX_TIMEOUT = 30000;        // 30s maximum

    // === BUFFER CONFIGURATION ===
    private static final int BUFFER_SIZE = 4096;         // 4KB buffer

    // === CONNECTION STATE ===
    private Socket socket;
    private String playerNumber;
    private int currentTimeout = DEFAULT_TIMEOUT;

    // === STATISTICS (simplified) ===
    private int totalRequests = 0;
    private int totalTimeouts = 0;
    private long totalResponseTime = 0;

    /**
     * Constructor - automatically connects to server
     */
    public Network() {
        this.playerNumber = connect();
    }

    /**
     * Get player number received from server
     */
    public String getP() {
        return playerNumber;
    }

    /**
     * Connect to game server and get player number
     */
    private String connect() {
        try {
            System.out.println("🔌 Connecting to " + SERVER + ":" + PORT + "...");

            socket = new Socket(SERVER, PORT);
            socket.setSoTimeout(CONNECT_TIMEOUT);

            System.out.println("✅ Connected! Waiting for player number...");

            // Read player number from server
            byte[] buffer = new byte[BUFFER_SIZE];
            InputStream in = socket.getInputStream();

            long startTime = System.currentTimeMillis();
            int bytesRead = in.read(buffer);
            long connectTime = System.currentTimeMillis() - startTime;

            if (bytesRead > 0) {
                String playerNum = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8).trim();
                System.out.printf("🎮 Assigned player number: %s (received in %dms)%n", playerNum, connectTime);

                // Set default timeout after successful connect
                socket.setSoTimeout(DEFAULT_TIMEOUT);
                currentTimeout = DEFAULT_TIMEOUT;

                return playerNum;
            } else {
                System.err.println("❌ No data received during connection");
                return null;
            }

        } catch (IOException e) {
            System.err.println("❌ Connection failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Send data to server and receive response
     */
    public String send(String data) {
        if (socket == null || socket.isClosed()) {
            System.err.println("❌ Socket not connected");
            return null;
        }

        totalRequests++;
        long requestStart = System.currentTimeMillis();

        try {
            // Send data
            OutputStream out = socket.getOutputStream();
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            out.write(dataBytes);
            out.flush();

            // Receive response
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[BUFFER_SIZE];

            StringBuilder response = new StringBuilder();
            int bytesRead = in.read(buffer);

            if (bytesRead > 0) {
                response.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));

                // Check if more data is available (for large responses)
                while (in.available() > 0) {
                    bytesRead = in.read(buffer);
                    if (bytesRead > 0) {
                        response.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
                    } else {
                        break;
                    }
                }
            }

            // Record response time
            long responseTime = System.currentTimeMillis() - requestStart;
            totalResponseTime += responseTime;

            if (bytesRead <= 0) {
                System.err.println("❌ No response data received");
                return null;
            }

            return response.toString();

        } catch (SocketTimeoutException e) {
            totalTimeouts++;
            // Silent timeout handling - caller will handle this
            return null;

        } catch (IOException e) {
            System.err.println("❌ Network error: " + e.getMessage());

            // Provide helpful error context
            if (e.getMessage().contains("Connection reset")) {
                System.err.println("🔌 Server reset the connection");
            } else if (e.getMessage().contains("Broken pipe")) {
                System.err.println("📡 Connection was broken");
            }

            return null;
        }
    }

    /**
     * Set socket timeout dynamically
     */
    public void setSocketTimeout(int timeoutMs) {
        if (timeoutMs < MIN_TIMEOUT || timeoutMs > MAX_TIMEOUT) {
            throw new IllegalArgumentException(
                    String.format("Timeout must be between %d and %d ms, got: %d",
                            MIN_TIMEOUT, MAX_TIMEOUT, timeoutMs));
        }

        currentTimeout = timeoutMs;

        try {
            if (socket != null && !socket.isClosed()) {
                socket.setSoTimeout(timeoutMs);
            }
        } catch (IOException e) {
            System.err.println("⚠️ Could not set socket timeout: " + e.getMessage());
        }
    }

    /**
     * Get current socket timeout
     */
    public int getCurrentTimeout() {
        return currentTimeout;
    }

    /**
     * Check if connection appears healthy
     */
    public boolean isConnectionHealthy() {
        if (socket == null || socket.isClosed()) {
            return false;
        }

        // Consider connection unhealthy if timeout rate is very high
        if (totalRequests > 10) {
            double timeoutRate = (double) totalTimeouts / totalRequests;
            return timeoutRate < 0.7;  // Less than 70% timeout rate
        }

        return true;
    }

    /**
     * Get basic connection statistics
     */
    public String getConnectionStats() {
        if (totalRequests == 0) {
            return "No requests made yet";
        }

        double timeoutRate = (double) totalTimeouts / totalRequests * 100;
        double avgResponseTime = (double) totalResponseTime / Math.max(1, totalRequests - totalTimeouts);

        return String.format("Requests: %d, Timeouts: %d (%.1f%%), Avg response: %.0fms, Current timeout: %dms",
                totalRequests, totalTimeouts, timeoutRate, avgResponseTime, currentTimeout);
    }

    /**
     * Auto-adjust timeout based on connection performance
     */
    public void autoAdjustTimeout() {
        if (totalRequests < 5) return;  // Need some data first

        double timeoutRate = (double) totalTimeouts / totalRequests;
        double avgResponseTime = (double) totalResponseTime / Math.max(1, totalRequests - totalTimeouts);

        int newTimeout = currentTimeout;

        if (timeoutRate > 0.3) {  // High timeout rate
            newTimeout = Math.min(MAX_TIMEOUT, currentTimeout + 1000);  // Increase timeout
        } else if (timeoutRate < 0.1 && avgResponseTime < currentTimeout * 0.3) {  // Low timeout rate, fast responses
            newTimeout = Math.max(MIN_TIMEOUT, currentTimeout - 500);   // Decrease timeout
        }

        if (newTimeout != currentTimeout) {
            System.out.printf("🔧 Auto-adjusting timeout: %dms → %dms (timeout rate: %.1f%%)%n",
                    currentTimeout, newTimeout, timeoutRate * 100);
            try {
                setSocketTimeout(newTimeout);
            } catch (Exception e) {
                // Ignore adjustment errors
            }
        }
    }

    /**
     * Close the connection
     */
    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("🔌 Network connection closed");

                // Print final stats
                if (totalRequests > 0) {
                    System.out.println("📊 Final network stats: " + getConnectionStats());
                }
            }
        } catch (IOException e) {
            System.err.println("⚠️ Error closing socket: " + e.getMessage());
        }
    }

    /**
     * Check if socket is connected
     */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Get basic diagnostic info
     */
    public String getDiagnosticInfo() {
        if (socket == null) {
            return "Socket: null";
        }

        return String.format("Socket: %s, Connected: %s, Closed: %s, Timeout: %dms",
                socket.getRemoteSocketAddress(),
                socket.isConnected(),
                socket.isClosed(),
                currentTimeout);
    }
}
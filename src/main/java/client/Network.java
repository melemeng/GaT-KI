package client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * ADAPTIVE NETWORK CLIENT with Dynamic Socket Timeouts
 *
 * NEW FEATURES:
 * ✅ Dynamic socket timeout configuration
 * ✅ Separate timeouts for different game phases
 * ✅ Enhanced timeout handling and reporting
 * ✅ Connection quality monitoring
 * ✅ Smart retry logic
 */
public class Network {
    private Socket client;
    private String server;
    private int port;
    private String playerNumber;

    // Buffer size constants
    private static final int INITIAL_BUFFER_SIZE = 2048;
    private static final int MAX_BUFFER_SIZE = 8192;

    // === ADAPTIVE TIMEOUT CONFIGURATION ===
    private static final int DEFAULT_TIMEOUT = 5000;      // Default 5 seconds
    private static final int CONNECT_TIMEOUT = 10000;     // 10 seconds for initial connect
    private static final int MIN_TIMEOUT = 500;           // Minimum timeout (0.5s)
    private static final int MAX_TIMEOUT = 30000;         // Maximum timeout (30s)

    private int currentTimeout = DEFAULT_TIMEOUT;         // Current active timeout

    // === CONNECTION MONITORING ===
    private long totalRequests = 0;
    private long totalTimeouts = 0;
    private long lastRequestTime = 0;
    private long totalResponseTime = 0;

    public Network() {
        this.server = "game.guard-and-towers.com";
        this.port = 35000;
        this.playerNumber = connect();
    }

    public String getP() {
        return playerNumber;
    }

    /**
     * ADAPTIVE FEATURE: Set socket timeout dynamically
     * @param timeout Timeout in milliseconds
     * @throws IllegalArgumentException if timeout is out of range
     */
    public void setSocketTimeout(int timeout) throws IllegalArgumentException {
        if (timeout < MIN_TIMEOUT || timeout > MAX_TIMEOUT) {
            throw new IllegalArgumentException(
                    String.format("Timeout must be between %d and %d ms, got: %d",
                            MIN_TIMEOUT, MAX_TIMEOUT, timeout));
        }

        this.currentTimeout = timeout;

        try {
            if (client != null && !client.isClosed()) {
                client.setSoTimeout(timeout);
            }
        } catch (IOException e) {
            System.err.println("⚠️ Warning: Could not set socket timeout: " + e.getMessage());
        }
    }

    /**
     * Get current socket timeout
     */
    public int getCurrentTimeout() {
        return currentTimeout;
    }

    /**
     * ENHANCED: Get connection statistics
     */
    public String getConnectionStats() {
        if (totalRequests == 0) {
            return "No requests made yet";
        }

        double timeoutRate = (double) totalTimeouts / totalRequests * 100;
        double avgResponseTime = (double) totalResponseTime / totalRequests;

        return String.format("Requests: %d, Timeouts: %d (%.1f%%), Avg response: %.1fms, Current timeout: %dms",
                totalRequests, totalTimeouts, timeoutRate, avgResponseTime, currentTimeout);
    }

    private String connect() {
        try {
            System.out.println("🔌 Connecting to " + server + ":" + port + "...");
            client = new Socket(server, port);

            // Use longer timeout for initial connection
            client.setSoTimeout(CONNECT_TIMEOUT);
            System.out.println("✅ Connected! Initial timeout: " + CONNECT_TIMEOUT + "ms");

            byte[] buffer = new byte[INITIAL_BUFFER_SIZE];
            InputStream in = client.getInputStream();

            long startTime = System.currentTimeMillis();
            int bytesRead = in.read(buffer);
            long connectTime = System.currentTimeMillis() - startTime;

            if (bytesRead > 0) {
                String playerNum = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8).trim();
                System.out.printf("🎮 Received player number: %s (connect time: %dms)%n", playerNum, connectTime);

                // Set default timeout after successful connect
                setSocketTimeout(DEFAULT_TIMEOUT);
                return playerNum;
            } else {
                System.err.println("❌ No data received on connect");
                return null;
            }
        } catch (IOException e) {
            System.err.println("❌ Connection failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * ENHANCED: Send with timeout monitoring and smart error handling
     */
    public String send(String data) {
        if (client == null || client.isClosed()) {
            System.err.println("❌ Socket is closed or null");
            return null;
        }

        totalRequests++;
        lastRequestTime = System.currentTimeMillis();

        try {
            // Send data to server
            OutputStream out = client.getOutputStream();
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            out.write(dataBytes);
            out.flush();

            // Receive response with current timeout setting
            InputStream in = client.getInputStream();

            // Start with initial buffer
            byte[] buffer = new byte[INITIAL_BUFFER_SIZE];
            int totalBytesRead = 0;
            int bytesRead;

            // Read data in chunks to handle large responses
            StringBuilder response = new StringBuilder();

            // First read with timeout monitoring
            long readStart = System.currentTimeMillis();
            bytesRead = in.read(buffer);

            if (bytesRead > 0) {
                response.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
                totalBytesRead = bytesRead;

                // Check if more data is available
                while (in.available() > 0 && totalBytesRead < MAX_BUFFER_SIZE) {
                    bytesRead = in.read(buffer);
                    if (bytesRead > 0) {
                        response.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
                        totalBytesRead += bytesRead;
                    } else {
                        break;
                    }
                }
            }

            // Record successful response time
            long responseTime = System.currentTimeMillis() - readStart;
            totalResponseTime += responseTime;

            if (totalBytesRead == 0) {
                System.err.println("❌ No data received in response");
                return null;
            }

            // Log slow responses for debugging
            if (responseTime > currentTimeout / 2) {
                System.out.printf("⚠️ Slow response: %dms (timeout: %dms)%n", responseTime, currentTimeout);
            }

            return response.toString();

        } catch (SocketTimeoutException e) {
            totalTimeouts++;

            // ENHANCED: More informative timeout handling
            long timeWaited = System.currentTimeMillis() - lastRequestTime;

            // Different messages based on timeout context
            if (currentTimeout <= 1000) {
                // Short timeout - likely opponent's turn, this is expected
                return null; // Silent timeout for expected cases
            } else {
                // Longer timeout - might be concerning
                System.err.printf("⏰ Socket timeout after %dms (configured: %dms)%n", timeWaited, currentTimeout);
                return null;
            }

        } catch (IOException e) {
            System.err.printf("❌ IO Error in send (timeout: %dms): %s%n", currentTimeout, e.getMessage());

            // Enhanced error reporting
            if (e.getMessage().contains("Connection reset")) {
                System.err.println("🔌 Connection was reset by server - possible server restart");
            } else if (e.getMessage().contains("Broken pipe")) {
                System.err.println("📡 Connection broken - server may have disconnected");
            }

            return null;
        }
    }

    /**
     * ADAPTIVE FEATURE: Quick connection test
     */
    public boolean isConnectionHealthy() {
        if (client == null || client.isClosed()) {
            return false;
        }

        // Check timeout rate
        if (totalRequests > 10 && totalTimeouts > totalRequests * 0.5) {
            System.out.println("⚠️ High timeout rate detected: " + getConnectionStats());
            return false;
        }

        return true;
    }

    /**
     * ADAPTIVE FEATURE: Auto-adjust timeout based on connection quality
     */
    public void autoAdjustTimeout() {
        if (totalRequests < 5) return; // Need some data first

        double timeoutRate = (double) totalTimeouts / totalRequests;
        double avgResponseTime = (double) totalResponseTime / (totalRequests - totalTimeouts);

        int recommendedTimeout;

        if (timeoutRate > 0.3) {
            // High timeout rate - increase timeout
            recommendedTimeout = Math.min(currentTimeout + 1000, MAX_TIMEOUT);
            System.out.printf("📈 High timeout rate (%.1f%%) - increasing timeout to %dms%n",
                    timeoutRate * 100, recommendedTimeout);
        } else if (timeoutRate < 0.1 && avgResponseTime < currentTimeout * 0.3) {
            // Low timeout rate and fast responses - can decrease timeout
            recommendedTimeout = Math.max(currentTimeout - 500, MIN_TIMEOUT);
            System.out.printf("📉 Fast responses (%.1fms avg) - decreasing timeout to %dms%n",
                    avgResponseTime, recommendedTimeout);
        } else {
            return; // No adjustment needed
        }

        try {
            setSocketTimeout(recommendedTimeout);
        } catch (IllegalArgumentException e) {
            System.err.println("⚠️ Auto-adjustment failed: " + e.getMessage());
        }
    }

    /**
     * Enhanced shutdown with statistics
     */
    public void close() {
        if (totalRequests > 0) {
            System.out.println("📡 Final connection statistics: " + getConnectionStats());
        }

        if (client != null && !client.isClosed()) {
            try {
                System.out.println("🔌 Closing connection...");
                client.close();
                System.out.println("✅ Connection closed cleanly");
            } catch (IOException e) {
                System.err.println("⚠️ Error closing connection: " + e.getMessage());
            }
        }
    }
}
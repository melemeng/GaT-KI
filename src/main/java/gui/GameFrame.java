package gui;

import GaT.search.Engine;
import GaT.game.GameState;
import GaT.game.Move;
import GaT.game.MoveGenerator;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * UPDATED GAME FRAME using BoardPanel
 *
 * Clean separation of concerns:
 * ✅ GameFrame handles game logic and controls
 * ✅ BoardPanel handles board rendering and clicks
 * ✅ Engine integration for AI moves
 * ✅ Simple game modes: Human vs AI, AI vs AI
 * ✅ Essential controls: Stop AI, Reset Game
 *
 * Much cleaner than original complex version.
 */
public class GameFrame extends JFrame implements BoardPanel.BoardClickListener {

    // === GAME STATE ===
    private GameState gameState;
    private Engine engine;
    private ExecutorService aiExecutor;
    private Future<?> currentAITask;

    // === UI COMPONENTS ===
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private JButton humanVsAiButton;
    private JButton aiVsAiButton;
    private JButton stopAiButton;
    private JButton resetButton;
    private JLabel engineStatsLabel;

    // === GAME MODE ===
    private enum GameMode { HUMAN_VS_AI, AI_VS_AI, STOPPED }
    private GameMode currentMode = GameMode.STOPPED;
    private boolean humanIsRed = true;
    private boolean aiThinking = false;
    private Move lastMove = null;

    public GameFrame() {
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        resetGame();

        setTitle("Guard & Towers - Clean Architecture");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        pack();
        setLocationRelativeTo(null);
    }

    // === INITIALIZATION ===

    private void initializeComponents() {
        engine = new Engine();
        aiExecutor = Executors.newSingleThreadExecutor();

        // Board panel
        boardPanel = new BoardPanel();
        boardPanel.setClickListener(this);

        // Controls
        humanVsAiButton = new JButton("Human vs AI");
        aiVsAiButton = new JButton("AI vs AI");
        stopAiButton = new JButton("Stop AI");
        resetButton = new JButton("Reset Game");

        // Status labels
        statusLabel = new JLabel("Welcome to Guard & Towers!", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));

        engineStatsLabel = new JLabel("Engine ready", SwingConstants.CENTER);
        engineStatsLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        engineStatsLabel.setForeground(Color.GRAY);
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        // Board in center
        add(boardPanel, BorderLayout.CENTER);

        // Controls at top
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.add(humanVsAiButton);
        controlPanel.add(aiVsAiButton);
        controlPanel.add(stopAiButton);
        controlPanel.add(resetButton);
        add(controlPanel, BorderLayout.NORTH);

        // Status at bottom
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(engineStatsLabel, BorderLayout.SOUTH);
        add(statusPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        // Game mode buttons
        humanVsAiButton.addActionListener(e -> startHumanVsAI());
        aiVsAiButton.addActionListener(e -> startAIVsAI());
        stopAiButton.addActionListener(e -> stopAI());
        resetButton.addActionListener(e -> resetGame());
    }

    // === BOARD CLICK HANDLER ===

    @Override
    public void onSquareClicked(int square) {
        if (currentMode != GameMode.HUMAN_VS_AI || aiThinking) {
            return; // Not accepting human input
        }

        if (gameState.redToMove != humanIsRed) {
            updateStatus("It's not your turn!");
            return;
        }

        int selectedSquare = boardPanel.getSelectedSquare();

        if (selectedSquare == -1) {
            // No piece selected - try to select piece
            if (boardPanel.hasPiece(square, humanIsRed)) {
                boardPanel.setSelectedSquare(square);
                updateStatus("Piece selected on " + boardPanel.getSquareName(square) + " - Click destination");
            } else {
                updateStatus("No piece to select on " + boardPanel.getSquareName(square));
            }
        } else {
            // Piece already selected - try to move
            if (square == selectedSquare) {
                // Clicked same square - deselect
                boardPanel.clearSelection();
                updateStatus("Selection cleared - Click a piece to select");
            } else if (boardPanel.hasPiece(square, humanIsRed)) {
                // Clicked another own piece - select it instead
                boardPanel.setSelectedSquare(square);
                updateStatus("New piece selected on " + boardPanel.getSquareName(square) + " - Click destination");
            } else {
                // Try to make move
                Move move = boardPanel.getLegalMoveToSquare(square);
                if (move != null) {
                    makeHumanMove(move);
                } else {
                    updateStatus("Illegal move to " + boardPanel.getSquareName(square) + " - Try again");
                }
            }
        }
    }

    // === GAME MODES ===

    private void startHumanVsAI() {
        stopAI();
        currentMode = GameMode.HUMAN_VS_AI;
        humanIsRed = true;
        boardPanel.clearSelection();

        updateStatus("Human vs AI mode - You are RED, AI is BLUE");
        updateButtonStates();

        if (!gameState.redToMove) {
            // AI's turn (we're blue)
            makeAIMove();
        } else {
            updateStatus("Your turn! Click a piece to select it");
        }
    }

    private void startAIVsAI() {
        stopAI();
        currentMode = GameMode.AI_VS_AI;
        boardPanel.clearSelection();

        updateStatus("AI vs AI mode - Watching engines play...");
        updateButtonStates();
        makeAIMove();
    }

    private void stopAI() {
        if (currentAITask != null && !currentAITask.isDone()) {
            currentAITask.cancel(true);
        }
        currentMode = GameMode.STOPPED;
        aiThinking = false;
        boardPanel.clearSelection();

        updateStatus("AI stopped - Game paused");
        updateButtonStates();
    }

    private void resetGame() {
        stopAI();
        gameState = new GameState();
        lastMove = null;
        currentMode = GameMode.STOPPED;

        boardPanel.setGameState(gameState);
        boardPanel.clearSelection();
        boardPanel.setLastMove(null);

        updateStatus("Game reset - Choose game mode to start");
        updateEngineStats("Engine ready");
        updateButtonStates();
    }

    // === MOVE HANDLING ===

    private void makeHumanMove(Move move) {
        // Apply move
        gameState.applyMove(move);
        lastMove = move;

        // Update display
        boardPanel.setGameState(gameState);
        boardPanel.setLastMove(lastMove);
        boardPanel.clearSelection();

        // Check game end
        if (isGameOver()) {
            handleGameOver();
            return;
        }

        // Continue with AI move
        updateStatus("Good move! AI is thinking...");
        makeAIMove();
    }

    private void makeAIMove() {
        if (currentMode == GameMode.STOPPED || aiThinking) {
            return;
        }

        aiThinking = true;
        updateButtonStates();

        String currentPlayer = gameState.redToMove ? "RED" : "BLUE";
        updateStatus("🧠 " + currentPlayer + " AI is thinking...");

        currentAITask = aiExecutor.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // Use different think times based on mode
                long thinkTime = (currentMode == GameMode.AI_VS_AI) ? 3000 : 5000; // 3s for AI vs AI, 5s for vs human
                Move aiMove = engine.findBestMove(gameState, thinkTime);

                long actualTime = System.currentTimeMillis() - startTime;

                if (aiMove != null && !Thread.currentThread().isInterrupted()) {
                    SwingUtilities.invokeLater(() -> {
                        // Apply AI move
                        gameState.applyMove(aiMove);
                        lastMove = aiMove;

                        // Update display
                        boardPanel.setGameState(gameState);
                        boardPanel.setLastMove(lastMove);

                        // Update stats
                        String moveDesc = String.format("%s played: %s", currentPlayer, aiMove);
                        String statsDesc = String.format("Search: %,d nodes, %.0f nps, %.1f%% TT hits",
                                engine.getNodesSearched(),
                                engine.getNodesSearched() * 1000.0 / Math.max(1, actualTime),
                                engine.getTTHitRate());

                        updateEngineStats(statsDesc);

                        // Check game end
                        if (isGameOver()) {
                            handleGameOver();
                        } else if (currentMode == GameMode.AI_VS_AI) {
                            updateStatus(moveDesc + " (" + actualTime + "ms) - Next move...");
                            // Continue AI vs AI after short delay
                            Timer timer = new Timer(1000, e -> makeAIMove());
                            timer.setRepeats(false);
                            timer.start();
                        } else {
                            updateStatus(moveDesc + " (" + actualTime + "ms) - Your turn!");
                        }

                        aiThinking = false;
                        updateButtonStates();
                    });
                }
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    SwingUtilities.invokeLater(() -> {
                        updateStatus("AI error: " + e.getMessage());
                        updateEngineStats("Engine error");
                        aiThinking = false;
                        updateButtonStates();
                    });
                }
            }
        });
    }

    // === GAME END HANDLING ===

    private boolean isGameOver() {
        // Check for guard captured
        if (gameState.redGuard == 0 || gameState.blueGuard == 0) {
            return true;
        }

        // Check for guard on enemy castle
        if ((gameState.redGuard & (1L << 3)) != 0) return true;  // Red guard on D1
        if ((gameState.blueGuard & (1L << 45)) != 0) return true; // Blue guard on D7

        // Check for no legal moves (stalemate)
        try {
            List<Move> legalMoves = MoveGenerator.generateAllMoves(gameState);
            return legalMoves.isEmpty();
        } catch (Exception e) {
            return false; // Assume game continues if we can't check
        }
    }

    private void handleGameOver() {
        String winner = determineWinner();

        updateStatus("🏆 GAME OVER - " + winner);
        currentMode = GameMode.STOPPED;
        aiThinking = false;
        boardPanel.clearSelection();
        updateButtonStates();

        // Show dialog after short delay
        Timer timer = new Timer(500, e -> {
            JOptionPane.showMessageDialog(this, winner, "Game Over", JOptionPane.INFORMATION_MESSAGE);
        });
        timer.setRepeats(false);
        timer.start();
    }

    private String determineWinner() {
        if (gameState.redGuard == 0) {
            return "BLUE wins! (Red guard captured)";
        } else if (gameState.blueGuard == 0) {
            return "RED wins! (Blue guard captured)";
        } else if ((gameState.redGuard & (1L << 3)) != 0) {
            return "RED wins! (Guard reached enemy castle)";
        } else if ((gameState.blueGuard & (1L << 45)) != 0) {
            return "BLUE wins! (Guard reached enemy castle)";
        } else {
            // Check for stalemate
            try {
                List<Move> legalMoves = MoveGenerator.generateAllMoves(gameState);
                if (legalMoves.isEmpty()) {
                    return "Draw! (No legal moves)";
                }
            } catch (Exception e) {
                // Ignore
            }
            return "Game ended";
        }
    }

    // === UI UPDATES ===

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void updateEngineStats(String stats) {
        engineStatsLabel.setText(stats);
    }

    private void updateButtonStates() {
        humanVsAiButton.setEnabled(!aiThinking);
        aiVsAiButton.setEnabled(!aiThinking);
        stopAiButton.setEnabled(aiThinking || currentMode == GameMode.AI_VS_AI);
        resetButton.setEnabled(true);
    }

    // === CLEANUP ===

    @Override
    public void dispose() {
        stopAI();
        if (aiExecutor != null && !aiExecutor.isShutdown()) {
            aiExecutor.shutdown();
        }
        super.dispose();
    }

    // === MAIN METHOD FOR TESTING ===

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getLookAndFeel());
            } catch (Exception e) {
                // Use default look and feel
            }

            new GameFrame().setVisible(true);
        });
    }
}
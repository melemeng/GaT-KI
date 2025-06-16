package GUI;

import GaT.*;
import GaT.Objects.GameState;
import GaT.Objects.Move;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class GameFrame extends JFrame {
    // Thread-safe game state management
    private volatile GameState state;
    private BoardPanel board;
    private volatile boolean aiThinking = false;
    private volatile boolean gameInProgress = true;
    private final Object stateLock = new Object();

    // Thread management
    private ExecutorService aiExecutor;
    private Future<?> currentAITask;

    // UI Components
    private JButton aiVsAiButton;
    private JButton resetButton;
    private JButton stopAIButton;
    private JLabel statusLabel;

    public GameFrame() {
        super("Guard & Towers - With Quiescence Search");

        // Initialize thread pool for AI
        aiExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AI-Worker");
            t.setDaemon(true);
            return t;
        });

        initializeGame();
        initializeUI();
    }

    private void initializeGame() {
        synchronized (stateLock) {
            try {
                // Try to load the specified position, fallback to default if it fails
                state = GameState.fromFen("7/7/7/BG6/3b33/3RG3/7 r");
                System.out.println("Game initialized - Red to move: " + state.redToMove);
            } catch (Exception e) {
                System.err.println("Failed to load custom position, using default: " + e.getMessage());
                state = new GameState(); // Fallback to default starting position
                System.out.println("Default game initialized - Red to move: " + state.redToMove);
            }
            gameInProgress = true;
        }
    }

    private void initializeUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Create board panel with thread-safe state access
        board = new BoardPanel(getStateCopy(), this::onMoveSelected);
        add(board, BorderLayout.CENTER);

        // Create control panel
        JPanel controlPanel = createControlPanel();
        add(controlPanel, BorderLayout.SOUTH);

        // Create status bar
        statusLabel = new JLabel("Ready - Your move (Red) - AI uses Quiescence Search");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        add(statusLabel, BorderLayout.NORTH);

        // Window settings
        setSize(660, 750); // Extra height for status and controls
        setResizable(false);
        setLocationRelativeTo(null);
        setVisible(true);

        updateUI();
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout());

        // AI vs AI button
        aiVsAiButton = new JButton("AI vs AI (Quiescence)");
        aiVsAiButton.addActionListener(e -> {
            if (!aiThinking) {
                runAiMatch();
            }
        });

        // Reset game button
        resetButton = new JButton("Reset Game");
        resetButton.addActionListener(e -> resetGame());

        // Stop AI button
        stopAIButton = new JButton("Stop AI");
        stopAIButton.addActionListener(e -> stopAI());
        stopAIButton.setEnabled(false);

        // Evaluate position button
        JButton evaluateButton = new JButton("Evaluate");
        evaluateButton.addActionListener(e -> showPositionEvaluation());

        panel.add(aiVsAiButton);
        panel.add(resetButton);
        panel.add(stopAIButton);
        panel.add(evaluateButton);

        return panel;
    }

    private void onMoveSelected(Move move) {
        // Prevent moves during AI thinking or game over
        if (aiThinking || !gameInProgress) {
            updateStatus("Please wait...");
            return;
        }

        // Validate and apply human move
        synchronized (stateLock) {
            if (!gameInProgress) return;

            // DEBUG: Print current turn before move
            System.out.println("Before move - Red to move: " + state.redToMove + ", Move: " + move);

            // Validate move is legal
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
            if (!legalMoves.contains(move)) {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("Illegal move: " + move);
                    // Reset status after 2 seconds
                    javax.swing.Timer timer = new javax.swing.Timer(2000, e -> {
                        String currentPlayer = state.redToMove ? "Red" : "Blue";
                        updateStatus("Your move (" + currentPlayer + ")");
                    });
                    timer.setRepeats(false);
                    timer.start();
                });
                return;
            }

            // Apply the move
            state.applyMove(move);
            System.out.println("Human move applied: " + move);
            System.out.println("After move - Red to move: " + state.redToMove);
        }

        // Update UI on EDT
        SwingUtilities.invokeLater(() -> {
            updateUI();

            if (Minimax.isGameOver(state)) {
                gameInProgress = false;
                showGameOverDialog();
                return;
            }

            // Check whose turn it is now and update status accordingly
            synchronized (stateLock) {
                if (state.redToMove) {
                    updateStatus("Your move (Red)");
                } else {
                    updateStatus("AI thinking with Quiescence Search...");
                    // Only start AI if it's Blue's turn (assuming human plays Red)
                    // You can modify this logic based on your game setup
                    startAIThinking();
                }
            }
        });
    }

    private void startAIThinking() {
        if (aiThinking || !gameInProgress) return;

        // Double-check it's actually AI's turn
        synchronized (stateLock) {
            if (state.redToMove) {
                System.out.println("WARNING: AI called but it's Red's turn - skipping AI");
                updateStatus("Your move (Red)");
                return;
            }
        }

        aiThinking = true;
        updateButtonStates();
        updateStatus("AI is thinking with Quiescence Search...");

        // Create immutable snapshot for AI thread
        final GameState aiState = getStateCopy();
        System.out.println("AI starting to think. Blue to move: " + !aiState.redToMove);

        // Submit AI task
        currentAITask = aiExecutor.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();
                // CHANGED: Use quiescence search instead of regular minimax
                Move aiMove = TimedMinimax.findBestMoveWithTimeAndQuiescence(aiState, 99, 2000);
                long thinkTime = System.currentTimeMillis() - startTime;

                // Apply AI move on EDT
                SwingUtilities.invokeLater(() -> {
                    if (!aiThinking || !gameInProgress) return; // Check if stopped or game ended

                    synchronized (stateLock) {
                        if (!gameInProgress) {
                            aiThinking = false;
                            updateButtonStates();
                            return;
                        }

                        // Double-check move is still legal (rare edge case)
                        List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
                        if (legalMoves.contains(aiMove)) {
                            System.out.println("AI applying move: " + aiMove);
                            System.out.println("Before AI move - Red to move: " + state.redToMove);
                            state.applyMove(aiMove);
                            System.out.println("After AI move - Red to move: " + state.redToMove);
                            System.out.println("AI move: " + aiMove + " (" + thinkTime + "ms)");
                        } else {
                            System.err.println("AI generated illegal move: " + aiMove);
                            // Find any legal move as fallback
                            if (!legalMoves.isEmpty()) {
                                Move fallbackMove = legalMoves.get(0);
                                state.applyMove(fallbackMove);
                                System.err.println("Using fallback move: " + fallbackMove);
                            }
                        }
                    }

                    aiThinking = false;
                    updateUI();
                    updateButtonStates();

                    if (Minimax.isGameOver(state)) {
                        gameInProgress = false;
                        showGameOverDialog();
                    } else {
                        // Update status based on whose turn it is now
                        synchronized (stateLock) {
                            String currentPlayer = state.redToMove ? "Red" : "Blue";
                            updateStatus("Your move (" + currentPlayer + ")");
                        }
                    }
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    aiThinking = false;
                    updateButtonStates();
                    updateStatus("AI Error: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        });
    }

    private void stopAI() {
        aiThinking = false;

        if (currentAITask != null && !currentAITask.isDone()) {
            currentAITask.cancel(true);
        }

        updateButtonStates();
        updateStatus("AI stopped - Your move");
    }

    private void resetGame() {
        stopAI();

        synchronized (stateLock) {
            try {
                // Reset to the same starting position
                state = GameState.fromFen("7/7/7/BG6/3b33/3RG3/7 r");
            } catch (Exception e) {
                state = new GameState(); // Fallback
            }
            gameInProgress = true;
        }

        SwingUtilities.invokeLater(() -> {
            updateUI();
            updateStatus("Game reset - Your move (Red)");
        });
    }

    private void runAiMatch() {
        if (aiThinking) return;

        aiThinking = true;
        updateButtonStates();
        updateStatus("AI vs AI with Quiescence Search in progress...");

        currentAITask = aiExecutor.submit(() -> {
            try {
                // Use array to make moveCount effectively final for lambda
                final int[] moveCount = {0};
                final int maxMoves = 200; // Prevent infinite games

                while (gameInProgress && aiThinking && !Thread.currentThread().isInterrupted() && moveCount[0] < maxMoves) {
                    // Get current state snapshot
                    GameState currentState = getStateCopy();

                    if (Minimax.isGameOver(currentState)) break;

                    long startTime = System.currentTimeMillis();
                    // CHANGED: Use quiescence search instead of regular minimax
                    Move move = TimedMinimax.findBestMoveWithTimeAndQuiescence(currentState, 99, 1000);
                    long moveTime = System.currentTimeMillis() - startTime;

                    // Apply move
                    synchronized (stateLock) {
                        if (!gameInProgress || !aiThinking) break;
                        state.applyMove(move);
                        moveCount[0]++;
                    }

                    // Update UI
                    final String player = currentState.redToMove ? "Red" : "Blue";
                    final int currentMoveCount = moveCount[0];
                    SwingUtilities.invokeLater(() -> {
                        updateUI();
                        updateStatus("AI vs AI (Q) - " + player + " played: " + move + " (" + moveTime + "ms) [Move " + currentMoveCount + "]");
                    });

                    System.out.println("AI " + player + ": " + move + " (" + moveTime + "ms) [Move " + moveCount[0] + "]");

                    // Pause between moves for visibility
                    Thread.sleep(500);
                }

                // Copy final moveCount for lambda
                final int finalMoveCount = moveCount[0];
                SwingUtilities.invokeLater(() -> {
                    aiThinking = false;
                    updateButtonStates();

                    if (finalMoveCount >= maxMoves) {
                        updateStatus("AI vs AI ended - Move limit reached");
                        JOptionPane.showMessageDialog(this, "Game ended due to move limit (" + maxMoves + " moves)",
                                "Game Ended", JOptionPane.INFORMATION_MESSAGE);
                    } else if (Minimax.isGameOver(state)) {
                        gameInProgress = false;
                        showGameOverDialog();
                    } else {
                        updateStatus("AI vs AI stopped");
                    }
                });

            } catch (InterruptedException e) {
                SwingUtilities.invokeLater(() -> {
                    aiThinking = false;
                    updateButtonStates();
                    updateStatus("AI vs AI interrupted");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    aiThinking = false;
                    updateButtonStates();
                    updateStatus("AI vs AI error: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        });
    }

    private void showPositionEvaluation() {
        GameState currentState = getStateCopy();

        // Run evaluation in background to avoid blocking UI
        aiExecutor.submit(() -> {
            try {
                int eval = Minimax.evaluate(currentState, 0);

                SwingUtilities.invokeLater(() -> {
                    String message = String.format(
                            "Position Evaluation: %+d\n\n" +
                                    "Positive = Good for Red\n" +
                                    "Negative = Good for Blue\n\n" +
                                    "Current turn: %s\n" +
                                    "AI uses Quiescence Search for better tactical analysis",
                            eval,
                            currentState.redToMove ? "Red" : "Blue"
                    );

                    JOptionPane.showMessageDialog(this, message,
                            "Position Analysis", JOptionPane.INFORMATION_MESSAGE);
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Error evaluating position: " + e.getMessage(),
                            "Evaluation Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    // Thread-safe state access
    private GameState getStateCopy() {
        synchronized (stateLock) {
            return state.copy();
        }
    }

    // OPTION 1: Update UI with proper state synchronization
    private void updateUI() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::updateUI);
            return;
        }

        // Update BoardPanel with current state
        board.updateState(getStateCopy());
        board.repaint();
    }

    private void updateButtonStates() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::updateButtonStates);
            return;
        }

        aiVsAiButton.setEnabled(!aiThinking && gameInProgress);
        resetButton.setEnabled(true); // Always enabled
        stopAIButton.setEnabled(aiThinking);
    }

    private void updateStatus(String message) {
        if (SwingUtilities.isEventDispatchThread()) {
            statusLabel.setText(message);
        } else {
            SwingUtilities.invokeLater(() -> statusLabel.setText(message));
        }
    }

    private void showGameOverDialog() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::showGameOverDialog);
            return;
        }

        String winner = determineWinner();
        updateStatus("Game Over - " + winner);

        JOptionPane.showMessageDialog(this, winner, "Game Over", JOptionPane.INFORMATION_MESSAGE);
    }

    private String determineWinner() {
        synchronized (stateLock) {
            // Check for captured guards
            if (state.redGuard == 0) return "Blue wins! (Red guard captured)";
            if (state.blueGuard == 0) return "Red wins! (Blue guard captured)";

            // Check for castle captures
            long redCastlePos = GameState.bit(GameState.getIndex(0, 3)); // D1 (Blue's castle)
            long blueCastlePos = GameState.bit(GameState.getIndex(6, 3)); // D7 (Red's castle)

            if ((state.redGuard & redCastlePos) != 0) {
                return "Red wins! (Reached Blue's castle)";
            }
            if ((state.blueGuard & blueCastlePos) != 0) {
                return "Blue wins! (Reached Red's castle)";
            }

            return "Game over!";
        }
    }

    @Override
    public void dispose() {
        // Clean shutdown
        gameInProgress = false;
        stopAI();

        // Shutdown executor gracefully
        aiExecutor.shutdown();
        try {
            if (!aiExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                System.out.println("Forcing AI executor shutdown...");
                aiExecutor.shutdownNow();
                if (!aiExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    System.err.println("AI executor did not terminate cleanly");
                }
            }
        } catch (InterruptedException e) {
            aiExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GameFrame::new);
    }
}
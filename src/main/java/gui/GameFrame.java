package gui;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.engine.TimedMinimax;
import GaT.search.Minimax;
import GaT.search.MoveGenerator;
import GaT.model.SearchConfig;

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
    private JButton humanVsAiButton;
    private JButton aiVsHumanButton;
    private volatile boolean humanVsAiMode = false;
    private volatile boolean humanIsRed = true;


    static String boardString = "7/2RG4/1b11r1b32/1b15/7/6r3/5BG1 r";

    public GameFrame() {
        super("Guard & Towers - ULTIMATE AI (PVS + Quiescence) - FIXED");

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
                state = GameState.fromFen(boardString);
                System.out.println("✅ Game initialized - Red to move: " + state.redToMove);

                // VALIDATION: Ensure game state is valid
                List<Move> testMoves = MoveGenerator.generateAllMoves(state);
                System.out.println("✅ Legal moves available: " + testMoves.size());

            } catch (Exception e) {
                System.err.println("❌ Failed to load custom position, using default: " + e.getMessage());
                state = new GameState(); // Fallback to default starting position
                System.out.println("✅ Default game initialized - Red to move: " + state.redToMove);
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
        statusLabel = new JLabel("✅ Ready - Your move (Red) - AI uses ULTIMATE STRATEGY (PVS + Quiescence) - FIXED");
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

        // Human vs AI button (Human = Red, AI = Blue)
        humanVsAiButton = new JButton("🧑 Human vs 🤖 AI");
        humanVsAiButton.setToolTipText("You play as Red, AI plays as Blue (Press H)");
        humanVsAiButton.addActionListener(e -> {
            if (!aiThinking) {
                startHumanVsAI(true); // Human plays Red
            }
        });

        // AI vs Human button (AI = Red, Human = Blue)
        aiVsHumanButton = new JButton("🤖 AI vs 🧑 Human");
        aiVsHumanButton.setToolTipText("AI plays as Red, you play as Blue");
        aiVsHumanButton.addActionListener(e -> {
            if (!aiThinking) {
                startHumanVsAI(false); // Human plays Blue
            }
        });

        // AI vs AI button (existing)
        aiVsAiButton = new JButton("🤖 AI vs 🤖 AI");
        aiVsAiButton.setToolTipText("Watch AI play against itself (Press A)");
        aiVsAiButton.addActionListener(e -> {
            if (!aiThinking) {
                runAiMatch();
            }
        });

        // Reset game button (existing)
        resetButton = new JButton("🔄 Reset");
        resetButton.setToolTipText("Reset game to starting position (Press R)");
        resetButton.addActionListener(e -> resetGame());

        // Stop AI button (existing)
        stopAIButton = new JButton("⛔ Stop");
        stopAIButton.setToolTipText("Stop AI thinking (Press Space)");
        stopAIButton.addActionListener(e -> stopAI());
        stopAIButton.setEnabled(false);

        // Add help button
        JButton helpButton = new JButton("❓ Help");
        helpButton.setToolTipText("Show game rules and controls");
        helpButton.addActionListener(e -> showHelp());

        // Add evaluation button
        JButton evalButton = new JButton("📊 Eval");
        evalButton.setToolTipText("Show position evaluation");
        evalButton.addActionListener(e -> showPositionEvaluation());

        // Add buttons to panel
        panel.add(humanVsAiButton);
        panel.add(aiVsHumanButton);
        panel.add(aiVsAiButton);
        panel.add(resetButton);
        panel.add(stopAIButton);
        panel.add(evalButton);
        panel.add(helpButton);

        return panel;
    }


    private void showHelp() {
        String helpText = """
            🎯 GUARD & TOWERS - How to Play:
            
            📋 OBJECTIVE:
            • Capture the opponent's guard, OR
            • Move your guard to the opponent's castle (center of opposite baseline)
            
            🎮 HUMAN CONTROLS:
            • Click a piece to select it
            • Click destination to move
            • Only legal moves are allowed
            
            📐 MOVEMENT RULES:
            • Guard moves exactly 1 square (orthogonally)
            • Tower moves exactly as many squares as its height
            • No diagonal moves, no jumping over pieces
            
            🏗️ STACKING:
            • Same-color towers combine when one moves to another
            • You can split towers by moving only part of them
            
            ⚔️ CAPTURING:
            • Guard captures any piece
            • Any tower captures the guard
            • Tower captures equal/smaller tower
            
            ⌨️ KEYBOARD SHORTCUTS:
            • H - Human vs AI    • A - AI vs AI
            • R - Reset game     • Space - Stop AI
            
            💡 TIPS:
            • Click a piece to see its legal moves highlighted
            • Red pieces: GUARD (G), towers (numbers show height)
            • Blue pieces: guard (g), towers (numbers show height)
            • Castle squares are in the center of each baseline
            """;

        JOptionPane.showMessageDialog(this, helpText, "Game Rules & Controls", JOptionPane.INFORMATION_MESSAGE);
    }


    private void startHumanVsAI(boolean humanPlaysRed) {
        synchronized (stateLock) {
            // Reset to starting position
            state = new GameState();
            gameInProgress = true;
            humanVsAiMode = true;
            humanIsRed = humanPlaysRed;
        }

        updateUI();
        updateButtonStates();

        String humanColor = humanPlaysRed ? "Red" : "Blue";
        String aiColor = humanPlaysRed ? "Blue" : "Red";

        updateStatus("🎮 Human vs AI - You are " + humanColor + ", AI is " + aiColor +
                (state.redToMove == humanPlaysRed ? " - Your turn!" : " - AI thinking..."));

        System.out.println("🎮 Starting Human vs AI - Human: " + humanColor + ", AI: " + aiColor);

        // If AI should move first (human is blue and red moves first, or human is red and blue moves first)
        if (state.redToMove != humanPlaysRed) {
            runAiMatch();
        }
    }



    private void testSingleAIMove() {
        if (aiThinking) {
            updateStatus("AI is already thinking...");
            return;
        }

        updateStatus("🧪 Testing AI move generation...");

        currentAITask = aiExecutor.submit(() -> {
            try {
                GameState testState = getStateCopy();
                System.out.println("\n🧪 === AI MOVE TEST ===");
                System.out.println("Current state - Red to move: " + testState.redToMove);
                testState.printBoard();

                List<Move> legalMoves = MoveGenerator.generateAllMoves(testState);
                System.out.println("Legal moves: " + legalMoves.size());

                if (legalMoves.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        updateStatus("❌ No legal moves available!");
                        JOptionPane.showMessageDialog(this, "No legal moves available in current position!",
                                "Test Result", JOptionPane.WARNING_MESSAGE);
                    });
                    return;
                }

                long startTime = System.currentTimeMillis();
                Move aiMove = TimedMinimax.findBestMoveUltimate(testState, 5, 3000);
                long endTime = System.currentTimeMillis();

                SwingUtilities.invokeLater(() -> {
                    if (aiMove != null) {
                        updateStatus("✅ AI Test successful: " + aiMove + " (" + (endTime - startTime) + "ms)");
                        JOptionPane.showMessageDialog(this,
                                "AI Move Test Result:\n\n" +
                                        "Best Move: " + aiMove + "\n" +
                                        "Time: " + (endTime - startTime) + "ms\n" +
                                        "Legal Moves: " + legalMoves.size(),
                                "AI Test Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        updateStatus("❌ AI Test failed: Returned null move");
                        JOptionPane.showMessageDialog(this,
                                "AI Test FAILED!\n\n" +
                                        "Returned: null\n" +
                                        "Legal Moves Available: " + legalMoves.size() + "\n" +
                                        "This indicates a bug in the AI search.",
                                "AI Test Failure", JOptionPane.ERROR_MESSAGE);
                    }
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("❌ AI Test error: " + e.getMessage());
                    JOptionPane.showMessageDialog(this,
                            "AI Test ERROR!\n\n" + e.getMessage(),
                            "AI Test Error", JOptionPane.ERROR_MESSAGE);
                });
                e.printStackTrace();
            }
        });
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
            System.out.println("🎮 Human move attempt - Red to move: " + state.redToMove + ", Move: " + move);

            // Validate move is legal
            List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
            if (!legalMoves.contains(move)) {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("❌ Illegal move: " + move);
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
            System.out.println("✅ Human move applied: " + move);
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
                    updateStatus("🤖 AI thinking with ULTIMATE STRATEGY (FIXED)...");
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
                System.out.println("⚠️ WARNING: AI called but it's Red's turn - skipping AI");
                updateStatus("Your move (Red)");
                return;
            }
        }

        aiThinking = true;
        updateButtonStates();
        updateStatus("🤖 AI thinking with ULTIMATE STRATEGY (FIXED)...");

        // Create immutable snapshot for AI thread
        final GameState aiState = getStateCopy();
        System.out.println("🤖 ULTIMATE AI starting to think. Blue to move: " + !aiState.redToMove);

        // VALIDATION: Check legal moves before AI thinks
        List<Move> availableMoves = MoveGenerator.generateAllMoves(aiState);
        if (availableMoves.isEmpty()) {
            System.err.println("❌ CRITICAL: No legal moves for AI!");
            aiThinking = false;
            updateButtonStates();
            updateStatus("❌ Game Over - No moves available");
            return;
        }

        System.out.println("🤖 AI has " + availableMoves.size() + " legal moves to consider");

        // Submit AI task
        currentAITask = aiExecutor.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // *** FIXED: Use Ultimate AI with proper error handling ***
                Move aiMove = TimedMinimax.findBestMoveUltimate(aiState, 99, 2000);

                long thinkTime = System.currentTimeMillis() - startTime;

                // CRITICAL NULL CHECK
                if (aiMove == null) {
                    System.err.println("❌ CRITICAL: AI returned null move!");
                    System.err.println("Available moves were: " + availableMoves);

                    // Emergency fallback - use first legal move
                    aiMove = availableMoves.get(0);
                    System.err.println("🚨 Using emergency fallback move: " + aiMove);
                }

                final Move finalAiMove = aiMove;

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
                        List<Move> currentLegalMoves = MoveGenerator.generateAllMoves(state);
                        if (currentLegalMoves.contains(finalAiMove)) {
                            System.out.println("🤖 ULTIMATE AI applying move: " + finalAiMove);
                            System.out.println("Before AI move - Red to move: " + state.redToMove);
                            state.applyMove(finalAiMove);
                            System.out.println("After AI move - Red to move: " + state.redToMove);
                            System.out.println("✅ ULTIMATE AI move: " + finalAiMove + " (" + thinkTime + "ms)");
                        } else {
                            System.err.println("❌ AI generated illegal move: " + finalAiMove);
                            // Find any legal move as fallback
                            if (!currentLegalMoves.isEmpty()) {
                                Move fallbackMove = currentLegalMoves.get(0);
                                state.applyMove(fallbackMove);
                                System.err.println("🚨 Using fallback move: " + fallbackMove);
                            } else {
                                System.err.println("❌ CRITICAL: No legal moves available!");
                                gameInProgress = false;
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
                    updateStatus("❌ ULTIMATE AI Error: " + e.getMessage());
                    System.err.println("❌ AI Exception: " + e.getMessage());
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
        updateStatus("🛑 AI stopped - Your move");
    }

    private void resetGame() {
        stopAI();

        synchronized (stateLock) {
            try {
                // Reset to the same starting position
                state = GameState.fromFen(boardString);
                System.out.println("🔄 Game reset to: " + boardString);
            } catch (Exception e) {
                state = new GameState(); // Fallback
                System.out.println("🔄 Game reset to default position");
            }
            gameInProgress = true;
        }

        SwingUtilities.invokeLater(() -> {
            updateUI();
            updateStatus("🔄 Game reset - Your move (Red)");
        });
    }

    private void runAiMatch() {
        if (aiThinking) return;

        aiThinking = true;
        updateButtonStates();
        updateStatus("🤖 vs 🤖 ULTIMATE AI match (FIXED) in progress...");

        currentAITask = aiExecutor.submit(() -> {
            try {
                // Use array to make moveCount effectively final for lambda
                final int[] moveCount = {0};
                final int maxMoves = 200; // Prevent infinite games

                while (gameInProgress && aiThinking && !Thread.currentThread().isInterrupted() && moveCount[0] < maxMoves) {
                    // Get current state snapshot
                    GameState currentState = getStateCopy();

                    if (Minimax.isGameOver(currentState)) break;

                    // Check for legal moves
                    List<Move> legalMoves = MoveGenerator.generateAllMoves(currentState);
                    if (legalMoves.isEmpty()) {
                        System.err.println("❌ No legal moves available in AI vs AI");
                        break;
                    }

                    long startTime = System.currentTimeMillis();

                    // *** FIXED: Use Ultimate AI with null protection ***
                    Move move = TimedMinimax.findBestMoveUltimate(currentState, 99, 1000);

                    // CRITICAL NULL CHECK
                    if (move == null) {
                        System.err.println("❌ AI returned null in AI vs AI, using fallback");
                        move = legalMoves.get(0);
                    }

                    long moveTime = System.currentTimeMillis() - startTime;

                    // Apply move with validation
                    synchronized (stateLock) {
                        if (!gameInProgress || !aiThinking) break;

                        // Final validation
                        List<Move> currentLegalMoves = MoveGenerator.generateAllMoves(state);
                        if (currentLegalMoves.contains(move)) {
                            state.applyMove(move);
                            moveCount[0]++;
                        } else {
                            System.err.println("❌ Invalid move in AI vs AI: " + move);
                            if (!currentLegalMoves.isEmpty()) {
                                move = currentLegalMoves.get(0);
                                state.applyMove(move);
                                moveCount[0]++;
                            } else {
                                break;
                            }
                        }
                    }

                    // Update UI
                    final String player = currentState.redToMove ? "Red" : "Blue";
                    final int currentMoveCount = moveCount[0];
                    final Move finalMove = move;
                    SwingUtilities.invokeLater(() -> {
                        updateUI();
                        updateStatus("🤖 vs 🤖 FIXED AI - " + player + " played: " + finalMove + " (" + moveTime + "ms) [Move " + currentMoveCount + "]");
                    });

                    System.out.println("🤖 FIXED AI " + player + ": " + move + " (" + moveTime + "ms) [Move " + moveCount[0] + "]");

                    // Pause between moves for visibility
                    Thread.sleep(500);
                }

                // Copy final moveCount for lambda
                final int finalMoveCount = moveCount[0];
                SwingUtilities.invokeLater(() -> {
                    aiThinking = false;
                    updateButtonStates();

                    if (finalMoveCount >= maxMoves) {
                        updateStatus("🤖 vs 🤖 FIXED AI ended - Move limit reached");
                        JOptionPane.showMessageDialog(this, "Game ended due to move limit (" + maxMoves + " moves)",
                                "Game Ended", JOptionPane.INFORMATION_MESSAGE);
                    } else if (Minimax.isGameOver(state)) {
                        gameInProgress = false;
                        showGameOverDialog();
                    } else {
                        updateStatus("🤖 vs 🤖 FIXED AI stopped");
                    }
                });

            } catch (InterruptedException e) {
                SwingUtilities.invokeLater(() -> {
                    aiThinking = false;
                    updateButtonStates();
                    updateStatus("🤖 vs 🤖 FIXED AI interrupted");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    aiThinking = false;
                    updateButtonStates();
                    updateStatus("❌ FIXED AI vs AI error: " + e.getMessage());
                    System.err.println("❌ AI vs AI Exception: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        });
    }

    // ... rest of the methods remain the same but with better error handling ...

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
                                    "AI uses ULTIMATE STRATEGY (PVS + Quiescence) - FIXED VERSION",
                            eval,
                            currentState.redToMove ? "Red" : "Blue"
                    );

                    JOptionPane.showMessageDialog(this, message,
                            "Position Analysis - FIXED", JOptionPane.INFORMATION_MESSAGE);
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

    private void showStrategyComparison() {
        if (aiThinking) {
            updateStatus("Please wait for AI to finish thinking...");
            return;
        }

        GameState currentState = getStateCopy();

        // Run comparison in background
        aiExecutor.submit(() -> {
            try {
                SwingUtilities.invokeLater(() -> updateStatus("Comparing AI strategies... (FIXED version)"));

                System.out.println("\n=== STRATEGY COMPARISON - FIXED ===");
                currentState.printBoard();

                // Test different strategies - FIXED: Use SearchConfig.SearchStrategy
                SearchConfig.SearchStrategy[] strategies = SearchConfig.SearchStrategy.values();

                StringBuilder results = new StringBuilder("Strategy Comparison Results (FIXED):\n\n");

                for (SearchConfig.SearchStrategy strategy : strategies) {
                    long startTime = System.currentTimeMillis();

                    Move move = null;
                    try {
                        // FIXED: Use unified findBestMoveWithStrategy method
                        move = TimedMinimax.findBestMoveWithStrategy(currentState, 4, 3000, strategy);
                    } catch (Exception e) {
                        System.err.println("Error testing " + strategy + ": " + e.getMessage());
                    }

                    long endTime = System.currentTimeMillis();
                    long searchTime = endTime - startTime;

                    // Evaluate the resulting position if move is valid
                    int evaluation = 0;
                    if (move != null) {
                        try {
                            GameState resultState = currentState.copy();
                            resultState.applyMove(move);
                            evaluation = Minimax.evaluate(resultState, 0);
                        } catch (Exception e) {
                            System.err.println("Error evaluating result for " + strategy + ": " + e.getMessage());
                        }
                    }

                    results.append(String.format("%s:\n", strategy.displayName));
                    results.append(String.format("  Move: %s\n", move != null ? move : "NULL/ERROR"));
                    results.append(String.format("  Evaluation: %+d\n", evaluation));
                    results.append(String.format("  Time: %dms\n\n", searchTime));

                    System.out.printf("FIXED %s: Move=%s, Eval=%+d, Time=%dms\n",
                            strategy.displayName, move, evaluation, searchTime);
                }

                SwingUtilities.invokeLater(() -> {
                    updateStatus("✅ Strategy comparison completed (FIXED)!");

                    JTextArea textArea = new JTextArea(results.toString());
                    textArea.setEditable(false);
                    textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

                    JScrollPane scrollPane = new JScrollPane(textArea);
                    scrollPane.setPreferredSize(new Dimension(500, 400));

                    JOptionPane.showMessageDialog(this, scrollPane,
                            "AI Strategy Comparison - FIXED", JOptionPane.INFORMATION_MESSAGE);
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("❌ Strategy comparison failed: " + e.getMessage());
                    JOptionPane.showMessageDialog(this,
                            "Error comparing strategies: " + e.getMessage(),
                            "Comparison Error", JOptionPane.ERROR_MESSAGE);
                });
                e.printStackTrace();
            }
        });
    }

    // Thread-safe state access
    private GameState getStateCopy() {
        synchronized (stateLock) {
            return state.copy();
        }
    }

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
        updateStatus("🏁 Game Over - " + winner);

        JOptionPane.showMessageDialog(this, winner + "\n\nAI Engine: ULTIMATE (FIXED)",
                "Game Over", JOptionPane.INFORMATION_MESSAGE);
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
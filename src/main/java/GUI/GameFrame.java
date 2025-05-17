package GUI;

import GaT.*;
import GaT.Objects.GameState;
import GaT.Objects.Move;

import javax.swing.*;
import java.awt.*;

public class GameFrame extends JFrame {
    private GameState state = GameState.fromFen("7/7/7/BG6/3b33/3RG3/7 r");
    private BoardPanel board;

    public GameFrame() {
        super("Guard & Towers");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        board = new BoardPanel(state, this::onMoveSelected);
        add(board, BorderLayout.CENTER);

        JButton aiVsAiButton = new JButton("AI vs AI");
        aiVsAiButton.addActionListener(e -> runAiMatch());
        add(aiVsAiButton, BorderLayout.SOUTH);

        // Adjust the frame size to accommodate the larger board with labels
        setSize(660, 710); // Increased from 600, 650 to account for labels
        setResizable(false);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void onMoveSelected(Move move) {
        state.applyMove(move);
        board.repaint();

        if (Minimax.isGameOver(state)) {
            showGameOverDialog();
            return;
        }

        // Let AI respond
        if (!state.redToMove) {
            new Thread(() -> {
                Move aiMove = TimedMinimax.findBestMoveWithTime(state,99, 2000);
                state.applyMove(aiMove);
                System.out.println(aiMove);

                SwingUtilities.invokeLater(() -> {
                    board.repaint();
                    if (Minimax.isGameOver(state)) {
                        showGameOverDialog();
                    }
                });
            }).start();
        }
    }

    private void runAiMatch() {
        new Thread(() -> {
            while (!Minimax.isGameOver(state)) {
                Move move = TimedMinimax.findBestMoveWithTime(state,99, 2000);
                state.applyMove(move);
                board.repaint();
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            }
            showGameOverDialog();
        }).start();
    }

    private void showGameOverDialog() {
        String winner = state.redGuard == 0 ? "Blue wins!" :
                state.blueGuard == 0 ? "Red wins!" :
                        GameState.getIndex(0, 3) == Long.numberOfTrailingZeros(state.blueGuard) ? "Blue wins by entering castle!" :
                                GameState.getIndex(6, 3) == Long.numberOfTrailingZeros(state.redGuard) ? "Red wins by entering castle!" :
                                        "Game over!";
        JOptionPane.showMessageDialog(this, winner, "Game Over", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GameFrame::new);
    }
}

package GUI;

import GaT.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

import static GaT.Minimax.scoreMove;

public class GameFrame extends JFrame {
    private GameState state = GameState.fromFen("r1r11RG1r1r1/2r11r12/3r13/7/3b13/2b11b12/b1b11BG1b1b1 r");
    //"BG6/7/7/RG6/6r1/7/7 r"
    //"7/7/BG6/RG6/6r1/7/7 r"
//    private GameState state = new GameState();
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

        setSize(600, 650);
        setResizable(false);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void onMoveSelected(Move move) {
        state.applyMove(move);
        Minimax.evaluate(state);
        board.repaint();

        if (Minimax.isGameOver(state)) {
            showGameOverDialog();
            return;
        }

        // Let AI respond
        if (!state.redToMove) {
            new Thread(() -> {
                Move aiMove = Minimax.findBestMove(state, 8);
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
//                Move move = Minimax.findBestMove(state, 8);
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




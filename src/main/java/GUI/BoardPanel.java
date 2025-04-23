package GUI;

import GaT.GameState;
import GaT.Move;
import GaT.MoveGenerator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class BoardPanel extends JPanel {
    private final GameState state;
    private final int squareSize = 80;
    private final MoveConsumer moveConsumer;

    private int selectedIndex = -1;

    public BoardPanel(GameState state, MoveConsumer moveConsumer) {
        this.state = state;
        this.moveConsumer = moveConsumer;

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int file = e.getX() / squareSize;
                int rank = 6 - (e.getY() / squareSize);
                int index = GameState.getIndex(rank, file);

                if (selectedIndex == -1) {
                    selectedIndex = index;
                } else {
                    // Try move
                    for (Move move : MoveGenerator.generateAllMoves(state)) {
                        if (move.from == selectedIndex && move.to == index) {
                            moveConsumer.onMove(move);
                            break;
                        }
                    }
                    selectedIndex = -1;
                }
                repaint();
            }
        });
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Draw grid
        for (int r = 0; r < 7; r++) {
            for (int f = 0; f < 7; f++) {
                int x = f * squareSize;
                int y = (6 - r) * squareSize;
                g.setColor((r + f) % 2 == 0 ? Color.LIGHT_GRAY : Color.WHITE);
                g.fillRect(x, y, squareSize, squareSize);

                int index = GameState.getIndex(r, f);
                drawPiece(g, index, x, y);
            }
        }

        if (selectedIndex != -1) {
            int f = GameState.file(selectedIndex);
            int r = GameState.rank(selectedIndex);
            g.setColor(Color.RED);
            g.drawRect(f * squareSize, (6 - r) * squareSize, squareSize, squareSize);
        }
    }

    private void drawPiece(Graphics g, int index, int x, int y) {
        long bit = GameState.bit(index);

        if ((state.redGuard & bit) != 0) {
            g.setColor(Color.RED);
            g.drawString("R", x + 35, y + 45);
        } else if ((state.blueGuard & bit) != 0) {
            g.setColor(Color.BLUE);
            g.drawString("B", x + 35, y + 45);
        } else if ((state.redTowers & bit) != 0) {
            g.setColor(Color.RED);
            g.drawString("" + state.redStackHeights[index], x + 35, y + 45);
        } else if ((state.blueTowers & bit) != 0) {
            g.setColor(Color.BLUE);
            g.drawString("" + state.blueStackHeights[index], x + 35, y + 45);
        }
    }

    public Dimension getPreferredSize() {
        return new Dimension(7 * squareSize, 7 * squareSize);
    }

    @FunctionalInterface
    interface MoveConsumer {
        void onMove(Move move);
    }
}



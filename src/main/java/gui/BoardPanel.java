package gui;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class BoardPanel extends JPanel {
    private GameState state; // Remove final to allow updates
    private final int squareSize = 80;
    private final int labelSize = 30;  // Space for drawing labels
    private final MoveConsumer moveConsumer;

    private int selectedIndex = -1;

    public BoardPanel(GameState state, MoveConsumer moveConsumer) {
        this.state = state;
        this.moveConsumer = moveConsumer;

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int file = (e.getX() - labelSize) / squareSize;
                int rank = 6 - ((e.getY() - labelSize) / squareSize);

                // Ensure click is within the board
                if (file >= 0 && file < 7 && rank >= 0 && rank < 7) {
                    int index = GameState.getIndex(rank, file);

                    System.out.println("Selected square: " + index + " (rank=" + rank + ", file=" + file + ")");

                    if (selectedIndex == -1) {
                        selectedIndex = index;
                        System.out.println("Selected from square: " + selectedIndex);
                        repaint(); // Show selection
                    } else {
                        System.out.println("Trying move from " + selectedIndex + " to " + index);

                        // Generate all legal moves and check if this move exists
                        java.util.List<Move> legalMoves = MoveGenerator.generateAllMoves(BoardPanel.this.state);
                        Move foundMove = null;

                        for (Move move : legalMoves) {
                            if (move.from == selectedIndex && move.to == index) {
                                foundMove = move;
                                break;
                            }
                        }

                        if (foundMove != null) {
                            System.out.println("Legal move found: " + foundMove);
                            moveConsumer.onMove(foundMove);
                        } else {
                            System.out.println("No legal move found from " + selectedIndex + " to " + index);
                            System.out.println("Current turn: " + (BoardPanel.this.state.redToMove ? "Red" : "Blue"));

                            // Debug: Check what's at the from square
                            long fromBit = GameState.bit(selectedIndex);
                            boolean hasRedPiece = (BoardPanel.this.state.redGuard & fromBit) != 0 || (BoardPanel.this.state.redTowers & fromBit) != 0;
                            boolean hasBluePiece = (BoardPanel.this.state.blueGuard & fromBit) != 0 || (BoardPanel.this.state.blueTowers & fromBit) != 0;
                            System.out.println("From square " + selectedIndex + ": Red=" + hasRedPiece + ", Blue=" + hasBluePiece);

                            // Debug: Show available moves from this square
                            System.out.print("Available moves from " + selectedIndex + ": ");
                            boolean foundAny = false;
                            for (Move move : legalMoves) {
                                if (move.from == selectedIndex) {
                                    System.out.print(move + " ");
                                    foundAny = true;
                                }
                            }
                            if (!foundAny) {
                                System.out.print("NONE");
                            }
                            System.out.println();
                        }

                        selectedIndex = -1;
                        repaint(); // Clear selection
                    }
                }
            }
        });
    }

    // ADD THIS METHOD - This is what's missing!
    public void updateState(GameState newState) {
        this.state = newState;
        this.selectedIndex = -1; // Clear selection when state updates
        System.out.println("BoardPanel state updated - Red to move: " + state.redToMove);
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Use anti-aliasing for smoother text
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw rank numbers (1-7) and file letters (a-f)
        drawLabels(g2d);

        // Draw grid
        for (int r = 0; r < 7; r++) {
            for (int f = 0; f < 7; f++) {
                int x = labelSize + f * squareSize;
                int y = labelSize + (6 - r) * squareSize;
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
            g.drawRect(labelSize + f * squareSize, labelSize + (6 - r) * squareSize, squareSize, squareSize);
        }
    }

    private void drawLabels(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 16));

        // Draw file letters (a-f)
        for (int f = 0; f < 7; f++) {
            String fileLetter = String.valueOf((char)('A' + f));
            FontMetrics metrics = g.getFontMetrics();
            int x = labelSize + f * squareSize + (squareSize - metrics.stringWidth(fileLetter)) / 2;
            int y = labelSize + 7 * squareSize + 20; // Position below the board

            g.setColor(Color.BLACK);
            g.drawString(fileLetter, x, y);
        }

        // Draw rank numbers (1-7)
        for (int r = 0; r < 7; r++) {
            String rankNumber = String.valueOf(r + 1);
            FontMetrics metrics = g.getFontMetrics();
            int x = (labelSize - metrics.stringWidth(rankNumber)) / 2;
            int y = labelSize + (6 - r) * squareSize + squareSize/2 + metrics.getAscent()/2;

            g.setColor(Color.BLACK);
            g.drawString(rankNumber, x, y);
        }
    }

    private void drawPiece(Graphics g, int index, int x, int y) {
        long bit = GameState.bit(index);

        // Set a bold, larger font
        Font originalFont = g.getFont();
        g.setFont(new Font("SansSerif", Font.BOLD, 21)); // You can tweak the size as needed

        if ((state.redGuard & bit) != 0) {
            g.setColor(Color.RED);
            g.drawString("R", x + 30, y + 50);
        } else if ((state.blueGuard & bit) != 0) {
            g.setColor(Color.BLUE);
            g.drawString("B", x + 30, y + 50);
        } else if ((state.redTowers & bit) != 0) {
            g.setColor(Color.RED);
            g.drawString("" + state.redStackHeights[index], x + 30, y + 50);
        } else if ((state.blueTowers & bit) != 0) {
            g.setColor(Color.BLUE);
            g.drawString("" + state.blueStackHeights[index], x + 30, y + 50);
        }

        // Reset to original font if needed
        g.setFont(originalFont);
    }

    public Dimension getPreferredSize() {
        // Add space for the labels on each side
        return new Dimension(7 * squareSize + labelSize * 2, 7 * squareSize + labelSize * 2);
    }

    @FunctionalInterface
    interface MoveConsumer {
        void onMove(Move move);
    }
}
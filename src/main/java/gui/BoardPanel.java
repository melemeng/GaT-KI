package gui;

import GaT.game.GameState;
import GaT.game.Move;
import GaT.game.MoveGenerator;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SIMPLIFIED BOARD PANEL for Guard & Towers
 *
 * Clean board rendering and interaction:
 * ✅ Visual 7x7 board display
 * ✅ Piece rendering (guards and towers)
 * ✅ Move highlighting (selected square + legal moves)
 * ✅ Castle square highlighting
 * ✅ Click handling for move input
 * ✅ Clean separation from game logic
 *
 * Removed complexity:
 * ❌ Complex animations
 * ❌ Excessive visual effects
 * ❌ Over-engineered rendering
 * ❌ Debug overlays
 */
public class BoardPanel extends JPanel {

    // === CONSTANTS ===
    private static final int BOARD_SIZE = 7;
    private static final int CELL_SIZE = 80;
    private static final int BORDER_SIZE = 2;

    // === COLORS ===
    private static final Color LIGHT_SQUARE = new Color(240, 217, 181);
    private static final Color DARK_SQUARE = new Color(181, 136, 99);
    private static final Color SELECTED_SQUARE = new Color(255, 255, 0, 120);
    private static final Color LEGAL_MOVE = new Color(0, 255, 0, 100);
    private static final Color CASTLE_BORDER = new Color(255, 215, 0); // Gold
    private static final Color LAST_MOVE = new Color(255, 165, 0, 80); // Orange

    // === PIECE COLORS ===
    private static final Color RED_PIECE = new Color(200, 0, 0);
    private static final Color BLUE_PIECE = new Color(0, 0, 200);
    private static final Color PIECE_BORDER = Color.BLACK;

    // === GAME STATE ===
    private GameState gameState;
    private int selectedSquare = -1;
    private List<Move> legalMoves = null;
    private Move lastMove = null;

    // === INTERACTION ===
    private BoardClickListener clickListener;

    public interface BoardClickListener {
        void onSquareClicked(int square);
    }

    public BoardPanel() {
        setupPanel();
        setupMouseListener();
    }

    // === SETUP ===

    private void setupPanel() {
        setPreferredSize(new Dimension(
                BOARD_SIZE * CELL_SIZE + (BOARD_SIZE + 1) * BORDER_SIZE,
                BOARD_SIZE * CELL_SIZE + (BOARD_SIZE + 1) * BORDER_SIZE
        ));
        setBackground(Color.DARK_GRAY);
    }

    private void setupMouseListener() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int square = getSquareFromPoint(e.getPoint());
                if (square >= 0 && square < BOARD_SIZE * BOARD_SIZE && clickListener != null) {
                    clickListener.onSquareClicked(square);
                }
            }
        });
    }

    // === PUBLIC INTERFACE ===

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
        repaint();
    }

    public void setSelectedSquare(int square) {
        this.selectedSquare = square;
        updateLegalMoves();
        repaint();
    }

    public void clearSelection() {
        this.selectedSquare = -1;
        this.legalMoves = null;
        repaint();
    }

    public void setLastMove(Move move) {
        this.lastMove = move;
        repaint();
    }

    public void setClickListener(BoardClickListener listener) {
        this.clickListener = listener;
    }

    public int getSelectedSquare() {
        return selectedSquare;
    }

    public List<Move> getLegalMoves() {
        return legalMoves;
    }

    // === RENDERING ===

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (gameState == null) {
            drawEmptyBoard(g);
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawBoard(g2d);
        drawPieces(g2d);
        drawHighlights(g2d);
        drawCoordinates(g2d);

        g2d.dispose();
    }

    private void drawEmptyBoard(Graphics g) {
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, getWidth(), getHeight());

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 16));
        FontMetrics fm = g.getFontMetrics();
        String text = "No game loaded";
        int x = (getWidth() - fm.stringWidth(text)) / 2;
        int y = getHeight() / 2;
        g.drawString(text, x, y);
    }

    private void drawBoard(Graphics2D g2d) {
        for (int rank = 0; rank < BOARD_SIZE; rank++) {
            for (int file = 0; file < BOARD_SIZE; file++) {
                int x = file * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE;
                int y = rank * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE;

                // Square color
                Color squareColor = ((rank + file) % 2 == 0) ? LIGHT_SQUARE : DARK_SQUARE;
                g2d.setColor(squareColor);
                g2d.fillRect(x, y, CELL_SIZE, CELL_SIZE);

                // Castle squares (D1 and D7)
                int square = rank * BOARD_SIZE + file;
                if (square == 3 || square == 45) { // D1 and D7
                    g2d.setColor(CASTLE_BORDER);
                    g2d.setStroke(new BasicStroke(3));
                    g2d.drawRect(x, y, CELL_SIZE, CELL_SIZE);
                    g2d.setStroke(new BasicStroke(1));
                }

                // Square border
                g2d.setColor(Color.BLACK);
                g2d.drawRect(x, y, CELL_SIZE, CELL_SIZE);
            }
        }
    }

    private void drawHighlights(Graphics2D g2d) {
        // Last move highlight
        if (lastMove != null) {
            drawSquareHighlight(g2d, lastMove.from, LAST_MOVE);
            drawSquareHighlight(g2d, lastMove.to, LAST_MOVE);
        }

        // Selected square highlight
        if (selectedSquare >= 0) {
            drawSquareHighlight(g2d, selectedSquare, SELECTED_SQUARE);
        }

        // Legal moves highlight
        if (legalMoves != null) {
            for (Move move : legalMoves) {
                drawSquareHighlight(g2d, move.to, LEGAL_MOVE);
            }
        }
    }

    private void drawSquareHighlight(Graphics2D g2d, int square, Color color) {
        if (square < 0 || square >= BOARD_SIZE * BOARD_SIZE) return;

        int rank = square / BOARD_SIZE;
        int file = square % BOARD_SIZE;
        int x = file * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE;
        int y = rank * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE;

        g2d.setColor(color);
        g2d.fillRect(x, y, CELL_SIZE, CELL_SIZE);
    }

    private void drawPieces(Graphics2D g2d) {
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        FontMetrics fm = g2d.getFontMetrics();

        for (int square = 0; square < BOARD_SIZE * BOARD_SIZE; square++) {
            int rank = square / BOARD_SIZE;
            int file = square % BOARD_SIZE;
            int x = file * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE;
            int y = rank * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE;

            String pieceText = getPieceText(square);
            if (!pieceText.isEmpty()) {
                Color pieceColor = getPieceColor(square);

                // Center the text
                int textWidth = fm.stringWidth(pieceText);
                int textHeight = fm.getHeight();
                int textX = x + (CELL_SIZE - textWidth) / 2;
                int textY = y + (CELL_SIZE + textHeight) / 2 - fm.getDescent();

                // Draw text with border for better visibility
                g2d.setColor(PIECE_BORDER);
                g2d.drawString(pieceText, textX - 1, textY);
                g2d.drawString(pieceText, textX + 1, textY);
                g2d.drawString(pieceText, textX, textY - 1);
                g2d.drawString(pieceText, textX, textY + 1);

                g2d.setColor(pieceColor);
                g2d.drawString(pieceText, textX, textY);
            }
        }
    }

    private void drawCoordinates(Graphics2D g2d) {
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        FontMetrics fm = g2d.getFontMetrics();

        // Files (A-G)
        for (int file = 0; file < BOARD_SIZE; file++) {
            String fileLabel = String.valueOf((char)('A' + file));
            int x = file * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE + CELL_SIZE / 2 - fm.stringWidth(fileLabel) / 2;
            int y = BOARD_SIZE * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE + fm.getHeight();
            g2d.drawString(fileLabel, x, y);
        }

        // Ranks (1-7)
        for (int rank = 0; rank < BOARD_SIZE; rank++) {
            String rankLabel = String.valueOf(BOARD_SIZE - rank);
            int x = -fm.stringWidth(rankLabel) - 5;
            int y = rank * (CELL_SIZE + BORDER_SIZE) + BORDER_SIZE + CELL_SIZE / 2 + fm.getHeight() / 2;
            g2d.drawString(rankLabel, x, y);
        }
    }

    // === HELPER METHODS ===

    private String getPieceText(int square) {
        if (gameState == null) return "";

        // Guard
        if ((gameState.redGuard & (1L << square)) != 0) {
            return "G";
        }
        if ((gameState.blueGuard & (1L << square)) != 0) {
            return "g";
        }

        // Towers
        if (gameState.redStackHeights[square] > 0) {
            return String.valueOf(gameState.redStackHeights[square]);
        }
        if (gameState.blueStackHeights[square] > 0) {
            return String.valueOf(gameState.blueStackHeights[square]);
        }

        return "";
    }

    private Color getPieceColor(int square) {
        if (gameState == null) return Color.BLACK;

        if (gameState.redStackHeights[square] > 0 || (gameState.redGuard & (1L << square)) != 0) {
            return RED_PIECE;
        }
        if (gameState.blueStackHeights[square] > 0 || (gameState.blueGuard & (1L << square)) != 0) {
            return BLUE_PIECE;
        }

        return Color.BLACK;
    }

    private int getSquareFromPoint(Point point) {
        int file = (point.x - BORDER_SIZE) / (CELL_SIZE + BORDER_SIZE);
        int rank = (point.y - BORDER_SIZE) / (CELL_SIZE + BORDER_SIZE);

        if (file >= 0 && file < BOARD_SIZE && rank >= 0 && rank < BOARD_SIZE) {
            return rank * BOARD_SIZE + file;
        }

        return -1;
    }

    private void updateLegalMoves() {
        if (gameState == null || selectedSquare < 0) {
            legalMoves = null;
            return;
        }

        try {
            List<Move> allMoves = MoveGenerator.generateAllMoves(gameState);
            legalMoves = allMoves.stream()
                    .filter(move -> move.from == selectedSquare)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            legalMoves = null;
        }
    }

    // === UTILITY METHODS ===

    public boolean hasLegalMoveToSquare(int toSquare) {
        if (legalMoves == null || selectedSquare < 0) return false;

        return legalMoves.stream()
                .anyMatch(move -> move.to == toSquare);
    }

    public Move getLegalMoveToSquare(int toSquare) {
        if (legalMoves == null || selectedSquare < 0) return null;

        return legalMoves.stream()
                .filter(move -> move.to == toSquare)
                .findFirst()
                .orElse(null);
    }

    public boolean hasPiece(int square, boolean red) {
        if (gameState == null) return false;

        if (red) {
            return gameState.redStackHeights[square] > 0 ||
                    (gameState.redGuard & (1L << square)) != 0;
        } else {
            return gameState.blueStackHeights[square] > 0 ||
                    (gameState.blueGuard & (1L << square)) != 0;
        }
    }

    public String getSquareName(int square) {
        if (square < 0 || square >= BOARD_SIZE * BOARD_SIZE) return "??";

        int rank = square / BOARD_SIZE;
        int file = square % BOARD_SIZE;
        return String.valueOf((char)('A' + file)) + (BOARD_SIZE - rank);
    }

    // === DEBUG METHODS ===

    public void highlightSquare(int square, Color color) {
        // For debugging - can be used to highlight specific squares
        Graphics2D g2d = (Graphics2D) getGraphics();
        if (g2d != null) {
            drawSquareHighlight(g2d, square, color);
            g2d.dispose();
        }
    }

    public void printBoardState() {
        if (gameState == null) {
            System.out.println("No game state loaded");
            return;
        }

        System.out.println("=== BOARD STATE ===");
        for (int rank = 0; rank < BOARD_SIZE; rank++) {
            for (int file = 0; file < BOARD_SIZE; file++) {
                int square = rank * BOARD_SIZE + file;
                String piece = getPieceText(square);
                System.out.print(piece.isEmpty() ? "." : piece);
                System.out.print(" ");
            }
            System.out.println();
        }
        System.out.println("===================");
    }
}
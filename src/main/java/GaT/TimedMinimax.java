package GaT;
import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.Objects.TTEntry;

import java.util.List;

public class TimedMinimax {

    private static long timeLimitMillis;
    private static long startTime;

    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        TimedMinimax.timeLimitMillis = timeMillis;
        TimedMinimax.startTime = System.currentTimeMillis();

        // NEU: Reset Killer Moves gelegentlich für Frische
        Minimax.resetKillerMoves();

        Move bestMove = null;
        Move lastCompleteMove = null;

        System.out.println("=== Starting Iterative Deepening ===");

        for (int depth = 1; depth <= maxDepth; depth++) {
            if (timedOut()) {
                System.out.println("⏱ Time limit reached before depth " + depth);
                break;
            }

            long depthStartTime = System.currentTimeMillis();

            try {
                // Verwende die verbesserte Minimax-Suche
                Move candidate = searchDepthWithBetterTT(state, depth);

                if (candidate != null) {
                    lastCompleteMove = candidate;
                    bestMove = candidate;

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    System.out.println("✓ Depth " + depth + " completed in " + depthTime + "ms. Best: " + candidate);

                    // Prüfe ob wir eine Winning-Position gefunden haben
                    GameState testState = state.copy();
                    testState.applyMove(candidate);
                    if (Minimax.isGameOver(testState)) {
                        System.out.println("🎯 Winning move found at depth " + depth);
                        break;
                    }
                }

            } catch (TimeoutException e) {
                System.out.println("⏱ Timeout at depth " + depth);
                break;
            }

            // Adaptive time management: Wenn wir weniger als 20% Zeit haben, stoppe
            long elapsed = System.currentTimeMillis() - startTime;
            long remaining = timeMillis - elapsed;
            if (remaining < timeMillis * 0.2) {
                System.out.println("⚡ Stopping early to save time. Remaining: " + remaining + "ms");
                break;
            }
        }

        System.out.println("=== Search completed. Best move: " + bestMove + " ===");
        return bestMove != null ? bestMove : lastCompleteMove;
    }

    /**
     * Einzelne Tiefe mit verbesserter TT Integration - verwendet Minimax.minimaxWithTimeout()
     */
    private static Move searchDepthWithBetterTT(GameState state, int depth) throws TimeoutException {
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);

        // NEU: Verwende erweiterte Move Ordering mit Killer Moves
        orderMovesWithAdvancedHeuristics(moves, state, depth, entry);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (Move move : moves) {
            if (timedOut()) throw new TimeoutException();

            GameState copy = state.copy();
            copy.applyMove(move);

            try {
                int score = Minimax.minimaxWithTimeout(copy, depth - 1, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, !isRed, () -> timedOut());

                if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                    bestScore = score;
                    bestMove = move;
                }
            } catch (RuntimeException e) {
                if (e.getMessage().equals("Timeout")) {
                    throw new TimeoutException();
                }
                throw e; // Re-throw andere Exceptions
            }
        }

        return bestMove;
    }

    /**
     * NEU: Erweiterte Move ordering - delegiert an Minimax für konsistente Logik
     */
    private static void orderMovesWithAdvancedHeuristics(List<Move> moves, GameState state, int depth, TTEntry entry) {
        // Verwende die bereits implementierte Methode aus Minimax für konsistente Ordering
        Minimax.orderMovesAdvanced(moves, state, depth, entry);
    }

    /**
     * Fallback: Einfache TT-basierte Move ordering (für Kompatibilität)
     */
    private static void orderMovesWithTT(List<Move> moves, TTEntry entry) {
        // TT Move an erste Stelle setzen
        if (entry != null && entry.bestMove != null) {
            for (int i = 0; i < moves.size(); i++) {
                if (moves.get(i).equals(entry.bestMove)) {
                    Move ttMove = moves.remove(i);
                    moves.add(0, ttMove);
                    break;
                }
            }
        }

        // Rest sortieren mit erweiterten Heuristiken falls verfügbar
        if (moves.size() > 1) {
            int startIndex = (entry != null && entry.bestMove != null) ? 1 : 0;
            List<Move> restMoves = moves.subList(startIndex, moves.size());

            restMoves.sort((a, b) -> {
                // Versuche erweiterte Bewertung, falls nicht verfügbar verwende einfache
                try {
                    int scoreA = Minimax.scoreMoveAdvanced(null, a, 0);
                    int scoreB = Minimax.scoreMoveAdvanced(null, b, 0);
                    return Integer.compare(scoreB, scoreA);
                } catch (Exception e) {
                    // Fallback zu einfacher Bewertung
                    int scoreA = Minimax.scoreMove(null, a);
                    int scoreB = Minimax.scoreMove(null, b);
                    return Integer.compare(scoreB, scoreA);
                }
            });
        }
    }

    private static boolean timedOut() {
        return System.currentTimeMillis() - startTime >= timeLimitMillis;
    }

    private static class TimeoutException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
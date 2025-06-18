package GaT;

import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.Objects.TTEntry;

import java.util.List;
import java.util.function.BooleanSupplier;

public class PVSSearch {

    private static BooleanSupplier timeoutChecker = null;

    /**
     * Standard PVS ohne Quiescence
     */
    public static int search(GameState state, int depth, int alpha, int beta,
                             boolean maximizingPlayer, boolean isPVNode) {

        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Timeout");
        }

        // TT-Lookup nur bei Non-PV Knoten oder mit Vorsicht
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);
        if (entry != null && entry.depth >= depth) {
            // In PV-Knoten nur EXACT Scores verwenden
            if (entry.flag == TTEntry.EXACT && (!isPVNode || depth <= 0)) {
                return entry.score;
            } else if (!isPVNode) { // Nur in Non-PV normale TT-Cutoffs
                if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                    return entry.score;
                } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                    return entry.score;
                }
            }
        }

        // Terminal conditions
        if (depth == 0 || Minimax.isGameOver(state)) {
            return Minimax.evaluate(state, depth);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Verschiedene Move Ordering für PV vs Non-PV
        if (isPVNode) {
            orderMovesForPV(moves, state, depth, entry);
        } else {
            Minimax.orderMovesAdvanced(moves, state, depth, entry);
        }

        Move bestMove = null;
        int originalAlpha = alpha;
        boolean isFirstMove = true;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                Minimax.counter++;

                int eval;

                if (isFirstMove || isPVNode) {
                    // Erste Züge UND PV-Knoten bekommen volle Suche
                    eval = search(copy, depth - 1, alpha, beta, false, isPVNode);
                    isFirstMove = false;
                } else {
                    // Weniger aggressive Null-Window für bessere Differenzierung
                    int nullWindow = isPVNode ? alpha + 10 : alpha + 1;
                    eval = search(copy, depth - 1, alpha, nullWindow, false, false);

                    if (eval > alpha && eval < beta) {
                        // Re-search mit vollem Fenster
                        eval = search(copy, depth - 1, eval, beta, false, true);
                    }
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            storeTTEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                Minimax.counter++;

                int eval;

                if (isFirstMove || isPVNode) {
                    eval = search(copy, depth - 1, alpha, beta, true, isPVNode);
                    isFirstMove = false;
                } else {
                    int nullWindow = isPVNode ? beta - 10 : beta - 1;
                    eval = search(copy, depth - 1, nullWindow, beta, true, false);

                    if (eval < beta && eval > alpha) {
                        eval = search(copy, depth - 1, alpha, eval, true, true);
                    }
                }

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            storeTTEntry(hash, minEval, depth, originalAlpha, beta, bestMove);
            return minEval;
        }
    }

    /**
     * PVS mit Quiescence Search Integration
     */
    public static int searchWithQuiescence(GameState state, int depth, int alpha, int beta,
                                           boolean maximizingPlayer, boolean isPVNode) {

        if (timeoutChecker != null && timeoutChecker.getAsBoolean()) {
            throw new RuntimeException("Timeout");
        }

        // TT-Lookup nur bei Non-PV Knoten oder mit Vorsicht
        long hash = state.hash();
        TTEntry entry = Minimax.getTranspositionEntry(hash);
        if (entry != null && entry.depth >= depth) {
            // In PV-Knoten nur EXACT Scores verwenden
            if (entry.flag == TTEntry.EXACT && (!isPVNode || depth <= 0)) {
                return entry.score;
            } else if (!isPVNode) { // Nur in Non-PV normale TT-Cutoffs
                if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                    return entry.score;
                } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                    return entry.score;
                }
            }
        }

        // Terminal conditions
        if (Minimax.isGameOver(state)) {
            return Minimax.evaluate(state, depth);
        }

        // Quiescence Search wenn Tiefe erschöpft
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Verschiedene Move Ordering für PV vs Non-PV
        if (isPVNode) {
            orderMovesForPV(moves, state, depth, entry);
        } else {
            Minimax.orderMovesAdvanced(moves, state, depth, entry);
        }

        Move bestMove = null;
        int originalAlpha = alpha;
        boolean isFirstMove = true;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                Minimax.counter++;

                int eval;

                if (isFirstMove || isPVNode) {
                    // Erste Züge UND PV-Knoten bekommen volle Suche
                    eval = searchWithQuiescence(copy, depth - 1, alpha, beta, false, isPVNode);
                    isFirstMove = false;
                } else {
                    // Weniger aggressive Null-Window für bessere Differenzierung
                    int nullWindow = isPVNode ? alpha + 10 : alpha + 1;
                    eval = searchWithQuiescence(copy, depth - 1, alpha, nullWindow, false, false);

                    if (eval > alpha && eval < beta) {
                        // Re-search mit vollem Fenster
                        eval = searchWithQuiescence(copy, depth - 1, eval, beta, false, true);
                    }
                }

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            storeTTEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;

            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                Minimax.counter++;

                int eval;

                if (isFirstMove || isPVNode) {
                    eval = searchWithQuiescence(copy, depth - 1, alpha, beta, true, isPVNode);
                    isFirstMove = false;
                } else {
                    int nullWindow = isPVNode ? beta - 10 : beta - 1;
                    eval = searchWithQuiescence(copy, depth - 1, nullWindow, beta, true, false);

                    if (eval < beta && eval > alpha) {
                        eval = searchWithQuiescence(copy, depth - 1, alpha, eval, true, true);
                    }
                }

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            storeTTEntry(hash, minEval, depth, originalAlpha, beta, bestMove);
            return minEval;
        }
    }

    /**
     * Spezielle Move Ordering für PV-Knoten
     */
    private static void orderMovesForPV(List<Move> moves, GameState state, int depth, TTEntry entry) {
        // TT Move hat weiterhin höchste Priorität
        if (entry != null && entry.bestMove != null) {
            for (int i = 0; i < moves.size(); i++) {
                if (moves.get(i).equals(entry.bestMove)) {
                    Move ttMove = moves.remove(i);
                    moves.add(0, ttMove);
                    break;
                }
            }
        }

        // In PV-Knoten mehr Züge mit hoher Priorität bewerten
        if (moves.size() > 1) {
            int startIndex = (entry != null && entry.bestMove != null) ? 1 : 0;
            List<Move> restMoves = moves.subList(startIndex, moves.size());

            restMoves.sort((a, b) -> {
                int scoreA = scoreMoveForPV(state, a, depth);
                int scoreB = scoreMoveForPV(state, b, depth);
                return Integer.compare(scoreB, scoreA);
            });
        }
    }

    /**
     * Erweiterte Move-Bewertung für PV-Knoten
     */
    private static int scoreMoveForPV(GameState state, Move move, int depth) {
        int score = Minimax.scoreMove(state, move);

        // In PV-Knoten auch ruhige Züge höher bewerten
        if (!isCapture(move, state)) {
            // Bonus für verschiedene Zielfelder (Anti-Repetition)
            score += move.to * 2; // Einfache Variation basierend auf Zielfeld

            // Bonus für verschiedene Anzahl bewegter Stücke
            score += move.amountMoved * 5;

            // Bonus für zentrale Züge
            int targetFile = GameState.file(move.to);
            int targetRank = GameState.rank(move.to);
            int centrality = Math.abs(targetFile - 3) + Math.abs(targetRank - 3);
            score += (6 - centrality) * 3;

            // Anti-Repetition: Bonus für Züge zu neuen Feldern
            long hash = state.hash();
            score += (int)(hash % 20) - 10; // Pseudo-random variation basierend auf Position
        }

        return score;
    }

// === HELPER METHODS ===

    /**
     * Store entry in transposition table
     */
    private static void storeTTEntry(long hash, int score, int depth, int originalAlpha, int beta, Move bestMove) {
        int flag;
        if (score <= originalAlpha) {
            flag = TTEntry.UPPER_BOUND;
        } else if (score >= beta) {
            flag = TTEntry.LOWER_BOUND;
        } else {
            flag = TTEntry.EXACT;
        }

        TTEntry entry = new TTEntry(score, depth, flag, bestMove);
        Minimax.storeTranspositionEntry(hash, entry);
    }

    /**
     * Check if move is a capture
     */
    private static boolean isCapture(Move move, GameState state) {
        if (state == null) return false;

        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        boolean capturesGuard = isRed ?
                (state.blueGuard & toBit) != 0 :
                (state.redGuard & toBit) != 0;

        boolean capturesTower = isRed ?
                (state.blueTowers & toBit) != 0 :
                (state.redTowers & toBit) != 0;

        return capturesGuard || capturesTower;
    }

    /**
     * Store killer move helper
     */
    private static void storeKillerMove(Move move, int depth) {
        try {
            Minimax.storeKillerMove(move, depth);
        } catch (Exception e) {
            // Fallback falls Minimax.storeKillerMove nicht verfügbar
            System.err.println("Could not store killer move: " + e.getMessage());
        }
    }

    /**
     * Set timeout checker from TimedMinimax
     */
    public static void setTimeoutChecker(BooleanSupplier checker) {
        timeoutChecker = checker;
    }



}
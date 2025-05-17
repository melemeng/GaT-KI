package GaT;
import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.Objects.TTEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class TimedMinimax {

    private static long timeLimitMillis;
    private static long startTime;

    private static final HashMap<Long, TTEntry> transpositionTable = new HashMap<>();

    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        TimedMinimax.timeLimitMillis = timeMillis;
        TimedMinimax.startTime = System.currentTimeMillis();

        Move bestMove = null;

        for (int depth = 1; depth <= maxDepth; depth++) {
            if (timedOut()) break;

            try {
                Move move = searchDepth(state, depth);
                bestMove = move;
                System.out.println("✓ Depth " + depth + " completed. Best so far: " + move);
            } catch (TimeoutException e) {
                System.out.println("⏱ Timeout at depth " + depth);
                break;
            }
        }

        return bestMove;
    }

    private static Move searchDepth(GameState state, int depth) throws TimeoutException {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        Random rand = new Random();
//        moves.sort((a, b) -> {
//            int scoreA = Minimax.scoreMove(state, a);
//            int scoreB = Minimax.scoreMove(state, b);
//            int cmp = Integer.compare(scoreB, scoreA);
//            return cmp != 0 ? cmp : rand.nextInt(3) - 1; // shuffle equally good moves
//        });
        moves.sort((a, b) -> Integer.compare(
                Minimax.scoreMove(state, b),
                Minimax.scoreMove(state, a)
        ));


        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (Move move : moves) {
            if (timedOut()) throw new TimeoutException();

            GameState copy = state.copy();
            copy.applyMove(move);

            int score = minimax(copy, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, !isRed);

            if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                bestScore = score;
                bestMove = move;
            }
        }

        return bestMove;
    }

    private static int minimax(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) throws TimeoutException {
        if (timedOut()) throw new TimeoutException();

        long hash= state.hash();
        TTEntry entry = transpositionTable.get(hash);
        if(entry !=null && entry.depth >= depth){
            return entry.score;
        }

        if (depth == 0 || Minimax.isGameOver(state)) {
            return Minimax.evaluate(state, depth);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                if (timedOut()) throw new TimeoutException();

                GameState copy = state.copy();
                copy.applyMove(move);
                int eval = minimax(copy, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            transpositionTable.put(hash, new TTEntry(maxEval, depth, alpha, beta));
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                if (timedOut()) throw new TimeoutException();

                GameState copy = state.copy();
                copy.applyMove(move);
                int eval = minimax(copy, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;
            }
            transpositionTable.put(hash, new TTEntry(minEval, depth, alpha, beta));
            return minEval;
        }
    }

    private static boolean timedOut() {
        return System.currentTimeMillis() - startTime >= timeLimitMillis;
    }

    private static class TimeoutException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}


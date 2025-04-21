package GaT;

import java.util.List;

public class Minimax {

    public static Move findBestMove(GameState state, int depth) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        moves.sort((a, b) -> Integer.compare(
                scoreMove(state, b),
                scoreMove(state, a)
        ));

        Move bestMove = null;
        int bestScore = Integer.MIN_VALUE;

        for (Move move : moves) {
            GameState copy = state.copy();
            copy.applyMove(move);
            int score = minimax(copy, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, true);

            if (score > bestScore || bestMove == null) {
                bestScore = score;
                bestMove = move;
            }
        }

        return bestMove;
    }

    private static int minimax(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        if (depth == 0 || isGameOver(state)) {
            return evaluate(state);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                int eval = minimax(copy, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;   //prune
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                int eval = minimax(copy, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;   //prune
            }
            return minEval;
        }
    }

    private static boolean isGameOver(GameState state) {
        boolean blueGuardOnD7= state.blueGuard == GameState.bit(GameState.getIndex(3,7));
        boolean redGuardOnD1 = state.redGuard == GameState.bit(GameState.getIndex(3,1));
        return state.redGuard == 0 || state.blueGuard == 0 || blueGuardOnD7 || redGuardOnD1;
    }

    // Same as earlier
    private static int evaluate(GameState state) {
        int redScore = 0;
        int blueScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            redScore += state.redStackHeights[i];
            blueScore += state.blueStackHeights[i];
        }

        if (state.redGuard != 0) redScore += 10;
        if (state.blueGuard != 0) blueScore += 10;

        return state.redToMove ? (redScore - blueScore) : (blueScore - redScore);
    }

    public static int scoreMove(GameState state, Move move) {
        int to = move.to;
        long toBit = GameState.bit(to);
        boolean isRed = state.redToMove;

        boolean capturesGuard = isRed
                ? (state.blueGuard & toBit) != 0
                : (state.redGuard & toBit) != 0;

        boolean capturesTower = isRed
                ? (state.blueTowers & toBit) != 0
                : (state.redTowers & toBit) != 0;

        boolean stacksOnOwn = isRed
                ? (state.redTowers & toBit) != 0
                : (state.blueTowers & toBit) != 0;

        int score = 0;
        if (capturesGuard) score += 5000;
        if (capturesTower) score += 1000;
        if (stacksOnOwn) score += 10;
        score += move.amountMoved; // prefer bigger towers

        return score;
    }

}


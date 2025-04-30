package GaT;

import java.util.List;
import java.util.Set;

import static GaT.GameState.getIndex;


public class Minimax {
    public static final int RED_CASTLE_INDEX = getIndex(6, 3); // D7
    public static final int BLUE_CASTLE_INDEX = getIndex(0, 3); // D1


    public static Move findBestMove(GameState state, int depth) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        //Sorting the moves using the scoreMove heuristic to find potentially better moves first
        moves.sort((a, b) -> Integer.compare(
                scoreMove(state, b),
                scoreMove(state, a)
        ));

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (Move move : moves) {
            GameState copy = state.copy();
            copy.applyMove(move);

            //Start algorithm with !isRed to simulate the next move from the enemy
            int score = minimax(copy, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, copy.redToMove);
            System.out.println(move + " -> Score: " + score);

            //Update the best move depending on if we are the max- or minimizer
            if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
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

    public static boolean isGameOver(GameState state) {
        boolean blueGuardOnD7= (state.blueGuard & GameState.bit(getIndex(6,3))) !=0;
        boolean redGuardOnD1 = (state.redGuard & GameState.bit(getIndex(0,3))) !=0;
        return state.redGuard == 0 || state.blueGuard == 0 || blueGuardOnD7 || redGuardOnD1;
    }

    /**
     * @apiNote this function is bases of reds POV --> positive values are good for red | negative values are good for blue
     */
    public static int evaluate(GameState state) {
        boolean redWinsByCastle = state.redGuard == GameState.bit(BLUE_CASTLE_INDEX);
        boolean blueWinsByCastle =state.blueGuard == GameState.bit(RED_CASTLE_INDEX);

        int redScore = 0;
        int blueScore = 0;

        //If the done move took the guard or "rushed the castle" give it a huge favor
        if (state.redGuard == 0 || blueWinsByCastle) blueScore+= 10000;
        if (state.blueGuard == 0 || redWinsByCastle) redScore += 10000;

        boolean redGuardInDanger = isGuardInDanger(state, true);
        boolean blueGuardInDanger = isGuardInDanger(state, false);

        if(state.redToMove){
            //If red can move and the enemy guard can be taken --> win
            if(redGuardInDanger && blueGuardInDanger) redScore += 10000;
            //If red can move and only red is in danger --> potential lose, so avoid this position
            if(redGuardInDanger && !blueGuardInDanger) redScore -= 10000;
        }else{
            if(blueGuardInDanger && redGuardInDanger) blueScore += 10000;
            if(blueGuardInDanger && !redGuardInDanger) blueScore -= 10000;
        }

//        Bonus for red guard being close to blue castle
        if (state.redGuard != 0) {
            int guardIndex = Long.numberOfTrailingZeros(state.redGuard);
            int guardRank = GameState.rank(guardIndex);
            int guardFile = GameState.file(guardIndex);

            int distanceToBlueCastleRank = Math.abs(guardRank - 0);  // red wants to reach rank 0
            int distanceToBlueCastleFile = Math.abs(guardFile - 3);  // D-file is column 3

            int rankBonus = (6 - distanceToBlueCastleRank) * 2000;   // moving down = good
            int fileBonus = (3 - distanceToBlueCastleFile) * 2000;    // closer to D-file = good

            redScore += rankBonus + fileBonus;
        }

        // Bonus for blue guard being close to red castle
        if (state.blueGuard != 0) {
            int guardIndex = Long.numberOfTrailingZeros(state.blueGuard);
            int guardRank = GameState.rank(guardIndex);
            int guardFile = GameState.file(guardIndex);

            int distanceToRedCastleRank = Math.abs(guardRank - 6);  // blue wants to reach rank 6 (row 7)
            int distanceToRedCastleFile = Math.abs(guardFile - 3);

            int rankBonus = (6 - distanceToRedCastleRank) * 2000;   // moving up = good
            int fileBonus = (3 - distanceToRedCastleFile) * 2000;

            blueScore += rankBonus + fileBonus;
        }

        //Count Material Diff (might want to remove that)
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            redScore += state.redStackHeights[i];
            blueScore += state.blueStackHeights[i];
        }
        return redScore - blueScore;
    }

    /**
     * @param state
     * @param checkRed true if red guard position should be evaluated
     * @return if the next enemy move would take the Guard
     */
    private static boolean isGuardInDanger(GameState state, boolean checkRed) {
        GameState copy = state.copy();
        copy.redToMove = !checkRed;     //Invert to make the enemy move next
        long targetGuard = checkRed ? state.redGuard : state.blueGuard;

        for (Move m : MoveGenerator.generateAllMoves(copy)) {
            if (GameState.bit(m.to) == targetGuard) return true;
        }
        return false;
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

        boolean isGuardMove = (move.amountMoved == 1) &&
                (((isRed && (state.redGuard & GameState.bit(move.from)) != 0)) ||
                        (!isRed && (state.blueGuard & GameState.bit(move.from)) != 0));

        boolean entersCastle = (isRed && move.to == BLUE_CASTLE_INDEX) ||
                (!isRed && move.to == RED_CASTLE_INDEX);


        int score = 0;
        if (entersCastle && isGuardMove) score += 10000;
        if (capturesGuard) score += 10000;
        if (capturesTower) score += 500 * (isRed ? state.redStackHeights[move.to] : state.blueStackHeights[move.to]);
        if (stacksOnOwn) score += 10;
        score += move.amountMoved;

        if (isGuardMove && isExposed(move, state)) {
            score -= 10000;
        }

        return score;
    }

    /**
     * @implNote basically does the same as isGuardInDanger() but applies a move before
     */
    private static boolean isExposed(Move move, GameState state) {
        GameState copy = state.copy();
        copy.applyMove(move);  // simulate the move

        boolean redToMove = state.redToMove;
        long guardPosition = redToMove ? copy.redGuard : copy.blueGuard;

        // Simulate opponent's turn — generate all moves
        copy.redToMove = !redToMove;
        List<Move> enemyMoves = MoveGenerator.generateAllMoves(copy);

        for (Move m : enemyMoves) {
            if (GameState.bit(m.to) == guardPosition) {
                return true; // guard can be captured
            }
        }
        return false;
    }


}


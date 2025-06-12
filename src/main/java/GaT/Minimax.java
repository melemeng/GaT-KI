package GaT;

import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.Objects.TTEntry;

import java.util.HashMap;
import java.util.List;

import static GaT.Objects.GameState.getIndex;


public class Minimax {
    public static final int RED_CASTLE_INDEX = getIndex(6, 3); // D7
    public static final int BLUE_CASTLE_INDEX = getIndex(0, 3); // D1
    static int counter= 0;

    private static final HashMap<Long, TTEntry> transpositionTable = new HashMap<>();

    final static int[] centralSquares = {
            GameState.getIndex(2, 3), // D3
            GameState.getIndex(3, 3), // D4
            GameState.getIndex(4, 3)  // D5
    };



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
            counter++;

            //Start algorithm with !isRed to simulate the next move from the enemy
            int score = minimax(copy, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, copy.redToMove);
            System.out.println(move + " -> Score: " + score);       //Debug line

            //Update the best move depending on if we are the max- or minimizer
            if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                bestScore = score;
                bestMove = move;
            }
        }
        System.out.println("Zustände: "+counter);
        return bestMove;
    }


    private static int minimax(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        //Check for existing Entry in the Transposition Table
        long hash= state.hash();
        TTEntry entry = transpositionTable.get(hash);
        if(entry !=null && entry.depth >= depth){
            return entry.score;
        }

        if (depth == 0 || isGameOver(state)) {
            return evaluate(state, depth);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;
                int eval = minimax(copy, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;   //prune
            }
            transpositionTable.put(hash, new TTEntry(maxEval, depth, alpha, beta));
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;
                int eval = minimax(copy, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;   //prune
            }
            transpositionTable.put(hash, new TTEntry(minEval, depth, alpha, beta));
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
    public static int evaluate(GameState state, int depth) {
        boolean redWinsByCastle = state.redGuard == GameState.bit(BLUE_CASTLE_INDEX);
        boolean blueWinsByCastle = state.blueGuard == GameState.bit(RED_CASTLE_INDEX);

        int redScore = 0;
        int blueScore = 0;

        //If the done move took the guard or "rushed the castle" give it a huge favor
        //Include the depth to reward early wins and penalize early losses
        if (state.redGuard == 0 || blueWinsByCastle) return -10000 - depth;
        if (state.blueGuard == 0 || redWinsByCastle) return 10000 + depth;


        boolean redGuardInDanger = isGuardInDanger(state, true);
        boolean blueGuardInDanger = isGuardInDanger(state, false);

        if(state.redToMove){
            if(redGuardInDanger && blueGuardInDanger) return 10000 + depth;
            if(redGuardInDanger && !blueGuardInDanger) return -10000 - depth;
        }else{
            if(blueGuardInDanger && redGuardInDanger) return -10000 - depth;
            if(blueGuardInDanger && !redGuardInDanger) return 10000 + depth;
        }

        // === VERBESSERTE WÄCHTER-BEWERTUNG ===
        // Bonus for red guard being close to blue castle
        if (state.redGuard != 0) {
            int guardIndex = Long.numberOfTrailingZeros(state.redGuard);
            int guardRank = GameState.rank(guardIndex);
            int guardFile = GameState.file(guardIndex);

            int distanceToBlueCastleRank = Math.abs(guardRank - 0);  // red wants to reach rank 0
            int distanceToBlueCastleFile = Math.abs(guardFile - 3);  // D-file is column 3

            int rankBonus = (6 - distanceToBlueCastleRank) * 500;   // moving down = good
            int fileBonus = (3 - distanceToBlueCastleFile) * 300;    // closer to D-file = good

            redScore += rankBonus + fileBonus;

            // NEU: Bonus für unterstützende Türme
            int support = countSupportingTowers(guardIndex, true, state);
            redScore += support * 100;

            // NEU: Penalty wenn Wächter blockiert ist
            if (isGuardBlocked(guardIndex, true, state)) {
                redScore -= 200;
            }
        }

        // Bonus for blue guard being close to red castle
        if (state.blueGuard != 0) {
            int guardIndex = Long.numberOfTrailingZeros(state.blueGuard);
            int guardRank = GameState.rank(guardIndex);
            int guardFile = GameState.file(guardIndex);

            int distanceToRedCastleRank = Math.abs(guardRank - 6);  // blue wants to reach rank 6 (row 7)
            int distanceToRedCastleFile = Math.abs(guardFile - 3);

            int rankBonus = (6 - distanceToRedCastleRank) * 500;   // moving up = good
            int fileBonus = (3 - distanceToRedCastleFile) * 300;

            blueScore += rankBonus + fileBonus;

            // NEU: Support und Blockade-Check
            int support = countSupportingTowers(guardIndex, false, state);
            blueScore += support * 100;

            if (isGuardBlocked(guardIndex, false, state)) {
                blueScore -= 200;
            }
        }

        // === VERBESSERTE ZENTRALE KONTROLLE ===
        for (int index : centralSquares){
            long bit = GameState.bit(index);

            // Türme im Zentrum mit Höhenbonus
            if ((state.redTowers & bit) != 0) {
                redScore += 300 + (state.redStackHeights[index] * 50);
            }
            if ((state.blueTowers & bit) != 0) {
                blueScore += 300 + (state.blueStackHeights[index] * 50);
            }

            if((state.redGuard & bit) != 0) redScore += 200;
            if(((state.blueGuard & bit) != 0)) blueScore += 200;
        }

        // === VERBESSERTE MATERIAL-BEWERTUNG ===
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                int height = state.redStackHeights[i];
                redScore += height * 100; // Basis-Materialwert

                // NEU: Mobilitätsbonus
                int mobility = calculateActualMobility(i, height, state);
                redScore += mobility * 20;

                // NEU: Penalty für große immobile Stapel
                if (height >= 4 && mobility == 0) {
                    redScore -= 150;
                }

                // NEU: Bonus für Stapel nahe gegnerischem Wächter
                if (state.blueGuard != 0) {
                    int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
                    int distance = manhattanDistance(i, blueGuardPos);
                    if (distance <= height) {
                        redScore += 80; // Bedrohungspotential
                    }
                }
            }

            if (state.blueStackHeights[i] > 0) {
                int height = state.blueStackHeights[i];
                blueScore += height * 100;

                int mobility = calculateActualMobility(i, height, state);
                blueScore += mobility * 20;

                if (height >= 4 && mobility == 0) {
                    blueScore -= 150;
                }

                if (state.redGuard != 0) {
                    int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
                    int distance = manhattanDistance(i, redGuardPos);
                    if (distance <= height) {
                        blueScore += 80;
                    }
                }
            }
        }

        // === ENDSPIEL-ANPASSUNGEN ===
        int totalTowers = Long.bitCount(state.redTowers | state.blueTowers);
        if (totalTowers <= 6) {
            // Im Endspiel ist Wächter-Fortschritt wichtiger
            if (state.redGuard != 0) {
                int gRank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
                redScore += gRank * 200; // Extra Bonus
            }
            if (state.blueGuard != 0) {
                int gRank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
                blueScore += (6 - gRank) * 200;
            }
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

    // === NEUE HILFSMETHODEN ===

    private static int countSupportingTowers(int guardPos, boolean isRed, GameState state) {
        long ownTowers = isRed ? state.redTowers : state.blueTowers;
        int count = 0;

        // Prüfe 2-Feld Radius
        for (int rank = -2; rank <= 2; rank++) {
            for (int file = -2; file <= 2; file++) {
                if (rank == 0 && file == 0) continue;

                int checkRank = GameState.rank(guardPos) + rank;
                int checkFile = GameState.file(guardPos) + file;

                if (checkRank >= 0 && checkRank < 7 && checkFile >= 0 && checkFile < 7) {
                    int index = GameState.getIndex(checkRank, checkFile);
                    if ((ownTowers & GameState.bit(index)) != 0) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    private static boolean isGuardBlocked(int guardPos, boolean isRed, GameState state) {
        // Sicherheitscheck
        if (!GameState.isOnBoard(guardPos)) return false;

        int[] directions = {-1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE};
        int blockedCount = 0;
        int validDirections = 0;

        for (int dir : directions) {
            int target = guardPos + dir;
            validDirections++;

            // Rand des Bretts
            if (!GameState.isOnBoard(target)) {
                blockedCount++;
                continue;
            }

            // Kantenwechsel prüfen bei horizontaler Bewegung
            if (Math.abs(dir) == 1 && GameState.rank(guardPos) != GameState.rank(target)) {
                blockedCount++;
                continue;
            }

            // Prüfe ob Feld besetzt
            if (((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard)
                    & GameState.bit(target)) != 0) {
                blockedCount++;
            }
        }

        // Wächter ist blockiert wenn mindestens 3 von 4 Richtungen blockiert sind
        return validDirections > 0 && blockedCount >= 3;
    }

    private static int calculateActualMobility(int index, int height, GameState state) {
        // Sicherheitscheck
        if (height <= 0 || height > 7) return 0;
        if (!GameState.isOnBoard(index)) return 0;

        int mobility = 0;
        int[] directions = {-1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE};

        for (int dir : directions) {
            for (int dist = 1; dist <= height && dist <= 7; dist++) { // Extra Check für dist
                int target = index + dir * dist;

                if (!GameState.isOnBoard(target)) break;

                // Kantenwechsel prüfen bei horizontalen Zügen
                if (Math.abs(dir) == 1) { // Horizontale Bewegung
                    // Prüfe alle Zwischenschritte auf Kantenwechsel
                    boolean edgeWrap = false;
                    for (int step = 1; step <= dist; step++) {
                        int intermediate = index + dir * step;
                        if (GameState.rank(index) != GameState.rank(intermediate)) {
                            edgeWrap = true;
                            break;
                        }
                    }
                    if (edgeWrap) break;
                }

                // Prüfe ob Zug möglich wäre
                long allPieces = state.redTowers | state.blueTowers | state.redGuard | state.blueGuard;

                // Blockiert durch andere Figur auf dem Weg?
                boolean blocked = false;
                for (int i = 1; i < dist; i++) {
                    int between = index + dir * i;
                    if ((allPieces & GameState.bit(between)) != 0) {
                        blocked = true;
                        break;
                    }
                }

                if (!blocked) {
                    mobility++;

                    // Bonus wenn Zielfeld leer ist
                    if ((allPieces & GameState.bit(target)) == 0) {
                        mobility++;
                    }
                } else {
                    break;
                }
            }
        }

        return mobility;
    }

    private static int manhattanDistance(int index1, int index2) {
        int rank1 = GameState.rank(index1);
        int file1 = GameState.file(index1);
        int rank2 = GameState.rank(index2);
        int file2 = GameState.file(index2);

        return Math.abs(rank1 - rank2) + Math.abs(file1 - file2);
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
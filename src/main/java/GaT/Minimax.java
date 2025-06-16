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
    static int counter = 0;

    private static final HashMap<Long, TTEntry> transpositionTable = new HashMap<>();

    final static int[] centralSquares = {
            GameState.getIndex(2, 3), // D3
            GameState.getIndex(3, 3), // D4
            GameState.getIndex(4, 3)  // D5
    };

    // === KILLER MOVES & PRINCIPAL VARIATION ===
    private static Move[][] killerMoves = new Move[20][2]; // [depth][primary/secondary]
    private static int killerAge = 0; // Für periodisches Reset
    private static Move[] pvLine = new Move[20]; // Hauptvariation speichern

    public static Move findBestMove(GameState state, int depth) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Sorting the moves using the scoreMove heuristic to find potentially better moves first
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

            // Start algorithm with !isRed to simulate the next move from the enemy
            int score = minimax(copy, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, copy.redToMove);
            System.out.println(move + " -> Score: " + score);       // Debug line

            // Update the best move depending on if we are the max- or minimizer
            if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                bestScore = score;
                bestMove = move;
            }
        }
        System.out.println("Zustände: " + counter);
        return bestMove;
    }

    private static int minimax(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        // Check for existing Entry in the Transposition Table
        long hash = state.hash();
        TTEntry entry = transpositionTable.get(hash);
        if (entry != null && entry.depth >= depth) {
            // KORRIGIERT: Verbesserte TT Logik
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        }

        if (depth == 0 || isGameOver(state)) {
            return evaluate(state, depth);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // NEU: Erweiterte Move Ordering mit Killer Moves
        orderMovesAdvanced(moves, state, depth, entry);

        Move bestMove = null; // Tracke besten Zug
        int originalAlpha = alpha; // Für TT Flag Bestimmung

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;
                int eval = minimax(copy, depth - 1, alpha, beta, false);

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move; // Update besten Zug
                    // NEU: Speichere in Principal Variation
                    storePVMove(move, depth);
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    // NEU: Killer Move speichern bei Beta-Cutoff
                    if (!isCapture(move, state)) { // Nur Non-Captures als Killer
                        storeKillerMove(move, depth);
                    }
                    break;   // prune
                }
            }

            // KORRIGIERT: Korrekte TT Flag Logik
            int flag;
            if (maxEval <= originalAlpha) {
                flag = TTEntry.UPPER_BOUND; // Fail-low: Score ist höchstens maxEval
            } else if (maxEval >= beta) {
                flag = TTEntry.LOWER_BOUND; // Fail-high: Score ist mindestens maxEval
            } else {
                flag = TTEntry.EXACT; // Exakter Wert zwischen alpha und beta
            }
            transpositionTable.put(hash, new TTEntry(maxEval, depth, flag, bestMove));

            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;
                int eval = minimax(copy, depth - 1, alpha, beta, true);

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move; // Update besten Zug
                    // NEU: Speichere in Principal Variation
                    storePVMove(move, depth);
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    // NEU: Killer Move speichern bei Beta-Cutoff
                    if (!isCapture(move, state)) {
                        storeKillerMove(move, depth);
                    }
                    break;   // prune
                }
            }

            // KORRIGIERT: Korrekte TT Flag Logik für minimizing player
            int flag;
            if (minEval <= originalAlpha) {
                flag = TTEntry.UPPER_BOUND; // Fail-low
            } else if (minEval >= beta) {
                flag = TTEntry.LOWER_BOUND; // Fail-high
            } else {
                flag = TTEntry.EXACT; // Exakter Wert
            }
            transpositionTable.put(hash, new TTEntry(minEval, depth, flag, bestMove));

            return minEval;
        }
    }

    /**
     * Erweiterte Move Ordering: TT Move > Killer Moves > PV Move > Score
     */
    private static void orderMovesWithTT(List<Move> moves, TTEntry entry) {
        orderMovesAdvanced(moves, null, 0, entry);
    }

    /**
     * Verbesserte Move Ordering mit allen Heuristiken
     */
    public static void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry entry) {
        // TT Move an erste Stelle (höchste Priorität)
        if (entry != null && entry.bestMove != null) {
            for (int i = 0; i < moves.size(); i++) {
                if (moves.get(i).equals(entry.bestMove)) {
                    Move ttMove = moves.remove(i);
                    moves.add(0, ttMove);
                    break;
                }
            }
        }

        // Sortiere den Rest mit erweiterten Heuristiken
        if (moves.size() > 1) {
            int startIndex = (entry != null && entry.bestMove != null) ? 1 : 0;
            List<Move> restMoves = moves.subList(startIndex, moves.size());

            restMoves.sort((a, b) -> {
                int scoreA = scoreMoveAdvanced(state, a, depth);
                int scoreB = scoreMoveAdvanced(state, b, depth);
                return Integer.compare(scoreB, scoreA);
            });
        }
    }

    /**
     * Erweiterte Move-Bewertung mit Killer Moves und PV
     */
    public static int scoreMoveAdvanced(GameState state, Move move, int depth) {
        int score = scoreMove(state, move); // Basis-Score

        // PV Move gets second highest priority
        if (depth < pvLine.length && move.equals(pvLine[depth])) {
            score += 15000;
        }

        // Killer Move Bonus
        if (depth < killerMoves.length) {
            if (move.equals(killerMoves[depth][0])) {
                score += 9000; // Primary killer
            } else if (move.equals(killerMoves[depth][1])) {
                score += 8000; // Secondary killer
            }
        }

        return score;
    }

    public static boolean isGameOver(GameState state) {
        boolean blueGuardOnD7 = (state.blueGuard & GameState.bit(getIndex(6, 3))) != 0;
        boolean redGuardOnD1 = (state.redGuard & GameState.bit(getIndex(0, 3))) != 0;
        return state.redGuard == 0 || state.blueGuard == 0 || blueGuardOnD7 || redGuardOnD1;
    }

    /**
     * @apiNote this function is based on red's POV --> positive values are good for red | negative values are good for blue
     */
    public static int evaluate(GameState state, int depth) {
        boolean redWinsByCastle = state.redGuard == GameState.bit(BLUE_CASTLE_INDEX);
        boolean blueWinsByCastle = state.blueGuard == GameState.bit(RED_CASTLE_INDEX);

        int redScore = 0;
        int blueScore = 0;

        // If the done move took the guard or "rushed the castle" give it a huge favor
        // Include the depth to reward early wins and penalize early losses
        if (state.redGuard == 0 || blueWinsByCastle) return -10000 - depth;
        if (state.blueGuard == 0 || redWinsByCastle) return 10000 + depth;

        boolean redGuardInDanger = isGuardInDanger(state, true);
        boolean blueGuardInDanger = isGuardInDanger(state, false);

        // KORRIGIERT: Evaluation Bug Fix
        if (state.redToMove) {
            // Red ist am Zug
            if (redGuardInDanger && blueGuardInDanger) return 10000 + depth; // Red kann Blue's Guard nehmen
            if (redGuardInDanger && !blueGuardInDanger) return -10000 - depth; // Red verliert Guard
        } else {
            // Blue ist am Zug - HIER WAR DER FEHLER
            if (blueGuardInDanger && redGuardInDanger) return -10000 - depth; // Blue verliert Guard
            if (blueGuardInDanger && !redGuardInDanger) return 10000 + depth; // Blue kann Red's Guard nehmen
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

            // Bonus für unterstützende Türme
            int support = countSupportingTowers(guardIndex, true, state);
            redScore += support * 100;

            // Penalty wenn Wächter blockiert ist
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

            // Support und Blockade-Check
            int support = countSupportingTowers(guardIndex, false, state);
            blueScore += support * 100;

            if (isGuardBlocked(guardIndex, false, state)) {
                blueScore -= 200;
            }
        }

        // === VERBESSERTE ZENTRALE KONTROLLE ===
        for (int index : centralSquares) {
            long bit = GameState.bit(index);

            // Türme im Zentrum mit Höhenbonus
            if ((state.redTowers & bit) != 0) {
                redScore += 300 + (state.redStackHeights[index] * 50);
            }
            if ((state.blueTowers & bit) != 0) {
                blueScore += 300 + (state.blueStackHeights[index] * 50);
            }

            if ((state.redGuard & bit) != 0) redScore += 200;
            if ((state.blueGuard & bit) != 0) blueScore += 200;
        }

        // === VERBESSERTE MATERIAL-BEWERTUNG ===
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                int height = state.redStackHeights[i];
                redScore += height * 100; // Basis-Materialwert

                // Mobilitätsbonus
                int mobility = calculateActualMobility(i, height, state);
                redScore += mobility * 20;

                // Penalty für große immobile Stapel
                if (height >= 4 && mobility == 0) {
                    redScore -= 150;
                }

                // Bonus für Stapel nahe gegnerischem Wächter
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
        copy.redToMove = !checkRed;     // Invert to make the enemy move next
        long targetGuard = checkRed ? state.redGuard : state.blueGuard;

        for (Move m : MoveGenerator.generateAllMoves(copy)) {
            if (GameState.bit(m.to) == targetGuard) return true;
        }
        return false;
    }

    // === HILFSMETHODEN ===

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
        if (state == null) {
            return move.amountMoved; // Einfache Bewertung ohne Kontext
        }
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

    // === PHASE 2: KILLER MOVES & PRINCIPAL VARIATION METHODS ===

    /**
     * Speichere Killer Move
     */
    private static void storeKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length) return;

        // Prüfe ob bereits Primary Killer
        if (move.equals(killerMoves[depth][0])) return;

        // Schiebe Primary zu Secondary, neuer Move wird Primary
        killerMoves[depth][1] = killerMoves[depth][0];
        killerMoves[depth][0] = move;
    }

    /**
     * Prüfe ob Move ein Capture ist
     */
    private static boolean isCapture(Move move, GameState state) {
        if (state == null) return false;

        long toBit = GameState.bit(move.to);
        boolean isRed = state.redToMove;

        // Capture Guard oder Tower
        boolean capturesGuard = isRed
                ? (state.blueGuard & toBit) != 0
                : (state.redGuard & toBit) != 0;

        boolean capturesTower = isRed
                ? (state.blueTowers & toBit) != 0
                : (state.redTowers & toBit) != 0;

        return capturesGuard || capturesTower;
    }

    /**
     * Speichere Principal Variation
     */
    private static void storePVMove(Move move, int depth) {
        if (depth < pvLine.length) {
            pvLine[depth] = move;
        }
    }

    /**
     * Reset Killer Moves periodisch für Frische
     */
    public static void resetKillerMoves() {
        killerAge++;
        if (killerAge > 1000) { // Reset alle 1000 Aufrufe
            killerMoves = new Move[20][2];
            killerAge = 0;
        }
    }

    // === ÖFFENTLICHE SCHNITTSTELLEN ===

    /**
     * Öffentliche Schnittstelle für TimedMinimax um auf TT zuzugreifen
     */
    public static TTEntry getTranspositionEntry(long hash) {
        return transpositionTable.get(hash);
    }

    /**
     * Minimax mit Timeout-Check für TimedMinimax
     */
    public static int minimaxWithTimeout(GameState state, int depth, int alpha, int beta,
                                         boolean maximizingPlayer, java.util.function.BooleanSupplier timeoutCheck) {
        if (timeoutCheck.getAsBoolean()) {
            throw new RuntimeException("Timeout"); // Wird von TimedMinimax gefangen
        }

        // Rest wie normale minimax, aber mit Timeout-Checks
        long hash = state.hash();
        TTEntry entry = transpositionTable.get(hash);
        if (entry != null && entry.depth >= depth) {
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        }

        if (depth == 0 || isGameOver(state)) {
            return evaluate(state, depth);
        }

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        orderMovesAdvanced(moves, state, depth, entry);

        Move bestMove = null;
        int originalAlpha = alpha;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                if (timeoutCheck.getAsBoolean()) throw new RuntimeException("Timeout");

                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;
                int eval = minimaxWithTimeout(copy, depth - 1, alpha, beta, false, timeoutCheck);

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                    storePVMove(move, depth);
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            // Konsistente TT Flag Logik
            int flag;
            if (maxEval <= originalAlpha) {
                flag = TTEntry.UPPER_BOUND;
            } else if (maxEval >= beta) {
                flag = TTEntry.LOWER_BOUND;
            } else {
                flag = TTEntry.EXACT;
            }
            transpositionTable.put(hash, new TTEntry(maxEval, depth, flag, bestMove));

            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                if (timeoutCheck.getAsBoolean()) throw new RuntimeException("Timeout");

                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;
                int eval = minimaxWithTimeout(copy, depth - 1, alpha, beta, true, timeoutCheck);

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                    storePVMove(move, depth);
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            // Konsistente TT Flag Logik für minimizing player
            int flag;
            if (minEval <= originalAlpha) {
                flag = TTEntry.UPPER_BOUND;
            } else if (minEval >= beta) {
                flag = TTEntry.LOWER_BOUND;
            } else {
                flag = TTEntry.EXACT;
            }
            transpositionTable.put(hash, new TTEntry(minEval, depth, flag, bestMove));

            return minEval;
        }
    }




    // Add these methods to your existing Minimax.java class

    /**
     * Enhanced findBestMove that uses QuiescenceSearch
     * Add this as a new method to your Minimax class
     */
    public static Move findBestMoveWithQuiescence(GameState state, int depth) {
        System.out.println("=== Starting search with Quiescence (Depth " + depth + ") ===");

        // Reset statistics
        counter = 0;
        QuiescenceSearch.resetQuiescenceStats();

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        orderMovesAdvanced(moves, state, depth, getTranspositionEntry(state.hash()));

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (Move move : moves) {
            GameState copy = state.copy();
            copy.applyMove(move);

            // Use the enhanced minimax with quiescence
            int score = minimaxWithQuiescence(copy, depth - 1, Integer.MIN_VALUE,
                    Integer.MAX_VALUE, !isRed);

            System.out.println(move + " -> Score: " + score);

            if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                bestScore = score;
                bestMove = move;
            }
        }

        System.out.println("Regular nodes: " + counter);
        QuiescenceSearch.printQuiescenceStats();
        System.out.println("Best move: " + bestMove + " (Score: " + bestScore + ")");

        return bestMove;
    }

    /**
     * Enhanced minimax that integrates with your QuiescenceSearch
     * This replaces the depth == 0 case with quiescence search
     */
    private static int minimaxWithQuiescence(GameState state, int depth, int alpha, int beta,
                                             boolean maximizingPlayer) {
        // Check transposition table first
        long hash = state.hash();
        TTEntry entry = getTranspositionEntry(hash);
        if (entry != null && entry.depth >= depth) {
            if (entry.flag == TTEntry.EXACT) {
                return entry.score;
            } else if (entry.flag == TTEntry.LOWER_BOUND && entry.score >= beta) {
                return entry.score;
            } else if (entry.flag == TTEntry.UPPER_BOUND && entry.score <= alpha) {
                return entry.score;
            }
        }

        // Terminal conditions
        if (isGameOver(state)) {
            return evaluate(state, depth);
        }

        // NEW: Use quiescence search when depth <= 0
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

        // Regular alpha-beta search
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        orderMovesAdvanced(moves, state, depth, entry);

        Move bestMove = null;
        int originalAlpha = alpha;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;

                int eval = minimaxWithQuiescence(copy, depth - 1, alpha, beta, false);

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                    storePVMove(move, depth);
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            int flag = maxEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    maxEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            transpositionTable.put(hash, new TTEntry(maxEval, depth, flag, bestMove));

            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : moves) {
                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;

                int eval = minimaxWithQuiescence(copy, depth - 1, alpha, beta, true);

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                    storePVMove(move, depth);
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    if (!isCapture(move, state)) {
                        storeKillerMove(move, depth);
                    }
                    break;
                }
            }

            int flag = minEval <= originalAlpha ? TTEntry.UPPER_BOUND :
                    minEval >= beta ? TTEntry.LOWER_BOUND : TTEntry.EXACT;
            transpositionTable.put(hash, new TTEntry(minEval, depth, flag, bestMove));

            return minEval;
        }
    }
}
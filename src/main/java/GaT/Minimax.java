package GaT;

import GaT.Objects.GameState;
import GaT.Objects.Move;
import GaT.Objects.TTEntry;

import java.util.HashMap;
import java.util.List;
import java.util.function.BooleanSupplier;

import static GaT.Objects.GameState.getIndex;

public class Minimax {
    public static final int RED_CASTLE_INDEX = getIndex(6, 3); // D7
    public static final int BLUE_CASTLE_INDEX = getIndex(0, 3); // D1
    public static int counter = 0;

    private static final HashMap<Long, TTEntry> transpositionTable = new HashMap<>();

    final static int[] centralSquares = {
            GameState.getIndex(2, 3), // D3
            GameState.getIndex(3, 3), // D4
            GameState.getIndex(4, 3)  // D5
    };

    // === KILLER MOVES & PRINCIPAL VARIATION ===
    private static Move[][] killerMoves = new Move[20][2]; // [depth][primary/secondary]
    private static int killerAge = 0;
    private static Move[] pvLine = new Move[20];

    /**
     * LEGACY COMPATIBILITY METHODS - Keep your existing interface
     */

    /**
     * Original findBestMove - uses Alpha-Beta
     */
    public static Move findBestMove(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.ALPHA_BETA);
    }

    /**
     * Quiescence version
     */
    public static Move findBestMoveWithQuiescence(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.ALPHA_BETA_Q);
    }

    /**
     * NEW: PVS version
     */
    public static Move findBestMoveWithPVS(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.PVS);
    }

    /**
     * NEW: Ultimate version - PVS + Quiescence
     */
    public static Move findBestMoveUltimate(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.PVS_Q);
    }

    // === SEARCH STRATEGY CONFIGURATION ===
    public enum SearchStrategy {
        ALPHA_BETA,           // Classic Alpha-Beta
        ALPHA_BETA_Q,         // Alpha-Beta + Quiescence
        PVS,                  // Principal Variation Search
        PVS_Q                 // PVS + Quiescence (Ultimate)
    }

    /**
     * UNIFIED SEARCH INTERFACE - Clean strategy selection
     */
    public static Move findBestMoveWithStrategy(GameState state, int depth, SearchStrategy strategy) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        orderMovesAdvanced(moves, state, depth, getTranspositionEntry(state.hash()));

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        System.out.println("=== Starting " + strategy + " Search (Depth " + depth + ") ===");

        counter = 0;
        if (strategy == SearchStrategy.ALPHA_BETA_Q || strategy == SearchStrategy.PVS_Q) {
            QuiescenceSearch.resetQuiescenceStats();
        }

        for (Move move : moves) {
            GameState copy = state.copy();
            copy.applyMove(move);
            counter++;

            int score = searchWithStrategy(copy, depth - 1, Integer.MIN_VALUE,
                    Integer.MAX_VALUE, !isRed, strategy, true);

            System.out.println(move + " -> Score: " + score);

            if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                bestScore = score;
                bestMove = move;
            }
        }

        System.out.println("Search nodes: " + counter);
        if (strategy == SearchStrategy.ALPHA_BETA_Q || strategy == SearchStrategy.PVS_Q) {
            if (QuiescenceSearch.qNodes > 0) {
                System.out.println("Q-nodes: " + QuiescenceSearch.qNodes);
                System.out.println("Stand-pat rate: " + (100.0 * QuiescenceSearch.standPatCutoffs / QuiescenceSearch.qNodes) + "%");
            }
        }
        System.out.println("Best move: " + bestMove + " (Score: " + bestScore + ")");

        return bestMove;
    }

    /**
     * STRATEGY DISPATCHER - Routes to appropriate search method
     */
    private static int searchWithStrategy(GameState state, int depth, int alpha, int beta,
                                          boolean maximizingPlayer, SearchStrategy strategy, boolean isPVNode) {
        switch (strategy) {
            case ALPHA_BETA:
                return minimax(state, depth, alpha, beta, maximizingPlayer);

            case ALPHA_BETA_Q:
                return minimaxWithQuiescence(state, depth, alpha, beta, maximizingPlayer);

            case PVS:
                return PVSSearch.search(state, depth, alpha, beta, maximizingPlayer, isPVNode);

            case PVS_Q:
                return PVSSearch.searchWithQuiescence(state, depth, alpha, beta, maximizingPlayer, isPVNode);

            default:
                throw new IllegalArgumentException("Unknown search strategy: " + strategy);
        }
    }

    // === CORE SEARCH ALGORITHMS ===

    /**
     * Classic Alpha-Beta Minimax
     */
    private static int minimax(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        // Check transposition table
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
                GameState copy = state.copy();
                copy.applyMove(move);
                counter++;
                int eval = minimax(copy, depth - 1, alpha, beta, false);

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
                int eval = minimax(copy, depth - 1, alpha, beta, true);

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

    /**
     * Alpha-Beta with Quiescence Search integration
     */
    private static int minimaxWithQuiescence(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        // Check transposition table
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

        // Terminal conditions
        if (isGameOver(state)) {
            return evaluate(state, depth);
        }

        // QUIESCENCE INTEGRATION: Use quiescence search when depth <= 0
        if (depth <= 0) {
            return QuiescenceSearch.quiesce(state, alpha, beta, maximizingPlayer, 0);
        }

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

    // === TIMEOUT SUPPORT FOR INTEGRATION ===

    /**
     * Minimax with timeout support for TimedMinimax
     */
    public static int minimaxWithTimeout(GameState state, int depth, int alpha, int beta,
                                         boolean maximizingPlayer, BooleanSupplier timeoutCheck) {
        if (timeoutCheck.getAsBoolean()) {
            throw new RuntimeException("Timeout");
        }

        // Use the strategy-based search with timeout
        SearchStrategy strategy = SearchStrategy.ALPHA_BETA; // Default for compatibility
        return searchWithTimeoutSupport(state, depth, alpha, beta, maximizingPlayer, strategy, timeoutCheck, false);
    }

    /**
     * Enhanced timeout support with strategy selection
     */
    private static int searchWithTimeoutSupport(GameState state, int depth, int alpha, int beta,
                                                boolean maximizingPlayer, SearchStrategy strategy,
                                                BooleanSupplier timeoutCheck, boolean isPVNode) {
        if (timeoutCheck.getAsBoolean()) {
            throw new RuntimeException("Timeout");
        }

        // Set timeout checker for PVSSearch if using PVS
        if (strategy == SearchStrategy.PVS || strategy == SearchStrategy.PVS_Q) {
            PVSSearch.setTimeoutChecker(timeoutCheck);
        }

        // Delegate to appropriate search method
        return searchWithStrategy(state, depth, alpha, beta, maximizingPlayer, strategy, isPVNode);
    }

    // === MOVE ORDERING & HEURISTICS ===

    /**
     * Enhanced Move Ordering - same as before but cleaner
     */
    public static void orderMovesAdvanced(List<Move> moves, GameState state, int depth, TTEntry entry) {
        // 1. TT Move at first position
        if (entry != null && entry.bestMove != null) {
            for (int i = 0; i < moves.size(); i++) {
                if (moves.get(i).equals(entry.bestMove)) {
                    Move ttMove = moves.remove(i);
                    moves.add(0, ttMove);
                    break;
                }
            }
        }

        // 2. Sort remaining moves with comprehensive scoring
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
     * Advanced move scoring with all heuristics
     */
    public static int scoreMoveAdvanced(GameState state, Move move, int depth) {
        int score = scoreMove(state, move); // Base tactical score

        // PV Move bonus (second highest priority after TT move)
        if (depth < pvLine.length && move.equals(pvLine[depth])) {
            score += 15000;
        }

        // Killer Move bonuses
        if (depth < killerMoves.length) {
            if (move.equals(killerMoves[depth][0])) {
                score += 9000; // Primary killer
            } else if (move.equals(killerMoves[depth][1])) {
                score += 8000; // Secondary killer
            }
        }

        // Positional bonuses for non-tactical moves
        if (!isCapture(move, state)) {
            score += getPositionalBonus(move, state);
        }

        return score;
    }

    /**
     * Positional bonus calculation
     */
    private static int getPositionalBonus(Move move, GameState state) {
        int bonus = 0;

        // Central control bonus
        for (int centralSquare : centralSquares) {
            if (move.to == centralSquare) {
                bonus += 50;
                break;
            }
        }

        // Guard advancement bonus
        boolean isRed = state.redToMove;
        boolean isGuardMove = (move.amountMoved == 1) &&
                (((isRed && (state.redGuard & GameState.bit(move.from)) != 0)) ||
                        (!isRed && (state.blueGuard & GameState.bit(move.from)) != 0));

        if (isGuardMove) {
            int targetRank = GameState.rank(move.to);
            if (isRed && targetRank < 3) { // Moving towards blue castle
                bonus += (3 - targetRank) * 20;
            } else if (!isRed && targetRank > 3) { // Moving towards red castle
                bonus += (targetRank - 3) * 20;
            }
        }

        return bonus;
    }

    // === PUBLIC INTERFACE METHODS FOR INTEGRATION ===

    /**
     * Public access to transposition table for PVSSearch
     */
    public static void storeTranspositionEntry(long hash, TTEntry entry) {
        transpositionTable.put(hash, entry);
    }

    /**
     * Public access to killer moves for PVSSearch
     */
    public static void storeKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length) return;
        if (move.equals(killerMoves[depth][0])) return;
        killerMoves[depth][1] = killerMoves[depth][0];
        killerMoves[depth][0] = move;
    }

    /**
     * Public access to PV line for PVSSearch
     */
    public static void storePVMove(Move move, int depth) {
        if (depth < pvLine.length) {
            pvLine[depth] = move;
        }
    }

    /**
     * Reset killer moves periodically
     */
    public static void resetKillerMoves() {
        killerAge++;
        if (killerAge > 1000) {
            killerMoves = new Move[20][2];
            killerAge = 0;
        }
    }

    /**
     * Public access to transposition table
     */
    public static TTEntry getTranspositionEntry(long hash) {
        return transpositionTable.get(hash);
    }

    // === GAME LOGIC ===

    public static boolean isGameOver(GameState state) {
        boolean blueGuardOnD7 = (state.blueGuard & GameState.bit(getIndex(6, 3))) != 0;
        boolean redGuardOnD1 = (state.redGuard & GameState.bit(getIndex(0, 3))) != 0;
        return state.redGuard == 0 || state.blueGuard == 0 || blueGuardOnD7 || redGuardOnD1;
    }

    // === FIXED COMPLETE EVALUATION FUNCTION ===

    /**
     * COMPLETE FIXED EVALUATION FUNCTION
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

        // === ENHANCED GUARD SAFETY EVALUATION ===
// Replace the existing guard danger section with this:

        boolean redGuardInDanger = isGuardInDanger(state, true);
        boolean blueGuardInDanger = isGuardInDanger(state, false);
        boolean redGuardInMateNet = isGuardInMateNet(state, true);
        boolean blueGuardInMateNet = isGuardInMateNet(state, false);

// Check for immediate mate nets (highest priority)
        if (redGuardInMateNet) return -20000 - depth; // Red guard trapped = instant loss
        if (blueGuardInMateNet) return 20000 + depth; // Blue guard trapped = instant win

// Check for guard captures when both players can move
        if (state.redToMove) {
            // Red is moving
            if (redGuardInDanger && blueGuardInDanger) return 10000 + depth; // Red can take Blue's Guard
            if (redGuardInDanger && !blueGuardInDanger) return -10000 - depth; // Red loses Guard
        } else {
            // Blue is moving
            if (blueGuardInDanger && redGuardInDanger) return -10000 - depth; // Blue loses Guard
            if (blueGuardInDanger && !redGuardInDanger) return 10000 + depth; // Blue can take Red's Guard
        }

// Add comprehensive guard safety evaluation
        int guardSafetyScore = evaluateGuardSafety(state);
        redScore += guardSafetyScore; // This handles both positive and negative adjustments

        // === ENHANCED GUARD EVALUATION ===
        // Bonus for red guard being close to blue castle
        if (state.redGuard != 0) {
            int guardIndex = Long.numberOfTrailingZeros(state.redGuard);
            int guardRank = GameState.rank(guardIndex);
            int guardFile = GameState.file(guardIndex);

            int distanceToBlueCastleRank = Math.abs(guardRank - 0);  // red wants to reach rank 0
            int distanceToBlueCastleFile = Math.abs(guardFile - 3);  // D-file is column 3

            // FIXED: Reduced from 500 to 120
            int rankBonus = (6 - distanceToBlueCastleRank) * 120;   // moving down = good
            // FIXED: Reduced from 300 to 80
            int fileBonus = (3 - distanceToBlueCastleFile) * 80;    // closer to D-file = good

            redScore += rankBonus + fileBonus;

            // Bonus for supporting towers
            int support = countSupportingTowers(guardIndex, true, state);
            redScore += support * 100;

            // Penalty when guard is blocked
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

            // FIXED: Reduced from 500 to 120
            int rankBonus = (6 - distanceToRedCastleRank) * 120;   // moving up = good
            // FIXED: Reduced from 300 to 80
            int fileBonus = (3 - distanceToRedCastleFile) * 80;

            blueScore += rankBonus + fileBonus;

            // Support and blockade check
            int support = countSupportingTowers(guardIndex, false, state);
            blueScore += support * 100;

            if (isGuardBlocked(guardIndex, false, state)) {
                blueScore -= 200;
            }
        }

        // === ENHANCED CENTRAL CONTROL ===
        for (int index : centralSquares) {
            long bit = GameState.bit(index);

            // Towers in center with height bonus
            if ((state.redTowers & bit) != 0) {
                redScore += 300 + (state.redStackHeights[index] * 50);
            }
            if ((state.blueTowers & bit) != 0) {
                blueScore += 300 + (state.blueStackHeights[index] * 50);
            }

            if ((state.redGuard & bit) != 0) redScore += 200;
            if ((state.blueGuard & bit) != 0) blueScore += 200;
        }

        // === ENHANCED MATERIAL EVALUATION ===
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                int height = state.redStackHeights[i];
                // FIXED: Increased from 100 to 130
                redScore += height * 130; // Base material value

                // Mobility bonus
                int mobility = calculateActualMobility(i, height, state);
                redScore += mobility * 20;

                // Penalty for large immobile stacks
                if (height >= 4 && mobility == 0) {
                    redScore -= 150;
                }

                // Bonus for stacks near enemy guard
                if (state.blueGuard != 0) {
                    int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
                    int distance = manhattanDistance(i, blueGuardPos);
                    if (distance <= height) {
                        redScore += 80; // Threat potential
                    }
                }
            }

            if (state.blueStackHeights[i] > 0) {
                int height = state.blueStackHeights[i];
                // FIXED: Increased from 100 to 130
                blueScore += height * 130;

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

        // === ENDGAME ADJUSTMENTS ===
        int totalTowers = Long.bitCount(state.redTowers | state.blueTowers);
        if (totalTowers <= 6) {
            // In endgame guard progress is more important
            if (state.redGuard != 0) {
                int gRank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
                redScore += gRank * 200; // Extra bonus
            }
            if (state.blueGuard != 0) {
                int gRank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
                blueScore += (6 - gRank) * 200;
            }
        }

        // === TACTICAL PATTERN BONUSES ===
        int redTacticalBonus = detectForks(state, true) + detectPins(state, true) + detectDiscoveredAttacks(state, true);
        int blueTacticalBonus = detectForks(state, false) + detectPins(state, false) + detectDiscoveredAttacks(state, false);

        redScore += redTacticalBonus;
        blueScore += blueTacticalBonus;

// === ENHANCED GUARD SAFETY BONUSES ===
        int guardSafetyBonus = evaluateGuardSafety(state);
        redScore += guardSafetyBonus; // Already calculated from red's perspective

        return redScore - blueScore;
    }

    // === HELPER METHODS ===

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

    private static int countSupportingTowers(int guardPos, boolean isRed, GameState state) {
        long ownTowers = isRed ? state.redTowers : state.blueTowers;
        int count = 0;

        // Check 2-field radius
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
        // Safety check
        if (!GameState.isOnBoard(guardPos)) return false;

        int[] directions = {-1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE};
        int blockedCount = 0;
        int validDirections = 0;

        for (int dir : directions) {
            int target = guardPos + dir;
            validDirections++;

            // Board edge
            if (!GameState.isOnBoard(target)) {
                blockedCount++;
                continue;
            }

            // Check edge wrapping for horizontal movement
            if (Math.abs(dir) == 1 && GameState.rank(guardPos) != GameState.rank(target)) {
                blockedCount++;
                continue;
            }

            // Check if square is occupied
            if (((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard)
                    & GameState.bit(target)) != 0) {
                blockedCount++;
            }
        }

        // Guard is blocked if at least 3 of 4 directions are blocked
        return validDirections > 0 && blockedCount >= 3;
    }

    private static int calculateActualMobility(int index, int height, GameState state) {
        // Safety check
        if (height <= 0 || height > 7) return 0;
        if (!GameState.isOnBoard(index)) return 0;

        int mobility = 0;
        int[] directions = {-1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE};

        for (int dir : directions) {
            for (int dist = 1; dist <= height && dist <= 7; dist++) { // Extra Check for dist
                int target = index + dir * dist;

                if (!GameState.isOnBoard(target)) break;

                // Check edge wrapping for horizontal moves
                if (Math.abs(dir) == 1) { // Horizontal movement
                    // Check all intermediate steps for edge wrapping
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

                // Check if move would be possible
                long allPieces = state.redTowers | state.blueTowers | state.redGuard | state.blueGuard;

                // Blocked by other piece on the way?
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

                    // Bonus if target square is empty
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
            return move.amountMoved; // Simple evaluation without context
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

    // === TACTICAL PATTERN RECOGNITION ===
// Add these methods to your Minimax.java class

    /**
     * Detect fork patterns: one piece attacking multiple targets
     */
    private static int detectForks(GameState state, boolean isRed) {
        int forkBonus = 0;
        long ownTowers = isRed ? state.redTowers : state.blueTowers;
        int[] ownHeights = isRed ? state.redStackHeights : state.blueStackHeights;

        // Check each tower for fork potential
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((ownTowers & GameState.bit(i)) == 0) continue;

            int height = ownHeights[i];
            if (height == 0) continue;

            int targetsAttacked = 0;
            int[] directions = {-1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE};

            // Check all possible moves for this tower
            for (int dir : directions) {
                for (int dist = 1; dist <= height; dist++) {
                    int target = i + dir * dist;
                    if (!GameState.isOnBoard(target)) break;

                    // Check edge wrapping for horizontal moves
                    if (Math.abs(dir) == 1 && GameState.rank(i) != GameState.rank(target)) {
                        break;
                    }

                    // Check if path is clear
                    boolean pathClear = true;
                    for (int step = 1; step < dist; step++) {
                        int intermediate = i + dir * step;
                        if (isOccupiedByAnyPiece(intermediate, state)) {
                            pathClear = false;
                            break;
                        }
                    }

                    if (!pathClear) break;

                    // Check if this square contains an enemy piece we can capture
                    if (isEnemyPieceAt(target, isRed, state)) {
                        targetsAttacked++;
                    }
                }
            }

            // Bonus for attacking multiple targets (fork)
            if (targetsAttacked >= 2) {
                forkBonus += 250 * (targetsAttacked - 1);
            }
        }

        return forkBonus;
    }

    /**
     * Detect pieces that are pinned (cannot move without exposing more valuable piece)
     */
    private static int detectPins(GameState state, boolean isRed) {
        int pinBonus = 0;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;

        // Look for pieces that could be pinning enemy pieces
        long ownTowers = isRed ? state.redTowers : state.blueTowers;
        int[] ownHeights = isRed ? state.redStackHeights : state.blueStackHeights;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((ownTowers & GameState.bit(i)) == 0) continue;

            int height = ownHeights[i];
            int[] directions = {-1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE};

            for (int dir : directions) {
                // Look for enemy piece + valuable target in line
                int enemyPos = -1;
                int valuablePos = -1;

                for (int dist = 1; dist <= height; dist++) {
                    int pos = i + dir * dist;
                    if (!GameState.isOnBoard(pos)) break;

                    if (Math.abs(dir) == 1 && GameState.rank(i) != GameState.rank(pos)) {
                        break;
                    }

                    if (isEnemyPieceAt(pos, isRed, state)) {
                        if (enemyPos == -1) {
                            enemyPos = pos;
                        } else {
                            // Check if second piece is more valuable (guard or big tower)
                            if ((enemyGuard & GameState.bit(pos)) != 0) {
                                valuablePos = pos; // Guard is most valuable
                                break;
                            }
                            // Check for large tower
                            int[] enemyHeights = isRed ? state.blueStackHeights : state.redStackHeights;
                            if (enemyHeights[pos] >= 3) {
                                valuablePos = pos;
                                break;
                            }
                        }
                    }
                }

                // If we found a pin pattern, add bonus
                if (enemyPos != -1 && valuablePos != -1) {
                    pinBonus += 180;
                    if ((enemyGuard & GameState.bit(valuablePos)) != 0) {
                        pinBonus += 220; // Extra bonus for pinning to guard
                    }
                }
            }
        }

        return pinBonus;
    }

    /**
     * Detect discovered attacks: moving one piece reveals attack from another
     */
    private static int detectDiscoveredAttacks(GameState state, boolean isRed) {
        int discoveredBonus = 0;
        long ownTowers = isRed ? state.redTowers : state.blueTowers;
        int[] ownHeights = isRed ? state.redStackHeights : state.blueStackHeights;
        long enemyGuard = isRed ? state.blueGuard : state.redGuard;

        // Check each of our towers
        for (int backPiece = 0; backPiece < GameState.NUM_SQUARES; backPiece++) {
            if ((ownTowers & GameState.bit(backPiece)) == 0) continue;

            int backHeight = ownHeights[backPiece];
            if (backHeight == 0) continue;

            int[] directions = {-1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE};

            for (int dir : directions) {
                // Look for own piece blocking the line, then enemy piece behind it
                int blockingPiece = -1;
                int enemyTarget = -1;

                for (int dist = 1; dist <= backHeight; dist++) {
                    int pos = backPiece + dir * dist;
                    if (!GameState.isOnBoard(pos)) break;

                    if (Math.abs(dir) == 1 && GameState.rank(backPiece) != GameState.rank(pos)) {
                        break;
                    }

                    if (isOwnPieceAt(pos, isRed, state)) {
                        if (blockingPiece == -1) {
                            blockingPiece = pos;
                            // Continue looking beyond this piece
                        }
                    } else if (isEnemyPieceAt(pos, isRed, state)) {
                        if (blockingPiece != -1) {
                            // Found potential discovered attack
                            enemyTarget = pos;
                            break;
                        }
                    }
                }

                // If we found a discovered attack pattern
                if (blockingPiece != -1 && enemyTarget != -1) {
                    discoveredBonus += 150;
                    // Extra bonus if targeting enemy guard
                    if ((enemyGuard & GameState.bit(enemyTarget)) != 0) {
                        discoveredBonus += 200;
                    }
                }
            }
        }

        return discoveredBonus;
    }

// === HELPER METHODS ===

    private static boolean isOccupiedByAnyPiece(int index, GameState state) {
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard)
                & GameState.bit(index)) != 0;
    }

    private static boolean isEnemyPieceAt(int index, boolean isRed, GameState state) {
        long bit = GameState.bit(index);
        if (isRed) {
            return (state.blueGuard & bit) != 0 || (state.blueTowers & bit) != 0;
        } else {
            return (state.redGuard & bit) != 0 || (state.redTowers & bit) != 0;
        }
    }

    private static boolean isOwnPieceAt(int index, boolean isRed, GameState state) {
        long bit = GameState.bit(index);
        if (isRed) {
            return (state.redGuard & bit) != 0 || (state.redTowers & bit) != 0;
        } else {
            return (state.blueGuard & bit) != 0 || (state.blueTowers & bit) != 0;
        }
    }





    // === ENHANCED GUARD SAFETY ===
// Add these methods to your Minimax.java class

    /**
     * Check if guard is in a mate net (will be captured regardless of where it moves)
     */
    private static boolean isGuardInMateNet(GameState state, boolean checkRed) {
        long guardBit = checkRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false; // No guard to check

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int[] directions = {-1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE};

        // Try all possible guard moves
        for (int dir : directions) {
            int newPos = guardPos + dir;
            if (!GameState.isOnBoard(newPos)) continue;

            // Check edge wrapping for horizontal moves
            if (Math.abs(dir) == 1 && GameState.rank(guardPos) != GameState.rank(newPos)) {
                continue;
            }

            // Create test state with guard moved
            GameState testState = state.copy();

            // Clear old guard position
            if (checkRed) {
                testState.redGuard &= ~GameState.bit(guardPos);
            } else {
                testState.blueGuard &= ~GameState.bit(guardPos);
            }

            // Check if destination is occupied by own piece
            long allOwnPieces = checkRed ?
                    (testState.redGuard | testState.redTowers) :
                    (testState.blueGuard | testState.blueTowers);

            if ((allOwnPieces & GameState.bit(newPos)) != 0) {
                continue; // Can't move to square occupied by own piece
            }

            // Clear any enemy piece at destination (capture)
            if (checkRed) {
                testState.blueGuard &= ~GameState.bit(newPos);
                testState.blueTowers &= ~GameState.bit(newPos);
                testState.blueStackHeights[newPos] = 0;
            } else {
                testState.redGuard &= ~GameState.bit(newPos);
                testState.redTowers &= ~GameState.bit(newPos);
                testState.redStackHeights[newPos] = 0;
            }

            // Set guard at new position
            if (checkRed) {
                testState.redGuard |= GameState.bit(newPos);
            } else {
                testState.blueGuard |= GameState.bit(newPos);
            }

            // Check if guard is still in danger after this move
            if (!isGuardInDanger(testState, checkRed)) {
                return false; // Found escape square
            }
        }

        return true; // No escape = mate net
    }

    /**
     * Enhanced guard safety evaluation with multiple threat levels
     */
    private static int evaluateGuardSafety(GameState state) {
        int safetyScore = 0;

        // Check red guard safety
        if (state.redGuard != 0) {
            if (isGuardInMateNet(state, true)) {
                safetyScore -= 15000; // Red guard in mate net (very bad)
            } else if (isGuardInDanger(state, true)) {
                safetyScore -= 5000; // Red guard just in danger
            } else {
                // Check if guard has multiple escape squares (safety bonus)
                int escapeSquares = countGuardEscapeSquares(state, true);
                if (escapeSquares >= 3) {
                    safetyScore += 100; // Bonus for having many escape options
                }
            }
        }

        // Check blue guard safety
        if (state.blueGuard != 0) {
            if (isGuardInMateNet(state, false)) {
                safetyScore += 15000; // Blue guard in mate net (good for us)
            } else if (isGuardInDanger(state, false)) {
                safetyScore += 5000; // Blue guard just in danger
            } else {
                // Check if enemy guard has multiple escape squares
                int escapeSquares = countGuardEscapeSquares(state, false);
                if (escapeSquares >= 3) {
                    safetyScore -= 100; // Penalty for enemy having many escape options
                }
            }
        }

        return safetyScore;
    }

    /**
     * Count how many safe squares the guard can move to
     */
    private static int countGuardEscapeSquares(GameState state, boolean checkRed) {
        long guardBit = checkRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return 0;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int[] directions = {-1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE};
        int escapeCount = 0;

        for (int dir : directions) {
            int newPos = guardPos + dir;
            if (!GameState.isOnBoard(newPos)) continue;

            // Check edge wrapping
            if (Math.abs(dir) == 1 && GameState.rank(guardPos) != GameState.rank(newPos)) {
                continue;
            }

            // Check if square is accessible
            long allOwnPieces = checkRed ?
                    (state.redGuard | state.redTowers) :
                    (state.blueGuard | state.blueTowers);

            if ((allOwnPieces & GameState.bit(newPos)) != 0) {
                continue; // Blocked by own piece
            }

            // Simulate move and check if still safe
            GameState testState = state.copy();

            // Move guard to new position
            if (checkRed) {
                testState.redGuard &= ~GameState.bit(guardPos);
                testState.redGuard |= GameState.bit(newPos);
            } else {
                testState.blueGuard &= ~GameState.bit(guardPos);
                testState.blueGuard |= GameState.bit(newPos);
            }

            // Clear any captured piece
            if (checkRed) {
                testState.blueGuard &= ~GameState.bit(newPos);
                testState.blueTowers &= ~GameState.bit(newPos);
                testState.blueStackHeights[newPos] = 0;
            } else {
                testState.redGuard &= ~GameState.bit(newPos);
                testState.redTowers &= ~GameState.bit(newPos);
                testState.redStackHeights[newPos] = 0;
            }

            // Check if guard would be safe after moving here
            if (!isGuardInDanger(testState, checkRed)) {
                escapeCount++;
            }
        }

        return escapeCount;
    }

    /**
     * Detect if guard is under multiple threats (more dangerous than single threat)
     */
    private static boolean isGuardUnderMultipleThreats(GameState state, boolean checkRed) {
        GameState copy = state.copy();
        copy.redToMove = !checkRed; // Enemy to move
        long targetGuard = checkRed ? state.redGuard : state.blueGuard;

        if (targetGuard == 0) return false;

        int threatCount = 0;

        // Count how many enemy pieces can attack the guard
        for (Move move : MoveGenerator.generateAllMoves(copy)) {
            if (GameState.bit(move.to) == targetGuard) {
                threatCount++;
                if (threatCount >= 2) {
                    return true; // Multiple threats detected
                }
            }
        }

        return false;
    }




}
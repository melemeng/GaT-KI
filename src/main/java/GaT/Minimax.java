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

    // === BALANCED EVALUATION CONSTANTS ===
    private static final int GUARD_CAPTURE_SCORE = 1500;      // Reduced from 3000
    private static final int MATE_NET_SCORE = 2000;           // Reduced from 5000
    private static final int CASTLE_REACH_SCORE = 2500;       // Reduced from 4000
    private static final int GUARD_DANGER_PENALTY = 600;      // Reduced from 800
    private static final int MATERIAL_VALUE = 130;
    private static final int MOBILITY_BONUS = 15;
    private static final int CENTRAL_CONTROL_BONUS = 25;
    private static final int GUARD_ADVANCEMENT_BONUS = 40;

    // Graduated guard safety values
    private static final int GUARD_SAFE_CASTLE = 500;
    private static final int GUARD_NEAR_CASTLE = 250;

    // === KILLER MOVES & PRINCIPAL VARIATION ===
    private static Move[][] killerMoves = new Move[20][2];
    private static int killerAge = 0;
    private static Move[] pvLine = new Move[20];

    // === TIME MANAGEMENT INTEGRATION ===
    private static long remainingTimeMs = 180000; // Default 3 minutes

    /**
     * LEGACY COMPATIBILITY METHODS
     */
    public static Move findBestMove(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.ALPHA_BETA);
    }

    public static Move findBestMoveWithQuiescence(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.ALPHA_BETA_Q);
    }

    public static Move findBestMoveWithPVS(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.PVS);
    }

    public static Move findBestMoveUltimate(GameState state, int depth) {
        return findBestMoveWithStrategy(state, depth, SearchStrategy.PVS_Q);
    }

    // === SEARCH STRATEGY CONFIGURATION ===
    public enum SearchStrategy {
        ALPHA_BETA,
        ALPHA_BETA_Q,
        PVS,
        PVS_Q
    }

    /**
     * Time management integration
     */
    public static void setRemainingTime(long timeMs) {
        remainingTimeMs = timeMs;
    }

    /**
     * UNIFIED SEARCH INTERFACE - Clean strategy selection
     */
    public static Move findBestMoveWithStrategy(GameState state, int depth, SearchStrategy strategy) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        // Use consistent advanced ordering for all situations
        orderMovesAdvanced(moves, state, depth, getTranspositionEntry(state.hash()));

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        System.out.println("=== Starting " + strategy + " Search (Depth " + depth + ") ===");

        counter = 0;
        if (strategy == SearchStrategy.ALPHA_BETA_Q || strategy == SearchStrategy.PVS_Q) {
            QuiescenceSearch.setRemainingTime(remainingTimeMs); // CRITICAL: Sync time with QuiescenceSearch
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
    public static int minimaxWithTimeout(GameState state, int depth, int alpha, int beta,
                                         boolean maximizingPlayer, BooleanSupplier timeoutCheck) {
        if (timeoutCheck.getAsBoolean()) {
            throw new RuntimeException("Timeout");
        }

        SearchStrategy strategy = SearchStrategy.ALPHA_BETA;
        return searchWithTimeoutSupport(state, depth, alpha, beta, maximizingPlayer, strategy, timeoutCheck, false);
    }

    private static int searchWithTimeoutSupport(GameState state, int depth, int alpha, int beta,
                                                boolean maximizingPlayer, SearchStrategy strategy,
                                                BooleanSupplier timeoutCheck, boolean isPVNode) {
        if (timeoutCheck.getAsBoolean()) {
            throw new RuntimeException("Timeout");
        }

        if (strategy == SearchStrategy.PVS || strategy == SearchStrategy.PVS_Q) {
            PVSSearch.setTimeoutChecker(timeoutCheck);
        }

        return searchWithStrategy(state, depth, alpha, beta, maximizingPlayer, strategy, isPVNode);
    }

    // === MOVE ORDERING & HEURISTICS ===
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
     * SIMPLIFIED Advanced move scoring - much faster and more balanced
     */
    public static int scoreMoveAdvanced(GameState state, Move move, int depth) {
        int score = scoreMove(state, move); // Base tactical score

        // Add guard danger awareness without expensive checks
        if (isGuardMove(move, state)) {
            boolean guardInDanger = isGuardInDangerFast(state, state.redToMove);
            if (guardInDanger) {
                score += 1500; // Moderate bonus for guard moves when in danger
            }
        }

        // PV Move bonus (reduced)
        if (depth < pvLine.length && move.equals(pvLine[depth])) {
            score += 5000; // Reduced from 15000
        }

        // Killer Move bonuses (reduced)
        if (depth < killerMoves.length) {
            if (move.equals(killerMoves[depth][0])) {
                score += 3000; // Reduced from 9000
            } else if (move.equals(killerMoves[depth][1])) {
                score += 2000; // Reduced from 8000
            }
        }

        // Simple positional bonus
        if (!isCapture(move, state)) {
            score += getPositionalBonus(move, state);
        }

        return score;
    }

    /**
     * Check if move is a guard move
     */
    private static boolean isGuardMove(Move move, GameState state) {
        boolean isRed = state.redToMove;
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        return guardBit != 0 && move.from == Long.numberOfTrailingZeros(guardBit);
    }

    /**
     * FAST guard danger detection - much more efficient
     */
    private static boolean isGuardInDangerFast(GameState state, boolean checkRed) {
        long guardBit = checkRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);

        // Quick danger check - only check immediate threats
        return isPositionUnderAttack(state, guardPos, !checkRed);
    }

    /**
     * Fast attack detection
     */
    private static boolean isPositionUnderAttack(GameState state, int pos, boolean byRed) {
        long attackers = byRed ? (state.redTowers | state.redGuard) : (state.blueTowers | state.blueGuard);

        for (int i = 0; i < 64; i++) {
            if ((attackers & GameState.bit(i)) != 0) {
                int height = byRed ? state.redStackHeights[i] : state.blueStackHeights[i];
                if (height == 0 && (byRed ? state.redGuard : state.blueGuard) != GameState.bit(i)) continue;

                // Check if this piece can attack the position
                if (canPieceAttackPosition(i, pos, height)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Fast piece attack check
     */
    private static boolean canPieceAttackPosition(int from, int to, int height) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        // Guard can move 1 square in any direction
        if (height <= 1) {
            return rankDiff <= 1 && fileDiff <= 1 && (rankDiff + fileDiff) == 1;
        }

        // Tower can move along rank/file up to its height
        boolean sameRank = rankDiff == 0;
        boolean sameFile = fileDiff == 0;
        int distance = Math.max(rankDiff, fileDiff);

        return (sameRank || sameFile) && distance <= height;
    }

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
                        ((!isRed) && (state.blueGuard & GameState.bit(move.from)) != 0));

        if (isGuardMove) {
            int targetRank = GameState.rank(move.to);
            if (isRed && targetRank < 3) {
                bonus += (3 - targetRank) * 20;
            } else if (!isRed && targetRank > 3) {
                bonus += (targetRank - 3) * 20;
            }
        }

        return bonus;
    }

    // === PUBLIC INTERFACE METHODS ===
    public static void storeTranspositionEntry(long hash, TTEntry entry) {
        transpositionTable.put(hash, entry);
    }

    public static void storeKillerMove(Move move, int depth) {
        if (depth >= killerMoves.length) return;
        if (move.equals(killerMoves[depth][0])) return;
        killerMoves[depth][1] = killerMoves[depth][0];
        killerMoves[depth][0] = move;
    }

    public static void storePVMove(Move move, int depth) {
        if (depth < pvLine.length) {
            pvLine[depth] = move;
        }
    }

    public static void resetKillerMoves() {
        killerAge++;
        if (killerAge > 1000) {
            killerMoves = new Move[20][2];
            killerAge = 0;
        }
    }

    public static TTEntry getTranspositionEntry(long hash) {
        return transpositionTable.get(hash);
    }

    // === GAME LOGIC ===
    public static boolean isGameOver(GameState state) {
        boolean blueGuardOnD7 = (state.blueGuard & GameState.bit(getIndex(6, 3))) != 0;
        boolean redGuardOnD1 = (state.redGuard & GameState.bit(getIndex(0, 3))) != 0;
        return state.redGuard == 0 || state.blueGuard == 0 || blueGuardOnD7 || redGuardOnD1;
    }

    // === TIME-AWARE EVALUATION FUNCTION ===
    public static int evaluate(GameState state, int depth) {
        // Quick evaluation in time pressure
        if (remainingTimeMs < 10000) { // Less than 10 seconds
            return evaluateQuick(state, depth);
        }

        // Full evaluation when time allows
        return evaluateFull(state, depth);
    }

    /**
     * Fast evaluation for time pressure
     */
    private static int evaluateQuick(GameState state, int depth) {
        // Terminal positions
        boolean redWinsByCastle = state.redGuard == GameState.bit(BLUE_CASTLE_INDEX);
        boolean blueWinsByCastle = state.blueGuard == GameState.bit(RED_CASTLE_INDEX);

        if (state.redGuard == 0 || blueWinsByCastle) return -CASTLE_REACH_SCORE - depth;
        if (state.blueGuard == 0 || redWinsByCastle) return CASTLE_REACH_SCORE + depth;

        int eval = 0;

        // Quick material count
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            eval += (state.redStackHeights[i] - state.blueStackHeights[i]) * MATERIAL_VALUE;
        }

        // Quick guard advancement
        if (state.redGuard != 0) {
            int redRank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
            eval += (6 - redRank) * 100;
        }
        if (state.blueGuard != 0) {
            int blueRank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
            eval -= blueRank * 100;
        }

        // Quick guard safety
        if (isGuardInDangerFast(state, true)) eval -= 800;
        if (isGuardInDangerFast(state, false)) eval += 800;

        return eval;
    }

    /**
     * Full evaluation when time allows
     */
    private static int evaluateFull(GameState state, int depth) {
        // Quick terminal checks
        boolean redWinsByCastle = state.redGuard == GameState.bit(BLUE_CASTLE_INDEX);
        boolean blueWinsByCastle = state.blueGuard == GameState.bit(RED_CASTLE_INDEX);

        // Terminal positions (actual wins)
        if (state.redGuard == 0 || blueWinsByCastle) return -CASTLE_REACH_SCORE - depth;
        if (state.blueGuard == 0 || redWinsByCastle) return CASTLE_REACH_SCORE + depth;

        int evaluation = 0;

        // === GUARD SAFETY EVALUATION ===
        evaluation += evaluateGuardSafety(state);

        // === MATERIAL EVALUATION ===
        evaluation += evaluateMaterial(state);

        // === POSITIONAL EVALUATION ===
        evaluation += evaluatePositional(state);

        // === TEMPO BONUS ===
        if (state.redToMove) {
            evaluation += 30; // Small bonus for having the move
        } else {
            evaluation -= 30;
        }

        // === ENDGAME EVALUATION ===
        if (isEndgame(state)) {
            evaluation += evaluateEndgame(state);
        }

        return evaluation;
    }

    /**
     * Simplified guard safety evaluation
     */
    private static int evaluateGuardSafety(GameState state) {
        int safetyScore = 0;

        boolean redInDanger = isGuardInDangerFast(state, true);
        boolean blueInDanger = isGuardInDangerFast(state, false);

        if (redInDanger) safetyScore -= GUARD_DANGER_PENALTY;
        if (blueInDanger) safetyScore += GUARD_DANGER_PENALTY;

        return safetyScore;
    }

    /**
     * Material evaluation
     */
    private static int evaluateMaterial(GameState state) {
        int materialScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int redHeight = state.redStackHeights[i];
            int blueHeight = state.blueStackHeights[i];

            // Basic material value
            materialScore += (redHeight - blueHeight) * MATERIAL_VALUE;

            // Mobility bonus for tall towers
            if (redHeight > 1) {
                materialScore += redHeight * 10;
            }
            if (blueHeight > 1) {
                materialScore -= blueHeight * 10;
            }
        }

        return materialScore;
    }

    /**
     * Positional evaluation
     */
    private static int evaluatePositional(GameState state) {
        int positionalScore = 0;

        // Guard advancement
        positionalScore += evaluateGuardAdvancement(state);

        // Central control
        positionalScore += evaluateCentralControl(state);

        return positionalScore;
    }

    /**
     * Evaluate guard advancement
     */
    private static int evaluateGuardAdvancement(GameState state) {
        int advancementScore = 0;

        // Red guard advancement
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            int redRank = GameState.rank(redGuardPos);
            int distanceToTarget = redRank;

            advancementScore += (6 - distanceToTarget) * GUARD_ADVANCEMENT_BONUS;

            int redFile = GameState.file(redGuardPos);
            int fileDistance = Math.abs(redFile - 3);
            advancementScore += (3 - fileDistance) * 20;
        }

        // Blue guard advancement
        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int blueRank = GameState.rank(blueGuardPos);
            int distanceToTarget = 6 - blueRank;

            advancementScore -= (6 - distanceToTarget) * GUARD_ADVANCEMENT_BONUS;

            int blueFile = GameState.file(blueGuardPos);
            int fileDistance = Math.abs(blueFile - 3);
            advancementScore -= (3 - fileDistance) * 20;
        }

        return advancementScore;
    }

    /**
     * Evaluate central control
     */
    private static int evaluateCentralControl(GameState state) {
        int centralScore = 0;

        for (int square : centralSquares) {
            long redBit = state.redTowers | state.redGuard;
            long blueBit = state.blueTowers | state.blueGuard;

            if ((redBit & GameState.bit(square)) != 0) {
                centralScore += CENTRAL_CONTROL_BONUS;
                int height = state.redStackHeights[square];
                centralScore += height * 10;
            }

            if ((blueBit & GameState.bit(square)) != 0) {
                centralScore -= CENTRAL_CONTROL_BONUS;
                int height = state.blueStackHeights[square];
                centralScore -= height * 10;
            }
        }

        return centralScore;
    }

    // === HELPER METHODS ===
    private static int manhattanDistance(int index1, int index2) {
        int rank1 = GameState.rank(index1);
        int file1 = GameState.file(index1);
        int rank2 = GameState.rank(index2);
        int file2 = GameState.file(index2);

        return Math.abs(rank1 - rank2) + Math.abs(file1 - file2);
    }

    public static int scoreMove(GameState state, Move move) {
        if (state == null) {
            return move.amountMoved;
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
        if (capturesGuard) score += GUARD_CAPTURE_SCORE;
        if (capturesTower) score += 500 * (isRed ? state.blueStackHeights[move.to] : state.redStackHeights[move.to]);
        if (stacksOnOwn) score += 10;
        score += move.amountMoved;

        return score;
    }

    public static boolean isCapture(Move move, GameState state) {
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

    // === LEGACY COMPATIBILITY METHODS ===
    public static boolean isGuardInDangerImproved(GameState state, boolean checkRed) {
        return isGuardInDangerFast(state, checkRed);
    }

    private static boolean isEndgame(GameState state) {
        int totalPieces = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalPieces += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return totalPieces <= 8;
    }

    private static int evaluateEndgame(GameState state) {
        int endgameScore = 0;

        // Guard advancement is crucial in endgame
        if (state.redGuard != 0) {
            int redRank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
            endgameScore += (6 - redRank) * 100;
        }

        if (state.blueGuard != 0) {
            int blueRank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
            endgameScore -= blueRank * 100;
        }

        return endgameScore;
    }
}
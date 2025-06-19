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

    // === IMPROVED EVALUATION CONSTANTS (BALANCED) ===
    private static final int GUARD_CAPTURE_SCORE = 3000;      // Instead of 10000
    private static final int MATE_NET_SCORE = 5000;           // Instead of 20000
    private static final int GUARD_DANGER_PENALTY = 800;      // Graduated response
    private static final int CASTLE_REACH_SCORE = 4000;       // Winning but not extreme
    private static final int MATERIAL_VALUE = 130;
    private static final int MOBILITY_BONUS = 15;
    private static final int CENTRAL_CONTROL_BONUS = 25;
    private static final int GUARD_ADVANCEMENT_BONUS = 40;

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
                        ((!isRed) && (state.blueGuard & GameState.bit(move.from)) != 0));

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

    // === IMPROVED EVALUATION FUNCTION ===

    /**
     * IMPROVED EVALUATION FUNCTION with balanced scores and better tactical awareness
     * @apiNote this function is based on red's POV --> positive values are good for red | negative values are good for blue
     */
    public static int evaluate(GameState state, int depth) {
        // Quick terminal checks
        boolean redWinsByCastle = state.redGuard == GameState.bit(BLUE_CASTLE_INDEX);
        boolean blueWinsByCastle = state.blueGuard == GameState.bit(RED_CASTLE_INDEX);

        // Terminal positions (actual wins)
        if (state.redGuard == 0 || blueWinsByCastle) return -CASTLE_REACH_SCORE - depth;
        if (state.blueGuard == 0 || redWinsByCastle) return CASTLE_REACH_SCORE + depth;

        int evaluation = 0;

        // === IMPROVED GUARD SAFETY EVALUATION ===
        evaluation += evaluateGuardSafetyImproved(state, depth);

        // === MATERIAL EVALUATION ===
        evaluation += evaluateMaterial(state);

        // === POSITIONAL EVALUATION ===
        evaluation += evaluatePositional(state);

        // === TACTICAL EVALUATION ===
        evaluation += evaluateTacticalThreats(state);

        // === ENDGAME EVALUATION ===
        if (isEndgame(state)) {
            evaluation += evaluateEndgame(state);
        }

        return evaluation;
    }

    /**
     * IMPROVED guard safety with graduated penalties instead of extremes
     */
    private static int evaluateGuardSafetyImproved(GameState state, int depth) {
        int safetyScore = 0;

        boolean redInDanger = isGuardInDangerImproved(state, true);
        boolean blueInDanger = isGuardInDangerImproved(state, false);
        boolean redInMateNet = isGuardInMateNetImproved(state, true);
        boolean blueInMateNet = isGuardInMateNetImproved(state, false);

        // Mate net detection (serious but not extreme)
        if (redInMateNet) {
            safetyScore -= MATE_NET_SCORE + depth;
        }
        if (blueInMateNet) {
            safetyScore += MATE_NET_SCORE + depth;
        }

        // If not in mate net, handle regular danger
        if (!redInMateNet && !blueInMateNet) {
            // Use graduated danger assessment
            safetyScore += evaluateGuardDangerLevel(state, redInDanger, blueInDanger);
        }

        return safetyScore;
    }

    /**
     * IMPROVED GUARD DANGER DETECTION with comprehensive threat analysis
     */
    private static boolean isGuardInDangerImproved(GameState state, boolean checkRed) {
        long guardBit = checkRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);

        // Create enemy move state
        GameState enemyState = state.copy();
        enemyState.redToMove = !checkRed;

        // Generate ALL enemy moves (not just captures)
        List<Move> enemyMoves = MoveGenerator.generateAllMoves(enemyState);

        // Check each enemy move for guard attacks
        for (Move move : enemyMoves) {
            if (moveAttacksGuard(move, guardPos, enemyState)) {
                // Verify the attack is actually legal
                if (isAttackLegal(move, guardPos, enemyState)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if a move attacks the guard position
     */
    private static boolean moveAttacksGuard(Move move, int guardPos, GameState state) {
        // Direct capture of guard
        if (move.to == guardPos) {
            return true;
        }

        // Check for discovery attacks
        if (hasDiscoveryAttackOnGuard(move, guardPos, state)) {
            return true;
        }

        // Check for tower range attacks after move
        if (hasTowerRangeAttackOnGuard(move, guardPos, state)) {
            return true;
        }

        return false;
    }

    /**
     * Verify that an attack is actually legal (no blocking pieces)
     */
    private static boolean isAttackLegal(Move move, int guardPos, GameState state) {
        // Apply the move temporarily
        GameState tempState = state.copy();
        tempState.applyMove(move);

        // Check if the attacking piece can actually reach the guard
        if (move.to == guardPos) {
            return true; // Direct capture is always legal if move is legal
        }

        // For tower range attacks, check path is clear
        return isPathClearToGuard(move.to, guardPos, tempState, move.amountMoved);
    }

    /**
     * Check for discovery attacks (moving piece reveals attack from behind)
     */
    private static boolean hasDiscoveryAttackOnGuard(Move move, int guardPos, GameState state) {
        // Check if moving piece was blocking an attack line
        int fromPos = move.from;

        // Look for towers behind the moving piece that could attack guard
        boolean isRed = state.redToMove;
        long enemyTowers = isRed ? state.redTowers : state.blueTowers;

        // Iterate through all enemy towers
        long tempTowers = enemyTowers;
        while (tempTowers != 0) {
            int towerPos = Long.numberOfTrailingZeros(tempTowers);
            tempTowers &= tempTowers - 1; // Clear the lowest bit

            if (towerPos == fromPos) continue; // Skip the moving piece

            // Check if this tower could attack guard if the moving piece wasn't there
            if (isOnAttackLine(towerPos, guardPos, fromPos)) {
                int towerHeight = isRed ? state.redStackHeights[towerPos] : state.blueStackHeights[towerPos];
                int distance = manhattanDistance(towerPos, guardPos);

                if (towerHeight >= distance) {
                    return true; // Discovery attack found
                }
            }
        }

        return false;
    }

    /**
     * Check for tower range attacks after the move
     */
    private static boolean hasTowerRangeAttackOnGuard(Move move, int guardPos, GameState state) {
        // Check if the moving piece (if it's a tower) can attack guard from new position
        boolean isRed = state.redToMove;
        long movingPieceBit = GameState.bit(move.from);

        // Check if moving piece is a tower
        long ownTowers = isRed ? state.redTowers : state.blueTowers;
        boolean isMovingTower = (ownTowers & movingPieceBit) != 0;
        if (!isMovingTower) return false;

        int towerHeight = (isRed ? state.redStackHeights[move.from] : state.blueStackHeights[move.from]) + move.amountMoved;
        int distance = manhattanDistance(move.to, guardPos);

        if (towerHeight >= distance) {
            // Apply move and check if path is clear
            GameState tempState = state.copy();
            tempState.applyMove(move);
            return isPathClearToGuard(move.to, guardPos, tempState, towerHeight);
        }

        return false;
    }

    /**
     * Check if three points are on the same attack line
     */
    private static boolean isOnAttackLine(int attackerPos, int targetPos, int blockingPos) {
        // Check if blocking piece is between attacker and target on same line
        int attackerFile = GameState.file(attackerPos);
        int attackerRank = GameState.rank(attackerPos);
        int targetFile = GameState.file(targetPos);
        int targetRank = GameState.rank(targetPos);
        int blockingFile = GameState.file(blockingPos);
        int blockingRank = GameState.rank(blockingPos);

        // Must be on same rank, file, or diagonal
        if (attackerRank == targetRank && attackerRank == blockingRank) {
            // Same rank - check if blocking piece is between
            return isBetween(blockingFile, attackerFile, targetFile);
        } else if (attackerFile == targetFile && attackerFile == blockingFile) {
            // Same file - check if blocking piece is between
            return isBetween(blockingRank, attackerRank, targetRank);
        } else if (Math.abs(attackerFile - targetFile) == Math.abs(attackerRank - targetRank)) {
            // Diagonal - check if blocking piece is on diagonal and between
            if (Math.abs(attackerFile - blockingFile) == Math.abs(attackerRank - blockingRank) &&
                    Math.abs(blockingFile - targetFile) == Math.abs(blockingRank - targetRank)) {
                return isBetween(blockingFile, attackerFile, targetFile) &&
                        isBetween(blockingRank, attackerRank, targetRank);
            }
        }

        return false;
    }

    /**
     * Check if middle value is between min and max
     */
    private static boolean isBetween(int middle, int a, int b) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        return middle > min && middle < max;
    }

    /**
     * Check if path from tower to guard is clear
     */
    private static boolean isPathClearToGuard(int towerPos, int guardPos, GameState state, int towerHeight) {
        int distance = manhattanDistance(towerPos, guardPos);
        if (towerHeight < distance) return false;

        // Check if path is clear (no blocking pieces)
        int deltaFile = Integer.signum(GameState.file(guardPos) - GameState.file(towerPos));
        int deltaRank = Integer.signum(GameState.rank(guardPos) - GameState.rank(towerPos));

        int currentFile = GameState.file(towerPos);
        int currentRank = GameState.rank(towerPos);

        // Walk along the path
        for (int step = 1; step < distance; step++) {
            currentFile += deltaFile;
            currentRank += deltaRank;
            int currentPos = GameState.getIndex(currentRank, currentFile);

            // Check if any piece blocks the path
            long allPieces = state.redGuard | state.blueGuard | state.redTowers | state.blueTowers;
            if ((allPieces & GameState.bit(currentPos)) != 0) {
                return false; // Path blocked
            }
        }

        return true; // Path is clear
    }

    /**
     * Enhanced mate net detection (guard has no escape squares)
     */
    private static boolean isGuardInMateNetImproved(GameState state, boolean checkRed) {
        // First check if guard is in danger
        if (!isGuardInDangerImproved(state, checkRed)) {
            return false; // Not in danger, can't be in mate net
        }

        long guardBit = checkRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int[] directions = {-1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE};

        // Check each possible escape square
        for (int dir : directions) {
            int newPos = guardPos + dir;
            if (!GameState.isOnBoard(newPos)) continue;

            // Check for edge wrapping
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

            // Simulate guard moving to this square
            GameState testState = state.copy();

            // Remove guard from old position
            if (checkRed) {
                testState.redGuard &= ~GameState.bit(guardPos);
            } else {
                testState.blueGuard &= ~GameState.bit(guardPos);
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

            // Place guard at new position
            if (checkRed) {
                testState.redGuard |= GameState.bit(newPos);
            } else {
                testState.blueGuard |= GameState.bit(newPos);
            }

            // Check if guard is still in danger after moving
            if (!isGuardInDangerImproved(testState, checkRed)) {
                return false; // Found escape square
            }
        }

        return true; // No escape squares found - mate net!
    }

    /**
     * Graduated danger assessment instead of all-or-nothing
     */
    private static int evaluateGuardDangerLevel(GameState state, boolean redInDanger, boolean blueInDanger) {
        int dangerScore = 0;

        if (state.redToMove) {
            // Red to move
            if (redInDanger && !blueInDanger) {
                // Red guard in danger, blue safe - bad for red
                dangerScore -= GUARD_DANGER_PENALTY;
                dangerScore -= evaluateAttackSeverity(state, true); // Additional penalty based on attack strength
            } else if (!redInDanger && blueInDanger) {
                // Blue guard in danger, red safe - good for red
                dangerScore += GUARD_DANGER_PENALTY;
                dangerScore += evaluateAttackSeverity(state, false);
            } else if (redInDanger && blueInDanger) {
                // Both in danger - red moves first so slight advantage
                dangerScore += 200; // Small advantage for moving first
            }
        } else {
            // Blue to move
            if (blueInDanger && !redInDanger) {
                // Blue guard in danger, red safe - good for red
                dangerScore += GUARD_DANGER_PENALTY;
                dangerScore += evaluateAttackSeverity(state, false);
            } else if (!blueInDanger && redInDanger) {
                // Red guard in danger, blue safe - bad for red
                dangerScore -= GUARD_DANGER_PENALTY;
                dangerScore -= evaluateAttackSeverity(state, true);
            } else if (redInDanger && blueInDanger) {
                // Both in danger - blue moves first so slight advantage for blue
                dangerScore -= 200;
            }
        }

        return dangerScore;
    }

    /**
     * Evaluate how severe an attack on the guard is
     */
    private static int evaluateAttackSeverity(GameState state, boolean guardIsRed) {
        long guardBit = guardIsRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return 0;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int attackerCount = 0;
        int defenderCount = 0;

        // Count attackers
        GameState enemyMoveState = state.copy();
        enemyMoveState.redToMove = !guardIsRed;
        List<Move> enemyMoves = MoveGenerator.generateAllMoves(enemyMoveState);

        for (Move move : enemyMoves) {
            if (move.to == guardPos) {
                attackerCount++;
            }
        }

        // Count defenders (pieces that can recapture)
        List<Move> ownMoves = MoveGenerator.generateAllMoves(state);
        for (Move move : ownMoves) {
            if (move.to == guardPos) {
                defenderCount++;
            }
        }

        // Calculate severity based on attacker/defender balance
        int severity = (attackerCount - defenderCount) * 150;

        // Bonus penalty if multiple attackers
        if (attackerCount > 1) {
            severity += 100;
        }

        return Math.max(0, severity);
    }

    /**
     * Material evaluation with context
     */
    private static int evaluateMaterial(GameState state) {
        int materialScore = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int redHeight = state.redStackHeights[i];
            int blueHeight = state.blueStackHeights[i];

            // Basic material value
            materialScore += (redHeight - blueHeight) * MATERIAL_VALUE;

            // Mobility bonus for tall towers
            if (redHeight > 0) {
                materialScore += calculateMobilityBonus(state, i, true, redHeight);
            }
            if (blueHeight > 0) {
                materialScore -= calculateMobilityBonus(state, i, false, blueHeight);
            }
        }

        return materialScore;
    }

    /**
     * Calculate mobility bonus for a tower
     */
    private static int calculateMobilityBonus(GameState state, int pos, boolean isRed, int height) {
        if (height == 0) return 0;

        int mobilityScore = 0;
        int possibleMoves = 0;

        // Count legal moves from this position
        for (int amount = 1; amount <= height; amount++) {
            for (int dir : new int[]{-1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE}) {
                int newPos = pos + dir * amount;

                if (GameState.isOnBoard(newPos) && isMoveLegal(pos, newPos, state, isRed)) {
                    possibleMoves++;

                    // Bonus for reaching central squares
                    if (isCentralSquare(newPos)) {
                        mobilityScore += 5;
                    }

                    // Bonus for attacking enemy pieces
                    if (threatsEnemyPiece(newPos, state, isRed)) {
                        mobilityScore += 10;
                    }
                }
            }
        }

        // Base mobility bonus
        mobilityScore += possibleMoves * MOBILITY_BONUS;

        // Penalty for immobile towers
        if (possibleMoves == 0 && height > 2) {
            mobilityScore -= height * 50; // Penalty for large immobile stacks
        }

        return mobilityScore;
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

        // Piece coordination
        positionalScore += evaluatePieceCoordination(state);

        return positionalScore;
    }

    /**
     * Evaluate guard advancement towards enemy castle
     */
    private static int evaluateGuardAdvancement(GameState state) {
        int advancementScore = 0;

        // Red guard advancement towards blue castle (rank 0)
        if (state.redGuard != 0) {
            int redGuardPos = Long.numberOfTrailingZeros(state.redGuard);
            int redRank = GameState.rank(redGuardPos);
            int distanceToTarget = redRank; // Distance to rank 0

            // Bonus for being closer to enemy castle
            advancementScore += (6 - distanceToTarget) * GUARD_ADVANCEMENT_BONUS;

            // Bonus for being on target file (D-file)
            int redFile = GameState.file(redGuardPos);
            int fileDistance = Math.abs(redFile - 3); // Distance from D-file
            advancementScore += (3 - fileDistance) * 20;
        }

        // Blue guard advancement towards red castle (rank 6)
        if (state.blueGuard != 0) {
            int blueGuardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int blueRank = GameState.rank(blueGuardPos);
            int distanceToTarget = 6 - blueRank; // Distance to rank 6

            // Penalty for blue being closer to red castle
            advancementScore -= (6 - distanceToTarget) * GUARD_ADVANCEMENT_BONUS;

            // File bonus/penalty
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

                // Extra bonus for tall towers in center
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

    /**
     * Evaluate tactical threats (captures, forks, etc.)
     */
    private static int evaluateTacticalThreats(GameState state) {
        int tacticalScore = 0;

        // Evaluate immediate tactical threats
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        for (Move move : moves) {
            // Capture threats
            if (isCapture(move, state)) {
                tacticalScore += state.redToMove ? 50 : -50;
            }

            // Fork threats (attacking multiple pieces)
            if (createsFork(move, state)) {
                tacticalScore += state.redToMove ? 100 : -100;
            }

            // Discovery attacks
            if (createsDiscoveryAttack(move, state)) {
                tacticalScore += state.redToMove ? 75 : -75;
            }
        }

        return tacticalScore;
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
        if (capturesGuard) score += GUARD_CAPTURE_SCORE; // Reduced from 10000
        if (capturesTower) score += 500 * (isRed ? state.redStackHeights[move.to] : state.blueStackHeights[move.to]);
        if (stacksOnOwn) score += 10;
        score += move.amountMoved;

        if (isGuardMove && isExposed(move, state)) {
            score -= GUARD_CAPTURE_SCORE; // Reduced penalty
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

    // === LEGACY METHODS FOR COMPATIBILITY ===

    /**
     * Original guard danger detection method (for compatibility)
     * @param state
     * @param checkRed true if red guard position should be evaluated
     * @return if the next enemy move would take the Guard
     */
    private static boolean isGuardInDanger(GameState state, boolean checkRed) {
        // Use improved version but keep method name for compatibility
        return isGuardInDangerImproved(state, checkRed);
    }

    /**
     * Original mate net detection method (for compatibility)
     */
    private static boolean isGuardInMateNet(GameState state, boolean checkRed) {
        // Use improved version but keep method name for compatibility
        return isGuardInMateNetImproved(state, checkRed);
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
            long allPieces = state.redTowers | state.blueTowers | state.redGuard | state.blueGuard;
            if ((allPieces & GameState.bit(target)) != 0) {
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

    // Helper methods (implement these based on your game logic)
    private static boolean isMoveLegal(int from, int to, GameState state, boolean isRed) {
        // Basic implementation - you may need to enhance this
        long ownPieces = isRed ? (state.redGuard | state.redTowers) : (state.blueGuard | state.blueTowers);

        // Can't move to square occupied by own piece
        return (ownPieces & GameState.bit(to)) == 0;
    }

    private static boolean isCentralSquare(int pos) {
        return pos == GameState.getIndex(2, 3) ||
                pos == GameState.getIndex(3, 3) ||
                pos == GameState.getIndex(4, 3);
    }

    private static boolean threatsEnemyPiece(int pos, GameState state, boolean isRed) {
        long enemyPieces = isRed ? (state.blueGuard | state.blueTowers) : (state.redGuard | state.redTowers);
        return (enemyPieces & GameState.bit(pos)) != 0;
    }

    private static boolean createsFork(Move move, GameState state) {
        // Simple fork detection - you can enhance this
        return false; // Placeholder
    }

    private static boolean createsDiscoveryAttack(Move move, GameState state) {
        // Simple discovery attack detection - you can enhance this
        return false; // Placeholder
    }

    private static int evaluatePieceCoordination(GameState state) {
        // Simple coordination evaluation - you can enhance this
        return 0; // Placeholder
    }

    private static boolean isEndgame(GameState state) {
        int totalPieces = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalPieces += state.redStackHeights[i] + state.blueStackHeights[i];
        }
        return totalPieces <= 8; // Simple endgame detection
    }

    private static int evaluateEndgame(GameState state) {
        // Simple endgame evaluation - you can enhance this
        int endgameScore = 0;

        // Guard advancement is more important in endgame
        if (state.redGuard != 0) {
            int redRank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
            endgameScore += (6 - redRank) * 100; // Extra bonus for advancement
        }

        if (state.blueGuard != 0) {
            int blueRank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
            endgameScore -= blueRank * 100; // Penalty for enemy advancement
        }

        return endgameScore;
    }
}
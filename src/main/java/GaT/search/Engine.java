package GaT.search;

import GaT.game.GameState;
import GaT.game.Move;
import GaT.game.MoveGenerator;
import GaT.game.TTEntry;
import GaT.evaluation.Evaluator;
import java.util.*;

/**
 * UPDATED ENGINE with SimpleMoveOrdering and SimpleTranspositionTable
 *
 * Clean integration with the new simplified components:
 * ✅ Uses SimpleMoveOrdering for move ordering
 * ✅ Uses SimpleTranspositionTable for caching
 * ✅ All search algorithms in one class
 * ✅ Game-specific optimizations
 * ✅ Clean, maintainable code
 */
public class Engine {

    // === CONSTANTS ===
    private static final int MAX_DEPTH = 64;
    private static final int Q_MAX_DEPTH = 8;

    // === CORE COMPONENTS ===
    private final Evaluator evaluator;
    private final SimpleMoveOrdering moveOrdering;
    private final SimpleTranspositionTable transpositionTable;

    // === SEARCH STATE ===
    private volatile boolean timeUp;
    private long searchStartTime;
    private long timeLimit;
    private int nodesSearched;
    private Move bestRootMove;

    public Engine() {
        this.evaluator = new Evaluator();
        this.moveOrdering = new SimpleMoveOrdering();
        this.transpositionTable = new SimpleTranspositionTable();
        reset();
    }

    // === PUBLIC INTERFACE ===

    /**
     * Find best move with time limit
     */
    public Move findBestMove(GameState state, long timeMs) {
        return findBestMove(state, MAX_DEPTH, timeMs);
    }

    /**
     * Find best move with depth and time limits
     */
    public Move findBestMove(GameState state, int maxDepth, long timeMs) {
        if (state == null) return null;

        setupSearch(timeMs);
        Move bestMove = null;
        int bestScore = state.redToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        // Iterative deepening
        for (int depth = 1; depth <= maxDepth && !timeUp; depth++) {
            long iterStart = System.currentTimeMillis();

            SearchResult result = searchRoot(state, depth);

            if (!timeUp && result.move != null) {
                bestMove = result.move;
                bestScore = result.score;
                bestRootMove = bestMove;

                long iterTime = System.currentTimeMillis() - iterStart;
                logIteration(depth, result, iterTime);

                // Check if we have time for next iteration
                if (!shouldContinueToNextDepth(iterTime)) {
                    break;
                }

                // Stop if we found a winning move
                if (Math.abs(bestScore) > 9000) {
                    System.out.println("🏆 Winning move found at depth " + depth);
                    break;
                }
            }
        }

        printSearchSummary(bestMove, bestScore);
        return bestMove;
    }

    // === ROOT SEARCH ===

    private SearchResult searchRoot(GameState state, int depth) {
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return new SearchResult(null, evaluator.evaluate(state));
        }

        // Order root moves using our move ordering
        TTEntry ttEntry = transpositionTable.get(state.hash());
        moveOrdering.orderMoves(moves, state, depth, ttEntry);

        Move bestMove = null;
        boolean isMaximizing = state.redToMove;
        int bestScore = isMaximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        for (int i = 0; i < moves.size() && !timeUp; i++) {
            Move move = moves.get(i);

            GameState newState = state.copy();
            newState.applyMove(move);

            int score;
            if (i == 0) {
                // First move: full window
                score = pvs(newState, depth - 1, alpha, beta, !isMaximizing, true);
            } else {
                // Later moves: null window first
                int nullWindow = isMaximizing ? alpha + 1 : beta - 1;
                score = pvs(newState, depth - 1,
                        isMaximizing ? nullWindow - 1 : alpha,
                        isMaximizing ? beta : nullWindow,
                        !isMaximizing, false);

                // Re-search if needed
                if (!timeUp && ((isMaximizing && score > alpha && score < beta) ||
                        (!isMaximizing && score < beta && score > alpha))) {
                    score = pvs(newState, depth - 1, alpha, beta, !isMaximizing, true);
                }
            }

            if ((isMaximizing && score > bestScore) || (!isMaximizing && score < bestScore)) {
                bestScore = score;
                bestMove = move;

                if (isMaximizing) {
                    alpha = Math.max(alpha, score);
                } else {
                    beta = Math.min(beta, score);
                }
            }
        }

        return new SearchResult(bestMove, bestScore);
    }

    // === PRINCIPAL VARIATION SEARCH ===

    private int pvs(GameState state, int depth, int alpha, int beta, boolean maximizing, boolean isPV) {
        nodesSearched++;

        if (timeUp) return evaluator.evaluate(state);

        // Check for terminal position
        int terminalScore = evaluator.checkTerminal(state);
        if (terminalScore != 0) return terminalScore;

        // Quiescence search at leaf nodes
        if (depth <= 0) {
            return quiescence(state, alpha, beta, maximizing, 0);
        }

        // Transposition table lookup
        long hash = state.hash();
        TTEntry ttEntry = transpositionTable.get(hash);
        if (ttEntry != null && transpositionTable.isUsable(ttEntry, depth, alpha, beta) && !isPV) {
            return ttEntry.score;
        }

        // Null-move pruning
        if (canDoNullMove(state, depth, beta, isPV, maximizing)) {
            GameState nullState = state.copy();
            nullState.redToMove = !nullState.redToMove;
            int nullScore = pvs(nullState, depth - 3, alpha, beta, !maximizing, false);
            if ((maximizing && nullScore >= beta) || (!maximizing && nullScore <= alpha)) {
                return nullScore;
            }
        }

        // Generate and order moves
        List<Move> moves = MoveGenerator.generateAllMoves(state);
        if (moves.isEmpty()) {
            return evaluator.evaluate(state); // No legal moves
        }

        moveOrdering.orderMoves(moves, state, depth, ttEntry);

        // Main search loop
        Move bestMove = null;
        int bestScore = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        boolean raisedAlpha = false;

        for (int i = 0; i < moves.size() && !timeUp; i++) {
            Move move = moves.get(i);

            GameState newState = state.copy();
            newState.applyMove(move);

            int score;

            if (i == 0) {
                // First move: full window
                score = pvs(newState, depth - 1, alpha, beta, !maximizing, isPV);
            } else {
                // Late move reduction
                int reduction = calculateLMR(depth, i, move, state, isPV);
                int searchDepth = Math.max(1, depth - 1 - reduction);

                // Null window search
                int nullWindow = maximizing ? alpha + 1 : beta - 1;
                score = pvs(newState, searchDepth,
                        maximizing ? nullWindow - 1 : alpha,
                        maximizing ? beta : nullWindow,
                        !maximizing, false);

                // Re-search if null window failed
                if (!timeUp && ((maximizing && score > alpha) || (!maximizing && score < beta))) {
                    score = pvs(newState, depth - 1, alpha, beta, !maximizing, false);
                }
            }

            // Update best score
            if (maximizing) {
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                    if (score > alpha) {
                        alpha = score;
                        raisedAlpha = true;
                    }
                }
                if (beta <= alpha) {
                    recordCutoff(move, depth, state);
                    break;
                }
            } else {
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = move;
                    if (score < beta) {
                        beta = score;
                        raisedAlpha = true;
                    }
                }
                if (beta <= alpha) {
                    recordCutoff(move, depth, state);
                    break;
                }
            }
        }

        // Store in transposition table
        int flag = raisedAlpha ? TTEntry.EXACT :
                (maximizing ? TTEntry.UPPER_BOUND : TTEntry.LOWER_BOUND);
        transpositionTable.put(hash, new TTEntry(bestScore, depth, flag, bestMove));

        return bestScore;
    }

    // === QUIESCENCE SEARCH ===

    private int quiescence(GameState state, int alpha, int beta, boolean maximizing, int qDepth) {
        nodesSearched++;

        if (timeUp || qDepth >= Q_MAX_DEPTH) return evaluator.evaluate(state);

        // Stand-pat evaluation
        int standPat = evaluator.evaluate(state);

        if (maximizing) {
            if (standPat >= beta) return beta;
            alpha = Math.max(alpha, standPat);
        } else {
            if (standPat <= alpha) return alpha;
            beta = Math.min(beta, standPat);
        }

        // Generate tactical moves only
        List<Move> tacticalMoves = generateTacticalMoves(state);
        if (tacticalMoves.isEmpty()) return standPat;

        // Order tactical moves (simple ordering for Q-search)
        tacticalMoves.sort((m1, m2) -> Integer.compare(
                scoreTacticalMove(m2, state),
                scoreTacticalMove(m1, state)
        ));

        int bestScore = standPat;

        for (Move move : tacticalMoves) {
            if (timeUp) break;

            // Delta pruning
            if (shouldDeltaPrune(move, state, standPat, alpha, beta, maximizing)) {
                continue;
            }

            GameState newState = state.copy();
            newState.applyMove(move);

            int score = quiescence(newState, alpha, beta, !maximizing, qDepth + 1);

            if (maximizing) {
                bestScore = Math.max(bestScore, score);
                alpha = Math.max(alpha, score);
                if (beta <= alpha) break;
            } else {
                bestScore = Math.min(bestScore, score);
                beta = Math.min(beta, score);
                if (beta <= alpha) break;
            }
        }

        return bestScore;
    }

    // === TACTICAL MOVE GENERATION ===

    private List<Move> generateTacticalMoves(GameState state) {
        List<Move> allMoves = MoveGenerator.generateAllMoves(state);
        List<Move> tacticalMoves = new ArrayList<>();

        for (Move move : allMoves) {
            if (isTacticalMove(move, state)) {
                tacticalMoves.add(move);
            }
        }

        return tacticalMoves;
    }

    private boolean isTacticalMove(Move move, GameState state) {
        return isCapture(move, state) ||
                isGuardMove(move, state) ||
                isWinningMove(move, state) ||
                threatensgEnemyGuard(move, state);
    }

    private int scoreTacticalMove(Move move, GameState state) {
        int score = 0;

        if (isWinningMove(move, state)) score += 100000;
        if (isCapture(move, state)) {
            score += getPieceValue(state, move.to) * 10 - getPieceValue(state, move.from);
        }
        if (isGuardMove(move, state)) score += 1000;

        return score;
    }

    // === PRUNING CONDITIONS ===

    private boolean canDoNullMove(GameState state, int depth, int beta, boolean isPV, boolean maximizing) {
        return !isPV && depth >= 3 && !isInCheck(state, maximizing);
    }

    private int calculateLMR(int depth, int moveIndex, Move move, GameState state, boolean isPV) {
        if (depth < 3 || moveIndex < 3 || isPV) return 0;
        if (isTacticalMove(move, state)) return 0;

        int reduction = 1;
        if (moveIndex > 6) reduction++;
        return Math.min(reduction, depth - 1);
    }

    private boolean shouldDeltaPrune(Move move, GameState state, int standPat,
                                     int alpha, int beta, boolean maximizing) {
        if (!isCapture(move, state)) return false;

        int captureValue = getPieceValue(state, move.to);
        int delta = standPat + captureValue + 200; // Safety margin

        return maximizing ? (delta < alpha) : (delta > beta);
    }

    // === HELPER METHODS ===

    private void recordCutoff(Move move, int depth, GameState state) {
        if (!isCapture(move, state)) { // Don't record captures
            moveOrdering.storeKillerMove(move, depth);
            moveOrdering.updateHistory(move, depth, state);
        }
    }

    // === GAME-SPECIFIC HELPERS ===

    private boolean isCapture(Move move, GameState state) {
        return state.redStackHeights[move.to] > 0 || state.blueStackHeights[move.to] > 0 ||
                (state.redGuard & (1L << move.to)) != 0 || (state.blueGuard & (1L << move.to)) != 0;
    }

    private boolean isGuardMove(Move move, GameState state) {
        long fromBit = 1L << move.from;
        return (state.redGuard & fromBit) != 0 || (state.blueGuard & fromBit) != 0;
    }

    private boolean isWinningMove(Move move, GameState state) {
        if (!isGuardMove(move, state)) return false;

        boolean isRedGuard = (state.redGuard & (1L << move.from)) != 0;
        int redCastle = 45; // D7 square
        int blueCastle = 3;  // D1 square

        return (isRedGuard && move.to == blueCastle) || (!isRedGuard && move.to == redCastle);
    }

    private boolean threatensgEnemyGuard(Move move, GameState state) {
        // Quick check if move threatens enemy guard
        GameState newState = state.copy();
        newState.applyMove(move);
        return isInCheck(newState, !state.redToMove);
    }

    private boolean isInCheck(GameState state, boolean redInCheck) {
        return evaluator.isGuardThreatened(state, redInCheck);
    }

    private int getPieceValue(GameState state, int square) {
        if ((state.redGuard & (1L << square)) != 0 || (state.blueGuard & (1L << square)) != 0) {
            return 1000; // Guard
        }
        return (state.redStackHeights[square] + state.blueStackHeights[square]) * 100;
    }

    // === TIME MANAGEMENT ===

    private void setupSearch(long timeMs) {
        timeUp = false;
        searchStartTime = System.currentTimeMillis();
        timeLimit = timeMs;
        nodesSearched = 0;
        bestRootMove = null;
    }

    private boolean shouldContinueToNextDepth(long lastIterationTime) {
        long elapsed = System.currentTimeMillis() - searchStartTime;
        long remaining = timeLimit - elapsed;

        // Need at least 2x the time of last iteration
        return remaining > (lastIterationTime * 2) && elapsed < timeLimit * 0.6;
    }

    // === LOGGING AND STATS ===

    private void logIteration(int depth, SearchResult result, long timeMs) {
        double nps = timeMs > 0 ? (nodesSearched * 1000.0 / timeMs) : 0;
        System.out.printf("Depth %2d: %s (score: %+5d) [%4dms, %,6d nodes, %.0f nps]%n",
                depth, result.move, result.score, timeMs, nodesSearched, nps);
    }

    private void printSearchSummary(Move bestMove, int bestScore) {
        long totalTime = System.currentTimeMillis() - searchStartTime;
        double nps = totalTime > 0 ? (nodesSearched * 1000.0 / totalTime) : 0;

        System.out.println("=== SEARCH COMPLETE ===");
        System.out.printf("Best move: %s (score: %+d)%n", bestMove, bestScore);
        System.out.printf("Time: %dms | Nodes: %,d | NPS: %.0f%n", totalTime, nodesSearched, nps);
        System.out.println("📊 " + moveOrdering.getStatistics());
        System.out.println("📊 " + transpositionTable.getStatistics());
    }

    public void reset() {
        moveOrdering.clear();
        transpositionTable.clear();
    }

    // === UTILITY CLASSES ===

    private static class SearchResult {
        final Move move;
        final int score;

        SearchResult(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }

    // === GETTERS ===

    public int getNodesSearched() { return nodesSearched; }
    public long getSearchTime() { return System.currentTimeMillis() - searchStartTime; }
    public double getTTHitRate() { return transpositionTable.getHitRate(); }
    public String getMoveOrderingStats() { return moveOrdering.getStatistics(); }
    public String getTranspositionTableStats() { return transpositionTable.getStatistics(); }
}
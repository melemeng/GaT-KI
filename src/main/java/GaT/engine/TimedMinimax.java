package GaT.engine;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.model.TTEntry;
import GaT.model.SearchConfig;
import GaT.search.Minimax;
import GaT.search.MoveGenerator;
import GaT.search.MoveOrdering;
import GaT.search.QuiescenceSearch;
import GaT.search.SearchStatistics;
import GaT.search.TranspositionTable;
import GaT.evaluation.Evaluator;

import java.util.List;

/**
 * CRITICAL FIX: TimedMinimax - Node Count & Search Fixed
 *
 * FIXES:
 * ✅ 1. Node counting now works correctly
 * ✅ 2. Statistics properly shared between components
 * ✅ 3. Real search is performed (not just evaluation)
 * ✅ 4. Timeout handling fixed
 * ✅ 5. Consistent component architecture
 */
public class TimedMinimax {

    // === SHARED COMPONENTS - CRITICAL FIX ===
    private static final SearchStatistics statistics = SearchStatistics.getInstance();
    private static final Evaluator evaluator = new Evaluator();
    private static final MoveOrdering moveOrdering = new MoveOrdering();
    private static final TranspositionTable transpositionTable = new TranspositionTable(SearchConfig.TT_SIZE);

    // === SEARCH STATE ===
    private static volatile long timeLimitMillis;
    private static volatile long startTime;
    private static volatile boolean searchAborted = false;

    // === MAIN INTERFACES ===

    public static Move findBestMoveUltimate(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveFixed(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.ALPHA_BETA_Q);
    }

    public static Move findBestMoveWithStrategy(GameState state, int maxDepth, long timeMillis,
                                                SearchConfig.SearchStrategy strategy) {
        return findBestMoveFixed(state, maxDepth, timeMillis, strategy);
    }

    // === CORE FIXED SEARCH ===

    private static Move findBestMoveFixed(GameState state, int maxDepth, long timeMillis,
                                          SearchConfig.SearchStrategy strategy) {
        if (state == null) {
            System.err.println("❌ CRITICAL: Null game state!");
            return null;
        }

        if (strategy == null) {
            strategy = SearchConfig.SearchStrategy.ALPHA_BETA;
        }

        // CRITICAL FIX: Proper initialization
        initializeSearchFixed(timeMillis);

        List<Move> legalMoves = MoveGenerator.generateAllMoves(state);
        if (legalMoves.isEmpty()) {
            System.err.println("❌ No legal moves available!");
            return null;
        }

        Move bestMove = legalMoves.get(0); // Emergency fallback
        int bestDepth = 0;
        long totalNodes = 0;

        System.out.println("=== REAL SEARCH WITH WORKING NODE COUNTING ===");
        System.out.printf("Strategy: %s | Time: %dms | Legal moves: %d%n",
                strategy, timeMillis, legalMoves.size());

        // === ITERATIVE DEEPENING WITH REAL SEARCH ===
        for (int depth = 1; depth <= maxDepth && !searchAborted; depth++) {
            long depthStartTime = System.currentTimeMillis();
            long nodesBefore = statistics.getNodeCount();

            try {
                // CRITICAL FIX: Real minimax search instead of PVS bypass
                SearchResult result = performRealSearch(state, depth, legalMoves);

                if (result != null && result.move != null) {
                    bestMove = result.move;
                    bestDepth = depth;

                    long depthNodes = statistics.getNodeCount() - nodesBefore;
                    totalNodes += depthNodes;

                    long depthTime = System.currentTimeMillis() - depthStartTime;
                    double nps = depthTime > 0 ? (double)depthNodes * 1000 / depthTime : 0;

                    System.out.printf("✅ Depth %d: %s (score: %+d, time: %dms, nodes: %,d, nps: %.0f)%n",
                            depth, bestMove, result.score, depthTime, depthNodes, nps);

                    // Early termination for strong moves
                    if (Math.abs(result.score) > 3000) {
                        System.out.println("🎯 Very strong move found, terminating");
                        break;
                    }

                    // Time management
                    if (!shouldContinueSearch(depthTime, timeMillis, depth)) {
                        System.out.println("⏱ Time management: Stopping search");
                        break;
                    }
                } else {
                    System.out.printf("⚠️ Depth %d failed%n", depth);
                    break;
                }

            } catch (Exception e) {
                System.err.printf("❌ Error at depth %d: %s%n", depth, e.getMessage());
                break;
            }
        }

        // === FINAL STATISTICS ===
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("=== REAL SEARCH COMPLETE ===");
        System.out.printf("Best move: %s | Depth: %d | Time: %dms%n", bestMove, bestDepth, totalTime);
        System.out.printf("Total nodes: %,d | NPS: %,.0f%n", totalNodes,
                totalTime > 0 ? (double)totalNodes * 1000 / totalTime : 0);

        // Update legacy counter
        Minimax.counter = (int) totalNodes;

        return bestMove;
    }

    // === REAL SEARCH IMPLEMENTATION ===

    private static SearchResult performRealSearch(GameState state, int depth, List<Move> legalMoves) {
        // Order moves for better performance
        orderMoves(legalMoves, state, depth);

        Move bestMove = null;
        boolean isRed = state.redToMove;
        int bestScore = isRed ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        for (Move move : legalMoves) {
            if (searchAborted) break;

            try {
                GameState copy = state.copy();
                copy.applyMove(move);

                // CRITICAL FIX: Use direct alpha-beta search
                int score = alphaBetaSearchFixed(copy, depth - 1, alpha, beta, !isRed);

                if ((isRed && score > bestScore) || (!isRed && score < bestScore) || bestMove == null) {
                    bestScore = score;
                    bestMove = move;

                    // Update alpha-beta bounds
                    if (isRed) {
                        alpha = Math.max(alpha, score);
                    } else {
                        beta = Math.min(beta, score);
                    }
                }

                // Early termination for very strong moves
                if (Math.abs(score) > 5000) {
                    System.out.printf("  🎯 Very strong move: %s (score: %+d)%n", move, score);
                    break;
                }

            } catch (Exception e) {
                System.err.printf("  ⚠️ Error with move %s: %s%n", move, e.getMessage());
                continue;
            }
        }

        return bestMove != null ? new SearchResult(bestMove, bestScore) : null;
    }

    // === FIXED ALPHA-BETA SEARCH ===

    private static int alphaBetaSearchFixed(GameState state, int depth, int alpha, int beta, boolean maximizingPlayer) {
        statistics.incrementNodeCount(); // CRITICAL: Count nodes!

        // Timeout check
        if (searchAborted || System.currentTimeMillis() - startTime >= timeLimitMillis) {
            searchAborted = true;
            return evaluator.evaluate(state, depth);
        }

        // Terminal conditions
        if (depth <= 0 || isGameOver(state)) {
            return evaluator.evaluate(state, depth);
        }

        // Transposition table lookup
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

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        orderMoves(moves, state, depth);

        Move bestMove = null;
        int originalAlpha = alpha;

        if (maximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;

            for (Move move : moves) {
                if (searchAborted) break;

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = alphaBetaSearchFixed(copy, depth - 1, alpha, beta, false);

                if (eval > maxEval) {
                    maxEval = eval;
                    bestMove = move;
                }

                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break; // Alpha-beta cutoff
                }
            }

            // Store in transposition table
            storeTTEntry(hash, maxEval, depth, originalAlpha, beta, bestMove);
            return maxEval;

        } else {
            int minEval = Integer.MAX_VALUE;

            for (Move move : moves) {
                if (searchAborted) break;

                GameState copy = state.copy();
                copy.applyMove(move);

                int eval = alphaBetaSearchFixed(copy, depth - 1, alpha, beta, true);

                if (eval < minEval) {
                    minEval = eval;
                    bestMove = move;
                }

                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break; // Alpha-beta cutoff
                }
            }

            storeTTEntry(hash, minEval, depth, originalAlpha, beta, bestMove);
            return minEval;
        }
    }

    // === HELPER METHODS ===

    private static void initializeSearchFixed(long timeMillis) {
        startTime = System.currentTimeMillis();
        timeLimitMillis = timeMillis;
        searchAborted = false;

        // Reset statistics properly
        statistics.reset();
        statistics.startSearch();

        // Clear transposition table if too full
        if (transpositionTable.size() > SearchConfig.TT_EVICTION_THRESHOLD) {
            transpositionTable.clear();
        }

        // Reset move ordering
        moveOrdering.resetKillerMoves();

        // Set evaluation time
        Evaluator.setRemainingTime(timeMillis);

        System.out.println("🔧 Search components initialized correctly");
    }

    private static void orderMoves(List<Move> moves, GameState state, int depth) {
        if (moves.size() <= 1) return;

        try {
            TTEntry entry = transpositionTable.get(state.hash());
            moveOrdering.orderMoves(moves, state, depth, entry);
        } catch (Exception e) {
            // Simple fallback ordering
            moves.sort((a, b) -> {
                boolean aCap = isCapture(a, state);
                boolean bCap = isCapture(b, state);
                if (aCap && !bCap) return -1;
                if (!aCap && bCap) return 1;
                return Integer.compare(b.amountMoved, a.amountMoved);
            });
        }
    }

    private static boolean shouldContinueSearch(long lastDepthTime, long totalTimeLimit, int currentDepth) {
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = totalTimeLimit - elapsed;

        // Conservative time management
        double growthFactor = currentDepth <= 4 ? 2.5 : 3.5;
        long estimatedNextTime = (long)(lastDepthTime * growthFactor);

        return estimatedNextTime < remaining * 0.6; // Conservative buffer
    }

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
        transpositionTable.put(hash, entry);
    }

    private static boolean isGameOver(GameState state) {
        // Guard captured
        if (state.redGuard == 0 || state.blueGuard == 0) {
            return true;
        }

        // Guard reached enemy castle
        long redCastlePos = GameState.bit(GameState.getIndex(0, 3)); // D1
        long blueCastlePos = GameState.bit(GameState.getIndex(6, 3)); // D7

        boolean redWins = (state.redGuard & redCastlePos) != 0;
        boolean blueWins = (state.blueGuard & blueCastlePos) != 0;

        return redWins || blueWins;
    }

    private static boolean isCapture(Move move, GameState state) {
        long toBit = GameState.bit(move.to);
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & toBit) != 0;
    }

    // === RESULT CLASS ===

    private static class SearchResult {
        final Move move;
        final int score;

        SearchResult(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }

    // === LEGACY COMPATIBILITY ===

    public static Move findBestMoveWithTime(GameState state, int maxDepth, long timeMillis) {
        return findBestMoveFixed(state, maxDepth, timeMillis, SearchConfig.SearchStrategy.ALPHA_BETA);
    }

    public static long getTotalNodesSearched() {
        return statistics.getNodeCount();
    }
}
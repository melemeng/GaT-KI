package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;

import java.util.List;

/**
 * TACTICAL EVALUATOR - Complete Implementation
 *
 * Provides tactical pattern recognition and evaluation for Guards and Towers.
 * This is a lightweight evaluator that focuses on tactical features only.
 * It does NOT extend Evaluator to avoid circular dependencies.
 */
public class TacticalEvaluator {

    // === CONSTANTS ===
    private static final int FORK_BONUS = 150;
    private static final int PIN_BONUS = 120;
    private static final int TOWER_CHAIN_BONUS = 80;
    private static final int FORCING_MOVE_BONUS = 60;
    private static final int DISCOVERY_BONUS = 100;

    // === MAIN EVALUATION ===

    /**
     * Evaluate tactical features of the position
     */
    public int evaluateTactical(GameState state) {
        if (state == null) return 0;

        int tacticalScore = 0;

        // Pattern detection
        tacticalScore += detectForks(state);
        tacticalScore += detectPins(state);
        tacticalScore += detectTowerChains(state);
        tacticalScore += evaluateForcingMoves(state);

        return tacticalScore;
    }

    // === FORK DETECTION ===

    /**
     * Detect fork opportunities (attacking 2+ targets)
     */
    public int detectForks(GameState state) {
        int forkScore = 0;

        // Check red forks
        GameState redState = state.copy();
        redState.redToMove = true;
        List<Move> redMoves = MoveGenerator.generateAllMoves(redState);

        for (Move move : redMoves) {
            int threats = countThreatsFromMove(state, move, true);
            if (threats >= 2) {
                forkScore += FORK_BONUS;
                if (threatsIncludeGuard(state, move, true)) {
                    forkScore += FORK_BONUS / 2;
                }
            }
        }

        // Check blue forks
        GameState blueState = state.copy();
        blueState.redToMove = false;
        List<Move> blueMoves = MoveGenerator.generateAllMoves(blueState);

        for (Move move : blueMoves) {
            int threats = countThreatsFromMove(state, move, false);
            if (threats >= 2) {
                forkScore -= FORK_BONUS;
                if (threatsIncludeGuard(state, move, false)) {
                    forkScore -= FORK_BONUS / 2;
                }
            }
        }

        return forkScore;
    }

    // === PIN DETECTION ===

    /**
     * Detect pinned pieces
     */
    public int detectPins(GameState state) {
        int pinScore = 0;

        // Check for pinned red pieces
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            pinScore -= detectPinsForSide(state, guardPos, true);
        }

        // Check for pinned blue pieces
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            pinScore += detectPinsForSide(state, guardPos, false);
        }

        return pinScore;
    }

    private int detectPinsForSide(GameState state, int guardPos, boolean checkRed) {
        int pinValue = 0;

        // Check each friendly piece
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((checkRed && state.redStackHeights[i] > 0) ||
                    (!checkRed && state.blueStackHeights[i] > 0)) {

                if (isPiecePinnedToGuard(state, i, guardPos, checkRed)) {
                    int pieceValue = checkRed ?
                            state.redStackHeights[i] : state.blueStackHeights[i];
                    pinValue += PIN_BONUS + (pieceValue * 30);
                }
            }
        }

        return pinValue;
    }

    // === TOWER CHAIN DETECTION ===

    /**
     * Detect tower chains (connected towers)
     */
    public int detectTowerChains(GameState state) {
        int chainScore = 0;
        boolean[] visited = new boolean[GameState.NUM_SQUARES];

        // Find red chains
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (!visited[i] && state.redStackHeights[i] > 0) {
                int chainLength = exploreChain(state, i, true, visited);
                if (chainLength >= 2) {
                    chainScore += TOWER_CHAIN_BONUS * chainLength;
                }
            }
        }

        // Reset visited for blue
        visited = new boolean[GameState.NUM_SQUARES];

        // Find blue chains
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (!visited[i] && state.blueStackHeights[i] > 0) {
                int chainLength = exploreChain(state, i, false, visited);
                if (chainLength >= 2) {
                    chainScore -= TOWER_CHAIN_BONUS * chainLength;
                }
            }
        }

        return chainScore;
    }

    // === FORCING MOVES ===

    /**
     * Evaluate forcing moves that demand response
     */
    private int evaluateForcingMoves(GameState state) {
        int forcingScore = 0;

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        int forcingMoves = 0;

        // Limit analysis for performance
        int analyzed = 0;
        for (Move move : moves) {
            if (analyzed++ > 20) break;

            if (isForcingMove(move, state)) {
                forcingMoves++;
            }
        }

        // Having multiple forcing moves is good
        forcingScore = forcingMoves * FORCING_MOVE_BONUS;

        return state.redToMove ? forcingScore : -forcingScore;
    }

    /**
     * Check if a move is forcing
     */
    public boolean isForcingMove(Move move, GameState state) {
        // Guard attacks are always forcing
        if (attacksGuard(move, state)) {
            return true;
        }

        // Undefended captures
        if (capturesUndefended(move, state)) {
            return true;
        }

        // Castle threats
        if (threatensCastleEntry(move, state)) {
            return true;
        }

        return false;
    }

    // === HELPER METHODS ===

    private int countThreatsFromMove(GameState state, Move move, boolean byRed) {
        int threats = 0;
        GameState afterMove = state.copy();
        afterMove.applyMove(move);

        int range = move.amountMoved;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            for (int dist = 1; dist <= range; dist++) {
                int target = move.to + dir * dist;

                if (!GameState.isOnBoard(target)) break;
                if (isRankWrap(move.to, target, dir)) break;

                // Check for enemy pieces
                if (byRed) {
                    if (state.blueStackHeights[target] > 0) threats++;
                    if (state.blueGuard != 0 &&
                            target == Long.numberOfTrailingZeros(state.blueGuard)) {
                        threats += 2;
                    }
                } else {
                    if (state.redStackHeights[target] > 0) threats++;
                    if (state.redGuard != 0 &&
                            target == Long.numberOfTrailingZeros(state.redGuard)) {
                        threats += 2;
                    }
                }

                // Stop at first piece
                if (state.redStackHeights[target] > 0 ||
                        state.blueStackHeights[target] > 0) break;
            }
        }

        return threats;
    }

    private boolean threatsIncludeGuard(GameState state, Move move, boolean byRed) {
        long enemyGuard = byRed ? state.blueGuard : state.redGuard;
        if (enemyGuard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(enemyGuard);
        return canReachSquare(state, move.to, guardPos, move.amountMoved);
    }

    private boolean isPiecePinnedToGuard(GameState state, int piecePos, int guardPos, boolean isRed) {
        if (!isOnSameLine(piecePos, guardPos)) return false;

        int direction = getDirection(piecePos, guardPos);
        if (direction == 0) return false;

        // Look for enemy attacker behind the piece
        int pos = piecePos - direction;
        while (GameState.isOnBoard(pos)) {
            if ((isRed && state.blueStackHeights[pos] > 0) ||
                    (!isRed && state.redStackHeights[pos] > 0)) {

                int range = isRed ? state.blueStackHeights[pos] : state.redStackHeights[pos];
                int distanceToGuard = Math.abs(guardPos - pos) / Math.abs(direction);

                return range >= distanceToGuard;
            }

            if (state.redStackHeights[pos] > 0 || state.blueStackHeights[pos] > 0) {
                break;
            }

            pos -= direction;
        }

        return false;
    }

    private int exploreChain(GameState state, int square, boolean forRed, boolean[] visited) {
        visited[square] = true;
        int chainLength = 1;

        // Check adjacent squares
        int[] directions = {-1, 1, -7, 7};
        for (int dir : directions) {
            int adjacent = square + dir;

            if (!GameState.isOnBoard(adjacent)) continue;
            if (isRankWrap(square, adjacent, dir)) continue;
            if (visited[adjacent]) continue;

            if ((forRed && state.redStackHeights[adjacent] > 0) ||
                    (!forRed && state.blueStackHeights[adjacent] > 0)) {
                chainLength += exploreChain(state, adjacent, forRed, visited);
            }
        }

        return chainLength;
    }

    private boolean attacksGuard(Move move, GameState state) {
        long enemyGuard = state.redToMove ? state.blueGuard : state.redGuard;
        if (enemyGuard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(enemyGuard);

        // Direct capture
        if (move.to == guardPos) return true;

        // Check if threatens from new position
        return canReachSquare(state, move.to, guardPos, move.amountMoved);
    }

    private boolean capturesUndefended(Move move, GameState state) {
        // Check if target has enemy piece
        boolean hasEnemyPiece = state.redToMove ?
                state.blueStackHeights[move.to] > 0 :
                state.redStackHeights[move.to] > 0;

        if (!hasEnemyPiece) return false;

        // Simple check - assume undefended for now
        return true;
    }

    private boolean threatensCastleEntry(Move move, GameState state) {
        // Check if guard move threatens castle
        long guardBit = state.redToMove ? state.redGuard : state.blueGuard;
        if (guardBit == 0 || move.from != Long.numberOfTrailingZeros(guardBit)) {
            return false;
        }

        int targetCastle = state.redToMove ?
                GameState.getIndex(0, 3) : GameState.getIndex(6, 3);

        return calculateManhattanDistance(move.to, targetCastle) <= 1;
    }

    private boolean canReachSquare(GameState state, int from, int to, int range) {
        if (!isOnSameLine(from, to)) return false;

        int distance = calculateManhattanDistance(from, to);
        if (distance > range) return false;

        return isPathClear(state, from, to);
    }

    private boolean isPathClear(GameState state, int from, int to) {
        int direction = getDirection(from, to);
        if (direction == 0) return false;

        int current = from + direction;
        while (current != to) {
            if (!GameState.isOnBoard(current)) return false;
            if (state.redStackHeights[current] > 0 || state.blueStackHeights[current] > 0) {
                return false;
            }
            current += direction;
        }

        return true;
    }

    private boolean isOnSameLine(int pos1, int pos2) {
        return GameState.rank(pos1) == GameState.rank(pos2) ||
                GameState.file(pos1) == GameState.file(pos2);
    }

    private int getDirection(int from, int to) {
        if (GameState.rank(from) == GameState.rank(to)) {
            return to > from ? 1 : -1;
        } else if (GameState.file(from) == GameState.file(to)) {
            return to > from ? 7 : -7;
        }
        return 0;
    }

    private boolean isRankWrap(int from, int to, int direction) {
        int fromRank = GameState.rank(from);
        int toRank = GameState.rank(to);

        if (direction == -1 || direction == 1) {
            return fromRank != toRank;
        }

        return false;
    }

    private int calculateManhattanDistance(int from, int to) {
        return Math.abs(GameState.rank(from) - GameState.rank(to)) +
                Math.abs(GameState.file(from) - GameState.file(to));
    }
}
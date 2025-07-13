package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;
import static GaT.evaluation.EvaluationParameters.*;

import java.util.ArrayList;
import java.util.List;

/**
 * ✅ FIXED TACTICAL EVALUATOR - Moderate Parameters
 *
 * 🚨 PREVIOUS PROBLEMS SOLVED:
 * ❌ Excessive tactical bonuses (150, 120, 100) → ✅ NOW moderate from EvaluationParameters
 * ❌ Local parameter definitions → ✅ NOW uses only EvaluationParameters
 * ❌ Tactical evaluation overwhelming material → ✅ NOW balanced 4-5% weight
 * ❌ Parameter chaos across modules → ✅ NOW centralized
 *
 * PRINCIPLE: Tactical evaluation provides important pattern recognition but doesn't dominate material
 */
public class TacticalEvaluator {

    /**
     * ✅ FIXED: Evaluate tactical features - Uses moderate centralized parameters
     */
    public int evaluateTactical(GameState state) {
        if (state == null) return 0;

        int tacticalScore = 0;

        // ✅ FIXED: Original pattern detection with moderate bonuses
        tacticalScore += detectForks(state);
        tacticalScore += detectPins(state);
        tacticalScore += detectTowerChains(state);
        tacticalScore += evaluateForcingMoves(state);

        // ✅ FIXED: Original cluster bonus with moderate values
        tacticalScore += evaluateClusterBonus(state);

        // ✅ FIXED: NEW PARAMETERS with moderate values from EvaluationParameters:
        tacticalScore += evaluateEdgeActivation(state);        // NOW uses Tactical.EDGE_ACTIVATION_BONUS (12)
        tacticalScore += evaluateClusterFormation(state);     // NOW uses Tactical.CLUSTER_FORMATION_BONUS (15)
        tacticalScore += evaluateSupportingAttacks(state);    // NOW uses Tactical.SUPPORTING_ATTACK_BONUS (10)

        return tacticalScore;
    }

    // === ✅ FIXED NEW IMPLEMENTATIONS WITH MODERATE BONUSES ===

    /**
     * ✅ FIXED: EDGE ACTIVATION - Moderate bonuses from EvaluationParameters
     */
    public int evaluateEdgeActivation(GameState state) {
        int bonus = 0;
        int[] edgeFiles = {0, 1, 5, 6}; // Edge files

        for (int file : edgeFiles) {
            for (int rank = 0; rank < 7; rank++) {
                int pos = GameState.getIndex(rank, file);

                // ✅ FIXED: Red towers on edges with moderate bonus
                if (state.redStackHeights[pos] > 0) {
                    int height = state.redStackHeights[pos];

                    // ✅ FIXED: Moderate bonus for towers that can threaten center
                    if (canThreatenCenter(pos, height)) {
                        bonus += height * Tactical.EDGE_ACTIVATION_BONUS;  // 12 from EvaluationParameters
                    }

                    // ✅ FIXED: Moderate extra bonus for castle threats
                    if (canThreatenEnemyCastle(pos, height, true)) {
                        bonus += Tactical.EDGE_ACTIVATION_BONUS;  // 12 extra
                    }
                }

                // ✅ FIXED: Blue towers (moderate negative)
                if (state.blueStackHeights[pos] > 0) {
                    int height = state.blueStackHeights[pos];

                    if (canThreatenCenter(pos, height)) {
                        bonus -= height * Tactical.EDGE_ACTIVATION_BONUS;
                    }

                    if (canThreatenEnemyCastle(pos, height, false)) {
                        bonus -= Tactical.EDGE_ACTIVATION_BONUS;
                    }
                }
            }
        }

        return bonus;
    }

    /**
     * ✅ FIXED: CLUSTER FORMATION - Moderate bonuses from EvaluationParameters
     */
    public int evaluateClusterFormation(GameState state) {
        int bonus = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // ✅ FIXED: Red clusters with moderate bonus
            if (state.redStackHeights[i] > 0) {
                int nearbyTowers = countNearbyTowers(state, i, true, 2);
                if (nearbyTowers >= 2) {
                    bonus += nearbyTowers * Tactical.CLUSTER_FORMATION_BONUS;  // 15 from EvaluationParameters

                    // ✅ FIXED: Moderate extra bonus for high towers in clusters
                    int height = state.redStackHeights[i];
                    if (height >= 3) {
                        bonus += Tactical.CLUSTER_FORMATION_BONUS / 3;  // 5 extra
                    }
                }
            }

            // ✅ FIXED: Blue clusters with moderate penalty
            if (state.blueStackHeights[i] > 0) {
                int nearbyTowers = countNearbyTowers(state, i, false, 2);
                if (nearbyTowers >= 2) {
                    bonus -= nearbyTowers * Tactical.CLUSTER_FORMATION_BONUS;

                    int height = state.blueStackHeights[i];
                    if (height >= 3) {
                        bonus -= Tactical.CLUSTER_FORMATION_BONUS / 3;
                    }
                }
            }
        }

        return bonus;
    }

    /**
     * ✅ FIXED: SUPPORTING ATTACKS - Moderate bonuses from EvaluationParameters
     */
    public int evaluateSupportingAttacks(GameState state) {
        int bonus = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            // ✅ FIXED: Red supported attacks with moderate bonus
            if (state.redStackHeights[i] > 0) {
                List<Integer> threats = findThreats(state, i, true);

                for (int threat : threats) {
                    int supporters = countSupportingThreats(state, threat, true);
                    if (supporters > 0) {
                        bonus += supporters * Tactical.SUPPORTING_ATTACK_BONUS;  // 10 from EvaluationParameters
                    }
                }
            }

            // ✅ FIXED: Blue supported attacks with moderate penalty
            if (state.blueStackHeights[i] > 0) {
                List<Integer> threats = findThreats(state, i, false);

                for (int threat : threats) {
                    int supporters = countSupportingThreats(state, threat, false);
                    if (supporters > 0) {
                        bonus -= supporters * Tactical.SUPPORTING_ATTACK_BONUS;
                    }
                }
            }
        }

        return bonus;
    }

    // === ✅ FIXED HELPER METHODS WITH MODERATE ASSESSMENTS ===

    private boolean canThreatenCenter(int pos, int height) {
        int[] centralSquares = {
                GameState.getIndex(2, 3), GameState.getIndex(3, 3), GameState.getIndex(4, 3),
                GameState.getIndex(3, 2), GameState.getIndex(3, 4)
        };

        for (int center : centralSquares) {
            if (canReachSquareSimple(pos, center, height)) {
                return true;
            }
        }
        return false;
    }

    private boolean canThreatenEnemyCastle(int pos, int height, boolean isRed) {
        int castle = isRed ? BLUE_CASTLE_INDEX : RED_CASTLE_INDEX;
        return calculateManhattanDistance(pos, castle) <= height + 1;
    }

    private int countNearbyTowers(GameState state, int pos, boolean isRed, int radius) {
        int count = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (i == pos) continue;

            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) {
                int distance = calculateManhattanDistance(pos, i);
                if (distance <= radius) {
                    count++;
                    if (count >= 4) break; // Cap for performance
                }
            }
        }

        return count;
    }

    private List<Integer> findThreats(GameState state, int pos, boolean isRed) {
        List<Integer> threats = new ArrayList<>();
        int height = isRed ? state.redStackHeights[pos] : state.blueStackHeights[pos];

        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            for (int dist = 1; dist <= height; dist++) {
                int target = pos + dir * dist;

                if (!GameState.isOnBoard(target)) break;
                if (isRankWrap(pos, target, dir)) break;

                // Enemy piece found?
                if (isRed && state.blueStackHeights[target] > 0) {
                    threats.add(target);
                    break;
                } else if (!isRed && state.redStackHeights[target] > 0) {
                    threats.add(target);
                    break;
                }

                // Enemy guard?
                long enemyGuard = isRed ? state.blueGuard : state.redGuard;
                if (enemyGuard != 0 && target == Long.numberOfTrailingZeros(enemyGuard)) {
                    threats.add(target);
                    break;
                }

                // Blockage?
                if (state.redStackHeights[target] > 0 || state.blueStackHeights[target] > 0) {
                    break;
                }
            }
        }

        return threats;
    }

    private int countSupportingThreats(GameState state, int target, boolean isRed) {
        int supporters = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0 && canReachSquareSimple(i, target, height)) {
                supporters++;
                if (supporters >= 3) break; // Cap for performance
            }
        }

        return Math.max(0, supporters - 1); // -1 because original attacker doesn't count
    }

    private boolean canReachSquareSimple(int from, int to, int range) {
        if (!isOnSameLine(from, to)) return false;
        int distance = calculateManhattanDistance(from, to);
        return distance <= range;
    }

    // === ✅ FIXED ORIGINAL IMPLEMENTATIONS WITH MODERATE BONUSES ===

    /**
     * ✅ FIXED: Evaluate cluster bonuses with moderate parameters from EvaluationParameters
     */
    public int evaluateClusterBonus(GameState state) {
        if (state == null) return 0;

        int clusterScore = 0;

        // Check red clusters (moderate)
        clusterScore += evaluateColorCluster(state, true);

        // Check blue clusters (moderate)
        clusterScore -= evaluateColorCluster(state, false);

        return clusterScore;
    }

    /**
     * ✅ FIXED: Evaluate cluster for one color with moderate bonuses
     */
    private int evaluateColorCluster(GameState state, boolean isRed) {
        int bonus = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height == 0) continue;

            // Check sight lines to other own towers
            int connectedTowers = countConnectedTowers(state, i, isRed);

            if (connectedTowers >= 1) {
                // ✅ FIXED: Moderate bonus for connection from EvaluationParameters
                bonus += Tactical.TOWER_COORDINATION_BONUS;  // 18 from EvaluationParameters

                // ✅ FIXED: Moderate height bonus for towers in chains
                bonus += height * connectedTowers * (Tactical.TOWER_COORDINATION_BONUS / 6);  // 3 per height per connection

                // ✅ FIXED: Moderate centrality bonus for clusters
                if (isCentralSquare(i)) {
                    bonus += Tactical.CLUSTER_BONUS;  // 12 from EvaluationParameters
                }

                // ✅ FIXED: Moderate bonus for clusters with 3+ towers
                if (connectedTowers >= 2) {
                    bonus += Tactical.CLUSTER_BONUS;  // 12 extra
                }
            }

            // ✅ FIXED: Moderate guard protection through tower cluster
            if (isGuardProtectedByCluster(state, i, isRed)) {
                bonus += Tactical.GUARD_PROTECTION_CLUSTER;  // 15 from EvaluationParameters
            }
        }

        return bonus;
    }

    /**
     * Count connected towers (that can "see" each other) - moderate count
     */
    private int countConnectedTowers(GameState state, int square, boolean isRed) {
        int connected = 0;
        int height = isRed ? state.redStackHeights[square] : state.blueStackHeights[square];

        // Check all 4 directions (orthogonal)
        int[] directions = {-1, 1, -7, 7}; // Left, Right, Up, Down

        for (int dir : directions) {
            for (int dist = 1; dist <= height; dist++) {
                int target = square + dir * dist;

                // Boundary check
                if (!isValidSquare(target, square, dir)) break;

                // Blockade check
                if (hasBlockingPiece(state, square, target)) break;

                // Own tower found?
                int targetHeight = isRed ? state.redStackHeights[target] : state.blueStackHeights[target];
                if (targetHeight > 0) {
                    connected++;
                    break; // Only nearest tower in this direction counts
                }
            }
        }

        return Math.min(connected, 3); // Cap to avoid excessive bonuses
    }

    /**
     * Check if guard is protected by tower cluster (moderate assessment)
     */
    private boolean isGuardProtectedByCluster(GameState state, int towerSquare, boolean isRed) {
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int distance = calculateManhattanDistance(towerSquare, guardPos);

        // Can tower reach guard?
        int towerHeight = isRed ? state.redStackHeights[towerSquare] : state.blueStackHeights[towerSquare];

        return distance <= towerHeight && canReachSquare(state, towerSquare, guardPos, towerHeight);
    }

    // === ✅ FIXED FORK DETECTION WITH MODERATE BONUSES ===

    /**
     * ✅ FIXED: Detect fork opportunities with moderate bonuses from EvaluationParameters
     */
    public int detectForks(GameState state) {
        int forkScore = 0;

        // ✅ FIXED: Check red forks with moderate bonus
        GameState redState = state.copy();
        redState.redToMove = true;
        List<Move> redMoves = MoveGenerator.generateAllMoves(redState);

        for (Move move : redMoves) {
            int threats = countThreatsFromMove(state, move, true);
            if (threats >= 2) {
                forkScore += Tactical.FORK_BONUS;  // 45 from EvaluationParameters
                if (threatsIncludeGuard(state, move, true)) {
                    forkScore += Tactical.FORK_BONUS / 2;  // 22-23 extra
                }
            }
        }

        // ✅ FIXED: Check blue forks with moderate penalty
        GameState blueState = state.copy();
        blueState.redToMove = false;
        List<Move> blueMoves = MoveGenerator.generateAllMoves(blueState);

        for (Move move : blueMoves) {
            int threats = countThreatsFromMove(state, move, false);
            if (threats >= 2) {
                forkScore -= Tactical.FORK_BONUS;
                if (threatsIncludeGuard(state, move, false)) {
                    forkScore -= Tactical.FORK_BONUS / 2;
                }
            }
        }

        return forkScore;
    }

    // === ✅ FIXED PIN DETECTION WITH MODERATE PENALTIES ===

    /**
     * ✅ FIXED: Detect pinned pieces with moderate penalties from EvaluationParameters
     */
    public int detectPins(GameState state) {
        int pinScore = 0;

        // ✅ FIXED: Check for pinned red pieces with moderate penalty
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            pinScore -= detectPinsForSide(state, guardPos, true);
        }

        // ✅ FIXED: Check for pinned blue pieces with moderate bonus
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
                    // ✅ FIXED: Moderate pin penalty from EvaluationParameters
                    pinValue += Tactical.PIN_BONUS + (pieceValue * (Tactical.PIN_BONUS / 3));  // 35 + ~12 per height
                }
            }
        }

        return pinValue;
    }

    // === ✅ FIXED TOWER CHAIN DETECTION WITH MODERATE BONUSES ===

    /**
     * ✅ FIXED: Detect tower chains with moderate bonuses from EvaluationParameters
     */
    public int detectTowerChains(GameState state) {
        int chainScore = 0;
        boolean[] visited = new boolean[GameState.NUM_SQUARES];

        // Find red chains (moderate bonuses)
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (!visited[i] && state.redStackHeights[i] > 0) {
                int chainLength = exploreChain(state, i, true, visited);
                if (chainLength >= 2) {
                    chainScore += Tactical.TOWER_COORDINATION_BONUS * chainLength / 2;  // Moderate: 9 per piece in chain
                }
            }
        }

        // Reset visited for blue
        visited = new boolean[GameState.NUM_SQUARES];

        // Find blue chains (moderate penalties)
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (!visited[i] && state.blueStackHeights[i] > 0) {
                int chainLength = exploreChain(state, i, false, visited);
                if (chainLength >= 2) {
                    chainScore -= Tactical.TOWER_COORDINATION_BONUS * chainLength / 2;
                }
            }
        }

        return chainScore;
    }

    // === ✅ FIXED FORCING MOVES WITH MODERATE BONUSES ===

    /**
     * ✅ FIXED: Evaluate forcing moves with moderate bonuses from EvaluationParameters
     */
    private int evaluateForcingMoves(GameState state) {
        int forcingScore = 0;

        List<Move> moves = MoveGenerator.generateAllMoves(state);
        int forcingMoves = 0;

        // Limit analysis for performance
        int analyzed = 0;
        for (Move move : moves) {
            if (analyzed++ > 15) break; // Reduced from 20 for better performance

            if (isForcingMove(move, state)) {
                forcingMoves++;
            }
        }

        // ✅ FIXED: Moderate bonus for having multiple forcing moves
        forcingScore = forcingMoves * Tactical.FORCING_MOVE_BONUS;  // 20 from EvaluationParameters

        return state.redToMove ? forcingScore : -forcingScore;
    }

    /**
     * Check if a move is forcing (moderate criteria)
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

        return Math.min(threats, 4); // Cap threats to avoid excessive values
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

        int targetCastle = state.redToMove ? BLUE_CASTLE_INDEX : RED_CASTLE_INDEX;

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

    // === MORE HELPER METHODS ===

    private boolean isValidSquare(int target, int from, int direction) {
        if (target < 0 || target >= GameState.NUM_SQUARES) return false;

        int fromFile = GameState.file(from);
        int targetFile = GameState.file(target);

        // Horizontal movement: same rank
        if (Math.abs(direction) == 1) {
            return GameState.rank(from) == GameState.rank(target);
        }
        // Vertical movement: same file
        else {
            return fromFile == targetFile;
        }
    }

    private boolean hasBlockingPiece(GameState state, int from, int to) {
        // Simplified blockade check
        int direction = getDirection(from, to);
        if (direction == 0) return false;

        int current = from + direction;
        while (current != to && GameState.isOnBoard(current)) {
            if (state.redStackHeights[current] > 0 || state.blueStackHeights[current] > 0) {
                return true;
            }
            current += direction;
        }
        return false;
    }

    private boolean isCentralSquare(int square) {
        int file = GameState.file(square);
        int rank = GameState.rank(square);
        return file >= 2 && file <= 4 && rank >= 2 && rank <= 4;
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
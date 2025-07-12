package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;

import java.util.List;

/**
 * SAFETY EVALUATION COMPONENT - Complete Implementation
 *
 * Handles guard safety, threat analysis, and defensive evaluation.
 * Integrates with ThreatMap for advanced threat detection.
 */
public class SafetyEval {

    // === SAFETY CONSTANTS ===
    private static final int GUARD_DANGER_PENALTY = 600;
    private static final int GUARD_PROTECTION_BONUS = 150;
    private static final int ESCAPE_ROUTE_BONUS = 80;
    private static final int DEFENDER_BONUS = 50;
    private static final int PIN_PENALTY = 250;
    private static final int FORK_PENALTY = 300;
    private static final int OVERLOADED_DEFENDER_PENALTY = 150;

    // === CASTLE INDICES ===
    private static final int RED_CASTLE_INDEX = GameState.getIndex(6, 3); // D7
    private static final int BLUE_CASTLE_INDEX = GameState.getIndex(0, 3); // D1

    // === THREAT MAP (passed from ModularEvaluator) ===
    private ThreatMap threatMap = null;

    /**
     * Set threat map for integration
     */
    public void setThreatMap(ThreatMap threatMap) {
        this.threatMap = threatMap;
    }

    /**
     * COMPREHENSIVE GUARD SAFETY EVALUATION
     */
    public int evaluateGuardSafety(GameState state) {
        int safetyScore = 0;

        // Basic danger assessment
        safetyScore += evaluateGuardSafetyBasic(state);

        // Escape route analysis
        safetyScore += evaluateEscapeRoutes(state);

        // Protection and support evaluation
        safetyScore += evaluateGuardProtection(state);

        // Tactical vulnerabilities
        safetyScore += evaluateTacticalVulnerabilities(state);

        return safetyScore;
    }

    /**
     * BASIC GUARD SAFETY - Fast evaluation
     */
    public int evaluateGuardSafetyBasic(GameState state) {
        int safetyScore = 0;

        // Check if guards are in immediate danger
        if (isGuardInDanger(state, true)) {
            safetyScore -= GUARD_DANGER_PENALTY;
        }
        if (isGuardInDanger(state, false)) {
            safetyScore += GUARD_DANGER_PENALTY;
        }

        return safetyScore;
    }

    /**
     * ADVANCED GUARD SAFETY - Deep analysis
     */
    public int evaluateGuardSafetyAdvanced(GameState state) {
        // Full safety evaluation
        return evaluateGuardSafety(state);
    }

    /**
     * Check if a guard is in immediate danger
     */
    public boolean isGuardInDanger(GameState state, boolean checkRed) {
        long guardBit = checkRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);

        // Check with threat map if available
        if (threatMap != null && threatMap.hasImmediateThreat(guardPos, !checkRed)) {
            return true;
        }

        // Fallback to traditional check
        GameState simState = state.copy();
        simState.redToMove = !checkRed;
        List<Move> enemyMoves = MoveGenerator.generateAllMoves(simState);

        for (Move move : enemyMoves) {
            if (move.to == guardPos) {
                return true;
            }
        }

        return false;
    }

    /**
     * Count total threats in position
     */
    public int countThreats(GameState state) {
        int threats = 0;

        // Count threats to all pieces
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                if (isSquareUnderAttack(state, i, false)) {
                    threats -= state.redStackHeights[i];
                }
            }
            if (state.blueStackHeights[i] > 0) {
                if (isSquareUnderAttack(state, i, true)) {
                    threats += state.blueStackHeights[i];
                }
            }
        }

        return threats;
    }

    // === ESCAPE ROUTE ANALYSIS ===

    private int evaluateEscapeRoutes(GameState state) {
        int escapeScore = 0;

        // Red guard escape routes
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int escapeRoutes = countEscapeSquares(state, guardPos, true);
            escapeScore += escapeRoutes * ESCAPE_ROUTE_BONUS;
        }

        // Blue guard escape routes
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int escapeRoutes = countEscapeSquares(state, guardPos, false);
            escapeScore -= escapeRoutes * ESCAPE_ROUTE_BONUS;
        }

        return escapeScore;
    }

    private int countEscapeSquares(GameState state, int guardPos, boolean isRed) {
        int escapeRoutes = 0;
        int[] directions = {-1, 1, -7, 7}; // left, right, up, down

        for (int dir : directions) {
            int targetSquare = guardPos + dir;

            if (!GameState.isOnBoard(targetSquare)) continue;
            if (isRankWrap(guardPos, targetSquare, dir)) continue;

            // Check if square is empty and safe
            boolean isEmpty = state.redStackHeights[targetSquare] == 0 &&
                    state.blueStackHeights[targetSquare] == 0;

            if (isEmpty && !isSquareUnderAttack(state, targetSquare, !isRed)) {
                escapeRoutes++;
            }
        }

        return escapeRoutes;
    }

    // === GUARD PROTECTION ===

    private int evaluateGuardProtection(GameState state) {
        int protectionScore = 0;

        // Red guard protection
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int protectors = countGuardProtectors(state, guardPos, true);
            protectionScore += protectors * DEFENDER_BONUS;
        }

        // Blue guard protection
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int protectors = countGuardProtectors(state, guardPos, false);
            protectionScore -= protectors * DEFENDER_BONUS;
        }

        return protectionScore;
    }

    private int countGuardProtectors(GameState state, int guardPos, boolean isRed) {
        int protectors = 0;

        // Check all friendly pieces
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((isRed && state.redStackHeights[i] > 0) ||
                    (!isRed && state.blueStackHeights[i] > 0)) {

                if (canProtectSquare(state, i, guardPos, isRed)) {
                    protectors++;
                }
            }
        }

        return protectors;
    }

    // === TACTICAL VULNERABILITIES ===

    private int evaluateTacticalVulnerabilities(GameState state) {
        int vulnerabilityScore = 0;

        // Pin detection
        vulnerabilityScore += evaluatePins(state);

        // Fork detection
        vulnerabilityScore += evaluateForks(state);

        // Overloaded defenders
        vulnerabilityScore += evaluateOverloadedDefenders(state);

        return vulnerabilityScore;
    }

    /**
     * Detect pinned pieces
     */
    private int evaluatePins(GameState state) {
        int pinScore = 0;

        // Check red pieces for pins
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);

            for (int i = 0; i < GameState.NUM_SQUARES; i++) {
                if (state.redStackHeights[i] > 0 && isPiecePinned(state, i, guardPos, true)) {
                    pinScore -= PIN_PENALTY;
                }
            }
        }

        // Check blue pieces for pins
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);

            for (int i = 0; i < GameState.NUM_SQUARES; i++) {
                if (state.blueStackHeights[i] > 0 && isPiecePinned(state, i, guardPos, false)) {
                    pinScore += PIN_PENALTY;
                }
            }
        }

        return pinScore;
    }

    /**
     * Evaluate fork threats
     */
    private int evaluateForks(GameState state) {
        int forkScore = 0;

        // Check each side's fork vulnerabilities
        GameState testState = state.copy();
        testState.redToMove = true;
        List<Move> redMoves = MoveGenerator.generateAllMoves(testState);

        for (Move move : redMoves) {
            int threats = countThreatsFromSquare(state, move.to, move.amountMoved, true);
            if (threats >= 2) {
                forkScore += FORK_PENALTY;
            }
        }

        testState.redToMove = false;
        List<Move> blueMoves = MoveGenerator.generateAllMoves(testState);

        for (Move move : blueMoves) {
            int threats = countThreatsFromSquare(state, move.to, move.amountMoved, false);
            if (threats >= 2) {
                forkScore -= FORK_PENALTY;
            }
        }

        return forkScore;
    }

    /**
     * Evaluate overloaded defenders
     */
    private int evaluateOverloadedDefenders(GameState state) {
        int overloadScore = 0;

        // Check each piece to see if it's defending multiple targets
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                int defendedTargets = countDefendedTargets(state, i, true);
                if (defendedTargets >= 2) {
                    overloadScore -= OVERLOADED_DEFENDER_PENALTY * (defendedTargets - 1);
                }
            }
            if (state.blueStackHeights[i] > 0) {
                int defendedTargets = countDefendedTargets(state, i, false);
                if (defendedTargets >= 2) {
                    overloadScore += OVERLOADED_DEFENDER_PENALTY * (defendedTargets - 1);
                }
            }
        }

        return overloadScore;
    }

    // === HELPER METHODS ===

    private boolean isSquareUnderAttack(GameState state, int square, boolean byRed) {
        // Use threat map if available
        if (threatMap != null) {
            return threatMap.hasImmediateThreat(square, byRed);
        }

        // Fallback to manual check
        GameState simState = state.copy();
        simState.redToMove = byRed;
        List<Move> moves = MoveGenerator.generateAllMoves(simState);

        for (Move move : moves) {
            if (move.to == square) {
                return true;
            }
        }

        return false;
    }

    private boolean canProtectSquare(GameState state, int piecePos, int targetSquare, boolean isRed) {
        if (piecePos == targetSquare) return false;

        int movementRange = isRed ? state.redStackHeights[piecePos] : state.blueStackHeights[piecePos];
        if (movementRange <= 0) return false;

        // Check if piece can reach adjacent squares to target
        int[] directions = {-1, 1, -7, 7};
        for (int dir : directions) {
            int defenseSquare = targetSquare + dir;
            if (!GameState.isOnBoard(defenseSquare)) continue;
            if (isRankWrap(targetSquare, defenseSquare, dir)) continue;

            int distance = calculateManhattanDistance(piecePos, defenseSquare);
            if (distance <= movementRange && isPathClear(state, piecePos, defenseSquare)) {
                return true;
            }
        }

        return false;
    }

    private boolean isPiecePinned(GameState state, int piecePos, int guardPos, boolean isRed) {
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

    private int countThreatsFromSquare(GameState state, int square, int moveRange, boolean byRed) {
        int threats = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            for (int dist = 1; dist <= moveRange; dist++) {
                int target = square + dir * dist;

                if (!GameState.isOnBoard(target)) break;
                if (isRankWrap(square, target, dir)) break;

                // Check for enemy pieces
                if (byRed) {
                    if (state.blueStackHeights[target] > 0) threats++;
                    if (state.blueGuard != 0 && target == Long.numberOfTrailingZeros(state.blueGuard)) {
                        threats += 2;
                    }
                } else {
                    if (state.redStackHeights[target] > 0) threats++;
                    if (state.redGuard != 0 && target == Long.numberOfTrailingZeros(state.redGuard)) {
                        threats += 2;
                    }
                }

                // Stop if path blocked
                if (state.redStackHeights[target] > 0 || state.blueStackHeights[target] > 0) break;
            }
        }

        return threats;
    }

    private int countDefendedTargets(GameState state, int defenderPos, boolean isRed) {
        int defendedCount = 0;
        int range = isRed ? state.redStackHeights[defenderPos] : state.blueStackHeights[defenderPos];

        // Check if defending the guard
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit != 0) {
            int guardPos = Long.numberOfTrailingZeros(guardBit);
            if (canProtectSquare(state, defenderPos, guardPos, isRed)) {
                defendedCount += 2;
            }
        }

        // Check if defending other pieces
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (i == defenderPos) continue;

            if ((isRed && state.redStackHeights[i] > 0) ||
                    (!isRed && state.blueStackHeights[i] > 0)) {

                if (canProtectSquare(state, defenderPos, i, isRed)) {
                    defendedCount++;
                }
            }
        }

        return defendedCount;
    }

    // === UTILITY METHODS ===

    private boolean isRankWrap(int from, int to, int direction) {
        int fromRank = GameState.rank(from);
        int toRank = GameState.rank(to);

        if (direction == -1 || direction == 1) {
            return fromRank != toRank;
        }

        return false;
    }

    private int calculateManhattanDistance(int from, int to) {
        int fromRank = GameState.rank(from);
        int fromFile = GameState.file(from);
        int toRank = GameState.rank(to);
        int toFile = GameState.file(to);

        return Math.abs(fromRank - toRank) + Math.abs(fromFile - toFile);
    }

    private boolean isPathClear(GameState state, int from, int to) {
        if (!isOnSameLine(from, to)) return false;

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
}
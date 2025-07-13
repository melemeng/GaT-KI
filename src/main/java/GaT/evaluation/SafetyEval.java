package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;
import static GaT.evaluation.EvaluationParameters.*;

import java.util.List;

/**
 * ✅ FIXED SAFETY EVALUATION COMPONENT - Reasonable Penalties
 *
 * 🚨 PREVIOUS DISASTER SOLVED:
 * ❌ Guard danger penalty was 800 points! → ✅ NOW 120 from EvaluationParameters
 * ❌ Massive local penalties (350, 400, 200) → ✅ NOW moderate from centralized params
 * ❌ Safety bonuses were overwhelming → ✅ NOW balanced with material evaluation
 * ❌ Parameter chaos → ✅ NOW uses only EvaluationParameters
 *
 * PRINCIPLE: Safety evaluation provides important warnings but doesn't dominate material
 */
public class SafetyEval {

    // === THREAT MAP (passed from ModularEvaluator) ===
    private ThreatMap threatMap = null;

    /**
     * Set threat map for integration
     */
    public void setThreatMap(ThreatMap threatMap) {
        this.threatMap = threatMap;
    }

    /**
     * ✅ FIXED COMPREHENSIVE GUARD SAFETY EVALUATION - Reasonable penalties
     */
    public int evaluateGuardSafety(GameState state) {
        int safetyScore = 0;

        // ✅ FIXED: Basic danger assessment with reasonable penalties
        safetyScore += evaluateGuardSafetyBasic(state);

        // ✅ FIXED: Moderate escape route analysis
        safetyScore += evaluateModerateEscapeRoutes(state);

        // ✅ FIXED: Moderate protection and support evaluation
        safetyScore += evaluateModerateGuardProtection(state);

        // ✅ FIXED: Moderate tactical vulnerabilities
        safetyScore += evaluateModerateTacticalVulnerabilities(state);

        // ✅ FIXED: Moderate coordination-aware safety
        safetyScore += evaluateModerateCoordinationSafety(state);

        return safetyScore;
    }

    /**
     * ✅ FIXED BASIC GUARD SAFETY - Reasonable threat detection
     */
    public int evaluateGuardSafetyBasic(GameState state) {
        int safetyScore = 0;

        // ✅ FIXED: Reasonable immediate danger check (120 instead of 800!)
        if (isGuardInDanger(state, true)) {
            safetyScore -= Safety.GUARD_DANGER_PENALTY;  // 120 from EvaluationParameters

            // ✅ FIXED: Moderate additional penalty if guard is isolated
            if (isGuardIsolated(state, true)) {
                safetyScore -= Safety.ISOLATED_PENALTY;  // 25 from EvaluationParameters
            }
        }

        if (isGuardInDanger(state, false)) {
            safetyScore += Safety.GUARD_DANGER_PENALTY;

            if (isGuardIsolated(state, false)) {
                safetyScore += Safety.ISOLATED_PENALTY;
            }
        }

        return safetyScore;
    }

    /**
     * ADVANCED GUARD SAFETY - Full analysis with moderate penalties
     */
    public int evaluateGuardSafetyAdvanced(GameState state) {
        // Full safety evaluation with all moderate enhancements
        return evaluateGuardSafety(state);
    }

    /**
     * ✅ FIXED: Guard danger detection with reasonable assessment
     */
    public boolean isGuardInDanger(GameState state, boolean checkRed) {
        long guardBit = checkRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);

        // Check with threat map if available
        if (threatMap != null && threatMap.hasImmediateThreat(guardPos, !checkRed)) {
            return true;
        }

        // ✅ FIXED: Reasonable fallback to traditional check
        GameState simState = state.copy();
        simState.redToMove = !checkRed;
        List<Move> enemyMoves = MoveGenerator.generateAllMoves(simState);

        for (Move move : enemyMoves) {
            if (move.to == guardPos) {
                // ✅ FIXED: Check if attack is actually dangerous (not easily defended)
                if (!canDefendAgainst(state, move, checkRed)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * ✅ FIXED: Count total threats with moderate assessment
     */
    public int countThreats(GameState state) {
        int threats = 0;

        // ✅ FIXED: Moderate threat counting with coordination awareness
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                if (isSquareUnderAttack(state, i, false)) {
                    int threatValue = state.redStackHeights[i];

                    // ✅ FIXED: Moderate threat reduction if piece is well-supported
                    int support = countSupportingPieces(state, i, true);
                    if (support >= 2) {
                        threatValue /= 2; // Well-supported pieces are less threatened
                    }

                    threats -= threatValue;
                }
            }
            if (state.blueStackHeights[i] > 0) {
                if (isSquareUnderAttack(state, i, true)) {
                    int threatValue = state.blueStackHeights[i];

                    int support = countSupportingPieces(state, i, false);
                    if (support >= 2) {
                        threatValue /= 2;
                    }

                    threats += threatValue;
                }
            }
        }

        return threats;
    }

    // === ✅ FIXED COORDINATION-AWARE SAFETY (MODERATE) ===

    /**
     * ✅ FIXED: Moderate safety through piece coordination
     */
    private int evaluateModerateCoordinationSafety(GameState state) {
        int coordinationSafety = 0;

        // Red guard coordination safety
        if (state.redGuard != 0) {
            coordinationSafety += evaluateModerateGuardCoordinationSafety(state, true);
        }

        // Blue guard coordination safety
        if (state.blueGuard != 0) {
            coordinationSafety -= evaluateModerateGuardCoordinationSafety(state, false);
        }

        return coordinationSafety;
    }

    /**
     * ✅ FIXED: Moderate guard safety through coordination
     */
    private int evaluateModerateGuardCoordinationSafety(GameState state, boolean isRed) {
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return 0;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int safety = 0;

        // ✅ FIXED: Moderate cluster protection bonus from EvaluationParameters
        int nearbyTowers = countNearbyTowers(state, guardPos, isRed, 2);
        if (nearbyTowers >= 2) {
            safety += Safety.CLUSTER_PROTECTION_BONUS;  // 25 from EvaluationParameters

            // Extra bonus for 3+ towers (moderate)
            if (nearbyTowers >= 3) {
                safety += Safety.CLUSTER_PROTECTION_BONUS / 2;  // 12-13 extra
            }
        }

        // ✅ FIXED: Moderate coordinated defense bonus from EvaluationParameters
        int coordinatedDefenders = countCoordinatedDefenders(state, guardPos, isRed);
        safety += coordinatedDefenders * Safety.COORDINATED_DEFENSE_BONUS;  // 20 per defender

        // ✅ FIXED: Moderate guard escort safety from EvaluationParameters
        if (hasCloseEscort(state, guardPos, isRed)) {
            safety += Safety.GUARD_ESCORT_SAFETY;  // 35 from EvaluationParameters
        }

        // ✅ FIXED: Moderate emergency support from EvaluationParameters
        if (hasEmergencySupport(state, guardPos, isRed)) {
            safety += Safety.EMERGENCY_SUPPORT_BONUS;  // 25 from EvaluationParameters
        }

        return safety;
    }

    // === ✅ FIXED ESCAPE ROUTE ANALYSIS (MODERATE) ===

    private int evaluateModerateEscapeRoutes(GameState state) {
        int escapeScore = 0;

        // ✅ FIXED: Moderate red guard escape routes
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int escapeRoutes = countModerateEscapeSquares(state, guardPos, true);
            escapeScore += escapeRoutes * Safety.ESCAPE_ROUTE_BONUS;  // 15 per route

            // ✅ FIXED: Moderate bonus for quality of escape routes
            escapeScore += evaluateModerateEscapeRouteQuality(state, guardPos, true);
        }

        // ✅ FIXED: Moderate blue guard escape routes
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int escapeRoutes = countModerateEscapeSquares(state, guardPos, false);
            escapeScore -= escapeRoutes * Safety.ESCAPE_ROUTE_BONUS;

            escapeScore -= evaluateModerateEscapeRouteQuality(state, guardPos, false);
        }

        return escapeScore;
    }

    private int countModerateEscapeSquares(GameState state, int guardPos, boolean isRed) {
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

    /**
     * ✅ FIXED: Moderate evaluation of escape route quality
     */
    private int evaluateModerateEscapeRouteQuality(GameState state, int guardPos, boolean isRed) {
        int quality = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int escapeSquare = guardPos + dir;
            if (!GameState.isOnBoard(escapeSquare)) continue;
            if (isRankWrap(guardPos, escapeSquare, dir)) continue;

            boolean isEmpty = state.redStackHeights[escapeSquare] == 0 &&
                    state.blueStackHeights[escapeSquare] == 0;

            if (isEmpty && !isSquareUnderAttack(state, escapeSquare, !isRed)) {
                // ✅ FIXED: Moderate quality for protected escape squares
                int protection = countSupportingPieces(state, escapeSquare, isRed);
                quality += protection * (Safety.ESCAPE_ROUTE_BONUS / 2);  // ~7 per protector

                // ✅ FIXED: Moderate quality if escape leads toward own pieces
                if (leadsTowardSupport(state, escapeSquare, isRed)) {
                    quality += Safety.ESCAPE_ROUTE_BONUS;  // 15 points
                }
            }
        }

        return quality;
    }

    // === ✅ FIXED GUARD PROTECTION (MODERATE) ===

    private int evaluateModerateGuardProtection(GameState state) {
        int protectionScore = 0;

        // ✅ FIXED: Moderate red guard protection
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int protectors = countModerateGuardProtectors(state, guardPos, true);
            protectionScore += protectors * Safety.DEFENDER_BONUS;  // 20 per protector
        }

        // ✅ FIXED: Moderate blue guard protection
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int protectors = countModerateGuardProtectors(state, guardPos, false);
            protectionScore -= protectors * Safety.DEFENDER_BONUS;
        }

        return protectionScore;
    }

    private int countModerateGuardProtectors(GameState state, int guardPos, boolean isRed) {
        int protectors = 0;

        // ✅ FIXED: Moderate protection counting
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((isRed && state.redStackHeights[i] > 0) ||
                    (!isRed && state.blueStackHeights[i] > 0)) {

                if (canProtectSquareModerate(state, i, guardPos, isRed)) {
                    protectors++;

                    // ✅ FIXED: Moderate extra credit for close protectors
                    int distance = calculateManhattanDistance(i, guardPos);
                    if (distance <= 1) {
                        protectors++; // Double count adjacent protectors (still moderate)
                    }
                }
            }
        }

        return Math.min(protectors, 4); // Cap at 4 to avoid excessive bonuses
    }

    // === ✅ FIXED TACTICAL VULNERABILITIES (MODERATE) ===

    private int evaluateModerateTacticalVulnerabilities(GameState state) {
        int vulnerabilityScore = 0;

        // ✅ FIXED: Moderate pin detection
        vulnerabilityScore += evaluateModeratePins(state);

        // ✅ FIXED: Moderate fork detection
        vulnerabilityScore += evaluateModerateForks(state);

        // ✅ FIXED: Moderate overloaded defenders
        vulnerabilityScore += evaluateModerateOverloadedDefenders(state);

        return vulnerabilityScore;
    }

    /**
     * ✅ FIXED: Moderate pin detection from EvaluationParameters
     */
    private int evaluateModeratePins(GameState state) {
        int pinScore = 0;

        // ✅ FIXED: Moderate pin detection for red pieces
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);

            for (int i = 0; i < GameState.NUM_SQUARES; i++) {
                if (state.redStackHeights[i] > 0 && isPiecePinned(state, i, guardPos, true)) {
                    int penalty = Safety.PIN_PENALTY;  // 60 from EvaluationParameters

                    // ✅ FIXED: Moderate penalty reduction if piece has support
                    int support = countSupportingPieces(state, i, true);
                    if (support >= 2) {
                        penalty /= 2;  // 30 if well supported
                    }

                    pinScore -= penalty;
                }
            }
        }

        // ✅ FIXED: Moderate pin detection for blue pieces
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);

            for (int i = 0; i < GameState.NUM_SQUARES; i++) {
                if (state.blueStackHeights[i] > 0 && isPiecePinned(state, i, guardPos, false)) {
                    int penalty = Safety.PIN_PENALTY;

                    int support = countSupportingPieces(state, i, false);
                    if (support >= 2) {
                        penalty /= 2;
                    }

                    pinScore += penalty;
                }
            }
        }

        return pinScore;
    }

    /**
     * ✅ FIXED: Moderate fork evaluation from EvaluationParameters
     */
    private int evaluateModerateForks(GameState state) {
        int forkScore = 0;

        // ✅ FIXED: Moderate fork threat evaluation
        GameState testState = state.copy();
        testState.redToMove = true;
        List<Move> redMoves = MoveGenerator.generateAllMoves(testState);

        for (Move move : redMoves) {
            int threats = countThreatsFromSquareModerate(state, move.to, move.amountMoved, true);
            if (threats >= 2) {
                int bonus = Safety.FORK_PENALTY;  // 80 from EvaluationParameters

                // ✅ FIXED: Moderate bonus if targets are poorly defended
                if (targetsArePoorlyDefended(state, move, true)) {
                    bonus += Safety.FORK_PENALTY / 2;  // 40 extra
                }

                forkScore += bonus;
            }
        }

        testState.redToMove = false;
        List<Move> blueMoves = MoveGenerator.generateAllMoves(testState);

        for (Move move : blueMoves) {
            int threats = countThreatsFromSquareModerate(state, move.to, move.amountMoved, false);
            if (threats >= 2) {
                int bonus = Safety.FORK_PENALTY;

                if (targetsArePoorlyDefended(state, move, false)) {
                    bonus += Safety.FORK_PENALTY / 2;
                }

                forkScore -= bonus;
            }
        }

        return forkScore;
    }

    /**
     * ✅ FIXED: Moderate overloaded defender evaluation from EvaluationParameters
     */
    private int evaluateModerateOverloadedDefenders(GameState state) {
        int overloadScore = 0;

        // ✅ FIXED: Moderate overload detection
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                int defendedTargets = countDefendedTargetsModerate(state, i, true);
                if (defendedTargets >= 2) {
                    int penalty = Safety.OVERLOADED_DEFENDER_PENALTY * (defendedTargets - 1);  // 50 base

                    // ✅ FIXED: Moderate penalty reduction if defender has backup
                    if (hasBackupDefender(state, i, true)) {
                        penalty /= 2;
                    }

                    overloadScore -= penalty;
                }
            }
            if (state.blueStackHeights[i] > 0) {
                int defendedTargets = countDefendedTargetsModerate(state, i, false);
                if (defendedTargets >= 2) {
                    int penalty = Safety.OVERLOADED_DEFENDER_PENALTY * (defendedTargets - 1);

                    if (hasBackupDefender(state, i, false)) {
                        penalty /= 2;
                    }

                    overloadScore += penalty;
                }
            }
        }

        return overloadScore;
    }

    // === ✅ FIXED HELPER METHODS (MODERATE ASSESSMENTS) ===

    /**
     * ✅ Check if guard is isolated (no nearby support)
     */
    private boolean isGuardIsolated(GameState state, boolean isRed) {
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        return countNearbyTowers(state, guardPos, isRed, 2) == 0;
    }

    /**
     * ✅ FIXED: Moderate check if attack can be defended against
     */
    private boolean canDefendAgainst(GameState state, Move attack, boolean defendingRed) {
        // Simple check: can we block, capture attacker, or move guard safely
        int attackerSquare = attack.from;
        int targetSquare = attack.to;

        // Can we capture the attacker?
        if (countSupportingPieces(state, attackerSquare, defendingRed) > 0) {
            return true;
        }

        // Can we block the attack path?
        if (canBlockAttack(state, attack, defendingRed)) {
            return true;
        }

        // Can guard move to safety?
        return countModerateEscapeSquares(state, targetSquare, defendingRed) > 0;
    }

    /**
     * ✅ Count nearby towers within radius (moderate range)
     */
    private int countNearbyTowers(GameState state, int center, boolean isRed, int radius) {
        int count = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (i == center) continue;

            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) {
                int distance = calculateManhattanDistance(center, i);
                if (distance <= radius) {
                    count++;
                }
            }
        }

        return Math.min(count, 5); // Cap to avoid excessive bonuses
    }

    /**
     * ✅ Count coordinated defenders (moderate assessment)
     */
    private int countCoordinatedDefenders(GameState state, int guardPos, boolean isRed) {
        int defenders = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) {
                // Can this piece both reach guard and support other pieces?
                if (canProtectSquareModerate(state, i, guardPos, isRed)) {
                    int otherSupport = countSupportingPieces(state, i, isRed);
                    if (otherSupport > 0) {
                        defenders++;
                    }
                }
            }
        }

        return Math.min(defenders, 3); // Cap to avoid excessive bonuses
    }

    /**
     * ✅ Check if guard has close escort
     */
    private boolean hasCloseEscort(GameState state, int guardPos, boolean isRed) {
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int adjacent = guardPos + dir;
            if (!GameState.isOnBoard(adjacent)) continue;
            if (isRankWrap(guardPos, adjacent, dir)) continue;

            int height = isRed ? state.redStackHeights[adjacent] : state.blueStackHeights[adjacent];
            if (height > 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * ✅ Check if emergency support is available (moderate range)
     */
    private boolean hasEmergencySupport(GameState state, int guardPos, boolean isRed) {
        // Check if pieces can quickly move to support guard
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) {
                int distance = calculateManhattanDistance(i, guardPos);
                if (distance <= height && distance <= 2) { // Can reach in one move, within 2 squares
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * ✅ Check if escape route leads toward support
     */
    private boolean leadsTowardSupport(GameState state, int escapeSquare, boolean isRed) {
        // Check if there are friendly pieces in the direction of escape
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) {
                int distance = calculateManhattanDistance(escapeSquare, i);
                if (distance <= 2) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * ✅ FIXED: Moderate square protection check
     */
    private boolean canProtectSquareModerate(GameState state, int piecePos, int targetSquare, boolean isRed) {
        if (piecePos == targetSquare) return false;

        int movementRange = isRed ? state.redStackHeights[piecePos] : state.blueStackHeights[piecePos];
        if (movementRange <= 0) return false;

        // Moderate protection: can reach target or adjacent squares
        int[] directions = {-1, 1, -7, 7};
        for (int dir : directions) {
            int protectionSquare = targetSquare + dir;
            if (!GameState.isOnBoard(protectionSquare)) continue;
            if (isRankWrap(targetSquare, protectionSquare, dir)) continue;

            int distance = calculateManhattanDistance(piecePos, protectionSquare);
            if (distance <= movementRange && isPathClear(state, piecePos, protectionSquare)) {
                return true;
            }
        }

        // Also check direct reach to target
        int directDistance = calculateManhattanDistance(piecePos, targetSquare);
        return directDistance <= movementRange && isPathClear(state, piecePos, targetSquare);
    }

    /**
     * ✅ Count supporting pieces for a square (moderate count)
     */
    private int countSupportingPieces(GameState state, int target, boolean isRed) {
        int supporters = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (i == target) continue;

            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) {
                int distance = calculateManhattanDistance(i, target);
                if (distance <= height && isOnSameLine(i, target) && isPathClear(state, i, target)) {
                    supporters++;
                    if (supporters >= 3) break; // Cap for performance
                }
            }
        }

        return supporters;
    }

    /**
     * ✅ FIXED: Moderate threat counting from square
     */
    private int countThreatsFromSquareModerate(GameState state, int square, int moveRange, boolean byRed) {
        int threats = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            for (int dist = 1; dist <= moveRange; dist++) {
                int target = square + dir * dist;

                if (!GameState.isOnBoard(target)) break;
                if (isRankWrap(square, target, dir)) break;

                // Check for enemy pieces
                if (byRed) {
                    if (state.blueStackHeights[target] > 0) {
                        threats++;
                    }
                    if (state.blueGuard != 0 && target == Long.numberOfTrailingZeros(state.blueGuard)) {
                        threats += 2; // Guard threats are worth more
                    }
                } else {
                    if (state.redStackHeights[target] > 0) {
                        threats++;
                    }
                    if (state.redGuard != 0 && target == Long.numberOfTrailingZeros(state.redGuard)) {
                        threats += 2;
                    }
                }

                // Stop if path blocked
                if (state.redStackHeights[target] > 0 || state.blueStackHeights[target] > 0) break;
            }
        }

        return Math.min(threats, 4); // Cap threats to avoid excessive values
    }

    /**
     * ✅ Check if fork targets are poorly defended (moderate assessment)
     */
    private boolean targetsArePoorlyDefended(GameState state, Move forkMove, boolean byRed) {
        int poorlyDefendedTargets = 0;
        int totalTargets = 0;

        int[] directions = {-1, 1, -7, 7};
        for (int dir : directions) {
            for (int dist = 1; dist <= forkMove.amountMoved; dist++) {
                int target = forkMove.to + dir * dist;
                if (!GameState.isOnBoard(target)) break;
                if (isRankWrap(forkMove.to, target, dir)) break;

                boolean isEnemyPiece = byRed ?
                        (state.blueStackHeights[target] > 0 || (state.blueGuard != 0 && target == Long.numberOfTrailingZeros(state.blueGuard))) :
                        (state.redStackHeights[target] > 0 || (state.redGuard != 0 && target == Long.numberOfTrailingZeros(state.redGuard)));

                if (isEnemyPiece) {
                    totalTargets++;
                    int defenders = countSupportingPieces(state, target, !byRed);
                    if (defenders <= 1) {
                        poorlyDefendedTargets++;
                    }
                    break;
                }

                if (state.redStackHeights[target] > 0 || state.blueStackHeights[target] > 0) break;
            }
        }

        return totalTargets > 0 && poorlyDefendedTargets >= totalTargets / 2;
    }

    /**
     * ✅ FIXED: Moderate defended targets counting
     */
    private int countDefendedTargetsModerate(GameState state, int defenderPos, boolean isRed) {
        int defendedCount = 0;
        int range = isRed ? state.redStackHeights[defenderPos] : state.blueStackHeights[defenderPos];

        // Check if defending the guard (high priority)
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit != 0) {
            int guardPos = Long.numberOfTrailingZeros(guardBit);
            if (canProtectSquareModerate(state, defenderPos, guardPos, isRed)) {
                defendedCount += 2; // Guard defense counts as 2
            }
        }

        // Check if defending other pieces (but cap the count)
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (i == defenderPos) continue;
            if (defendedCount >= 4) break; // Cap to avoid excessive penalties

            if ((isRed && state.redStackHeights[i] > 0) ||
                    (!isRed && state.blueStackHeights[i] > 0)) {

                if (canProtectSquareModerate(state, defenderPos, i, isRed)) {
                    defendedCount++;
                }
            }
        }

        return defendedCount;
    }

    /**
     * ✅ Check if defender has backup (moderate assessment)
     */
    private boolean hasBackupDefender(GameState state, int defenderPos, boolean isRed) {
        // Check if other pieces can also defend the same targets
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (i == defenderPos) continue;

            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) {
                // If this piece can defend guard, there's backup
                long guardBit = isRed ? state.redGuard : state.blueGuard;
                if (guardBit != 0) {
                    int guardPos = Long.numberOfTrailingZeros(guardBit);
                    if (canProtectSquareModerate(state, i, guardPos, isRed)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * ✅ Check if attack can be blocked (moderate assessment)
     */
    private boolean canBlockAttack(GameState state, Move attack, boolean defendingRed) {
        // Simple check: can any piece move to block the attack path
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = defendingRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) {
                // Check if this piece can move to block
                int direction = getDirection(attack.from, attack.to);
                if (direction != 0) {
                    for (int step = 1; step < attack.amountMoved; step++) {
                        int blockSquare = attack.from + direction * step;
                        if (GameState.isOnBoard(blockSquare)) {
                            int distance = calculateManhattanDistance(i, blockSquare);
                            if (distance <= height) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    // === UTILITY METHODS ===

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

    // === MORE UTILITY METHODS ===

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
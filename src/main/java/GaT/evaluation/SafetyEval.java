package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;

import java.util.List;

/**
 * ENHANCED SAFETY EVALUATION COMPONENT
 *
 * ✅ Enhanced for aggressive coordinated play
 * ✅ Better threat detection for cluster formations
 * ✅ Improved guard protection evaluation
 * ✅ Enhanced escape route analysis
 * ✅ Coordination-aware safety assessment
 *
 * Handles guard safety, threat analysis, and defensive evaluation.
 * Integrates with ThreatMap for advanced threat detection.
 */
public class SafetyEval {

    // === ✅ ENHANCED SAFETY CONSTANTS ===
    private static final int GUARD_DANGER_PENALTY = 800;           // Erhöht von 600
    private static final int GUARD_PROTECTION_BONUS = 200;         // Erhöht von 150
    private static final int ESCAPE_ROUTE_BONUS = 100;             // Erhöht von 80
    private static final int DEFENDER_BONUS = 70;                  // Erhöht von 50
    private static final int PIN_PENALTY = 350;                    // Erhöht von 250
    private static final int FORK_PENALTY = 400;                   // Erhöht von 300
    private static final int OVERLOADED_DEFENDER_PENALTY = 200;    // Erhöht von 150

    // === ✅ NEW: COORDINATION-AWARE SAFETY BONUSES ===
    private static final int CLUSTER_PROTECTION_BONUS = 120;       // NEU! Schutz durch Turm-Cluster
    private static final int COORDINATED_DEFENSE_BONUS = 80;       // NEU! Koordinierte Verteidigung
    private static final int GUARD_ESCORT_SAFETY = 150;            // NEU! Wächter-Begleitung
    private static final int EMERGENCY_SUPPORT_BONUS = 100;        // NEU! Notfall-Unterstützung

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
     * ✅ ENHANCED COMPREHENSIVE GUARD SAFETY EVALUATION
     */
    public int evaluateGuardSafety(GameState state) {
        int safetyScore = 0;

        // Enhanced basic danger assessment
        safetyScore += evaluateGuardSafetyBasic(state);

        // Enhanced escape route analysis
        safetyScore += evaluateEnhancedEscapeRoutes(state);

        // Enhanced protection and support evaluation
        safetyScore += evaluateEnhancedGuardProtection(state);

        // Enhanced tactical vulnerabilities
        safetyScore += evaluateEnhancedTacticalVulnerabilities(state);

        // ✅ NEW: Coordination-aware safety
        safetyScore += evaluateCoordinationSafety(state);

        return safetyScore;
    }

    /**
     * ✅ ENHANCED BASIC GUARD SAFETY - More aggressive threat detection
     */
    public int evaluateGuardSafetyBasic(GameState state) {
        int safetyScore = 0;

        // Enhanced immediate danger check
        if (isGuardInDanger(state, true)) {
            safetyScore -= GUARD_DANGER_PENALTY;

            // ✅ Additional penalty if guard is isolated
            if (isGuardIsolated(state, true)) {
                safetyScore -= GUARD_DANGER_PENALTY / 2;
            }
        }

        if (isGuardInDanger(state, false)) {
            safetyScore += GUARD_DANGER_PENALTY;

            if (isGuardIsolated(state, false)) {
                safetyScore += GUARD_DANGER_PENALTY / 2;
            }
        }

        return safetyScore;
    }

    /**
     * ADVANCED GUARD SAFETY - Deep analysis
     */
    public int evaluateGuardSafetyAdvanced(GameState state) {
        // Full safety evaluation with all enhancements
        return evaluateGuardSafety(state);
    }

    /**
     * ✅ Enhanced guard danger detection with cluster awareness
     */
    public boolean isGuardInDanger(GameState state, boolean checkRed) {
        long guardBit = checkRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);

        // Check with threat map if available (enhanced)
        if (threatMap != null && threatMap.hasImmediateThreat(guardPos, !checkRed)) {
            return true;
        }

        // Enhanced fallback to traditional check
        GameState simState = state.copy();
        simState.redToMove = !checkRed;
        List<Move> enemyMoves = MoveGenerator.generateAllMoves(simState);

        for (Move move : enemyMoves) {
            if (move.to == guardPos) {
                // ✅ Check if attack is actually dangerous (not easily defended)
                if (!canDefendAgainst(state, move, checkRed)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Count total threats in position - enhanced
     */
    public int countThreats(GameState state) {
        int threats = 0;

        // Enhanced threat counting with coordination awareness
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                if (isSquareUnderAttack(state, i, false)) {
                    int threatValue = state.redStackHeights[i];

                    // ✅ Reduced threat value if piece is well-supported
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

    // === ✅ NEW: COORDINATION-AWARE SAFETY EVALUATION ===

    /**
     * ✅ Evaluate safety through piece coordination
     */
    private int evaluateCoordinationSafety(GameState state) {
        int coordinationSafety = 0;

        // Red guard coordination safety
        if (state.redGuard != 0) {
            coordinationSafety += evaluateGuardCoordinationSafety(state, true);
        }

        // Blue guard coordination safety
        if (state.blueGuard != 0) {
            coordinationSafety -= evaluateGuardCoordinationSafety(state, false);
        }

        return coordinationSafety;
    }

    /**
     * ✅ Evaluate guard safety through coordination
     */
    private int evaluateGuardCoordinationSafety(GameState state, boolean isRed) {
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return 0;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        int safety = 0;

        // ✅ Cluster protection bonus
        int nearbyTowers = countNearbyTowers(state, guardPos, isRed, 2);
        if (nearbyTowers >= 2) {
            safety += CLUSTER_PROTECTION_BONUS;

            // Extra bonus for 3+ towers
            if (nearbyTowers >= 3) {
                safety += CLUSTER_PROTECTION_BONUS / 2;
            }
        }

        // ✅ Coordinated defense bonus
        int coordinatedDefenders = countCoordinatedDefenders(state, guardPos, isRed);
        safety += coordinatedDefenders * COORDINATED_DEFENSE_BONUS;

        // ✅ Guard escort safety
        if (hasCloseEscort(state, guardPos, isRed)) {
            safety += GUARD_ESCORT_SAFETY;
        }

        // ✅ Emergency support availability
        if (hasEmergencySupport(state, guardPos, isRed)) {
            safety += EMERGENCY_SUPPORT_BONUS;
        }

        return safety;
    }

    // === ✅ ENHANCED ESCAPE ROUTE ANALYSIS ===

    private int evaluateEnhancedEscapeRoutes(GameState state) {
        int escapeScore = 0;

        // Enhanced red guard escape routes
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int escapeRoutes = countEnhancedEscapeSquares(state, guardPos, true);
            escapeScore += escapeRoutes * ESCAPE_ROUTE_BONUS;

            // ✅ Bonus for quality of escape routes
            escapeScore += evaluateEscapeRouteQuality(state, guardPos, true);
        }

        // Enhanced blue guard escape routes
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int escapeRoutes = countEnhancedEscapeSquares(state, guardPos, false);
            escapeScore -= escapeRoutes * ESCAPE_ROUTE_BONUS;

            escapeScore -= evaluateEscapeRouteQuality(state, guardPos, false);
        }

        return escapeScore;
    }

    private int countEnhancedEscapeSquares(GameState state, int guardPos, boolean isRed) {
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
     * ✅ Evaluate quality of escape routes
     */
    private int evaluateEscapeRouteQuality(GameState state, int guardPos, boolean isRed) {
        int quality = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int escapeSquare = guardPos + dir;
            if (!GameState.isOnBoard(escapeSquare)) continue;
            if (isRankWrap(guardPos, escapeSquare, dir)) continue;

            boolean isEmpty = state.redStackHeights[escapeSquare] == 0 &&
                    state.blueStackHeights[escapeSquare] == 0;

            if (isEmpty && !isSquareUnderAttack(state, escapeSquare, !isRed)) {
                // ✅ Higher quality if escape square is protected
                int protection = countSupportingPieces(state, escapeSquare, isRed);
                quality += protection * 30;

                // ✅ Higher quality if escape leads toward own pieces
                if (leadsTowardSupport(state, escapeSquare, isRed)) {
                    quality += 50;
                }
            }
        }

        return quality;
    }

    // === ✅ ENHANCED GUARD PROTECTION ===

    private int evaluateEnhancedGuardProtection(GameState state) {
        int protectionScore = 0;

        // Enhanced red guard protection
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int protectors = countEnhancedGuardProtectors(state, guardPos, true);
            protectionScore += protectors * DEFENDER_BONUS;
        }

        // Enhanced blue guard protection
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int protectors = countEnhancedGuardProtectors(state, guardPos, false);
            protectionScore -= protectors * DEFENDER_BONUS;
        }

        return protectionScore;
    }

    private int countEnhancedGuardProtectors(GameState state, int guardPos, boolean isRed) {
        int protectors = 0;

        // Enhanced protection counting
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((isRed && state.redStackHeights[i] > 0) ||
                    (!isRed && state.blueStackHeights[i] > 0)) {

                if (canProtectSquareEnhanced(state, i, guardPos, isRed)) {
                    protectors++;

                    // ✅ Extra credit for close protectors
                    int distance = calculateManhattanDistance(i, guardPos);
                    if (distance <= 1) {
                        protectors++; // Double count adjacent protectors
                    }
                }
            }
        }

        return protectors;
    }

    // === ✅ ENHANCED TACTICAL VULNERABILITIES ===

    private int evaluateEnhancedTacticalVulnerabilities(GameState state) {
        int vulnerabilityScore = 0;

        // Enhanced pin detection
        vulnerabilityScore += evaluateEnhancedPins(state);

        // Enhanced fork detection
        vulnerabilityScore += evaluateEnhancedForks(state);

        // Enhanced overloaded defenders
        vulnerabilityScore += evaluateEnhancedOverloadedDefenders(state);

        return vulnerabilityScore;
    }

    /**
     * ✅ Enhanced pin detection with coordination awareness
     */
    private int evaluateEnhancedPins(GameState state) {
        int pinScore = 0;

        // Enhanced pin detection for red pieces
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);

            for (int i = 0; i < GameState.NUM_SQUARES; i++) {
                if (state.redStackHeights[i] > 0 && isPiecePinned(state, i, guardPos, true)) {
                    int penalty = PIN_PENALTY;

                    // ✅ Reduced penalty if piece has support
                    int support = countSupportingPieces(state, i, true);
                    if (support >= 2) {
                        penalty /= 2;
                    }

                    pinScore -= penalty;
                }
            }
        }

        // Enhanced pin detection for blue pieces
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);

            for (int i = 0; i < GameState.NUM_SQUARES; i++) {
                if (state.blueStackHeights[i] > 0 && isPiecePinned(state, i, guardPos, false)) {
                    int penalty = PIN_PENALTY;

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
     * ✅ Enhanced fork evaluation
     */
    private int evaluateEnhancedForks(GameState state) {
        int forkScore = 0;

        // Enhanced fork threat evaluation
        GameState testState = state.copy();
        testState.redToMove = true;
        List<Move> redMoves = MoveGenerator.generateAllMoves(testState);

        for (Move move : redMoves) {
            int threats = countThreatsFromSquareEnhanced(state, move.to, move.amountMoved, true);
            if (threats >= 2) {
                int bonus = FORK_PENALTY;

                // ✅ Enhanced bonus if targets are poorly defended
                if (targetsArePoorlyDefended(state, move, true)) {
                    bonus += FORK_PENALTY / 2;
                }

                forkScore += bonus;
            }
        }

        testState.redToMove = false;
        List<Move> blueMoves = MoveGenerator.generateAllMoves(testState);

        for (Move move : blueMoves) {
            int threats = countThreatsFromSquareEnhanced(state, move.to, move.amountMoved, false);
            if (threats >= 2) {
                int bonus = FORK_PENALTY;

                if (targetsArePoorlyDefended(state, move, false)) {
                    bonus += FORK_PENALTY / 2;
                }

                forkScore -= bonus;
            }
        }

        return forkScore;
    }

    /**
     * ✅ Enhanced overloaded defender evaluation
     */
    private int evaluateEnhancedOverloadedDefenders(GameState state) {
        int overloadScore = 0;

        // Enhanced overload detection
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redStackHeights[i] > 0) {
                int defendedTargets = countDefendedTargetsEnhanced(state, i, true);
                if (defendedTargets >= 2) {
                    int penalty = OVERLOADED_DEFENDER_PENALTY * (defendedTargets - 1);

                    // ✅ Reduced penalty if defender has backup
                    if (hasBackupDefender(state, i, true)) {
                        penalty /= 2;
                    }

                    overloadScore -= penalty;
                }
            }
            if (state.blueStackHeights[i] > 0) {
                int defendedTargets = countDefendedTargetsEnhanced(state, i, false);
                if (defendedTargets >= 2) {
                    int penalty = OVERLOADED_DEFENDER_PENALTY * (defendedTargets - 1);

                    if (hasBackupDefender(state, i, false)) {
                        penalty /= 2;
                    }

                    overloadScore += penalty;
                }
            }
        }

        return overloadScore;
    }

    // === ✅ NEW ENHANCED HELPER METHODS ===

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
     * ✅ Check if attack can be defended against
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
        return countEnhancedEscapeSquares(state, targetSquare, defendingRed) > 0;
    }

    /**
     * ✅ Count nearby towers within radius
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

        return count;
    }

    /**
     * ✅ Count coordinated defenders
     */
    private int countCoordinatedDefenders(GameState state, int guardPos, boolean isRed) {
        int defenders = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) {
                // Can this piece both reach guard and support other pieces?
                if (canProtectSquareEnhanced(state, i, guardPos, isRed)) {
                    int otherSupport = countSupportingPieces(state, i, isRed);
                    if (otherSupport > 0) {
                        defenders++;
                    }
                }
            }
        }

        return defenders;
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
     * ✅ Check if emergency support is available
     */
    private boolean hasEmergencySupport(GameState state, int guardPos, boolean isRed) {
        // Check if pieces can quickly move to support guard
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int height = isRed ? state.redStackHeights[i] : state.blueStackHeights[i];
            if (height > 0) {
                int distance = calculateManhattanDistance(i, guardPos);
                if (distance <= height && distance <= 3) { // Can reach in one move, within 3 squares
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
     * ✅ Enhanced square protection check
     */
    private boolean canProtectSquareEnhanced(GameState state, int piecePos, int targetSquare, boolean isRed) {
        if (piecePos == targetSquare) return false;

        int movementRange = isRed ? state.redStackHeights[piecePos] : state.blueStackHeights[piecePos];
        if (movementRange <= 0) return false;

        // Enhanced protection: can reach target or adjacent squares
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
     * ✅ Count supporting pieces for a square
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
                }
            }
        }

        return supporters;
    }

    /**
     * ✅ Enhanced threat counting from square
     */
    private int countThreatsFromSquareEnhanced(GameState state, int square, int moveRange, boolean byRed) {
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

        return threats;
    }

    /**
     * ✅ Check if fork targets are poorly defended
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
     * ✅ Enhanced defended targets counting
     */
    private int countDefendedTargetsEnhanced(GameState state, int defenderPos, boolean isRed) {
        int defendedCount = 0;
        int range = isRed ? state.redStackHeights[defenderPos] : state.blueStackHeights[defenderPos];

        // Check if defending the guard (high priority)
        long guardBit = isRed ? state.redGuard : state.blueGuard;
        if (guardBit != 0) {
            int guardPos = Long.numberOfTrailingZeros(guardBit);
            if (canProtectSquareEnhanced(state, defenderPos, guardPos, isRed)) {
                defendedCount += 2; // Guard defense counts as 2
            }
        }

        // Check if defending other pieces
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (i == defenderPos) continue;

            if ((isRed && state.redStackHeights[i] > 0) ||
                    (!isRed && state.blueStackHeights[i] > 0)) {

                if (canProtectSquareEnhanced(state, defenderPos, i, isRed)) {
                    defendedCount++;
                }
            }
        }

        return defendedCount;
    }

    /**
     * ✅ Check if defender has backup
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
                    if (canProtectSquareEnhanced(state, i, guardPos, isRed)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * ✅ Check if attack can be blocked
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

    // === EXISTING HELPER METHODS ===

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
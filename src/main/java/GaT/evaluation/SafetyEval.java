package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.Move;
import GaT.search.MoveGenerator;

import java.util.List;

/**
 * SAFETY EVALUATION COMPONENT
 * Handles guard safety, threat analysis, and tactical vulnerabilities
 */
public class SafetyEval {

    // === SAFETY CONSTANTS ===
    private static final int GUARD_DANGER_PENALTY = 600;
    private static final int GUARD_PROTECTION_BONUS = 150;
    private static final int IMMEDIATE_THREAT_PENALTY = 400;
    private static final int POTENTIAL_THREAT_PENALTY = 100;
    private static final int ESCAPE_ROUTE_BONUS = 80;
    private static final int DEFENDER_BONUS = 50;

    // === CASTLE INDICES ===
    private static final int RED_CASTLE_INDEX = GameState.getIndex(6, 3); // D7
    private static final int BLUE_CASTLE_INDEX = GameState.getIndex(0, 3); // D1

    /**
     * COMPREHENSIVE GUARD SAFETY EVALUATION
     */
    public int evaluateGuardSafety(GameState state) {
        int safetyScore = 0;

        // Basic danger assessment
        safetyScore += evaluateGuardSafetyBasic(state);

        // Advanced threat analysis
        safetyScore += evaluateAdvancedThreats(state);

        // Escape route analysis
        safetyScore += evaluateEscapeRoutes(state);

        // Protection and support evaluation
        safetyScore += evaluateGuardProtection(state);

        return safetyScore;
    }

    /**
     * BASIC GUARD SAFETY - Fast evaluation for time pressure
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
     * ADVANCED GUARD SAFETY - Deep analysis for abundant time
     */
    public int evaluateGuardSafetyAdvanced(GameState state) {
        int safetyScore = 0;

        // Comprehensive threat analysis
        safetyScore += evaluateGuardSafety(state);

        // Tactical motifs (pins, forks, discoveries)
        safetyScore += evaluateTacticalVulnerabilities(state);

        // Defensive formations
        safetyScore += evaluateDefensiveFormations(state);

        return safetyScore;
    }

    // === GUARD DANGER DETECTION ===

    /**
     * Check if a guard is in immediate danger
     */
    public boolean isGuardInDanger(GameState state, boolean checkRed) {
        long guardBit = checkRed ? state.redGuard : state.blueGuard;
        if (guardBit == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(guardBit);
        return isPositionUnderAttack(state, guardPos, !checkRed);
    }

    /**
     * Check if both guards are in danger
     */
    public boolean areGuardsInDanger(GameState state) {
        return isGuardInDanger(state, true) || isGuardInDanger(state, false);
    }

    /**
     * Check if a position is under attack by enemy pieces
     */
    public boolean isPositionUnderAttack(GameState state, int position, boolean byRed) {
        long attackers = byRed ? (state.redTowers | state.redGuard) : (state.blueTowers | state.blueGuard);

        // Check all enemy pieces for attacks on this position
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((attackers & GameState.bit(i)) != 0) {
                if (canPieceAttackPosition(state, i, position, byRed)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if a specific piece can attack a position
     */
    private boolean canPieceAttackPosition(GameState state, int from, int to, boolean isRed) {
        // Determine piece type and movement capability
        boolean isGuard = isRed ?
                (state.redGuard & GameState.bit(from)) != 0 :
                (state.blueGuard & GameState.bit(from)) != 0;

        int movementRange = isGuard ? 1 :
                (isRed ? state.redStackHeights[from] : state.blueStackHeights[from]);

        if (movementRange <= 0) return false;

        // Calculate distance and direction
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        // Guard movement pattern (adjacent squares only)
        if (isGuard || movementRange == 1) {
            return rankDiff <= 1 && fileDiff <= 1 && (rankDiff + fileDiff) == 1;
        }

        // Tower movement pattern (rank/file movement up to range)
        boolean sameRank = rankDiff == 0;
        boolean sameFile = fileDiff == 0;
        int distance = Math.max(rankDiff, fileDiff);

        if (!(sameRank || sameFile) || distance > movementRange) {
            return false;
        }

        // Check if path is clear
        return isPathClear(state, from, to);
    }

    // === ADVANCED THREAT ANALYSIS ===

    /**
     * Evaluate advanced threats and vulnerabilities
     */
    private int evaluateAdvancedThreats(GameState state) {
        int threatScore = 0;

        // Multi-move threats
        threatScore += evaluateMultiMoveThreatss(state);

        // Discovered attack potential
        threatScore += evaluateDiscoveredAttacks(state);

        // Zugzwang evaluation (forced bad moves)
        threatScore += evaluateZugzwang(state);

        return threatScore;
    }

    /**
     * Count immediate threats against both sides
     */
    public int countThreats(GameState state) {
        int threatCount = 0;

        // Count threats against red guard
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            threatCount += countDirectThreats(state, guardPos, false);
        }

        // Count threats against blue guard
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            threatCount += countDirectThreats(state, guardPos, true);
        }

        return threatCount;
    }

    /**
     * Count direct threats against a position
     */
    private int countDirectThreats(GameState state, int position, boolean byRed) {
        int threats = 0;
        long attackers = byRed ? (state.redTowers | state.redGuard) : (state.blueTowers | state.blueGuard);

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((attackers & GameState.bit(i)) != 0) {
                if (canPieceAttackPosition(state, i, position, byRed)) {
                    threats++;
                }
            }
        }

        return threats;
    }

    /**
     * Evaluate multi-move threats
     */
    private int evaluateMultiMoveThreatss(GameState state) {
        int threatScore = 0;

        // Look for pieces that can create threats in the next move
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        for (Move move : moves) {
            GameState afterMove = state.copy();
            afterMove.applyMove(move);

            // Check if this move creates new threats
            boolean createsGuardThreat = !state.redToMove ?
                    isGuardInDanger(afterMove, true) :
                    isGuardInDanger(afterMove, false);

            if (createsGuardThreat) {
                threatScore += state.redToMove ? POTENTIAL_THREAT_PENALTY : -POTENTIAL_THREAT_PENALTY;
            }
        }

        return threatScore;
    }

    /**
     * Evaluate discovered attack potential
     */
    private int evaluateDiscoveredAttacks(GameState state) {
        // TODO: Implement discovered attack detection
        // This would check for pieces that can move to reveal attacks from other pieces
        return 0;
    }

    /**
     * Evaluate zugzwang positions (where any move worsens position)
     */
    private int evaluateZugzwang(GameState state) {
        // TODO: Implement zugzwang detection
        // This is complex and requires deep analysis
        return 0;
    }

    // === ESCAPE ROUTES AND PROTECTION ===

    /**
     * Evaluate escape routes for guards
     */
    private int evaluateEscapeRoutes(GameState state) {
        int escapeScore = 0;

        // Red guard escape routes
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            escapeScore += countEscapeSquares(state, guardPos, true) * ESCAPE_ROUTE_BONUS;
        }

        // Blue guard escape routes
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            escapeScore -= countEscapeSquares(state, guardPos, false) * ESCAPE_ROUTE_BONUS;
        }

        return escapeScore;
    }

    /**
     * Count safe escape squares for a guard
     */
    private int countEscapeSquares(GameState state, int guardPos, boolean isRed) {
        int escapeSquares = 0;
        int[] directions = {-1, 1, -7, 7}; // Left, Right, Up, Down

        for (int dir : directions) {
            int escape = guardPos + dir;
            if (!GameState.isOnBoard(escape)) continue;
            if (isRankWrap(guardPos, escape, dir)) continue;

            // Check if square is free or capturable
            if (!isOccupiedByFriendly(escape, state, isRed)) {
                // Check if escape square is safe
                if (!isPositionUnderAttack(state, escape, !isRed)) {
                    escapeSquares++;
                }
            }
        }

        return escapeSquares;
    }

    /**
     * Evaluate guard protection and support
     */
    private int evaluateGuardProtection(GameState state) {
        int protectionScore = 0;

        // Red guard protection
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int protectors = countGuardProtectors(state, guardPos, true);
            protectionScore += protectors * GUARD_PROTECTION_BONUS;
        }

        // Blue guard protection
        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int protectors = countGuardProtectors(state, guardPos, false);
            protectionScore -= protectors * GUARD_PROTECTION_BONUS;
        }

        return protectionScore;
    }

    /**
     * Count pieces protecting a guard
     */
    private int countGuardProtectors(GameState state, int guardPos, boolean isRed) {
        int protectors = 0;
        int[] directions = {-1, 1, -7, 7};

        // Direct protection (adjacent pieces)
        for (int dir : directions) {
            int protector = guardPos + dir;
            if (!GameState.isOnBoard(protector)) continue;
            if (isRankWrap(guardPos, protector, dir)) continue;

            if (isOccupiedByFriendly(protector, state, isRed)) {
                protectors++;
            }
        }

        // Indirect protection (pieces that can defend)
        protectors += countIndirectProtectors(state, guardPos, isRed);

        return protectors;
    }

    /**
     * Count pieces that can come to guard's defense
     */
    private int countIndirectProtectors(GameState state, int guardPos, boolean isRed) {
        int indirectProtectors = 0;
        long friendlyPieces = isRed ? (state.redTowers | state.redGuard) : (state.blueTowers | state.blueGuard);

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((friendlyPieces & GameState.bit(i)) != 0 && i != guardPos) {
                // Check if this piece can reach guard's vicinity
                if (canDefendGuard(state, i, guardPos, isRed)) {
                    indirectProtectors++;
                }
            }
        }

        return indirectProtectors;
    }

    /**
     * Check if a piece can come to guard's defense
     */
    private boolean canDefendGuard(GameState state, int piecePos, int guardPos, boolean isRed) {
        int movementRange = isRed ? state.redStackHeights[piecePos] : state.blueStackHeights[piecePos];
        if (movementRange <= 0) return false;

        // Check if piece can reach squares adjacent to guard
        int[] directions = {-1, 1, -7, 7};
        for (int dir : directions) {
            int defenseSquare = guardPos + dir;
            if (!GameState.isOnBoard(defenseSquare)) continue;
            if (isRankWrap(guardPos, defenseSquare, dir)) continue;

            int distance = calculateManhattanDistance(piecePos, defenseSquare);
            if (distance <= movementRange && isPathClear(state, piecePos, defenseSquare)) {
                return true;
            }
        }

        return false;
    }

    // === TACTICAL VULNERABILITIES ===

    /**
     * Evaluate tactical vulnerabilities (pins, forks, skewers)
     */
    private int evaluateTacticalVulnerabilities(GameState state) {
        int vulnerabilityScore = 0;

        // Pin detection
        vulnerabilityScore += evaluatePins(state);

        // Fork potential
        vulnerabilityScore += evaluateForks(state);

        // Overloaded defenders
        vulnerabilityScore += evaluateOverloadedDefenders(state);

        return vulnerabilityScore;
    }

    /**
     * Detect pinned pieces
     */
    private int evaluatePins(GameState state) {
        // TODO: Implement pin detection
        // A pin occurs when a piece cannot move because it would expose a more valuable piece
        return 0;
    }

    /**
     * Evaluate fork potential
     */
    private int evaluateForks(GameState state) {
        // TODO: Implement fork detection
        // A fork is an attack on two or more pieces simultaneously
        return 0;
    }

    /**
     * Evaluate overloaded defenders
     */
    private int evaluateOverloadedDefenders(GameState state) {
        // TODO: Implement overloaded defender detection
        // An overloaded defender is protecting multiple pieces/squares
        return 0;
    }

    // === DEFENSIVE FORMATIONS ===

    /**
     * Evaluate defensive formations and structures
     */
    private int evaluateDefensiveFormations(GameState state) {
        int formationScore = 0;

        // Guard fortress evaluation
        formationScore += evaluateGuardFortress(state);

        // Piece coordination for defense
        formationScore += evaluateDefensiveCoordination(state);

        // Control of key defensive squares
        formationScore += evaluateDefensiveSquareControl(state);

        return formationScore;
    }

    /**
     * Evaluate guard fortress (protected guard positions)
     */
    private int evaluateGuardFortress(GameState state) {
        int fortressScore = 0;

        // Check if guards are in well-protected positions
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            if (isInFortress(state, guardPos, true)) {
                fortressScore += 200;
            }
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            if (isInFortress(state, guardPos, false)) {
                fortressScore -= 200;
            }
        }

        return fortressScore;
    }

    /**
     * Check if guard is in a fortress position
     */
    private boolean isInFortress(GameState state, int guardPos, boolean isRed) {
        // A fortress is a position where the guard is protected by multiple pieces
        // and has limited escape routes (making it hard to attack)

        int protectors = countGuardProtectors(state, guardPos, isRed);
        int escapeRoutes = countEscapeSquares(state, guardPos, isRed);

        // Fortress criteria: high protection, limited but safe escape routes
        return protectors >= 3 && escapeRoutes >= 1 && escapeRoutes <= 2;
    }

    /**
     * Evaluate defensive coordination
     */
    private int evaluateDefensiveCoordination(GameState state) {
        // TODO: Implement defensive coordination evaluation
        // This evaluates how well pieces work together for defense
        return 0;
    }

    /**
     * Evaluate control of defensive squares
     */
    private int evaluateDefensiveSquareControl(GameState state) {
        int controlScore = 0;

        // Key defensive squares around guards
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            controlScore += evaluateDefensiveControlAround(state, guardPos, true);
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            controlScore -= evaluateDefensiveControlAround(state, guardPos, false);
        }

        return controlScore;
    }

    /**
     * Evaluate defensive control around a guard
     */
    private int evaluateDefensiveControlAround(GameState state, int guardPos, boolean isRed) {
        int control = 0;
        int[] directions = {-1, 1, -7, 7};

        for (int dir : directions) {
            int square = guardPos + dir;
            if (!GameState.isOnBoard(square)) continue;
            if (isRankWrap(guardPos, square, dir)) continue;

            // Count friendly pieces that control this square
            if (isPositionControlledBy(state, square, isRed)) {
                control += 25;
            }
        }

        return control;
    }

    /**
     * Check if a position is controlled by friendly pieces
     */
    private boolean isPositionControlledBy(GameState state, int position, boolean byRed) {
        long friendlyPieces = byRed ? (state.redTowers | state.redGuard) : (state.blueTowers | state.blueGuard);

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if ((friendlyPieces & GameState.bit(i)) != 0) {
                if (canPieceAttackPosition(state, i, position, byRed)) {
                    return true;
                }
            }
        }

        return false;
    }

    // === UTILITY METHODS ===

    /**
     * Check if path between squares is clear
     */
    private boolean isPathClear(GameState state, int from, int to) {
        int rankDiff = GameState.rank(to) - GameState.rank(from);
        int fileDiff = GameState.file(to) - GameState.file(from);

        // Must be on same rank or file
        if (rankDiff != 0 && fileDiff != 0) return false;

        int step = rankDiff != 0 ? (rankDiff > 0 ? 7 : -7) : (fileDiff > 0 ? 1 : -1);
        int current = from + step;

        while (current != to) {
            if (isOccupied(current, state)) return false;
            current += step;
        }

        return true;
    }

    private boolean isOccupied(int square, GameState state) {
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard)
                & GameState.bit(square)) != 0;
    }

    private boolean isOccupiedByFriendly(int square, GameState state, boolean isRed) {
        long bit = GameState.bit(square);
        if (isRed) {
            return (state.redGuard & bit) != 0 || (state.redTowers & bit) != 0;
        } else {
            return (state.blueGuard & bit) != 0 || (state.blueTowers & bit) != 0;
        }
    }

    private boolean isRankWrap(int from, int to, int direction) {
        if (Math.abs(direction) == 1) { // Horizontal movement
            return GameState.rank(from) != GameState.rank(to);
        }
        return false;
    }

    private int calculateManhattanDistance(int from, int to) {
        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));
        return rankDiff + fileDiff;
    }


}
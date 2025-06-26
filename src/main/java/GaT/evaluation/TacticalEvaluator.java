package GaT.evaluation;

import GaT.model.GameState;
import GaT.search.MoveGenerator;
import GaT.model.Move;

import java.util.List;

/**
 * TACTICAL EVALUATOR - Enhanced tactical and aggressive play
 */
public class TacticalEvaluator extends Evaluator {

    // === TACTICAL CONSTANTS ===
    private static final int GUARD_THREAT_BONUS = 500;      // Threatening enemy guard
    private static final int CASTLE_THREAT_BONUS = 400;     // Threatening to reach castle
    private static final int FORK_BONUS = 300;              // Attacking multiple pieces
    private static final int PIN_BONUS = 250;               // Pinning pieces
    private static final int DISCOVERED_ATTACK_BONUS = 200; // Discovery potential
    private static final int TEMPO_BONUS = 50;              // Forcing moves

    @Override
    public int evaluate(GameState state, int depth) {
        if (state == null) return 0;

        // Terminal positions first
        int terminalScore = checkTerminalPosition(state, depth);
        if (terminalScore != 0) {
            return terminalScore;
        }

        // Base evaluation
        int eval = super.evaluate(state, depth);

        // Add tactical evaluation
        eval += evaluateTactics(state);

        return eval;
    }

    /**
     * Enhanced tactical evaluation
     */
    private int evaluateTactics(GameState state) {
        int tacticalScore = 0;

        // Direct threats
        tacticalScore += evaluateDirectThreats(state);

        // Forcing moves and tempo
        tacticalScore += evaluateForcingMoves(state);

        // Piece coordination for attacks
        tacticalScore += evaluateAttackCoordination(state);

        // Tactical patterns
        tacticalScore += evaluateTacticalPatterns(state);

        return tacticalScore;
    }

    /**
     * Evaluate direct threats to enemy pieces
     */
    private int evaluateDirectThreats(GameState state) {
        int threatScore = 0;
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        for (Move move : moves) {
            // Guard threats are most valuable
            if (threatsEnemyGuard(move, state)) {
                threatScore += GUARD_THREAT_BONUS;
            }

            // Castle reaching threats
            if (threatsToCastle(move, state)) {
                threatScore += CASTLE_THREAT_BONUS;
            }

            // Multiple threats (forks)
            int threatsCount = countThreatsFromSquare(state, move.to, move.amountMoved);
            if (threatsCount >= 2) {
                threatScore += FORK_BONUS * (threatsCount - 1);
            }
        }

        return state.redToMove ? threatScore : -threatScore;
    }

    /**
     * Evaluate forcing moves and tempo
     */
    private int evaluateForcingMoves(GameState state) {
        int forceScore = 0;
        List<Move> moves = MoveGenerator.generateAllMoves(state);

        for (Move move : moves) {
            GameState afterMove = state.copy();
            afterMove.applyMove(move);

            // Check if opponent has limited responses
            List<Move> responses = MoveGenerator.generateAllMoves(afterMove);

            // Forcing moves limit opponent's options
            if (responses.size() <= 3) {
                forceScore += TEMPO_BONUS * (4 - responses.size());
            }

            // Checks are forcing
            if (putGuardInCheck(move, state)) {
                forceScore += TEMPO_BONUS * 2;
            }
        }

        return state.redToMove ? forceScore : -forceScore;
    }

    /**
     * Evaluate piece coordination for attacks
     */
    private int evaluateAttackCoordination(GameState state) {
        int coordinationScore = 0;

        // Look for pieces that can support attacks
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (state.redToMove && state.redStackHeights[i] > 0) {
                // Count how many enemy pieces this tower can attack
                int attacks = countPossibleAttacks(state, i, state.redStackHeights[i], true);
                if (attacks > 0) {
                    coordinationScore += attacks * 25;
                }
            } else if (!state.redToMove && state.blueStackHeights[i] > 0) {
                int attacks = countPossibleAttacks(state, i, state.blueStackHeights[i], false);
                if (attacks > 0) {
                    coordinationScore += attacks * 25;
                }
            }
        }

        return coordinationScore;
    }

    /**
     * Detect tactical patterns (pins, skewers, discoveries)
     */
    private int evaluateTacticalPatterns(GameState state) {
        int patternScore = 0;

        // Pin detection
        patternScore += detectPins(state) * PIN_BONUS;

        // Discovery potential
        patternScore += detectDiscoveries(state) * DISCOVERED_ATTACK_BONUS;

        return state.redToMove ? patternScore : -patternScore;
    }

    // === HELPER METHODS ===

    private boolean threatsEnemyGuard(Move move, GameState state) {
        long enemyGuard = state.redToMove ? state.blueGuard : state.redGuard;
        if (enemyGuard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(enemyGuard);
        return canAttackFrom(move.to, guardPos, move.amountMoved);
    }

    private boolean threatsToCastle(Move move, GameState state) {
        int enemyCastle = state.redToMove ?
                GameState.getIndex(0, 3) : GameState.getIndex(6, 3);

        // Check if guard move threatens castle
        if (move.amountMoved == 1) { // Guard move
            long guardBit = state.redToMove ? state.redGuard : state.blueGuard;
            if ((guardBit & GameState.bit(move.from)) != 0) {
                return canAttackFrom(move.to, enemyCastle, 1);
            }
        }

        return false;
    }

    private int countThreatsFromSquare(GameState state, int square, int range) {
        int threats = 0;
        boolean isRed = state.redToMove;

        // Check all enemy pieces
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            long bit = GameState.bit(i);

            if (isRed) {
                if (((state.blueTowers | state.blueGuard) & bit) != 0) {
                    if (canAttackFrom(square, i, range)) {
                        threats++;
                    }
                }
            } else {
                if (((state.redTowers | state.redGuard) & bit) != 0) {
                    if (canAttackFrom(square, i, range)) {
                        threats++;
                    }
                }
            }
        }

        return threats;
    }

    private boolean putGuardInCheck(Move move, GameState state) {
        long enemyGuard = state.redToMove ? state.blueGuard : state.redGuard;
        if (enemyGuard == 0) return false;

        int guardPos = Long.numberOfTrailingZeros(enemyGuard);

        // Direct attack on guard
        if (canAttackFrom(move.to, guardPos, move.amountMoved)) {
            return true;
        }

        // Check if move discovers attack on guard
        return false; // Simplified - full implementation would check discoveries
    }

    private int countPossibleAttacks(GameState state, int from, int range, boolean isRed) {
        int attacks = 0;

        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (canAttackFrom(from, i, range)) {
                long bit = GameState.bit(i);
                if (isRed && ((state.blueTowers | state.blueGuard) & bit) != 0) {
                    attacks++;
                } else if (!isRed && ((state.redTowers | state.redGuard) & bit) != 0) {
                    attacks++;
                }
            }
        }

        return attacks;
    }

    private int detectPins(GameState state) {
        // Simplified pin detection
        // A pin occurs when a piece cannot move without exposing a more valuable piece
        return 0; // Would need complex implementation
    }

    private int detectDiscoveries(GameState state) {
        // Simplified discovery detection
        // A discovery occurs when moving a piece reveals an attack from another piece
        return 0; // Would need complex implementation
    }

    private boolean canAttackFrom(int from, int to, int range) {
        if (from == to) return false;

        int rankDiff = Math.abs(GameState.rank(from) - GameState.rank(to));
        int fileDiff = Math.abs(GameState.file(from) - GameState.file(to));

        // Must be on same rank or file
        if (rankDiff != 0 && fileDiff != 0) return false;

        int distance = Math.max(rankDiff, fileDiff);
        return distance <= range;
    }

    private int checkTerminalPosition(GameState state, int depth) {
        // Guard captured
        if (state.redGuard == 0) return -5000 - depth;
        if (state.blueGuard == 0) return 5000 + depth;

        // Guard reached enemy castle
        boolean redWins = (state.redGuard & GameState.bit(GameState.getIndex(0, 3))) != 0;
        boolean blueWins = (state.blueGuard & GameState.bit(GameState.getIndex(6, 3))) != 0;

        if (redWins) return 5000 + depth;
        if (blueWins) return -5000 - depth;

        return 0;
    }
}
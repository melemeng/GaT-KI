package GaT.game;

import java.util.*;

public class MoveGenerator {
    public static List<Move> generateAllMoves(GameState state) {
        List<Move> moves = new ArrayList<>();

        if (state.redToMove) {
            generateGuardMoves(state.redGuard, state, true, moves);
            generateTowerMoves(state.redTowers, state.redStackHeights, state, true, moves);
        } else {
            generateGuardMoves(state.blueGuard, state, false, moves);
            generateTowerMoves(state.blueTowers, state.blueStackHeights, state, false, moves);
        }
        return moves;
    }

    private static void generateGuardMoves(long guardBit, GameState state, boolean isRed, List<Move> moves) {
        if (guardBit == 0) return; // Safety check - no guard exists

        int from = Long.numberOfTrailingZeros(guardBit);
        int[] directions = { -1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE }; // ← → ↑ ↓

        for (int dir : directions) {
            int to = from + dir;
            if (!GameState.isOnBoard(to)) continue;

            // ✅ FIXED: Removed undefined 'check' variable, added proper direction parameter
            boolean isHorizontal = (dir == -1 || dir == 1);
            if (isHorizontal && isEdgeWrap(from, to, dir)) continue;

            if (!isOccupied(to, state)) {
                // Empty square - guard can move here
                moves.add(new Move(from, to, 1));
            } else if (isEnemyPiece(to, isRed, state)) {
                // Enemy piece - guard can capture it
                moves.add(new Move(from, to, 1));
            }
            // Note: If it's our own piece, we don't add a move (can't move there)
        }
    }

    private static void generateTowerMoves(long towers, int[] heights, GameState state, boolean isRed, List<Move> moves) {
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            if (((towers >>> i) & 1) == 0) continue;

            int height = heights[i];
            if (height == 0) continue;

            int[] directions = { -1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE };

            for (int amount = 1; amount <= height; amount++) {
                for (int dir : directions) {
                    int to = i + dir * amount;
                    if (!GameState.isOnBoard(to)) continue;

                    // Your fix was correct here
                    if (isEdgeWrap(i, to, dir)) continue;

                    if (isPathClear(i, dir, amount, state)) {
                        if (!isOccupied(to, state)) {
                            moves.add(new Move(i, to, amount));
                        } else if (canCaptureTower(i, to, amount, isRed, state)) {
                            moves.add(new Move(i, to, amount));
                        } else if (isOwnTower(to, isRed, state)) {
                            moves.add(new Move(i, to, amount)); // stacking
                        }
                    }
                }
            }
        }
    }

    private static boolean isEdgeWrap(int from, int to, int direction) {
        // Only horizontal moves can wrap around board edges
        if (Math.abs(direction) == 1) { // Horizontal movement (-1 or +1)
            return GameState.rank(from) != GameState.rank(to);
        }
        return false; // Vertical moves (-7 or +7) cannot edge wrap
    }


    private static boolean isOccupied(int index, GameState state) {
        return ((state.redTowers | state.blueTowers | state.redGuard | state.blueGuard) & GameState.bit(index)) != 0;
    }

    private static boolean isOwnTower(int index, boolean isRed, GameState state) {
        return ((isRed ? state.redTowers : state.blueTowers) & GameState.bit(index)) != 0;
    }

    // ✅ NEW: Fixed method to check for enemy pieces (what you need for guard moves)
    private static boolean isEnemyPiece(int index, boolean isRed, GameState state) {
        long bit = GameState.bit(index);
        if (isRed) {
            // For red player, enemies are blue pieces
            return (state.blueGuard & bit) != 0 || (state.blueTowers & bit) != 0;
        } else {
            // For blue player, enemies are red pieces
            return (state.redGuard & bit) != 0 || (state.redTowers & bit) != 0;
        }
    }

    private static boolean canCaptureGuard(int from, int to, boolean isRed, GameState state) {
        long targetGuard = isRed ? state.blueGuard : state.redGuard;
        return (targetGuard & GameState.bit(to)) != 0;
    }

    private static boolean canCaptureTower(int from, int to, int amount, boolean isRed, GameState state) {
        long enemyTowers = isRed ? state.blueTowers : state.redTowers;
        int[] enemyHeights = isRed ? state.blueStackHeights : state.redStackHeights;

        // If there is a tower on target
        if ((enemyTowers & GameState.bit(to)) != 0) {
            return amount >= enemyHeights[to]; // tower height must be >= to capture
        }

        long enemyGuard = isRed ? state.blueGuard : state.redGuard;
        return (enemyGuard & GameState.bit(to)) != 0; // any tower can capture guard
    }

    // ✅ FIXED: Your isPathClear was mostly correct, but let's make it more robust
    private static boolean isPathClear(int from, int dir, int steps, GameState state) {
        for (int i = 1; i < steps; i++) {
            int check = from + dir * i;
            if (!GameState.isOnBoard(check)) return false;
            if (isOccupied(check, state)) return false;

            // Check edge wrap for each step
            if (isEdgeWrap(from, check, dir)) return false;
        }
        return true;
    }

}


package GaT;

import java.util.*;

public class MoveGenerator {
    public static List<Move> generateAllMoves(GameState state) {
        List<Move> moves = new ArrayList<>();

        if (state.whiteToMove) {
            generateGuardMoves(state.whiteGuard, state, true, moves);
            generateTowerMoves(state.whiteTowers, state.whiteStackHeights, state, true, moves);
        } else {
            generateGuardMoves(state.blackGuard, state, false, moves);
            generateTowerMoves(state.blackTowers, state.blackStackHeights, state, false, moves);
        }

        return moves;
    }

    private static void generateGuardMoves(long guardBit, GameState state, boolean isWhite, List<Move> moves) {
        int from = Long.numberOfTrailingZeros(guardBit);
        int[] directions = { -1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE }; // ← → ↑ ↓

        for (int dir : directions) {
            int to = from + dir;
            if (!GameState.isOnBoard(to)) continue;
            boolean isHorizontal = (dir == -1 || dir == 1);     //Edge-wrap can only happen on horizontal moves
            if (isHorizontal && isEdgeWrap(from, to)) continue;

            if (!isOccupied(to, state)) {
                moves.add(new Move(from, to, 1));
            } else if (canCaptureGuard(from, to, isWhite, state)) {
                moves.add(new Move(from, to, 1));
            }
        }
    }

    private static void generateTowerMoves(long towers, int[] heights, GameState state, boolean isWhite, List<Move> moves) {
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            //Checks if there is tower on square i
            if (((towers >>> i) & 1) == 0) continue;


            int height = heights[i];
            if (height == 0) continue;

            int[] directions = { -1, 1, -GameState.BOARD_SIZE, GameState.BOARD_SIZE }; // ← → ↑ ↓

            for (int amount = 1; amount <= height; amount++) {
                for (int dir : directions) {
                    int to = i + dir * amount;
                    if (!GameState.isOnBoard(to)) continue;
                    boolean isHorizontal = (dir == -1 || dir == 1);     //Edge-wrap can only happen on horizontal moves
                    if (isHorizontal && isEdgeWrap(i, to)) continue;

                    if (isPathClear(i, dir, amount, state)) {
                        if (!isOccupied(to, state)) {
                            moves.add(new Move(i, to, amount));
                        } else if (canCaptureTower(i, to, amount, isWhite, state)) {
                            moves.add(new Move(i, to, amount));
                        } else if (isOwnTower(to, isWhite, state)) {
                            moves.add(new Move(i, to, amount)); // stacking
                        }
                    }
                }
            }
        }
    }

    private static boolean isEdgeWrap(int from, int to) {
        return GameState.rank(from) != GameState.rank(to);
    }

    private static boolean isOccupied(int index, GameState state) {
        return ((state.whiteTowers | state.blackTowers | state.whiteGuard | state.blackGuard) & GameState.bit(index)) != 0;
    }

    private static boolean isOwnTower(int index, boolean isWhite, GameState state) {
        return ((isWhite ? state.whiteTowers : state.blackTowers) & GameState.bit(index)) != 0;
    }

    private static boolean canCaptureGuard(int from, int to, boolean isWhite, GameState state) {
        long targetGuard = isWhite ? state.blackGuard : state.whiteGuard;
        return (targetGuard & GameState.bit(to)) != 0;
    }

    private static boolean canCaptureTower(int from, int to, int amount, boolean isWhite, GameState state) {
        long enemyTowers = isWhite ? state.blackTowers : state.whiteTowers;
        int[] enemyHeights = isWhite ? state.blackStackHeights : state.whiteStackHeights;

        //If there is a tower on target
        if ((enemyTowers & GameState.bit(to)) != 0) {
            return amount >= enemyHeights[to]; // tower height must be >= to capture
        }

        long enemyGuard = isWhite ? state.blackGuard : state.whiteGuard;
        return (enemyGuard & GameState.bit(to)) != 0; // any tower can capture guard
    }

    private static boolean isPathClear(int from, int dir, int steps, GameState state) {
        for (int i = 1; i < steps; i++) {
            int check = from + dir * i;
            if (!GameState.isOnBoard(check)) return false;
            if (isOccupied(check, state)) return false;
            boolean isHorizontal = (dir == -1 || dir == 1);
            if (isHorizontal && isEdgeWrap(from, check)) return false;
        }
        return true;
    }
}


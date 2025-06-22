package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.SearchConfig;

/**
 * MAIN EVALUATION COORDINATOR - Extracted from Minimax.evaluate()
 * Coordinates all evaluation components and adapts to time pressure
 */
public class Evaluator {

    // === EVALUATION COMPONENTS ===
    private final MaterialEval materialEval;
    private final PositionalEval positionalEval;
    private final SafetyEval safetyEval;

    // === EVALUATION CONSTANTS ===
    public static final int CASTLE_REACH_SCORE = 2500;
    public static final int GUARD_CAPTURE_SCORE = 1500;

    // === TIME-ADAPTIVE EVALUATION ===
    private static long remainingTimeMs = 180000; // Default 3 minutes

    public Evaluator() {
        this.materialEval = new MaterialEval();
        this.positionalEval = new PositionalEval();
        this.safetyEval = new SafetyEval();
    }

    /**
     * MAIN EVALUATION INTERFACE - Adaptive based on time pressure
     * (Extracted from Minimax.evaluate())
     */
    public int evaluate(GameState state, int depth) {
        // === TERMINAL POSITION CHECK ===
        EvaluationResult terminalResult = checkTerminalPosition(state, depth);
        if (terminalResult.isTerminal) {
            return terminalResult.score;
        }

        // === TIME-ADAPTIVE EVALUATION STRATEGY ===
        if (remainingTimeMs < 3000) {
            return evaluateUltraFast(state, depth);
        } else if (remainingTimeMs < 10000) {
            return evaluateQuick(state, depth);
        } else if (remainingTimeMs > 30000) {
            return evaluateEnhanced(state, depth);
        } else {
            return evaluateBalanced(state, depth);
        }
    }

    /**
     * Check for terminal positions (wins/losses)
     */
    private EvaluationResult checkTerminalPosition(GameState state, int depth) {
        // Guard captured
        if (state.redGuard == 0) {
            return new EvaluationResult(true, -CASTLE_REACH_SCORE - depth);
        }
        if (state.blueGuard == 0) {
            return new EvaluationResult(true, CASTLE_REACH_SCORE + depth);
        }

        // Guard reached enemy castle
        boolean redWinsByCastle = state.redGuard == GameState.bit(GameState.getIndex(0, 3)); // D1
        boolean blueWinsByCastle = state.blueGuard == GameState.bit(GameState.getIndex(6, 3)); // D7

        if (redWinsByCastle) {
            return new EvaluationResult(true, CASTLE_REACH_SCORE + depth);
        }
        if (blueWinsByCastle) {
            return new EvaluationResult(true, -CASTLE_REACH_SCORE - depth);
        }

        return new EvaluationResult(false, 0);
    }

    /**
     * ULTRA-FAST EVALUATION - For extreme time pressure (< 3s)
     * Focus: Material + Guard advancement only
     */
    private int evaluateUltraFast(GameState state, int depth) {
        int eval = 0;

        // Material difference (simplified)
        eval += materialEval.evaluateMaterialSimple(state);

        // Guard advancement (critical in endgame)
        eval += positionalEval.evaluateGuardAdvancementFast(state);

        return eval;
    }

    /**
     * QUICK EVALUATION - For time pressure (< 10s)
     * Focus: Material + Guard safety + Basic advancement
     */
    private int evaluateQuick(GameState state, int depth) {
        int eval = 0;

        // Material (40% weight)
        eval += materialEval.evaluateMaterialBasic(state) * 40 / 100;

        // Guard advancement (35% weight)
        eval += positionalEval.evaluateGuardAdvancement(state) * 35 / 100;

        // Guard safety (25% weight)
        eval += safetyEval.evaluateGuardSafetyBasic(state) * 25 / 100;

        return eval;
    }

    /**
     * BALANCED EVALUATION - Standard tournament play
     * Focus: Well-rounded evaluation with all components
     */
    private int evaluateBalanced(GameState state, int depth) {
        int eval = 0;

        // Material + Activity (40% weight)
        eval += materialEval.evaluateMaterialWithActivity(state) * 40 / 100;

        // Guard safety (30% weight)
        eval += safetyEval.evaluateGuardSafety(state) * 30 / 100;

        // Positional factors (20% weight)
        eval += positionalEval.evaluatePositional(state) * 20 / 100;

        // Tempo bonus (10% weight)
        eval += evaluateTempo(state) * 10 / 100;

        return eval;
    }

    /**
     * ENHANCED EVALUATION - For abundant time (> 30s)
     * Focus: Deep positional understanding and advanced concepts
     */
    private int evaluateEnhanced(GameState state, int depth) {
        int eval = 0;

        // Material with advanced features (35% weight)
        eval += materialEval.evaluateMaterialAdvanced(state) * 35 / 100;

        // Advanced guard safety (25% weight)
        eval += safetyEval.evaluateGuardSafetyAdvanced(state) * 25 / 100;

        // Advanced positional evaluation (25% weight)
        eval += positionalEval.evaluatePositionalAdvanced(state) * 25 / 100;

        // Piece coordination and tactics (15% weight)
        eval += evaluateAdvancedConcepts(state) * 15 / 100;

        return eval;
    }

    /**
     * Evaluate tempo (who to move advantage)
     */
    private int evaluateTempo(GameState state) {
        return state.redToMove ? 25 : -25;
    }

    /**
     * Advanced concepts (piece coordination, tactical patterns)
     */
    private int evaluateAdvancedConcepts(GameState state) {
        int eval = 0;

        // Piece coordination
        eval += positionalEval.evaluatePieceCoordination(state);

        // Control of strategic squares
        eval += positionalEval.evaluateStrategicControl(state);

        // Tactical patterns (pins, forks, etc.)
        eval += evaluateTacticalPatterns(state);

        return eval;
    }

    /**
     * Basic tactical pattern recognition
     */
    private int evaluateTacticalPatterns(GameState state) {
        // TODO: Implement pattern recognition
        // - Guard pins
        // - Tower batteries
        // - Discovered attacks
        return 0;
    }

    // === PHASE DETECTION ===

    /**
     * Detect current game phase for evaluation adaptation
     */
    public GamePhase detectGamePhase(GameState state) {
        int totalMaterial = materialEval.getTotalMaterial(state);
        boolean guardsAdvanced = positionalEval.areGuardsAdvanced(state);
        boolean guardsInDanger = safetyEval.areGuardsInDanger(state);

        if (totalMaterial <= 4 || (totalMaterial <= 6 && guardsAdvanced)) {
            return GamePhase.TABLEBASE;
        } else if (totalMaterial <= 8 || guardsAdvanced) {
            return GamePhase.ENDGAME;
        } else if (guardsInDanger || hasTacticalComplexity(state)) {
            return GamePhase.MIDDLEGAME;
        } else {
            return GamePhase.OPENING;
        }
    }

    private boolean hasTacticalComplexity(GameState state) {
        // Simple tactical complexity heuristic
        return safetyEval.countThreats(state) >= SearchConfig.TACTICAL_COMPLEXITY_THRESHOLD;
    }

    // === SPECIALIZED EVALUATION METHODS ===

    /**
     * Evaluate position for specific purposes (opening book, endgame, etc.)
     */
    public int evaluateForPhase(GameState state, GamePhase phase, int depth) {
        return switch (phase) {
            case OPENING -> evaluateOpening(state, depth);
            case MIDDLEGAME -> evaluateMiddlegame(state, depth);
            case ENDGAME -> evaluateEndgame(state, depth);
            case TABLEBASE -> evaluateTablebase(state, depth);
        };
    }

    private int evaluateOpening(GameState state, int depth) {
        int eval = 0;

        // Development is key in opening
        eval += positionalEval.evaluateDevelopment(state) * 40 / 100;
        eval += materialEval.evaluateMaterialBasic(state) * 30 / 100;
        eval += safetyEval.evaluateGuardSafety(state) * 20 / 100;
        eval += positionalEval.evaluateCentralControl(state) * 10 / 100;

        return eval;
    }

    private int evaluateMiddlegame(GameState state, int depth) {
        int eval = 0;

        // Tactical awareness is critical
        eval += materialEval.evaluateMaterialWithActivity(state) * 35 / 100;
        eval += safetyEval.evaluateGuardSafety(state) * 30 / 100;
        eval += positionalEval.evaluatePositional(state) * 20 / 100;
        eval += evaluateAdvancedConcepts(state) * 15 / 100;

        return eval;
    }

    private int evaluateEndgame(GameState state, int depth) {
        int eval = 0;

        // Guard advancement becomes paramount
        eval += positionalEval.evaluateGuardAdvancement(state) * 50 / 100;
        eval += materialEval.evaluateMaterialEndgame(state) * 30 / 100;
        eval += positionalEval.evaluateGuardActivity(state) * 20 / 100;

        return eval;
    }

    private int evaluateTablebase(GameState state, int depth) {
        // Perfect endgame evaluation (when tablebase is available)
        int eval = evaluateEndgame(state, depth);

        // Boost precision for tablebase positions
        if (Math.abs(eval) > 500) {
            eval = eval > 0 ? eval + 1000 + depth : eval - 1000 - depth;
        }

        return eval;
    }

    // === TIME MANAGEMENT INTEGRATION ===

    /**
     * Update remaining time for adaptive evaluation
     */
    public static void setRemainingTime(long timeMs) {
        remainingTimeMs = timeMs;
    }

    public static long getRemainingTime() {
        return remainingTimeMs;
    }

    // === EVALUATION STATISTICS ===

    /**
     * Get evaluation statistics for debugging
     */
    public String getEvaluationStats(GameState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== EVALUATION BREAKDOWN ===\n");

        GamePhase phase = detectGamePhase(state);
        sb.append("Phase: ").append(phase).append("\n");
        sb.append("Time Remaining: ").append(remainingTimeMs).append("ms\n");

        sb.append("Material: ").append(materialEval.evaluateMaterialBasic(state)).append("\n");
        sb.append("Positional: ").append(positionalEval.evaluatePositional(state)).append("\n");
        sb.append("Safety: ").append(safetyEval.evaluateGuardSafety(state)).append("\n");
        sb.append("Total: ").append(evaluate(state, 0)).append("\n");

        return sb.toString();
    }

    // === HELPER CLASSES ===

    private static class EvaluationResult {
        final boolean isTerminal;
        final int score;

        EvaluationResult(boolean isTerminal, int score) {
            this.isTerminal = isTerminal;
            this.score = score;
        }
    }

    public enum GamePhase {
        OPENING,
        MIDDLEGAME,
        ENDGAME,
        TABLEBASE
    }

    // === GETTERS FOR COMPONENTS ===

    public MaterialEval getMaterialEvaluator() { return materialEval; }
    public PositionalEval getPositionalEvaluator() { return positionalEval; }
    public SafetyEval getSafetyEvaluator() { return safetyEval; }
}
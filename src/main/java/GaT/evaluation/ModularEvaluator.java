package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.SearchConfig;

/**
 * MODULAR EVALUATOR - Intelligent Orchestration of Evaluation Modules
 *
 * This class integrates all specialized evaluation modules while maintaining
 * full backward compatibility with the existing Evaluator interface.
 *
 * Features:
 * - Phase-aware evaluation (Opening/Middlegame/Endgame)
 * - Time-adaptive strategies for tournament conditions
 * - Emergency mode for extreme time pressure
 * - Graceful degradation and error handling
 * - Full backward compatibility
 */
public class ModularEvaluator extends Evaluator {

    // === IMPORTED MODULES ===
    private final SafetyEval safetyModule;
    private final PositionalEval positionalModule;
    private final MaterialEval materialModule;

    // === CONFIGURATION ===
    private volatile boolean useModularEvaluation = true;
    private volatile EvaluationMode mode = EvaluationMode.STANDARD;

    // === PHASE WEIGHTS ===
    private static final class PhaseWeights {
        final double material;
        final double positional;
        final double safety;
        final double tactical;

        PhaseWeights(double material, double positional, double safety, double tactical) {
            this.material = material;
            this.positional = positional;
            this.safety = safety;
            this.tactical = tactical;
        }
    }

    // Predefined weight configurations
    private static final PhaseWeights OPENING_WEIGHTS = new PhaseWeights(0.30, 0.40, 0.20, 0.10);
    private static final PhaseWeights MIDDLEGAME_WEIGHTS = new PhaseWeights(0.35, 0.25, 0.25, 0.15);
    private static final PhaseWeights ENDGAME_WEIGHTS = new PhaseWeights(0.25, 0.35, 0.15, 0.25);
    private static final PhaseWeights TACTICAL_WEIGHTS = new PhaseWeights(0.20, 0.20, 0.30, 0.30);

    // === EVALUATION MODES ===
    public enum EvaluationMode {
        EMERGENCY,    // < 200ms - Ultra fast
        BLITZ,        // < 1000ms - Fast evaluation
        STANDARD,     // < 5000ms - Normal evaluation
        DEEP,         // > 5000ms - Full analysis
        ANALYSIS      // Unlimited - Complete evaluation
    }

    // === CONSTRUCTOR ===
    public ModularEvaluator() {
        // Initialize modules
        this.safetyModule = new SafetyEval();
        this.positionalModule = new PositionalEval();
        this.materialModule = new MaterialEval();

        System.out.println("🚀 ModularEvaluator initialized with all evaluation modules");
    }

    // === MAIN EVALUATION INTERFACE ===

    @Override
    public int evaluate(GameState state, int depth) {
        if (state == null) return 0;

        // Legacy compatibility - use base evaluator if disabled
        if (!useModularEvaluation) {
            return super.evaluate(state, depth);
        }

        try {
            // Determine evaluation mode based on time
            updateEvaluationMode();

            // Terminal position check (always fast)
            int terminalScore = checkTerminalPosition(state, depth);
            if (terminalScore != 0) {
                return terminalScore;
            }

            // Choose evaluation strategy based on mode
            return switch (mode) {
                case EMERGENCY -> evaluateEmergency(state, depth);
                case BLITZ -> evaluateBlitz(state, depth);
                case STANDARD -> evaluateStandard(state, depth);
                case DEEP -> evaluateDeep(state, depth);
                case ANALYSIS -> evaluateAnalysis(state, depth);
            };

        } catch (Exception e) {
            // Graceful fallback to base evaluator
            System.err.println("⚠️ ModularEvaluator error, falling back: " + e.getMessage());
            return super.evaluate(state, depth);
        }
    }

    // === EVALUATION STRATEGIES ===

    /**
     * EMERGENCY MODE - Ultra fast evaluation for extreme time pressure
     */
    private int evaluateEmergency(GameState state, int depth) {
        // Only material counting - fastest possible
        return materialModule.evaluateMaterialSimple(state);
    }

    /**
     * BLITZ MODE - Fast evaluation with basic features
     */
    private int evaluateBlitz(GameState state, int depth) {
        int eval = 0;

        // Material with basic positional (70%)
        eval += materialModule.evaluateMaterialBasic(state) * 70 / 100;

        // Basic guard safety (20%)
        eval += safetyModule.evaluateGuardSafetyBasic(state) * 20 / 100;

        // Fast positional (10%)
        eval += positionalModule.evaluateGuardAdvancementFast(state) * 10 / 100;

        return eval;
    }

    /**
     * STANDARD MODE - Balanced evaluation for normal time
     */
    private int evaluateStandard(GameState state, int depth) {
        // Detect game phase
        GamePhase phase = detectGamePhase(state);
        PhaseWeights weights = getPhaseWeights(phase);

        int eval = 0;

        // Apply weighted evaluation
        eval += (int)(materialModule.evaluateMaterialWithActivity(state) * weights.material);
        eval += (int)(positionalModule.evaluatePositional(state) * weights.positional);
        eval += (int)(safetyModule.evaluateGuardSafety(state) * weights.safety);

        // Add tactical awareness if critical
        if (isCriticalPosition(state)) {
            eval += (int)(evaluateTactical(state, depth) * weights.tactical);
        }

        return eval;
    }

    /**
     * DEEP MODE - Comprehensive evaluation with all features
     */
    private int evaluateDeep(GameState state, int depth) {
        GamePhase phase = detectGamePhase(state);
        PhaseWeights weights = getPhaseWeights(phase);

        int eval = 0;

        // Full module evaluation
        eval += (int)(materialModule.evaluateMaterialAdvanced(state) * weights.material);
        eval += (int)(positionalModule.evaluatePositionalAdvanced(state) * weights.positional);
        eval += (int)(safetyModule.evaluateGuardSafetyAdvanced(state) * weights.safety);
        eval += (int)(evaluateTactical(state, depth) * weights.tactical);

        // Phase-specific adjustments
        switch (phase) {
            case OPENING -> eval += evaluateOpeningSpecific(state);
            case MIDDLEGAME -> eval += evaluateMiddlegameSpecific(state);
            case ENDGAME -> eval += evaluateEndgameSpecific(state);
        }

        return eval;
    }

    /**
     * ANALYSIS MODE - Complete evaluation for infinite time
     */
    private int evaluateAnalysis(GameState state, int depth) {
        // Use all modules at maximum depth
        return evaluateDeep(state, depth);
    }

    // === PHASE DETECTION ===

    private GamePhase detectGamePhase(GameState state) {
        int totalMaterial = materialModule.getTotalMaterial(state);
        boolean guardsAdvanced = positionalModule.areGuardsAdvanced(state);

        if (totalMaterial <= 6) {
            return GamePhase.ENDGAME;
        } else if (totalMaterial <= 12 || guardsAdvanced) {
            return GamePhase.MIDDLEGAME;
        } else {
            return GamePhase.OPENING;
        }
    }

    private PhaseWeights getPhaseWeights(GamePhase phase) {
        return switch (phase) {
            case OPENING -> OPENING_WEIGHTS;
            case MIDDLEGAME -> MIDDLEGAME_WEIGHTS;
            case ENDGAME -> ENDGAME_WEIGHTS;
        };
    }

    // === SPECIALIZED EVALUATIONS ===

    private int evaluateOpeningSpecific(GameState state) {
        int bonus = 0;

        // Development is key in opening
        bonus += positionalModule.evaluateDevelopment(state);

        // Central control
        bonus += positionalModule.evaluateCentralControl(state) / 2;

        return bonus;
    }

    private int evaluateMiddlegameSpecific(GameState state) {
        int bonus = 0;

        // Tactical opportunities
        if (safetyModule.areGuardsInDanger(state)) {
            bonus += evaluateTactical(state, 0) / 2;
        }

        // Piece activity
        bonus += materialModule.evaluatePieceActivity(state) / 3;

        return bonus;
    }

    private int evaluateEndgameSpecific(GameState state) {
        int bonus = 0;

        // Guard activity is crucial
        bonus += positionalModule.evaluateGuardActivity(state);

        // Material becomes less important
        bonus += materialModule.evaluateMaterialEndgame(state) / 2;

        return bonus;
    }

    // === CRITICAL POSITION DETECTION ===

    private boolean isCriticalPosition(GameState state) {
        // Guards in danger
        if (safetyModule.isGuardInDanger(state, true) ||
                safetyModule.isGuardInDanger(state, false)) {
            return true;
        }

        // Many threats
        if (safetyModule.countThreats(state) >= 3) {
            return true;
        }

        // Late endgame
        if (materialModule.getTotalMaterial(state) <= 6) {
            return true;
        }

        return false;
    }

    // === TACTICAL EVALUATION ===

    private int evaluateTactical(GameState state, int depth) {
        // Basic tactical evaluation (can be expanded)
        int tactical = 0;

        // Check for immediate threats
        tactical += safetyModule.countThreats(state) * 50;

        // Guard safety is tactical
        if (safetyModule.isGuardInDanger(state, state.redToMove)) {
            tactical -= 200;
        }
        if (safetyModule.isGuardInDanger(state, !state.redToMove)) {
            tactical += 200;
        }

        return tactical;
    }

    // === TERMINAL POSITIONS ===

    private int checkTerminalPosition(GameState state, int depth) {
        // Guard captured
        if (state.redGuard == 0) return -GUARD_CAPTURE_SCORE - depth;
        if (state.blueGuard == 0) return GUARD_CAPTURE_SCORE + depth;

        // Castle reached
        boolean redWins = (state.redGuard & GameState.bit(RED_CASTLE)) != 0;
        boolean blueWins = (state.blueGuard & GameState.bit(BLUE_CASTLE)) != 0;

        if (redWins) return CASTLE_REACH_SCORE + depth;
        if (blueWins) return -CASTLE_REACH_SCORE - depth;

        return 0;
    }

    // === TIME MANAGEMENT ===

    private void updateEvaluationMode() {
        long remainingTime = getRemainingTime();

        if (remainingTime < 200) {
            mode = EvaluationMode.EMERGENCY;
        } else if (remainingTime < 1000) {
            mode = EvaluationMode.BLITZ;
        } else if (remainingTime < 5000) {
            mode = EvaluationMode.STANDARD;
        } else if (remainingTime < 20000) {
            mode = EvaluationMode.DEEP;
        } else {
            mode = EvaluationMode.ANALYSIS;
        }
    }

    // === CONFIGURATION METHODS ===

    /**
     * Enable/disable modular evaluation (for A/B testing)
     */
    public void setUseModularEvaluation(boolean use) {
        this.useModularEvaluation = use;
        System.out.println("ModularEvaluator: " + (use ? "ENABLED" : "DISABLED"));
    }

    /**
     * Force a specific evaluation mode
     */
    public void setEvaluationMode(EvaluationMode mode) {
        this.mode = mode;
        System.out.println("ModularEvaluator mode: " + mode);
    }

    /**
     * Get current evaluation mode
     */
    public EvaluationMode getEvaluationMode() {
        return mode;
    }

    // === DIAGNOSTICS ===

    /**
     * Get detailed evaluation breakdown (for debugging)
     */
    public String getEvaluationBreakdown(GameState state) {
        if (!useModularEvaluation) {
            return "ModularEvaluation disabled";
        }

        GamePhase phase = detectGamePhase(state);
        PhaseWeights weights = getPhaseWeights(phase);

        StringBuilder sb = new StringBuilder();
        sb.append("=== MODULAR EVALUATION BREAKDOWN ===\n");
        sb.append("Phase: ").append(phase).append("\n");
        sb.append("Mode: ").append(mode).append("\n");
        sb.append("Time remaining: ").append(getRemainingTime()).append("ms\n\n");

        sb.append("Components:\n");
        sb.append(String.format("  Material: %+d (%.0f%%)\n",
                materialModule.evaluateMaterialWithActivity(state), weights.material * 100));
        sb.append(String.format("  Positional: %+d (%.0f%%)\n",
                positionalModule.evaluatePositional(state), weights.positional * 100));
        sb.append(String.format("  Safety: %+d (%.0f%%)\n",
                safetyModule.evaluateGuardSafety(state), weights.safety * 100));
        sb.append(String.format("  Tactical: %+d (%.0f%%)\n",
                evaluateTactical(state, 0), weights.tactical * 100));

        sb.append("\nTotal: ").append(evaluate(state, 0));

        return sb.toString();
    }

    // === LEGACY COMPATIBILITY ===

    @Override
    public boolean isGuardInDanger(GameState state, boolean checkRed) {
        // Delegate to safety module
        return safetyModule.isGuardInDanger(state, checkRed);
    }

    // === CONSTANTS ===
    private static final int GUARD_CAPTURE_SCORE = 2500;
    private static final int CASTLE_REACH_SCORE = 3000;
    private static final int RED_CASTLE = GameState.getIndex(0, 3);
    private static final int BLUE_CASTLE = GameState.getIndex(6, 3);

    private enum GamePhase {
        OPENING, MIDDLEGAME, ENDGAME
    }
}
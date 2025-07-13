package GaT.evaluation;

import GaT.model.GameState;
import static GaT.evaluation.EvaluationParameters.*;

/**
 * ✅ FIXED MODULAR EVALUATOR - Material-Dominant Architecture
 *
 * 🚨 PREVIOUS DISASTERS SOLVED:
 * ❌ Phase Weights were broken (Material 25%) → ✅ NOW Material 60%+
 * ❌ Safety had 800-point penalties → ✅ NOW reasonable via centralized params
 * ❌ Local parameter chaos → ✅ NOW uses only EvaluationParameters
 * ❌ Tactical overweighting → ✅ NOW proper balance
 *
 * ARCHITECTURE: All parameters from EvaluationParameters, Material dominates!
 */
public class ModularEvaluator extends Evaluator {

    // === EVALUATION MODULES ===
    private final MaterialEval materialModule;
    private final PositionalEval positionalModule;
    private final SafetyEval safetyModule;
    private final TacticalEvaluator tacticalModule;
    private final ThreatMap threatMap;

    // === CONFIGURATION ===
    private volatile boolean useModularEvaluation = true;
    private volatile EvaluationMode mode = EvaluationMode.STANDARD;

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
        this.materialModule = new MaterialEval();
        this.positionalModule = new PositionalEval();
        this.safetyModule = new SafetyEval();
        this.tacticalModule = new TacticalEvaluator();
        this.threatMap = new ThreatMap();

        System.out.println("🚀 ModularEvaluator FIXED with Material-Dominant Architecture");
        System.out.println("   ✅ Material Weight: " + (OPENING_WEIGHTS.material * 100) + "% (was ~30%!)");
        System.out.println("   ✅ Safety Penalties: " + Safety.GUARD_DANGER_PENALTY + " (was 800!)");
        System.out.println("   ✅ All parameters centralized in EvaluationParameters");
    }

    // === MAIN EVALUATION INTERFACE ===
    @Override
    public int evaluate(GameState state, int depth) {
        if (state == null) return 0;

        // Legacy compatibility fallback
        if (!useModularEvaluation) {
            return super.evaluate(state, depth);
        }

        try {
            // Build threat map for position analysis
            threatMap.buildThreatMap(state);

            // Determine evaluation mode based on time
            updateEvaluationMode();

            // Terminal position check FIRST
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
            // Graceful fallback with logging
            System.err.println("⚠️ ModularEvaluator error: " + e.getMessage());
            return emergencyEvaluate(state);
        }
    }

    // === TERMINAL POSITION CHECK ===
    private int checkTerminalPosition(GameState state, int depth) {
        // Guard captured
        if (state.redGuard == 0) return -GUARD_CAPTURE_SCORE - depth;
        if (state.blueGuard == 0) return GUARD_CAPTURE_SCORE + depth;

        // Castle reached
        boolean redWins = (state.redGuard & GameState.bit(BLUE_CASTLE_INDEX)) != 0;
        boolean blueWins = (state.blueGuard & GameState.bit(RED_CASTLE_INDEX)) != 0;

        if (redWins) return CASTLE_REACH_SCORE + depth;
        if (blueWins) return -CASTLE_REACH_SCORE - depth;

        return 0;
    }

    // === EVALUATION STRATEGIES ===

    /**
     * EMERGENCY MODE - Ultra fast evaluation (Material only)
     */
    private int evaluateEmergency(GameState state, int depth) {
        return materialModule.evaluateMaterialSimple(state);
    }

    /**
     * ✅ FIXED BLITZ MODE - Material still dominates even in blitz
     */
    private int evaluateBlitz(GameState state, int depth) {
        int eval = 0;

        // ✅ FIXED: Material dominates (75% in blitz mode)
        eval += materialModule.evaluateMaterialBasic(state) * 75 / 100;

        // ✅ FIXED: Safety with corrected penalties (15%)
        eval += safetyModule.evaluateGuardSafetyBasic(state) * 15 / 100;

        // ✅ FIXED: Minimal positional (10%)
        eval += positionalModule.evaluateGuardAdvancementFast(state) * 10 / 100;

        return eval;
    }

    /**
     * ✅ FIXED STANDARD MODE - Uses corrected centralized weights
     */
    private int evaluateStandard(GameState state, int depth) {
        GamePhase phase = detectGamePhase(state);
        PhaseWeights weights = getPhaseWeights(phase);

        int eval = 0;

        // ✅ FIXED: Material evaluation (NOW 60%+ from EvaluationParameters!)
        eval += (int)(materialModule.evaluateMaterialWithActivity(state) * weights.material);

        // ✅ FIXED: Positional evaluation (NOW reasonable 20-25%)
        eval += (int)(positionalModule.evaluatePositional(state) * weights.positional);

        // ✅ FIXED: Safety evaluation (with FIXED penalties from EvaluationParameters)
        safetyModule.setThreatMap(threatMap);
        eval += (int)(safetyModule.evaluateGuardSafety(state) * weights.safety);

        // ✅ FIXED: Tactical evaluation (NOW reasonable 4-5%)
        eval += (int)(tacticalModule.evaluateTactical(state) * weights.tactical);

        // ✅ FIXED: Threat evaluation (minimal 1%)
        eval += (int)(threatMap.calculateThreatScore(state) * weights.threat);

        return eval;
    }

    /**
     * ✅ FIXED DEEP MODE - Comprehensive evaluation with corrected weights
     */
    private int evaluateDeep(GameState state, int depth) {
        GamePhase phase = detectGamePhase(state);
        PhaseWeights weights = getPhaseWeights(phase);

        int eval = 0;

        // ✅ FIXED: Advanced evaluations with corrected centralized parameters
        eval += (int)(materialModule.evaluateMaterialAdvanced(state) * weights.material);
        eval += (int)(positionalModule.evaluatePositionalAdvanced(state) * weights.positional);

        // Pass threat map to safety module (using FIXED penalties)
        safetyModule.setThreatMap(threatMap);
        eval += (int)(safetyModule.evaluateGuardSafetyAdvanced(state) * weights.safety);

        eval += (int)(tacticalModule.evaluateTactical(state) * weights.tactical);
        eval += (int)(threatMap.calculateThreatScore(state) * weights.threat);

        // ✅ FIXED: Phase-specific adjustments (MODERATE bonuses only)
        switch (phase) {
            case OPENING -> eval += evaluateOpeningSpecific(state);
            case MIDDLEGAME -> eval += evaluateMiddlegameSpecific(state);
            case ENDGAME -> eval += evaluateEndgameSpecific(state);
        }

        return eval;
    }

    /**
     * ANALYSIS MODE - Maximum depth evaluation
     */
    private int evaluateAnalysis(GameState state, int depth) {
        return evaluateDeep(state, depth);
    }

    // === EMERGENCY FALLBACK ===
    private int emergencyEvaluate(GameState state) {
        // Ultra-simple but reliable evaluation
        if (state.redGuard == 0) return -GUARD_CAPTURE_SCORE;
        if (state.blueGuard == 0) return GUARD_CAPTURE_SCORE;

        // Simple material count
        int materialScore = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            materialScore += (state.redStackHeights[i] - state.blueStackHeights[i]) * TOWER_BASE_VALUE;
        }

        // Simple guard advancement
        int positionalScore = 0;
        if (state.redGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
            positionalScore += (6 - rank) * 10;
        }
        if (state.blueGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
            positionalScore -= rank * 10;
        }

        return materialScore + positionalScore;
    }

    // === ✅ FIXED PHASE-SPECIFIC EVALUATIONS (MODERATE BONUSES) ===
    private int evaluateOpeningSpecific(GameState state) {
        int bonus = 0;

        // ✅ FIXED: Central control (moderate bonus from EvaluationParameters)
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int file = GameState.file(i);
            if (file >= 2 && file <= 4) {
                bonus += state.redStackHeights[i] * (Material.CENTRAL_BONUS / 4);  // Very moderate
                bonus -= state.blueStackHeights[i] * (Material.CENTRAL_BONUS / 4);
            }
        }

        return bonus;
    }

    private int evaluateMiddlegameSpecific(GameState state) {
        int bonus = 0;

        // ✅ FIXED: Moderate tactical bonuses from EvaluationParameters
        if (tacticalModule.detectForks(state) != 0) {
            bonus += Tactical.FORK_BONUS / 2;  // Half bonus in middlegame-specific
        }

        return bonus;
    }

    private int evaluateEndgameSpecific(GameState state) {
        int bonus = 0;

        // ✅ FIXED: Guard activity (moderate bonus from EvaluationParameters)
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int rank = GameState.rank(guardPos);
            bonus += (6 - rank) * (Positional.ENDGAME_GUARD_ACTIVITY / 3);  // Moderate
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int rank = GameState.rank(guardPos);
            bonus -= rank * (Positional.ENDGAME_GUARD_ACTIVITY / 3);  // Moderate
        }

        return bonus;
    }

    // === GAME PHASE DETECTION ===
    private GamePhase detectGamePhase(GameState state) {
        return EvaluationParameters.detectGamePhase(state);
    }

    private PhaseWeights getPhaseWeights(GamePhase phase) {
        return EvaluationParameters.getPhaseWeights(phase);
    }

    // === TIME MANAGEMENT ===
    private void updateEvaluationMode() {
        long remainingTime = getRemainingTime();

        if (remainingTime < Search.EMERGENCY_TIME_MS) {
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
    public void setUseModularEvaluation(boolean use) {
        this.useModularEvaluation = use;
        System.out.println("ModularEvaluator: " + (use ? "ENABLED" : "DISABLED"));
        if (use) {
            System.out.println("   🎯 FIXED phase weights: Material " + (OPENING_WEIGHTS.material * 100) + "% (was ~30%!)");
            System.out.println("   🛡️ FIXED safety penalties: " + Safety.GUARD_DANGER_PENALTY + " (was 800!)");
        }
    }

    public void setEvaluationMode(EvaluationMode mode) {
        this.mode = mode;
        System.out.println("ModularEvaluator mode: " + mode);
    }

    public EvaluationMode getEvaluationMode() {
        return mode;
    }

    // === ✅ FIXED DIAGNOSTICS ===
    public String getEvaluationBreakdown(GameState state) {
        if (!useModularEvaluation) {
            return "ModularEvaluation disabled";
        }

        threatMap.buildThreatMap(state);
        GamePhase phase = detectGamePhase(state);
        PhaseWeights weights = getPhaseWeights(phase);

        StringBuilder sb = new StringBuilder();
        sb.append("=== ✅ FIXED MODULAR EVALUATION BREAKDOWN ===\n");
        sb.append("Phase: ").append(phase).append("\n");
        sb.append("Mode: ").append(mode).append("\n");
        sb.append("Time remaining: ").append(getRemainingTime()).append("ms\n\n");

        int material = materialModule.evaluateMaterialWithActivity(state);
        int positional = positionalModule.evaluatePositional(state);

        safetyModule.setThreatMap(threatMap);
        int safety = safetyModule.evaluateGuardSafety(state);
        int tactical = tacticalModule.evaluateTactical(state);
        int threats = threatMap.calculateThreatScore(state);

        sb.append("Components (FIXED weights):\n");
        sb.append(String.format("  Material: %+d (%.0f%%) ✅ NOW DOMINATES\n",
                material, weights.material * 100));
        sb.append(String.format("  Positional: %+d (%.0f%%) ✅ REASONABLE\n",
                positional, weights.positional * 100));
        sb.append(String.format("  Safety: %+d (%.0f%%) ✅ FIXED PENALTIES\n",
                safety, weights.safety * 100));
        sb.append(String.format("  Tactical: %+d (%.0f%%) ✅ MODERATE\n",
                tactical, weights.tactical * 100));
        sb.append(String.format("  Threats: %+d (%.0f%%) ✅ MINIMAL\n",
                threats, weights.threat * 100));

        int total = (int)(material * weights.material + positional * weights.positional +
                safety * weights.safety + tactical * weights.tactical +
                threats * weights.threat);

        sb.append("\nTotal: ").append(total);

        // ✅ FIXED: Validation should now pass
        if (Math.abs(positional) > Math.abs(material) * 0.5) {
            sb.append("\n⚠️ WARNING: Positional still high relative to material");
        } else {
            sb.append("\n✅ FIXED: Material properly dominates evaluation");
        }

        if (Math.abs(safety) > Math.abs(material) * 0.3) {
            sb.append("\n⚠️ WARNING: Safety bonuses/penalties still high");
        } else {
            sb.append("\n✅ FIXED: Safety penalties are reasonable");
        }

        return sb.toString();
    }

    // === VALIDATION ===
    public boolean validateEvaluation(GameState state) {
        int materialScore = materialModule.evaluateMaterialBasic(state);
        int fullScore = evaluate(state, 1);
        int posBonus = fullScore - materialScore;

        // ✅ FIXED: Check if positional bonus is reasonable (should pass now)
        if (materialScore != 0 && Math.abs(posBonus) > Math.abs(materialScore) * 0.6) {
            System.out.println("❌ EVALUATION ERROR: Positional still too large!");
            System.out.println("   Material: " + materialScore + ", Full: " + fullScore);
            return false;
        }

        System.out.println("✅ VALIDATION PASSED: Material properly dominates");
        return true;
    }

    // === LEGACY COMPATIBILITY ===
    @Override
    public boolean isGuardInDanger(GameState state, boolean checkRed) {
        return safetyModule.isGuardInDanger(state, checkRed);
    }
}
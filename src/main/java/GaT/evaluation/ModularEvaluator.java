package GaT.evaluation;

import GaT.model.GameState;
import GaT.model.SearchConfig;

/**
 * MODULAR EVALUATOR - Complete Implementation with All Modules
 *
 * ✅ UPDATED WEIGHTS FOR AGGRESSIVE TACTICAL PLAY!
 * ✅ All new parameters now properly integrated
 * ✅ Enhanced tactical evaluation priority
 * ✅ Better coordination and edge activation
 *
 * Integrates all evaluation modules for Guards and Towers:
 * - MaterialEval: Piece values and material balance
 * - PositionalEval: Guard advancement and piece positioning (with TOWER_CHAIN_BONUS)
 * - SafetyEval: Guard safety and threat detection
 * - TacticalEvaluator: Pattern recognition (with all new parameters)
 * - ThreatMap: Multi-turn threat analysis
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

    // === PHASE WEIGHTS ===
    private static final class PhaseWeights {
        final double material;
        final double positional;
        final double safety;
        final double tactical;
        final double threat;

        PhaseWeights(double material, double positional, double safety, double tactical, double threat) {
            this.material = material;
            this.positional = positional;
            this.safety = safety;
            this.tactical = tactical;
            this.threat = threat;
        }
    }

    // ✅ UPDATED WEIGHTS FOR AGGRESSIVE TACTICAL PLAY!
    // Weight configurations for different game phases
    private static final PhaseWeights OPENING_WEIGHTS =
            new PhaseWeights(0.25, 0.30, 0.15, 0.25, 0.05);  // +Tactical! (war 0.10)
    private static final PhaseWeights MIDDLEGAME_WEIGHTS =
            new PhaseWeights(0.25, 0.20, 0.15, 0.30, 0.10);  // +Tactical! (war 0.15)
    private static final PhaseWeights ENDGAME_WEIGHTS =
            new PhaseWeights(0.30, 0.25, 0.20, 0.20, 0.05);  // Ausgewogen

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
        // Initialize all modules
        this.materialModule = new MaterialEval();
        this.positionalModule = new PositionalEval();
        this.safetyModule = new SafetyEval();
        this.tacticalModule = new TacticalEvaluator();
        this.threatMap = new ThreatMap();

        System.out.println("🚀 ModularEvaluator initialized with AGGRESSIVE TACTICAL WEIGHTS");
        System.out.println("   ✅ EDGE_ACTIVATION_BONUS: Active");
        System.out.println("   ✅ CLUSTER_FORMATION_BONUS: Active");
        System.out.println("   ✅ SUPPORTING_ATTACK_BONUS: Active");
        System.out.println("   ✅ TOWER_CHAIN_BONUS: Active");
    }

    // === MAIN EVALUATION INTERFACE ===

    @Override
    public int evaluate(GameState state, int depth) {
        if (state == null) return 0;

        // Legacy compatibility
        if (!useModularEvaluation) {
            return super.evaluate(state, depth);
        }

        try {
            // Build threat map for position analysis
            threatMap.buildThreatMap(state);

            // Determine evaluation mode based on time
            updateEvaluationMode();

            // Terminal position check
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
            // Graceful fallback
            System.err.println("⚠️ ModularEvaluator error: " + e.getMessage());
            return super.evaluate(state, depth);
        }
    }

    // === EVALUATION STRATEGIES ===

    /**
     * EMERGENCY MODE - Ultra fast evaluation
     */
    private int evaluateEmergency(GameState state, int depth) {
        // Only material counting
        return materialModule.evaluateMaterialSimple(state);
    }

    /**
     * BLITZ MODE - Fast evaluation with basics
     */
    private int evaluateBlitz(GameState state, int depth) {
        int eval = 0;

        // Material (60%) - reduced to make room for tactical
        eval += materialModule.evaluateMaterialBasic(state) * 60 / 100;

        // ✅ Tactical gets priority even in blitz mode! (25%)
        eval += tacticalModule.evaluateTactical(state) * 25 / 100;

        // Basic safety (10%)
        eval += safetyModule.evaluateGuardSafetyBasic(state) * 10 / 100;

        // Fast positional (5%)
        eval += positionalModule.evaluateGuardAdvancementFast(state) * 5 / 100;

        return eval;
    }

    /**
     * ✅ STANDARD MODE - ENHANCED WITH NEW PARAMETERS!
     */
    private int evaluateStandard(GameState state, int depth) {
        GamePhase phase = detectGamePhase(state);
        PhaseWeights weights = getPhaseWeights(phase);

        int eval = 0;

        // Material evaluation
        eval += (int)(materialModule.evaluateMaterialWithActivity(state) * weights.material);

        // ✅ Positional evaluation (NOW WITH TOWER_CHAIN_BONUS!)
        eval += (int)(positionalModule.evaluatePositional(state) * weights.positional);

        // Safety evaluation (pass threat map for integration)
        safetyModule.setThreatMap(threatMap);
        eval += (int)(safetyModule.evaluateGuardSafety(state) * weights.safety);

        // ✅ ENHANCED Tactical evaluation (NOW WITH ALL NEW PARAMETERS!)
        eval += (int)(tacticalModule.evaluateTactical(state) * weights.tactical);

        // Threat evaluation
        eval += (int)(threatMap.calculateThreatScore(state) * weights.threat);

        return eval;
    }

    /**
     * ✅ DEEP MODE - ALL PARAMETERS ACTIVE!
     */
    private int evaluateDeep(GameState state, int depth) {
        GamePhase phase = detectGamePhase(state);
        PhaseWeights weights = getPhaseWeights(phase);

        int eval = 0;

        // Advanced evaluations
        eval += (int)(materialModule.evaluateMaterialAdvanced(state) * weights.material);

        // ✅ Advanced positional (WITH TOWER_CHAIN_BONUS!)
        eval += (int)(positionalModule.evaluatePositionalAdvanced(state) * weights.positional);

        // Pass threat map to safety module
        safetyModule.setThreatMap(threatMap);
        eval += (int)(safetyModule.evaluateGuardSafetyAdvanced(state) * weights.safety);

        // ✅ Full tactical evaluation (ALL NEW PARAMETERS ACTIVE!)
        eval += (int)(tacticalModule.evaluateTactical(state) * weights.tactical);

        eval += (int)(threatMap.calculateThreatScore(state) * weights.threat);

        // Phase-specific adjustments
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
        // Use deep evaluation with extra analysis
        return evaluateDeep(state, depth);
    }

    // === GAME PHASE DETECTION ===

    private enum GamePhase {
        OPENING, MIDDLEGAME, ENDGAME
    }

    private GamePhase detectGamePhase(GameState state) {
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        // Check if guards are advanced
        boolean guardsAdvanced = false;
        if (state.redGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.redGuard));
            if (rank <= 2) guardsAdvanced = true;
        }
        if (state.blueGuard != 0) {
            int rank = GameState.rank(Long.numberOfTrailingZeros(state.blueGuard));
            if (rank >= 4) guardsAdvanced = true;
        }

        // Phase determination
        if (totalMaterial <= 6 || (totalMaterial <= 8 && guardsAdvanced)) {
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

    // === ✅ ENHANCED PHASE-SPECIFIC EVALUATIONS ===

    private int evaluateOpeningSpecific(GameState state) {
        int bonus = 0;

        // Central control is important in opening
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            int file = GameState.file(i);
            if (file >= 2 && file <= 4) { // Central files
                bonus += state.redStackHeights[i] * 10;
                bonus -= state.blueStackHeights[i] * 10;
            }
        }

        // ✅ Edge activation bonus in opening
        bonus += evaluateEdgeActivationOpening(state);

        return bonus;
    }

    private int evaluateMiddlegameSpecific(GameState state) {
        int bonus = 0;

        // ✅ Enhanced piece coordination and tactics
        if (tacticalModule.detectForks(state) != 0) {
            bonus += 100; // Erhöht von 50
        }

        // ✅ Cluster formation bonus
        bonus += tacticalModule.evaluateClusterFormation(state) / 2; // Halber Bonus hier

        return bonus;
    }

    private int evaluateEndgameSpecific(GameState state) {
        int bonus = 0;

        // Guard activity is crucial
        if (state.redGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.redGuard);
            int rank = GameState.rank(guardPos);
            bonus += (6 - rank) * 75; // Erhöht von 50
        }

        if (state.blueGuard != 0) {
            int guardPos = Long.numberOfTrailingZeros(state.blueGuard);
            int rank = GameState.rank(guardPos);
            bonus -= rank * 75; // Erhöht von 50
        }

        return bonus;
    }

    // === ✅ NEUE HILFSMETHODEN ===

    /**
     * ✅ Edge activation specifically for opening
     */
    private int evaluateEdgeActivationOpening(GameState state) {
        int bonus = 0;
        int[] edgeFiles = {0, 1, 5, 6};

        for (int file : edgeFiles) {
            for (int rank = 3; rank < 7; rank++) { // Nur hintere Ränge in Eröffnung
                int pos = GameState.getIndex(rank, file);

                if (state.redStackHeights[pos] > 0) {
                    bonus += 15; // Entwicklung der Randtürme
                }
                if (state.blueStackHeights[pos] > 0) {
                    bonus -= 15;
                }
            }
        }

        return bonus;
    }

    // === TERMINAL POSITIONS ===

    private int checkTerminalPosition(GameState state, int depth) {
        // Guard captured
        if (state.redGuard == 0) return -GUARD_CAPTURE_SCORE - depth;
        if (state.blueGuard == 0) return GUARD_CAPTURE_SCORE + depth;

        // Castle reached
        int redCastle = GameState.getIndex(0, 3); // D1
        int blueCastle = GameState.getIndex(6, 3); // D7

        boolean redWins = (state.redGuard & GameState.bit(redCastle)) != 0;
        boolean blueWins = (state.blueGuard & GameState.bit(blueCastle)) != 0;

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

    public void setUseModularEvaluation(boolean use) {
        this.useModularEvaluation = use;
        System.out.println("ModularEvaluator: " + (use ? "ENABLED" : "DISABLED"));
        if (use) {
            System.out.println("   🎯 Aggressive tactical weights active!");
            System.out.println("   ⚔️ Edge activation, cluster formation, supporting attacks!");
        }
    }

    public void setEvaluationMode(EvaluationMode mode) {
        this.mode = mode;
        System.out.println("ModularEvaluator mode: " + mode);
    }

    public EvaluationMode getEvaluationMode() {
        return mode;
    }

    // === ✅ ENHANCED DIAGNOSTICS ===

    public String getEvaluationBreakdown(GameState state) {
        if (!useModularEvaluation) {
            return "ModularEvaluation disabled";
        }

        // Rebuild threat map for analysis
        threatMap.buildThreatMap(state);

        GamePhase phase = detectGamePhase(state);
        PhaseWeights weights = getPhaseWeights(phase);

        StringBuilder sb = new StringBuilder();
        sb.append("=== ✅ ENHANCED MODULAR EVALUATION BREAKDOWN ===\n");
        sb.append("Phase: ").append(phase).append("\n");
        sb.append("Mode: ").append(mode).append("\n");
        sb.append("Time remaining: ").append(getRemainingTime()).append("ms\n\n");

        sb.append("Components:\n");

        int material = materialModule.evaluateMaterialWithActivity(state);
        int positional = positionalModule.evaluatePositional(state);
        int tactical = tacticalModule.evaluateTactical(state);

        sb.append(String.format("  Material: %+d (%.0f%%)\n", material, weights.material * 100));
        sb.append(String.format("  Positional: %+d (%.0f%%) ✅ WITH TOWER_CHAINS\n", positional, weights.positional * 100));

        // Pass threat map to safety module for breakdown
        safetyModule.setThreatMap(threatMap);
        int safety = safetyModule.evaluateGuardSafety(state);
        sb.append(String.format("  Safety: %+d (%.0f%%)\n", safety, weights.safety * 100));

        sb.append(String.format("  Tactical: %+d (%.0f%%) ✅ WITH NEW PARAMETERS\n", tactical, weights.tactical * 100));

        int threats = threatMap.calculateThreatScore(state);
        sb.append(String.format("  Threats: %+d (%.0f%%)\n", threats, weights.threat * 100));

        // ✅ Zeige Details der neuen Parameter
        sb.append("\n🎯 New Parameter Details:\n");
        sb.append(String.format("  Edge Activation: %+d\n", tacticalModule.evaluateEdgeActivation(state)));
        sb.append(String.format("  Cluster Formation: %+d\n", tacticalModule.evaluateClusterFormation(state)));
        sb.append(String.format("  Supporting Attacks: %+d\n", tacticalModule.evaluateSupportingAttacks(state)));
        sb.append(String.format("  Tower Chains: %+d\n", positionalModule.evaluateTowerChains(state)));

        sb.append("\nTotal: ").append(evaluate(state, 0));

        return sb.toString();
    }

    // === LEGACY COMPATIBILITY ===

    @Override
    public boolean isGuardInDanger(GameState state, boolean checkRed) {
        return safetyModule.isGuardInDanger(state, checkRed);
    }

    // === CONSTANTS ===
    private static final int GUARD_CAPTURE_SCORE = 2500;
    private static final int CASTLE_REACH_SCORE = 3000;
}
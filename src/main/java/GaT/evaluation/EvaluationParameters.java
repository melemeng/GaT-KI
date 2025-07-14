package GaT.evaluation;

import GaT.model.GameState;

/**
 * ✅ FIXED EVALUATION PARAMETERS - Material-Dominant Architecture
 *
 * 🚨 PREVIOUS PROBLEMS SOLVED:
 * ❌ Material got only 25-30% weight → ✅ NOW 60%+
 * ❌ Safety penalties were 800 points → ✅ NOW reasonable 120-150
 * ❌ Parameter chaos across classes → ✅ NOW centralized
 * ❌ Tactical overweights → ✅ NOW balanced 5-8%
 *
 * CORE PRINCIPLE: Material evaluation must dominate in Turm & Wächter!
 */
public class EvaluationParameters {

    // === CORE PIECE VALUES (NEVER CHANGE) ===
    public static final int TOWER_BASE_VALUE = 100;
    public static final int GUARD_BASE_VALUE = 50;
    public static final int CASTLE_REACH_SCORE = 5000;
    public static final int GUARD_CAPTURE_SCORE = 4000;

    // === CASTLE INDICES ===
    public static final int RED_CASTLE_INDEX = GameState.getIndex(6, 3);  // D7
    public static final int BLUE_CASTLE_INDEX = GameState.getIndex(0, 3); // D1

    // === MATERIAL EVALUATION PARAMETERS (MODERATE) ===
    public static class Material {
        // ✅ FIXED: Moderate bonuses that enhance but don't dominate material
        public static final int ADVANCEMENT_BONUS = 8;           // Per rank advanced
        public static final int CENTRAL_BONUS = 12;              // Central file control
        public static final int CONNECTED_BONUS = 15;            // Adjacent friendly pieces
        public static final int MOBILITY_BONUS = 6;              // Per legal move
        public static final int OUTPOST_BONUS = 18;              // Deep enemy territory
        public static final int EDGE_TOWER_BONUS = 10;           // Active edge towers
        public static final int DEEP_PENETRATION_BONUS = 20;     // Very advanced pieces
        public static final int GUARD_ESCORT_BONUS = 15;         // Pieces near guard

        // ✅ FIXED: Moderate phase multipliers
        public static final double[] OPENING_MULTIPLIERS = {1.0, 0.9};    // [tower, guard]
        public static final double[] MIDDLEGAME_MULTIPLIERS = {1.1, 1.0};
        public static final double[] ENDGAME_MULTIPLIERS = {1.2, 1.5};
    }

    // === POSITIONAL EVALUATION PARAMETERS (MODERATE) ===
    public static class Positional {
        // ✅ FIXED: Moderate positional bonuses
        public static final int GUARD_ADVANCEMENT_BONUS = 15;     // Per rank toward enemy castle
        public static final int CENTRAL_CONTROL_BONUS = 18;       // Control of central squares
        public static final int MOBILITY_BONUS = 8;               // General piece mobility
        public static final int COORDINATION_BONUS = 12;          // Piece coordination
        public static final int DEVELOPMENT_BONUS = 25;           // Off back rank

        // Turm & Wächter specific bonuses (moderate)
        public static final int TOWER_HEIGHT_BONUS = 8;           // Height bonus in center
        public static final int GUARD_CASTLE_APPROACH = 20;       // Guard approaching castle
        public static final int DEFENSIVE_FORMATION_BONUS = 12;   // Towers protecting guard
        public static final int ENDGAME_GUARD_ACTIVITY = 30;      // Guard activity in endgame
        public static final int TOWER_CHAIN_BONUS = 20;           // Connected towers

        // Strategic square definitions
        public static final int[] CENTRAL_SQUARES = {
                GameState.getIndex(2, 3), GameState.getIndex(3, 3), GameState.getIndex(4, 3),
                GameState.getIndex(3, 2), GameState.getIndex(3, 4)
        };

        public static final int[] ADVANCED_SQUARES = {
                GameState.getIndex(1, 3), GameState.getIndex(5, 3),
                GameState.getIndex(2, 2), GameState.getIndex(2, 4),
                GameState.getIndex(4, 2), GameState.getIndex(4, 4)
        };
    }

    // === SAFETY EVALUATION PARAMETERS (FIXED - MUCH LOWER) ===
    public static class Safety {
        // ✅ FIXED: Reasonable penalties that don't break evaluation
        public static final int GUARD_DANGER_PENALTY = 120;       // Was 800! → NOW reasonable
        public static final int GUARD_PROTECTION_BONUS = 30;      // Guard well defended
        public static final int ESCAPE_ROUTE_BONUS = 15;          // Guard mobility options
        public static final int DEFENDER_BONUS = 20;              // Piece defending guard
        public static final int ISOLATED_PENALTY = 25;            // Piece without support

        // Moderate safety bonuses
        public static final int CLUSTER_PROTECTION_BONUS = 25;    // Protection by tower cluster
        public static final int COORDINATED_DEFENSE_BONUS = 20;   // Coordinated defense
        public static final int GUARD_ESCORT_SAFETY = 35;         // Close guard escort
        public static final int EMERGENCY_SUPPORT_BONUS = 25;     // Emergency support available

        // Moderate tactical vulnerabilities
        public static final int PIN_PENALTY = 60;                 // Piece pinned to guard
        public static final int FORK_PENALTY = 80;                // Multiple pieces attacked
        public static final int OVERLOADED_DEFENDER_PENALTY = 50; // Defender protecting multiple targets
    }

    // === TACTICAL EVALUATION PARAMETERS (MODERATE) ===
    public static class Tactical {
        // ✅ FIXED: Moderate tactical bonuses
        public static final int FORK_BONUS = 45;                  // Attacking 2+ pieces
        public static final int PIN_BONUS = 35;                   // Pinning enemy pieces
        public static final int DISCOVERY_BONUS = 30;             // Discovery attacks
        public static final int FORCING_MOVE_BONUS = 20;          // Moves that demand response

        // Enhanced tactical concepts (moderate)
        public static final int EDGE_ACTIVATION_BONUS = 12;       // Edge tower activation
        public static final int CLUSTER_FORMATION_BONUS = 15;     // Coordinated tower clusters
        public static final int SUPPORTING_ATTACK_BONUS = 10;     // Supported attacks
        public static final int CLUSTER_BONUS = 12;               // General cluster coordination
        public static final int TOWER_COORDINATION_BONUS = 18;    // Towers supporting each other
        public static final int GUARD_PROTECTION_CLUSTER = 15;    // Guard protected by cluster
    }

    // === ✅ FIXED PHASE WEIGHTS - MATERIAL NOW DOMINATES ===
    public static class PhaseWeights {
        public final double material;
        public final double positional;
        public final double safety;
        public final double tactical;
        public final double threat;

        public PhaseWeights(double material, double positional, double safety, double tactical, double threat) {
            this.material = material;
            this.positional = positional;
            this.safety = safety;
            this.tactical = tactical;
            this.threat = threat;

            // Validation: weights should sum to ~1.0
            double sum = material + positional + safety + tactical + threat;
            if (Math.abs(sum - 1.0) > 0.01) {
                System.err.println("⚠️ WARNING: Phase weights sum to " + sum + ", should be 1.0");
            }

            // ✅ FIXED: Material must dominate (was failing before!)
            if (material < 0.55) {
                System.err.println("⚠️ WARNING: Material weight too low: " + material + " (should be 55%+)");
            }
        }
    }

    // ✅ FIXED: Material-dominant phase weights (was broken before!)
    public static final PhaseWeights OPENING_WEIGHTS =
            new PhaseWeights(0.65, 0.20, 0.10, 0.04, 0.01);      // Material 65%!

    public static final PhaseWeights MIDDLEGAME_WEIGHTS =
            new PhaseWeights(0.60, 0.22, 0.12, 0.05, 0.01);      // Material 60%!

    public static final PhaseWeights ENDGAME_WEIGHTS =
            new PhaseWeights(0.55, 0.25, 0.15, 0.04, 0.01);      // Material 55%!

    // === SEARCH AND TIMING PARAMETERS ===
    public static class Search {
        public static final int MAX_QUIESCENCE_DEPTH = 16;
        public static final double TIME_ALLOCATION_CONSERVATIVE = 0.05;  // 5% per move
        public static final double TIME_ALLOCATION_AGGRESSIVE = 0.08;    // 8% per move
        public static final int EMERGENCY_TIME_MS = 200;
        public static final int PANIC_TIME_MS = 50;
    }

    // === GAME PHASE DETECTION ===
    public static GamePhase detectGamePhase(GameState state) {
        int totalMaterial = 0;
        for (int i = 0; i < GameState.NUM_SQUARES; i++) {
            totalMaterial += state.redStackHeights[i] + state.blueStackHeights[i];
        }

        if (totalMaterial <= 6) return GamePhase.ENDGAME;
        if (totalMaterial <= 12) return GamePhase.MIDDLEGAME;
        return GamePhase.OPENING;
    }

    /**
     * Get phase weights based on game phase
     */
    public static PhaseWeights getPhaseWeights(GamePhase phase) {
        return switch (phase) {
            case OPENING -> OPENING_WEIGHTS;
            case MIDDLEGAME -> MIDDLEGAME_WEIGHTS;
            case ENDGAME -> ENDGAME_WEIGHTS;
        };
    }

    // === VALIDATION ===
    public static boolean validateParameters() {
        boolean valid = true;

        // ✅ FIXED: Check material bonuses don't exceed 20% of piece value (was 25%)
        int maxMaterialBonus = Math.max(
                Math.max(Material.ADVANCEMENT_BONUS, Material.CENTRAL_BONUS),
                Math.max(Material.CONNECTED_BONUS, Material.MOBILITY_BONUS)
        );

        if (maxMaterialBonus > TOWER_BASE_VALUE * 0.20) {
            System.err.println("❌ Material bonus too large: " + maxMaterialBonus);
            valid = false;
        }

        // ✅ FIXED: Check safety penalties are reasonable (not 800!)
        if (Safety.GUARD_DANGER_PENALTY > TOWER_BASE_VALUE * 1.5) {
            System.err.println("❌ Guard danger penalty too large: " + Safety.GUARD_DANGER_PENALTY);
            valid = false;
        }

        // ✅ FIXED: Check phase weights - material must dominate
        if (OPENING_WEIGHTS.material < 0.55) {
            System.err.println("❌ Opening material weight too low: " + OPENING_WEIGHTS.material);
            valid = false;
        }

        return valid;
    }

    /**
     * Print all parameters for debugging
     */
    public static void printAllParameters() {
        System.out.println("=== ✅ FIXED EVALUATION PARAMETERS ===");
        System.out.println("Material (moderate):");
        System.out.println("  ADVANCEMENT_BONUS: " + Material.ADVANCEMENT_BONUS);
        System.out.println("  CENTRAL_BONUS: " + Material.CENTRAL_BONUS);
        System.out.println("  CONNECTED_BONUS: " + Material.CONNECTED_BONUS);

        System.out.println("Positional (moderate):");
        System.out.println("  GUARD_ADVANCEMENT_BONUS: " + Positional.GUARD_ADVANCEMENT_BONUS);
        System.out.println("  CENTRAL_CONTROL_BONUS: " + Positional.CENTRAL_CONTROL_BONUS);
        System.out.println("  TOWER_CHAIN_BONUS: " + Positional.TOWER_CHAIN_BONUS);

        System.out.println("Safety (FIXED - was 800!):");
        System.out.println("  GUARD_DANGER_PENALTY: " + Safety.GUARD_DANGER_PENALTY + " (was 800!)");
        System.out.println("  GUARD_PROTECTION_BONUS: " + Safety.GUARD_PROTECTION_BONUS);

        System.out.println("Tactical (moderate):");
        System.out.println("  FORK_BONUS: " + Tactical.FORK_BONUS);
        System.out.println("  CLUSTER_FORMATION_BONUS: " + Tactical.CLUSTER_FORMATION_BONUS);

        System.out.println("✅ FIXED Phase Weights (Material now dominates!):");
        System.out.println("  OPENING: Material " + (OPENING_WEIGHTS.material * 100) + "% (was ~30%!)");
        System.out.println("  MIDDLEGAME: Material " + (MIDDLEGAME_WEIGHTS.material * 100) + "%");
        System.out.println("  ENDGAME: Material " + (ENDGAME_WEIGHTS.material * 100) + "%");
    }

    // === GAME PHASE ENUM ===
    public enum GamePhase {
        OPENING, MIDDLEGAME, ENDGAME
    }

    // Static initializer to validate parameters on class load
    static {
        if (!validateParameters()) {
            System.err.println("❌ Parameter validation failed! Check EvaluationParameters configuration.");
        } else {
            System.out.println("✅ EvaluationParameters FIXED and validated successfully");
            System.out.println("   🎯 Material now gets " + (OPENING_WEIGHTS.material * 100) + "% weight (was ~30%!)");
            System.out.println("   🛡️ Safety penalties reduced to " + Safety.GUARD_DANGER_PENALTY + " (was 800!)");
        }
    }
}
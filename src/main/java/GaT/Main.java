package GaT;

import gui.GameFrame;
import client.GameClient;
import GaT.search.Engine;
import GaT.game.GameState;
import GaT.game.Move;
import GaT.benchmark.*;

import javax.swing.*;

/**
 * FIXED MAIN CLASS for Guard & Towers
 *
 * Clean entry point for the new architecture:
 * ✅ Launch GUI (GameFrame)
 * ✅ Launch tournament client
 * ✅ Run engine benchmark (delegates to benchmark package)
 * ✅ Simple command line interface
 * ✅ Fixed padRight errors
 *
 * Usage:
 *   java Main gui         - Launch visual game
 *   java Main tournament  - Launch tournament client
 *   java Main benchmark   - Run engine benchmark
 *   java Main eval        - Run evaluation benchmark
 *   java Main             - Show help and launch GUI
 */
public class Main {

    private static final String VERSION = "2.0-Simplified";

    public static void main(String[] args) {
        System.out.println("🎯 GUARD & TOWERS AI - Version " + VERSION);
        System.out.println("🚀 Simplified Architecture with Clean Engine");

        if (args.length == 0) {
            showHelp();
            launchGUI();
            return;
        }

        String mode = args[0].toLowerCase();

        switch (mode) {
            case "gui":
            case "g":
                launchGUI();
                break;

            case "tournament":
            case "t":
                launchTournament();
                break;

            case "benchmark":
            case "b":
                runEngineBenchmark();
                break;

            case "eval":

            case "help":
            case "h":
            case "--help":
                showHelp();
                break;

            default:
                System.err.println("❌ Unknown mode: " + mode);
                showHelp();
                System.exit(1);
        }
    }

    /**
     * Launch the visual GUI
     */
    private static void launchGUI() {
        System.out.println("🎮 Launching visual game interface...");

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getLookAndFeel());
            } catch (Exception e) {
                System.out.println("⚠️ Could not set system look and feel, using default");
            }

            GameFrame gameFrame = new GameFrame();
            gameFrame.setVisible(true);

            System.out.println("✅ GUI launched successfully");
            System.out.println("   Human vs AI: Play against the engine");
            System.out.println("   AI vs AI: Watch engine play against itself");
        });
    }

    /**
     * Launch tournament client
     */
    private static void launchTournament() {
        System.out.println("🏆 Launching tournament client...");
        System.out.println("🔌 Connecting to game.guard-and-towers.com:35000");

        try {
            GameClient.main(new String[0]);
        } catch (Exception e) {
            System.err.println("❌ Tournament client failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Run engine benchmark (delegates to EngineBenchmark)
     */
    private static void runEngineBenchmark() {
        System.out.println("📊 Running engine benchmark...");
        try {
            EngineBenchmark.runFullBenchmark();
        } catch (Exception e) {
            System.err.println("❌ Engine benchmark failed: " + e.getMessage());
            e.printStackTrace();
        }
    }



    /**
     * Show help information
     */
    private static void showHelp() {
        System.out.println("\n📖 GUARD & TOWERS AI - USAGE:");
        System.out.println("   java Main [mode]");
        System.out.println();
        System.out.println("🎮 MODES:");
        System.out.println("   gui, g         Launch visual game interface (default)");
        System.out.println("   tournament, t  Connect to tournament server");
        System.out.println("   benchmark, b   Run engine performance benchmark");
        System.out.println("   eval, e        Run evaluation performance benchmark");
        System.out.println("   help, h        Show this help message");
        System.out.println();
        System.out.println("🏗️ ARCHITECTURE:");
        System.out.println("   Engine:        Single search class with all algorithms");
        System.out.println("   Evaluator:     Fixed performance bugs, fast evaluation");
        System.out.println("   GameClient:    Simplified tournament client");
        System.out.println("   GameFrame:     Clean GUI with essential features");
        System.out.println();
        System.out.println("🎯 EXAMPLES:");
        System.out.println("   java Main                    # Launch GUI");
        System.out.println("   java Main gui                # Launch GUI explicitly");
        System.out.println("   java Main tournament         # Join tournament");
        System.out.println("   java Main benchmark          # Test engine performance");
        System.out.println("   java Main eval               # Test evaluation performance");
        System.out.println();
    }

    /**
     * Quick engine test
     */
    public static void quickTest() {
        System.out.println("🧪 Running quick engine test...");

        try {
            Engine engine = new Engine();
            GameState startPos = new GameState();

            System.out.println("Position: " + startPos.toString());

            long startTime = System.currentTimeMillis();
            Move bestMove = engine.findBestMove(startPos, 6, 3000); // Depth 6, 3 seconds
            long endTime = System.currentTimeMillis();

            System.out.println("✅ Engine test successful:");
            System.out.println("   Best move: " + bestMove);
            System.out.println("   Time: " + (endTime - startTime) + "ms");
            System.out.println("   Nodes: " + engine.getNodesSearched());

        } catch (Exception e) {
            System.err.println("❌ Engine test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get system info
     */
    public static void printSystemInfo() {
        System.out.println("💻 SYSTEM INFO:");
        System.out.println("   Java: " + System.getProperty("java.version"));
        System.out.println("   OS: " + System.getProperty("os.name"));
        System.out.println("   Cores: " + Runtime.getRuntime().availableProcessors());
        System.out.println("   Memory: " +
                Runtime.getRuntime().maxMemory() / (1024 * 1024) + "MB");
    }
}

// Utility class for string formatting (replaces the missing padRight)
class StringUtils {
    public static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    public static String padLeft(String s, int n) {
        return String.format("%" + n + "s", s);
    }
}
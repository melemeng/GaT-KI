package GaT;

import gui.GameFrame;
import client.GameClient;
import GaT.benchmark.EngineBenchmark;

import javax.swing.*;
import java.util.Scanner;

/**
 * MAIN CLASS for Guard & Towers AI
 *
 * Simple launcher with menu selection:
 * - GUI for human play
 * - Tournament client for competitions
 * - Benchmark for performance testing
 */
public class Main {

    private static final String VERSION = "3.0-Advanced";

    public static void main(String[] args) {
        System.out.println("🎯 GUARD & TOWERS AI - Version " + VERSION);
        System.out.println("🚀 Advanced Search Features: Null-Move, LMR, Aspiration, Futility");
        System.out.println();

        // If command line argument provided, use it directly
        if (args.length > 0) {
            handleCommand(args[0].toLowerCase());
            return;
        }

        // Otherwise show interactive menu
        showMenu();
    }

    /**
     * Show interactive menu for mode selection
     */
    private static void showMenu() {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("=".repeat(50));
            System.out.println("🎮 GUARD & TOWERS AI - MAIN MENU");
            System.out.println("=".repeat(50));
            System.out.println("1. 🎮 Launch GUI (Human vs AI)");
            System.out.println("2. 🏆 Tournament Client");
            System.out.println("3. 📊 Run Benchmark");
            System.out.println("4. ❓ Help");
            System.out.println("5. 🚪 Exit");
            System.out.println();
            System.out.print("Choose option (1-5): ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                case "gui":
                case "g":
                    launchGUI();
                    return;

                case "2":
                case "tournament":
                case "t":
                    launchTournament();
                    return;

                case "3":
                case "benchmark":
                case "b":
                    runBenchmark();
                    return;

                case "4":
                case "help":
                case "h":
                    showHelp();
                    break;

                case "5":
                case "exit":
                case "quit":
                case "q":
                    System.out.println("👋 Goodbye!");
                    return;

                default:
                    System.out.println("❌ Invalid choice. Please try again.\n");
            }
        }
    }

    /**
     * Handle command line arguments
     */
    private static void handleCommand(String command) {
        switch (command) {
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
                runBenchmark();
                break;
            case "help":
            case "h":
                showHelp();
                break;
            default:
                System.err.println("❌ Unknown command: " + command);
                showHelp();
        }
    }

    /**
     * Launch GUI for human vs AI games
     */
    private static void launchGUI() {
        System.out.println("🎮 Launching GUI...");

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getLookAndFeel());
            } catch (Exception e) {
                // Use default look and feel
            }

            GameFrame frame = new GameFrame();
            frame.setVisible(true);

            System.out.println("✅ GUI launched successfully");
        });
    }

    /**
     * Launch tournament client
     */
    private static void launchTournament() {
        System.out.println("🏆 Starting tournament client...");
        GameClient.main(new String[0]);
    }

    /**
     * Run performance benchmark
     */
    private static void runBenchmark() {
        System.out.println("📊 Running benchmark...");
        EngineBenchmark.runFullBenchmark();
    }

    /**
     * Show help information
     */
    private static void showHelp() {
        System.out.println("\n📖 GUARD & TOWERS AI - HELP");
        System.out.println("=".repeat(40));
        System.out.println("USAGE:");
        System.out.println("  java Main [command]");
        System.out.println();
        System.out.println("COMMANDS:");
        System.out.println("  gui          Launch visual game interface");
        System.out.println("  tournament   Connect to tournament server");
        System.out.println("  benchmark    Run performance tests");
        System.out.println("  help         Show this help");
        System.out.println();
        System.out.println("EXAMPLES:");
        System.out.println("  java Main              # Show interactive menu");
        System.out.println("  java Main gui          # Launch GUI directly");
        System.out.println("  java Main tournament   # Join tournament");
        System.out.println("  java Main benchmark    # Run performance tests");
        System.out.println();
        System.out.println("🚀 ADVANCED FEATURES:");
        System.out.println("  • Null-Move Pruning");
        System.out.println("  • Late-Move Reductions");
        System.out.println("  • Aspiration Windows");
        System.out.println("  • Futility Pruning");
        System.out.println("  • Advanced Move Ordering");
        System.out.println();
    }
}
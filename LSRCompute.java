import graph.LSALoader;
import gui.MainWindow;
import lsr.DijkstraLSR;
import lsr.PathResult;
import lsr.StepResult;

import javax.swing.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main entry point for the Link State Routing (LSR) computation program.
 *
 * <p>CLI usage:
 * <pre>
 *   java LSRCompute &lt;lsa-file&gt; &lt;source-node&gt; [SS|CA]
 * </pre>
 * Omitting all arguments launches an interactive menu (CLI or GUI).
 */
public class LSRCompute {

    private static final String DEFAULT_LSA_FILE = "data/routes.lsa";

    // =========================================================
    // Entry point
    // =========================================================

    public static void main(String[] args) {
        if (args.length >= 2) {
            String lsaFile  = args[0];
            String source   = args[1].toUpperCase();
            String mode     = args.length >= 3 ? args[2].toUpperCase() : "CA";
            runCliDirect(lsaFile, source, mode);
        } else {
            runInteractive();
        }
    }

    // =========================================================
    // Direct CLI mode  (java LSRCompute routes.lsa A SS|CA)
    // =========================================================

    private static void runCliDirect(String lsaFile, String source, String mode) {
        Map<String, Map<String, Integer>> graph = loadGraphOrExit(lsaFile);

        if (!graph.containsKey(source)) {
            System.err.println("Error: Source node '" + source + "' not found in " + lsaFile);
            System.err.println("Available nodes: " + sortedKeys(graph));
            System.exit(1);
        }

        DijkstraLSR dijkstra = new DijkstraLSR(graph);

        if ("SS".equals(mode)) {
            runSingleStep(dijkstra, source, new Scanner(System.in));
        } else {
            runComputeAll(dijkstra, source);
        }
    }

    // =========================================================
    // Interactive menu
    // =========================================================

    private static void runInteractive() {
        System.out.println("Welcome to the LSR Pathfinding Application!");
        System.out.println("1. Command Line Interface (CLI)");
        System.out.println("2. Graphical User Interface (GUI)");
        System.out.print("Enter choice (1 or 2): ");

        try (Scanner scanner = new Scanner(System.in)) {
            int choice = readInt(scanner, -1);
            if (choice == 1) {
                runInteractiveCli(scanner);
            } else if (choice == 2) {
                launchGui();
            } else {
                System.out.println("Invalid choice. Exiting.");
            }
        }
    }

    // =========================================================
    // Interactive CLI
    // =========================================================

    private static void runInteractiveCli(Scanner scanner) {
        String[] currentFile = {DEFAULT_LSA_FILE};

        boolean running = true;
        while (running) {
            System.out.println("\n--- LSR Compute CLI  [File: " + currentFile[0] + "] ---");
            System.out.println("1. Preview Network Topology");
            System.out.println("2. Modify Network");
            System.out.println("3. Run LSR (Dijkstra)");
            System.out.println("4. Change LSA File");
            System.out.println("5. Quit");
            System.out.print("Enter choice (1-5): ");

            int choice = readInt(scanner, -1);
            switch (choice) {
                case 1: previewNetwork(currentFile[0]);                   break;
                case 2: modifyNetwork(scanner, currentFile[0]);           break;
                case 3: runLsrInteractive(scanner, currentFile[0]);       break;
                case 4:
                    System.out.print("Enter new LSA file path: ");
                    String path = scanner.nextLine().trim();
                    if (!path.isEmpty()) currentFile[0] = path;
                    break;
                case 5: running = false; System.out.println("Goodbye!"); break;
                default: System.err.println("Invalid choice.");           break;
            }

            if (running && choice >= 1 && choice <= 4) {
                System.out.println("\nPress Enter to continue...");
                scanner.nextLine();
            }
        }
    }

    /** Prints the loaded topology. */
    private static void previewNetwork(String lsaFile) {
        Map<String, Map<String, Integer>> graph = loadGraphSilent(lsaFile);
        if (graph == null) return;

        System.out.println("\n--- Network Topology: " + lsaFile + " ---");
        graph.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    String neighbours = e.getValue().entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(nb -> nb.getKey() + "(" + nb.getValue() + ")")
                            .collect(Collectors.joining("  "));
                    System.out.println(e.getKey() + ": " + (neighbours.isEmpty() ? "(no links)" : neighbours));
                });
    }

    /** Sub-menu for modifying graph nodes and edges. */
    private static void modifyNetwork(Scanner scanner, String lsaFile) {
        Map<String, Map<String, Integer>> graph = loadGraphSilent(lsaFile);
        if (graph == null) return;

        boolean back = false;
        while (!back) {
            System.out.println("\n--- Modify Network ---");
            System.out.println("1. Add Node");
            System.out.println("2. Delete Node");
            System.out.println("3. Add / Update Link");
            System.out.println("4. Remove Link");
            System.out.println("5. Back");
            System.out.print("Enter choice (1-5): ");

            int choice = readInt(scanner, -1);
            boolean modified = false;
            switch (choice) {
                case 1: modified = doAddNode(scanner, graph);               break;
                case 2: modified = doDeleteNode(scanner, graph);            break;
                case 3: modified = doAddUpdateLink(scanner, graph);         break;
                case 4: modified = doRemoveLink(scanner, graph);            break;
                case 5: back = true;                                        break;
                default: System.err.println("Invalid choice."); break;
            }
            if (modified) {
                try {
                    LSALoader.save(graph, lsaFile);
                    System.out.println("Changes saved to " + lsaFile);
                } catch (IOException e) {
                    System.err.println("Error saving file: " + e.getMessage());
                }
            }
        }
    }

    private static boolean doAddNode(Scanner scanner, Map<String, Map<String, Integer>> graph) {
        System.out.print("New node name: ");
        String name = scanner.nextLine().trim().toUpperCase();
        if (name.isEmpty()) return false;
        if (graph.containsKey(name)) {
            System.err.println("Node '" + name + "' already exists.");
            return false;
        }
        graph.put(name, new LinkedHashMap<>());
        System.out.println("Node '" + name + "' added.");
        return true;
    }

    private static boolean doDeleteNode(Scanner scanner, Map<String, Map<String, Integer>> graph) {
        System.out.print("Node to delete: ");
        String name = scanner.nextLine().trim().toUpperCase();
        if (!graph.containsKey(name)) {
            System.err.println("Node '" + name + "' not found.");
            return false;
        }
        graph.remove(name);
        graph.values().forEach(nb -> nb.remove(name));
        System.out.println("Node '" + name + "' and its links deleted.");
        return true;
    }

    private static boolean doAddUpdateLink(Scanner scanner, Map<String, Map<String, Integer>> graph) {
        System.out.print("Source node: ");
        String from = scanner.nextLine().trim().toUpperCase();
        System.out.print("Destination node: ");
        String to = scanner.nextLine().trim().toUpperCase();
        System.out.print("Link cost (integer): ");
        int cost = readInt(scanner, -1);
        if (cost <= 0) { System.err.println("Cost must be positive."); return false; }
        if (from.equals(to)) { System.err.println("Cannot link a node to itself."); return false; }

        graph.computeIfAbsent(from, k -> new LinkedHashMap<>()).put(to, cost);
        graph.computeIfAbsent(to,   k -> new LinkedHashMap<>()).put(from, cost);
        System.out.println("Link " + from + " <-> " + to + " (" + cost + ") added/updated.");
        return true;
    }

    private static boolean doRemoveLink(Scanner scanner, Map<String, Map<String, Integer>> graph) {
        System.out.print("Source node: ");
        String from = scanner.nextLine().trim().toUpperCase();
        System.out.print("Destination node: ");
        String to = scanner.nextLine().trim().toUpperCase();
        boolean changed = false;
        if (graph.containsKey(from) && graph.get(from).remove(to) != null) changed = true;
        if (graph.containsKey(to)   && graph.get(to).remove(from) != null) changed = true;
        if (!changed) { System.err.println("Link not found."); return false; }
        System.out.println("Link " + from + " <-> " + to + " removed.");
        return true;
    }

    /** Prompts for source and mode, then runs accordingly. */
    private static void runLsrInteractive(Scanner scanner, String lsaFile) {
        Map<String, Map<String, Integer>> graph = loadGraphSilent(lsaFile);
        if (graph == null) return;

        System.out.println("Available nodes: " + sortedKeys(graph));
        System.out.print("Source node: ");
        String source = scanner.nextLine().trim().toUpperCase();
        if (!graph.containsKey(source)) {
            System.err.println("Node '" + source + "' not found.");
            return;
        }

        System.out.print("Mode — SS (single-step) or CA (compute-all): ");
        String mode = scanner.nextLine().trim().toUpperCase();

        DijkstraLSR dijkstra = new DijkstraLSR(graph);
        if ("SS".equals(mode)) {
            runSingleStep(dijkstra, source, scanner);
        } else {
            runComputeAll(dijkstra, source);
        }
    }

    // =========================================================
    // Dijkstra execution helpers
    // =========================================================

    /** Single-step mode: pauses after each settled node. */
    static void runSingleStep(DijkstraLSR dijkstra, String source, Scanner scanner) {
        dijkstra.initStepMode(source);
        System.out.println("\nStarting LSR computation from source: " + source + "  (SS mode)");

        // The first step settles the source itself — skip printing it
        StepResult first = dijkstra.nextStep();
        if (first != null && !first.done && source.equals(first.foundNode)) {
            // source node settled with cost 0 — don't prompt for it
        }

        while (!dijkstra.isStepDone()) {
            StepResult step = dijkstra.nextStep();
            if (step.done) break;
            System.out.print(step.toStepLine() + " [press Enter to continue]");
            scanner.nextLine();
        }

        printSummary(source, dijkstra.getStepResults());
    }

    /** Compute-all mode: runs full Dijkstra and prints summary. */
    static void runComputeAll(DijkstraLSR dijkstra, String source) {
        System.out.println("Computing shortest paths from " + source + "...");
        Map<String, PathResult> results = dijkstra.computeAll(source);
        printSummary(source, results);
    }

    private static void printSummary(String source, Map<String, PathResult> results) {
        System.out.println("\nSource " + source + ":");
        results.values().forEach(r -> System.out.println(r.toSummaryLine()));
    }

    // =========================================================
    // GUI launcher
    // =========================================================

    private static void launchGui() {
        SwingUtilities.invokeLater(() -> {
            try {
                Map<String, Map<String, Integer>> graph = LSALoader.load(DEFAULT_LSA_FILE);
                MainWindow window = new MainWindow(graph, DEFAULT_LSA_FILE);
                window.setVisible(true);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                        "Error loading " + DEFAULT_LSA_FILE + ":\n" + e.getMessage(),
                        "Data Loading Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    // =========================================================
    // Utilities
    // =========================================================

    private static Map<String, Map<String, Integer>> loadGraphOrExit(String path) {
        try {
            return LSALoader.load(path);
        } catch (IOException e) {
            System.err.println("Error loading '" + path + "': " + e.getMessage());
            System.exit(1);
            return null;
        }
    }

    private static Map<String, Map<String, Integer>> loadGraphSilent(String path) {
        try {
            return LSALoader.load(path);
        } catch (IOException e) {
            System.err.println("Error loading '" + path + "': " + e.getMessage());
            return null;
        }
    }

    private static int readInt(Scanner scanner, int fallback) {
        try {
            int v = scanner.nextInt();
            scanner.nextLine();
            return v;
        } catch (InputMismatchException e) {
            scanner.nextLine();
            return fallback;
        } catch (NoSuchElementException e) {
            return fallback;
        }
    }

    private static String sortedKeys(Map<String, ?> map) {
        return map.keySet().stream().sorted().collect(Collectors.joining(", "));
    }
}

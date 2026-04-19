package gui;

import graph.LSALoader;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import lsr.DijkstraLSR;
import lsr.PathResult;
import lsr.StepResult;

/**
 * Main GUI window for the LSR Pathfinding application.
 * Supports full compute-all and interactive single-step Dijkstra execution.
 */
public class MainWindow extends JFrame {

    // ---- Components ----
    private GraphPanel  graphPanel;
    private JTable      resultsTable;
    private JScrollPane tableScrollPane;
    private JTextArea   logArea;
    private JScrollPane logScrollPane;

    // ---- Controls ----
    private JTextField sourceField;
    private JButton    modifyNetBtn;
    private JButton    runAllBtn;
    private JButton    stepModeBtn;
    private JButton    nextStepBtn;
    private JButton    loadFileBtn;

    // ---- Data ----
    private Map<String, Map<String, Integer>> graphData;
    private final String lsaFilePath;

    // ---- Step-mode state ----
    private DijkstraLSR stepDijkstra = null;
    private boolean     inStepMode   = false;

    // =========================================================
    // Constructor
    // =========================================================

    public MainWindow(Map<String, Map<String, Integer>> graph, String lsaFilePath) {
        this.graphData   = new HashMap<>(graph);
        this.lsaFilePath = lsaFilePath;

        setTitle("LSR Pathfinding — " + lsaFilePath);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1050, 720);
        setLocationRelativeTo(null);

        initComponents();
        layoutComponents();
        addListeners();
    }

    // =========================================================
    // Initialisation
    // =========================================================

    private void initComponents() {
        graphPanel = new GraphPanel(graphData);

        graphPanel.setOnNodeClicked(nodeName -> {
            sourceField.setText(nodeName);
            log("Selected source node: " + nodeName);
        });

        resultsTable = new JTable();
        resultsTable.setEnabled(false);
        resultsTable.setFillsViewportHeight(true);
        tableScrollPane = new JScrollPane(resultsTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Shortest Paths"));
        tableScrollPane.setPreferredSize(new Dimension(210, 0));

        logArea = new JTextArea(7, 40);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Log"));

        sourceField   = new JTextField(5);
        modifyNetBtn  = new JButton("Modify Network");
        runAllBtn     = new JButton("Run Dijkstra (All)");
        stepModeBtn   = new JButton("Step Mode");
        nextStepBtn   = new JButton("Next Step");
        loadFileBtn   = new JButton("Load LSA File");
        loadFileBtn.setToolTipText("Open a .lsa topology file");

        nextStepBtn.setVisible(false);
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(5, 5));

        add(graphPanel, BorderLayout.CENTER);
        add(tableScrollPane, BorderLayout.EAST);

        JPanel controlRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        controlRow.add(new JLabel("Source:"));
        controlRow.add(sourceField);
        controlRow.add(modifyNetBtn);
        controlRow.add(runAllBtn);
        controlRow.add(stepModeBtn);
        controlRow.add(nextStepBtn);
        controlRow.add(loadFileBtn);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(controlRow,    BorderLayout.NORTH);
        southPanel.add(logScrollPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
    }

    private void addListeners() {
        modifyNetBtn.addActionListener(e -> handleModifyNetwork());
        runAllBtn   .addActionListener(e -> handleRunAll());
        stepModeBtn .addActionListener(e -> handleStartStepMode());
        nextStepBtn .addActionListener(e -> handleNextStep());
        loadFileBtn .addActionListener(e -> handleOpenFile());
    }

    // =========================================================
    // Button handlers
    // =========================================================

    private void handleRunAll() {
        String source = getSourceNode();
        if (source == null) return;

        log("Running Dijkstra (all) from '" + source + "'...");
        DijkstraLSR dijkstra = new DijkstraLSR(graphData);
        Map<String, PathResult> results = dijkstra.computeAll(source);

        graphPanel.clearHighlights();
        populateResultsTable(source, results);
        log("Source " + source + ":");
        results.values().forEach(r -> log("  " + r.toSummaryLine()));
    }

    private void handleStartStepMode() {
        String source = getSourceNode();
        if (source == null) return;

        stepDijkstra = new DijkstraLSR(graphData);
        stepDijkstra.initStepMode(source);
        inStepMode = true;

        graphPanel.clearHighlights();
        clearResultsTable();
        setStepModeUI(true);
        log("Step mode started from '" + source + "'. Press 'Next Step' to advance.");

        // Settle source node immediately (cost 0)
        StepResult first = stepDijkstra.nextStep();
        if (first != null && !first.done) {
            graphPanel.addStepHighlight(first.foundNode, first.pathToNode);
            log(first.toStepLine());
        }
    }

    private void handleNextStep() {
        if (stepDijkstra == null || !inStepMode) return;

        if (stepDijkstra.isStepDone()) {
            finishStepMode();
            return;
        }

        StepResult step = stepDijkstra.nextStep();
        if (step.done) {
            finishStepMode();
        } else {
            graphPanel.addStepHighlight(step.foundNode, step.pathToNode);
            log(step.toStepLine());
        }
    }

    private void finishStepMode() {
        log("[All nodes settled]");
        String source = sourceField.getText().trim().toUpperCase();
        Map<String, PathResult> results = stepDijkstra.getStepResults();
        populateResultsTable(source, results);
        log("Summary:");
        results.values().forEach(r -> log("  " + r.toSummaryLine()));
        setStepModeUI(false);
        inStepMode = false;
    }

    private void handleOpenFile() {
        JFileChooser fileChooser = new JFileChooser(".");
        fileChooser.setDialogTitle("Select LSA Topology File");
        int userSelection = fileChooser.showOpenDialog(this);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToOpen = fileChooser.getSelectedFile();
            try {
                // Use your existing LSALoader
                this.graphData = new HashMap<>(LSALoader.load(fileToOpen.getAbsolutePath()));
                this.graphPanel.updateGraph(graphData); // Refresh visual
                log("Loaded new topology: " + fileToOpen.getName());
            } catch (IOException ex) {
                showError("Could not load file: " + ex.getMessage());
            }
        }
    }

    private void handleModifyNetwork() {
        String[] options = {"Add Node", "Delete Node", "Add/Update Link", "Remove Link", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, "Choose a modification:",
                "Modify Network", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);

        boolean modified = false;
        try {
            switch (choice) {
                case 0: modified = doAddNode();        break;
                case 1: modified = doDeleteNode();     break;
                case 2: modified = doAddUpdateLink();  break;
                case 3: modified = doRemoveLink();     break;
                default: return;
            }
            if (modified) {
                LSALoader.save(graphData, lsaFilePath);
                graphPanel.updateGraph(graphData);
                log("Network modified and saved to " + lsaFilePath);
            }
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    private boolean doAddNode() {
        String name = promptInput("New node name:");
        if (name == null || name.isEmpty()) return false;
        name = name.toUpperCase();
        if (graphData.containsKey(name)) { showError("Node '" + name + "' already exists."); return false; }
        graphData.put(name, new LinkedHashMap<>());
        log("Node '" + name + "' added.");
        return true;
    }

    private boolean doDeleteNode() {
        String name = promptNode("Node to delete:");
        if (name == null) return false;
        graphData.remove(name);
        graphData.values().forEach(nb -> nb.remove(name));
        log("Node '" + name + "' and its links deleted.");
        return true;
    }

    private boolean doAddUpdateLink() {
        String from = promptNode("Source node:");
        if (from == null) return false;
        String to = promptNode("Destination node:");
        if (to == null) return false;
        if (from.equals(to)) { showError("Cannot link a node to itself."); return false; }
        String costStr = promptInput("Link cost (positive integer):");
        if (costStr == null) return false;
        try {
            int cost = Integer.parseInt(costStr.trim());
            if (cost <= 0) { showError("Cost must be positive."); return false; }
            graphData.computeIfAbsent(from, k -> new LinkedHashMap<>()).put(to, cost);
            graphData.computeIfAbsent(to,   k -> new LinkedHashMap<>()).put(from, cost);
            log("Link " + from + " <-> " + to + " (" + cost + ") added/updated.");
            return true;
        } catch (NumberFormatException e) {
            showError("Invalid cost."); return false;
        }
    }

    private boolean doRemoveLink() {
        String from = promptNode("Source node:");
        if (from == null) return false;
        String to = promptNode("Destination node:");
        if (to == null) return false;
        boolean changed = false;
        if (graphData.containsKey(from) && graphData.get(from).remove(to)   != null) changed = true;
        if (graphData.containsKey(to)   && graphData.get(to)  .remove(from) != null) changed = true;
        if (!changed) { showError("Link not found."); return false; }
        log("Link " + from + " <-> " + to + " removed.");
        return true;
    }

    private void handleReload() {
        try {
            graphData = new HashMap<>(LSALoader.load(lsaFilePath));
            graphPanel.updateGraph(graphData);
            clearResultsTable();
            log("Data reloaded from " + lsaFilePath);
        } catch (IOException e) {
            showError("Reload failed: " + e.getMessage());
        }
    }

    // =========================================================
    // UI helpers
    // =========================================================

    /** Returns the trimmed, upper-cased source node, or null with an error if invalid. */
    private String getSourceNode() {
        String source = sourceField.getText().trim().toUpperCase();
        if (source.isEmpty()) {
            showError("Please enter a source node name.");
            return null;
        }
        if (!graphData.containsKey(source)) {
            showError("Node '" + source + "' not found. Available: " +
                    graphData.keySet().stream().sorted().collect(Collectors.joining(", ")));
            return null;
        }
        return source;
    }

    private void setStepModeUI(boolean stepping) {
        stepModeBtn .setVisible(!stepping);
        nextStepBtn .setVisible(stepping);
        runAllBtn   .setEnabled(!stepping);
        modifyNetBtn.setEnabled(!stepping);
        loadFileBtn.setEnabled(!stepping);
    }

    private void populateResultsTable(String source, Map<String, PathResult> results) {
        String[] cols = {"Destination", "Path", "Cost"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        results.values().stream()
                .sorted(Comparator.comparing(r -> r.destination))
                .forEach(r -> model.addRow(new Object[]{
                        r.destination,
                        r.isReachable() ? String.join(">", r.path) : "UNREACHABLE",
                        r.isReachable() ? r.cost : "-"
                }));
        resultsTable.setModel(model);
        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(40);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Shortest Paths from " + source));
    }

    private void clearResultsTable() {
        resultsTable.setModel(new DefaultTableModel(new String[]{"Destination", "Path", "Cost"}, 0));
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Shortest Paths"));
    }

    private void log(String msg) {
        Runnable r = () -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    private String promptInput(String message) {
        return JOptionPane.showInputDialog(this, message);
    }

    private String promptNode(String message) {
        Set<String> nodes = graphData.keySet();
        if (nodes.isEmpty()) { showError("No nodes in graph."); return null; }
        String[] arr = nodes.stream().sorted().toArray(String[]::new);
        Object sel = JOptionPane.showInputDialog(this, message, "Select Node",
                JOptionPane.PLAIN_MESSAGE, null, arr, arr[0]);
        return (String) sel;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}

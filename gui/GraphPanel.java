package gui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

public class GraphPanel extends JPanel {

    // ---- Graph data ----
    private Map<String, Map<String, Integer>> graph;
    private Map<String, Point2D>              nodePositions;

    // ---- Layout constants ----
    private static final int PADDING     = 55;
    private static final int NODE_RADIUS = 18;

    // ---- Theme colors - Dark cybersecurity dashboard ----
    private static final Color BACKGROUND_COLOR = new Color(10, 16, 22);
    private static final Color GRID_COLOR_LIGHT = new Color(0, 90, 40, 40);
    private static final Color GRID_COLOR_DARK  = new Color(0, 60, 30, 24);
    private static final Color COLOR_SETTLED_NODE = new Color(255, 255,   0); // yellow (impacted)
    private static final Color COLOR_CURRENT_NODE = new Color(255,   0,   0); // red (origin)
    private static final Color COLOR_PATH_EDGE    = new Color(80, 255, 160); // terminal green
    private static final Color COLOR_DEFAULT_NODE = new Color(40, 175, 80);
    private static final Color COLOR_DEFAULT_EDGE = new Color(0, 130, 90, 180);
    private static final Color COLOR_EDGE_LABEL   = new Color(160, 255, 170);
    private static final Color COLOR_NODE_GLOW    = new Color(0, 255, 150, 80);
    private static final Color COLOR_NODE_BORDER  = new Color(0, 230, 130);
    private static final Color COLOR_LABEL        = new Color(170, 255, 140);
    private static final float STROKE_DEFAULT     = 1.5f;
    private static final float STROKE_HIGHLIGHT   = 3.2f;

    // ---- Highlighting ----
    /** Nodes settled so far in step-by-step mode (accumulated). */
    private final Set<String>                   highlightedNodes = new HashSet<>();
    /** Edges on the best paths highlighted so far (accumulated). */
    private final Set<Map.Entry<String,String>> highlightedEdges = new HashSet<>();
    /** The most recently settled node (shown in red). */
    private String currentNode = null;

    private Consumer<String> onNodeClicked;

    // =========================================================
    // Constructor
    // =========================================================

    public GraphPanel(Map<String, Map<String, Integer>> graph) {
        this.graph         = graph != null ? new HashMap<>(graph) : new HashMap<>();
        this.nodePositions = new HashMap<>();
        setBackground(BACKGROUND_COLOR);
        setPreferredSize(new Dimension(640, 520));
        calculateNodePositions();

        addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
            handleMouseClick(e);
        }
    });
    }

    // =========================================================
    // Layout
    // =========================================================

    private void handleMouseClick(MouseEvent e) {
        for (Map.Entry<String, Point2D> entry : nodePositions.entrySet()) {
            if (entry.getValue().distance(e.getPoint()) < NODE_RADIUS) {
                if (onNodeClicked != null) {
                    onNodeClicked.accept(entry.getKey());
                }
                break;
            }
        }
    }
    /** Arranges all nodes equally around a circle. */
    private void calculateNodePositions() {
        Set<String> allNodes = new HashSet<>(graph.keySet());
        graph.values().forEach(nb -> allNodes.addAll(nb.keySet()));

        List<String> nodeList = new ArrayList<>(allNodes);
        Collections.sort(nodeList);

        int n = nodeList.size();
        if (n == 0) return;

        int w = Math.max(getWidth(),  (int) getPreferredSize().getWidth());
        int h = Math.max(getHeight(), (int) getPreferredSize().getHeight());

        double cx     = w / 2.0;
        double cy     = h / 2.0;
        double radius = Math.min(cx, cy) - PADDING - NODE_RADIUS;

        nodePositions.clear();
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n - Math.PI / 2; // start at top
            double x = cx + radius * Math.cos(angle);
            double y = cy + radius * Math.sin(angle);
            nodePositions.put(nodeList.get(i), new Point2D.Double(x, y));
        }
    }

    @Override
    public void doLayout() {
        super.doLayout();
        calculateNodePositions();
    }

    // =========================================================
    // Painting
    // =========================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Dark background
        g2.setColor(BACKGROUND_COLOR);
        g2.fillRect(0, 0, getWidth(), getHeight());
        
        // Digital grid background
        drawBackgroundGrid(g2);

        if (graph.isEmpty()) {
            g2.setColor(Color.RED);
            g2.drawString("No graph data loaded.", PADDING, PADDING);
            g2.dispose();
            return;
        }

        drawEdges(g2);
        drawNodes(g2);
        g2.dispose();
    }

    private void drawBackgroundGrid(Graphics2D g2) {
        int step = 28;
        for (int x = 0; x <= getWidth(); x += step) {
            g2.setColor(x % (step * 4) == 0 ? GRID_COLOR_LIGHT : GRID_COLOR_DARK);
            g2.drawLine(x, 0, x, getHeight());
        }
        for (int y = 0; y <= getHeight(); y += step) {
            g2.setColor(y % (step * 4) == 0 ? GRID_COLOR_LIGHT : GRID_COLOR_DARK);
            g2.drawLine(0, y, getWidth(), y);
        }
    }

    private void drawEdges(Graphics2D g2) {
        Set<String> drawn = new HashSet<>();
        for (Map.Entry<String, Map<String, Integer>> entry : graph.entrySet()) {
            String  src    = entry.getKey();
            Point2D srcPos = nodePositions.get(src);
            if (srcPos == null) continue;

            for (Map.Entry<String, Integer> nb : entry.getValue().entrySet()) {
                String tgt = nb.getKey();
                String edgeKey = src.compareTo(tgt) < 0 ? src + "|" + tgt : tgt + "|" + src;
                if (drawn.contains(edgeKey)) continue;
                drawn.add(edgeKey);

                Point2D tgtPos = nodePositions.get(tgt);
                if (tgtPos == null) continue;

                String u = src.compareTo(tgt) < 0 ? src : tgt;
                String v = src.compareTo(tgt) < 0 ? tgt : src;
                boolean highlighted = highlightedEdges.contains(Map.entry(u, v));
                int cost = nb.getValue();

                // Vary thickness by cost (bandwidth representation)
                float baseWidth = Math.min(4.0f, 1.4f + cost * 0.16f);
                float glowWidth = baseWidth + 2.5f;
                float[] dash = cost > 5 ? new float[]{10f, 6f} : new float[]{6f, 4f};
                
                BasicStroke glowStroke = new BasicStroke(glowWidth, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND, 0, dash, 0);
                BasicStroke edgeStroke = new BasicStroke(highlighted ? STROKE_HIGHLIGHT : baseWidth,
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                        highlighted ? new float[]{12f, 6f} : dash, 0);

                Line2D line = new Line2D.Double(srcPos, tgtPos);
                
                // Glow effect for non-highlighted edges
                if (!highlighted) {
                    g2.setColor(COLOR_NODE_GLOW);
                    g2.setStroke(glowStroke);
                    g2.draw(line);
                }
                
                // Main edge
                g2.setColor(highlighted ? COLOR_PATH_EDGE : COLOR_DEFAULT_EDGE);
                g2.setStroke(edgeStroke);
                g2.draw(line);

                // Cost label (2x bigger - 22pt)
                g2.setColor(COLOR_EDGE_LABEL);
                g2.setFont(new Font("Monospaced", Font.PLAIN, 22));
                double mx = (srcPos.getX() + tgtPos.getX()) / 2;
                double my = (srcPos.getY() + tgtPos.getY()) / 2;
                g2.drawString(String.valueOf(cost), (float) mx + 4, (float) my - 6);
            }
        }
        g2.setStroke(new BasicStroke(STROKE_DEFAULT));
    }

    private void drawNodes(Graphics2D g2) {
        // Node label font (1.5x bigger, 20pt, bold, black)
        g2.setFont(new Font("Monospaced", Font.BOLD, 20));
        FontMetrics fm = g2.getFontMetrics();

        for (Map.Entry<String, Point2D> entry : nodePositions.entrySet()) {
            String   name   = entry.getKey();
            Point2D  pos    = entry.getValue();
            double   x      = pos.getX() - NODE_RADIUS;
            double   y      = pos.getY() - NODE_RADIUS;
            Ellipse2D circle = new Ellipse2D.Double(x, y, NODE_RADIUS * 2, NODE_RADIUS * 2);

            Color fill = name.equals(currentNode)        ? COLOR_CURRENT_NODE
                       : highlightedNodes.contains(name) ? COLOR_SETTLED_NODE
                       :                                   COLOR_DEFAULT_NODE;

            // Radial gradient for glowing hub effect
            float[] stops = {0.1f, 1f};
            Color inner = fill.brighter();
            Color outer = fill.darker().darker();
            RadialGradientPaint paint = new RadialGradientPaint(
                    new Point2D.Double(pos.getX(), pos.getY()), NODE_RADIUS,
                    stops, new Color[]{inner, outer});
            g2.setPaint(paint);
            g2.fill(circle);

            // Glow effect outer ring
            g2.setColor(COLOR_NODE_GLOW);
            g2.setStroke(new BasicStroke(8f));
            g2.draw(circle);

            // Neon border
            g2.setColor(COLOR_NODE_BORDER);
            g2.setStroke(new BasicStroke(2.4f));
            g2.draw(circle);

            // Centred label (bold, black, 20pt)
            int tw = fm.stringWidth(name);
            int th = fm.getAscent() - fm.getDescent();
            g2.setColor(Color.BLACK);
            g2.drawString(name, (float)(pos.getX() - tw / 2.0), (float)(pos.getY() + th / 2.0));
        }
    }

    // =========================================================
    // Public API
    // =========================================================

    /** Replaces the graph, recalculates layout, and repaints. */
    public void updateGraph(Map<String, Map<String, Integer>> newGraph) {
        this.graph = newGraph != null ? new HashMap<>(newGraph) : new HashMap<>();
        calculateNodePositions();
        clearHighlights();
        repaint();
    }

    /**
     * Highlights a complete path (all nodes and edges) and repaints.
     * Clears any previous highlights first.
     */
    public void highlightPath(List<String> path) {
        clearHighlights();
        if (path == null || path.isEmpty()) return;
        highlightedNodes.addAll(path);
        for (int i = 0; i < path.size() - 1; i++) {
            addEdgeHighlight(path.get(i), path.get(i + 1));
        }
        repaint();
    }

    /**
     * Adds a single Dijkstra step highlight without clearing previous ones.
     * The newest settled node is shown in amber; earlier ones in blue.
     */
    public void addStepHighlight(String settledNode, List<String> path) {
        if (currentNode != null) {
            highlightedNodes.add(currentNode);  // demote previous "current" to settled
        }
        currentNode = settledNode;
        if (path != null) {
            for (int i = 0; i < path.size() - 1; i++) {
                addEdgeHighlight(path.get(i), path.get(i + 1));
            }
        }
        repaint();
    }

    /** Clears all highlights and repaints. */
    public void clearHighlights() {
        highlightedNodes.clear();
        highlightedEdges.clear();
        currentNode = null;
        repaint();
    }

    public Map<String, Point2D> getNodePositions() {
        return Collections.unmodifiableMap(nodePositions);
    }

    public void setOnNodeClicked(Consumer<String> callback) {
        this.onNodeClicked = callback;
    }
        
    // =========================================================
    // Helpers
    // =========================================================

    private void addEdgeHighlight(String a, String b) {
        String u = a.compareTo(b) < 0 ? a : b;
        String v = a.compareTo(b) < 0 ? b : a;
        highlightedEdges.add(Map.entry(u, v));
    }

    
}


    // --- Highlighting State ---

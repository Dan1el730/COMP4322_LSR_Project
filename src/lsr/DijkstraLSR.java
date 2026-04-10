package lsr;

import java.util.*;

/**
 * Implements Dijkstra's shortest-path algorithm for LSR computation.
 * Supports both full compute-all mode and interactive single-step mode.
 */
public class DijkstraLSR {

    // Min-heap entry: (node, tentative distance)
    private static class Entry implements Comparable<Entry> {
        final String node;
        final int dist;

        Entry(String node, int dist) {
            this.node = node;
            this.dist = dist;
        }

        @Override
        public int compareTo(Entry other) {
            return Integer.compare(this.dist, other.dist);
        }
    }

    private final Map<String, Map<String, Integer>> graph;

    // ---- Step-by-step state ----
    private PriorityQueue<Entry> stepPQ;
    private Set<String>          stepSettled;
    private Map<String, Integer> stepDist;
    private Map<String, String>  stepPrev;   // previous node on best path
    private String               stepSource;
    private boolean              stepDone;

    public DijkstraLSR(Map<String, Map<String, Integer>> graph) {
        this.graph = graph;
    }

    // =========================================================
    // Single-step mode
    // =========================================================

    /**
     * Initialises the algorithm for single-step execution from {@code source}.
     * Call {@link #nextStep()} repeatedly to advance the computation.
     */
    public void initStepMode(String source) {
        this.stepSource  = source;
        this.stepDone    = false;
        this.stepPQ      = new PriorityQueue<>();
        this.stepSettled = new HashSet<>();
        this.stepDist    = new HashMap<>();
        this.stepPrev    = new HashMap<>();

        for (String node : getAllNodes()) {
            stepDist.put(node, Integer.MAX_VALUE);
        }
        stepDist.put(source, 0);
        stepPQ.add(new Entry(source, 0));
    }

    /** Returns {@code true} once all reachable nodes have been settled. */
    public boolean isStepDone() {
        return stepDone;
    }

    /**
     * Advances the algorithm by one step (settles the next minimum-distance node).
     *
     * @return A {@link StepResult} describing the newly settled node,
     *         or a terminal result with {@code done=true} if the queue is empty.
     */
    public StepResult nextStep() {
        if (stepDone) {
            return new StepResult(null, null, -1, true);
        }

        while (!stepPQ.isEmpty()) {
            Entry current = stepPQ.poll();

            // Skip stale entries
            if (stepSettled.contains(current.node)) continue;
            if (current.dist > stepDist.getOrDefault(current.node, Integer.MAX_VALUE)) continue;

            stepSettled.add(current.node);

            // Relax neighbours
            for (Map.Entry<String, Integer> nb : graph.getOrDefault(current.node, Collections.emptyMap()).entrySet()) {
                String neighbor = nb.getKey();
                int edgeCost = nb.getValue();
                if (stepSettled.contains(neighbor)) continue;

                int newDist = current.dist + edgeCost;
                if (newDist < stepDist.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    stepDist.put(neighbor, newDist);
                    stepPrev.put(neighbor, current.node);
                    stepPQ.add(new Entry(neighbor, newDist));
                }
            }

            List<String> path = buildPath(current.node, stepPrev);
            return new StepResult(current.node, path, current.dist, false);
        }

        stepDone = true;
        return new StepResult(null, null, -1, true);
    }

    /**
     * Returns the shortest-path results collected so far (or upon completion)
     * from the step-mode source to every other node.
     */
    public Map<String, PathResult> getStepResults() {
        Map<String, PathResult> results = new TreeMap<>();
        for (String node : getAllNodes()) {
            if (node.equals(stepSource)) continue;
            int d = stepDist.getOrDefault(node, Integer.MAX_VALUE);
            List<String> path = (d == Integer.MAX_VALUE)
                    ? Collections.emptyList()
                    : buildPath(node, stepPrev);
            results.put(node, new PathResult(node, path, d == Integer.MAX_VALUE ? -1 : d));
        }
        return results;
    }

    // =========================================================
    // Compute-all mode
    // =========================================================

    /**
     * Runs Dijkstra's algorithm completely from {@code source} and returns
     * the shortest-path results to every other node in the network.
     *
     * @param source The source (root) node identifier.
     * @return A sorted map of destination → {@link PathResult}.
     */
    public Map<String, PathResult> computeAll(String source) {
        PriorityQueue<Entry> pq      = new PriorityQueue<>();
        Set<String>          settled = new HashSet<>();
        Map<String, Integer> dist    = new HashMap<>();
        Map<String, String>  prev    = new HashMap<>();

        for (String node : getAllNodes()) {
            dist.put(node, Integer.MAX_VALUE);
        }
        dist.put(source, 0);
        pq.add(new Entry(source, 0));

        while (!pq.isEmpty()) {
            Entry current = pq.poll();
            if (settled.contains(current.node)) continue;
            if (current.dist > dist.getOrDefault(current.node, Integer.MAX_VALUE)) continue;
            settled.add(current.node);

            for (Map.Entry<String, Integer> nb : graph.getOrDefault(current.node, Collections.emptyMap()).entrySet()) {
                String neighbor = nb.getKey();
                if (settled.contains(neighbor)) continue;
                int newDist = current.dist + nb.getValue();
                if (newDist < dist.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    dist.put(neighbor, newDist);
                    prev.put(neighbor, current.node);
                    pq.add(new Entry(neighbor, newDist));
                }
            }
        }

        Map<String, PathResult> results = new TreeMap<>();
        for (String node : getAllNodes()) {
            if (node.equals(source)) continue;
            int d = dist.getOrDefault(node, Integer.MAX_VALUE);
            List<String> path = (d == Integer.MAX_VALUE)
                    ? Collections.emptyList()
                    : buildPath(node, prev);
            results.put(node, new PathResult(node, path, d == Integer.MAX_VALUE ? -1 : d));
        }
        return results;
    }

    // =========================================================
    // Helpers
    // =========================================================

    private List<String> buildPath(String dest, Map<String, String> prev) {
        LinkedList<String> path = new LinkedList<>();
        String current = dest;
        while (current != null) {
            path.addFirst(current);
            current = prev.get(current);
        }
        return path;
    }

    /** Collects every node referenced in the graph (as source or neighbour). */
    private Set<String> getAllNodes() {
        Set<String> nodes = new HashSet<>(graph.keySet());
        for (Map<String, Integer> neighbors : graph.values()) {
            nodes.addAll(neighbors.keySet());
        }
        return nodes;
    }
}

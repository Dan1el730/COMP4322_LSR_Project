package graph;

import java.io.*;
import java.util.*;

/**
 * Utility class for loading and saving network topologies in LSA file format.
 *
 * <p>Expected file format (one node per line):
 * <pre>
 *   NodeName: Neighbor1:Cost1 Neighbor2:Cost2 ...
 * </pre>
 * Lines beginning with {@code #} are treated as comments and ignored.
 * Links are bidirectional: if A→B exists, B→A is added automatically if absent.
 */
public class LSALoader {

    /**
     * Loads a network topology from an LSA file.
     *
     * @param filePath Path to the {@code .lsa} file.
     * @return An adjacency-list map: node → (neighbour → cost).
     * @throws IOException If the file cannot be read.
     */
    public static Map<String, Map<String, Integer>> load(String filePath) throws IOException {
        Map<String, Map<String, Integer>> graph = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                int colonIdx = line.indexOf(':');
                if (colonIdx < 0) continue;

                String nodeName = line.substring(0, colonIdx).trim();
                if (nodeName.isEmpty()) continue;

                String rest = line.substring(colonIdx + 1).trim();
                graph.putIfAbsent(nodeName, new LinkedHashMap<>());

                if (!rest.isEmpty()) {
                    for (String token : rest.split("\\s+")) {
                        String[] parts = token.split(":");
                        if (parts.length != 2) continue;
                        String neighbor = parts[0].trim();
                        try {
                            int cost = Integer.parseInt(parts[1].trim());
                            graph.get(nodeName).put(neighbor, cost);
                            graph.putIfAbsent(neighbor, new LinkedHashMap<>());
                        } catch (NumberFormatException e) {
                            System.err.println("Warning: Skipping invalid cost in: " + token);
                        }
                    }
                }
            }
        }

        // Enforce bidirectionality
        for (Map.Entry<String, Map<String, Integer>> entry : new HashMap<>(graph).entrySet()) {
            for (Map.Entry<String, Integer> nb : entry.getValue().entrySet()) {
                graph.get(nb.getKey()).putIfAbsent(entry.getKey(), nb.getValue());
            }
        }

        return graph;
    }

    /**
     * Saves a network topology to an LSA file using space-separated format.
     *
     * @param graph    The adjacency-list map to save.
     * @param filePath Destination file path.
     * @throws IOException If the file cannot be written.
     */
    public static void save(Map<String, Map<String, Integer>> graph, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            List<String> nodes = new ArrayList<>(graph.keySet());
            Collections.sort(nodes);
            for (String node : nodes) {
                StringBuilder sb = new StringBuilder(node).append(":");
                Map<String, Integer> neighbors = graph.getOrDefault(node, Collections.emptyMap());
                List<String> sortedNeighbors = new ArrayList<>(neighbors.keySet());
                Collections.sort(sortedNeighbors);
                for (String neighbor : sortedNeighbors) {
                    sb.append(" ").append(neighbor).append(":").append(neighbors.get(neighbor));
                }
                writer.write(sb.toString());
                writer.newLine();
            }
        }
    }
}

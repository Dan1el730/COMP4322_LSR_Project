package lsr;

import java.util.Collections;
import java.util.List;

/**
 * Represents the shortest-path result from a source node to a specific destination.
 */
public class PathResult {

    public final String destination;
    public final List<String> path;
    public final int cost;

    public PathResult(String destination, List<String> path, int cost) {
        this.destination = destination;
        this.path = Collections.unmodifiableList(path);
        this.cost = cost;
    }

    /** Returns true if the destination is reachable from the source. */
    public boolean isReachable() {
        return cost >= 0 && !path.isEmpty();
    }

    /**
     * Formats the result as a summary line, e.g.:
     * {@code B: Path: A>B Cost: 5}
     */
    public String toSummaryLine() {
        if (!isReachable()) {
            return destination + ": UNREACHABLE";
        }
        return destination + ": Path: " + String.join(">", path) + " Cost: " + cost;
    }
}

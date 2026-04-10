package lsr;

import java.util.List;

/**
 * Represents the result of one Dijkstra step (one newly settled node).
 */
public class StepResult {

    public final String foundNode;
    public final List<String> pathToNode;
    public final int cost;
    public final boolean done;

    public StepResult(String foundNode, List<String> pathToNode, int cost, boolean done) {
        this.foundNode = foundNode;
        this.pathToNode = pathToNode;
        this.cost = cost;
        this.done = done;
    }

    /**
     * Formats the step as a status line, e.g.:
     * {@code Found F: Path: A>B>F Cost: 7}
     */
    public String toStepLine() {
        if (done || foundNode == null) {
            return "[All nodes settled — computation complete]";
        }
        return "Found " + foundNode + ": Path: " + String.join(">", pathToNode) + " Cost: " + cost;
    }
}

# COMP4322 — Link State Routing (LSR) Project

A Java program that emulates the **Link State Routing (LSR) protocol** used by routers in a network. Each router exchanges Link State Advertisements (LSAs) to build a complete picture of the network topology, then computes shortest paths to all other routers using **Dijkstra's algorithm**.

## Project Structure

```
COMP4322_LSR_Project/
├── LSRCompute.java       # Main entry point
├── data/
│   └── routes.lsa        # Network topology input file
├── graph/
│   └── LSALoader.java    # LSA file parser and saver
├── gui/
│   ├── GraphPanel.java   # Swing graph visualisation panel
│   └── MainWindow.java   # Main GUI window
└── lsr/
    ├── DijkstraLSR.java  # Dijkstra's algorithm (compute-all & step-by-step)
    ├── PathResult.java   # Shortest path result model
    └── StepResult.java   # Single Dijkstra step result model
```

## LSA File Format

Each node occupies exactly one line:

```
NodeName: Neighbor1:Cost1 Neighbor2:Cost2 ...
```

Example (`data/routes.lsa`):

```
A: B:5 C:3 D:5
B: A:5 C:4 E:3 F:2
C: A:3 B:4 D:1 E:6
D: A:5 C:1 E:3
E: B:3 C:6 D:3 F:5
F: B:2 E:5
```

Links are **bidirectional** — if A→B has cost 5, then B→A is also cost 5.

## Compilation

```bash
javac -d out graph/LSALoader.java lsr/PathResult.java lsr/StepResult.java \
      lsr/DijkstraLSR.java gui/GraphPanel.java gui/MainWindow.java LSRCompute.java
```

## Usage

### Direct CLI (Compute-All mode)

```bash
java -cp out LSRCompute data/routes.lsa A CA
```

Output:
```
Source A:
B: Path: A>B Cost: 5
C: Path: A>C Cost: 3
D: Path: A>C>D Cost: 4
E: Path: A>C>D>E Cost: 7
F: Path: A>B>F Cost: 7
```

### Direct CLI (Single-Step mode)

```bash
java -cp out LSRCompute data/routes.lsa A SS
```

Pauses after each settled node and prompts you to press Enter to continue:
```
Found C: Path: A>C Cost: 3 [press Enter to continue]
Found D: Path: A>C>D Cost: 4 [press Enter to continue]
...
```

### Interactive Menu

```bash
java -cp out LSRCompute
```

Presents a menu to choose **CLI** or **GUI**, load/modify the network, and run LSR computation.

### GUI

Select option `2` from the interactive menu (or use the direct GUI launch).

- **Source** field — enter the source node name
- **Run Dijkstra (All)** — full computation, result table populated instantly
- **Step Mode / Next Step** — step through each settled node visually
  - Amber node = currently settled
  - Blue nodes = previously settled
  - Green edges = best-path edges found so far
- **Modify Network** — add/delete nodes and links (saved back to the `.lsa` file)
- **Reload Data** — reload topology from file

## Requirements

- Java 11 or higher

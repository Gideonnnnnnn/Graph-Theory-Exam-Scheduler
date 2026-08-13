import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced exam scheduler:
 *  - Builds a conflict graph from enrollments (student_id|c1,c2,c3,...)
 *  - Splits into connected components and solves each independently
 *  - Computes an EXACT maximum clique (Tomita) for a strong lower bound (LB)
 *  - Uses DSATUR to get a tight upper bound (UB)
 *  - Runs exact Branch-and-Bound with:
 *      • Precoloring of the max clique into earliest colors (symmetry breaking)
 *      • Color-precedence (no gaps in used colors)
 *      • DSATUR-style branching order (highest saturation, break ties by degree)
 *  - Merges component colorings into a global schedule
 *
 * No external dependencies.
 *
 * Input format for buildFromFile(...):
 *   Each line:  studentId|CLASS101,CLASS205,CLASS333
 */
public class ExamSchedulerAdv {

    /* ======================= Graph ======================= */

    public static class Graph {
        private final Map<String, Set<String>> adj = new HashMap<>();

        public void addVertex(String v) {
            adj.computeIfAbsent(v, k -> new HashSet<>());
        }

        public void addEdge(String a, String b) {
            if (a.equals(b)) return;
            addVertex(a);
            addVertex(b);
            adj.get(a).add(b);
            adj.get(b).add(a);
        }

        public boolean isEmpty() {
            return adj.isEmpty();
        }

        public Set<String> vertices() {
            return adj.keySet();
        }

        public Set<String> neighbors(String v) {
            return adj.getOrDefault(v, Collections.emptySet());
        }

        public int degree(String v) {
            return adj.getOrDefault(v, Collections.emptySet()).size();
        }

        public Graph induced(Set<String> keep) {
            Graph g = new Graph();
            for (String v : keep) g.addVertex(v);
            for (String v : keep) {
                for (String u : neighbors(v)) {
                    if (keep.contains(u)) g.addEdge(v, u);
                }
            }
            return g;
        }

        public List<Set<String>> connectedComponents() {
            List<Set<String>> comps = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String s : vertices()) {
                if (seen.contains(s)) continue;
                Set<String> comp = new HashSet<>();
                Deque<String> dq = new ArrayDeque<>();
                dq.add(s);
                seen.add(s);
                comp.add(s);
                while (!dq.isEmpty()) {
                    String v = dq.poll();
                    for (String u : neighbors(v)) {
                        if (!seen.contains(u)) {
                            seen.add(u); comp.add(u); dq.add(u);
                        }
                    }
                }
                comps.add(comp);
            }
            return comps;
        }
    }

    /* ======================= Tomita Max Clique (exact) ======================= */

    private static class Tomita {
        final String[] nodes;                  // index -> vertex id
        final Map<String,Integer> idx;         // vertex id -> index
        final long[] N;                        // bitset neighbors
        int n;
        int[] best; int bestSize;

        Tomita(Graph g, Set<String> subset) {
            this.nodes = subset.toArray(new String[0]);
            Arrays.sort(this.nodes);
            this.n = nodes.length;
            this.idx = new HashMap<>();
            for (int i = 0; i < n; i++) idx.put(nodes[i], i);
            this.N = new long[n];
            for (int i = 0; i < n; i++) {
                long bs = 0L;
                for (String u : g.neighbors(nodes[i])) {
                    Integer j = idx.get(u);
                    if (j != null) bs |= (1L << j);
                }
                N[i] = bs;
            }
            this.best = new int[n];
            this.bestSize = 0;
        }

        private int choosePivot(long P) {
            if (P == 0L) return -1;
            int pivot = -1, max = -1;
            long c = P;
            while (c != 0L) {
                int u = Long.numberOfTrailingZeros(c);
                c &= c - 1;
                int deg = Long.bitCount(P & N[u]);
                if (deg > max) { max = deg; pivot = u; }
            }
            return pivot;
        }

        private void expand(int[] R, int rSize, long P) {
            if (P == 0L) {
                if (rSize > bestSize) {
                    bestSize = rSize;
                    System.arraycopy(R, 0, best, 0, rSize);
                }
                return;
            }
            if (rSize + Long.bitCount(P) <= bestSize) return;

            int u = choosePivot(P);
            long X = (u == -1) ? P : (P & ~N[u]);

            while (X != 0L) {
                int v = Long.numberOfTrailingZeros(X);
                X &= X - 1;
                R[rSize] = v;
                expand(R, rSize + 1, P & N[v]);
                P &= ~(1L << v);
                if (rSize + Long.bitCount(P) <= bestSize) return;
            }
        }

        public List<String> run() {
            int[] R = new int[n];
            expand(R, 0, (n >= 64) ? -1L : ((1L << n) - 1));
            List<String> clique = new ArrayList<>();
            for (int i = 0; i < bestSize; i++) clique.add(nodes[best[i]]);
            return clique;
        }
    }

    /* ======================= DSATUR heuristic (upper bound) ======================= */

    private static Map<String,Integer> dsaturColoring(Graph g, Set<String> nodes) {
        Map<String,Integer> color = new HashMap<>();
        Map<String,Integer> degree = new HashMap<>();
        for (String v : nodes) degree.put(v, g.degree(v));

        Comparator<String> pickOrder = (a, b) -> {
            int sa = saturation(g, color, a);
            int sb = saturation(g, color, b);
            if (sa != sb) return Integer.compare(sb, sa);
            int da = degree.get(a), db = degree.get(b);
            if (da != db) return Integer.compare(db, da);
            return a.compareTo(b);
        };

        Set<String> uncolored = new HashSet<>(nodes);
        while (!uncolored.isEmpty()) {
            String v = Collections.max(uncolored, pickOrder);
            Set<Integer> forb = new HashSet<>();
            for (String u : g.neighbors(v)) if (color.containsKey(u)) forb.add(color.get(u));
            int c = 0; while (forb.contains(c)) c++;
            color.put(v, c);
            uncolored.remove(v);
        }
        return color;
    }

    private static int saturation(Graph g, Map<String,Integer> color, String v) {
        Set<Integer> s = new HashSet<>();
        for (String u : g.neighbors(v)) if (color.containsKey(u)) s.add(color.get(u));
        return s.size();
    }

    /* ======================= Exact Branch-and-Bound Coloring ======================= */

    private static class ExactColoring {
        final Graph g;
        final List<String> orderSeed; // for tiebreaks
        final int K;                  // target number of colors
        final Map<String,Integer> assignment = new HashMap<>();
        final Map<String,Set<Integer>> forbid = new HashMap<>();
        boolean found = false;

        ExactColoring(Graph g, Set<String> nodes, int K,
                      Map<String,Integer> precolor /* may be null */,
                      Map<String,Integer> hint /* may be null */) {
            this.g = g;
            this.K = K;
            this.orderSeed = new ArrayList<>(nodes);
            this.orderSeed.sort(Comparator.comparingInt((String v) -> -g.degree(v)).thenComparing(v -> v));
            // init domains / forbid
            for (String v : nodes) forbid.put(v, new HashSet<>());
            if (precolor != null) {
                for (Map.Entry<String,Integer> e : precolor.entrySet()) {
                    String v = e.getKey(); int c = e.getValue();
                    if (!forbid.containsKey(v)) continue;
                    assign(v, c); // will set found=false but enforce constraints
                }
            }
            if (hint != null) {
                // record as soft preference by sorting (we’ll try hinted color first)
            }
        }

        private void assign(String v, int c) {
            assignment.put(v, c);
            // propagate: neighbors cannot be color c
            for (String u : g.neighbors(v)) {
                Set<Integer> f = forbid.get(u);
                if (f != null) f.add(c);
            }
        }

        private void unassign(String v, int c) {
            assignment.remove(v);
            for (String u : g.neighbors(v)) {
                Set<Integer> f = forbid.get(u);
                if (f != null) f.remove(c);
            }
        }

        private String pickNext() {
            // DSATUR branching: max saturation, then degree, then seed order
            String best = null;
            int bestSat = -1, bestDeg = -1;
            for (String v : forbid.keySet()) {
                if (assignment.containsKey(v)) continue;
                int sat = saturation(g, assignment, v);
                int deg = g.degree(v);
                if (sat > bestSat || (sat == bestSat && deg > bestDeg)
                        || (sat == bestSat && deg == bestDeg && (best == null ||
                        orderSeed.indexOf(v) < orderSeed.indexOf(best)))) {
                    bestSat = sat; bestDeg = deg; best = v;
                }
            }
            return best;
        }

        private List<Integer> colorOrder(String v, Map<String,Integer> hint) {
            // Try colors 0..K-1, but push any hinted color first
            List<Integer> ord = new ArrayList<>();
            Integer hinted = (hint == null) ? null : hint.get(v);
            if (hinted != null) ord.add(hinted);
            for (int c = 0; c < K; c++) if (!ord.contains(c)) ord.add(c);
            return ord;
        }

        public boolean solve(Map<String,Integer> hint) {
            // quick invalidity: any precolored conflict?
            for (String v : assignment.keySet()) {
                int c = assignment.get(v);
                for (String u : g.neighbors(v)) {
                    Integer cu = assignment.get(u);
                    if (cu != null && cu == c) return false;
                }
            }
            backtrack(hint);
            return found;
        }

        private void backtrack(Map<String,Integer> hint) {
            if (found) return;
            if (assignment.size() == forbid.size()) { found = true; return; }

            String v = pickNext();
            if (v == null) { found = true; return; }

            // Color precedence: don't allow gaps. If we use a new color t,
            // it must be exactly 1 + maxUsed so far.
            int maxUsed = -1;
            for (int c : assignment.values()) if (c > maxUsed) maxUsed = c;

            Set<Integer> forbSet = new HashSet<>();
            for (String u : g.neighbors(v)) {
                Integer cu = assignment.get(u);
                if (cu != null) forbSet.add(cu);
            }

            for (int c : colorOrder(v, hint)) {
                if (c >= K) continue;
                if (forbSet.contains(c)) continue;
                if (c > maxUsed + 1) continue;          // precedence: no gaps
                if (c == maxUsed + 1 && c >= K) continue;

                assign(v, c);
                backtrack(hint);
                if (found) return;
                unassign(v, c);
            }
        }
    }

    /* ======================= Scheduler: building & solving ======================= */

    private final Graph graph = new Graph();
    private final Map<String, Set<String>> studentCourses = new HashMap<>();

    public void addClass(String className) {
        graph.addVertex(className);
    }

    public void addConflict(String class1, String class2) {
        graph.addEdge(class1, class2);
    }

    /** Build conflict graph from enrollments file (format: studentId|C1,C2,...) */
    public void buildFromFile(String path) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|");
                if (parts.length != 2) continue;
                String student = parts[0].trim();
                String[] courses = parts[1].split(",");
                Set<String> set = new HashSet<>();
                for (String c : courses) {
                    String cc = c.trim();
                    if (cc.isEmpty()) continue;
                    set.add(cc);
                    addClass(cc);
                }
                if (set.size() >= 2) {
                    // Make a clique among this student's courses
                    List<String> list = new ArrayList<>(set);
                    for (int i = 0; i < list.size(); i++) {
                        for (int j = i + 1; j < list.size(); j++) {
                            addConflict(list.get(i), list.get(j));
                        }
                    }
                }
                studentCourses.put(student, set);
            }
        }
    }

    /** Solve entire graph by components; returns assignments and summary. */
    public Result solveExact() {
        if (graph.isEmpty()) {
            return new Result(Collections.emptyMap(), 0, Collections.emptyList());
        }

        List<Set<String>> comps = graph.connectedComponents();
        List<ComponentResult> details = new ArrayList<>();
        Map<String,Integer> global = new HashMap<>();
        int globalK = 0;

        for (Set<String> compVerts : comps) {
            Graph sub = graph.induced(compVerts);

            // Lower bound: EXACT maximum clique
            List<String> clique = new Tomita(sub, compVerts).run();
            int LB = clique.size();

            // Upper bound: DSATUR
            Map<String,Integer> ds = dsaturColoring(sub, compVerts);
            int UB = ds.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;

            // Precolor clique into earliest slots 0..LB-1 (symmetry break)
            Map<String,Integer> precolor = new HashMap<>();
            for (int t = 0; t < clique.size(); t++) precolor.put(clique.get(t), t);

            // Use DSATUR as a hint
            Map<String,Integer> hint = new HashMap<>(ds);

            // Exact search: try K = UB, UB-1, ..., LB
            Map<String,Integer> best = new HashMap<>(ds);
            int bestK = UB;
            for (int K = UB; K >= LB; K--) {
                // Remap hint colors into 0..K-1 for safety
                Map<String,Integer> hintK = new HashMap<>();
                for (String v : compVerts) {
                    int c = hint.getOrDefault(v, 0);
                    hintK.put(v, c % K);
                }

                ExactColoring solver = new ExactColoring(sub, compVerts, K, precolor, hintK);
                boolean ok = solver.solve(hintK);
                if (ok) {
                    bestK = K;
                    best = new HashMap<>(solver.assignment);
                } else {
                    // first infeasible while decreasing ⇒ previous best is minimal
                    break;
                }
            }

            // Merge component solution; color indices are local 0..bestK-1, and we re-use them globally
            for (Map.Entry<String,Integer> e : best.entrySet()) {
                global.put(e.getKey(), e.getValue());
            }
            globalK = Math.max(globalK, bestK);

            details.add(new ComponentResult(compVerts.size(), LB, UB, bestK, clique));
        }

        return new Result(global, globalK, details);
    }

    /* ======================= Output structures ======================= */

    public static class ComponentResult {
        public final int classes;
        public final int lowerBoundClique;
        public final int upperBoundDsatur;
        public final int bestK;
        public final List<String> exampleClique;
        ComponentResult(int classes, int lb, int ub, int k, List<String> clique) {
            this.classes = classes;
            this.lowerBoundClique = lb;
            this.upperBoundDsatur = ub;
            this.bestK = k;
            this.exampleClique = clique;
        }
    }

    public static class Result {
        public final Map<String,Integer> assignment;  // class -> slot index (0..K-1)
        public final int K;                           // minimal slots found (often optimal)
        public final List<ComponentResult> components;

        Result(Map<String,Integer> assignment, int K, List<ComponentResult> components) {
            this.assignment = assignment;
            this.K = K;
            this.components = components;
        }

        /** Human-readable schedule: "Time Slot t+1" -> list of classes */
        public Map<String, List<String>> toSchedule() {
            Map<Integer, List<String>> by = new HashMap<>();
            for (Map.Entry<String,Integer> e : assignment.entrySet()) {
                by.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
            }
            Map<String, List<String>> sched = new LinkedHashMap<>();
            List<Integer> slots = new ArrayList<>(by.keySet());
            Collections.sort(slots);
            for (int t : slots) {
                List<String> cls = by.get(t);
                Collections.sort(cls);
                sched.put("Time Slot " + (t + 1), cls);
            }
            return sched;
        }

        public boolean isValid(Graph g) {
            for (String v : g.vertices()) {
                Integer cv = assignment.get(v);
                if (cv == null) return false;
                for (String u : g.neighbors(v)) {
                    Integer cu = assignment.get(u);
                    if (cu == null || cu.equals(cv)) return false;
                }
            }
            return true;
        }

        public void printSummary() {
            System.out.println("\n=== Global Summary ===");
            System.out.println("Minimal slots found (often optimal): " + K);
            System.out.println("Components: " + components.size());
            int i = 1;
            for (ComponentResult cr : components) {
                System.out.printf(Locale.US,
                        "\n-- Component %d --%nClasses: %d  LB(max clique): %d  UB(DSATUR): %d  BestK: %d%n",
                        i++, cr.classes, cr.lowerBoundClique, cr.upperBoundDsatur, cr.bestK);
                System.out.println("Clique example: " + cr.exampleClique);
            }
            Map<String,List<String>> sched = toSchedule();
            for (Map.Entry<String,List<String>> e : sched.entrySet()) {
                List<String> lst = e.getValue();
                System.out.printf("%n%s: %d classes%n", e.getKey(), lst.size());
                if (lst.size() <= 25) {
                    System.out.println(String.join(", ", lst));
                } else {
                    System.out.println(lst.subList(0, 25) + " ...");
                }
            }
        }
    }

    /* ======================= Demo CLI ======================= */

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java ExamSchedulerAdv <enrollments.txt>");
            System.err.println("Line format: studentId|CLASS101,CLASS205,CLASS333");
            System.exit(1);
        }
        String path = args[0];
        ExamSchedulerAdv sched = new ExamSchedulerAdv();
        try {
            sched.buildFromFile(path);
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
            System.exit(2);
        }

        Result res = sched.solveExact();
        res.printSummary();

        // Optional: validity check
        if (!res.isValid(sched.graph)) {
            System.err.println("WARNING: schedule is not a valid coloring!");
        }
    }
}

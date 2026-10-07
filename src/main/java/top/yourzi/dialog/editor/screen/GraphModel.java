package top.yourzi.dialog.editor.screen;

import top.yourzi.dialog.editor.TextCodec;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the relationship graph shows, independent of how it is drawn.
 *
 * <p>Nodes carry their ports: a node that continues has one output port beside its title, a node
 * with choices has one output port per choice row, so a link always starts at the thing that causes
 * it. Positions come from the writer's saved arrangement where there is one and from an automatic
 * layered layout everywhere else.
 */
final class GraphModel {
    static final int WIDTH = 176;
    static final int HEAD = 18;
    static final int LINE = 13;
    static final int CHOICE = 14;
    static final int PAD = 5;
    static final int COLUMN_GAP = 80;
    static final int ROW_GAP = 18;
    static final int GROUP_GAP = 40;
    /** Port index of the single "continue" output. */
    static final int NEXT_PORT = -1;

    enum Kind {
        /** Falls through to the next node in file order. */
        CONTINUE,
        /** Explicit {@code next} jump. */
        JUMP,
        CHOICE,
        /** Starts another dialogue file through a command. */
        FILE
    }

    static final class Node {
        final String id;
        final DialogEntry entry;
        final boolean file;
        final List<String> choices = new ArrayList<>();
        final List<Integer> choiceColors = new ArrayList<>();
        final String summary;
        final int height;
        boolean start;
        boolean unreachable;
        boolean ends;
        int x;
        int y;
        boolean placed;

        Node(String id, DialogEntry entry, boolean file, String summary) {
            this.id = id;
            this.entry = entry;
            this.file = file;
            this.summary = summary;
            if (entry != null && entry.getOptions() != null) {
                for (DialogOption option : entry.getOptions()) {
                    this.choices.add(option == null ? "" : TextCodec.preview(option.getText()));
                }
            }
            this.height = HEAD + LINE + PAD + this.choices.size() * CHOICE + (this.choices.isEmpty() ? 0 : 2);
        }

        boolean hasChoices() {
            return !this.choices.isEmpty();
        }

        int inputX() {
            return this.x;
        }

        int inputY() {
            return this.y + HEAD / 2;
        }

        int portX() {
            return this.x + WIDTH;
        }

        int portY(int port) {
            if (port == NEXT_PORT) {
                return this.y + HEAD / 2;
            }
            return this.y + HEAD + LINE + PAD + port * CHOICE + CHOICE / 2;
        }

        /** Picks the point on the left edge where this node's incoming link {@code index} arrives. */
        int inputY(int index, int count) {
            if (count <= 1) {
                return this.inputY();
            }
            int span = Math.min(HEAD - 4, Math.max(6, this.height - 4));
            int steps = count - 1;
            int step = Math.max(4, span / Math.max(1, steps));
            int first = this.y + Math.max(2, (HEAD - (steps * step)) / 2);
            return Math.min(this.y + this.height - 3, first + index * step);
        }

        boolean contains(double worldX, double worldY) {
            return worldX >= this.x && worldX < this.x + WIDTH && worldY >= this.y && worldY < this.y + this.height;
        }
    }

    /**
     * A link. {@code port} is the output it leaves ({@link #NEXT_PORT} for the single continue
     * output); {@code arrival} is the slot on the target's left edge it lands on, which the layout
     * spreads out so several links into one node do not overlap.
     */
    static final class Edge {
        private final Node from;
        private final int port;
        private final Node to;
        private final String missing;
        private final Kind kind;
        private final int color;
        private int arrival;

        Edge(Node from, int port, Node to, String missing, Kind kind, int color) {
            this.from = from;
            this.port = port;
            this.to = to;
            this.missing = missing;
            this.kind = kind;
            this.color = color;
        }

        Node from() {
            return this.from;
        }

        int port() {
            return this.port;
        }

        Node to() {
            return this.to;
        }

        /** Target id of a link that points nowhere, drawn beside the red marker. */
        String missing() {
            return this.missing;
        }

        Kind kind() {
            return this.kind;
        }

        int color() {
            return this.color;
        }

        /** World Y where this link meets its target's left edge. */
        int arrivalY(int count) {
            return this.to.inputY(this.arrival, count);
        }

        /** Detour around nodes as x0, y0, x1, y1, ...; null when the plain curve is clear. */
        float[] route;
    }

    /** How many links arrive at each node, keyed by node id. */
    final Map<String, Integer> arrivals = new HashMap<>();

    /**
     * Gives each link into a node its own slot on the node's left edge, ordered by where the source
     * sits vertically, so links fan in without crossing one another at the target.
     */
    void assignArrivals() {
        this.arrivals.clear();
        Map<String, List<Edge>> incoming = this.incomingEdges();
        for (Map.Entry<String, List<Edge>> entry : incoming.entrySet()) {
            List<Edge> list = new ArrayList<>(entry.getValue());
            list.sort((a, b) -> Integer.compare(a.from().portY(a.port()), b.from().portY(b.port())));
            for (int i = 0; i < list.size(); i++) {
                list.get(i).arrival = i;
            }
            this.arrivals.put(entry.getKey(), list.size());
        }
    }

    final Map<String, Node> nodes = new LinkedHashMap<>();
    final List<Edge> edges = new ArrayList<>();

    boolean isEmpty() {
        return this.nodes.isEmpty();
    }

    /** {minX, minY, maxX, maxY} of everything drawn. */
    int[] bounds() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Node node : this.nodes.values()) {
            minX = Math.min(minX, node.x);
            minY = Math.min(minY, node.y);
            maxX = Math.max(maxX, node.x + WIDTH);
            maxY = Math.max(maxY, node.y + node.height);
        }
        return this.nodes.isEmpty() ? new int[]{0, 0, 1, 1} : new int[]{minX, minY, maxX, maxY};
    }

    Node nodeAt(double worldX, double worldY) {
        List<Node> list = new ArrayList<>(this.nodes.values());
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).contains(worldX, worldY)) {
                return list.get(i);
            }
        }
        return null;
    }

    // ----- building -----

    static GraphModel ofSequence(DialogSequence sequence, Map<String, int[]> saved) {
        GraphModel model = new GraphModel();
        List<DialogEntry> entries = NodeGraph.entries(sequence);
        if (entries.isEmpty()) {
            return model;
        }
        Set<String> unreachable = NodeGraph.unreachable(sequence);
        for (DialogEntry entry : entries) {
            String speaker = TextCodec.preview(entry.getSpeaker());
            String text = TextCodec.preview(entry.getText());
            Node node = new Node(entry.getId(), entry, false, speaker.isEmpty() ? text : speaker + "：" + text);
            node.start = NodeGraph.isStart(sequence, entry);
            node.unreachable = unreachable.contains(entry.getId());
            node.ends = entry.isEndDialog() || (!NodeGraph.hasOptions(entry) && NodeGraph.implicitNext(sequence, entry) == null);
            model.nodes.put(entry.getId(), node);
        }

        Map<String, Integer> branchColors = new HashMap<>();
        Map<String, Node> fileNodes = new LinkedHashMap<>();
        for (DialogEntry entry : entries) {
            Node from = model.nodes.get(entry.getId());
            if (NodeGraph.hasOptions(entry)) {
                for (int i = 0; i < entry.getOptions().length; i++) {
                    DialogOption option = entry.getOptions()[i];
                    String target = option == null ? null : option.getTargetId();
                    int color = Theme.TEXT_MUTED;
                    if (target != null && !target.isBlank()) {
                        color = branchColors.computeIfAbsent(target, key -> branchColor(branchColors.size()));
                        Node to = model.nodes.get(target);
                        model.edges.add(new Edge(from, i, to, to == null ? target : null, Kind.CHOICE, to == null ? Theme.DANGER : color));
                    }
                    from.choiceColors.add(color);
                }
            } else if (!entry.isEndDialog()) {
                boolean explicit = entry.getNextId() != null && !entry.getNextId().isBlank();
                DialogEntry next = NodeGraph.implicitNext(sequence, entry);
                if (next != null) {
                    model.edges.add(new Edge(from, NEXT_PORT, model.nodes.get(next.getId()), null,
                            explicit ? Kind.JUMP : Kind.CONTINUE, explicit ? Theme.CYAN : 0xFF8A8E97));
                } else if (explicit) {
                    model.edges.add(new Edge(from, NEXT_PORT, null, entry.getNextId(), Kind.JUMP, Theme.DANGER));
                }
            }
            for (String file : GraphModel.startedFiles(entry)) {
                Node stub = fileNodes.computeIfAbsent(file, id -> new Node("file:" + id, null, true, id));
                model.edges.add(new Edge(from, from.hasChoices() ? 0 : NEXT_PORT, stub, null, Kind.FILE, Theme.CYAN));
            }
        }
        for (Node stub : fileNodes.values()) {
            model.nodes.put(stub.id, stub);
        }
        model.layout(sequence == null || sequence.getFirstEntry() == null ? null : sequence.getFirstEntry().getId(), saved);
        return model;
    }

    /** One node per dialogue file; edges where a file starts another. */
    static GraphModel ofFiles(List<DialogSequence> files, String activeId, Map<String, int[]> saved) {
        GraphModel model = new GraphModel();
        for (DialogSequence file : files) {
            String title = file.getTitle() == null || file.getTitle().isBlank() ? "" : file.getTitle();
            Node node = new Node(file.getId(), null, true,
                    Theme.tr("status.nodes", NodeGraph.entries(file).size()).getString() + (title.isEmpty() ? "" : "  ·  " + title));
            node.start = file.getId().equals(activeId);
            model.nodes.put(file.getId(), node);
        }
        for (DialogSequence file : files) {
            Node from = model.nodes.get(file.getId());
            List<String> seen = new ArrayList<>();
            for (DialogEntry entry : NodeGraph.entries(file)) {
                for (String target : startedFiles(entry)) {
                    if (seen.contains(target)) {
                        continue;
                    }
                    seen.add(target);
                    Node to = model.nodes.get(target);
                    model.edges.add(new Edge(from, NEXT_PORT, to, to == null ? target : null, Kind.FILE,
                            to == null ? Theme.DANGER : Theme.CYAN));
                }
            }
        }
        model.layout(null, saved);
        return model;
    }

    /** Dialogue ids started by {@code dialog ... show ... <id>} commands on the node or its choices. */
    static List<String> startedFiles(DialogEntry entry) {
        List<String> commands = new ArrayList<>();
        if (entry.getCommands() != null) {
            commands.addAll(entry.getCommands());
        }
        if (entry.getOptions() != null) {
            for (DialogOption option : entry.getOptions()) {
                if (option != null && option.getCommand() != null) {
                    commands.addAll(option.getCommand());
                }
            }
        }
        List<String> files = new ArrayList<>();
        for (String command : commands) {
            if (command == null) {
                continue;
            }
            String[] tokens = command.trim().replaceFirst("^/", "").split("\\s+");
            if (tokens.length < 3 || !tokens[0].equalsIgnoreCase("dialog")) {
                continue;
            }
            for (int i = 1; i < tokens.length - 1; i++) {
                if (tokens[i].equalsIgnoreCase("show")) {
                    String id = tokens[tokens.length - 1];
                    if (!files.contains(id)) {
                        files.add(id);
                    }
                    break;
                }
            }
        }
        return files;
    }

    static int branchColor(int index) {
        float hue = (0.58f + index * 0.61803398875f) % 1.0f;
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.5f, 0.95f) & 0xFFFFFF);
    }

    // ----- layout -----

    private Map<String, List<Edge>> outgoingEdges() {
        Map<String, List<Edge>> map = new HashMap<>();
        for (Edge edge : this.edges) {
            if (edge.to() != null) {
                map.computeIfAbsent(edge.from().id, key -> new ArrayList<>()).add(edge);
            }
        }
        return map;
    }

    private Map<String, List<Edge>> incomingEdges() {
        Map<String, List<Edge>> map = new HashMap<>();
        for (Edge edge : this.edges) {
            if (edge.to() != null) {
                map.computeIfAbsent(edge.to().id, key -> new ArrayList<>()).add(edge);
            }
        }
        return map;
    }

    /**
     * Saved positions first. Everything else is laid out as a layered graph: depth by longest path
     * from a root, then a few barycenter sweeps so the rows of one column line up with the nodes
     * they connect to. That ordering is what keeps links from crossing each other, which matters far
     * more than compactness on a big script.
     *
     * <p>A node the writer placed by hand keeps its coordinates and anchors its neighbours.
     */
    private void layout(String startId, Map<String, int[]> saved) {
        Map<String, List<Edge>> outgoing = this.outgoingEdges();
        Map<String, List<Edge>> incoming = this.incomingEdges();
        Map<Node, Integer> column = this.assignColumns(startId, outgoing, incoming);
        List<List<Node>> layers = new ArrayList<>();
        int maxColumn = 0;
        for (int value : column.values()) {
            maxColumn = Math.max(maxColumn, value);
        }
        for (int i = 0; i <= maxColumn; i++) {
            layers.add(new ArrayList<>());
        }
        for (Node node : this.nodes.values()) {
            layers.get(column.getOrDefault(node, 0)).add(node);
        }

        // Components are laid out one under another so unrelated scripts do not interleave.
        List<List<Node>> components = this.components();
        int top = 0;
        for (List<Node> component : components) {
            Set<Node> members = new HashSet<>(component);
            List<List<Node>> local = new ArrayList<>();
            for (List<Node> layer : layers) {
                List<Node> kept = new ArrayList<>();
                for (Node node : layer) {
                    if (members.contains(node)) {
                        kept.add(node);
                    }
                }
                local.add(kept);
            }
            this.orderRows(local, outgoing, incoming);
            int bottom = top;
            int deepestLayer = 0;
            for (List<Node> layer : local) {
                deepestLayer = Math.max(deepestLayer, this.layerHeight(layer));
            }
            for (List<Node> layer : local) {
                int offset = top + (deepestLayer - this.layerHeight(layer)) / 2;
                int y = offset;
                for (Node node : layer) {
                    node.y = y;
                    y += node.height + ROW_GAP;
                }
                bottom = Math.max(bottom, y);
            }
            top = bottom + GROUP_GAP;
        }
        for (Node node : this.nodes.values()) {
            node.x = column.getOrDefault(node, 0) * (WIDTH + COLUMN_GAP);
        }

        if (saved == null || saved.isEmpty()) {
            return;
        }
        List<Node> free = new ArrayList<>();
        for (Node node : this.nodes.values()) {
            int[] position = saved.get(node.id);
            if (position != null && position.length == 2) {
                node.x = position[0];
                node.y = position[1];
                node.placed = true;
            } else {
                free.add(node);
            }
        }
        // Free nodes sit to the right of a placed parent when they have one, then avoid overlaps.
        for (Edge edge : this.edges) {
            if (edge.to() != null && free.contains(edge.to()) && edge.from().placed && !edge.to().placed) {
                edge.to().x = edge.from().x + WIDTH + COLUMN_GAP;
                edge.to().y = edge.from().y;
                edge.to().placed = true;
            }
        }
        for (Node node : free) {
            node.placed = true;
            int guard = 0;
            while (this.overlaps(node) && guard++ < 500) {
                node.y += ROW_GAP;
            }
        }
    }

    /**
     * Column of every node: the longest path from a root, counted left to right. Loop edges (links
     * back to a node still being visited) are found first and ignored, so a loop is drawn as a link
     * going back rather than pushing the whole script ever further right. Iterative, so a script of
     * thousands of nodes cannot overflow the stack.
     */
    private Map<Node, Integer> assignColumns(String startId, Map<String, List<Edge>> outgoing,
                                             Map<String, List<Edge>> incoming) {
        List<Node> roots = new ArrayList<>();
        if (startId != null && this.nodes.containsKey(startId)) {
            roots.add(this.nodes.get(startId));
        }
        for (Node node : this.nodes.values()) {
            if (!incoming.containsKey(node.id) && !roots.contains(node)) {
                roots.add(node);
            }
        }
        roots.addAll(this.nodes.values());

        Set<Edge> loops = new HashSet<>();
        Map<Node, Integer> state = new HashMap<>();
        List<Node> postOrder = new ArrayList<>();
        for (Node root : roots) {
            if (state.containsKey(root)) {
                continue;
            }
            ArrayDeque<Node> stack = new ArrayDeque<>();
            ArrayDeque<Integer> cursor = new ArrayDeque<>();
            stack.push(root);
            cursor.push(0);
            state.put(root, 1);
            while (!stack.isEmpty()) {
                Node node = stack.peek();
                int index = cursor.pop();
                List<Edge> links = outgoing.getOrDefault(node.id, List.of());
                if (index >= links.size()) {
                    stack.pop();
                    state.put(node, 2);
                    postOrder.add(node);
                    continue;
                }
                cursor.push(index + 1);
                Edge edge = links.get(index);
                Node child = edge.to();
                Integer childState = state.get(child);
                if (childState == null) {
                    state.put(child, 1);
                    stack.push(child);
                    cursor.push(0);
                } else if (childState == 1) {
                    loops.add(edge);
                }
            }
        }

        Map<Node, Integer> column = new HashMap<>();
        for (Node node : this.nodes.values()) {
            column.put(node, 0);
        }
        for (int i = postOrder.size() - 1; i >= 0; i--) {
            Node node = postOrder.get(i);
            int next = column.get(node) + 1;
            for (Edge edge : outgoing.getOrDefault(node.id, List.of())) {
                if (!loops.contains(edge) && edge.to() != node && column.get(edge.to()) < next) {
                    column.put(edge.to(), next);
                }
            }
        }
        return column;
    }

    /** Weakly connected node groups, in the order the nodes were declared. */
    private List<List<Node>> components() {
        Map<String, List<String>> neighbours = new HashMap<>();
        for (Edge edge : this.edges) {
            String from = edge.from().id;
            String to = edge.to() == null ? null : edge.to().id;
            if (to == null) {
                continue;
            }
            neighbours.computeIfAbsent(from, key -> new ArrayList<>()).add(to);
            neighbours.computeIfAbsent(to, key -> new ArrayList<>()).add(from);
        }
        Set<Node> seen = new HashSet<>();
        List<List<Node>> components = new ArrayList<>();
        for (Node node : this.nodes.values()) {
            if (!seen.add(node)) {
                continue;
            }
            List<Node> group = new ArrayList<>();
            ArrayDeque<Node> queue = new ArrayDeque<>();
            queue.add(node);
            while (!queue.isEmpty()) {
                Node current = queue.poll();
                group.add(current);
                for (String id : neighbours.getOrDefault(current.id, List.of())) {
                    Node next = this.nodes.get(id);
                    if (next != null && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            components.add(group);
        }
        return components;
    }

    /**
     * Barycenter ordering: repeatedly place every node near the average row of the nodes it links
     * to, alternating direction, which is the standard way to remove edge crossings.
     */
    private void orderRows(List<List<Node>> layers, Map<String, List<Edge>> outgoing,
                           Map<String, List<Edge>> incoming) {
        for (int sweep = 0; sweep < 4; sweep++) {
            boolean downward = sweep % 2 == 0;
            for (int i = 0; i < layers.size(); i++) {
                List<Node> layer = layers.get(downward ? i : layers.size() - 1 - i);
                if (layer.size() < 2) {
                    continue;
                }
                Map<Node, Double> position = this.rows(layers);
                Map<String, List<Edge>> links = downward ? incoming : outgoing;
                layer.sort((a, b) -> Double.compare(this.barycenter(a, links, position, downward),
                        this.barycenter(b, links, position, downward)));
            }
        }
    }

    private Map<Node, Double> rows(List<List<Node>> layers) {
        Map<Node, Double> position = new HashMap<>();
        for (List<Node> layer : layers) {
            for (int i = 0; i < layer.size(); i++) {
                position.put(layer.get(i), (double) i);
            }
        }
        return position;
    }

    private double barycenter(Node node, Map<String, List<Edge>> links, Map<Node, Double> position,
                              boolean downward) {
        List<Edge> edges = links.get(node.id);
        if (edges == null || edges.isEmpty()) {
            Double own = position.get(node);
            return own == null ? 0.0 : own;
        }
        double total = 0.0;
        int count = 0;
        for (Edge edge : edges) {
            Node other = downward ? edge.from() : edge.to();
            Double value = position.get(other);
            if (value != null) {
                total += value;
                count++;
            }
        }
        if (count == 0) {
            Double own = position.get(node);
            return own == null ? 0.0 : own;
        }
        return total / count;
    }

    /** Total height of one column of nodes. */
    private int layerHeight(List<Node> layer) {
        int height = 0;
        for (Node node : layer) {
            height += node.height + ROW_GAP;
        }
        return Math.max(0, height - ROW_GAP);
    }

    private boolean overlaps(Node node) {
        for (Node other : this.nodes.values()) {
            if (other != node && other.placed
                    && node.x < other.x + WIDTH + 8 && other.x < node.x + WIDTH + 8
                    && node.y < other.y + other.height + 8 && other.y < node.y + node.height + 8) {
                return true;
            }
        }
        return false;
    }
}

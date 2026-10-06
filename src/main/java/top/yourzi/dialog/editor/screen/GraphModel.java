package top.yourzi.dialog.editor.screen;

import top.yourzi.dialog.editor.TextCodec;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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

        boolean contains(double worldX, double worldY) {
            return worldX >= this.x && worldX < this.x + WIDTH && worldY >= this.y && worldY < this.y + this.height;
        }
    }

    record Edge(Node from, int port, Node to, String missing, Kind kind, int color) {
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

    /**
     * Saved positions first. Everything else goes into columns by distance from the start (or from
     * each unvisited node, in file order) with rows sorted by the average row of their parents. A node
     * placed next to hand-arranged ones is then nudged down until it overlaps nothing.
     */
    private void layout(String startId, Map<String, int[]> saved) {
        Map<String, List<Node>> outgoing = new HashMap<>();
        for (Edge edge : this.edges) {
            if (edge.to() != null) {
                outgoing.computeIfAbsent(edge.from().id, key -> new ArrayList<>()).add(edge.to());
            }
        }
        List<String> roots = new ArrayList<>();
        if (startId != null) {
            roots.add(startId);
        }
        roots.addAll(this.nodes.keySet());
        Map<Node, Integer> column = new HashMap<>();
        Map<Node, Integer> group = new HashMap<>();
        int groups = 0;
        for (String rootId : roots) {
            Node root = this.nodes.get(rootId);
            if (root == null || column.containsKey(root)) {
                continue;
            }
            ArrayDeque<Node> queue = new ArrayDeque<>();
            queue.add(root);
            column.put(root, 0);
            group.put(root, groups);
            while (!queue.isEmpty()) {
                Node node = queue.poll();
                for (Node child : outgoing.getOrDefault(node.id, List.of())) {
                    if (!column.containsKey(child)) {
                        column.put(child, column.get(node) + 1);
                        group.put(child, groups);
                        queue.add(child);
                    }
                }
            }
            groups++;
        }

        Map<Node, Double> hint = new HashMap<>();
        int top = 0;
        for (int g = 0; g < groups; g++) {
            TreeMap<Integer, List<Node>> columns = new TreeMap<>();
            for (Node node : this.nodes.values()) {
                if (group.get(node) == g) {
                    columns.computeIfAbsent(column.get(node), key -> new ArrayList<>()).add(node);
                }
            }
            int groupBottom = top;
            for (Map.Entry<Integer, List<Node>> col : columns.entrySet()) {
                List<Node> list = col.getValue();
                list.sort((a, b) -> Double.compare(hint.getOrDefault(a, Double.MAX_VALUE), hint.getOrDefault(b, Double.MAX_VALUE)));
                int y = top;
                for (Node node : list) {
                    node.x = col.getKey() * (WIDTH + COLUMN_GAP);
                    node.y = y;
                    for (Node child : outgoing.getOrDefault(node.id, List.of())) {
                        hint.merge(child, (double) y, (a, b) -> (a + b) / 2.0);
                    }
                    y += node.height + ROW_GAP;
                }
                groupBottom = Math.max(groupBottom, y);
            }
            top = groupBottom + GROUP_GAP;
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

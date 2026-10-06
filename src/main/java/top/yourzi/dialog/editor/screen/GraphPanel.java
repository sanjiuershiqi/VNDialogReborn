package top.yourzi.dialog.editor.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import top.yourzi.dialog.editor.TextCodec;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiNode;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Relationship graph: every node of the file and how they connect, plus the other dialogue files it
 * starts through {@code dialog show} commands.
 *
 * <p>The layout is computed, not stored: columns are the distance from the start node, so reading
 * left to right follows the story; rows inside a column follow their parents to keep lines short.
 * Nothing the writer arranges here can drift out of sync with the file, which keeps the graph a
 * reliable overview for large scripts. Click a node to edit it, drag the background to pan and use
 * the wheel to zoom.
 */
public final class GraphPanel extends EditorPanel {
    private static final int NODE_W = 150;
    private static final int NODE_H = 40;
    private static final int COLUMN_GAP = 70;
    private static final int ROW_GAP = 14;
    private static final int GROUP_GAP = 36;
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 2.0f;
    private static final String FILE = "file:";

    /** One drawn box: a node of this file, or a stub for another dialogue file. */
    private record Box(String id, DialogEntry entry, int x, int y) {
        boolean external() {
            return this.entry == null;
        }
    }

    /** One connection; {@code kind} 0 = continues, 1 = choice, 2 = starts another file. */
    private record Edge(String from, String to, int kind, int color, boolean missing) {
    }

    private final EditorContext context;
    private final Canvas canvas = new Canvas();
    private final Map<String, Box> boxes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Map<String, Integer> fileSizes = new HashMap<>();
    private Consumer<String> openDocument;
    private Supplier<List<DialogSequence>> allFiles;
    private boolean filesOverview;
    private final Button fileModeButton;
    private final Button overviewModeButton;
    private float zoom = 1.0f;
    private double panX;
    private double panY;
    private boolean fitPending = true;
    private String shownSequence;
    private int contentWidth;
    private int contentHeight;

    public GraphPanel(EditorContext context) {
        super(null);
        this.context = context;
        Row toolbar = this.createToolbar(Theme.ROW);
        this.fileModeButton = Button.of(Theme.tr("graph.mode_nodes"), () -> this.setFilesOverview(false))
                .tone(Button.Tone.TAB).fit();
        this.overviewModeButton = Button.of(Theme.tr("graph.mode_files"), () -> this.setFilesOverview(true))
                .tone(Button.Tone.TAB).fit();
        toolbar.add(this.fileModeButton);
        toolbar.add(this.overviewModeButton);
        toolbar.add(Nodes.fill());
        toolbar.add(Button.of(Theme.tr("graph.fit"), this::fit).tone(Button.Tone.GHOST).fit());
        toolbar.add(Button.of(Theme.tr("graph.actual"), () -> this.zoomAround(1.0f, this.canvas.x() + this.canvas.width() / 2.0,
                this.canvas.y() + this.canvas.height() / 2.0)).tone(Button.Tone.GHOST).fit());
        toolbar.add(Button.of(Theme.tr("graph.focus"), this::focusSelected).tone(Button.Tone.GHOST).fit());
        this.add(this.canvas);
        this.fileModeButton.selected(true);
    }

    /** Opens another dialogue file when its box is clicked. */
    public void setOpenDocument(Consumer<String> openDocument) {
        this.openDocument = openDocument;
    }

    /** Reads every dialogue file in the editor folder, for the files overview. */
    public void setAllFiles(Supplier<List<DialogSequence>> allFiles) {
        this.allFiles = allFiles;
    }

    private void setFilesOverview(boolean filesOverview) {
        this.filesOverview = filesOverview;
        this.fileModeButton.selected(!filesOverview);
        this.overviewModeButton.selected(filesOverview);
        this.fitPending = true;
        this.refresh();
    }

    @Override
    protected void onLayout() {
        super.onLayout();
        this.canvas.setBounds(this.content().x(), this.content().y(), this.content().width(), this.content().height());
    }

    // ----- model → layout -----

    public void refresh() {
        if (this.filesOverview) {
            this.refreshFiles();
            return;
        }
        DialogSequence sequence = this.context.sequence();
        String sequenceId = sequence == null ? null : sequence.getId();
        if (!java.util.Objects.equals(sequenceId, this.shownSequence)) {
            this.shownSequence = sequenceId;
            this.fitPending = true;
        }
        this.boxes.clear();
        this.edges.clear();
        List<DialogEntry> entries = NodeGraph.entries(sequence);
        if (entries.isEmpty()) {
            this.contentWidth = 0;
            this.contentHeight = 0;
            return;
        }
        Map<String, List<String>> next = new HashMap<>();
        Map<String, Integer> branchColors = new HashMap<>();
        for (DialogEntry entry : entries) {
            List<String> targets = new ArrayList<>();
            if (NodeGraph.hasOptions(entry)) {
                for (DialogOption option : entry.getOptions()) {
                    String target = option == null ? null : option.getTargetId();
                    if (target != null && !target.isBlank()) {
                        targets.add(target);
                        int color = branchColors.computeIfAbsent(target, key -> branchColor(branchColors.size()));
                        this.edges.add(new Edge(entry.getId(), target, 1, color, NodeGraph.byId(sequence, target) == null));
                    }
                }
            } else if (!entry.isEndDialog()) {
                DialogEntry following = NodeGraph.implicitNext(sequence, entry);
                boolean explicit = entry.getNextId() != null && !entry.getNextId().isBlank();
                if (following != null) {
                    targets.add(following.getId());
                    this.edges.add(new Edge(entry.getId(), following.getId(), 0, Theme.TEXT_MUTED, false));
                } else if (explicit) {
                    this.edges.add(new Edge(entry.getId(), entry.getNextId(), 0, Theme.DANGER, true));
                }
            }
            for (String file : externalTargets(entry)) {
                this.edges.add(new Edge(entry.getId(), FILE + file, 2, Theme.CYAN, false));
            }
            next.put(entry.getId(), targets);
        }
        this.layout(sequence, entries, next);
    }

    /**
     * Files overview: one box per dialogue file and an edge wherever a node starts another file, so a
     * story split across many files can be navigated without opening each one. Files that nothing
     * starts are the roots of the first column.
     */
    private void refreshFiles() {
        this.boxes.clear();
        this.edges.clear();
        List<DialogSequence> files = this.allFiles == null ? List.of() : this.allFiles.get();
        Map<String, List<String>> links = new LinkedHashMap<>();
        java.util.Set<String> started = new java.util.HashSet<>();
        for (DialogSequence file : files) {
            List<String> targets = new ArrayList<>();
            for (DialogEntry entry : NodeGraph.entries(file)) {
                for (String target : externalTargets(entry)) {
                    if (!targets.contains(target)) {
                        targets.add(target);
                        started.add(target);
                    }
                }
            }
            links.put(file.getId(), targets);
        }
        List<String> roots = new ArrayList<>();
        for (String id : links.keySet()) {
            if (!started.contains(id)) {
                roots.add(id);
            }
        }
        roots.addAll(links.keySet());

        Map<String, Integer> column = new HashMap<>();
        Map<Integer, Integer> rowsPerColumn = new HashMap<>();
        Map<String, Integer> row = new HashMap<>();
        for (String root : roots) {
            if (column.containsKey(root)) {
                continue;
            }
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(root);
            column.put(root, 0);
            while (!queue.isEmpty()) {
                String id = queue.poll();
                int col = column.get(id);
                row.put(id, rowsPerColumn.merge(col, 1, Integer::sum) - 1);
                for (String target : links.getOrDefault(id, List.of())) {
                    if (!column.containsKey(target)) {
                        column.put(target, col + 1);
                        queue.add(target);
                    }
                }
            }
        }
        int right = 0;
        int bottom = 0;
        for (Map.Entry<String, Integer> placed : column.entrySet()) {
            int x = placed.getValue() * (NODE_W + COLUMN_GAP);
            int y = row.get(placed.getKey()) * (NODE_H + ROW_GAP);
            this.boxes.put(FILE + placed.getKey(), new Box(FILE + placed.getKey(), null, x, y));
            right = Math.max(right, x + NODE_W);
            bottom = Math.max(bottom, y + NODE_H);
        }
        for (Map.Entry<String, List<String>> link : links.entrySet()) {
            for (String target : link.getValue()) {
                boolean missing = !links.containsKey(target);
                this.edges.add(new Edge(FILE + link.getKey(), FILE + target, 2, missing ? Theme.DANGER : Theme.CYAN, missing));
            }
        }
        this.fileSizes.clear();
        for (DialogSequence file : files) {
            this.fileSizes.put(file.getId(), NodeGraph.entries(file).size());
        }
        this.contentWidth = right;
        this.contentHeight = bottom;
    }

    /** Dialogue ids started by {@code dialog ... show ... <id>} commands on the node or its choices. */
    private static List<String> externalTargets(DialogEntry entry) {
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
            if (tokens.length >= 3 && tokens[0].equalsIgnoreCase("dialog")) {
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
        }
        return files;
    }

    /**
     * Columns by breadth-first distance from the start; nodes that cannot be reached form their own
     * groups below, in file order. Inside a column, nodes sit near the average row of their parents.
     */
    private void layout(DialogSequence sequence, List<DialogEntry> entries, Map<String, List<String>> next) {
        Map<String, Integer> column = new HashMap<>();
        Map<String, Integer> group = new HashMap<>();
        List<String> roots = new ArrayList<>();
        DialogEntry start = sequence.getFirstEntry();
        if (start != null) {
            roots.add(start.getId());
        }
        for (DialogEntry entry : entries) {
            roots.add(entry.getId());
        }
        int groups = 0;
        for (String root : roots) {
            if (column.containsKey(root)) {
                continue;
            }
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(root);
            column.put(root, 0);
            group.put(root, groups);
            while (!queue.isEmpty()) {
                String id = queue.poll();
                for (String target : next.getOrDefault(id, List.of())) {
                    if (!column.containsKey(target) && next.containsKey(target)) {
                        column.put(target, column.get(id) + 1);
                        group.put(target, groups);
                        queue.add(target);
                    }
                }
            }
            groups++;
        }

        Map<String, Double> rowHint = new HashMap<>();
        int top = 0;
        int maxRight = 0;
        for (int g = 0; g < groups; g++) {
            Map<Integer, List<String>> columns = new java.util.TreeMap<>();
            for (DialogEntry entry : entries) {
                if (group.get(entry.getId()) == g) {
                    columns.computeIfAbsent(column.get(entry.getId()), key -> new ArrayList<>()).add(entry.getId());
                }
            }
            int groupHeight = 0;
            for (Map.Entry<Integer, List<String>> col : columns.entrySet()) {
                List<String> ids = col.getValue();
                ids.sort((a, b) -> Double.compare(rowHint.getOrDefault(a, 1e9), rowHint.getOrDefault(b, 1e9)));
                for (int i = 0; i < ids.size(); i++) {
                    String id = ids.get(i);
                    int x = col.getKey() * (NODE_W + COLUMN_GAP);
                    int y = top + i * (NODE_H + ROW_GAP);
                    this.boxes.put(id, new Box(id, NodeGraph.byId(sequence, id), x, y));
                    maxRight = Math.max(maxRight, x + NODE_W);
                    groupHeight = Math.max(groupHeight, (i + 1) * (NODE_H + ROW_GAP));
                    for (String child : next.getOrDefault(id, List.of())) {
                        rowHint.merge(child, (double) i, (a, b) -> (a + b) / 2.0);
                    }
                }
            }
            top += groupHeight + GROUP_GAP;
        }

        // Other files go into one column to the right of everything.
        int stubX = maxRight + COLUMN_GAP;
        int stubY = 0;
        for (Edge edge : this.edges) {
            if (edge.kind() == 2 && !this.boxes.containsKey(edge.to())) {
                this.boxes.put(edge.to(), new Box(edge.to(), null, stubX, stubY));
                stubY += NODE_H + ROW_GAP;
            }
        }
        this.contentWidth = stubY > 0 ? stubX + NODE_W : maxRight;
        this.contentHeight = Math.max(top - GROUP_GAP, stubY);
    }

    private static int branchColor(int index) {
        float hue = (0.58f + index * 0.61803398875f) % 1.0f;
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.45f, 0.95f) & 0xFFFFFF);
    }

    // ----- view -----

    private void fit() {
        if (this.contentWidth <= 0 || this.canvas.width() <= 0) {
            return;
        }
        float zx = (this.canvas.width() - 40f) / this.contentWidth;
        float zy = (this.canvas.height() - 40f) / Math.max(1, this.contentHeight);
        this.zoom = Mth.clamp(Math.min(zx, zy), MIN_ZOOM, 1.0f);
        this.panX = (this.canvas.width() - this.contentWidth * this.zoom) / 2.0;
        this.panY = Math.max(20, (this.canvas.height() - this.contentHeight * this.zoom) / 2.0);
    }

    private void zoomAround(float target, double screenX, double screenY) {
        float clamped = Mth.clamp(target, MIN_ZOOM, MAX_ZOOM);
        double worldX = (screenX - this.canvas.x() - this.panX) / this.zoom;
        double worldY = (screenY - this.canvas.y() - this.panY) / this.zoom;
        this.zoom = clamped;
        this.panX = screenX - this.canvas.x() - worldX * this.zoom;
        this.panY = screenY - this.canvas.y() - worldY * this.zoom;
    }

    /** Pans so the selected node is in view; called when selection changes elsewhere. */
    public void focusSelected() {
        Box box = this.boxes.get(this.context.selectedId());
        if (box == null || this.canvas.width() <= 0) {
            return;
        }
        double left = box.x() * this.zoom + this.panX;
        double topY = box.y() * this.zoom + this.panY;
        double right = left + NODE_W * this.zoom;
        double bottom = topY + NODE_H * this.zoom;
        if (left < 20 || right > this.canvas.width() - 20) {
            this.panX = this.canvas.width() / 2.0 - (box.x() + NODE_W / 2.0) * this.zoom;
        }
        if (topY < 20 || bottom > this.canvas.height() - 20) {
            this.panY = this.canvas.height() / 2.0 - (box.y() + NODE_H / 2.0) * this.zoom;
        }
    }

    /** The drawing surface; owns pan and zoom input. */
    private final class Canvas extends UiNode {
        private boolean panning;
        private double lastX;
        private double lastY;

        private Box boxAt(double mouseX, double mouseY) {
            double worldX = (mouseX - this.x() - GraphPanel.this.panX) / GraphPanel.this.zoom;
            double worldY = (mouseY - this.y() - GraphPanel.this.panY) / GraphPanel.this.zoom;
            for (Box box : GraphPanel.this.boxes.values()) {
                if (worldX >= box.x() && worldX < box.x() + NODE_W && worldY >= box.y() && worldY < box.y() + NODE_H) {
                    return box;
                }
            }
            return null;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            this.host().focus(null);
            Box box = this.boxAt(mouseX, mouseY);
            if (box != null && button == 0) {
                if (box.external()) {
                    if (GraphPanel.this.openDocument != null) {
                        GraphPanel.this.openDocument.accept(box.id().substring(FILE.length()));
                    }
                    if (GraphPanel.this.filesOverview) {
                        GraphPanel.this.setFilesOverview(false);
                    }
                } else {
                    GraphPanel.this.context.select(box.id());
                }
                return true;
            }
            this.panning = true;
            this.lastX = mouseX;
            this.lastY = mouseY;
            this.host().claimPointer(this);
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (this.panning) {
                GraphPanel.this.panX += mouseX - this.lastX;
                GraphPanel.this.panY += mouseY - this.lastY;
                this.lastX = mouseX;
                this.lastY = mouseY;
            }
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            this.panning = false;
            return true;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            GraphPanel.this.zoomAround(GraphPanel.this.zoom * (scrollY > 0 ? 1.15f : 1 / 1.15f), mouseX, mouseY);
            return true;
        }

        @Override
        protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            GraphPanel panel = GraphPanel.this;
            graphics.fill(this.x(), this.y(), this.right(), this.bottom(), Theme.BG);
            if (panel.fitPending && this.width() > 0 && panel.contentWidth > 0) {
                panel.fitPending = false;
                panel.fit();
            }
            if (panel.boxes.isEmpty()) {
                Theme.centered(graphics, Theme.tr("graph.empty").getString(), this.x() + this.width() / 2,
                        this.y() + this.height() / 2 - 4, Theme.TEXT_MUTED);
                return;
            }
            Box hovered = this.isHovered() && !this.panning ? this.boxAt(mouseX, mouseY) : null;
            String selected = panel.context.selectedId();
            graphics.enableScissor(this.x(), this.y(), this.right(), this.bottom());
            graphics.pose().pushPose();
            graphics.pose().translate((float) (this.x() + panel.panX), (float) (this.y() + panel.panY), 0.0f);
            graphics.pose().scale(panel.zoom, panel.zoom, 1.0f);
            int line = Math.max(1, Math.round(1.0f / panel.zoom));
            for (Edge edge : panel.edges) {
                boolean related = edge.from().equals(selected) || edge.to().equals(selected);
                this.drawEdge(graphics, edge, related, selected != null && !related, line);
            }
            for (Box box : panel.boxes.values()) {
                this.drawBox(graphics, box, box.id().equals(selected), box == hovered);
            }
            graphics.pose().popPose();
            graphics.disableScissor();
            String hint = Theme.tr("graph.hint").getString() + "   " + Math.round(panel.zoom * 100) + "%";
            Theme.text(graphics, hint, this.x() + 8, this.bottom() - 12, Theme.TEXT_MUTED);
        }

        private void drawBox(GuiGraphics graphics, Box box, boolean selected, boolean hovered) {
            int x = box.x();
            int y = box.y();
            if (box.external()) {
                String file = box.id().substring(FILE.length());
                boolean current = GraphPanel.this.context.document() != null
                        && file.equals(GraphPanel.this.context.document().id());
                Integer size = GraphPanel.this.fileSizes.get(file);
                boolean exists = !GraphPanel.this.filesOverview || size != null;
                graphics.fill(x, y, x + NODE_W, y + NODE_H, current ? Theme.SELECTED : hovered ? Theme.HOVER : Theme.SURFACE);
                Theme.border(graphics, x, y, NODE_W, NODE_H, current ? Theme.ACCENT : exists ? Theme.CYAN : Theme.DANGER);
                String caption = GraphPanel.this.filesOverview
                        ? (size == null ? Theme.tr("graph.missing_file").getString() : Theme.tr("status.nodes", size).getString())
                        : Theme.tr("graph.other_file").getString();
                Theme.textIn(graphics, file, x + 8, y + 5, NODE_W - 16, 10, exists ? Theme.TEXT : Theme.DANGER);
                Theme.textIn(graphics, caption, x + 8, y + 21, NODE_W - 16, 10, Theme.TEXT_MUTED);
                return;
            }
            DialogEntry entry = box.entry();
            graphics.fill(x, y, x + NODE_W, y + NODE_H, selected ? Theme.SELECTED : hovered ? Theme.HOVER : Theme.RAISED);
            graphics.fill(x, y, x + 3, y + NODE_H, OutlinePanel.kindColor(entry));
            if (selected) {
                Theme.border(graphics, x, y, NODE_W, NODE_H, Theme.ACCENT);
            }
            boolean start = NodeGraph.isStart(GraphPanel.this.context.sequence(), entry);
            String speaker = TextCodec.preview(entry.getSpeaker());
            String head = (start ? "▶ " : "") + box.id();
            Theme.textIn(graphics, head, x + 8, y + 5, NODE_W - 16, 10, start ? Theme.SUCCESS : Theme.TEXT);
            String text = TextCodec.preview(entry.getText());
            String body = speaker.isEmpty() ? text : speaker + "：" + text;
            Theme.textIn(graphics, body.isEmpty() ? Theme.tr("outline.no_text").getString() : body, x + 8, y + 20,
                    NODE_W - 16, 12, Theme.TEXT_MUTED);
            if (entry.isEndDialog()) {
                Theme.text(graphics, "■", x + NODE_W - 12, y + 5, Theme.DANGER);
            }
        }

        /** Right side of the source to the left side of the target, routed with right angles. */
        private void drawEdge(GuiGraphics graphics, Edge edge, boolean related, boolean faded, int line) {
            Box from = GraphPanel.this.boxes.get(edge.from());
            Box to = GraphPanel.this.boxes.get(edge.to());
            if (from == null) {
                return;
            }
            int color = edge.color();
            if (faded) {
                color = (color & 0x00FFFFFF) | 0x50000000;
            } else if (related && edge.kind() == 0) {
                color = Theme.TEXT_DIM;
            }
            int startX = from.x() + NODE_W;
            int startY = from.y() + NODE_H / 2 + (edge.kind() == 1 ? 6 : 0);
            if (to == null) {
                // Target missing: a short red stub that ends in a cross.
                hLine(graphics, startX, startX + 24, startY, line, Theme.DANGER);
                Theme.text(graphics, "✕", startX + 26, startY - 4, Theme.DANGER);
                return;
            }
            int endX = to.x();
            int endY = to.y() + NODE_H / 2;
            if (endX > startX) {
                int midX = startX + Math.max(12, Math.min(COLUMN_GAP / 2, (endX - startX) / 2));
                hLine(graphics, startX, midX, startY, line, color);
                vLine(graphics, midX, startY, endY, line, color);
                hLine(graphics, midX, endX, endY, line, color);
            } else {
                // Backward jump (a loop): leave right, run below both boxes, come back in from the left.
                int laneY = Math.max(from.y(), to.y()) + NODE_H + ROW_GAP / 2;
                int outX = startX + 10;
                int inX = endX - 10;
                hLine(graphics, startX, outX, startY, line, color);
                vLine(graphics, outX, startY, laneY, line, color);
                hLine(graphics, inX, outX, laneY, line, color);
                vLine(graphics, inX, endY, laneY, line, color);
                hLine(graphics, inX, endX, endY, line, color);
            }
            // Arrow head pointing into the target.
            for (int i = 0; i < 4; i++) {
                graphics.fill(endX - 1 - i, endY - i, endX - i, endY + i + 1, color);
            }
        }

        private void hLine(GuiGraphics graphics, int x1, int x2, int y, int width, int color) {
            graphics.fill(Math.min(x1, x2), y, Math.max(x1, x2) + 1, y + width, color);
        }

        private void vLine(GuiGraphics graphics, int x, int y1, int y2, int width, int color) {
            graphics.fill(x, Math.min(y1, y2), x + width, Math.max(y1, y2) + 1, color);
        }
    }
}

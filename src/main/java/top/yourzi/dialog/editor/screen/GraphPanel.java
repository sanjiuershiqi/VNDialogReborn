package top.yourzi.dialog.editor.screen;

import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.yourzi.dialog.editor.LayoutStore;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.ContextMenu;
import top.yourzi.dialog.editor.ui.Lines;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiNode;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Relationship graph for large scripts.
 *
 * <ul>
 *     <li>drag a node to arrange it; the arrangement is saved per file, outside the dialogue JSON;</li>
 *     <li>drag from a port (the dots on a node's right edge) onto another node to link it, or onto
 *     empty space to create a new linked node there;</li>
 *     <li>double-click empty space to add a node, right-click nodes and ports for actions;</li>
 *     <li>drag the background to pan, wheel to zoom around the cursor, minimap to jump.</li>
 * </ul>
 * The "all files" mode shows dialogue files and the {@code dialog show} links between them.
 */
public final class GraphPanel extends EditorPanel {
    private static final float MIN_ZOOM = 0.2f;
    private static final float MAX_ZOOM = 2.0f;
    private static final int PORT_HIT = 7;
    private static final int GRID = 40;
    private static final int MAP_W = 150;
    private static final int MAP_H = 96;
    private static final long DOUBLE_CLICK_MS = 350L;

    private final EditorContext context;
    private final Canvas canvas = new Canvas();
    private final Button nodesMode;
    private final Button filesMode;
    private GraphModel model = new GraphModel();
    private Map<String, int[]> positions = new java.util.LinkedHashMap<>();
    private String layoutKey;
    private Consumer<String> openDocument;
    private Supplier<List<DialogSequence>> allFiles;
    private FlowPanel.Commands commands;
    private boolean filesOverview;
    private float zoom = 1.0f;
    private double panX;
    private double panY;
    private boolean fitPending = true;
    private boolean routesDirty = true;

    public GraphPanel(EditorContext context) {
        super(null);
        this.context = context;
        Row toolbar = this.createToolbar(Theme.ROW);
        this.nodesMode = Button.of(Theme.tr("graph.mode_nodes"), () -> this.setFilesOverview(false)).tone(Button.Tone.TAB).fit();
        this.filesMode = Button.of(Theme.tr("graph.mode_files"), () -> this.setFilesOverview(true)).tone(Button.Tone.TAB).fit();
        toolbar.add(this.nodesMode);
        toolbar.add(this.filesMode);
        toolbar.add(Nodes.fill());
        toolbar.add(Button.of(Theme.tr("graph.arrange"), this::autoArrange).tone(Button.Tone.GHOST).fit()
                .withTooltip(Theme.tr("graph.arrange_tip")));
        toolbar.add(Button.of(Theme.tr("graph.fit"), this::fit).tone(Button.Tone.GHOST).fit());
        toolbar.add(Button.of(Theme.tr("graph.focus"), this::focusSelected).tone(Button.Tone.GHOST).fit());
        this.nodesMode.selected(true);
        this.add(this.canvas);
    }

    public void setOpenDocument(Consumer<String> openDocument) {
        this.openDocument = openDocument;
    }

    public void setAllFiles(Supplier<List<DialogSequence>> allFiles) {
        this.allFiles = allFiles;
    }

    public void setCommands(FlowPanel.Commands commands) {
        this.commands = commands;
    }

    private void setFilesOverview(boolean filesOverview) {
        this.filesOverview = filesOverview;
        this.nodesMode.selected(!filesOverview);
        this.filesMode.selected(filesOverview);
        this.refresh();
    }

    @Override
    protected void onLayout() {
        super.onLayout();
        this.canvas.setBounds(this.content().x(), this.content().y(), this.content().width(), this.content().height());
    }

    // ----- model -----

    public void refresh() {
        String key;
        if (this.filesOverview) {
            key = LayoutStore.FILES_KEY;
        } else {
            key = this.context.document() == null ? null : this.context.document().id();
        }
        if (!java.util.Objects.equals(key, this.layoutKey)) {
            this.layoutKey = key;
            this.positions = LayoutStore.load(key);
            this.fitPending = true;
        }
        if (this.filesOverview) {
            String active = this.context.document() == null ? null : this.context.document().id();
            this.model = GraphModel.ofFiles(this.allFiles == null ? List.of() : this.allFiles.get(), active, this.positions);
        } else {
            this.model = GraphModel.ofSequence(this.context.sequence(), this.positions);
        }
        this.routesDirty = true;
    }

    /** Keeps a hand arrangement when a node is renamed. */
    public void renameNode(String oldId, String newId) {
        if (this.filesOverview || this.layoutKey == null) {
            return;
        }
        int[] position = this.positions.remove(oldId);
        if (position != null) {
            this.positions.put(newId, position);
            LayoutStore.save(this.layoutKey, this.positions);
        }
    }

    private void remember(GraphModel.Node node) {
        if (this.layoutKey == null) {
            return;
        }
        this.positions.put(node.id, new int[]{node.x, node.y});
        LayoutStore.save(this.layoutKey, this.positions);
    }

    private void autoArrange() {
        if (this.layoutKey == null) {
            return;
        }
        this.positions.clear();
        LayoutStore.save(this.layoutKey, this.positions);
        this.refresh();
        this.fit();
    }

    // ----- view -----

    private void fit() {
        if (this.model.isEmpty() || this.canvas.width() <= 0) {
            return;
        }
        int[] b = this.model.bounds();
        float zx = (this.canvas.width() - 60f) / Math.max(1, b[2] - b[0]);
        float zy = (this.canvas.height() - 60f) / Math.max(1, b[3] - b[1]);
        this.zoom = Mth.clamp(Math.min(zx, zy), MIN_ZOOM, 1.0f);
        this.panX = (this.canvas.width() - (b[2] - b[0]) * this.zoom) / 2.0 - b[0] * this.zoom;
        this.panY = (this.canvas.height() - (b[3] - b[1]) * this.zoom) / 2.0 - b[1] * this.zoom;
    }

    private void zoomAround(float target, double screenX, double screenY) {
        double worldX = this.worldX(screenX);
        double worldY = this.worldY(screenY);
        this.zoom = Mth.clamp(target, MIN_ZOOM, MAX_ZOOM);
        this.panX = screenX - this.canvas.x() - worldX * this.zoom;
        this.panY = screenY - this.canvas.y() - worldY * this.zoom;
    }

    private double worldX(double screenX) {
        return (screenX - this.canvas.x() - this.panX) / this.zoom;
    }

    private double worldY(double screenY) {
        return (screenY - this.canvas.y() - this.panY) / this.zoom;
    }

    private void centerOn(double worldX, double worldY) {
        this.panX = this.canvas.width() / 2.0 - worldX * this.zoom;
        this.panY = this.canvas.height() / 2.0 - worldY * this.zoom;
    }

    /** Pans so the selected node is visible. */
    public void focusSelected() {
        GraphModel.Node node = this.model.nodes.get(this.context.selectedId());
        if (node == null || this.canvas.width() <= 0) {
            return;
        }
        double left = node.x * this.zoom + this.panX;
        double top = node.y * this.zoom + this.panY;
        if (left < 20 || left + GraphModel.WIDTH * this.zoom > this.canvas.width() - 20
                || top < 20 || top + node.height * this.zoom > this.canvas.height() - 20) {
            this.centerOn(node.x + GraphModel.WIDTH / 2.0, node.y + node.height / 2.0);
        }
    }

    // ----- editing through the graph -----

    /** Links an output port to a target node, replacing what the port pointed at. */
    private void connect(GraphModel.Node from, int port, GraphModel.Node to) {
        DialogEntry entry = from.entry;
        if (entry == null || to.entry == null) {
            return;
        }
        if (port == GraphModel.NEXT_PORT) {
            entry.setNextId(to.id);
            entry.setEndDialog(null);
        } else if (entry.getOptions() != null && port < entry.getOptions().length && entry.getOptions()[port] != null) {
            entry.getOptions()[port].setTargetId(to.id);
        }
        this.context.touchStructure();
        this.context.status(Theme.tr("graph.linked", from.id, to.id), EditorContext.StatusKind.SUCCESS);
    }

    private void disconnect(GraphModel.Node from, int port) {
        DialogEntry entry = from.entry;
        if (entry == null) {
            return;
        }
        if (port == GraphModel.NEXT_PORT) {
            entry.setNextId(null);
            // Without an explicit jump the node would fall through to the next one; ending is what
            // "disconnect" means to a writer looking at the graph.
            if (NodeGraph.implicitNext(this.context.sequence(), entry) != null) {
                entry.setEndDialog(Boolean.TRUE);
            }
        } else if (entry.getOptions() != null && port < entry.getOptions().length && entry.getOptions()[port] != null) {
            entry.getOptions()[port].setTargetId(null);
        }
        this.context.touchStructure();
    }

    /** Creates a node at a world position, optionally linked from a port. */
    private void createAt(double worldX, double worldY, GraphModel.Node from, int port) {
        DialogSequence sequence = this.context.sequence();
        if (sequence == null) {
            return;
        }
        String base = from == null ? "node_" + (NodeGraph.entries(sequence).size() + 1) : from.id + "_next";
        DialogEntry entry = NodeGraph.create(NodeGraph.uniqueId(sequence, base));
        NodeGraph.add(sequence, entry);
        int x = (int) Math.round(worldX / 10.0) * 10;
        int y = (int) Math.round(worldY / 10.0) * 10 - GraphModel.HEAD / 2;
        this.positions.put(entry.getId(), new int[]{x, y});
        LayoutStore.save(this.layoutKey, this.positions);
        if (from != null && from.entry != null) {
            if (port == GraphModel.NEXT_PORT) {
                from.entry.setNextId(entry.getId());
                from.entry.setEndDialog(null);
            } else if (from.entry.getOptions() != null && port < from.entry.getOptions().length) {
                from.entry.getOptions()[port].setTargetId(entry.getId());
            }
        }
        this.context.select(entry.getId());
        this.context.touchStructure();
        this.context.status(Theme.tr("status.node_added", entry.getId()), EditorContext.StatusKind.SUCCESS);
    }

    private void openNodeMenu(GraphModel.Node node, int mouseX, int mouseY) {
        this.context.select(node.id);
        List<ContextMenu.Item> items = new ArrayList<>();
        DialogSequence sequence = this.context.sequence();
        items.add(ContextMenu.Item.of(Theme.tr("menu.rename"), this.commands::renameSelected));
        items.add(ContextMenu.Item.of(Theme.tr("menu.duplicate"), this.commands::duplicateSelected));
        items.add(ContextMenu.Item.of(Theme.tr("menu.set_start"), () -> {
            sequence.setStartId(node.id);
            this.context.touchStructure();
        }, !node.start));
        items.add(ContextMenu.Item.of(Theme.tr("menu.add_branch"), () -> {
            List<DialogOption> options = new ArrayList<>();
            if (node.entry.getOptions() != null) {
                options.addAll(List.of(node.entry.getOptions()));
            }
            options.add(DialogOption.builder().text(new JsonPrimitive(Theme.tr("branch.new").getString())).build());
            node.entry.setOptions(options.toArray(new DialogOption[0]));
            node.entry.setNextId(null);
            this.context.touchStructure();
        }));
        items.add(ContextMenu.Item.of(Theme.tr("menu.toggle_end"), () -> {
            node.entry.setEndDialog(node.entry.isEndDialog() ? null : Boolean.TRUE);
            this.context.touchStructure();
        }));
        items.add(ContextMenu.Item.separator());
        items.add(ContextMenu.Item.of(Theme.tr("menu.delete"), this.commands::deleteSelected));
        ContextMenu.open(this.host(), mouseX, mouseY, Component.literal(node.id), items);
    }

    private void openPortMenu(GraphModel.Node node, int port, int mouseX, int mouseY) {
        List<ContextMenu.Item> items = new ArrayList<>();
        items.add(ContextMenu.Item.of(Theme.tr("graph.disconnect"), () -> this.disconnect(node, port)));
        items.add(ContextMenu.Item.of(Theme.tr("graph.new_linked"), () -> this.createAt(
                node.portX() + GraphModel.COLUMN_GAP, node.portY(port), node, port)));
        ContextMenu.open(this.host(), mouseX, mouseY, null, items);
    }

    // ----- canvas -----

    private enum Drag {
        NONE,
        PAN,
        NODE,
        LINK,
        MINIMAP
    }

    private final class Canvas extends UiNode {
        private Drag drag = Drag.NONE;
        private GraphModel.Node dragNode;
        private int dragPort;
        private double grabX;
        private double grabY;
        private double pressX;
        private double pressY;
        private double mouseX;
        private double mouseY;
        private long lastEmptyClick;

        private GraphPanel panel() {
            return GraphPanel.this;
        }

        /** Output port under the cursor as {node, port}, or null. */
        private Object[] portAt(double worldX, double worldY) {
            float slack = PORT_HIT / Math.max(0.5f, this.panel().zoom);
            for (GraphModel.Node node : this.panel().model.nodes.values()) {
                if (node.file || Math.abs(worldX - node.portX()) > slack) {
                    continue;
                }
                if (node.hasChoices()) {
                    for (int i = 0; i < node.choices.size(); i++) {
                        if (Math.abs(worldY - node.portY(i)) <= Math.max(slack, GraphModel.CHOICE / 2.0)) {
                            return new Object[]{node, i};
                        }
                    }
                } else if (!node.entry.isEndDialog() && Math.abs(worldY - node.portY(GraphModel.NEXT_PORT)) <= slack) {
                    return new Object[]{node, GraphModel.NEXT_PORT};
                }
            }
            return null;
        }

        private boolean onMinimap(double x, double y) {
            return !this.panel().model.isEmpty() && x >= this.right() - MAP_W - 8 && x < this.right() - 8
                    && y >= this.bottom() - MAP_H - 8 && y < this.bottom() - 8;
        }

        @Override
        public Component tooltip() {
            if (this.drag != Drag.NONE) {
                return null;
            }
            GraphModel.Node node = this.panel().model.nodeAt(this.panel().worldX(this.mouseX), this.panel().worldY(this.mouseY));
            if (node == null || node.summary.isEmpty()) {
                return null;
            }
            return Component.literal(node.summary);
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            GraphPanel panel = this.panel();
            this.host().focus(this);
            this.mouseX = x;
            this.mouseY = y;
            if (this.onMinimap(x, y) && button == 0) {
                this.drag = Drag.MINIMAP;
                this.host().claimPointer(this);
                this.jumpMinimap(x, y);
                return true;
            }
            double worldX = panel.worldX(x);
            double worldY = panel.worldY(y);
            Object[] port = panel.filesOverview ? null : this.portAt(worldX, worldY);
            GraphModel.Node node = panel.model.nodeAt(worldX, worldY);
            if (button == 1) {
                if (port != null) {
                    panel.openPortMenu((GraphModel.Node) port[0], (Integer) port[1], (int) x, (int) y);
                } else if (node != null && !node.file) {
                    panel.openNodeMenu(node, (int) x, (int) y);
                }
                return true;
            }
            if (button != 0) {
                return false;
            }
            this.pressX = x;
            this.pressY = y;
            this.host().claimPointer(this);
            if (port != null) {
                this.drag = Drag.LINK;
                this.dragNode = (GraphModel.Node) port[0];
                this.dragPort = (Integer) port[1];
            } else if (node != null) {
                this.drag = Drag.NODE;
                this.dragNode = node;
                this.grabX = worldX - node.x;
                this.grabY = worldY - node.y;
            } else {
                long now = System.currentTimeMillis();
                if (!panel.filesOverview && now - this.lastEmptyClick < DOUBLE_CLICK_MS) {
                    this.drag = Drag.NONE;
                    panel.createAt(worldX, worldY, null, 0);
                    this.lastEmptyClick = 0L;
                    return true;
                }
                this.lastEmptyClick = now;
                this.drag = Drag.PAN;
                this.grabX = x;
                this.grabY = y;
            }
            return true;
        }

        @Override
        public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
            GraphPanel panel = this.panel();
            this.mouseX = x;
            this.mouseY = y;
            switch (this.drag) {
                case PAN -> {
                    panel.panX += x - this.grabX;
                    panel.panY += y - this.grabY;
                    this.grabX = x;
                    this.grabY = y;
                }
                case NODE -> {
                    this.dragNode.x = (int) Math.round((panel.worldX(x) - this.grabX) / 10.0) * 10;
                    this.dragNode.y = (int) Math.round((panel.worldY(y) - this.grabY) / 10.0) * 10;
                    panel.routesDirty = true;
                }
                case MINIMAP -> this.jumpMinimap(x, y);
                default -> {
                }
            }
            return true;
        }

        @Override
        public boolean mouseReleased(double x, double y, int button) {
            GraphPanel panel = this.panel();
            Drag finished = this.drag;
            this.drag = Drag.NONE;
            boolean moved = Math.abs(x - this.pressX) + Math.abs(y - this.pressY) > 3;
            if (finished == Drag.NODE && this.dragNode != null) {
                if (moved) {
                    panel.remember(this.dragNode);
                } else if (this.dragNode.file) {
                    String id = this.dragNode.id.startsWith("file:") ? this.dragNode.id.substring(5) : this.dragNode.id;
                    if (panel.openDocument != null) {
                        panel.openDocument.accept(id);
                    }
                    if (panel.filesOverview) {
                        panel.setFilesOverview(false);
                    }
                } else {
                    panel.context.select(this.dragNode.id);
                }
            } else if (finished == Drag.LINK && this.dragNode != null && moved) {
                double worldX = panel.worldX(x);
                double worldY = panel.worldY(y);
                GraphModel.Node target = panel.model.nodeAt(worldX, worldY);
                if (target != null && !target.file) {
                    panel.connect(this.dragNode, this.dragPort, target);
                } else if (target == null) {
                    panel.createAt(worldX, worldY, this.dragNode, this.dragPort);
                }
            }
            this.dragNode = null;
            return true;
        }

        @Override
        public void onMouseMoved(double x, double y) {
            this.mouseX = x;
            this.mouseY = y;
        }

        @Override
        public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
            this.panel().zoomAround(this.panel().zoom * (scrollY > 0 ? 1.15f : 1 / 1.15f), x, y);
            return true;
        }

        private void jumpMinimap(double x, double y) {
            int[] b = this.panel().model.bounds();
            float scale = this.minimapScale(b);
            int mapX = this.right() - MAP_W - 8;
            int mapY = this.bottom() - MAP_H - 8;
            this.panel().centerOn(b[0] + (x - mapX - 4) / scale, b[1] + (y - mapY - 4) / scale);
        }

        private float minimapScale(int[] b) {
            return Math.min((MAP_W - 8f) / Math.max(1, b[2] - b[0]), (MAP_H - 8f) / Math.max(1, b[3] - b[1]));
        }

        // ----- drawing -----

        @Override
        protected void render(GuiGraphics graphics, int mx, int my, float partialTick) {
            GraphPanel panel = this.panel();
            this.mouseX = mx;
            this.mouseY = my;
            graphics.fill(this.x(), this.y(), this.right(), this.bottom(), Theme.BG);
            if (panel.fitPending && this.width() > 0 && !panel.model.isEmpty()) {
                panel.fitPending = false;
                panel.fit();
            }
            if (panel.model.isEmpty()) {
                Theme.centered(graphics, Theme.tr(panel.filesOverview ? "graph.empty" : "graph.empty_nodes").getString(),
                        this.x() + this.width() / 2, this.y() + this.height() / 2 - 4, Theme.TEXT_MUTED);
                return;
            }
            double worldMouseX = panel.worldX(mx);
            double worldMouseY = panel.worldY(my);
            GraphModel.Node hovered = this.isHovered() && this.drag == Drag.NONE ? panel.model.nodeAt(worldMouseX, worldMouseY) : null;
            Object[] hoveredPort = this.isHovered() && this.drag == Drag.NONE && !panel.filesOverview
                    ? this.portAt(worldMouseX, worldMouseY) : null;
            String selected = panel.filesOverview ? null : panel.context.selectedId();
            String focus = hovered != null ? hovered.id : selected;

            if (panel.routesDirty) {
                panel.routesDirty = false;
                this.routeEdges();
            }
            Theme.clip(graphics, this.x(), this.y(), this.right(), this.bottom());
            this.drawGrid(graphics);
            graphics.pose().pushPose();
            graphics.pose().translate((float) (this.x() + panel.panX), (float) (this.y() + panel.panY), 0.0f);
            graphics.pose().scale(panel.zoom, panel.zoom, 1.0f);
            float line = 1.4f / panel.zoom;
            for (GraphModel.Edge edge : panel.model.edges) {
                boolean related = focus != null && (edge.from().id.equals(focus) || edge.to() != null && edge.to().id.equals(focus));
                this.drawEdge(graphics, edge, related, focus != null && !related, line);
            }
            if (this.drag == Drag.LINK && this.dragNode != null) {
                float sx = this.dragNode.portX();
                float sy = this.dragNode.portY(this.dragPort);
                Lines.curve(graphics, sx, sy, (float) worldMouseX, (float) worldMouseY,
                        Math.max(30, Math.abs((float) worldMouseX - sx) / 2), line * 1.5f, Theme.ACCENT, false);
            }
            Lines.flush(graphics);
            boolean detailed = panel.zoom >= 0.45f;
            for (GraphModel.Node node : panel.model.nodes.values()) {
                this.drawNode(graphics, node, node.id.equals(selected), node == hovered || node == this.dragNode,
                        hoveredPort != null && hoveredPort[0] == node ? (Integer) hoveredPort[1] : null, detailed);
            }
            graphics.pose().popPose();
            Theme.unclip(graphics);
            this.drawMinimap(graphics);
            this.drawLegend(graphics);
        }

        private void drawGrid(GuiGraphics graphics) {
            GraphPanel panel = this.panel();
            float step = GRID * panel.zoom;
            if (step < 10) {
                return;
            }
            double startX = this.x() + ((panel.panX % step) + step) % step;
            double startY = this.y() + ((panel.panY % step) + step) % step;
            for (double gx = startX; gx < this.right(); gx += step) {
                for (double gy = startY; gy < this.bottom(); gy += step) {
                    graphics.fill((int) gx, (int) gy, (int) gx + 1, (int) gy + 1, 0xFF2C2E33);
                }
            }
        }

        private void drawEdge(GuiGraphics graphics, GraphModel.Edge edge, boolean related, boolean faded, float line) {
            GraphModel.Node from = edge.from();
            float sx = from.portX();
            float sy = from.file ? from.y + from.height / 2.0f : from.portY(edge.port());
            int color = edge.color();
            if (faded) {
                color = (color & 0x00FFFFFF) | 0x40000000;
            }
            float width = related ? line * 2.0f : line;
            if (edge.to() == null) {
                Lines.segment(graphics, sx, sy, sx + 26, sy, width, Theme.DANGER);
                Lines.segment(graphics, sx + 26, sy - 4, sx + 34, sy + 4, width, Theme.DANGER);
                Lines.segment(graphics, sx + 26, sy + 4, sx + 34, sy - 4, width, Theme.DANGER);
                if (edge.missing() != null) {
                    Lines.flush(graphics);
                    Theme.text(graphics, edge.missing(), (int) sx + 38, (int) sy - 4, Theme.DANGER);
                }
                return;
            }
            GraphModel.Node to = edge.to();
            int count = this.panel().model.arrivals.getOrDefault(to.id, 1);
            float tx = to.inputX();
            float ty = to.file ? to.y + to.height / 2.0f : edge.arrivalY(count);
            boolean dashed = edge.kind() == GraphModel.Kind.FILE || tx < sx + 10;
            if (edge.route != null) {
                Lines.path(graphics, edge.route, 8.0f, width, color, dashed);
            } else {
                Lines.curve(graphics, sx, sy, tx - 5, ty, Math.max(30, (tx - sx) / 2), width, color, dashed);
            }
            Lines.triangle(graphics, tx, ty, tx - 7, ty - 4, tx - 7, ty + 4, color);
        }

        /**
         * Works out which links need a detour. A link that goes backwards (a loop) leaves to the
         * right, runs underneath both nodes and comes back in from the left; a forward link whose
         * curve would cross another node runs along a corridor above or below the nodes in the way.
         * Each detour gets its own lane so parallel detours do not sit on top of each other.
         */
        private void routeEdges() {
            GraphModel model = this.panel().model;
            model.assignArrivals();
            int loopLane = 0;
            int corridorLane = 0;
            for (GraphModel.Edge edge : model.edges) {
                edge.route = null;
                GraphModel.Node from = edge.from();
                GraphModel.Node to = edge.to();
                if (to == null) {
                    continue;
                }
                float sx = from.portX();
                float sy = from.file ? from.y + from.height / 2.0f : from.portY(edge.port());
                float tx = to.inputX();
                float ty = to.file ? to.y + to.height / 2.0f : edge.arrivalY(model.arrivals.getOrDefault(to.id, 1));
                if (tx < sx + 10) {
                    float lane = 14 + (loopLane++ % 6) * 6;
                    float below = Math.max(from.y + from.height, to.y + to.height) + lane;
                    edge.route = new float[]{sx, sy, sx + lane, sy, sx + lane, below, tx - lane - 6, below,
                            tx - lane - 6, ty, tx - 5, ty};
                    continue;
                }
                List<GraphModel.Node> blockers = this.blockers(edge, sx, sy, tx, ty);
                if (blockers.isEmpty()) {
                    continue;
                }
                float lane = 12 + (corridorLane++ % 5) * 5;
                float top = Float.MAX_VALUE;
                float bottom = -Float.MAX_VALUE;
                for (GraphModel.Node node : blockers) {
                    top = Math.min(top, node.y);
                    bottom = Math.max(bottom, node.y + node.height);
                }
                float middle = (sy + ty) / 2.0f;
                float corridor = Math.abs(middle - (top - lane)) <= Math.abs(middle - (bottom + lane))
                        ? top - lane : bottom + lane;
                float out = sx + 16;
                float in = tx - 18;
                edge.route = new float[]{sx, sy, out, sy, out, corridor, in, corridor, in, ty, tx - 5, ty};
            }
        }

        /** Nodes other than the two ends that the plain curve would pass through. */
        private List<GraphModel.Node> blockers(GraphModel.Edge edge, float sx, float sy, float tx, float ty) {
            List<GraphModel.Node> result = new ArrayList<>();
            float minX = Math.min(sx, tx);
            float maxX = Math.max(sx, tx);
            float minY = Math.min(sy, ty);
            float maxY = Math.max(sy, ty);
            float bend = Math.max(30, (tx - sx) / 2);
            for (GraphModel.Node node : this.panel().model.nodes.values()) {
                if (node == edge.from() || node == edge.to()
                        || node.x > maxX || node.x + GraphModel.WIDTH < minX
                        || node.y > maxY + 4 || node.y + node.height < minY - 4) {
                    continue;
                }
                for (int i = 1; i < 16; i++) {
                    float t = i / 16.0f;
                    float u = 1.0f - t;
                    float x = u * u * u * sx + 3 * u * u * t * (sx + bend) + 3 * u * t * t * (tx - 5 - bend) + t * t * t * (tx - 5);
                    float y = u * u * u * sy + 3 * u * u * t * sy + 3 * u * t * t * ty + t * t * t * ty;
                    if (x >= node.x - 3 && x <= node.x + GraphModel.WIDTH + 3
                            && y >= node.y - 3 && y <= node.y + node.height + 3) {
                        result.add(node);
                        break;
                    }
                }
            }
            return result;
        }

        private void drawNode(GuiGraphics graphics, GraphModel.Node node, boolean selected, boolean hovered,
                              Integer hoveredPort, boolean detailed) {
            int x = node.x;
            int y = node.y;
            int w = GraphModel.WIDTH;
            int h = node.height;
            int kind = node.file ? Theme.CYAN : OutlinePanel.kindColor(node.entry);
            // Shadow, body, tinted header and the type stripe.
            graphics.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x60000000);
            graphics.fill(x, y, x + w, y + h, selected ? Theme.SELECTED : Theme.RAISED);
            graphics.fill(x, y, x + w, y + GraphModel.HEAD, (kind & 0x00FFFFFF) | 0x38000000);
            graphics.fill(x, y, x + 3, y + h, kind);
            int border = selected ? Theme.ACCENT : hovered ? Theme.BORDER_STRONG : node.unreachable ? Theme.WARNING : Theme.BORDER;
            Theme.border(graphics, x, y, w, h, border);

            String title = node.file ? (node.id.startsWith("file:") ? node.id.substring(5) : node.id) : node.id;
            String badge = "";
            int badgeColor = Theme.TEXT_MUTED;
            if (node.file && node.start) {
                badge = Theme.tr("graph.current").getString();
                badgeColor = Theme.ACCENT;
            } else if (node.file && node.id.startsWith("file:")) {
                badge = Theme.tr("graph.other_file").getString();
                badgeColor = Theme.CYAN;
            } else if (node.start) {
                badge = Theme.tr("graph.badge_start").getString();
                badgeColor = Theme.SUCCESS;
            } else if (node.unreachable) {
                badge = Theme.tr("graph.badge_unlinked").getString();
                badgeColor = Theme.WARNING;
            } else if (node.ends) {
                badge = Theme.tr("graph.badge_end").getString();
                badgeColor = Theme.DANGER;
            }
            int badgeWidth = badge.isEmpty() ? 0 : Theme.font().width(badge) + 8;
            Theme.textIn(graphics, title, x + 8, y + 1, w - 18 - badgeWidth, GraphModel.HEAD, Theme.TEXT);
            if (!badge.isEmpty()) {
                int bx = x + w - badgeWidth - 8;
                graphics.fill(bx, y + 4, bx + badgeWidth, y + GraphModel.HEAD - 3, (badgeColor & 0x00FFFFFF) | 0x30000000);
                Theme.textIn(graphics, badge, bx + 4, y + 4, badgeWidth - 6, GraphModel.HEAD - 7, badgeColor);
            }
            if (detailed) {
                String summary = node.summary.isEmpty() ? Theme.tr("outline.no_text").getString() : node.summary;
                Theme.textIn(graphics, summary, x + 8, y + GraphModel.HEAD + 2, w - 14, GraphModel.LINE, Theme.TEXT_DIM);
                for (int i = 0; i < node.choices.size(); i++) {
                    int rowY = y + GraphModel.HEAD + GraphModel.LINE + GraphModel.PAD + i * GraphModel.CHOICE;
                    graphics.fill(x + 6, rowY, x + w - 6, rowY + 1, Theme.BORDER);
                    Theme.textIn(graphics, "› " + node.choices.get(i), x + 8, rowY + 1, w - 22, GraphModel.CHOICE, Theme.TEXT_DIM);
                }
            }
            if (node.file) {
                return;
            }
            // Input on the left, outputs on the right.
            this.dot(graphics, node.inputX(), node.inputY(), 3, Theme.BORDER_STRONG);
            if (node.hasChoices()) {
                for (int i = 0; i < node.choices.size(); i++) {
                    boolean hot = hoveredPort != null && hoveredPort == i;
                    this.dot(graphics, node.portX(), node.portY(i), hot ? 5 : 3, node.choiceColors.get(i));
                }
            } else if (!node.entry.isEndDialog()) {
                boolean hot = hoveredPort != null && hoveredPort == GraphModel.NEXT_PORT;
                boolean explicit = node.entry.getNextId() != null && !node.entry.getNextId().isBlank();
                this.dot(graphics, node.portX(), node.portY(GraphModel.NEXT_PORT), hot ? 5 : 3,
                        explicit ? Theme.CYAN : 0xFF8A8E97);
            }
        }

        private void dot(GuiGraphics graphics, int cx, int cy, int radius, int color) {
            graphics.fill(cx - radius, cy - radius + 1, cx + radius, cy + radius - 1, color);
            graphics.fill(cx - radius + 1, cy - radius, cx + radius - 1, cy + radius, color);
        }

        private void drawMinimap(GuiGraphics graphics) {
            GraphPanel panel = this.panel();
            int[] b = panel.model.bounds();
            float scale = this.minimapScale(b);
            int mapX = this.right() - MAP_W - 8;
            int mapY = this.bottom() - MAP_H - 8;
            graphics.fill(mapX, mapY, mapX + MAP_W, mapY + MAP_H, 0xE0222327);
            Theme.border(graphics, mapX, mapY, MAP_W, MAP_H, Theme.BORDER);
            String selected = panel.context.selectedId();
            for (GraphModel.Node node : panel.model.nodes.values()) {
                int nx = mapX + 4 + (int) ((node.x - b[0]) * scale);
                int ny = mapY + 4 + (int) ((node.y - b[1]) * scale);
                int nw = Math.max(2, (int) (GraphModel.WIDTH * scale));
                int nh = Math.max(2, (int) (node.height * scale));
                int color = node.id.equals(selected) ? Theme.ACCENT
                        : node.file ? Theme.CYAN : OutlinePanel.kindColor(node.entry);
                graphics.fill(nx, ny, nx + nw, ny + nh, (color & 0x00FFFFFF) | 0xB0000000);
            }
            double viewLeft = panel.worldX(this.x());
            double viewTop = panel.worldY(this.y());
            double viewRight = panel.worldX(this.right());
            double viewBottom = panel.worldY(this.bottom());
            int vx1 = Mth.clamp(mapX + 4 + (int) ((viewLeft - b[0]) * scale), mapX, mapX + MAP_W);
            int vy1 = Mth.clamp(mapY + 4 + (int) ((viewTop - b[1]) * scale), mapY, mapY + MAP_H);
            int vx2 = Mth.clamp(mapX + 4 + (int) ((viewRight - b[0]) * scale), mapX, mapX + MAP_W);
            int vy2 = Mth.clamp(mapY + 4 + (int) ((viewBottom - b[1]) * scale), mapY, mapY + MAP_H);
            if (vx2 > vx1 && vy2 > vy1) {
                Theme.border(graphics, vx1, vy1, vx2 - vx1, vy2 - vy1, 0xC0E4E5E9);
            }
        }

        private void drawLegend(GuiGraphics graphics) {
            GraphPanel panel = this.panel();
            int x = this.x() + 8;
            int y = this.bottom() - 14;
            String zoom = Math.round(panel.zoom * 100) + "%";
            x = this.legendItem(graphics, x, y, Theme.ACCENT, Theme.tr("graph.legend_line").getString(), false);
            x = this.legendItem(graphics, x, y, Theme.WARNING, Theme.tr("graph.legend_choice").getString(), false);
            x = this.legendItem(graphics, x, y, Theme.DANGER, Theme.tr("graph.legend_end").getString(), false);
            x = this.legendItem(graphics, x, y, 0xFF8A8E97, Theme.tr("graph.legend_continue").getString(), true);
            x = this.legendItem(graphics, x, y, Theme.CYAN, Theme.tr("graph.legend_jump").getString(), true);
            Theme.text(graphics, zoom + "   " + Theme.tr(panel.filesOverview ? "graph.hint_files" : "graph.hint").getString(),
                    x + 6, y + 1, Theme.TEXT_MUTED);
        }

        private int legendItem(GuiGraphics graphics, int x, int y, int color, String label, boolean asLine) {
            if (asLine) {
                graphics.fill(x, y + 4, x + 10, y + 6, color);
            } else {
                graphics.fill(x, y + 1, x + 8, y + 9, color);
            }
            Theme.text(graphics, label, x + 13, y + 1, Theme.TEXT_MUTED);
            return x + 13 + Theme.font().width(label) + 10;
        }
    }
}

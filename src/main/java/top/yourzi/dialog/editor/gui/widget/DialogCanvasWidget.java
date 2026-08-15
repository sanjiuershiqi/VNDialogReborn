package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.yourzi.dialog.editor.gui.EditorRenderHelper;
import top.yourzi.dialog.editor.gui.EditorScreenState;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 节点画布组件：以无限画布 + 节点卡片 + 连线的方式可视化对话流向（DAG）。
 *
 * 布局与交互借鉴 FTB Quests 编辑器（原生 MC GUI 中被大规模验证的节点画布范式）：
 * - 滚轮=纵向平移，Shift+滚轮=横向平移，Ctrl+滚轮=以鼠标为中心缩放；
 * - 左键拖节点=移动（4px 网格吸附），左键拖空白/中键拖=平移画布；
 * - 右键节点/空白=上下文菜单；双击节点=打开属性面板。
 *
 * 三种边（对话数据模型特有，通用节点编辑器没有的）：
 * - 显式 next：实线蓝（EDGE_NEXT），源=节点头部右侧端口；
 * - 隐式顺序边：虚线灰（EDGE_IMPLICIT），复刻运行时 getNextEntry 的"无 next 时落到数组下一条"回退，
 *   让作者清楚看到不写 next 时的隐含流向（此前任何视图都不表达）；
 * - 选项边：按选项索引取 OPTION_PALETTE 颜色，与节点右侧端口圆点一一对应。
 *
 * 渲染采用"世界坐标系 pose"方案：pushPose(pan) + scale 后直接按世界坐标画填充与文字，
 * 缩放时节点/文字/边一起缩放（与 FTB Quests 一致），1px 线宽按 1/scale 补偿保持屏幕恒定。
 */
public class DialogCanvasWidget extends AbstractWidget {
    // ===== 节点卡片尺寸（世界像素） =====
    private static final int NODE_W = 96;
    private static final int HEADER_H = 12;
    private static final int LINE_H = 9;
    private static final int SUMMARY_LINES = 2;
    private static final int BASE_NODE_H = HEADER_H + 3 + SUMMARY_LINES * LINE_H + 4; // 37
    private static final int PORT_SPACING = 8;
    private static final int PORT_SZ = 4;
    // ===== 自动布局间距 =====
    private static final int LAYER_GAP_X = NODE_W + 48;
    private static final int ROW_GAP_Y = 14;
    private static final int ORPHAN_COL_SIZE = 10; // 孤儿列每列节点数（超出换列）
    // ===== 相机 =====
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 1.75f;
    private static final float ZOOM_STEP = 1.15f;
    // ===== 交互 =====
    private static final int SNAP = 4;          // 节点拖拽网格吸附
    private static final int DBLCLICK_MS = 500; // 双击判定窗口
    private static final float PAN_STEP = 40f;  // 方向键平移步长（世界像素）

    private final Font font;
    private DialogSequence sequence;
    // ===== 相机状态 =====
    private float scale = 1.0f;
    private float panX = 0f;
    private float panY = 0f;
    private boolean cameraInit = false;
    // ===== 模型缓存 =====
    /** 节点 ID → 世界坐标 [x, y]。 */
    private final Map<String, int[]> positions = new HashMap<>();
    /** 节点 ID → 入度（汇合检测，>1 表示多路分支汇合，DAG 特有）。 */
    private final Map<String, Integer> inDegree = new HashMap<>();
    /** 节点 ID → 卡片高度（取决于选项数）。 */
    private final Map<String, Integer> nodeHeights = new HashMap<>();
    private String selectedId = null;
    // ===== 交互状态 =====
    private enum DragMode { NONE, PAN, NODE, CONNECT }
    private DragMode dragMode = DragMode.NONE;
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    /** 左键按下时是否落在空白（用于区分"点击空白取消选中"与"拖拽平移"，拖拽不取消选中）。 */
    private boolean pressOnEmpty = false;
    /** 本次拖拽累计位移（屏幕像素），<4 视为点击。 */
    private float dragDist = 0f;
    // ===== 端口拖拽连线（NodeGraph/QuestCraft 范式：拖到节点=建边，拖到空白=新建并连接） =====
    /** 连线拖拽源节点 ID。 */
    private String connectSourceId = null;
    /** 连线拖拽源端口：-1=next 头部端口，>=0=选项索引端口。 */
    private int connectPortIndex = -1;
    /** 渲染帧 hover 的节点 ID（悬停聚焦：相关边高亮、其余变暗，FTB Quests 范式）。 */
    private String hoveredNodeId = null;
    private String dragNodeId = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private String lastClickNodeId = null;
    private long lastClickTime = 0L;
    // ===== 右键菜单 =====
    private boolean menuOpen = false;
    private int menuX = 0;
    private int menuY = 0;
    private final List<MenuItem> menuItems = new ArrayList<>();
    private record MenuItem(Component label, Runnable action) {
    }
    // ===== 回调（由宿主 Screen 注入） =====
    private Consumer<DialogEntry> onSelect;
    private Consumer<DialogEntry> onDelete;
    private Consumer<DialogEntry> onAddChild;
    private Consumer<DialogEntry> onRename;
    private Runnable onEditProperties;
    private Runnable onAddNode;
    private Runnable onCopy;
    private Runnable onPaste;

    public DialogCanvasWidget(int x, int y, int width, int height, Font font) {
        super(x, y, width, height, Component.translatable("gui.vn_edit.canvas"));
        this.font = font;
    }

    public void setCallbacks(Consumer<DialogEntry> onSelect, Consumer<DialogEntry> onDelete,
                             Consumer<DialogEntry> onAddChild, Consumer<DialogEntry> onRename,
                             Runnable onEditProperties, Runnable onAddNode,
                             Runnable onCopy, Runnable onPaste) {
        this.onSelect = onSelect;
        this.onDelete = onDelete;
        this.onAddChild = onAddChild;
        this.onRename = onRename;
        this.onEditProperties = onEditProperties;
        this.onAddNode = onAddNode;
        this.onCopy = onCopy;
        this.onPaste = onPaste;
    }

    // =====================================================================
    // 数据绑定
    // =====================================================================

    /** 绑定序列：恢复布局与选中，无缓存布局时执行自动布局并聚焦起点。 */
    public void setSequence(DialogSequence sequence) {
        this.sequence = sequence;
        this.cameraInit = false;
        this.positions.clear();
        this.selectedId = null;
        if (sequence != null) {
            this.positions.putAll(EditorScreenState.get().getCanvasLayout(sequence.getId()));
            this.selectedId = EditorScreenState.get().getSelectedNodeId();
            if (this.positions.isEmpty() && this.hasEntries()) {
                this.autoLayout(false);
            } else {
                this.rebuildCaches();
                this.ensurePositionsForNewNodes();
            }
        } else {
            this.inDegree.clear();
            this.nodeHeights.clear();
        }
        this.centerOnStart();
    }

    /** 结构变化后刷新缓存（宿主增删节点/改引用后调用），保留已有布局，只为新节点补位置。 */
    public void refresh() {
        if (this.sequence == null) {
            return;
        }
        this.rebuildCaches();
        this.ensurePositionsForNewNodes();
        this.saveLayout();
    }

    private boolean hasEntries() {
        return this.sequence != null && this.sequence.getEntries() != null && this.sequence.getEntries().length > 0;
    }

    /** 重算入度与卡片高度缓存。 */
    private void rebuildCaches() {
        this.inDegree.clear();
        this.nodeHeights.clear();
        if (!this.hasEntries()) {
            return;
        }
        DialogEntry[] entries = this.sequence.getEntries();
        for (DialogEntry e : entries) {
            if (e == null || e.getId() == null) {
                continue;
            }
            int optCount = e.getOptions() != null ? e.getOptions().length : 0;
            this.nodeHeights.put(e.getId(), Math.max(BASE_NODE_H, HEADER_H + 10 + optCount * PORT_SPACING));
            for (String target : this.outTargets(e)) {
                this.inDegree.merge(target, 1, Integer::sum);
            }
        }
    }

    /** 收集节点全部出边目标 ID（显式 next + 选项 target），不校验是否可解析。 */
    private List<String> outTargets(DialogEntry entry) {
        List<String> targets = new ArrayList<>();
        if (entry.getNextId() != null && !entry.getNextId().isEmpty()) {
            targets.add(entry.getNextId());
        }
        if (entry.getOptions() != null) {
            for (DialogOption opt : entry.getOptions()) {
                if (opt != null && opt.getTargetId() != null && !opt.getTargetId().isEmpty()) {
                    targets.add(opt.getTargetId());
                }
            }
        }
        return targets;
    }

    /** 为没有布局坐标的新节点在现有内容下方补一列落点（从起点正下方开始堆叠）。 */
    private void ensurePositionsForNewNodes() {
        if (!this.hasEntries()) {
            return;
        }
        int maxY = Integer.MIN_VALUE;
        for (int[] pos : this.positions.values()) {
            maxY = Math.max(maxY, pos[1]);
        }
        int spawnY = (maxY == Integer.MIN_VALUE ? 0 : maxY) + BASE_NODE_H + ROW_GAP_Y;
        int spawned = 0;
        for (DialogEntry e : this.sequence.getEntries()) {
            if (e == null || e.getId() == null || this.positions.containsKey(e.getId())) {
                continue;
            }
            this.positions.put(e.getId(), new int[]{0, spawnY + spawned * (BASE_NODE_H + ROW_GAP_Y)});
            spawned++;
        }
        if (spawned > 0) {
            this.saveLayout();
        }
    }

    private void saveLayout() {
        if (this.sequence != null && this.sequence.getId() != null) {
            EditorScreenState.get().setCanvasLayout(this.sequence.getId(), this.positions);
        }
    }

    // =====================================================================
    // 坐标换算
    // =====================================================================

    private float worldToScreenX(float wx) {
        return wx * this.scale + this.panX;
    }

    private float worldToScreenY(float wy) {
        return wy * this.scale + this.panY;
    }

    private float screenToWorldX(float sx) {
        return (sx - this.panX) / this.scale;
    }

    private float screenToWorldY(float sy) {
        return (sy - this.panY) / this.scale;
    }

    /** 相机归一化屏幕坐标（相对组件左上角）→ 世界坐标。 */
    private float mouseWorldX(double mouseX) {
        return this.screenToWorldX((float) (mouseX - this.getX()));
    }

    private float mouseWorldY(double mouseY) {
        return this.screenToWorldY((float) (mouseY - this.getY()));
    }

    /** 世界厚度：按缩放补偿，保证屏幕上恒定约 pixels 像素。 */
    private int lineThickness(float screenPixels) {
        return Math.max(1, Mth.ceil(screenPixels / this.scale));
    }

    private void centerOn(int worldX, int worldY) {
        this.panX = this.getWidth() / 2f - worldX * this.scale;
        this.panY = this.getHeight() / 2f - worldY * this.scale;
    }

    private void centerOnStart() {
        DialogEntry start = this.sequence != null ? this.sequence.getFirstEntry() : null;
        if (start == null || start.getId() == null) {
            this.panX = 24;
            this.panY = 24;
            this.cameraInit = true;
            return;
        }
        int[] pos = this.positions.get(start.getId());
        if (pos != null) {
            this.centerOn(pos[0] + NODE_W / 2, pos[1] + 20);
        }
        this.cameraInit = true;
    }

    private void zoomAt(float screenX, float screenY, float factor) {
        float worldX = this.screenToWorldX(screenX);
        float worldY = this.screenToWorldY(screenY);
        this.scale = Mth.clamp(this.scale * factor, MIN_SCALE, MAX_SCALE);
        this.panX = screenX - worldX * this.scale;
        this.panY = screenY - worldY * this.scale;
    }

    // =====================================================================
    // 自动布局（简化分层：起点可达部分按最长路径分层 + 层内居中；孤儿放右侧列）
    // =====================================================================

    /**
     * 自动布局。resetCamera=true 时布局后重置缩放并聚焦起点。
     */
    public void autoLayout(boolean resetCamera) {
        this.positions.clear();
        if (!this.hasEntries()) {
            this.saveLayout();
            return;
        }
        DialogEntry[] entries = this.sequence.getEntries();
        Map<String, Integer> indexById = new HashMap<>();
        for (int i = 0; i < entries.length; i++) {
            if (entries[i] != null && entries[i].getId() != null) {
                indexById.put(entries[i].getId(), i);
            }
        }
        // 从起点 BFS 标记可达节点（与运行时行为一致）
        Set<String> reachable = new HashSet<>();
        DialogEntry start = this.sequence.getFirstEntry();
        if (start != null && start.getId() != null) {
            List<String> queue = new ArrayList<>();
            queue.add(start.getId());
            reachable.add(start.getId());
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                DialogEntry e = this.sequence.findEntryById(id);
                if (e == null) {
                    continue;
                }
                for (String target : this.outTargets(e)) {
                    if (this.sequence.findEntryById(target) != null && reachable.add(target)) {
                        queue.add(target);
                    }
                }
            }
        }
        // 最长路径分层（迭代松弛，循环图以 |E| 次迭代封顶）
        Map<String, Integer> layer = new HashMap<>();
        for (String id : reachable) {
            layer.put(id, 0);
        }
        for (int iter = 0; iter < entries.length; iter++) {
            boolean changed = false;
            for (String id : reachable) {
                DialogEntry e = this.sequence.findEntryById(id);
                if (e == null) {
                    continue;
                }
                int fromLayer = layer.get(id);
                for (String target : this.outTargets(e)) {
                    if (!reachable.contains(target)) {
                        continue;
                    }
                    if (layer.get(target) < fromLayer + 1) {
                        layer.put(target, fromLayer + 1);
                        changed = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
        // 分层装箱：层内按数组顺序，垂直居中
        int maxLayer = 0;
        for (int l : layer.values()) {
            maxLayer = Math.max(maxLayer, l);
        }
        Map<Integer, List<String>> byLayer = new HashMap<>();
        for (String id : layer.keySet()) {
            byLayer.computeIfAbsent(layer.get(id), k -> new ArrayList<>()).add(id);
        }
        for (List<String> ids : byLayer.values()) {
            ids.sort((a, b) -> Integer.compare(indexById.getOrDefault(a, 0), indexById.getOrDefault(b, 0)));
        }
        for (Map.Entry<Integer, List<String>> e : byLayer.entrySet()) {
            List<String> ids = e.getValue();
            int x = e.getKey() * LAYER_GAP_X;
            int totalH = (ids.size() - 1) * (BASE_NODE_H + ROW_GAP_Y);
            for (int i = 0; i < ids.size(); i++) {
                this.positions.put(ids.get(i), new int[]{x, i * (BASE_NODE_H + ROW_GAP_Y) - totalH / 2});
            }
        }
        // 孤儿节点（起点不可达）：放在最右，每列 ORPHAN_COL_SIZE 个
        List<String> orphans = new ArrayList<>();
        for (DialogEntry e : entries) {
            if (e != null && e.getId() != null && !reachable.contains(e.getId())) {
                orphans.add(e.getId());
            }
        }
        for (int i = 0; i < orphans.size(); i++) {
            int col = i / ORPHAN_COL_SIZE;
            int row = i % ORPHAN_COL_SIZE;
            this.positions.put(orphans.get(i), new int[]{
                    (maxLayer + 1) * LAYER_GAP_X + col * LAYER_GAP_X,
                    row * (BASE_NODE_H + ROW_GAP_Y)});
        }
        this.saveLayout();
        if (resetCamera) {
            this.scale = 1.0f;
            this.centerOnStart();
        }
    }

    /** 聚焦起点节点（缩放回 1.0）。 */
    public void focusStart() {
        this.scale = 1.0f;
        this.centerOnStart();
    }

    // =====================================================================
    // 选中
    // =====================================================================

    public DialogEntry getSelectedEntry() {
        if (this.selectedId == null || this.sequence == null) {
            return null;
        }
        return this.sequence.findEntryById(this.selectedId);
    }

    /**
     * 节点重命名后迁移坐标缓存：旧 ID 的位置转给新 ID，避免重命名后节点跳到生成列。
     * 由宿主在重命名成功后调用（refresh 会为新 ID 生成临时位置，随后被迁移的旧位置覆盖）。
     */
    public void renameNode(String oldId, String newId) {
        int[] pos = this.positions.remove(oldId);
        if (pos != null) {
            this.positions.put(newId, pos);
            this.saveLayout();
        }
    }

    /**
     * 平移相机使节点完整进入可视区（不改变缩放，四周留 24px 边距）。
     * 画布模式右侧停靠面板展开/收起导致画布宽度变化后，由宿主调用防止选中节点被裁出视野。
     */
    public void ensureVisible(String id) {
        int[] pos = this.positions.get(id);
        if (pos == null) {
            return;
        }
        int nodeH = this.nodeHeights.getOrDefault(id, BASE_NODE_H);
        float sx0 = pos[0] * this.scale + this.panX;
        float sy0 = pos[1] * this.scale + this.panY;
        float sx1 = (pos[0] + NODE_W) * this.scale + this.panX;
        float sy1 = (pos[1] + nodeH) * this.scale + this.panY;
        float margin = 24f;
        if (sx0 < margin) {
            this.panX += margin - sx0;
        }
        if (sy0 < margin) {
            this.panY += margin - sy0;
        }
        if (sx1 > this.getWidth() - margin) {
            this.panX -= sx1 - (this.getWidth() - margin);
        }
        if (sy1 > this.getHeight() - margin) {
            this.panY -= sy1 - (this.getHeight() - margin);
        }
    }

    /** 按 ID 选中节点（供粘贴/复制后同步选中），触发回调但不移动相机。 */
    public boolean selectEntryById(String id) {
        if (id == null || this.sequence == null || this.sequence.findEntryById(id) == null) {
            return false;
        }
        this.selectedId = id;
        EditorScreenState.get().setSelectedNodeId(id);
        DialogEntry entry = this.sequence.findEntryById(id);
        if (this.onSelect != null && entry != null) {
            this.onSelect.accept(entry);
        }
        return true;
    }

    private void selectNode(String id, DialogEntry entry) {
        this.selectedId = id;
        EditorScreenState.get().setSelectedNodeId(id);
        if (this.onSelect != null) {
            this.onSelect.accept(entry);
        }
    }

    private void clearSelection() {
        this.selectedId = null;
        EditorScreenState.get().setSelectedNodeId(null);
        if (this.onSelect != null) {
            this.onSelect.accept(null);
        }
    }

    // =====================================================================
    // 渲染
    // =====================================================================

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 画布底色
        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), EditorTheme.CANVAS_BG);
        graphics.enableScissor(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight());
        try {
            graphics.pose().pushPose();
            graphics.pose().translate(this.getX() + this.panX, this.getY() + this.panY, 0);
            graphics.pose().scale(this.scale, this.scale, 1.0f);
            try {
                this.renderGrid(graphics);
                this.renderEdges(graphics);
                this.renderNodes(graphics, mouseX, mouseY);
            } finally {
                graphics.pose().popPose();
            }
        } finally {
            graphics.disableScissor();
        }
        this.renderEmptyStates(graphics);
        this.renderHud(graphics, mouseX, mouseY);
        if (this.menuOpen) {
            this.renderMenu(graphics, mouseX, mouseY);
        }
    }

    /** 点阵网格：世界坐标每 32px 一个点，缩放太小或点数过多时跳过。 */
    private void renderGrid(GuiGraphics graphics) {
        if (this.scale < 0.6f) {
            return;
        }
        float viewLeftW = this.screenToWorldX(0);
        float viewTopW = this.screenToWorldY(0);
        float viewRightW = this.screenToWorldX(this.getWidth());
        float viewBottomW = this.screenToWorldY(this.getHeight());
        int step = 32;
        int dot = 1;
        int cols = (int) ((viewRightW - viewLeftW) / step);
        int rows = (int) ((viewBottomW - viewTopW) / step);
        if (cols > 90 || rows > 60) {
            return;
        }
        int color = EditorRenderHelper.withAlphaRatio(EditorTheme.TEXT_PRIMARY, 0.06f);
        for (int wx = (int) Math.floor(viewLeftW / step) * step; wx <= viewRightW; wx += step) {
            for (int wy = (int) Math.floor(viewTopW / step) * step; wy <= viewBottomW; wy += step) {
                graphics.fill(wx, wy, wx + dot, wy + dot, color);
            }
        }
    }

    /** 渲染三种边：显式 next（实线）、隐式顺序（虚线）、选项边（调色板）。先画边再画节点。 */
    private void renderEdges(GuiGraphics graphics) {
        if (!this.hasEntries()) {
            return;
        }
        DialogEntry[] entries = this.sequence.getEntries();
        for (int i = 0; i < entries.length; i++) {
            DialogEntry e = entries[i];
            if (e == null || e.getId() == null) {
                continue;
            }
            boolean hot = e.getId().equals(this.selectedId);
            // 1) 显式 next
            if (e.getNextId() != null && !e.getNextId().isEmpty()) {
                this.drawEdge(graphics, e.getId(), e.getNextId(), EditorTheme.EDGE_NEXT, false, hot);
            } else if ((e.getOptions() == null || e.getOptions().length == 0)
                    && !e.isEndDialog() && i < entries.length - 1) {
                // 2) 隐式顺序边：复刻运行时 getNextEntry 的数组顺序回退
                DialogEntry next = entries[i + 1];
                if (next != null && next.getId() != null) {
                    this.drawEdge(graphics, e.getId(), next.getId(), EditorTheme.EDGE_IMPLICIT, true, hot);
                }
            }
            // 3) 选项边
            if (e.getOptions() != null) {
                for (int oi = 0; oi < e.getOptions().length; oi++) {
                    DialogOption opt = e.getOptions()[oi];
                    if (opt == null || opt.getTargetId() == null || opt.getTargetId().isEmpty()) {
                        continue;
                    }
                    int color = EditorTheme.OPTION_PALETTE[oi % EditorTheme.OPTION_PALETTE.length];
                    this.drawEdge(graphics, e.getId(), opt.getTargetId(), color, false, hot);
                }
            }
        }
    }

    /**
     * 画一条从 source 到 target 的三次贝塞尔边（水平切线控制点）。
     * 细线：逐像素步进的 th×th 小方块逼近（MC GuiGraphics 只有轴对齐 fill，
     * 若按段画包围盒，斜线段会糊成实心块——上一版视觉事故的根因）。
     * dashed=true 时 2 段画 2 段跳模拟虚线；端点选中时 2px 提亮，平时 1px。
     */
    private void drawEdge(GuiGraphics graphics, String sourceId, String targetId, int color, boolean dashed, boolean hot) {
        int[] sp = this.positions.get(sourceId);
        int[] tp = this.positions.get(targetId);
        DialogEntry source = this.sequence.findEntryById(sourceId);
        if (sp == null || tp == null) {
            return; // 悬空引用：不画（节点卡片上的 ⚠ 已表达）
        }
        boolean targetHot = targetId.equals(this.selectedId);
        int th = this.lineThickness(hot || targetHot ? 2f : 1f);
        int edgeColor = hot || targetHot ? EditorRenderHelper.brighten(color, 40) : color;
        DialogEntry targetEntry = this.sequence.findEntryById(targetId);
        int sy = optionPortWorldY(source, sourceId, targetId);
        int sx = sp[0] + NODE_W;
        int ty = tp[1] + (targetEntry != null
                ? this.nodeHeights.getOrDefault(targetId, BASE_NODE_H) / 2
                : BASE_NODE_H / 2);
        int tx = tp[0];
        // 水平切线三次贝塞尔
        float dx = Math.abs(tx - sx);
        float ext = Math.max(24f, dx * 0.5f);
        float c1x = sx + ext, c1y = sy;
        float c2x = tx - ext, c2y = ty;
        int segments = 24;
        float prevX = sx, prevY = sy;
        for (int s = 1; s <= segments; s++) {
            float t = s / (float) segments;
            float mt = 1 - t;
            float x = mt * mt * mt * sx + 3 * mt * mt * t * c1x + 3 * mt * t * t * c2x + t * t * t * tx;
            float y = mt * mt * mt * sy + 3 * mt * mt * t * c1y + 3 * mt * t * t * c2y + t * t * t * ty;
            if (!dashed || ((s - 1) / 2) % 2 == 0) {
                this.fillLine(graphics, prevX, prevY, x, y, th, edgeColor);
            }
            prevX = x;
            prevY = y;
        }
        // 端口方块：源端口（右缘）+ 目标端口（左缘）
        graphics.fill(sx - PORT_SZ, sy - PORT_SZ / 2, sx, sy + (PORT_SZ + 1) / 2, edgeColor);
        graphics.fill(tx, ty - PORT_SZ / 2, tx + PORT_SZ, ty + (PORT_SZ + 1) / 2, edgeColor);
    }

    /**
     * 细线段（世界坐标）：沿线每 1 世界像素步进画 th×th 方块，
     * 视觉上连续且粗细恒定，避免包围盒 fill 把斜线糊成多边形色块。
     */
    private void fillLine(GuiGraphics graphics, float x0, float y0, float x1, float y1, int th, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        int steps = Math.max(1, Mth.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        float half = th / 2f;
        int prevIx = Integer.MIN_VALUE;
        int prevIy = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int ix = Mth.floor(x0 + dx * t - half);
            int iy = Mth.floor(y0 + dy * t - half);
            if (ix != prevIx || iy != prevIy) { // 相邻步落在同一像素时跳过重绘
                graphics.fill(ix, iy, ix + th, iy + th, color);
                prevIx = ix;
                prevIy = iy;
            }
        }
    }

    /** 计算从 source 指向 targetId 的边在源节点右缘的端口世界 Y：next 用头部端口，选项用对应索引端口。 */
    private int optionPortWorldY(DialogEntry source, String sourceId, String targetId) {
        int[] sp = this.positions.get(sourceId);
        int baseY = sp[1] + HEADER_H / 2 + 1;
        if (source.getNextId() != null && targetId.equals(source.getNextId())) {
            return baseY;
        }
        if (source.getOptions() != null) {
            for (int oi = 0; oi < source.getOptions().length; oi++) {
                DialogOption opt = source.getOptions()[oi];
                if (opt != null && targetId.equals(opt.getTargetId())) {
                    return sp[1] + HEADER_H + 6 + oi * PORT_SPACING;
                }
            }
        }
        return baseY;
    }

    /** 渲染节点卡片：头部（类型图标+ID）+ 两行摘要 + 右缘端口 + 状态标记（起点/孤儿/汇合）。 */
    private void renderNodes(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!this.hasEntries()) {
            return;
        }
        float wmx = this.mouseWorldX(mouseX);
        float wmy = this.mouseWorldY(mouseY);
        int border = this.lineThickness(1f);
        DialogEntry start = this.sequence.getFirstEntry();
        String startId = start != null ? start.getId() : null;
        for (DialogEntry e : this.sequence.getEntries()) {
            if (e == null || e.getId() == null) {
                continue;
            }
            int[] pos = this.positions.get(e.getId());
            if (pos == null) {
                continue;
            }
            boolean isStart = e.getId().equals(startId);
            boolean isOrphan = this.isOrphan(e.getId());
            boolean selected = e.getId().equals(this.selectedId);
            boolean hovered = wmx >= pos[0] && wmx <= pos[0] + NODE_W
                    && wmy >= pos[1] && wmy <= pos[1] + this.nodeHeights.getOrDefault(e.getId(), BASE_NODE_H);
            int nodeH = this.nodeHeights.getOrDefault(e.getId(), BASE_NODE_H);
            // 卡片主体
            int body = selected ? EditorTheme.BG_SELECTED : EditorTheme.BG_ELEVATED;
            graphics.fill(pos[0], pos[1], pos[0] + NODE_W, pos[1] + nodeH, body);
            // 头部条：起点=ACCENT_DIM，endDialog=暗红，普通=ELEVATED 提亮
            int headerBg = isStart ? EditorTheme.ACCENT_DIM
                    : e.isEndDialog() ? EditorRenderHelper.withAlphaRatio(EditorTheme.DANGER, 0.35f)
                    : EditorTheme.BG_HOVER;
            graphics.fill(pos[0], pos[1], pos[0] + NODE_W, pos[1] + HEADER_H, headerBg);
            // 边框：选中=ACCENT，悬空引用=警告色，普通=BORDER
            int borderColor = selected ? EditorTheme.ACCENT
                    : this.hasDanglingRef(e) ? EditorTheme.STATUS_WARNING
                    : hovered ? EditorTheme.BORDER_LIGHT : EditorTheme.BORDER;
            EditorRenderHelper.drawBorder(graphics, pos[0], pos[1], NODE_W, nodeH, borderColor);
            if (selected) {
                // 选中额外外发光（1px 半透明 ACCENT 扩边）
                EditorRenderHelper.drawBorder(graphics, pos[0] - border, pos[1] - border,
                        NODE_W + border * 2, nodeH + border * 2, EditorTheme.ACCENT_TINT);
            }
            // 头部内容：图标 + ID（截断）
            String icon = isStart ? "\u25b6" : this.typeIcon(e);
            String idText = (isOrphan ? "\u26a0 " : "") + icon + " " + e.getId();
            String clippedId = this.font.plainSubstrByWidth(idText, NODE_W - 14);
            graphics.drawString(this.font, clippedId, pos[0] + 4, pos[1] + 2,
                    selected ? EditorTheme.TEXT_PRIMARY : EditorTheme.TEXT_SECONDARY, selected);
            // 汇合标记：入度>1 时头部右侧显示 ↓n
            int indeg = this.inDegree.getOrDefault(e.getId(), 0);
            if (indeg > 1) {
                String badge = "\u2193" + indeg;
                graphics.drawString(this.font, badge, pos[0] + NODE_W - this.font.width(badge) - 3,
                        pos[1] + 2, EditorTheme.TEXT_MUTED);
            }
            // 摘要两行：说话人 / 正文
            int textMaxW = NODE_W - 8;
            String speaker = this.plainText(e.getSpeaker(), 40);
            String text = this.plainText(e.getText(), 80);
            if (!speaker.isEmpty()) {
                graphics.drawString(this.font, this.font.plainSubstrByWidth(speaker, textMaxW),
                        pos[0] + 4, pos[1] + HEADER_H + 3, EditorTheme.TEXT_WARM);
            }
            if (!text.isEmpty()) {
                String l1 = this.font.plainSubstrByWidth(text, textMaxW * 2);
                String line1 = this.font.plainSubstrByWidth(l1, textMaxW);
                graphics.drawString(this.font, line1, pos[0] + 4, pos[1] + HEADER_H + 3 + LINE_H, EditorTheme.TEXT_SECONDARY);
                if (this.font.width(l1) > textMaxW) {
                    String rest = l1.substring(line1.length());
                    String line2 = this.font.plainSubstrByWidth(rest, textMaxW);
                    graphics.drawString(this.font, line2, pos[0] + 4, pos[1] + HEADER_H + 3 + LINE_H * 2, EditorTheme.TEXT_SECONDARY);
                }
            }
            // 选项端口圆点（与边颜色一一对应）
            if (e.getOptions() != null) {
                for (int oi = 0; oi < e.getOptions().length; oi++) {
                    int py = pos[1] + HEADER_H + 6 + oi * PORT_SPACING;
                    int color = EditorTheme.OPTION_PALETTE[oi % EditorTheme.OPTION_PALETTE.length];
                    graphics.fill(pos[0] + NODE_W - PORT_SZ, py - PORT_SZ / 2, pos[0] + NODE_W, py + (PORT_SZ + 1) / 2, color);
                    // 选项序号小字
                    graphics.drawString(this.font, String.valueOf(oi + 1), pos[0] + NODE_W - 14, py - 4, EditorTheme.TEXT_MUTED);
                }
            }
            // 悬停整卡提亮
            if (hovered && !selected) {
                graphics.fill(pos[0], pos[1], pos[0] + NODE_W, pos[1] + nodeH, EditorTheme.HOVER_TINT);
            }
        }
    }

    private String typeIcon(DialogEntry e) {
        if (e.isEndDialog()) {
            return "\u2297";
        }
        if (e.getOptions() != null && e.getOptions().length > 0) {
            return "\u25c6";
        }
        return "\u25cb";
    }

    /** 从起点不可达（等价树视图的孤儿判定，但画布上单独一列展示）。 */
    private boolean isOrphan(String entryId) {
        DialogEntry start = this.sequence != null ? this.sequence.getFirstEntry() : null;
        if (start == null || start.getId() == null || start.getId().equals(entryId)) {
            return false;
        }
        // BFS 判定可达性（节点数有限，每次全量计算可接受；缓存到 selectedId 变化代价更低，v1 从简）
        Set<String> visited = new HashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(start.getId());
        visited.add(start.getId());
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            DialogEntry e = this.sequence.findEntryById(id);
            if (e == null) {
                continue;
            }
            for (String t : this.outTargets(e)) {
                if (this.sequence.findEntryById(t) != null && visited.add(t)) {
                    queue.add(t);
                }
            }
        }
        return !visited.contains(entryId);
    }

    /** 节点是否存在悬空引用（next/option target 指向不存在的节点）。 */
    private boolean hasDanglingRef(DialogEntry e) {
        for (String t : this.outTargets(e)) {
            if (this.sequence.findEntryById(t) == null) {
                return true;
            }
        }
        return false;
    }

    /** 从 JsonElement 提取纯文本摘要（原始字符串 / translate key / 数组拼接）。 */
    private String plainText(JsonElement element, int maxChars) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        String s = this.plainTextDeep(element);
        if (s.length() > maxChars) {
            s = s.substring(0, maxChars);
        }
        return s.trim();
    }

    private String plainTextDeep(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("translate")) {
                return obj.get("translate").getAsString();
            }
            if (obj.has("text")) {
                return obj.get("text").getAsString();
            }
            return "";
        }
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            StringBuilder sb = new StringBuilder();
            for (JsonElement child : arr) {
                sb.append(this.plainTextDeep(child)).append(' ');
            }
            return sb.toString();
        }
        return "";
    }

    /** 空状态提示（序列未加载/无节点）。 */
    private void renderEmptyStates(GuiGraphics graphics) {
        if (this.sequence == null) {
            this.drawCenteredHint(graphics, Component.translatable("gui.vn_edit.tree.no_sequence"));
        } else if (!this.hasEntries()) {
            this.drawCenteredHint(graphics, Component.translatable("gui.vn_edit.canvas.empty"));
        }
    }

    private void drawCenteredHint(GuiGraphics graphics, Component text) {
        graphics.drawCenteredString(this.font, text,
                this.getX() + this.getWidth() / 2, this.getY() + this.getHeight() / 2 - 4, EditorTheme.TEXT_MUTED);
    }

    // ===== HUD（屏幕坐标，不随画布缩放） =====

    private static final int HUD_BTN_H = 14;
    private static final int HUD_BTN_W = 60;

    private int hudBtn1X() {
        return this.getX() + this.getWidth() - HUD_BTN_W * 2 - 12;
    }

    private int hudBtn2X() {
        return this.getX() + this.getWidth() - HUD_BTN_W - 6;
    }

    private int hudBtnY() {
        return this.getY() + 5;
    }

    /** 顶部 HUD：左上缩放百分比+操作提示；右上 [自动布局][回到起点] 按钮。 */
    private void renderHud(GuiGraphics graphics, int mouseX, int mouseY) {
        // 左上：缩放 + 提示
        String zoom = Math.round(this.scale * 100) + "%";
        graphics.drawString(this.font, zoom, this.getX() + 6, this.getY() + 5, EditorTheme.ACCENT, true);
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.canvas.hint"),
                this.getX() + 6 + this.font.width(zoom) + 8, this.getY() + 5, EditorTheme.TEXT_MUTED);
        // 右上按钮
        this.renderHudButton(graphics, this.hudBtn1X(), this.hudBtnY(),
                Component.translatable("gui.vn_edit.canvas.auto_layout"), mouseX, mouseY);
        this.renderHudButton(graphics, this.hudBtn2X(), this.hudBtnY(),
                Component.translatable("gui.vn_edit.canvas.focus_start"), mouseX, mouseY);
    }

    private void renderHudButton(GuiGraphics graphics, int x, int y, Component label, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + HUD_BTN_W && mouseY >= y && mouseY < y + HUD_BTN_H;
        graphics.fill(x, y, x + HUD_BTN_W, y + HUD_BTN_H, hovered ? EditorTheme.BG_HOVER : EditorTheme.BG_ELEVATED);
        EditorRenderHelper.drawBorder(graphics, x, y, HUD_BTN_W, HUD_BTN_H, hovered ? EditorTheme.ACCENT : EditorTheme.BORDER);
        graphics.drawCenteredString(this.font, label, x + HUD_BTN_W / 2, y + 3, EditorTheme.TEXT_SECONDARY);
    }

    // ===== 右键菜单 =====

    private void openMenu(double mouseX, double mouseY, String nodeId) {
        this.menuItems.clear();
        if (nodeId != null) {
            DialogEntry entry = this.sequence.findEntryById(nodeId);
            if (entry == null) {
                return;
            }
            this.menuItems.add(new MenuItem(Component.translatable("gui.vn_edit.canvas.ctx.edit"), () -> {
                if (this.onEditProperties != null) this.onEditProperties.run();
            }));
            this.menuItems.add(new MenuItem(Component.translatable("gui.vn_edit.canvas.ctx.add_child"), () -> {
                if (this.onAddChild != null) this.onAddChild.accept(entry);
            }));
            this.menuItems.add(new MenuItem(Component.translatable("gui.vn_edit.canvas.ctx.rename"), () -> {
                if (this.onRename != null) this.onRename.accept(entry);
            }));
            this.menuItems.add(new MenuItem(Component.translatable("gui.vn_edit.canvas.ctx.copy"), () -> {
                if (this.onCopy != null) this.onCopy.run();
            }));
            this.menuItems.add(new MenuItem(Component.translatable("gui.vn_edit.canvas.ctx.delete"), () -> {
                if (this.onDelete != null) this.onDelete.accept(entry);
            }));
        } else {
            if (this.onAddNode != null) {
                this.menuItems.add(new MenuItem(Component.translatable("gui.vn_edit.canvas.ctx.add_node"), this.onAddNode));
            }
            if (this.onPaste != null) {
                this.menuItems.add(new MenuItem(Component.translatable("gui.vn_edit.canvas.ctx.paste"), this.onPaste));
            }
            this.menuItems.add(new MenuItem(Component.translatable("gui.vn_edit.canvas.auto_layout"), () -> this.autoLayout(true)));
            this.menuItems.add(new MenuItem(Component.translatable("gui.vn_edit.canvas.focus_start"), this::focusStart));
        }
        // 菜单宽度与位置（屏幕坐标，夹在组件范围内）
        int width = 90;
        for (MenuItem item : this.menuItems) {
            width = Math.max(width, this.font.width(item.label()) + 20);
        }
        int height = this.menuItems.size() * 15 + 2;
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (mx + width > this.getX() + this.getWidth() - 2) {
            mx = this.getX() + this.getWidth() - 2 - width;
        }
        if (my + height > this.getY() + this.getHeight() - 2) {
            my = this.getY() + this.getHeight() - 2 - height;
        }
        this.menuX = Math.max(this.getX() + 2, mx);
        this.menuY = Math.max(this.getY() + 2, my);
        this.menuOpen = true;
    }

    private void renderMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        int height = this.menuItems.size() * 15 + 2;
        int width = 90;
        for (MenuItem item : this.menuItems) {
            width = Math.max(width, this.font.width(item.label()) + 20);
        }
        EditorRenderHelper.fillWithShadow(graphics, this.menuX, this.menuY, width, height,
                EditorTheme.POPUP_BG, EditorTheme.SHADOW_DROP);
        EditorRenderHelper.drawBorder(graphics, this.menuX, this.menuY, width, height, EditorTheme.BORDER_LIGHT);
        for (int i = 0; i < this.menuItems.size(); i++) {
            int itemY = this.menuY + 1 + i * 15;
            boolean hovered = mouseX >= this.menuX && mouseX <= this.menuX + width
                    && mouseY >= itemY && mouseY < itemY + 15;
            if (hovered) {
                graphics.fill(this.menuX + 1, itemY, this.menuX + width - 1, itemY + 15, EditorTheme.BG_HOVER);
            }
            boolean danger = this.menuItems.get(i).label().getString().equals(
                    Component.translatable("gui.vn_edit.canvas.ctx.delete").getString());
            graphics.drawString(this.font, this.menuItems.get(i).label(), this.menuX + 8, itemY + 3,
                    danger ? EditorTheme.DANGER : hovered ? EditorTheme.TEXT_PRIMARY : EditorTheme.TEXT_SECONDARY, hovered);
        }
    }

    /** 命中菜单项并执行；返回 false 表示点击在菜单外。 */
    private boolean clickMenu(double mouseX, double mouseY) {
        int width = 90;
        for (MenuItem item : this.menuItems) {
            width = Math.max(width, this.font.width(item.label()) + 20);
        }
        if (mouseX >= this.menuX && mouseX <= this.menuX + width
                && mouseY >= this.menuY && mouseY <= this.menuY + this.menuItems.size() * 15 + 2) {
            int idx = (int) ((mouseY - this.menuY - 1) / 15);
            if (idx >= 0 && idx < this.menuItems.size()) {
                MenuItem item = this.menuItems.remove(idx);
                this.menuOpen = false;
                item.action().run();
            }
            return true;
        }
        this.menuOpen = false;
        return true; // 菜单外点击也消费（关闭菜单），不穿透
    }

    // =====================================================================
    // 命中检测
    // =====================================================================

    private String hitNode(double mouseX, double mouseY) {
        if (!this.hasEntries()) {
            return null;
        }
        float wx = this.mouseWorldX(mouseX);
        float wy = this.mouseWorldY(mouseY);
        String hit = null;
        // 后画的在上层，倒序遍历取最上层命中
        DialogEntry[] entries = this.sequence.getEntries();
        for (int i = entries.length - 1; i >= 0; i--) {
            DialogEntry e = entries[i];
            if (e == null || e.getId() == null) {
                continue;
            }
            int[] pos = this.positions.get(e.getId());
            if (pos == null) {
                continue;
            }
            int nodeH = this.nodeHeights.getOrDefault(e.getId(), BASE_NODE_H);
            if (wx >= pos[0] && wx <= pos[0] + NODE_W && wy >= pos[1] && wy <= pos[1] + nodeH) {
                hit = e.getId();
                break;
            }
        }
        return hit;
    }

    // =====================================================================
    // 鼠标/键盘交互
    // =====================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            if (this.menuOpen) {
                this.menuOpen = false;
                return true;
            }
            return false;
        }
        // 菜单展开时优先处理菜单
        if (this.menuOpen) {
            return this.clickMenu(mouseX, mouseY);
        }
        // HUD 按钮
        if (button == 0 && mouseY >= this.hudBtnY() && mouseY < this.hudBtnY() + HUD_BTN_H) {
            if (mouseX >= this.hudBtn1X() && mouseX < this.hudBtn1X() + HUD_BTN_W) {
                this.autoLayout(true);
                return true;
            }
            if (mouseX >= this.hudBtn2X() && mouseX < this.hudBtn2X() + HUD_BTN_W) {
                this.focusStart();
                return true;
            }
        }
        if (button == 1) {
            // 右键：节点/空白上下文菜单
            String nodeId = this.hitNode(mouseX, mouseY);
            if (nodeId != null) {
                this.selectNode(nodeId, this.sequence.findEntryById(nodeId));
            }
            this.openMenu(mouseX, mouseY, nodeId);
            return true;
        }
        if (button == 0 || button == 2) {
            String nodeId = button == 0 ? this.hitNode(mouseX, mouseY) : null;
            if (button == 0 && nodeId != null) {
                DialogEntry entry = this.sequence.findEntryById(nodeId);
                this.selectNode(nodeId, entry);
                // 双击判定 → 打开属性面板
                long now = System.currentTimeMillis();
                if (nodeId.equals(this.lastClickNodeId) && now - this.lastClickTime < DBLCLICK_MS) {
                    this.lastClickNodeId = null;
                    this.lastClickTime = 0L;
                    if (this.onEditProperties != null) {
                        this.onEditProperties.run();
                    }
                } else {
                    this.lastClickNodeId = nodeId;
                    this.lastClickTime = now;
                }
                // 准备节点拖拽
                int[] pos = this.positions.get(nodeId);
                this.dragMode = DragMode.NODE;
                this.dragNodeId = nodeId;
                this.dragOffsetX = (int) this.mouseWorldX(mouseX) - pos[0];
                this.dragOffsetY = (int) this.mouseWorldY(mouseY) - pos[1];
            } else {
                // 空白按下：开始平移；是否取消选中推迟到 mouseReleased 按"点击/拖拽"判定，
                // 避免只想平移画布却把选中（和停靠面板）误清掉。
                this.pressOnEmpty = button == 0;
                this.dragDist = 0f;
                this.dragMode = DragMode.PAN;
            }
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragMode == DragMode.NONE) {
            return false;
        }
        this.dragDist += Math.abs((float) (mouseX - this.lastMouseX)) + Math.abs((float) (mouseY - this.lastMouseY));
        if (this.dragMode == DragMode.PAN) {
            this.panX += (float) (mouseX - this.lastMouseX);
            this.panY += (float) (mouseY - this.lastMouseY);
        } else if (this.dragMode == DragMode.NODE && this.dragNodeId != null) {
            int[] pos = this.positions.get(this.dragNodeId);
            if (pos != null) {
                int nx = (int) this.mouseWorldX(mouseX) - this.dragOffsetX;
                int ny = (int) this.mouseWorldY(mouseY) - this.dragOffsetY;
                pos[0] = Math.round((float) nx / SNAP) * SNAP;
                pos[1] = Math.round((float) ny / SNAP) * SNAP;
            }
        }
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragMode == DragMode.NODE) {
            this.saveLayout();
        }
        // 空白"单击"（位移<4px）才取消选中；拖拽平移不清
        if (this.dragMode == DragMode.PAN && this.pressOnEmpty && this.dragDist < 4f) {
            this.clearSelection();
        }
        this.pressOnEmpty = false;
        this.dragDist = 0f;
        this.dragMode = DragMode.NONE;
        this.dragNodeId = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (Screen.hasControlDown()) {
            // Ctrl+滚轮：以鼠标为中心缩放
            this.zoomAt((float) (mouseX - this.getX()), (float) (mouseY - this.getY()),
                    scrollY > 0 ? ZOOM_STEP : 1 / ZOOM_STEP);
            return true;
        }
        // 滚轮=纵向平移，Shift=横向平移
        if (Screen.hasShiftDown()) {
            this.panX -= (float) scrollY * 30f;
        } else {
            this.panY -= (float) scrollY * 30f;
        }
        this.panX += (float) scrollX * 30f;
        return true;
    }

    /**
     * 键盘交互（由宿主在无 EditBox 聚焦时转发）：
     * 方向键=平移，+/-=缩放，Ctrl+0=重置缩放。
     */
    public boolean keyPressed(int keyCode) {
        float step = PAN_STEP / this.scale;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT:
                this.panX += step;
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                this.panX -= step;
                return true;
            case GLFW.GLFW_KEY_UP:
                this.panY += step;
                return true;
            case GLFW.GLFW_KEY_DOWN:
                this.panY -= step;
                return true;
            case GLFW.GLFW_KEY_EQUAL:
            case GLFW.GLFW_KEY_KP_ADD:
                this.zoomAt(this.getWidth() / 2f, this.getHeight() / 2f, ZOOM_STEP);
                return true;
            case GLFW.GLFW_KEY_MINUS:
            case GLFW.GLFW_KEY_KP_SUBTRACT:
                this.zoomAt(this.getWidth() / 2f, this.getHeight() / 2f, 1 / ZOOM_STEP);
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, Component.translatable("gui.vn_edit.canvas"));
    }
}

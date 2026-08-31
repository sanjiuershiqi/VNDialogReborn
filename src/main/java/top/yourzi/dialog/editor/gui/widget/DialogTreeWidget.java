package top.yourzi.dialog.editor.gui.widget;

import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.editor.gui.EditorRenderHelper;
import top.yourzi.dialog.editor.gui.EditorScreenState;
import top.yourzi.dialog.editor.gui.InputDialogScreen;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.editor.validation.DialogValidator;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;
import top.yourzi.dialog.util.ComponentJson;
import top.yourzi.dialog.editor.util.ConfigLanguageCache;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 对话树组件，按 next/options 引用关系构建层级树。融合自 visual_mod_edit_vndialog。
 */
public class DialogTreeWidget extends AbstractWidget {
    private static final int HEADER_HEIGHT = 20;
    private static final int ROW_HEIGHT = EditorTheme.TREE_ROW_H;
    private static final int INDENT_WIDTH = EditorTheme.TREE_INDENT;
    private static final int SCROLLBAR_WIDTH = EditorTheme.SCROLLBAR_W;
    private final Font font;
    private DialogSequence sequence;
    private final List<TreeNode> visibleNodes = new ArrayList<>();
    private int scrollOffset = 0;
    /** 滚动条拖拽 + 平滑滚动状态（借鉴 Sparkle OptionScreen）。 */
    private final EditorRenderHelper.ScrollState scrollState = new EditorRenderHelper.ScrollState();
    /** 上一帧纳秒时间戳，用于计算 dt 驱动平滑滚动。 */
    private long lastFrameNanos = 0L;
    private int selectedIndex = -1;
    /** 当前搜索文本（非空时 visibleNodes 仅含匹配项及其祖先链）。由外部 searchBox 通过 setSearchText 注入。 */
    private String searchText = "";
    private Consumer<DialogEntry> onEntrySelected;
    private Consumer<DialogEntry> onEntryDelete;
    private final List<TreeNode> roots = new ArrayList<>();
    private final List<TreeNode> orphans = new ArrayList<>();
    private int lastClickIndex = -1;
    private long lastClickTime = 0L;
    private final Map<String, Integer> refCounts = new HashMap<>();

    public DialogTreeWidget(int x, int y, int width, int height, Font font) {
        super(x, y, width, height, Component.empty());
        this.font = font;
    }

    public void setSequence(DialogSequence sequence) {
        this.sequence = sequence;
        this.selectedIndex = -1;
        this.scrollOffset = 0;
        this.buildTree();
        // 从状态单例回填搜索文本：搜索激活时过滤可见节点，跳过选中恢复（搜索态选中无意义）
        this.searchText = EditorScreenState.get().getTreeSearchText();
        if (this.isSearching()) {
            this.applySearch();
            return;
        }
        // 非搜索态：从状态单例恢复选中节点与滚动位置（子屏返回重建后保持状态）
        String savedId = EditorScreenState.get().getSelectedNodeId();
        if (savedId != null && this.visibleNodes != null) {
            for (int i = 0; i < this.visibleNodes.size(); i++) {
                if (savedId.equals(this.visibleNodes.get(i).entry.getId())) {
                    this.selectedIndex = i;
                    break;
                }
            }
        }
        int maxScroll = Math.max(0, this.visibleNodes.size() * ROW_HEIGHT - this.visibleHeight());
        this.scrollOffset = Mth.clamp(EditorScreenState.get().getTreeScrollOffset(), 0, maxScroll);
        // 同步 display 到恢复后的 scrollOffset，避免首帧 lerp 跳变
        this.scrollState.reset(this.scrollOffset);
    }

    public void setCallbacks(Consumer<DialogEntry> onSelect, Consumer<DialogEntry> onDelete, Consumer<DialogEntry> onAddChild) {
        this.onEntrySelected = onSelect;
        this.onEntryDelete = onDelete;
        // onAddChild 回调当前未实现，保留参数以维持 API 兼容
    }

    /**
     * 设置搜索文本并重新过滤可见节点。由外部 searchBox 的 responder 调用。
     * 空文本恢复完整树（flattenTree）；非空文本仅保留 ID 包含文本的节点及其祖先链。
     */
    public void setSearchText(String text) {
        this.searchText = text == null ? "" : text;
        this.applySearch();
    }

    /** 当前是否处于搜索态（搜索文本非空）。供 render 判断是否显示空结果提示。 */
    public boolean isSearching() {
        return this.searchText != null && !this.searchText.isEmpty();
    }

    /** 应用搜索过滤：重建 visibleNodes。搜索时清空选中并重置滚动，避免索引错位。 */
    private void applySearch() {
        if (!this.isSearching()) {
            this.flattenTree();
            return;
        }
        String q = this.searchText.toLowerCase(Locale.ROOT);
        this.visibleNodes.clear();
        for (TreeNode root : this.roots) {
            this.addFilteredNodes(root, q, this.visibleNodes);
        }
        for (TreeNode orphan : this.orphans) {
            this.addFilteredNodes(orphan, q, this.visibleNodes);
        }
        this.selectedIndex = -1;
        this.scrollOffset = 0;
        this.scrollState.reset(0);
    }

    /** 判断 node 子树中是否有匹配 q 的节点（含自身）。 */
    private boolean subtreeMatches(TreeNode node, String q) {
        if (this.entryMatches(node.entry, q)) {
            return true;
        }
        for (TreeNode child : node.children) {
            if (this.subtreeMatches(child, q)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断节点是否匹配搜索词 q（不区分大小写，包含匹配）。
     * 搜索范围：节点 ID + 说话人 + 正文 + 选项文本 + 选项目标 + 命令 + 音频路径。
     * 比"只搜 ID"实用：创作者通常记得"说了什么"而非"节点叫什么"。
     */
    private boolean entryMatches(DialogEntry entry, String q) {
        String haystack = this.searchableText(entry);
        return haystack.contains(q);
    }

    /** 拼接节点所有可搜索字段为一个小写文本串。JsonElement 用 toString 兜底提取纯文本。 */
    private String searchableText(DialogEntry entry) {
        if (entry == null) return "";
        StringBuilder sb = new StringBuilder();
        if (entry.getId() != null) sb.append(entry.getId()).append(' ');
        if (entry.getSpeaker() != null) sb.append(this.plain(entry.getSpeaker())).append(' ');
        if (entry.getText() != null) sb.append(this.plain(entry.getText())).append(' ');
        if (entry.getNextId() != null) sb.append(entry.getNextId()).append(' ');
        if (entry.getAudioPath() != null) sb.append(entry.getAudioPath()).append(' ');
        if (entry.getOptions() != null) {
            for (DialogOption opt : entry.getOptions()) {
                if (opt == null) continue;
                if (opt.getText() != null) sb.append(this.plain(opt.getText())).append(' ');
                if (opt.getTargetId() != null) sb.append(opt.getTargetId()).append(' ');
            }
        }
        if (entry.getCommands() != null) {
            for (String cmd : entry.getCommands()) {
                if (cmd != null) sb.append(cmd).append(' ');
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** 前序输出子树中"自身或后代匹配 q"的节点（含祖先链），保留原 depth 缩进。 */
    private void addFilteredNodes(TreeNode node, String q, List<TreeNode> out) {
        if (!this.subtreeMatches(node, q)) {
            return;
        }
        out.add(node);
        for (TreeNode child : node.children) {
            this.addFilteredNodes(child, q, out);
        }
    }

    /** 获取当前选中节点，无选中返回 null。供 Delete/F2 快捷键判断目标。 */
    public DialogEntry getSelectedEntry() {
        if (this.selectedIndex < 0 || this.selectedIndex >= this.visibleNodes.size()) {
            return null;
        }
        return this.visibleNodes.get(this.selectedIndex).entry;
    }

    /**
     * 按 ID 选中节点并滚动到可见。供粘贴/复制后选中新节点使用。
     * 在 setSequence 重建后调用，会复用 selectIndex 的选中+滚动+回调逻辑。
     * @return true 表示找到并选中
     */
    public boolean selectEntryById(String id) {
        if (id == null) {
            return false;
        }
        for (int i = 0; i < this.visibleNodes.size(); i++) {
            if (id.equals(this.visibleNodes.get(i).entry.getId())) {
                this.selectIndex(i);
                return true;
            }
        }
        return false;
    }

    /**
     * 键盘导航：由宿主 Screen 在 EditBox 未聚焦时转发 UP/DOWN/LEFT/RIGHT/Enter。
     * AbstractWidget 不自带 keyPressed，故公开由外部调用。
     * - UP/DOWN：移动选中并滚动到可见，触发 onEntrySelected（与单击语义一致）
     * - LEFT：当前节点有子节点且展开 → 折叠
     * - RIGHT：当前节点有子节点且折叠 → 展开
     * - Enter：触发 onEntrySelected 打开属性面板
     * @return true 表示按键被消费
     */
    public boolean keyPressed(int keyCode) {
        if (this.visibleNodes.isEmpty()) {
            return false;
        }
        // UP/DOWN：移动选中
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
            if (this.selectedIndex < 0) {
                this.selectIndex(0);
                return true;
            }
            int dir = keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP ? -1 : 1;
            int next = Mth.clamp(this.selectedIndex + dir, 0, this.visibleNodes.size() - 1);
            if (next != this.selectedIndex) {
                this.selectIndex(next);
            }
            return true;
        }
        if (this.selectedIndex < 0 || this.selectedIndex >= this.visibleNodes.size()) {
            return false;
        }
        TreeNode node = this.visibleNodes.get(this.selectedIndex);
        // LEFT：折叠当前节点（有子节点且展开时）
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
            if (!node.children.isEmpty() && node.expanded) {
                node.expanded = false;
                this.flattenTree();
                return true;
            }
            return false;
        }
        // RIGHT：展开当前节点（有子节点且折叠时）
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
            if (!node.children.isEmpty() && !node.expanded) {
                node.expanded = true;
                this.flattenTree();
                return true;
            }
            return false;
        }
        // Enter：触发选中回调（打开属性面板），与单击语义一致
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            if (this.onEntrySelected != null) {
                this.onEntrySelected.accept(node.entry);
            }
            return true;
        }
        return false;
    }

    /**
     * 设置选中索引并同步状态/回调/滚动。抽出自 mouseClicked 与 keyPressed 共用。
     */
    private void selectIndex(int index) {
        if (index < 0 || index >= this.visibleNodes.size()) {
            return;
        }
        this.selectedIndex = index;
        EditorScreenState.get().setSelectedNodeId(this.visibleNodes.get(index).entry.getId());
        this.scrollIntoView(index);
        if (this.onEntrySelected != null) {
            this.onEntrySelected.accept(this.visibleNodes.get(index).entry);
        }
    }

    /**
     * 滚动到使 index 对应行可见。若行在视口上方，上调 scrollOffset；若在下方，下调。
     */
    private void scrollIntoView(int index) {
        int rowTop = index * ROW_HEIGHT;
        int rowBottom = rowTop + ROW_HEIGHT;
        int viewTop = this.scrollOffset;
        int viewBottom = this.scrollOffset + this.visibleHeight();
        int maxScroll = Math.max(0, this.visibleNodes.size() * ROW_HEIGHT - this.visibleHeight());
        if (rowTop < viewTop) {
            this.scrollOffset = rowTop;
        } else if (rowBottom > viewBottom) {
            this.scrollOffset = rowBottom - this.visibleHeight();
        }
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll);
        EditorScreenState.get().setTreeScrollOffset(this.scrollOffset);
    }

    /**
     * 重命名当前选中节点。由双击重命名和 F2 快捷键共用。
     * 含 ID 唯一性校验、引用更新（nextId/options.targetId）、重建树、恢复选中。
     * @return true 表示成功（含新旧 ID 相同的无变化情况）；false 表示 ID 冲突或无选中
     */
    public boolean renameSelectedEntry(String newId) {
        if (this.selectedIndex < 0 || this.selectedIndex >= this.visibleNodes.size()) {
            return false;
        }
        TreeNode node = this.visibleNodes.get(this.selectedIndex);
        String oldId = node.entry.getId();
        if (oldId.equals(newId)) {
            return true;
        }
        if (this.sequence.findEntryById(newId) != null) {
            return false;
        }
        node.entry.setId(newId);
        if (this.sequence.getEntries() != null) {
            for (DialogEntry e : this.sequence.getEntries()) {
                DialogOption[] options;
                if (oldId.equals(e.getNextId())) {
                    e.setNextId(newId);
                }
                if ((options = e.getOptions()) == null) {
                    continue;
                }
                for (DialogOption opt : options) {
                    if (!oldId.equals(opt.getTargetId())) {
                        continue;
                    }
                    opt.setTargetId(newId);
                }
            }
        }
        this.buildTree();
        this.selectedIndex = this.visibleNodes.indexOf(this.visibleNodes.stream()
                .filter(n -> n.entry.getId().equals(newId)).findFirst().orElse(null));
        EditorScreenState.get().setSelectedNodeId(newId);
        if (this.onEntrySelected != null) {
            this.onEntrySelected.accept(node.entry);
        }
        return true;
    }

    private void buildTree() {
        this.roots.clear();
        this.orphans.clear();
        this.visibleNodes.clear();
        this.refCounts.clear();
        if (this.sequence == null || this.sequence.getEntries() == null) {
            return;
        }
        for (DialogEntry entry : this.sequence.getEntries()) {
            DialogOption[] options;
            String nextId = entry.getNextId();
            if (nextId != null && !nextId.isEmpty()) {
                this.refCounts.put(nextId, this.refCounts.getOrDefault(nextId, 0) + 1);
            }
            if ((options = entry.getOptions()) == null) {
                continue;
            }
            for (DialogOption opt : options) {
                String target = opt.getTargetId();
                if (target == null || target.isEmpty()) {
                    continue;
                }
                this.refCounts.put(target, this.refCounts.getOrDefault(target, 0) + 1);
            }
        }
        HashSet<String> visited = new HashSet<>();
        ArrayDeque<TreeNode> queue = new ArrayDeque<>();
        DialogEntry start = this.sequence.getFirstEntry();
        if (start != null) {
            TreeNode root = new TreeNode(start, null, 0);
            this.roots.add(root);
            queue.add(root);
            visited.add(start.getId());
        }
        while (!queue.isEmpty()) {
            DialogOption[] options;
            DialogEntry nextEntry;
            TreeNode node = queue.poll();
            DialogEntry entry = node.entry;
            String nextId = entry.getNextId();
            if (nextId != null && !nextId.isEmpty() && !visited.contains(nextId)
                    && (nextEntry = this.sequence.findEntryById(nextId)) != null) {
                visited.add(nextId);
                TreeNode child = new TreeNode(nextEntry, node, node.depth + 1);
                node.children.add(child);
                queue.add(child);
            }
            if ((options = entry.getOptions()) == null) {
                continue;
            }
            for (DialogOption opt : options) {
                DialogEntry targetEntry;
                String target = opt.getTargetId();
                if (target == null || target.isEmpty() || visited.contains(target)
                        || (targetEntry = this.sequence.findEntryById(target)) == null) {
                    continue;
                }
                visited.add(target);
                TreeNode child = new TreeNode(targetEntry, node, node.depth + 1);
                node.children.add(child);
                queue.add(child);
            }
        }
        for (DialogEntry entry : this.sequence.getEntries()) {
            if (visited.contains(entry.getId())) {
                continue;
            }
            TreeNode orphan = new TreeNode(entry, null, 0);
            orphan.isOrphan = true;
            this.orphans.add(orphan);
        }
        this.flattenTree();
    }

    private void flattenTree() {
        this.visibleNodes.clear();
        for (TreeNode root : this.roots) {
            this.addNodeAndVisibleChildren(root);
        }
        this.visibleNodes.addAll(this.orphans);
    }

    private void addNodeAndVisibleChildren(TreeNode node) {
        this.visibleNodes.add(node);
        if (node.expanded) {
            for (TreeNode child : node.children) {
                this.addNodeAndVisibleChildren(child);
            }
        }
    }

    private String getTypeIcon(DialogEntry entry) {
        if (entry.isEndDialog()) {
            return "\u2297";
        }
        if (entry.getOptions() != null && entry.getOptions().length > 0) {
            return "\u25c6";
        }
        return "\u25cb";
    }

    private String getConnectionInfo(DialogEntry entry) {
        DialogOption[] options;
        StringBuilder sb = new StringBuilder();
        if (entry.getNextId() != null && !entry.getNextId().isEmpty()) {
            sb.append("\u2192 ").append(entry.getNextId());
        }
        if ((options = entry.getOptions()) != null && options.length > 0) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append("[");
            for (int i = 0; i < options.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(options[i].getTargetId() != null ? options[i].getTargetId() : "?");
            }
            sb.append("]");
        }
        return sb.toString();
    }

    private String plain(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "";
        }
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        if (value.isJsonObject() && value.getAsJsonObject().has("translate")) {
            String key = value.getAsJsonObject().get("translate").getAsString();
            String translated = ConfigLanguageCache.get(key);
            return translated != null ? translated : Component.translatable(key).getString();
        }
        try {
            return ComponentJson.fromJson(value).getString();
        } catch (Exception ignored) {
            return value.toString();
        }
    }

    private String summary(DialogEntry entry) {
        String speaker = plain(entry.getSpeaker()).replace('\n', ' ').trim();
        String text = plain(entry.getText()).replace('\n', ' ').trim();
        if (speaker.isEmpty()) {
            speaker = "-";
        }
        if (text.isEmpty()) {
            text = Component.translatable("gui.vn_edit.tree.empty_text").getString();
        }
        return speaker + "  |  " + text;
    }

    private int visibleHeight() {
        return Math.max(1, this.getHeight() - HEADER_HEIGHT);
    }

    private Map<String, DialogValidator.Severity> validationSeverity() {
        Map<String, DialogValidator.Severity> result = new HashMap<>();
        if (this.sequence == null) {
            return result;
        }
        for (DialogValidator.Issue issue : DialogValidator.validate(this.sequence)) {
            if (issue.nodeId() == null) {
                continue;
            }
            DialogValidator.Severity previous = result.get(issue.nodeId());
            if (previous == DialogValidator.Severity.ERROR || issue.severity() == DialogValidator.Severity.ERROR) {
                result.put(issue.nodeId(), DialogValidator.Severity.ERROR);
            } else {
                result.put(issue.nodeId(), DialogValidator.Severity.WARNING);
            }
        }
        return result;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), EditorTheme.BG_SURFACE);
        int nodeAreaTop = this.getY() + HEADER_HEIGHT;
        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), nodeAreaTop, EditorTheme.BG_ELEVATED);
        int totalNodes = this.sequence == null || this.sequence.getEntries() == null ? 0 : this.sequence.getEntries().length;
        int choices = 0;
        if (this.sequence != null && this.sequence.getEntries() != null) {
            for (DialogEntry entry : this.sequence.getEntries()) {
                if (entry != null && entry.hasOptions()) choices++;
            }
        }
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.tree.header", totalNodes, choices, this.orphans.size()),
                this.getX() + 7, this.getY() + 4, EditorTheme.TEXT_WARM, true);
        graphics.enableScissor(this.getX(), nodeAreaTop, this.getX() + this.getWidth(), this.getY() + this.getHeight());
        try {
        int maxScroll = Math.max(0, this.visibleNodes.size() * ROW_HEIGHT - this.visibleHeight());
        if (this.scrollOffset > maxScroll) {
            this.scrollOffset = maxScroll;
        }
        if (this.scrollOffset < 0) {
            this.scrollOffset = 0;
        }
        // 计算 dt 驱动平滑滚动（首帧 lastFrameNanos=0 直接吸附）
        long now = System.nanoTime();
        float dt = this.lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - this.lastFrameNanos) / 1.0e9f);
        this.lastFrameNanos = now;
        int displayOffset = this.scrollState.tick(this.scrollOffset, dt);
        int yOffset = nodeAreaTop - displayOffset;
        Map<String, DialogValidator.Severity> severityById = this.validationSeverity();
        for (int i = 0; i < this.visibleNodes.size(); i++) {
            TreeNode node = this.visibleNodes.get(i);
            int rowY = yOffset + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < nodeAreaTop || rowY > this.getY() + this.getHeight()) {
                continue;
            }
            int indent = node.depth * INDENT_WIDTH;
            boolean hovered = mouseX >= this.getX() && mouseX <= this.getX() + this.getWidth()
                    && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
            if (hovered) {
                // 第八轮美化：hover 用 HOVER_TINT 半透明提亮叠层（借鉴 Sparkle blendBg），比实色 BG_HOVER 柔和
                graphics.fill(this.getX(), rowY, this.getX() + this.getWidth(), rowY + ROW_HEIGHT, EditorTheme.HOVER_TINT);
            }
            boolean isSelected = (i == this.selectedIndex);
            if (isSelected) {
                graphics.fill(this.getX(), rowY, this.getX() + this.getWidth(), rowY + ROW_HEIGHT, EditorTheme.BG_SELECTED);
                // 选中项左侧 2px 强调色竖条（VS Code 活动标签风格），增强视觉锚点
                graphics.fill(this.getX(), rowY, this.getX() + 2, rowY + ROW_HEIGHT, EditorTheme.ACCENT);
            }
            // 选中项文字提亮为纯白，hover/普通保持次要色
            int textColor = isSelected ? EditorTheme.TEXT_PRIMARY : EditorTheme.TEXT_SECONDARY;
            String arrow = !node.children.isEmpty() ? (node.expanded ? "\u25bc " : "\u25b6 ") : "  ";
            String icon = this.getTypeIcon(node.entry);
            int refs = this.refCounts.getOrDefault(node.entry.getId(), 0);
            String refMarker = refs > 1 ? "*" : "";
            String idText = arrow + (node.isOrphan ? "\u26a0 " : "") + icon + " "
                    + (node.entry.getId() == null ? "untitled" : node.entry.getId()) + refMarker;
            // 第八轮美化：选中项文字加阴影（项 9），强化视觉锚点
            int textX = this.getX() + 5 + indent;
            int textRight = this.getX() + this.getWidth() - 8;
            int available = Math.max(30, textRight - textX);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(idText, available), textX, rowY + 3, textColor, isSelected);
            String summary = this.font.plainSubstrByWidth(this.summary(node.entry), available);
            graphics.drawString(this.font, summary, textX, rowY + 16,
                    node.isOrphan ? EditorTheme.STATUS_WARNING : EditorTheme.TEXT_MUTED);
            graphics.fill(this.getX() + 5, rowY + ROW_HEIGHT - 1, this.getX() + this.getWidth() - 8,
                    rowY + ROW_HEIGHT, EditorTheme.DIVIDER);
            DialogValidator.Severity severity = severityById.get(node.entry.getId());
            if (severity != null) {
                int badgeColor = severity == DialogValidator.Severity.ERROR ? EditorTheme.STATUS_ERROR : EditorTheme.STATUS_WARNING;
                graphics.fill(this.getX() + this.getWidth() - 8, rowY + 5, this.getX() + this.getWidth() - 4, rowY + 9, badgeColor);
            }
        }
        // 空状态：序列未加载或无节点时显示引导（借鉴 Sparkle 三态列表，但不引入加载/错误态避免过度设计）
        if (this.sequence == null) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.vn_edit.tree.no_sequence"),
                    this.getX() + this.getWidth() / 2, this.getY() + this.getHeight() / 2 - 4, EditorTheme.TEXT_MUTED);
        } else if (this.visibleNodes.isEmpty() && !this.isSearching()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.vn_edit.tree.empty"),
                    this.getX() + this.getWidth() / 2, this.getY() + this.getHeight() / 2 - 4, EditorTheme.TEXT_MUTED);
        }
        // 搜索无匹配：居中显示提示（借鉴 Sparkle 三态列表的 no_results）
        if (this.isSearching() && this.visibleNodes.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.vn_edit.search_no_result"),
                    this.getX() + this.getWidth() / 2, this.getY() + this.getHeight() / 2 - 4, EditorTheme.TEXT_SECONDARY);
        }
        if (this.visibleNodes.size() * ROW_HEIGHT > this.visibleHeight()) {
            int scrollBarHeight = Math.max(10, this.visibleHeight() * this.visibleHeight() / (this.visibleNodes.size() * ROW_HEIGHT));
            int scrollBarY = nodeAreaTop + (int) ((float) displayOffset / (float) (this.visibleNodes.size() * ROW_HEIGHT - this.visibleHeight()) * (float) (this.visibleHeight() - scrollBarHeight));
            graphics.fill(this.getX() + this.getWidth() - SCROLLBAR_WIDTH, nodeAreaTop, this.getX() + this.getWidth(), this.getY() + this.getHeight(), EditorTheme.SCROLLBAR_TRACK);
            int thumbColor = this.scrollState.dragging ? EditorTheme.TEXT_PRIMARY : EditorTheme.SCROLLBAR_THUMB;
            graphics.fill(this.getX() + this.getWidth() - SCROLLBAR_WIDTH, scrollBarY, this.getX() + this.getWidth(), scrollBarY + scrollBarHeight, thumbColor);
        }
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        // 滚动条命中：开始拖拽并立即跳到点击位置（优先于节点命中，避免误选节点）
        int totalH = this.visibleNodes.size() * ROW_HEIGHT;
        int maxScroll = Math.max(0, totalH - this.visibleHeight());
        if (maxScroll > 0 && button == 0) {
            int trackX = this.getX() + this.getWidth() - SCROLLBAR_WIDTH;
            int nodeAreaTop = this.getY() + HEADER_HEIGHT;
            if (EditorRenderHelper.isOnVerticalScrollbar(mouseX, mouseY, trackX, nodeAreaTop, SCROLLBAR_WIDTH, this.visibleHeight())) {
                this.scrollState.dragging = true;
                this.scrollOffset = EditorRenderHelper.offsetFromMouseY(mouseY, nodeAreaTop, nodeAreaTop + this.visibleHeight(), maxScroll);
                return true;
            }
        }
        int relY = (int) mouseY - this.getY() - HEADER_HEIGHT + this.scrollOffset;
        if (relY < 0) {
            return true;
        }
        int index = relY / ROW_HEIGHT;
        if (index >= 0 && index < this.visibleNodes.size()) {
            TreeNode node = this.visibleNodes.get(index);
            if (button == 0) {
                long now = System.currentTimeMillis();
                if (this.lastClickIndex == index && now - this.lastClickTime < 500L) {
                    // 双击重命名：复用 renameSelectedEntry（与 F2 快捷键共用），selectedIndex 已由首次单击设为 index
                    DialogEntry entryToRename = node.entry;
                    Minecraft.getInstance().setScreen(new InputDialogScreen(Component.translatable("gui.vn_edit.rename.title"), entryToRename.getId(), newId -> {
                        this.renameSelectedEntry(newId);
                    }, Minecraft.getInstance().screen));
                    this.lastClickIndex = -1;
                    return true;
                }
                this.lastClickIndex = index;
                this.lastClickTime = now;
                int indent = node.depth * INDENT_WIDTH;
                int arrowX = this.getX() + 4 + indent;
                if (mouseX >= arrowX && mouseX <= arrowX + 10 && !node.children.isEmpty()) {
                    node.expanded = !node.expanded;
                    this.flattenTree();
                    return true;
                }
                this.selectedIndex = index;
                EditorScreenState.get().setSelectedNodeId(node.entry.getId());
                if (this.onEntrySelected != null) {
                    this.onEntrySelected.accept(node.entry);
                }
                return true;
            }
            if (button == 1) {
                if (this.onEntryDelete != null) {
                    this.onEntryDelete.accept(node.entry);
                }
                return true;
            }
        } else if (button == 0) {
            this.selectedIndex = -1;
            EditorScreenState.get().setSelectedNodeId(null);
            if (this.onEntrySelected != null) {
                this.onEntrySelected.accept(null);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        this.scrollOffset = Mth.clamp(this.scrollOffset - (int) scrollY * ROW_HEIGHT, 0,
                Math.max(0, this.visibleNodes.size() * ROW_HEIGHT - this.visibleHeight()));
        EditorScreenState.get().setTreeScrollOffset(this.scrollOffset);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (this.scrollState.dragging) {
            int maxScroll = Math.max(0, this.visibleNodes.size() * ROW_HEIGHT - this.visibleHeight());
            int nodeAreaTop = this.getY() + HEADER_HEIGHT;
            this.scrollOffset = EditorRenderHelper.offsetFromMouseY(mouseY, nodeAreaTop, nodeAreaTop + this.visibleHeight(), maxScroll);
            EditorScreenState.get().setTreeScrollOffset(this.scrollOffset);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.scrollState.dragging) {
            this.scrollState.dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, Component.translatable("gui.vn_edit.tree"));
    }
}

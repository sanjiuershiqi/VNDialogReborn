package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.editor.gui.InputDialogScreen;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 对话树组件，按 next/options 引用关系构建层级树。融合自 visual_mod_edit_vndialog。
 */
public class DialogTreeWidget extends AbstractWidget {
    private static final int ROW_HEIGHT = EditorTheme.TREE_ROW_H;
    private static final int INDENT_WIDTH = EditorTheme.TREE_INDENT;
    private static final int SCROLLBAR_WIDTH = EditorTheme.SCROLLBAR_W;
    private final Font font;
    private DialogSequence sequence;
    private final List<TreeNode> visibleNodes = new ArrayList<>();
    private int scrollOffset = 0;
    private int selectedIndex = -1;
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
    }

    public void setCallbacks(Consumer<DialogEntry> onSelect, Consumer<DialogEntry> onDelete, Consumer<DialogEntry> onAddChild) {
        this.onEntrySelected = onSelect;
        this.onEntryDelete = onDelete;
        // onAddChild 回调当前未实现，保留参数以维持 API 兼容
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

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), EditorTheme.BG_SURFACE);
        graphics.enableScissor(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight());
        try {
        int maxScroll = Math.max(0, this.visibleNodes.size() * ROW_HEIGHT - this.getHeight());
        if (this.scrollOffset > maxScroll) {
            this.scrollOffset = maxScroll;
        }
        if (this.scrollOffset < 0) {
            this.scrollOffset = 0;
        }
        int yOffset = this.getY() - this.scrollOffset;
        for (int i = 0; i < this.visibleNodes.size(); i++) {
            TreeNode node = this.visibleNodes.get(i);
            int rowY = yOffset + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < this.getY() || rowY > this.getY() + this.getHeight()) {
                continue;
            }
            int indent = node.depth * INDENT_WIDTH;
            boolean hovered = mouseX >= this.getX() && mouseX <= this.getX() + this.getWidth()
                    && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
            if (hovered) {
                graphics.fill(this.getX(), rowY, this.getX() + this.getWidth(), rowY + ROW_HEIGHT, EditorTheme.BG_HOVER);
            }
            if (i == this.selectedIndex) {
                graphics.fill(this.getX(), rowY, this.getX() + this.getWidth(), rowY + ROW_HEIGHT, EditorTheme.BG_SELECTED);
            }
            int textColor = EditorTheme.TEXT_SECONDARY;
            String arrow = !node.children.isEmpty() ? (node.expanded ? "\u25bc " : "\u25b6 ") : "  ";
            String icon = this.getTypeIcon(node.entry);
            int refs = this.refCounts.getOrDefault(node.entry.getId(), 0);
            String refMarker = refs > 1 ? "*" : "";
            String idText = arrow + (node.isOrphan ? "\u26a0 " : "") + icon + " " + node.entry.getId() + refMarker;
            graphics.drawString(this.font, idText, this.getX() + 4 + indent, rowY + 2, textColor);
            String connectionInfo = this.getConnectionInfo(node.entry);
            if (connectionInfo.isEmpty()) {
                continue;
            }
            int infoWidth = this.font.width(connectionInfo);
            int infoX = this.getX() + this.getWidth() - infoWidth - 6;
            graphics.drawString(this.font, connectionInfo, infoX, rowY + 2, EditorTheme.TEXT_MUTED);
        }
        if (this.visibleNodes.size() * ROW_HEIGHT > this.getHeight()) {
            int scrollBarHeight = Math.max(10, this.getHeight() * this.getHeight() / (this.visibleNodes.size() * ROW_HEIGHT));
            int scrollBarY = this.getY() + (int) ((float) this.scrollOffset / (float) (this.visibleNodes.size() * ROW_HEIGHT - this.getHeight()) * (float) (this.getHeight() - scrollBarHeight));
            graphics.fill(this.getX() + this.getWidth() - SCROLLBAR_WIDTH, this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0x33000000);
            graphics.fill(this.getX() + this.getWidth() - SCROLLBAR_WIDTH, scrollBarY, this.getX() + this.getWidth(), scrollBarY + scrollBarHeight, EditorTheme.TEXT_MUTED);
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
        int relY = (int) mouseY - this.getY() + this.scrollOffset;
        int index = relY / ROW_HEIGHT;
        if (index >= 0 && index < this.visibleNodes.size()) {
            TreeNode node = this.visibleNodes.get(index);
            if (button == 0) {
                long now = System.currentTimeMillis();
                if (this.lastClickIndex == index && now - this.lastClickTime < 500L) {
                    Minecraft.getInstance().setScreen(new InputDialogScreen(Component.translatable("gui.vn_edit.rename.title"), node.entry.getId(), newId -> {
                        String oldId = node.entry.getId();
                        if (oldId.equals(newId)) {
                            return;
                        }
                        if (this.sequence.findEntryById(newId) != null) {
                            Dialog.LOGGER.warn("Rename failed: ID '{}' already exists", newId);
                            return;
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
                        if (this.onEntrySelected != null) {
                            this.onEntrySelected.accept(node.entry);
                        }
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
                Math.max(0, this.visibleNodes.size() * ROW_HEIGHT - this.getHeight()));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, Component.translatable("gui.vn_edit.tree"));
    }
}

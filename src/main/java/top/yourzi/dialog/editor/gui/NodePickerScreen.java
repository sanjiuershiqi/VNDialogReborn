package top.yourzi.dialog.editor.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import top.yourzi.dialog.editor.gui.widget.EditorButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 节点选择屏幕：在对话序列中挑选下一个节点 ID。融合自 visual_mod_edit_vndialog。
 */
public class NodePickerScreen extends Screen {
    private static final int ROW_HEIGHT = 14;
    private static final int LIST_TOP = 30;
    private static final int LIST_BOTTOM = 40;
    /** 搜索文本跨屏持久化（NodePicker 是临时屏，静态字段足够，不污染主编辑器状态）。 */
    private static String lastSearchText = "";

    private final DialogSequence sequence;
    private final Consumer<String> onSelected;
    private final Screen parent;
    private final List<String> nodeIds = new ArrayList<>();
    /** 经搜索过滤后的节点 ID 列表，render/mouseClicked 均基于此。 */
    private final List<String> filteredIds = new ArrayList<>();
    private EditBox searchBox;
    private int scrollOffset = 0;
    /** 键盘焦点行索引（与鼠标 hover 区分）。UP/DOWN 移动，Enter 确认。 */
    private int focusedIndex = -1;

    public NodePickerScreen(DialogSequence sequence, Consumer<String> onSelected, Screen parent) {
        super(Component.translatable("gui.vn_edit.node_picker.title"));
        this.sequence = sequence;
        this.onSelected = onSelected;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.nodeIds.clear();
        if (this.sequence != null && this.sequence.getEntries() != null) {
            for (DialogEntry e : this.sequence.getEntries()) {
                this.nodeIds.add(e.getId());
            }
        }
        // 搜索框：居中，宽 200，过滤节点列表
        this.searchBox = new EditBox(this.font, this.width / 2 - 100, 8, 200, 16, Component.translatable("gui.vn_edit.search"));
        this.searchBox.setMaxLength(999999999);
        this.searchBox.setHint(Component.translatable("gui.vn_edit.search_hint"));
        this.searchBox.setResponder(text -> { lastSearchText = text; this.applyFilter(); });
        // silent 回填初值，避免触发 responder 重复过滤
        this.searchBox.setResponder(null);
        this.searchBox.setValue(lastSearchText);
        this.searchBox.setResponder(text -> { lastSearchText = text; this.applyFilter(); });
        this.addRenderableWidget(this.searchBox);
        this.applyFilter();
        // 顶部右侧"清空选择"按钮：用于把已设置的下一节点清回 None
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.clear_selection"), btn -> {
            this.onSelected.accept("");
            this.onClose();
        }).bounds(this.width - 90, 8, 80, 18).build());
        this.addRenderableWidget(EditorButton.builder(Component.translatable("gui.vn_edit.cancel"), btn -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    /** 按搜索文本过滤 nodeIds 到 filteredIds（不区分大小写包含匹配）。 */
    private void applyFilter() {
        this.filteredIds.clear();
        String q = lastSearchText == null ? "" : lastSearchText.toLowerCase(Locale.ROOT);
        for (String id : this.nodeIds) {
            if (q.isEmpty() || (id != null && id.toLowerCase(Locale.ROOT).contains(q))) {
                this.filteredIds.add(id);
            }
        }
        this.scrollOffset = 0;
        // 过滤后列表变化，清空键盘焦点避免索引错位
        this.focusedIndex = -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, EditorTheme.TEXT_PRIMARY);
        int listHeight = this.height - LIST_TOP - LIST_BOTTOM;
        graphics.enableScissor(0, LIST_TOP, this.width, LIST_TOP + listHeight);
        int yOffset = LIST_TOP - this.scrollOffset;
        for (int i = 0; i < this.filteredIds.size(); i++) {
            int rowY = yOffset + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < LIST_TOP || rowY > LIST_TOP + listHeight) {
                continue;
            }
            boolean hovered = mouseX >= 20 && mouseX <= this.width - 20 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
            // 键盘焦点行：画选中背景 + 左侧 ACCENT 竖条（与 DialogTreeWidget 选中风格一致）
            if (!hovered && i == this.focusedIndex) {
                graphics.fill(20, rowY, this.width - 20, rowY + ROW_HEIGHT, EditorTheme.BG_SELECTED);
                graphics.fill(20, rowY, 22, rowY + ROW_HEIGHT, EditorTheme.ACCENT);
            }
            int color = hovered ? EditorTheme.ACCENT : EditorTheme.TEXT_PRIMARY;
            if (hovered) {
                graphics.fill(20, rowY, this.width - 20, rowY + ROW_HEIGHT, EditorTheme.BG_HOVER);
            }
            graphics.drawString(this.font, this.filteredIds.get(i), 25, rowY + 2, color);
        }
        graphics.disableScissor();
        // 搜索无匹配：居中显示提示
        if (this.filteredIds.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.vn_edit.search_no_result"),
                    this.width / 2, LIST_TOP + listHeight / 2 - 4, EditorTheme.TEXT_SECONDARY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listHeight = this.height - LIST_TOP - LIST_BOTTOM;
            if (mouseY >= LIST_TOP && mouseY <= LIST_TOP + listHeight) {
                int index = (int) ((mouseY - LIST_TOP + this.scrollOffset) / ROW_HEIGHT);
                if (index >= 0 && index < this.filteredIds.size()) {
                    this.onSelected.accept(this.filteredIds.get(index));
                    this.onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollOffset = Math.max(0, this.scrollOffset - (int) scrollY * ROW_HEIGHT);
        int maxScroll = Math.max(0, this.filteredIds.size() * ROW_HEIGHT - (this.height - LIST_TOP - LIST_BOTTOM));
        if (this.scrollOffset > maxScroll) {
            this.scrollOffset = maxScroll;
        }
        return true;
    }

    /**
     * 键盘导航：UP/DOWN 移动焦点行并滚动可见，Enter 确认选择，Esc 关闭。
     * 单行 EditBox 不消费 UP/DOWN，故即便搜索框聚焦也能导航列表，搜索框保持聚焦可继续输入。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (this.filteredIds.isEmpty()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
            if (this.focusedIndex < 0) {
                this.focusedIndex = 0;
            } else {
                int dir = keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP ? -1 : 1;
                this.focusedIndex = Math.max(0, Math.min(this.filteredIds.size() - 1, this.focusedIndex + dir));
            }
            this.scrollFocusedIntoView();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && this.focusedIndex >= 0 && this.focusedIndex < this.filteredIds.size()) {
            this.onSelected.accept(this.filteredIds.get(this.focusedIndex));
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 滚动使 focusedIndex 行可见。 */
    private void scrollFocusedIntoView() {
        int listHeight = this.height - LIST_TOP - LIST_BOTTOM;
        int rowTop = LIST_TOP + this.focusedIndex * ROW_HEIGHT - this.scrollOffset;
        int rowBottom = rowTop + ROW_HEIGHT;
        if (rowTop < LIST_TOP) {
            this.scrollOffset -= (LIST_TOP - rowTop);
        } else if (rowBottom > LIST_TOP + listHeight) {
            this.scrollOffset += (rowBottom - (LIST_TOP + listHeight));
        }
        int maxScroll = Math.max(0, this.filteredIds.size() * ROW_HEIGHT - listHeight);
        this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, EditorTheme.BG_DEEPEST);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}

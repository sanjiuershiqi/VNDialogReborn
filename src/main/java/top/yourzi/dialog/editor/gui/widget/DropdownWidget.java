package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.yourzi.dialog.editor.util.EditorTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 下拉列表控件。移植自 visual_mod_edit_vndialog，适配 NeoForge 1.21.1。
 * 渲染分两阶段：renderWidget 只画按钮条，renderPopup 画展开列表。
 * 父容器需在所有其他控件之后调用 renderPopup，避免展开列表被遮挡或被 scissor 裁剪。
 */
public class DropdownWidget extends AbstractWidget {
    private final Font font;
    private List<String> items = new ArrayList<>();
    private int selectedIndex = -1;
    private boolean expanded = false;
    private final Consumer<String> onSelected;
    private static final int MAX_VISIBLE = 8;
    /** 当前实例的最大可见项数，默认 MAX_VISIBLE，可通过 setMaxVisible 调整。 */
    private int maxVisible = MAX_VISIBLE;
    private int scrollOffset = 0;
    private static final int ITEM_HEIGHT = 12;
    /** 弹出列表是否向上展开（用于避免覆盖下方的输入框等控件）。 */
    private boolean popupAbove = false;

    public DropdownWidget(Font font, int x, int y, int width, int height, List<String> items, Consumer<String> onSelected) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.items = items;
        this.onSelected = onSelected;
    }

    /** 设置最大可见项数，用于选项数超过默认值 8 的场景（如 9 项动画列表需显示全部）。 */
    public void setMaxVisible(int max) {
        this.maxVisible = Math.max(1, max);
    }

    /** 设置弹出方向：true=向上展开（适合下方有其他控件的场景），false=向下展开（默认）。 */
    public void setPopupAbove(boolean above) {
        this.popupAbove = above;
    }

    public void setItems(List<String> items) {
        this.items = items != null ? items : new ArrayList<>();
        this.selectedIndex = -1;
        this.scrollOffset = 0;
    }

    public String getSelected() {
        return this.selectedIndex >= 0 && this.selectedIndex < this.items.size() ? this.items.get(this.selectedIndex) : "";
    }

    public void setSelected(String value) {
        if (this.items != null && value != null) {
            this.selectedIndex = this.items.indexOf(value);
        }
    }

    public List<String> getItems() {
        return this.items;
    }

    public boolean isExpanded() {
        return this.expanded;
    }

    public void close() {
        this.expanded = false;
    }

    /** 弹出列表顶部 Y 坐标（含边框）。向下展开=按钮底部；向上展开=按钮顶部-列表高度。 */
    private int getPopupTop() {
        int visibleCount = Math.min(this.maxVisible, this.items.size());
        int totalHeight = visibleCount * ITEM_HEIGHT + 2;
        return this.popupAbove ? this.getY() - totalHeight : this.getY() + this.getHeight();
    }

    /** 弹出列表底部 Y 坐标（含边框）。 */
    private int getPopupBottom() {
        int visibleCount = Math.min(this.maxVisible, this.items.size());
        int totalHeight = visibleCount * ITEM_HEIGHT + 2;
        return this.getPopupTop() + totalHeight;
    }

    /**
     * 只渲染按钮条（折叠状态）。展开的列表由 renderPopup 单独渲染。
     */
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), EditorTheme.BG_ELEVATED);
        if (this.isHovered()) {
            graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), EditorTheme.BG_HOVER);
        }
        String text = this.selectedIndex >= 0 ? this.items.get(this.selectedIndex) : "";
        if (text.length() > 20) {
            text = text.substring(0, 17) + "...";
        }
        graphics.drawString(this.font, text, this.getX() + 3, this.getY() + (this.getHeight() - 8) / 2, EditorTheme.TEXT_PRIMARY);
        graphics.drawString(this.font, this.expanded ? "\u25b2" : "\u25bc", this.getX() + this.getWidth() - 10, this.getY() + (this.getHeight() - 8) / 2, EditorTheme.TEXT_MUTED);
    }

    /**
     * 渲染展开的弹出列表。应在所有其他控件渲染之后调用，且在 scissor 之外。
     */
    public void renderPopup(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.expanded || !this.visible || this.items.isEmpty()) {
            return;
        }
        int dropY = this.getPopupTop();
        int dropBottom = this.getPopupBottom();
        int visibleCount = Math.min(this.maxVisible, this.items.size());
        int totalHeight = visibleCount * ITEM_HEIGHT;
        // 完全不透明背景，确保下方控件/文字不会透出
        graphics.fill(this.getX(), dropY, this.getX() + this.getWidth(), dropBottom, 0xFF181818);
        // 边框
        graphics.fill(this.getX(), dropY, this.getX() + this.getWidth(), dropY + 1, EditorTheme.BORDER);
        graphics.fill(this.getX(), dropBottom - 1, this.getX() + this.getWidth(), dropBottom, EditorTheme.BORDER);
        graphics.fill(this.getX(), dropY, this.getX() + 1, dropBottom, EditorTheme.BORDER);
        graphics.fill(this.getX() + this.getWidth() - 1, dropY, this.getX() + this.getWidth(), dropBottom, EditorTheme.BORDER);

        graphics.enableScissor(this.getX(), dropY, this.getX() + this.getWidth(), dropBottom);
        try {
            for (int i = 0; i < this.items.size(); i++) {
                int rowY = dropY + 1 + (i - this.scrollOffset) * ITEM_HEIGHT;
                if (rowY + ITEM_HEIGHT < dropY || rowY > dropBottom) {
                    continue;
                }
                boolean hovered = mouseX >= this.getX() && mouseX <= this.getX() + this.getWidth() && mouseY >= rowY && mouseY <= rowY + ITEM_HEIGHT;
                int bg = hovered ? EditorTheme.BG_HOVER : (i == this.selectedIndex ? EditorTheme.BG_SELECTED : EditorTheme.BG_SURFACE);
                graphics.fill(this.getX() + 1, rowY, this.getX() + this.getWidth() - 1, rowY + ITEM_HEIGHT, bg);
                String itemText = this.items.get(i);
                if (itemText.length() > 22) {
                    itemText = itemText.substring(0, 19) + "...";
                }
                int textColor = hovered ? EditorTheme.TEXT_PRIMARY : EditorTheme.TEXT_SECONDARY;
                graphics.drawString(this.font, itemText, this.getX() + 3, rowY + 2, textColor);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.active) {
            return false;
        }
        if (button != 0) {
            return false;
        }
        // 检查是否点击在按钮条上
        boolean onButton = mouseX >= this.getX() && mouseX <= this.getX() + this.getWidth()
                && mouseY >= this.getY() && mouseY <= this.getY() + this.getHeight();
        if (this.expanded) {
            int dropY = this.getPopupTop();
            int dropBottom = this.getPopupBottom();
            // 检查是否点击在弹出列表区域
            boolean onPopup = mouseX >= this.getX() && mouseX <= this.getX() + this.getWidth()
                    && mouseY >= dropY && mouseY <= dropBottom;
            if (onPopup) {
                int relY = (int) (mouseY - dropY - 1);
                int index = relY / ITEM_HEIGHT + this.scrollOffset;
                if (index >= 0 && index < this.items.size()) {
                    this.selectedIndex = index;
                    this.expanded = false;
                    if (this.onSelected != null) {
                        this.onSelected.accept(this.items.get(index));
                    }
                }
                return true;
            }
            // 点击按钮条本身则切换关闭
            if (onButton) {
                this.expanded = false;
                return true;
            }
            // 点击其他区域，关闭但不消费事件
            this.expanded = false;
            return false;
        }
        if (onButton) {
            this.expanded = true;
            // 确保选中项在可见范围内
            if (this.selectedIndex >= 0) {
                this.scrollOffset = Mth.clamp(this.scrollOffset, Math.max(0, this.selectedIndex - this.maxVisible + 1), Math.min(this.selectedIndex, Math.max(0, this.items.size() - this.maxVisible)));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.expanded) {
            this.scrollOffset = Mth.clamp(this.scrollOffset - (int) scrollY, 0, Math.max(0, this.items.size() - this.maxVisible));
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, Component.literal("Dropdown"));
    }
}

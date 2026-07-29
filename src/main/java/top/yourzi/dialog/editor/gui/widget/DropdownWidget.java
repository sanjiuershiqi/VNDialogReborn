package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

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
    private int scrollOffset = 0;
    private static final int ITEM_HEIGHT = 12;

    public DropdownWidget(Font font, int x, int y, int width, int height, List<String> items, Consumer<String> onSelected) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.items = items;
        this.onSelected = onSelected;
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

    /**
     * 只渲染按钮条（折叠状态）。展开的列表由 renderPopup 单独渲染。
     */
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), -14540254);
        if (this.isHovered()) {
            graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0x44FFFFFF);
        }
        String text = this.selectedIndex >= 0 ? this.items.get(this.selectedIndex) : "";
        if (text.length() > 20) {
            text = text.substring(0, 17) + "...";
        }
        graphics.drawString(this.font, text, this.getX() + 3, this.getY() + (this.getHeight() - 8) / 2, 0xFFFFFF);
        graphics.drawString(this.font, this.expanded ? "\u25b2" : "\u25bc", this.getX() + this.getWidth() - 10, this.getY() + (this.getHeight() - 8) / 2, 0xAAAAAA);
    }

    /**
     * 渲染展开的弹出列表。应在所有其他控件渲染之后调用，且在 scissor 之外。
     */
    public void renderPopup(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.expanded || !this.visible || this.items.isEmpty()) {
            return;
        }
        int dropY = this.getY() + this.getHeight();
        int visibleCount = Math.min(MAX_VISIBLE, this.items.size());
        int totalHeight = visibleCount * ITEM_HEIGHT;
        // 不透明背景，确保文字清晰
        graphics.fill(this.getX(), dropY, this.getX() + this.getWidth(), dropY + totalHeight + 2, 0xF0101010);
        // 边框
        graphics.fill(this.getX(), dropY, this.getX() + this.getWidth(), dropY + 1, 0xFF555555);
        graphics.fill(this.getX(), dropY + totalHeight + 1, this.getX() + this.getWidth(), dropY + totalHeight + 2, 0xFF555555);
        graphics.fill(this.getX(), dropY, this.getX() + 1, dropY + totalHeight + 2, 0xFF555555);
        graphics.fill(this.getX() + this.getWidth() - 1, dropY, this.getX() + this.getWidth(), dropY + totalHeight + 2, 0xFF555555);

        graphics.enableScissor(this.getX(), dropY, this.getX() + this.getWidth(), dropY + totalHeight + 2);
        try {
            for (int i = 0; i < this.items.size(); i++) {
                int rowY = dropY + 1 + (i - this.scrollOffset) * ITEM_HEIGHT;
                if (rowY + ITEM_HEIGHT < dropY || rowY > dropY + totalHeight) {
                    continue;
                }
                boolean hovered = mouseX >= this.getX() && mouseX <= this.getX() + this.getWidth() && mouseY >= rowY && mouseY <= rowY + ITEM_HEIGHT;
                int bg = hovered ? 0xFF404040 : (i == this.selectedIndex ? 0xFF305060 : 0xFF202020);
                graphics.fill(this.getX() + 1, rowY, this.getX() + this.getWidth() - 1, rowY + ITEM_HEIGHT, bg);
                String itemText = this.items.get(i);
                if (itemText.length() > 22) {
                    itemText = itemText.substring(0, 19) + "...";
                }
                int textColor = hovered ? 0xFFFFFF : 0xCCCCCC;
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
            int dropY = this.getY() + this.getHeight();
            int visibleCount = Math.min(MAX_VISIBLE, this.items.size());
            int totalHeight = visibleCount * ITEM_HEIGHT;
            // 检查是否点击在弹出列表区域
            boolean onPopup = mouseX >= this.getX() && mouseX <= this.getX() + this.getWidth()
                    && mouseY >= dropY && mouseY <= dropY + totalHeight + 2;
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
                this.scrollOffset = Mth.clamp(this.scrollOffset, Math.max(0, this.selectedIndex - MAX_VISIBLE + 1), Math.min(this.selectedIndex, Math.max(0, this.items.size() - MAX_VISIBLE)));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.expanded) {
            this.scrollOffset = Mth.clamp(this.scrollOffset - (int) scrollY, 0, Math.max(0, this.items.size() - MAX_VISIBLE));
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, Component.literal("Dropdown"));
    }
}

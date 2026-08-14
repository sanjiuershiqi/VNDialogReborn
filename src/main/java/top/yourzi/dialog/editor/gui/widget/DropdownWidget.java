package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.yourzi.dialog.editor.gui.EditorRenderHelper;
import top.yourzi.dialog.editor.util.EditorTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 下拉列表控件。移植自 visual_mod_edit_vndialog，适配 NeoForge 1.21.1。
 *
 * 自包含浮层设计（借鉴 Sparkle-Morpher 浮层协议）：
 * - renderWidget 内部自渲染展开列表，父容器无需手动调 renderPopup。
 * - 展开时通过 onPopupToggle 回调通知父容器跳过内容 scissor，避免弹出列表被裁剪。
 * - 父容器仍需在 mouseClicked/mouseScrolled 中优先路由事件给展开的 dropdown（通过 isPopupOpen 判断）。
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
    /** 浮层展开/收起回调，父容器据此控制内容 scissor。 */
    private Consumer<Boolean> onPopupToggle = null;
    /** hover 渐变进度（0=未 hover，1=hover），第八轮美化，借鉴 Sparkle blendBg。 */
    private float hoverProgress = 0f;
    /** 上一帧纳秒时间戳，用于计算 dt 驱动 hoverProgress lerp。 */
    private long lastFrameNanos = 0L;

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

    /**
     * 注册浮层展开/收起回调。父容器据此跳过内容 scissor，让弹出列表不被裁剪。
     * 回调参数：true=即将展开，false=已收起。
     */
    public void setOnPopupToggle(Consumer<Boolean> callback) {
        this.onPopupToggle = callback;
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

    /** 浮层是否展开（父容器据此路由事件与控制 scissor）。 */
    public boolean isPopupOpen() {
        return this.expanded;
    }

    /** 兼容旧 API：等同 isPopupOpen。 */
    public boolean isExpanded() {
        return this.expanded;
    }

    /** 关闭浮层（不触发回调，用于父容器外部点击关闭）。 */
    public void closePopup() {
        if (this.expanded) {
            this.expanded = false;
            this.notifyToggle(false);
        }
    }

    /** 兼容旧 API：等同 closePopup。 */
    public void close() {
        closePopup();
    }

    private void notifyToggle(boolean open) {
        if (this.onPopupToggle != null) {
            this.onPopupToggle.accept(open);
        }
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
     * 渲染按钮条；若展开则同时渲染弹出列表（自包含，父容器无需额外调用）。
     * 父容器须在展开时跳过内容 scissor（通过 onPopupToggle 回调感知），否则弹出列表会被裁剪。
     */
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 第八轮美化：hover lerp 推进
        long now = System.nanoTime();
        float dt = this.lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - this.lastFrameNanos) / 1.0e9f);
        this.lastFrameNanos = now;
        float targetHover = this.isHovered() ? 1f : 0f;
        this.hoverProgress = EditorRenderHelper.tickProgress(this.hoverProgress, targetHover, dt);

        // 按钮条：lerp 背景从 BG_ELEVATED 到 BG_HOVER，圆角填充
        int bg = EditorRenderHelper.lerpColor(EditorTheme.BG_ELEVATED, EditorTheme.BG_HOVER, this.hoverProgress);
        EditorRenderHelper.fillRoundedRect(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), 2, bg);
        String text = this.selectedIndex >= 0 ? this.items.get(this.selectedIndex) : "";
        if (text.length() > 20) {
            text = text.substring(0, 17) + "...";
        }
        // hover 时文字加阴影（项 9）
        boolean textShadow = this.hoverProgress > 0.5f;
        graphics.drawString(this.font, text, this.getX() + 3, this.getY() + (this.getHeight() - 8) / 2, EditorTheme.TEXT_PRIMARY, textShadow);
        graphics.drawString(this.font, this.expanded ? "\u25b2" : "\u25bc", this.getX() + this.getWidth() - 10, this.getY() + (this.getHeight() - 8) / 2, EditorTheme.TEXT_MUTED);
        // 聚焦时 ACCENT 描边（与 EditorButton 一致），未展开时显示，展开时浮层已有边框
        if (this.isFocused() && !this.expanded) {
            graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + 1, EditorTheme.ACCENT);
            graphics.fill(this.getX(), this.getY() + this.getHeight() - 1, this.getX() + this.getWidth(), this.getY() + this.getHeight(), EditorTheme.ACCENT);
            graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.getHeight(), EditorTheme.ACCENT);
            graphics.fill(this.getX() + this.getWidth() - 1, this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), EditorTheme.ACCENT);
        }

        // 展开时自渲染浮层（父容器已通过 onPopupToggle 跳过 scissor）
        if (this.expanded && this.visible && !this.items.isEmpty()) {
            renderPopupInternal(graphics, mouseX, mouseY);
        }
    }

    /** 浮层内部渲染（自包含调用，外部无需调用）。 */
    private void renderPopupInternal(GuiGraphics graphics, int mouseX, int mouseY) {
        int dropY = this.getPopupTop();
        int dropBottom = this.getPopupBottom();
        int pw = this.getWidth();
        // 第八轮美化：浮层投影，制造悬浮感（项 5）
        EditorRenderHelper.fillWithShadow(graphics, this.getX(), dropY, pw, dropBottom - dropY, EditorTheme.POPUP_BG, EditorTheme.SHADOW_DROP);
        // 边框
        graphics.fill(this.getX(), dropY, this.getX() + pw, dropY + 1, EditorTheme.BORDER);
        graphics.fill(this.getX(), dropBottom - 1, this.getX() + pw, dropBottom, EditorTheme.BORDER);
        graphics.fill(this.getX(), dropY, this.getX() + 1, dropBottom, EditorTheme.BORDER);
        graphics.fill(this.getX() + pw - 1, dropY, this.getX() + pw, dropBottom, EditorTheme.BORDER);

        graphics.enableScissor(this.getX(), dropY, this.getX() + pw, dropBottom);
        try {
            for (int i = 0; i < this.items.size(); i++) {
                int rowY = dropY + 1 + (i - this.scrollOffset) * ITEM_HEIGHT;
                if (rowY + ITEM_HEIGHT < dropY || rowY > dropBottom) {
                    continue;
                }
                boolean hovered = mouseX >= this.getX() && mouseX <= this.getX() + pw && mouseY >= rowY && mouseY <= rowY + ITEM_HEIGHT;
                int bg = hovered ? EditorTheme.BG_HOVER : (i == this.selectedIndex ? EditorTheme.BG_SELECTED : EditorTheme.BG_SURFACE);
                graphics.fill(this.getX() + 1, rowY, this.getX() + pw - 1, rowY + ITEM_HEIGHT, bg);
                String itemText = this.items.get(i);
                if (itemText.length() > 22) {
                    itemText = itemText.substring(0, 19) + "...";
                }
                int textColor = hovered ? EditorTheme.TEXT_PRIMARY : EditorTheme.TEXT_SECONDARY;
                // 第八轮美化：hover 项文字加阴影（项 9）
                graphics.drawString(this.font, itemText, this.getX() + 3, rowY + 2, textColor, hovered);
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
                    this.notifyToggle(false);
                    if (this.onSelected != null) {
                        this.onSelected.accept(this.items.get(index));
                    }
                }
                return true;
            }
            // 点击按钮条本身则切换关闭
            if (onButton) {
                this.expanded = false;
                this.notifyToggle(false);
                return true;
            }
            // 点击其他区域，关闭但不消费事件
            this.expanded = false;
            this.notifyToggle(false);
            return false;
        }
        if (onButton) {
            this.expanded = true;
            this.notifyToggle(true);
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

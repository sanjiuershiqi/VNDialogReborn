package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.yourzi.dialog.editor.gui.EditorRenderHelper;
import top.yourzi.dialog.editor.gui.EditorScreenState;
import top.yourzi.dialog.editor.gui.property.AppearancePropertyPage;
import top.yourzi.dialog.editor.gui.property.LogicPropertyPage;
import top.yourzi.dialog.editor.gui.property.PropertyPage;
import top.yourzi.dialog.editor.gui.property.TextPropertyPage;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * 属性面板：包含文本/外观/逻辑三个标签页。融合自 visual_mod_edit_vndialog。
 * 在原实现基础上增加垂直滚动，防止内容超出可视区域时被裁剪（如文本页颜色/格式按钮）。
 */
public class PropertyPanel extends AbstractWidget {
    private static final int TAB_HEIGHT = EditorTheme.PROP_TAB_H;
    private static final int HEADER_HEIGHT = EditorTheme.PANEL_HEADER_H;
    private static final int TAB_WIDTH = EditorTheme.PROP_TAB_W;
    private static final int SCROLLBAR_WIDTH = EditorTheme.SCROLLBAR_W;
    private final List<Tab> tabs = new ArrayList<>();
    private int activeTabIndex = 0;
    private boolean initialized = false;
    private final Font font;
    private OnTabChangeListener onTabChangeListener;
    // 内容垂直滚动偏移，用于在页面内容超出可视高度时滚动查看
    private int scrollOffset = 0;
    /** 滚动条拖拽 + 平滑滚动状态（借鉴 Sparkle OptionScreen）。 */
    private final EditorRenderHelper.ScrollState scrollState = new EditorRenderHelper.ScrollState();
    /** 标签 hover 渐变进度数组（每标签一个 0~1 值），第八轮美化，借鉴 Sparkle blendBg。 */
    private float[] tabHoverProgress = new float[0];
    /** 上一帧纳秒时间戳，用于计算 dt 驱动平滑滚动。 */
    private long lastFrameNanos = 0L;
    /** 当前活动页是否有下拉框浮层展开，展开时跳过内容 scissor 避免裁剪浮层。 */
    private boolean popupOpen = false;
    /** 当前绑定的节点（relayout 时重新绑定数据用；null=未绑定）。 */
    private DialogEntry currentEntry;

    public PropertyPanel(int x, int y, int width, int height, Font font) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.tabs.add(new Tab(Component.translatable("gui.vn_edit.tab.text"), new TextPropertyPage(font)));
        this.tabs.add(new Tab(Component.translatable("gui.vn_edit.tab.appearance"), new AppearancePropertyPage(font)));
        this.tabs.add(new Tab(Component.translatable("gui.vn_edit.tab.logic"), new LogicPropertyPage(font)));
        // 从状态单例恢复上次活动标签，子屏返回后不再回退到首个标签
        this.activeTabIndex = EditorScreenState.get().getActivePropertyTab();
        if (this.activeTabIndex < 0 || this.activeTabIndex >= this.tabs.size()) {
            this.activeTabIndex = 0;
        }
    }

    public void setOnTabChangeListener(OnTabChangeListener listener) {
        this.onTabChangeListener = listener;
    }

    private void ensureInitialized() {
        if (!this.initialized) {
            this.initializePages();
            this.initialized = true;
        }
    }

    public void bindTo(DialogEntry entry) {
        this.ensureInitialized();
        this.currentEntry = entry;
        for (Tab tab : this.tabs) {
            tab.page.bindTo(entry);
        }
        for (int i = 0; i < this.tabs.size(); i++) {
            this.tabs.get(i).page.setVisible(i == this.activeTabIndex);
        }
        this.scrollOffset = 0;
        this.scrollState.reset(0);
    }

    public void unbind() {
        this.ensureInitialized();
        this.currentEntry = null;
        for (Tab tab : this.tabs) {
            tab.page.unbind();
        }
        this.scrollOffset = 0;
        this.scrollState.reset(0);
    }

    /**
     * 宿主重设面板几何后重建页面布局（窗口缩放或检查器宽度变化时调用）。
     * 页面控件坐标在 init 时固定，故需重新 init + 重绑当前节点数据。
     */
    public void relayout() {
        if (!this.initialized) {
            return; // 尚未初始化时后续 ensureInitialized 会用新几何
        }
        if (this.popupOpen) {
            for (DropdownWidget dd : this.tabs.get(this.activeTabIndex).page.getDropdowns()) {
                dd.closePopup();
            }
            this.popupOpen = false;
        }
        this.initializePages();
        if (this.currentEntry != null) {
            for (Tab tab : this.tabs) {
                tab.page.bindTo(this.currentEntry);
            }
            for (int i = 0; i < this.tabs.size(); i++) {
                this.tabs.get(i).page.setVisible(i == this.activeTabIndex);
            }
            this.clampScroll();
        }
    }

    public void setSequence(DialogSequence sequence) {
        this.ensureInitialized();
        for (Tab tab : this.tabs) {
            tab.page.setSequence(sequence);
        }
    }

    /**
     * 转发字段变脏回调到所有属性页，页内 Option 变脏时触发（用于主屏 markDirty 序列）。
     */
    public void setDirtyListener(Runnable listener) {
        this.ensureInitialized();
        for (Tab tab : this.tabs) {
            tab.page.setDirtyListener(listener);
        }
    }

    /**
     * 序列保存成功后转发到所有属性页，各页重置字段 dirty 基线（snapshot）。
     */
    public void onSequenceSaved() {
        this.ensureInitialized();
        for (Tab tab : this.tabs) {
            tab.page.onSequenceSaved();
        }
    }

    private void initializePages() {
        int pageX = this.getX() + 4;
        int pageY = this.getY() + HEADER_HEIGHT + TAB_HEIGHT + 3;
        int pageWidth = this.getWidth() - 8;
        int pageHeight = this.getHeight() - HEADER_HEIGHT - TAB_HEIGHT - 6;
        for (Tab tab : this.tabs) {
            tab.page.init(pageX, pageY, pageWidth, pageHeight);
            // 为页面内所有下拉框注册浮层回调，展开时跳过内容 scissor
            for (DropdownWidget dd : tab.page.getDropdowns()) {
                dd.setOnPopupToggle(open -> this.popupOpen = open);
            }
        }
    }

    private int getPageTop() {
        return this.getY() + HEADER_HEIGHT + TAB_HEIGHT + 3;
    }

    private int getPageHeight() {
        return this.getHeight() - HEADER_HEIGHT - TAB_HEIGHT - 6;
    }

    private int getActiveContentHeight() {
        if (this.activeTabIndex < 0 || this.activeTabIndex >= this.tabs.size()) {
            return 0;
        }
        return this.tabs.get(this.activeTabIndex).page().getContentHeight();
    }

    private int getMaxScroll() {
        return Math.max(0, this.getActiveContentHeight() - this.getPageHeight());
    }

    private void clampScroll() {
        int max = this.getMaxScroll();
        if (this.scrollOffset > max) {
            this.scrollOffset = max;
        }
        if (this.scrollOffset < 0) {
            this.scrollOffset = 0;
        }
    }

    public void setActiveTab(int index) {
        this.ensureInitialized();
        if (index >= 0 && index < this.tabs.size()) {
            // 切换标签页前关闭当前页所有下拉浮层，避免浮层状态残留
            if (this.popupOpen) {
                for (DropdownWidget dd : this.tabs.get(this.activeTabIndex).page.getDropdowns()) {
                    dd.closePopup();
                }
                this.popupOpen = false;
            }
            this.activeTabIndex = index;
            for (int i = 0; i < this.tabs.size(); i++) {
                this.tabs.get(i).page.setVisible(i == index);
            }
            // 切换标签页时重置滚动，避免上一个页面的偏移影响新页面
            this.scrollOffset = 0;
            this.scrollState.reset(0);
            // 写回状态单例，子屏重建后 PropertyPanel 构造时自动恢复活动标签
            EditorScreenState.get().setActivePropertyTab(index);
            if (this.onTabChangeListener != null) {
                this.onTabChangeListener.onTabChanged(index);
            }
        }
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        this.ensureInitialized();
        for (Tab tab : this.tabs) {
            tab.page.setVisible(visible);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }
        this.ensureInitialized();
        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), EditorTheme.BG_SURFACE);
        EditorTheme.drawPanelHeader(graphics, this.font, this.getX(), this.getY(), this.getWidth(), "03",
                Component.translatable("gui.vn_edit.property"));
        int tabX = this.getX();
        int tabY = this.getY() + HEADER_HEIGHT;
        // 第八轮美化：标签 hover lerp 推进（项 4），dt 复用上一帧时间戳
        long nowTab = System.nanoTime();
        float dtTab = this.lastFrameNanos == 0L ? 0f : Math.min(0.1f, (nowTab - this.lastFrameNanos) / 1.0e9f);
        if (this.tabHoverProgress.length < this.tabs.size()) {
            this.tabHoverProgress = new float[this.tabs.size()];
        }
        for (int i = 0; i < this.tabs.size(); i++) {
            Tab tab = this.tabs.get(i);
            int color = i == this.activeTabIndex ? EditorTheme.TEXT_PRIMARY : EditorTheme.TEXT_SECONDARY;
            // hover 检测 + lerp 推进
            boolean tabHovered = mouseX >= tabX && mouseX <= tabX + TAB_WIDTH && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT;
            float targetTabHover = (tabHovered && i != this.activeTabIndex) ? 1f : 0f;
            this.tabHoverProgress[i] = EditorRenderHelper.tickProgress(this.tabHoverProgress[i], targetTabHover, dtTab);
            int bgColor;
            if (i == this.activeTabIndex) {
                bgColor = EditorTheme.BG_SURFACE;
            } else {
                bgColor = EditorRenderHelper.lerpColor(EditorTheme.BG_ELEVATED, EditorTheme.BG_HOVER, this.tabHoverProgress[i]);
            }
            graphics.fill(tabX, tabY, tabX + TAB_WIDTH, tabY + TAB_HEIGHT, bgColor);
            graphics.drawCenteredString(this.font, tab.title, tabX + TAB_WIDTH / 2, tabY + 4, color);
            if (i == this.activeTabIndex) {
                graphics.fill(tabX, tabY + TAB_HEIGHT - 2, tabX + TAB_WIDTH, tabY + TAB_HEIGHT, EditorTheme.ACCENT);
            }
            graphics.fill(tabX + TAB_WIDTH, tabY, tabX + TAB_WIDTH + 1, tabY + TAB_HEIGHT, EditorTheme.BORDER);
            tabX += TAB_WIDTH + 1;
        }
        if (this.activeTabIndex >= 0 && this.activeTabIndex < this.tabs.size()) {
            this.clampScroll();
            int pageTop = this.getPageTop();
            int pageH = this.getPageHeight();
            int contentH = this.getActiveContentHeight();
            // 计算 dt 驱动平滑滚动（首帧 lastFrameNanos=0 直接吸附）
            long now = System.nanoTime();
            float dt = this.lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - this.lastFrameNanos) / 1.0e9f);
            this.lastFrameNanos = now;
            int displayOffset = this.scrollState.tick(this.scrollOffset, dt);
            // 用 scissor 裁剪页面内容区域，滚动时不会溢出到标签栏上。
            // 当下拉框浮层展开时跳过 scissor，让 DropdownWidget 自渲染的浮层不被裁剪。
            boolean useScissor = !this.popupOpen;
            if (useScissor) {
                graphics.enableScissor(this.getX(), pageTop, this.getX() + this.getWidth(), pageTop + pageH);
            }
            try {
                graphics.pose().pushPose();
                graphics.pose().translate(0, -displayOffset, 0);
                // 滚动后鼠标逻辑坐标需要相应补偿，保证悬停/点击对齐
                this.tabs.get(this.activeTabIndex).page.render(graphics, mouseX, mouseY + displayOffset, partialTick);
                graphics.pose().popPose();
            } finally {
                if (useScissor) {
                    graphics.disableScissor();
                }
            }
            // 滚动条（用 displayOffset 计算 thumbY，拖拽中滑块高亮）
            if (contentH > pageH) {
                int scrollBarHeight = Math.max(10, pageH * pageH / contentH);
                int scrollBarY = pageTop + (int) ((float) displayOffset / (float) (contentH - pageH) * (float) (pageH - scrollBarHeight));
                graphics.fill(this.getX() + this.getWidth() - SCROLLBAR_WIDTH, pageTop, this.getX() + this.getWidth(), pageTop + pageH, EditorTheme.SCROLLBAR_TRACK);
                int thumbColor = this.scrollState.dragging ? EditorTheme.TEXT_PRIMARY : EditorTheme.SCROLLBAR_THUMB;
                graphics.fill(this.getX() + this.getWidth() - SCROLLBAR_WIDTH, scrollBarY, this.getX() + this.getWidth(), scrollBarY + scrollBarHeight, thumbColor);
            }
            // 下拉浮层已由 DropdownWidget.renderWidget 自包含渲染（跟随页面 translate），无需在此手动调用。
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.ensureInitialized();
        double adjustedY = mouseY + this.scrollOffset;
        // 如果有下拉框已展开，优先处理它的点击（包括点击外部关闭）
        if (this.activeTabIndex >= 0 && this.activeTabIndex < this.tabs.size()) {
            List<DropdownWidget> dropdowns = this.tabs.get(this.activeTabIndex).page.getDropdowns();
            for (DropdownWidget dd : dropdowns) {
                if (dd.isExpanded()) {
                    if (dd.mouseClicked(mouseX, adjustedY, button)) {
                        return true;
                    }
                    // 点击在下拉框外部，关闭并消费事件防止误触其他控件
                    dd.close();
                    return true;
                }
            }
        }
        // 滚动条命中：开始拖拽并立即跳到点击位置
        int maxScroll = this.getMaxScroll();
        if (maxScroll > 0 && button == 0) {
            int pageTop = this.getPageTop();
            int pageH = this.getPageHeight();
            int trackX = this.getX() + this.getWidth() - SCROLLBAR_WIDTH;
            if (EditorRenderHelper.isOnVerticalScrollbar(mouseX, mouseY, trackX, pageTop, SCROLLBAR_WIDTH, pageH)) {
                this.scrollState.dragging = true;
                this.scrollOffset = EditorRenderHelper.offsetFromMouseY(mouseY, pageTop, pageTop + pageH, maxScroll);
                this.clampScroll();
                return true;
            }
        }
        int tabX = this.getX();
        int tabY = this.getY() + HEADER_HEIGHT;
        for (int i = 0; i < this.tabs.size(); i++) {
            if (mouseX >= tabX && mouseX <= tabX + TAB_WIDTH && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT) {
                this.setActiveTab(i);
                for (GuiEventListener child : this.tabs.get(i).page.children()) {
                    child.setFocused(false);
                }
                return true;
            }
            tabX += TAB_WIDTH + 1;
        }
        if (this.activeTabIndex >= 0) {
            List<? extends GuiEventListener> children = this.tabs.get(this.activeTabIndex).page.children();
            // 页面内容已滚动，鼠标坐标需加上滚动偏移才能命中实际控件
            for (GuiEventListener child : children) {
                if (!child.mouseClicked(mouseX, adjustedY, button)) {
                    continue;
                }
                for (GuiEventListener other : children) {
                    other.setFocused(false);
                }
                child.setFocused(true);
                if (this.activeTabIndex == 0) {
                    TextPropertyPage tp = (TextPropertyPage) this.tabs.get(0).page;
                    if (tp.modeSwitchBtn != null) {
                        tp.modeSwitchBtn.setFocused(false);
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visible) {
            return false;
        }
        // 先让子控件（如展开的下拉框）处理滚动
        if (this.activeTabIndex >= 0) {
            double adjustedY = mouseY + this.scrollOffset;
            for (GuiEventListener child : this.tabs.get(this.activeTabIndex).page.children()) {
                if (child.mouseScrolled(mouseX, adjustedY, scrollX, scrollY)) {
                    return true;
                }
            }
        }
        // 仅当鼠标悬停在面板内容区时才处理滚动，避免抢夺对话树组件的滚动
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int max = this.getMaxScroll();
        if (max <= 0) {
            return false;
        }
        this.scrollOffset = Mth.clamp(this.scrollOffset - (int) scrollY * EditorTheme.FIELD_HEIGHT, 0, max);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (this.scrollState.dragging) {
            int pageTop = this.getPageTop();
            int pageH = this.getPageHeight();
            this.scrollOffset = EditorRenderHelper.offsetFromMouseY(mouseY, pageTop, pageTop + pageH, this.getMaxScroll());
            this.clampScroll();
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        this.ensureInitialized();
        if (this.activeTabIndex >= 0) {
            for (GuiEventListener child : this.tabs.get(this.activeTabIndex).page.children()) {
                if (child.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        this.ensureInitialized();
        if (this.activeTabIndex >= 0) {
            for (GuiEventListener child : this.tabs.get(this.activeTabIndex).page.children()) {
                if (child.charTyped(codePoint, modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, Component.translatable("gui.vn_edit.property"));
    }

    private record Tab(Component title, PropertyPage page) {
    }

    public interface OnTabChangeListener {
        void onTabChanged(int index);
    }
}

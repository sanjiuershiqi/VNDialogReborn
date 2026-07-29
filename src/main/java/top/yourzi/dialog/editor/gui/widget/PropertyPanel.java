package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
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
    private static final int TAB_WIDTH = EditorTheme.PROP_TAB_W;
    private static final int SCROLLBAR_WIDTH = EditorTheme.SCROLLBAR_W;
    private final List<Tab> tabs = new ArrayList<>();
    private int activeTabIndex = 0;
    private boolean initialized = false;
    private final Font font;
    private OnTabChangeListener onTabChangeListener;
    // 内容垂直滚动偏移，用于在页面内容超出可视高度时滚动查看
    private int scrollOffset = 0;

    public PropertyPanel(int x, int y, int width, int height, Font font) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.tabs.add(new Tab(Component.translatable("gui.vn_edit.tab.text"), new TextPropertyPage(font)));
        this.tabs.add(new Tab(Component.translatable("gui.vn_edit.tab.appearance"), new AppearancePropertyPage(font)));
        this.tabs.add(new Tab(Component.translatable("gui.vn_edit.tab.logic"), new LogicPropertyPage(font)));
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
        for (Tab tab : this.tabs) {
            tab.page.bindTo(entry);
        }
        for (int i = 0; i < this.tabs.size(); i++) {
            this.tabs.get(i).page.setVisible(i == this.activeTabIndex);
        }
        this.scrollOffset = 0;
    }

    public void unbind() {
        this.ensureInitialized();
        for (Tab tab : this.tabs) {
            tab.page.unbind();
        }
        this.scrollOffset = 0;
    }

    public void setSequence(DialogSequence sequence) {
        this.ensureInitialized();
        for (Tab tab : this.tabs) {
            tab.page.setSequence(sequence);
        }
    }

    private void initializePages() {
        int pageX = this.getX() + 4;
        int pageY = this.getY() + TAB_HEIGHT + 3;
        int pageWidth = this.getWidth() - 8;
        int pageHeight = this.getHeight() - TAB_HEIGHT - 6;
        for (Tab tab : this.tabs) {
            tab.page.init(pageX, pageY, pageWidth, pageHeight);
        }
    }

    private int getPageTop() {
        return this.getY() + TAB_HEIGHT + 3;
    }

    private int getPageHeight() {
        return this.getHeight() - TAB_HEIGHT - 6;
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
            this.activeTabIndex = index;
            for (int i = 0; i < this.tabs.size(); i++) {
                this.tabs.get(i).page.setVisible(i == index);
            }
            // 切换标签页时重置滚动，避免上一个页面的偏移影响新页面
            this.scrollOffset = 0;
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
        int tabX = this.getX();
        int tabY = this.getY();
        for (int i = 0; i < this.tabs.size(); i++) {
            Tab tab = this.tabs.get(i);
            int color = i == this.activeTabIndex ? EditorTheme.TEXT_PRIMARY : EditorTheme.TEXT_SECONDARY;
            int bgColor = i == this.activeTabIndex ? EditorTheme.BG_SURFACE : EditorTheme.BG_ELEVATED;
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
            // 用 scissor 裁剪页面内容区域，滚动时不会溢出到标签栏上
            graphics.enableScissor(this.getX(), pageTop, this.getX() + this.getWidth(), pageTop + pageH);
            try {
                graphics.pose().pushPose();
                graphics.pose().translate(0, -this.scrollOffset, 0);
                // 滚动后鼠标逻辑坐标需要相应补偿，保证悬停/点击对齐
                this.tabs.get(this.activeTabIndex).page.render(graphics, mouseX, mouseY + this.scrollOffset, partialTick);
                graphics.pose().popPose();
            } finally {
                graphics.disableScissor();
            }
            // 滚动条
            if (contentH > pageH) {
                int scrollBarHeight = Math.max(10, pageH * pageH / contentH);
                int scrollBarY = pageTop + (int) ((float) this.scrollOffset / (float) (contentH - pageH) * (float) (pageH - scrollBarHeight));
                graphics.fill(this.getX() + this.getWidth() - SCROLLBAR_WIDTH, pageTop, this.getX() + this.getWidth(), pageTop + pageH, 0x33000000);
                graphics.fill(this.getX() + this.getWidth() - SCROLLBAR_WIDTH, scrollBarY, this.getX() + this.getWidth(), scrollBarY + scrollBarHeight, EditorTheme.TEXT_MUTED);
            }
            // 在 scissor 之外渲染展开的下拉弹出列表，确保不被裁剪或遮挡
            List<DropdownWidget> dropdowns = this.tabs.get(this.activeTabIndex).page.getDropdowns();
            if (!dropdowns.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, -this.scrollOffset, 0);
                for (DropdownWidget dd : dropdowns) {
                    dd.renderPopup(graphics, mouseX, mouseY + this.scrollOffset, partialTick);
                }
                graphics.pose().popPose();
            }
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
        int tabX = this.getX();
        int tabY = this.getY();
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

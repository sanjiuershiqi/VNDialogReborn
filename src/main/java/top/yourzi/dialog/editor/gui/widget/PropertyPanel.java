package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.gui.property.AppearancePropertyPage;
import top.yourzi.dialog.editor.gui.property.LogicPropertyPage;
import top.yourzi.dialog.editor.gui.property.PropertyPage;
import top.yourzi.dialog.editor.gui.property.TextPropertyPage;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * 属性面板：包含文本/外观/逻辑三个标签页。融合自 visual_mod_edit_vndialog。
 */
public class PropertyPanel extends AbstractWidget {
    private static final int TAB_HEIGHT = 15;
    private static final int TAB_WIDTH = 50;
    private final List<Tab> tabs = new ArrayList<>();
    private int activeTabIndex = 0;
    private boolean initialized = false;
    private final Font font;
    private OnTabChangeListener onTabChangeListener;

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
    }

    public void unbind() {
        this.ensureInitialized();
        for (Tab tab : this.tabs) {
            tab.page.unbind();
        }
    }

    public void setSequence(DialogSequence sequence) {
        this.ensureInitialized();
        for (Tab tab : this.tabs) {
            tab.page.setSequence(sequence);
        }
    }

    private void initializePages() {
        int pageX = this.getX() + 2;
        int pageY = this.getY() + TAB_HEIGHT + 2;
        int pageWidth = this.getWidth() - 4;
        int pageHeight = this.getHeight() - TAB_HEIGHT - 4;
        for (Tab tab : this.tabs) {
            tab.page.init(pageX, pageY, pageWidth, pageHeight);
        }
    }

    public void setActiveTab(int index) {
        this.ensureInitialized();
        if (index >= 0 && index < this.tabs.size()) {
            this.activeTabIndex = index;
            for (int i = 0; i < this.tabs.size(); i++) {
                this.tabs.get(i).page.setVisible(i == index);
            }
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
        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), -869059789);
        int tabX = this.getX();
        int tabY = this.getY();
        for (int i = 0; i < this.tabs.size(); i++) {
            Tab tab = this.tabs.get(i);
            int color = i == this.activeTabIndex ? -1 : -5592406;
            int bgColor = i == this.activeTabIndex ? -581610155 : -1439485133;
            graphics.fill(tabX, tabY, tabX + TAB_WIDTH, tabY + TAB_HEIGHT, bgColor);
            graphics.drawCenteredString(this.font, tab.title, tabX + TAB_WIDTH / 2, tabY + 2, color);
            if (i == this.activeTabIndex) {
                graphics.fill(tabX, tabY + TAB_HEIGHT - 1, tabX + TAB_WIDTH, tabY + TAB_HEIGHT, -256);
            }
            graphics.fill(tabX + TAB_WIDTH, tabY, tabX + TAB_WIDTH + 1, tabY + TAB_HEIGHT, -10066330);
            tabX += TAB_WIDTH + 1;
        }
        if (this.activeTabIndex >= 0 && this.activeTabIndex < this.tabs.size()) {
            this.tabs.get(this.activeTabIndex).page.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.ensureInitialized();
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
            for (GuiEventListener child : children) {
                if (!child.mouseClicked(mouseX, mouseY, button)) {
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

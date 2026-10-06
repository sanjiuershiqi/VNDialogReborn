package top.yourzi.dialog.editor.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.ui.Chrome;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.Stack;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiNode;

/**
 * Workspace panel: a numbered title rail, an optional toolbar, one content slot and an optional
 * footer.
 *
 * <p>Panels are laid out side by side rather than floated, so the workspace keeps three readable
 * columns at every window size and no panel needs to know where its neighbours are.
 */
public abstract class EditorPanel extends UiNode {
    private final String index;
    private final Component title;
    private final Stack content = new Stack();
    private Row toolbar;
    private UiNode footer;
    private int toolbarHeight;

    protected EditorPanel(String index, Component title) {
        this.index = index;
        this.title = title;
        this.add(this.content);
    }

    /** Reserves a toolbar strip under the title rail; call before adding toolbar nodes. */
    protected final Row createToolbar(int height) {
        this.toolbarHeight = height;
        this.toolbar = new Row().gap(2);
        this.add(this.toolbar);
        return this.toolbar;
    }

    protected final Row toolbar() {
        return this.toolbar;
    }

    protected final Stack content() {
        return this.content;
    }

    protected final void showOnly(UiNode node) {
        for (UiNode child : this.content.children()) {
            child.setVisible(child == node);
        }
    }

    protected final void setFooter(UiNode footer) {
        this.footer = footer;
        this.add(footer);
    }

    @Override
    protected void onLayout() {
        int top = this.y() + Theme.ROW + 4;
        if (this.toolbar != null) {
            this.toolbar.setBounds(this.x() + 2, top, this.width() - 4, this.toolbarHeight);
            top += this.toolbarHeight + 3;
        }
        int bottom = this.y() + this.height();
        if (this.footer != null) {
            int footerHeight = this.footer.measureHeight(this.width());
            this.footer.setBounds(this.x(), bottom - footerHeight, this.width(), footerHeight);
            bottom -= footerHeight;
        }
        this.content.setBounds(this.x(), top, this.width(), Math.max(0, bottom - top));
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.x(), this.y(), this.right(), this.bottom(), Theme.SURFACE);
        if (this.index != null) {
            Chrome.titleRail(graphics, this.x(), this.y(), this.width(), this.index, this.title);
        } else {
            graphics.fill(this.x(), this.y(), this.right(), this.y() + Theme.ROW + 4, Theme.RAISED);
            graphics.fill(this.x(), this.y(), this.x() + 3, this.y() + Theme.ROW + 4, Theme.ACCENT);
            Theme.textIn(graphics, this.title.getString(), this.x() + 10, this.y() + 2, this.width() - 16,
                    Theme.ROW, Theme.TEXT);
        }
        graphics.fill(this.right() - 1, this.y(), this.right(), this.bottom(), Theme.BORDER);
    }
}

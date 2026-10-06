package top.yourzi.dialog.editor.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiNode;

/**
 * Workspace panel: a numbered title rail, an optional toolbar and a content area that subclasses
 * fill with their own nodes in {@link #onLayout()}.
 */
public abstract class EditorPanel extends UiNode {
    private static final int RAIL_H = Theme.ROW + 4;

    private final String index;
    private final Component title;
    private final Slot content = new Slot();
    private Row toolbar;
    private int toolbarHeight;

    protected EditorPanel(String index, Component title) {
        this.index = index;
        this.title = title;
        this.add(this.content);
    }

    /** Reserves a toolbar strip under the title rail. */
    protected final Row createToolbar(int height) {
        this.toolbarHeight = height;
        this.toolbar = new Row().gap(2);
        this.add(this.toolbar);
        return this.toolbar;
    }

    /** The area below the title rail and toolbar; subclasses place their nodes inside it. */
    protected final UiNode content() {
        return this.content;
    }

    @Override
    protected void onLayout() {
        int top = this.y() + RAIL_H;
        if (this.toolbar != null) {
            this.toolbar.setBounds(this.x() + 2, top, this.width() - 4, this.toolbarHeight);
            top += this.toolbarHeight + 3;
        }
        this.content.setBounds(this.x(), top, this.width(), Math.max(0, this.bottom() - top));
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.x();
        int y = this.y();
        graphics.fill(x, y, this.right(), this.bottom(), Theme.SURFACE);
        graphics.fill(x, y, this.right(), y + RAIL_H, Theme.RAISED);
        graphics.fill(x, y, x + 3, y + RAIL_H, Theme.ACCENT);
        Theme.text(graphics, this.index, x + 9, y + (RAIL_H - 8) / 2, Theme.ACCENT);
        Theme.textIn(graphics, this.title.getString(), x + 28, y, Math.max(1, this.width() - 34), RAIL_H, Theme.TEXT);
        graphics.fill(x, y + RAIL_H - 1, this.right(), y + RAIL_H, Theme.BORDER);
        graphics.fill(this.right() - 1, y, this.right(), this.bottom(), Theme.BORDER);
    }

    /** Layout-only region; disabled so it never swallows clicks meant for the panel. */
    private static final class Slot extends UiNode {
        Slot() {
            this.setEnabled(false);
        }
    }
}

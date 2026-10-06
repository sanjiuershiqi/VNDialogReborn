package top.yourzi.dialog.editor.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiNode;

/**
 * Workspace panel: an optional slim header, an optional toolbar and a content area that subclasses
 * fill with their own nodes in {@link #onLayout()}. Panels are separated from each other by surface
 * tone, not by boxes.
 */
public abstract class EditorPanel extends UiNode {
    private static final int HEADER_H = 20;

    private final Component title;
    private final Slot content = new Slot();
    private Row toolbar;
    private int toolbarHeight;

    /** @param title header text, or null for a header-less panel */
    protected EditorPanel(Component title) {
        this.title = title;
        this.add(this.content);
    }

    /** Reserves a toolbar strip under the header. */
    protected final Row createToolbar(int height) {
        this.toolbarHeight = height;
        this.toolbar = new Row().gap(3);
        this.add(this.toolbar);
        return this.toolbar;
    }

    /** The area below header and toolbar; subclasses place their nodes inside it. */
    protected final UiNode content() {
        return this.content;
    }

    /** Short right-aligned header note such as a count; empty by default. */
    protected String headerNote() {
        return "";
    }

    @Override
    protected void onLayout() {
        int top = this.y() + (this.title == null ? 4 : HEADER_H);
        if (this.toolbar != null) {
            this.toolbar.setBounds(this.x() + 6, top, this.width() - 12, this.toolbarHeight);
            top += this.toolbarHeight + 6;
        }
        this.content.setBounds(this.x(), top, this.width(), Math.max(0, this.bottom() - top));
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.x(), this.y(), this.right(), this.bottom(), Theme.SURFACE);
        if (this.title != null) {
            String note = this.headerNote();
            int noteWidth = note.isEmpty() ? 0 : Theme.font().width(note) + 8;
            Theme.textIn(graphics, this.title.getString(), this.x() + 8, this.y() + 4, this.width() - noteWidth - 16,
                    HEADER_H - 6, Theme.TEXT_DIM);
            if (!note.isEmpty()) {
                Theme.text(graphics, note, this.right() - noteWidth, this.y() + 4 + (HEADER_H - 14) / 2, Theme.TEXT_MUTED);
            }
        }
    }

    /** Layout-only region; disabled so it never swallows clicks meant for the panel. */
    private static final class Slot extends UiNode {
        Slot() {
            this.setEnabled(false);
        }
    }
}

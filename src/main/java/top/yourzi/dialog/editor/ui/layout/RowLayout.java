package top.yourzi.dialog.editor.ui.layout;

import top.yourzi.dialog.editor.ui.core.UiNode;
import top.yourzi.dialog.editor.ui.core.UiRect;

/** Horizontal flow layout used by toolbars and compact field rows. */
public final class RowLayout implements UiLayout {
    private final int gap;

    public RowLayout(int gap) { this.gap = Math.max(0, gap); }

    @Override
    public void layout(UiNode parent, UiRect available) {
        int x = available.x();
        for (UiNode child : parent.children()) {
            if (!child.isVisible()) continue;
            UiRect old = child.bounds();
            child.setBounds(new UiRect(x, available.y(), old.width(), available.height()));
            x += old.width() + this.gap;
        }
    }
}

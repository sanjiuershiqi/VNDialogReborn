package top.yourzi.dialog.editor.ui.layout;

import top.yourzi.dialog.editor.ui.core.UiNode;
import top.yourzi.dialog.editor.ui.core.UiRect;

/** Simple vertical flow layout used by inspectors and scrollable form sections. */
public final class StackLayout implements UiLayout {
    private final int gap;

    public StackLayout(int gap) { this.gap = Math.max(0, gap); }

    @Override
    public void layout(UiNode parent, UiRect available) {
        int y = available.y();
        for (UiNode child : parent.children()) {
            if (!child.isVisible()) continue;
            UiRect old = child.bounds();
            child.setBounds(new UiRect(available.x(), y, available.width(), old.height()));
            y += old.height() + this.gap;
        }
    }
}

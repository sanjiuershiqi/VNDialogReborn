package top.yourzi.dialog.editor.ui.layout;

import top.yourzi.dialog.editor.ui.core.UiNode;
import top.yourzi.dialog.editor.ui.core.UiRect;

/** Layout strategy applied to a retained node's direct children. */
public interface UiLayout {
    void layout(UiNode parent, UiRect available);
}

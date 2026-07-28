package top.yourzi.dialog.editor.gui.widget;

import top.yourzi.dialog.model.DialogEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话树节点。融合自 visual_mod_edit_vndialog。
 */
public class TreeNode {
    public DialogEntry entry;
    public TreeNode parent;
    public List<TreeNode> children = new ArrayList<>();
    public int depth;
    public boolean expanded = true;
    public boolean isOrphan;

    public TreeNode(DialogEntry entry, TreeNode parent, int depth) {
        this.entry = entry;
        this.parent = parent;
        this.depth = depth;
    }
}

package top.yourzi.dialog.editor.ui;

/** Every visible child fills the whole box; used to swap views by toggling visibility. */
public class Stack extends UiNode {
    @Override
    protected void onLayout() {
        for (UiNode child : this.children()) {
            child.setBounds(this.x(), this.y(), this.width(), this.height());
        }
    }
}

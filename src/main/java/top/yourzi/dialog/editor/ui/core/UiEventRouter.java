package top.yourzi.dialog.editor.ui.core;

/** Central event router with one pointer capture owner, preventing drag and popup leakage. */
public final class UiEventRouter {
    private UiNode root;
    private UiNode pointerCapture;
    private UiNode focused;

    public void setRoot(UiNode root) { this.root = root; }
    public UiNode pointerCapture() { return this.pointerCapture; }
    public UiNode focused() { return this.focused; }

    public boolean dispatch(UiEvent event) {
        if (this.root == null || event == null) return false;
        UiNode target = this.pointerCapture != null && isPointer(event)
                ? this.pointerCapture : findTarget(this.root, event.mouseX(), event.mouseY());
        if (event.type() == UiEvent.Type.KEY_DOWN || event.type() == UiEvent.Type.CHAR_TYPED) {
            target = this.focused != null ? this.focused : this.root;
        }
        if (target == null) target = this.root;
        java.util.ArrayList<UiNode> path = new java.util.ArrayList<>();
        for (UiNode node = target; node != null; node = node.parent()) path.add(node);
        for (int i = path.size() - 1; i >= 0 && !event.consumed(); i--) path.get(i).handleCapture(event);
        if (!event.consumed()) target.handleEvent(event);
        for (int i = 1; i < path.size() && !event.consumed(); i++) path.get(i).handleBubble(event);
        if (event.captureRequested()) this.pointerCapture = target;
        if (event.type() == UiEvent.Type.POINTER_UP && this.pointerCapture != null) this.pointerCapture = null;
        return event.consumed();
    }

    public void focus(UiNode node) { this.focused = node; }

    private static boolean isPointer(UiEvent event) {
        return event.type() == UiEvent.Type.POINTER_DOWN || event.type() == UiEvent.Type.POINTER_UP
                || event.type() == UiEvent.Type.POINTER_MOVE || event.type() == UiEvent.Type.SCROLL;
    }

    private static UiNode findTarget(UiNode node, double x, double y) {
        if (!node.hitTest(x, y)) return null;
        var children = node.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            UiNode target = findTarget(children.get(i), x, y);
            if (target != null) return target;
        }
        return node;
    }
}

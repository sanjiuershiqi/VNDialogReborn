package top.yourzi.dialog.editor.ui.core;

/** Rendering backend boundary. A future Skija renderer can implement this without changing widgets. */
public interface UiRenderer {
    void render(UiNode root, UiContext context);
}

package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * An overlay stacked above the workspace root: modal dialogs, dropdown popups, the help sheet.
 *
 * <p>Only the topmost layer receives pointer and keyboard input, so a popup can never leak clicks
 * into the panel underneath it. A {@code dismissOnOutsideClick} layer closes itself when the click
 * lands outside its own box and swallows that click.
 */
public final class Layer {
    private final UiNode node;
    private final boolean modal;
    private final boolean dismissOnOutsideClick;
    private Runnable onDismiss;

    public Layer(UiNode node, boolean modal, boolean dismissOnOutsideClick) {
        this.node = node;
        this.modal = modal;
        this.dismissOnOutsideClick = dismissOnOutsideClick;
    }

    public UiNode node() {
        return this.node;
    }

    public boolean modal() {
        return this.modal;
    }

    public boolean dismissOnOutsideClick() {
        return this.dismissOnOutsideClick;
    }

    public void setOnDismiss(Runnable onDismiss) {
        this.onDismiss = onDismiss;
    }

    void dismiss() {
        if (this.onDismiss != null) {
            this.onDismiss.run();
        }
    }

    void paint(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.node.paint(graphics, mouseX, mouseY, partialTick);
    }

    void flushLayout() {
        this.node.flushLayout();
    }
}

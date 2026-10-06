package top.yourzi.dialog.editor.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiNode;

import java.util.function.Supplier;

/**
 * Bottom strip: the last message on the left, document facts on the right. Errors stay until the
 * next message; everything else fades after a few seconds so stale news never looks current.
 */
final class StatusBar extends UiNode {
    private static final long TIMEOUT_MS = 5000L;

    private final Supplier<String> summary;
    private Component message = Component.empty();
    private EditorContext.StatusKind kind = EditorContext.StatusKind.INFO;
    private long shownAt;

    StatusBar(Supplier<String> summary) {
        this.summary = summary;
    }

    void set(Component message, EditorContext.StatusKind kind) {
        this.message = message;
        this.kind = kind;
        this.shownAt = System.currentTimeMillis();
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.x(), this.y(), this.right(), this.bottom(), Theme.SURFACE);
        graphics.fill(this.x(), this.y(), this.right(), this.y() + 1, Theme.BORDER);
        String right = this.summary.get();
        int rightWidth = Theme.font().width(right);
        Theme.textIn(graphics, right, this.right() - rightWidth - 6, this.y() + 1, rightWidth, this.height() - 1,
                Theme.TEXT_MUTED);
        boolean expired = this.kind != EditorContext.StatusKind.ERROR
                && System.currentTimeMillis() - this.shownAt > TIMEOUT_MS;
        if (expired || this.message.getString().isEmpty()) {
            return;
        }
        int color = switch (this.kind) {
            case SUCCESS -> Theme.SUCCESS;
            case WARNING -> Theme.WARNING;
            case ERROR -> Theme.DANGER;
            default -> Theme.TEXT_DIM;
        };
        graphics.fill(this.x() + 4, this.y() + 5, this.x() + 7, this.bottom() - 4, color);
        Theme.textIn(graphics, this.message.getString(), this.x() + 11, this.y() + 1,
                this.width() - rightWidth - 24, this.height() - 1, color);
    }
}

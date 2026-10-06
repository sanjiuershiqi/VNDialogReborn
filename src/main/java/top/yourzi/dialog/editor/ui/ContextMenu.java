package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Right-click menu. Dropped where the cursor was, closed by the first choice, and closed by a click
 * anywhere outside without letting that click reach the panel underneath.
 */
public final class ContextMenu extends UiNode {
    public record Item(Component label, Runnable action, boolean enabled) {
        public static Item of(Component label, Runnable action) {
            return new Item(label, action, true);
        }

        public static Item of(Component label, Runnable action, boolean enabled) {
            return new Item(label, action, enabled);
        }

        public static Item disabled(Component label) {
            return new Item(label, null, false);
        }

        public static Item separator() {
            return new Item(Component.empty(), null, false);
        }
    }

    private static final int ROW = 15;
    private static final int TITLE_H = 12;

    private final List<Item> items;
    private final Component title;
    private int hovered = -1;

    private ContextMenu(Component title, List<Item> items) {
        this.title = title;
        this.items = new ArrayList<>(items);
    }

    /** Opens the menu near {@code x}/{@code y}, flipped to stay on screen. */
    public static void open(UiHost host, int x, int y, Component title, List<Item> items) {
        ContextMenu menu = new ContextMenu(title, items);
        int width = 80;
        for (Item item : items) {
            width = Math.max(width, Theme.font().width(item.label().getString()) + 20);
        }
        int header = title == null ? 0 : TITLE_H;
        int height = items.size() * ROW + 4 + header;
        int menuX = Math.max(2, Math.min(x, host.width() - width - 2));
        int menuY = Math.max(2, Math.min(y, host.height() - height - 2));
        menu.setBounds(menuX, menuY, width, height);
        host.open(menu, false, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = this.indexAt(mouseY);
        if (index >= 0 && this.items.get(index).enabled() && this.items.get(index).action() != null) {
            Runnable action = this.items.get(index).action();
            this.dismissLayer();
            action.run();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.dismissLayer();
            return true;
        }
        return false;
    }

    private int indexAt(double mouseY) {
        int index = (int) ((mouseY - this.y() - 2 - this.titleHeight()) / ROW);
        return index >= 0 && index < this.items.size() ? index : -1;
    }

    private int titleHeight() {
        return this.title == null ? 0 : TITLE_H;
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.x(), this.y(), this.right(), this.bottom(), Theme.RAISED);
        Theme.border(graphics, this.x(), this.y(), this.width(), this.height(), Theme.BORDER_STRONG);
        if (this.title != null) {
            Theme.textIn(graphics, this.title.getString(), this.x() + 7, this.y() + 1, this.width() - 12, TITLE_H,
                    Theme.TEXT_MUTED);
            graphics.fill(this.x() + 1, this.y() + TITLE_H, this.right() - 1, this.y() + TITLE_H + 1, Theme.BORDER);
        }
        this.hovered = this.indexAt(mouseY);
        for (int i = 0; i < this.items.size(); i++) {
            Item item = this.items.get(i);
            int rowY = this.y() + 2 + this.titleHeight() + i * ROW;
            if (item.label().getString().isEmpty()) {
                graphics.fill(this.x() + 6, rowY + ROW / 2, this.right() - 6, rowY + ROW / 2 + 1, Theme.BORDER);
                continue;
            }
            boolean hover = i == this.hovered && item.enabled() && item.action() != null;
            if (hover) {
                graphics.fill(this.x() + 1, rowY, this.right() - 1, rowY + ROW, Theme.HOVER);
                graphics.fill(this.x() + 1, rowY, this.x() + 3, rowY + ROW, Theme.ACCENT);
            }
            int color = !item.enabled() ? Theme.TEXT_MUTED : hover ? Theme.TEXT : Theme.TEXT_DIM;
            Theme.textIn(graphics, item.label().getString(), this.x() + 9, rowY, this.width() - 14, ROW, color);
        }
    }
}

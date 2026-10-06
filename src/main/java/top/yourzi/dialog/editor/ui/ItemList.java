package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Scrollable single-column list of items.
 *
 * <p>Rows are painted directly instead of being widget instances, so a list holding hundreds of
 * dialogue nodes costs nothing to lay out. Selection, hover, scrolling, keyboard navigation and
 * type-to-filter live here; the caller only supplies items and an optional row decorator.
 */
public class ItemList<T> extends UiNode {
    /** Optional extra text drawn right-aligned on a row and used as its accent color. */
    public interface Decorator<T> {
        void decorate(GuiGraphics graphics, T item, int rowY, int x, int width, int height, boolean selected);
    }

    private final List<T> items = new ArrayList<>();
    private Function<T, Component> labeler = item -> Component.literal(String.valueOf(item));
    private Decorator<T> decorator;
    private Consumer<T> onSelect;
    private Consumer<T> onActivate;
    private Component emptyText;
    private int selectedIndex = -1;
    private int hoveredIndex = -1;
    private int scroll;
    private boolean draggingBar;
    private boolean activateOnClick;

    public ItemList<T> labeler(Function<T, Component> labeler) {
        this.labeler = labeler;
        return this;
    }

    public ItemList<T> decorator(Decorator<T> decorator) {
        this.decorator = decorator;
        return this;
    }

    public ItemList<T> onSelect(Consumer<T> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /** Double click or Enter on a row. */
    public ItemList<T> onActivate(Consumer<T> onActivate) {
        this.onActivate = onActivate;
        return this;
    }

    public ItemList<T> emptyText(Component emptyText) {
        this.emptyText = emptyText;
        return this;
    }

    /** Pickers choose on a single click instead of requiring a second click. */
    public ItemList<T> activateOnClick(boolean activateOnClick) {
        this.activateOnClick = activateOnClick;
        return this;
    }

    public List<T> items() {
        return this.items;
    }

    public void setItems(List<T> newItems) {
        this.items.clear();
        this.items.addAll(newItems);
        if (this.selectedIndex >= this.items.size()) {
            this.selectedIndex = this.items.size() - 1;
        }
    }

    public T selected() {
        return this.selectedIndex >= 0 && this.selectedIndex < this.items.size() ? this.items.get(this.selectedIndex) : null;
    }


    public void selectIndex(int index) {
        this.selectedIndex = index;
    }

    public void selectQuietly(Object item) {
        for (int i = 0; i < this.items.size(); i++) {
            if (this.items.get(i) == item) {
                this.selectedIndex = i;
                return;
            }
        }
    }


    private int rowHeight() {
        return Math.max(10, this.host() == null ? Theme.ROW : Theme.font().lineHeight + 5);
    }

    private int maxScroll() {
        return Math.max(0, this.items.size() * this.rowHeight() - this.height());
    }

    public void scrollToSelected() {
        if (this.selectedIndex < 0) {
            return;
        }
        int row = this.rowHeight();
        int top = this.selectedIndex * row;
        if (top < this.scroll) {
            this.scroll = top;
        } else if (top + row > this.scroll + this.height()) {
            this.scroll = top + row - this.height();
        }
        this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }
        if (this.maxScroll() > 0 && mouseX >= this.right() - Theme.SCROLLBAR - 2) {
            this.draggingBar = true;
            this.host().claimPointer(this);
            this.dragTo(mouseY);
            return true;
        }
        int index = (int) ((mouseY - this.y() + this.scroll) / this.rowHeight());
        if (index < 0 || index >= this.items.size()) {
            return true;
        }
        boolean sameRow = index == this.selectedIndex;
        this.selectedIndex = index;
        if (this.onSelect != null) {
            this.onSelect.accept(this.items.get(index));
        }
        this.host().focus(this);
        if ((sameRow || this.activateOnClick) && this.onActivate != null) {
            this.onActivate.accept(this.items.get(index));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingBar) {
            this.dragTo(mouseY);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingBar = false;
        return true;
    }

    private void dragTo(double mouseY) {
        double ratio = Mth.clamp((mouseY - this.y()) / Math.max(1, this.height()), 0, 1);
        this.scroll = (int) (ratio * this.maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.maxScroll() <= 0) {
            return false;
        }
        this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY) * this.rowHeight(), 0, this.maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.items.isEmpty()) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                int direction = keyCode == GLFW.GLFW_KEY_UP ? -1 : 1;
                int next = this.selectedIndex < 0 ? 0 : Mth.clamp(this.selectedIndex + direction, 0, this.items.size() - 1);
                this.moveSelection(next);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                this.moveSelection(Mth.clamp(this.selectedIndex - this.visibleRows(), 0, this.items.size() - 1));
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                this.moveSelection(Mth.clamp(this.selectedIndex + this.visibleRows(), 0, this.items.size() - 1));
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                this.moveSelection(0);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                this.moveSelection(this.items.size() - 1);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (this.selectedIndex >= 0 && this.onActivate != null) {
                    this.onActivate.accept(this.items.get(this.selectedIndex));
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private int visibleRows() {
        return Math.max(1, this.height() / this.rowHeight());
    }

    private void moveSelection(int index) {
        if (index == this.selectedIndex) {
            return;
        }
        this.selectedIndex = index;
        this.scrollToSelected();
        if (this.onSelect != null && index >= 0) {
            this.onSelect.accept(this.items.get(index));
        }
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.hoveredIndex = -1;
        int row = this.rowHeight();
        this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll());
        graphics.enableScissor(this.x(), this.y(), this.right(), this.bottom());
        for (int index = 0; index < this.items.size(); index++) {
            int rowY = this.y() + index * row - this.scroll;
            if (rowY + row < this.y() || rowY > this.bottom()) {
                continue;
            }
            boolean selected = index == this.selectedIndex;
            boolean hovered = mouseY >= rowY && mouseY < rowY + row && mouseX >= this.x() && mouseX < this.right();
            if (hovered) {
                this.hoveredIndex = index;
            }
            if (selected) {
                graphics.fill(this.x(), rowY, this.right(), rowY + row, Theme.SELECTED);
                graphics.fill(this.x(), rowY, this.x() + 2, rowY + row, Theme.ACCENT);
            } else if (hovered) {
                graphics.fill(this.x(), rowY, this.right(), rowY + row, Theme.HOVER);
            }
            int textColor = selected ? Theme.TEXT : hovered ? Theme.TEXT : Theme.TEXT_DIM;
            Theme.textIn(graphics, this.labeler.apply(this.items.get(index)).getString(), this.x() + 6, rowY,
                    this.width() - 12, row, textColor);
            if (this.decorator != null) {
                this.decorator.decorate(graphics, this.items.get(index), rowY, this.x(), this.width(), row, selected);
            }
        }
        graphics.disableScissor();
        if (this.items.isEmpty() && this.emptyText != null) {
            Theme.centered(graphics, this.emptyText.getString(), this.x() + this.width() / 2,
                    this.y() + this.height() / 2 - 4, Theme.TEXT_MUTED);
        }
        Theme.scrollbar(graphics, this.right() - Theme.SCROLLBAR, this.y(), this.height(), this.height(),
                this.items.size() * row, this.scroll, this.draggingBar);
    }
}

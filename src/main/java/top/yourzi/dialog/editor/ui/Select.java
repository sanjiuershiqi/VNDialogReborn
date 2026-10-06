package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Dropdown selector.
 *
 * <p>The closed state is one row; the open state is a popup {@link Layer}, so the option list can
 * escape the inspector's clipping and no click can leak into the panel behind it.
 */
public class Select<T> extends UiNode {
    private static final int MAX_ROWS = 10;
    private static final int ROW = 14;

    private final List<T> items = new ArrayList<>();
    private final Function<T, Component> labeler;
    private final Consumer<T> onSelect;
    private T selected;
    private Layer layer;

    public Select(List<T> items, T selected, Function<T, Component> labeler, Consumer<T> onSelect) {
        this.items.addAll(items);
        this.selected = selected;
        this.labeler = labeler;
        this.onSelect = onSelect;
    }

    public T selected() {
        return this.selected;
    }

    public void setSelected(T selected) {
        this.selected = selected;
    }

    private void close() {
        if (this.layer != null && this.host() != null) {
            Layer open = this.layer;
            this.layer = null;
            this.host().close(open);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        this.host().focus(null);
        if (this.layer != null) {
            this.close();
        } else {
            this.openPopup();
        }
        return true;
    }

    private void openPopup() {
        ItemList<T> list = new ItemList<>();
        list.labeler(this.labeler);
        list.setItems(new ArrayList<>(this.items));
        list.selectQuietly(this.selected);
        list.activateOnClick(true);
        list.onActivate(item -> {
            this.selected = item;
            this.close();
            this.onSelect.accept(item);
        });
        int height = Math.min(MAX_ROWS, Math.max(1, this.items.size())) * ROW + 2;
        int width = this.width();
        for (T item : this.items) {
            width = Math.max(width, Theme.font().width(this.labeler.apply(item).getString()) + 16);
        }
        UiHost host = this.host();
        width = Math.min(width, Math.max(40, host.width() - 4));
        int popupX = Math.max(2, Math.min(this.x(), host.width() - width - 2));
        int popupY = this.bottom() + 1;
        if (popupY + height > host.height()) {
            popupY = Math.max(0, this.y() - height - 1);
        }
        Panel panel = Panel.flat(list, Theme.RAISED, Theme.BORDER_STRONG).padding(1);
        panel.setBounds(popupX, popupY, width, height);
        this.layer = host.open(panel, false, true);
        this.layer.setOnDismiss(() -> this.layer = null);
        list.scrollToSelected();
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHovered() || this.layer != null;
        graphics.fill(this.x(), this.y(), this.right(), this.bottom(), hovered ? Theme.HOVER : Theme.RAISED);
        Theme.border(graphics, this.x(), this.y(), this.width(), this.height(),
                this.layer != null ? Theme.ACCENT : hovered ? Theme.BORDER_STRONG : Theme.BORDER);
        String label = this.selected == null ? "" : this.labeler.apply(this.selected).getString();
        Theme.textIn(graphics, label, this.x() + Theme.PAD, this.y(), this.width() - Theme.PAD * 2 - 8,
                this.height(), Theme.TEXT);
        int arrowX = this.right() - 10;
        int arrowY = this.y() + this.height() / 2 - 1;
        graphics.fill(arrowX, arrowY, arrowX + 5, arrowY + 1, Theme.TEXT_DIM);
        graphics.fill(arrowX + 1, arrowY + 1, arrowX + 4, arrowY + 2, Theme.TEXT_DIM);
        graphics.fill(arrowX + 2, arrowY + 2, arrowX + 3, arrowY + 3, Theme.TEXT_DIM);
    }
}

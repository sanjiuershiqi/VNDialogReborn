package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 下拉选择框组件。融合自 visual_mod_edit_vndialog。
 */
public class DropdownWidget extends AbstractWidget {
    private final Font font;
    private List<String> items = new ArrayList<>();
    private int selectedIndex = -1;
    private boolean expanded = false;
    private final Consumer<String> onSelected;
    private static final int MAX_VISIBLE = 6;
    private int scrollOffset = 0;
    private static final int ITEM_HEIGHT = 10;

    public DropdownWidget(Font font, int x, int y, int width, int height, List<String> items, Consumer<String> onSelected) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.items = items;
        this.onSelected = onSelected;
    }

    public void setItems(List<String> items) {
        this.items = items != null ? items : new ArrayList<>();
        this.selectedIndex = -1;
        this.scrollOffset = 0;
    }

    public String getSelected() {
        return selectedIndex >= 0 && selectedIndex < items.size() ? items.get(selectedIndex) : "";
    }

    public void setSelected(String value) {
        if (items != null && value != null) {
            selectedIndex = items.indexOf(value);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), -14540254);
        String text = selectedIndex >= 0 ? items.get(selectedIndex) : "";
        if (text.length() > 20) {
            text = text.substring(0, 17) + "...";
        }
        graphics.drawString(font, text, getX() + 3, getY() + (getHeight() - 8) / 2, 0xFFFFFF, false);
        graphics.drawString(font, expanded ? "\u25b2" : "\u25bc", getX() + getWidth() - 10, getY() + (getHeight() - 8) / 2, 0xAAAAAA, false);
        if (expanded && !items.isEmpty()) {
            int dropY = getY() + getHeight();
            int totalHeight = Math.min(MAX_VISIBLE, items.size()) * ITEM_HEIGHT;
            graphics.fill(getX() + 1, dropY, getX() + getWidth() - 1, dropY + totalHeight + 2, -869059789);
            graphics.enableScissor(getX(), dropY, getX() + getWidth(), dropY + totalHeight + 2);
            for (int i = 0; i < items.size(); i++) {
                int rowY = dropY + 1 + i * ITEM_HEIGHT - scrollOffset * ITEM_HEIGHT;
                if (rowY + ITEM_HEIGHT < dropY || rowY > dropY + totalHeight) continue;
                boolean hovered = mouseX >= getX() && mouseX <= getX() + getWidth() && mouseY >= rowY && mouseY <= rowY + ITEM_HEIGHT;
                int bg = hovered ? -1 : (i == selectedIndex ? -1996488705 : 0x55555555);
                graphics.fill(getX() + 1, rowY, getX() + getWidth() - 1, rowY + ITEM_HEIGHT, bg);
                String itemText = items.get(i);
                if (itemText.length() > 22) {
                    itemText = itemText.substring(0, 19) + "...";
                }
                graphics.drawString(font, itemText, getX() + 3, rowY + 1, -16777216, false);
            }
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !active) return false;
        if (button == 0) {
            if (expanded) {
                int dropY = getY() + getHeight();
                int relY = (int) (mouseY - dropY - 1.0);
                int index = relY / ITEM_HEIGHT + scrollOffset;
                if (mouseX >= getX() && mouseX <= getX() + getWidth() && index >= 0 && index < items.size()) {
                    selectedIndex = index;
                    expanded = false;
                    if (onSelected != null) {
                        onSelected.accept(items.get(index));
                    }
                    return true;
                }
                expanded = false;
            } else if (mouseY >= getY() && mouseY <= getY() + getHeight()) {
                expanded = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (expanded && isMouseOver(mouseX, mouseY)) {
            scrollOffset = Mth.clamp(scrollOffset - (int) scrollY, 0, Math.max(0, items.size() - MAX_VISIBLE));
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, Component.literal("Dropdown"));
    }
}

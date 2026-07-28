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
 * 下拉列表控件。移植自 visual_mod_edit_vndialog，适配 NeoForge 1.21.1。
 * 用于动画类型、渲染方式等枚举选择，展开后可看到所有选项。
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
        return this.selectedIndex >= 0 && this.selectedIndex < this.items.size() ? this.items.get(this.selectedIndex) : "";
    }

    public void setSelected(String value) {
        if (this.items != null && value != null) {
            this.selectedIndex = this.items.indexOf(value);
        }
    }

    public List<String> getItems() {
        return this.items;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), -14540254);
        String text = this.selectedIndex >= 0 ? this.items.get(this.selectedIndex) : "";
        if (text.length() > 20) {
            text = text.substring(0, 17) + "...";
        }
        graphics.drawString(this.font, text, this.getX() + 3, this.getY() + (this.getHeight() - 8) / 2, 0xFFFFFF);
        graphics.drawString(this.font, this.expanded ? "\u25b2" : "\u25bc", this.getX() + this.getWidth() - 10, this.getY() + (this.getHeight() - 8) / 2, 0xAAAAAA);
        if (this.expanded && !this.items.isEmpty()) {
            int dropY = this.getY() + this.getHeight();
            int totalHeight = Math.min(MAX_VISIBLE, this.items.size()) * ITEM_HEIGHT;
            graphics.fill(this.getX() + 1, dropY, this.getX() + this.getWidth() - 1, dropY + totalHeight + 2, -869059789);
            graphics.enableScissor(this.getX(), dropY, this.getX() + this.getWidth(), dropY + totalHeight + 2);
            try {
                for (int i = 0; i < this.items.size(); i++) {
                    int rowY = dropY + 1 + i * ITEM_HEIGHT - this.scrollOffset * ITEM_HEIGHT;
                    if (rowY + ITEM_HEIGHT < dropY || rowY > dropY + totalHeight) {
                        continue;
                    }
                    boolean hovered = mouseX >= this.getX() && mouseX <= this.getX() + this.getWidth() && mouseY >= rowY && mouseY <= rowY + ITEM_HEIGHT;
                    int bg = hovered ? -1 : (i == this.selectedIndex ? -1996488705 : 0x55555555);
                    graphics.fill(this.getX() + 1, rowY, this.getX() + this.getWidth() - 1, rowY + ITEM_HEIGHT, bg);
                    String itemText = this.items.get(i);
                    if (itemText.length() > 22) {
                        itemText = itemText.substring(0, 19) + "...";
                    }
                    graphics.drawString(this.font, itemText, this.getX() + 3, rowY + 1, -16777216);
                }
            } finally {
                graphics.disableScissor();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.active) {
            return false;
        }
        if (button == 0) {
            if (this.expanded) {
                int dropY = this.getY() + this.getHeight();
                int relY = (int) (mouseY - (double) dropY - 1.0);
                int index = relY / ITEM_HEIGHT + this.scrollOffset;
                if (mouseX >= (double) this.getX() && mouseX <= (double) (this.getX() + this.getWidth()) && index >= 0 && index < this.items.size()) {
                    this.selectedIndex = index;
                    this.expanded = false;
                    if (this.onSelected != null) {
                        this.onSelected.accept(this.items.get(index));
                    }
                    return true;
                }
                this.expanded = false;
            } else if (mouseY >= (double) this.getY() && mouseY <= (double) (this.getY() + this.getHeight())) {
                this.expanded = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.expanded && this.isMouseOver(mouseX, mouseY)) {
            this.scrollOffset = Mth.clamp((int) (this.scrollOffset - (int) scrollY), 0, Math.max(0, this.items.size() - MAX_VISIBLE));
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, Component.literal("Dropdown"));
    }
}

package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Checkbox row: box on the left, label after it, whole row clickable. */
public class Toggle extends UiNode {
    private static final int BOX = 10;

    private final Component label;
    private final Consumer<Boolean> onChange;
    private boolean value;

    public Toggle(Component label, boolean value, Consumer<Boolean> onChange) {
        this.label = label;
        this.value = value;
        this.onChange = onChange;
    }

    public boolean value() {
        return this.value;
    }

    public Toggle setValue(boolean value) {
        this.value = value;
        return this;
    }

    @Override
    public int measureHeight(int width) {
        return 14;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }
        this.host().focus(null);
        this.value = !this.value;
        this.onChange.accept(this.value);
        return true;
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int boxY = this.y() + (this.height() - BOX) / 2;
        int fill = this.value ? Theme.ACCENT : Theme.FIELD;
        graphics.fill(this.x(), boxY, this.x() + BOX, boxY + BOX, fill);
        Theme.border(graphics, this.x(), boxY, BOX, BOX, this.value ? Theme.ACCENT : Theme.BORDER_STRONG);
        if (this.value) {
            graphics.fill(this.x() + 2, boxY + 2, this.x() + BOX - 2, boxY + BOX - 2, 0xFF241F0A);
        }
        Theme.textIn(graphics, this.label.getString(), this.x() + BOX + 6, this.y(),
                this.width() - BOX - 6, this.height(), this.isHovered() ? Theme.TEXT : Theme.TEXT_DIM);
    }
}

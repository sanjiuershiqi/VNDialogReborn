package top.yourzi.dialog.editor.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiNode;

import java.util.Locale;
import java.util.function.Consumer;

/** Label, track and value readout for a bounded float, dragged or clicked anywhere on the track. */
final class Slider extends UiNode {
    private final Component label;
    private final float min;
    private final float max;
    private final Consumer<Float> onChange;
    private float value;
    private boolean dragging;
    private final int decimals = 2;

    Slider(Component label, float min, float max, float value, Consumer<Float> onChange) {
        this.label = label;
        this.min = min;
        this.max = max;
        this.value = value;
        this.onChange = onChange;
    }


    void setValue(float value) {
        this.value = Mth.clamp(value, this.min, this.max);
    }

    @Override
    public int measureHeight(int width) {
        return Theme.ROW;
    }

    private int trackX() {
        return this.x() + Theme.LABEL_W;
    }

    private int trackWidth() {
        return Math.max(20, this.width() - Theme.LABEL_W - 42);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }
        this.host().claimPointer(this);
        this.dragging = true;
        this.applyFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging) {
            this.applyFromMouse(mouseX);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = false;
        return true;
    }

    private void applyFromMouse(double mouseX) {
        float ratio = Mth.clamp((float) (mouseX - this.trackX()) / this.trackWidth(), 0.0f, 1.0f);
        this.value = this.min + ratio * (this.max - this.min);
        this.onChange.accept(this.value);
    }


    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Theme.textIn(graphics, this.label.getString(), this.x(), this.y(), Theme.LABEL_W - 4, this.height(),
                Theme.TEXT_DIM);
        int trackX = this.trackX();
        int trackWidth = this.trackWidth();
        int centerY = this.y() + this.height() / 2;
        graphics.fill(trackX, centerY - 1, trackX + trackWidth, centerY + 1, Theme.FIELD);
        float ratio = (this.value - this.min) / Math.max(0.0001f, this.max - this.min);
        int filled = (int) (ratio * trackWidth);
        graphics.fill(trackX, centerY - 1, trackX + filled, centerY + 1, Theme.ACCENT);
        int knobX = trackX + filled;
        graphics.fill(knobX - 2, centerY - 4, knobX + 2, centerY + 4,
                this.dragging ? Theme.TEXT : Theme.TEXT_DIM);
        String text = String.format(Locale.ROOT, "%." + this.decimals + "f", this.value);
        Theme.textIn(graphics, text, this.right() - 36, this.y(), 36, this.height(), Theme.TEXT);
    }
}

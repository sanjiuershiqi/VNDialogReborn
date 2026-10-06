package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Flat themed button. Tones carry meaning: primary confirms, danger destroys, ghost stays quiet. */
public class Button extends UiNode {
    public enum Tone {
        NORMAL,
        PRIMARY,
        GHOST,
        DANGER
    }

    private Component label;
    private Consumer<Button> action;
    private Tone tone = Tone.NORMAL;
    private boolean active = true;
    private boolean selected;
    private int swatch;

    public Button(Component label, Consumer<Button> action) {
        this.label = label;
        this.action = action;
    }

    public static Button of(Component label, Runnable action) {
        return new Button(label, button -> action.run());
    }

    public Button tone(Tone tone) {
        this.tone = tone;
        return this;
    }

    /** Sizes the button to its label, for toolbars. */
    public Button fit() {
        this.prefWidth(Theme.font().width(this.label.getString()) + 14);
        return this;
    }

    /** Draws the button as a solid colour chip, used by the formatting palette. */
    public Button swatchColor(int rgb) {
        this.swatch = 0xFF000000 | rgb;
        return this;
    }

    /** Disabled buttons stay visible but ignore clicks. */
    public Button active(boolean active) {
        this.active = active;
        return this;
    }

    /** Selected buttons render as the current page of a segmented control. */
    public Button selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public void setAction(Runnable action) {
        this.action = button -> action.run();
    }

    public Component label() {
        return this.label;
    }

    public void setLabel(Component label) {
        this.label = label;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (!this.active) {
            return true;
        }
        this.host().focus(null);
        this.action.accept(this);
        return true;
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.active && this.isHovered();
        if (this.swatch != 0) {
            graphics.fill(this.x(), this.y(), this.right(), this.bottom(), this.swatch);
            Theme.border(graphics, this.x(), this.y(), this.width(), this.height(), hovered ? Theme.TEXT : Theme.BORDER);
            return;
        }
        int fill;
        int border;
        int text;
        if (!this.active) {
            fill = Theme.SURFACE;
            border = Theme.BORDER;
            text = Theme.TEXT_MUTED;
        } else if (this.selected) {
            fill = Theme.SELECTED;
            border = Theme.ACCENT;
            text = Theme.TEXT;
        } else {
            switch (this.tone) {
                case PRIMARY -> {
                    fill = hovered ? 0xFFE8CE45 : Theme.ACCENT;
                    border = Theme.ACCENT;
                    text = 0xFF1A1A12;
                }
                case DANGER -> {
                    fill = hovered ? 0xFFF07C6E : Theme.DANGER;
                    border = Theme.DANGER;
                    text = 0xFF201210;
                }
                case GHOST -> {
                    fill = hovered ? Theme.HOVER : Theme.SURFACE;
                    border = hovered ? Theme.BORDER_STRONG : Theme.SURFACE;
                    text = Theme.TEXT_DIM;
                }
                default -> {
                    fill = hovered ? Theme.HOVER : Theme.RAISED;
                    border = Theme.BORDER;
                    text = Theme.TEXT;
                }
            }
        }
        graphics.fill(this.x(), this.y(), this.right(), this.bottom(), fill);
        Theme.border(graphics, this.x(), this.y(), this.width(), this.height(), border);
        Theme.centered(graphics, Theme.ellipsize(this.label.getString(), this.width() - 6),
                this.x() + this.width() / 2, this.y() + (this.height() - 8) / 2, text);
    }
}

package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Flat button. Tones carry meaning: primary confirms, danger destroys, ghost stays quiet, and tab
 * marks the current page of a segmented control with an underline.
 */
public class Button extends UiNode {
    public enum Tone {
        NORMAL,
        PRIMARY,
        GHOST,
        DANGER,
        TAB
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
            if (hovered) {
                Theme.border(graphics, this.x(), this.y(), this.width(), this.height(), Theme.TEXT);
            }
            return;
        }
        int fill = 0;
        int text;
        if (this.tone == Tone.TAB) {
            text = this.selected || hovered ? Theme.TEXT : Theme.TEXT_MUTED;
            if (this.selected) {
                graphics.fill(this.x() + 2, this.bottom() - 2, this.right() - 2, this.bottom(), Theme.ACCENT);
            }
        } else if (!this.active) {
            fill = this.tone == Tone.NORMAL || this.tone == Tone.PRIMARY ? Theme.SURFACE : 0;
            text = Theme.TEXT_MUTED;
        } else if (this.selected) {
            fill = Theme.SELECTED;
            text = Theme.TEXT;
        } else {
            switch (this.tone) {
                case PRIMARY -> {
                    fill = hovered ? 0xFF85ACFF : Theme.ACCENT;
                    text = 0xFF0E1526;
                }
                case DANGER -> {
                    fill = hovered ? 0x40EC7A8C : 0;
                    text = Theme.DANGER;
                }
                case GHOST -> {
                    fill = hovered ? Theme.HOVER : 0;
                    text = hovered ? Theme.TEXT : Theme.TEXT_DIM;
                }
                default -> {
                    fill = hovered ? Theme.HOVER : Theme.RAISED;
                    text = Theme.TEXT;
                }
            }
        }
        if (fill != 0) {
            graphics.fill(this.x(), this.y(), this.right(), this.bottom(), fill);
        }
        Theme.centered(graphics, Theme.ellipsize(this.label.getString(), this.width() - 6),
                this.x() + this.width() / 2, this.y() + (this.height() - 8) / 2, text);
    }
}
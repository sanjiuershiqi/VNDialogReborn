package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Small static nodes used to build forms declaratively. */
public final class Nodes {
    private Nodes() {
    }

    /** Section title with an accent anchor and a divider. */
    public static UiNode section(Component title) {
        return new Section(title);
    }

    /** Muted field caption. */
    public static Label caption(Component text) {
        return new Label(text, Theme.TEXT_MUTED).prefHeight(10);
    }

    /** Fixed-size empty node used to push siblings apart. */
    public static UiNode spacer(int width) {
        return new Spacer().prefWidth(width).prefHeight(0);
    }

    /** Spacer that absorbs leftover width in a {@link Row}. */
    public static UiNode fill() {
        return new Spacer().flex(1).prefHeight(0);
    }

    private static final class Section extends UiNode {
        private final Component title;

        Section(Component title) {
            this.title = title;
            this.prefHeight(16);
        }

        @Override
        protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int top = this.y() + 2;
            graphics.fill(this.x(), top, this.right(), top + 13, Theme.RAISED);
            graphics.fill(this.x(), top + 3, this.x() + 2, top + 10, Theme.ACCENT);
            Theme.textIn(graphics, this.title.getString(), this.x() + 7, top, this.width() - 10, 13, Theme.TEXT);
        }
    }

    private static final class Spacer extends UiNode {
        Spacer() {
            this.setEnabled(false);
        }
    }

    /** Single-line text whose content can change after construction. */
    public static final class Label extends UiNode {
        private Component text;
        private int color;

        public Label(Component text, int color) {
            this.text = text;
            this.color = color;
            this.prefHeight(11);
        }

        public void setText(Component text) {
            this.text = text;
        }

        public void setColor(int color) {
            this.color = color;
        }

        @Override
        protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Theme.textIn(graphics, this.text.getString(), this.x(), this.y(), this.width(), this.height(), this.color);
        }
    }
}

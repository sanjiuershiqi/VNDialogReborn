package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Static text row that wraps to the container width and sizes itself from the wrapped line count.
 * Used for explanations, validation summaries and help copy.
 */
public class Paragraph extends UiNode {
    private final Component text;
    private int color = Theme.TEXT_DIM;

    public Paragraph(Component text) {
        this.text = text;
    }

    public static Paragraph of(Component text) {
        return new Paragraph(text);
    }

    public Paragraph color(int color) {
        this.color = color;
        return this;
    }

    @Override
    public int measureHeight(int width) {
        if (width <= 0) {
            return 10;
        }
        return Math.max(1, this.wrapped(width).size()) * 10 + 2;
    }

    private List<String> wrapped(int width) {
        return Wrap.text(this.text, Math.max(10, width - 2));
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<String> lines = this.wrapped(this.width());
        for (int i = 0; i < lines.size(); i++) {
            Theme.text(graphics, lines.get(i), this.x() + 1, this.y() + i * 10 + 1, this.color);
        }
    }
}

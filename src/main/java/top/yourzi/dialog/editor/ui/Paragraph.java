package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Read-only text that wraps to the container width and sizes itself from the line count. */
public class Paragraph extends UiNode {
    private static final int LINE_H = 10;

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
        return Wrap.lines(this.text, width - 2).size() * LINE_H + 2;
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int y = this.y() + 1;
        for (FormattedCharSequence line : Wrap.lines(this.text, this.width() - 2)) {
            graphics.drawString(Theme.font(), line, this.x() + 1, y, this.color, false);
            y += LINE_H;
        }
    }
}

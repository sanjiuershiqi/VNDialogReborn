package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.gui.property.Option;
import top.yourzi.dialog.editor.util.EditorTheme;

/**
 * 布尔选项行：自绘复选框 + 标签，绑定 {@link Option}。
 *
 * 借鉴 Sparkle-Morpher 的 BooleanOptionRow：读 option.get() 渲染，onClick 翻转 option.set。
 * dirty 视觉：dirty 时标签 TEXT_PRIMARY、复选框边框 ACCENT；干净时标签 TEXT_SECONDARY、边框 BORDER_LIGHT。
 * 整行点击均可翻转（与原 Checkbox 行为一致），不限定命中复选框小区域。
 */
public class BooleanOptionRow extends AbstractWidget {
    private static final int BOX_SIZE = 12;
    private final Option<Boolean> option;
    private final Font font;

    public BooleanOptionRow(int x, int y, int width, int height, Component label, Option<Boolean> option, Font font) {
        super(x, y, width, height, label);
        this.option = option;
        this.font = font;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean checked = Boolean.TRUE.equals(option.get());
        boolean dirty = option.isDirty();
        int boxX = getX() + 2;
        int boxY = getY() + (getHeight() - BOX_SIZE) / 2;
        int borderColor = dirty ? EditorTheme.ACCENT : EditorTheme.BORDER_LIGHT;
        g.fill(boxX, boxY, boxX + BOX_SIZE, boxY + BOX_SIZE, EditorTheme.BG_SURFACE);
        g.renderOutline(boxX, boxY, BOX_SIZE, BOX_SIZE, borderColor);
        if (checked) {
            // 选中：内填 ACCENT + 白色对勾符号
            g.fill(boxX + 2, boxY + 2, boxX + BOX_SIZE - 2, boxY + BOX_SIZE - 2, EditorTheme.ACCENT);
            g.drawCenteredString(font, "\u2713", boxX + BOX_SIZE / 2, boxY + 1, EditorTheme.TEXT_PRIMARY);
        }
        int labelColor = dirty ? EditorTheme.TEXT_PRIMARY : EditorTheme.TEXT_SECONDARY;
        g.drawString(font, getMessage(), boxX + BOX_SIZE + 6, getY() + (getHeight() - 8) / 2, labelColor);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        option.set(!option.get());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, getMessage());
    }
}

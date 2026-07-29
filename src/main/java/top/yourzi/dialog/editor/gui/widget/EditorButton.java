package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.yourzi.dialog.editor.util.EditorTheme;

/**
 * 编辑器风格按钮：替代原生石质按钮，与编辑器暗色主题统一。
 * 保持与原生 Button 相同的 builder 模式和事件接口，便于直接替换。
 */
public class EditorButton extends AbstractButton {
    private OnPress onPress;
    private boolean focused = false;

    public EditorButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public interface OnPress {
        void onPress(EditorButton button);
    }

    public static Builder builder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
    public void onPress() {
        if (this.onPress != null) {
            this.onPress.onPress(this);
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();

        // 背景色：根据状态选择
        int bgColor;
        if (!this.active) {
            bgColor = EditorTheme.BG_SURFACE;
        } else if (this.isHoveredOrFocused()) {
            bgColor = EditorTheme.BG_HOVER;
        } else {
            bgColor = EditorTheme.BG_ELEVATED;
        }
        graphics.fill(x, y, x + w, y + h, bgColor);

        // 边框：悬停/聚焦时使用强调色，否则使用普通边框色
        int borderColor = this.isHoveredOrFocused() && this.active ? EditorTheme.ACCENT : EditorTheme.BORDER;
        graphics.fill(x, y, x + w, y + 1, borderColor);
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        graphics.fill(x, y, x + 1, y + h, borderColor);
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor);

        // 计算文字颜色：根据按钮状态
        int textColor;
        if (!this.active) {
            textColor = EditorTheme.TEXT_MUTED;
        } else if (this.isHoveredOrFocused()) {
            textColor = EditorTheme.TEXT_PRIMARY;
        } else {
            textColor = EditorTheme.TEXT_SECONDARY;
        }

        // 使用原版滚动文字渲染：文字超长时自动滚动（与原版 Button 行为一致）
        // drawCenteredString 内部的 Font.draw 会优先使用 Component 自带的 Style 颜色，
        // 因此颜色按钮（■ 带颜色样式）会显示自身颜色，普通按钮使用 textColor
        Component message = this.getMessage();
        int textY = y + (h - 8) / 2;
        int textW = font.width(message);
        int padding = 2;
        int availW = w - padding * 2;

        if (textW > availW) {
            // 文字超长：使用滚动渲染（模拟原版按钮行为）
            int scrollAmount = textW - availW;
            double time = (double) System.currentTimeMillis() / 1000.0;
            double speed = Math.max(scrollAmount * 0.5, 3.0);
            double wave = Math.sin((Math.PI / 2) * Math.cos((Math.PI * 2) * time / speed)) / 2.0 + 0.5;
            double offset = Mth.lerp(wave, 0.0, scrollAmount);
            graphics.enableScissor(x + padding, y + 1, x + w - padding, y + h - 1);
            graphics.drawString(font, message, x + padding - (int) offset, textY, textColor, false);
            graphics.disableScissor();
        } else {
            // 文字不超长：居中显示
            graphics.drawCenteredString(font, message, x + w / 2, textY, textColor);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 焦点感知：未聚焦时不响应空格/回车
        if (!this.focused && (keyCode == 32 || keyCode == 257 || keyCode == 335)) {
            return false;
        }
        if (this.active && this.visible) {
            if (keyCode == 32 || keyCode == 257 || keyCode == 335) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                this.onPress();
                return true;
            }
        }
        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean isFocused() {
        return this.focused;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        this.defaultButtonNarrationText(narration);
    }

    /**
     * Builder 模式，兼容原生 Button.builder 的使用方式。
     */
    public static class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width;
        private int height = 18;
        private boolean active = true;

        public Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public EditorButton build() {
            EditorButton btn = new EditorButton(this.x, this.y, this.width, this.height, this.message, this.onPress);
            btn.active = this.active;
            return btn;
        }
    }
}

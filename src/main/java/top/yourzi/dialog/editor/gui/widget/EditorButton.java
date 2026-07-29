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
        // 上边框
        graphics.fill(x, y, x + w, y + 1, borderColor);
        // 下边框
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        // 左边框
        graphics.fill(x, y, x + 1, y + h, borderColor);
        // 右边框
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor);

        // 文字
        int textColor;
        if (!this.active) {
            textColor = EditorTheme.TEXT_MUTED;
        } else if (this.isHoveredOrFocused()) {
            textColor = EditorTheme.TEXT_PRIMARY;
        } else {
            textColor = EditorTheme.TEXT_SECONDARY;
        }

        // 文字截断以适应按钮宽度
        Component message = this.getMessage();
        String text = message.getString();
        int maxWidth = w - 6;
        if (font.width(text) > maxWidth) {
            text = font.plainSubstrByWidth(text, maxWidth - 8);
            text = text + "...";
        }
        graphics.drawCenteredString(font, Component.literal(text), x + w / 2, y + (h - 8) / 2, textColor);
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

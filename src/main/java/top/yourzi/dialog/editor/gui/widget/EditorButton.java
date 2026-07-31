package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.yourzi.dialog.editor.gui.EditorRenderHelper;
import top.yourzi.dialog.editor.util.EditorTheme;

/**
 * 编辑器风格按钮：替代原生石质按钮，与编辑器暗色主题统一。
 * 保持与原生 Button 相同的 builder 模式和事件接口，便于直接替换。
 */
public class EditorButton extends AbstractButton {
    private OnPress onPress;
    private boolean focused = false;
    /** hover 渐变进度（0=未 hover，1=hover），每帧 lerp 推进，避免布尔硬切换跳变。借鉴 Sparkle blendBg。 */
    private float hoverProgress = 0f;
    /** 上一帧纳秒时间戳，用于计算 dt 驱动 hoverProgress lerp。 */
    private long lastFrameNanos = 0L;

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

        // 第八轮美化：hover 渐变 lerp，避免布尔硬切换跳变（借鉴 Sparkle blendBg）
        long now = System.nanoTime();
        float dt = this.lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - this.lastFrameNanos) / 1.0e9f);
        this.lastFrameNanos = now;
        float targetHover = (this.active && this.isHoveredOrFocused()) ? 1f : 0f;
        this.hoverProgress = EditorRenderHelper.tickProgress(this.hoverProgress, targetHover, dt);

        // 背景色：disabled 固定；enabled 用 lerp 从 BG_ELEVATED 渐变到 BG_HOVER
        int bgColor;
        if (!this.active) {
            bgColor = EditorTheme.BG_SURFACE;
        } else {
            bgColor = EditorRenderHelper.lerpColor(EditorTheme.BG_ELEVATED, EditorTheme.BG_HOVER, this.hoverProgress);
        }
        // 第八轮美化：圆角填充（radius=2），边缘柔和
        EditorRenderHelper.fillRoundedRect(graphics, x, y, w, h, 2, bgColor);

        // 边框：lerp 从 BORDER 渐变到 ACCENT
        int borderColor = !this.active ? EditorTheme.BORDER
                : EditorRenderHelper.lerpColor(EditorTheme.BORDER, EditorTheme.ACCENT, this.hoverProgress);
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
        // 第八轮美化：hover 时文字加阴影，让文字在背景变化时更"浮出"
        boolean textShadow = this.active && this.isHoveredOrFocused();

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
            graphics.drawString(font, message, x + padding - (int) offset, textY, textColor, textShadow);
            graphics.disableScissor();
        } else {
            // 文字不超长：居中显示（drawCenteredString 无 shadow 重载，用 drawString 手动居中）
            int cx = x + (w - textW) / 2;
            graphics.drawString(font, message, cx, textY, textColor, textShadow);
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

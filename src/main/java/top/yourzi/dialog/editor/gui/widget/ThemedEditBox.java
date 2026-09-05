package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.util.EditorTheme;

/**
 * 编辑器统一输入框。Minecraft 原生 EditBox 的行为保持不变，只收口文字与边框色，
 * 让所有编辑子屏使用同一种输入组件视觉。
 */
public class ThemedEditBox extends EditBox {
    public ThemedEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        this.setTextColor(EditorTheme.TEXT_PRIMARY);
        this.setTextColorUneditable(EditorTheme.TEXT_MUTED);
        this.setBordered(true);
    }
}

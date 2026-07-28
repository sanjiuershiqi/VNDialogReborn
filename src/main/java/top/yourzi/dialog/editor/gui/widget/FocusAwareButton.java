package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 焦点感知按钮，防止在未聚焦时响应空格/回车。融合自 visual_mod_edit_vndialog。
 */
public class FocusAwareButton extends Button {

    public FocusAwareButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() && (keyCode == 32 || keyCode == 257 || keyCode == 335)) {
            return false;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

package top.yourzi.dialog.ui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class OptionButton extends Button {
    public OptionButton(int x, int y, int width, int height, int xTexStart, int yTexStart, int yDiffText,
            ResourceLocation resourceLocation, int textureWidth, int textureHeight,
            OnPress onPress, Component message) {
        super(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
    }
}

package top.yourzi.dialog.editor.gui.property;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogSequence;

import java.util.List;

/**
 * 属性页接口。融合自 visual_mod_edit_vndialog，适配 NeoForge 1.21.1。
 */
public interface PropertyPage {
    void init(int x, int y, int width, int height);

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    List<? extends GuiEventListener> children();

    void bindTo(DialogEntry entry);

    void unbind();

    void refreshDisplay();

    void setVisible(boolean visible);

    default void setSequence(DialogSequence sequence) {
    }

    int getContentHeight();
}

package top.yourzi.dialog.editor.gui.property;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import top.yourzi.dialog.editor.gui.widget.DropdownWidget;
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

    /**
     * 返回页面中的所有下拉控件，供父容器在 scissor 之外渲染弹出列表。
     */
    default List<DropdownWidget> getDropdowns() {
        return List.of();
    }

    /**
     * 设置字段变更回调，字段变脏时触发（用于主屏 markDirty 序列）。
     * 默认空实现，由使用 Option 值模型的属性页覆写使用。
     */
    default void setDirtyListener(Runnable listener) {
    }

    /**
     * 序列保存成功后调用，各页重置字段 dirty 基线（snapshot）。
     * 默认空实现，由使用 Option 值模型的属性页覆写使用。
     */
    default void onSequenceSaved() {
    }
}

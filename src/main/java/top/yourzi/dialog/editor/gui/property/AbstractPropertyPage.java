package top.yourzi.dialog.editor.gui.property;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import top.yourzi.dialog.model.DialogEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 属性页抽象基类：收编三个 PropertyPage 实现的公共样板（字段 + 静默设置 EditBox）。
 *
 * 子类仍需实现 init/render/children/bindTo/unbind/refreshDisplay/getContentHeight，
 * 但可直接使用基类的 x/y/width/visible/currentEntry 字段和 setBoxSilent 工具方法，
 * 不再各自重复「setResponder(null)→setValue→setResponder」回环规避模板。
 */
public abstract class AbstractPropertyPage implements PropertyPage {

    protected final Font font;
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;
    protected DialogEntry currentEntry = null;
    /** 字段变脏回调，由主屏注入（markDirty 序列）；Option 构造时引用此字段。 */
    protected Runnable dirtyListener;
    /** 程序化回填期间（refreshDisplay/unbind/init 构建）为 true，抑制 notifyDirty 误触发 markDirty。 */
    private boolean refreshing = false;

    protected AbstractPropertyPage(Font font) {
        this.font = font;
    }

    /** 通知数据被用户编辑而变脏。程序化回填（refreshing）或未绑定 entry 时静默跳过，避免打开即标记 dirty。 */
    protected void notifyDirty() {
        if (!this.refreshing && this.currentEntry != null && this.dirtyListener != null) {
            this.dirtyListener.run();
        }
    }

    /** 标记进入程序化回填阶段，setValue 不应视为用户编辑。子类在 refreshDisplay/unbind 首尾配对调用。 */
    protected void beginSilentRefresh() { this.refreshing = true; }
    protected void endSilentRefresh() { this.refreshing = false; }

    /**
     * 设置 EditBox 值且不触发 responder 回调。
     * 收编各属性页重复的「setResponder(null) → setValue → setResponder(原回调)」模板。
     * 注意：调用方需传入原 responder 以便恢复；若 responder 不变可传 null（保持无 responder）。
     *
     * @param box       目标输入框
     * @param value     要设置的值
     * @param responder 恢复用的 responder（可为 null）
     */
    protected void setBoxSilent(EditBox box, String value, java.util.function.Consumer<String> responder) {
        box.setResponder(null);
        box.setValue(value);
        box.setResponder(responder);
    }

    /**
     * 设置 EditBox 值且不触发 responder，用编辑器内联 responder 写回 currentEntry 字段。
     * 子类通常用此变体：responder 把字符串写回 entry 的某字段。
     */
    protected void setBoxSilent(EditBox box, String value, java.util.function.Consumer<String> responder, Runnable extra) {
        box.setResponder(null);
        box.setValue(value);
        box.setResponder(responder);
        if (extra != null) extra.run();
    }

    /** 存储 dirtyListener，供子类构造 Option 时通过 this.dirtyListener 引用。 */
    @Override
    public void setDirtyListener(Runnable listener) {
        this.dirtyListener = listener;
    }

    /** 默认 setVisible：遍历 children() 联动可见性。子类可重写扩展。 */
    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        for (GuiEventListener child : children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                widget.visible = visible;
            }
        }
    }

    /** 默认 getDropdowns：无下拉框。子类按需重写。 */
    @Override
    public List<top.yourzi.dialog.editor.gui.widget.DropdownWidget> getDropdowns() {
        return java.util.List.of();
    }
}

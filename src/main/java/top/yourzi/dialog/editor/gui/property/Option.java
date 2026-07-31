package top.yourzi.dialog.editor.gui.property;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 字段值模型：getter/setter 读写字段，baseline 记录上次保存/绑定时的值，
 * dirty 表示当前值 != baseline。即时回写：set() 立即写回数据源并触发 onDirty。
 *
 * 借鉴 Sparkle-Morpher 的 Option 三态语义（getter/setter/pending/dirty + setPending/apply/undo），
 * 适配 VNDialog 的即时回写架构：去掉 pending（即时模式下 getter.get() 即当前值），
 * set() 内部立即写回 + 重算 dirty + 触发 onDirty 回调；snapshot() 重置基线。
 * 试点用于 BooleanOptionRow，后续可扩展 StringOptionRow 等迁移 EditBox 字段。
 */
public class Option<T> {
    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private final Runnable onDirty;       // 值变脏时触发（用于主屏 markDirty 序列），可为 null
    private T baseline;                    // 上次 snapshot 时的值
    private boolean dirty;

    public Option(Supplier<T> getter, Consumer<T> setter, Runnable onDirty) {
        this.getter = getter;
        this.setter = setter;
        this.onDirty = onDirty;
        this.baseline = getter.get();      // 构造时即以当前值为基线
    }

    /** 读取当前值（即时模式下即数据源当前值）。 */
    public T get() {
        return getter.get();
    }

    /**
     * 即时写回数据源 + 重算 dirty + 触发 onDirty（若由干净变脏）。
     * onDirty 仅在「由干净变脏」时触发一次，避免每次 set 都调 markDirty；
     * markDirty 本身幂等，此处仅为语义清晰。
     */
    public void set(T value) {
        setter.accept(value);
        boolean nowDirty = !Objects.equals(value, baseline);
        if (nowDirty && !this.dirty && onDirty != null) {
            onDirty.run();
        }
        this.dirty = nowDirty;
    }

    /** 重置基线为当前值，清除 dirty。绑定/保存后调用。 */
    public void snapshot() {
        this.baseline = getter.get();
        this.dirty = false;
    }

    public boolean isDirty() {
        return dirty;
    }
}

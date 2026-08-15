package top.yourzi.dialog.editor.util;

import java.util.ArrayDeque;

/**
 * 编辑器撤销/重做栈：存 JSON 快照字符串（借鉴 MainGraph HistoryManager）。
 *
 * 快照式实现：每次结构变化前把当前序列 entries 序列化压栈，undo/redo 时整体还原。
 * 优点是实现极简（无需细粒度命令模式）、绝对正确（不会出现增量补丁错乱）；
 * 代价是每次快照全量序列化——对话节点数十到数百级，Gson 序列化开销可忽略。
 *
 * 栈顶去重：与当前状态相同的快照不重复压栈，避免"无变化也产生撤销点"。
 * 上限 {@link #MAX_HISTORY} 条，超出丢弃最旧（removeLast）。
 */
public class EditorHistory {
    private static final int MAX_HISTORY = 50;

    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack = new ArrayDeque<>();

    /** 压入一个快照（变化前调用）；与栈顶相同则跳过，压入同时清空 redo 栈。 */
    public void push(String snapshot) {
        if (snapshot == null) {
            return;
        }
        if (!this.undoStack.isEmpty() && this.undoStack.peek().equals(snapshot)) {
            return; // 去重：状态未变
        }
        this.undoStack.push(snapshot);
        if (this.undoStack.size() > MAX_HISTORY) {
            this.undoStack.removeLast();
        }
        this.redoStack.clear();
    }

    /**
     * 撤销：传入当前状态快照（压入 redo 栈），返回应还原的历史快照；
     * 无可撤销时返回 null。栈顶若与当前状态相同则跳过（跳过冗余层）。
     */
    public String undo(String currentSnapshot) {
        while (!this.undoStack.isEmpty()) {
            String top = this.undoStack.pop();
            if (currentSnapshot != null && top.equals(currentSnapshot)) {
                continue; // 与当前一致的历史层，跳过
            }
            if (currentSnapshot != null) {
                this.redoStack.push(currentSnapshot);
                if (this.redoStack.size() > MAX_HISTORY) {
                    this.redoStack.removeLast();
                }
            }
            return top;
        }
        return null;
    }

    /**
     * 重做：传入当前状态快照（压回 undo 栈），返回应还原的未来快照；
     * 无可重做时返回 null。
     */
    public String redo(String currentSnapshot) {
        while (!this.redoStack.isEmpty()) {
            String top = this.redoStack.pop();
            if (currentSnapshot != null && top.equals(currentSnapshot)) {
                continue;
            }
            if (currentSnapshot != null) {
                this.undoStack.push(currentSnapshot);
                if (this.undoStack.size() > MAX_HISTORY) {
                    this.undoStack.removeLast();
                }
            }
            return top;
        }
        return null;
    }

    public boolean canUndo() {
        return !this.undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !this.redoStack.isEmpty();
    }

    /** 清空历史（切换/关闭序列时）。 */
    public void clear() {
        this.undoStack.clear();
        this.redoStack.clear();
    }
}

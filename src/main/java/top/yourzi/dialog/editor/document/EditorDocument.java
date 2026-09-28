package top.yourzi.dialog.editor.document;

import top.yourzi.dialog.editor.util.EditorHistory;
import top.yourzi.dialog.model.DialogSequence;

import java.util.Objects;

/**
 * Per-tab editing state. Keeping dirty state and history with the model avoids
 * ID-keyed bookkeeping and lets each open sequence keep an independent undo stack.
 */
public final class EditorDocument {
    private final DialogSequence sequence;
    private final EditorHistory history = new EditorHistory();
    private boolean dirty;

    public EditorDocument(DialogSequence sequence) {
        this.sequence = Objects.requireNonNull(sequence, "sequence");
    }

    public DialogSequence sequence() {
        return this.sequence;
    }

    public boolean dirty() {
        return this.dirty;
    }

    public boolean markDirty() {
        if (this.dirty) {
            return false;
        }
        this.dirty = true;
        return true;
    }

    public boolean markClean() {
        if (!this.dirty) {
            return false;
        }
        this.dirty = false;
        return true;
    }

    public void pushHistory(String snapshot) {
        this.history.push(snapshot);
    }

    public String undo(String currentSnapshot) {
        return this.history.undo(currentSnapshot);
    }

    public String redo(String currentSnapshot) {
        return this.history.redo(currentSnapshot);
    }

    public boolean canUndo() {
        return this.history.canUndo();
    }

    public boolean canRedo() {
        return this.history.canRedo();
    }
}

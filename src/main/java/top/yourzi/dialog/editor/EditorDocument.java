package top.yourzi.dialog.editor;

import top.yourzi.dialog.DialogManager;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * One open dialogue file: the live model, its undo stack and its saved-state marker.
 *
 * <p>History is snapshot based. Every mutation announces itself through {@link #touch}, which
 * serializes the document once and compares against the last snapshot: an edit that changed nothing
 * is dropped, and an edit that continues the previous one inside a short window is coalesced so that
 * typing a sentence is a single undo step rather than one per keystroke.
 *
 * <p>Dirty state is derived from the snapshots, so undoing back to the state on disk clears the
 * unsaved marker instead of leaving a stale asterisk behind.
 */
public final class EditorDocument {
    private static final int MAX_HISTORY = 64;
    private static final long MERGE_WINDOW_MS = 1200L;

    private DialogSequence sequence;
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private String currentSnapshot;
    private String savedSnapshot;
    private long lastTouchAt;
    private boolean dirty;

    public EditorDocument(DialogSequence sequence) {
        this.sequence = sequence;
        this.currentSnapshot = this.snapshot();
        this.savedSnapshot = this.currentSnapshot;
    }

    public DialogSequence sequence() {
        return this.sequence;
    }


    public String id() {
        return this.sequence.getId();
    }

    public boolean dirty() {
        return this.dirty;
    }

    private String snapshot() {
        return DialogManager.GSON.toJson(this.sequence);
    }

    /**
     * Records that the document changed through an already-applied mutation.
     *
     * @param merge true when the edit continues a run of typing in the same field
     * @return true when the model actually changed
     */
    public boolean touch(boolean merge) {
        String snapshot = this.snapshot();
        if (snapshot.equals(this.currentSnapshot)) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean coalesce = merge && now - this.lastTouchAt < MERGE_WINDOW_MS && !this.undo.isEmpty();
        this.lastTouchAt = now;
        if (!coalesce) {
            this.undo.push(this.currentSnapshot);
            while (this.undo.size() > MAX_HISTORY) {
                this.undo.removeLast();
            }
            this.redo.clear();
        }
        this.currentSnapshot = snapshot;
        this.markDirty();
        return true;
    }

    private void markDirty() {
        this.dirty = !this.currentSnapshot.equals(this.savedSnapshot);
    }

    public void markSaved() {
        this.savedSnapshot = this.currentSnapshot;
        this.dirty = false;
    }

    public boolean canUndo() {
        return !this.undo.isEmpty();
    }

    public boolean canRedo() {
        return !this.redo.isEmpty();
    }

    /**
     * Restores the previous snapshot.
     *
     * @return the sequence that must now be installed in the UI, or null when there is nothing to undo
     */
    public DialogSequence undo() {
        if (this.undo.isEmpty()) {
            return null;
        }
        String previous = this.undo.pop();
        this.redo.push(this.currentSnapshot);
        this.currentSnapshot = previous;
        this.sequence = DialogManager.GSON.fromJson(previous, DialogSequence.class);
        this.lastTouchAt = 0L;
        this.markDirty();
        return this.sequence;
    }

    public DialogSequence redo() {
        if (this.redo.isEmpty()) {
            return null;
        }
        String next = this.redo.pop();
        this.undo.push(this.currentSnapshot);
        this.currentSnapshot = next;
        this.sequence = DialogManager.GSON.fromJson(next, DialogSequence.class);
        this.lastTouchAt = 0L;
        this.markDirty();
        return this.sequence;
    }
}

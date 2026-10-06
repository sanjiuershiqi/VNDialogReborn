package top.yourzi.dialog.editor.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.EditorDocument;
import top.yourzi.dialog.model.DialogSequence;

/**
 * What a panel may see and do.
 *
 * <p>Panels never open files, write to disk or reach into other panels. They read the active
 * sequence, mutate it in place and report the change through {@link #touch} or
 * {@link #touchStructure}; the screen then records history and refreshes the other views. Keeping
 * this boundary is what stops a UI change from leaking into the file layer.
 */
public interface EditorContext {
    /** Active document, or null when nothing is open. */
    EditorDocument document();

    default DialogSequence sequence() {
        EditorDocument document = this.document();
        return document == null ? null : document.sequence();
    }

    /** Selected node id, shared by every panel. */
    String selectedId();

    void select(String nodeId);

    /**
     * Records an edit that only changed field values.
     *
     * @param merge true for a run of keystrokes that should undo as one step
     */
    void touch(boolean merge);

    /** Records an edit that added, removed, reordered or relinked nodes; every view is rebuilt. */
    void touchStructure();

    void status(Component message, StatusKind kind);

    /** Status message severity: drives the colour and whether the message clears itself. */
    enum StatusKind {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }

    default boolean shiftDown() {
        return Screen.hasShiftDown();
    }
}

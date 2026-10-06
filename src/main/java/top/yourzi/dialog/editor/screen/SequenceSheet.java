package top.yourzi.dialog.editor.screen;

import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.EditorStore;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.Column;
import top.yourzi.dialog.editor.ui.Modal;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.TextBox;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.Toggle;
import top.yourzi.dialog.editor.ui.UiHost;
import top.yourzi.dialog.model.DialogSequence;

import java.util.function.Consumer;

/**
 * Sequence-level settings: id (which is also the file name), title, description, effect, start node
 * and whether the player may close the dialogue. Values apply together on confirm, so cancelling
 * leaves the document untouched.
 */
final class SequenceSheet extends Modal {
    /** Result handed back to the screen, which owns renaming and history. */
    record Result(String id, String title, String description, String effect, String startId, boolean allowClose) {
    }

    private final TextBox id;
    private final TextBox title;
    private final TextBox description;
    private final TextBox effect;
    private final Button start;
    private final Toggle allowClose;
    private final Consumer<Consumer<String>> nodePicker;
    private final Consumer<Result> onApply;
    private final Runnable onDelete;
    private String startId;

    private SequenceSheet(DialogSequence sequence, Consumer<Consumer<String>> nodePicker, Consumer<Result> onApply,
                          Runnable onDelete) {
        super(Theme.tr("props.title"));
        this.setCardWidth(360);
        this.nodePicker = nodePicker;
        this.onApply = onApply;
        this.onDelete = onDelete;
        this.startId = sequence.getStartId();
        this.id = new TextBox(sequence.getId());
        this.title = new TextBox(nullToEmpty(sequence.getTitle()));
        this.description = new TextBox(nullToEmpty(sequence.getDescription()));
        this.effect = new TextBox(nullToEmpty(sequence.getEffect()));
        this.start = Button.of(Component.literal(this.startLabel()), this::pickStart);
        this.allowClose = new Toggle(Theme.tr("props.allow_close"), sequence.isCloseAllowed(), value -> {
        });
    }

    static void open(UiHost host, DialogSequence sequence, Consumer<Consumer<String>> nodePicker,
                     Consumer<Result> onApply, Runnable onDelete) {
        SequenceSheet sheet = new SequenceSheet(sequence, nodePicker, onApply, onDelete);
        sheet.build();
        host.open(sheet, true, false);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String startLabel() {
        return this.startId == null || this.startId.isBlank() ? Theme.tr("props.start_default").getString() : this.startId;
    }

    private void pickStart() {
        this.nodePicker.accept(selected -> {
            this.startId = selected == null || selected.isBlank() ? null : selected;
            this.start.setLabel(Component.literal(this.startLabel()));
        });
    }

    @Override
    protected void buildBody(Column body) {
        this.id.placeholder(Theme.tr("props.id_hint"));
        this.effect.placeholder(Theme.tr("props.effect_hint"));
        body.add(Nodes.caption(Theme.tr("props.id")));
        body.add(this.id);
        body.add(Nodes.caption(Theme.tr("props.title_field")));
        body.add(this.title);
        body.add(Nodes.caption(Theme.tr("props.description")));
        body.add(this.description);
        body.add(Nodes.caption(Theme.tr("props.effect")));
        body.add(this.effect);
        body.add(Nodes.caption(Theme.tr("props.start")));
        body.add(this.start);
        body.add(this.allowClose);
        Row danger = new Row().gap(2);
        danger.add(Nodes.fill());
        danger.add(Button.of(Theme.tr("props.delete_file"), () -> {
            this.dismissLayer();
            this.onDelete.run();
        }).tone(Button.Tone.DANGER).fit());
        body.add(danger);
    }

    @Override
    public void build() {
        this.footerButton(Theme.tr("apply"), Button.Tone.PRIMARY, this::apply);
        super.build();
    }

    private void apply() {
        String newId = this.id.value().trim();
        if (!EditorStore.isSafeId(newId)) {
            this.id.setValue(newId);
            return;
        }
        this.dismissLayer();
        this.onApply.accept(new Result(newId, blankToNull(this.title.value()), blankToNull(this.description.value()),
                blankToNull(this.effect.value()), this.startId, this.allowClose.value()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

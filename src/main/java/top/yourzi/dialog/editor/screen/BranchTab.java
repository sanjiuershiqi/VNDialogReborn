package top.yourzi.dialog.editor.screen;

import com.google.gson.JsonPrimitive;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.TextCodec;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.Column;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Paragraph;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.Select;
import top.yourzi.dialog.editor.ui.TextBox;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.Toggle;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Branch tab: how the script leaves this node.
 *
 * <p>A node hands control on in one of three ways that used to be easy to confuse: falling through
 * to the next node in file order, jumping to an explicit {@code next}, or offering choices. They are
 * picked explicitly here, and the tab always shows where the script will really go.
 */
final class BranchTab extends Column {
    /** The three ways a node can hand control on. */
    enum ExitKind {
        IMPLICIT,
        EXPLICIT,
        BRANCHES
    }

    private final EditorContext context;
    private final Select<ExitKind> exitKind;
    private final Nodes.Label resolved = new Nodes.Label(Component.empty(), Theme.TEXT_DIM);
    private final Button nextTarget = new Button(Component.literal("—"), button -> {
    });
    private final Button addBranch = new Button(Theme.tr("branch.add"), button -> {
    });
    private final Column branchRows = new Column().gap(2);
    private final Paragraph branchHint = Paragraph.of(Theme.tr("branch.hint")).color(Theme.TEXT_MUTED);
    private final Toggle endDialog;
    private final Toggle allowSkip;
    private Consumer<Consumer<String>> targetPicker;
    private DialogEntry entry;
    private boolean binding;

    BranchTab(EditorContext context) {
        this.context = context;
        this.gap(3).padding(2);
        this.exitKind = new Select<>(List.of(ExitKind.values()), ExitKind.IMPLICIT,
                kind -> Theme.tr("branch.kind." + kind.name().toLowerCase(java.util.Locale.ROOT)),
                this::onExitKindChosen);
        this.endDialog = new Toggle(Theme.tr("branch.end_dialog"), false, value -> {
            if (this.entry != null && !this.binding) {
                this.entry.setEndDialog(value ? Boolean.TRUE : null);
                this.context.touchStructure();
            }
        });
        this.allowSkip = new Toggle(Theme.tr("branch.allow_skip"), true, value -> {
            if (this.entry != null && !this.binding) {
                this.entry.setAllowSkip(value ? null : Boolean.FALSE);
                this.context.touch(false);
            }
        });

        this.add(Nodes.section(Theme.tr("section.exit")));
        this.add(this.exitKind);
        this.add(this.resolved);
        this.add(this.nextTarget);
        this.add(this.addBranch.fit());
        this.add(this.branchRows);
        this.add(this.branchHint);
        this.add(Nodes.section(Theme.tr("section.behaviour")));
        this.add(this.endDialog);
        this.add(this.allowSkip);

        this.nextTarget.setAction(this::pickNextTarget);
        this.nextTarget.withTooltip(Theme.tr("branch.next_tip"));
        this.addBranch.setAction(this::appendBranch);
    }

    /** Target pickers are owned by the screen, which renders the node list overlay. */
    void setTargetPicker(Consumer<Consumer<String>> targetPicker) {
        this.targetPicker = targetPicker;
    }

    void bind(DialogEntry entry, DialogSequence sequence) {
        this.binding = true;
        try {
            this.entry = entry;
            this.branchRows.clear();
            if (entry == null) {
                return;
            }
            this.endDialog.setValue(entry.isEndDialog());
            this.allowSkip.setValue(entry.isSkipAllowed());
            boolean explicit = entry.getNextId() != null && !entry.getNextId().isBlank();
            boolean branches = NodeGraph.hasOptions(entry);
            ExitKind kind = branches ? ExitKind.BRANCHES : explicit ? ExitKind.EXPLICIT : ExitKind.IMPLICIT;
            this.exitKind.setSelected(kind);
            this.nextTarget.setLabel(Component.literal(explicit ? entry.getNextId() : "—"));
            if (entry.getOptions() != null) {
                for (int i = 0; i < entry.getOptions().length; i++) {
                    DialogOption option = entry.getOptions()[i];
                    if (option != null) {
                        this.branchRows.add(new BranchRow(option, i, entry.getOptions().length));
                    }
                }
            }
            this.updateResolved(sequence);
            this.applyKind(kind);
        } finally {
            this.binding = false;
        }
    }

    private void updateResolved(DialogSequence sequence) {
        if (this.entry == null) {
            return;
        }
        if (this.entry.isEndDialog()) {
            this.resolved.setText(Theme.tr("branch.resolved_end"));
            this.resolved.setColor(Theme.DANGER);
            return;
        }
        if (NodeGraph.hasOptions(this.entry)) {
            this.resolved.setText(Theme.tr("branch.resolved_choice", this.entry.getOptions().length));
            this.resolved.setColor(Theme.TEXT_DIM);
            return;
        }
        DialogEntry next = NodeGraph.implicitNext(sequence, this.entry);
        boolean dangling = this.entry.getNextId() != null && !this.entry.getNextId().isBlank() && next == null;
        if (dangling) {
            this.resolved.setText(Theme.tr("branch.resolved_missing", this.entry.getNextId()));
            this.resolved.setColor(Theme.DANGER);
        } else if (next == null) {
            this.resolved.setText(Theme.tr("branch.resolved_end"));
            this.resolved.setColor(Theme.TEXT_MUTED);
        } else {
            this.resolved.setText(Theme.tr("branch.resolved_next", next.getId()));
            this.resolved.setColor(Theme.CYAN);
        }
    }

    private void applyKind(ExitKind kind) {
        this.nextTarget.setVisible(kind == ExitKind.EXPLICIT);
        this.addBranch.setVisible(kind == ExitKind.BRANCHES);
        this.branchRows.setVisible(kind == ExitKind.BRANCHES);
        this.branchHint.setVisible(kind == ExitKind.BRANCHES);
    }

    private void onExitKindChosen(ExitKind kind) {
        if (this.entry == null || this.binding) {
            return;
        }
        switch (kind) {
            case IMPLICIT -> {
                this.entry.setNextId(null);
                this.entry.setOptions(null);
            }
            case EXPLICIT -> {
                this.entry.setOptions(null);
                if (this.entry.getNextId() == null || this.entry.getNextId().isBlank()) {
                    DialogEntry next = NodeGraph.implicitNext(this.context.sequence(), this.entry);
                    this.entry.setNextId(next == null ? null : next.getId());
                }
            }
            case BRANCHES -> {
                this.entry.setNextId(null);
                if (!NodeGraph.hasOptions(this.entry)) {
                    this.appendBranchToModel();
                }
            }
        }
        this.context.touchStructure();
    }

    private void pickNextTarget() {
        if (this.entry == null || this.targetPicker == null) {
            return;
        }
        DialogEntry target = this.entry;
        this.targetPicker.accept(selected -> {
            target.setNextId(selected == null || selected.isBlank() ? null : selected);
            this.context.touchStructure();
        });
    }

    private void appendBranch() {
        if (this.entry == null) {
            return;
        }
        this.appendBranchToModel();
        this.context.touchStructure();
    }

    private void appendBranchToModel() {
        List<DialogOption> options = this.options();
        options.add(DialogOption.builder()
                .text(new JsonPrimitive(Theme.tr("branch.new").getString()))
                .build());
        this.entry.setOptions(options.toArray(new DialogOption[0]));
    }

    private List<DialogOption> options() {
        List<DialogOption> options = new ArrayList<>();
        if (this.entry.getOptions() != null) {
            options.addAll(List.of(this.entry.getOptions()));
        }
        return options;
    }

    private void moveBranch(int index, int delta) {
        List<DialogOption> options = this.options();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= options.size()) {
            return;
        }
        options.add(target, options.remove(index));
        this.entry.setOptions(options.toArray(new DialogOption[0]));
        this.context.touchStructure();
    }

    private void removeBranch(int index) {
        List<DialogOption> options = this.options();
        if (index < 0 || index >= options.size()) {
            return;
        }
        options.remove(index);
        this.entry.setOptions(options.isEmpty() ? null : options.toArray(new DialogOption[0]));
        this.context.touchStructure();
    }

    private void pickBranchTarget(DialogOption option) {
        if (this.targetPicker == null) {
            return;
        }
        this.targetPicker.accept(selected -> {
            option.setTargetId(selected == null || selected.isBlank() ? null : selected);
            this.context.touchStructure();
        });
    }

    /** One choice: label, target, reorder and delete. */
    private final class BranchRow extends Row {
        BranchRow(DialogOption option, int index, int count) {
            this.gap(2);
            TextBox label = new TextBox(TextCodec.toEditable(option.getText()));
            label.placeholder(Theme.tr("branch.label_hint"));
            label.flex(1);
            label.onChange(value -> {
                if (!BranchTab.this.binding) {
                    option.setText(TextCodec.fromEditable(value));
                    BranchTab.this.context.touch(true);
                }
            });
            String targetId = option.getTargetId();
            boolean missing = targetId != null && !targetId.isBlank()
                    && NodeGraph.byId(BranchTab.this.context.sequence(), targetId) == null;
            String targetLabel = targetId == null || targetId.isBlank()
                    ? Theme.tr("branch.target_end").getString() : (missing ? "! " : "→ ") + targetId;
            Button target = Button.of(Component.literal(targetLabel), () -> BranchTab.this.pickBranchTarget(option));
            target.prefWidth(78);
            target.withTooltip(Theme.tr("branch.target_tip"));
            Button up = Button.of(Component.literal("▲"), () -> BranchTab.this.moveBranch(index, -1)).active(index > 0);
            Button down = Button.of(Component.literal("▼"), () -> BranchTab.this.moveBranch(index, 1))
                    .active(index < count - 1);
            Button remove = Button.of(Component.literal("✕"), () -> BranchTab.this.removeBranch(index))
                    .tone(Button.Tone.GHOST);
            boolean extras = option.getCommand() != null && !option.getCommand().isEmpty()
                    || option.getVisibilityCommand() != null && !option.getVisibilityCommand().isBlank();
            Button more = Button.of(Component.literal(extras ? "⚙" : "…"), () -> OptionSheet.open(BranchTab.this.host(),
                    option, BranchTab.this.context::touchStructure));
            more.prefWidth(15);
            more.withTooltip(Theme.tr(extras ? "branch.more_set_tip" : "branch.more_tip"));
            if (extras) {
                more.tone(Button.Tone.PRIMARY);
            }
            up.prefWidth(15);
            down.prefWidth(15);
            remove.prefWidth(15);
            this.add(label);
            this.add(target);
            this.add(more);
            this.add(up);
            this.add(down);
            this.add(remove);
        }
    }
}

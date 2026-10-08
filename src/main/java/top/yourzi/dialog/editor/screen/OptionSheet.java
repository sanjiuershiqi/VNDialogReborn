package top.yourzi.dialog.editor.screen;

import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.Column;
import top.yourzi.dialog.editor.ui.Modal;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Paragraph;
import top.yourzi.dialog.editor.ui.TextArea;
import top.yourzi.dialog.editor.ui.TextBox;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiHost;
import top.yourzi.dialog.model.DialogOption;

import java.util.ArrayList;
import java.util.List;

/**
 * What a choice does besides jumping: the commands it runs when picked and the condition under
 * which it is offered at all. Applied together on confirm.
 */
final class OptionSheet extends Modal {
    private final DialogOption option;
    private final Runnable onApplied;
    private final TextArea commands;
    private final TextBox visibility;

    private OptionSheet(DialogOption option, Runnable onApplied) {
        super(Theme.tr("option_edit.title"));
        this.setCardWidth(360);
        this.option = option;
        this.onApplied = onApplied;
        this.commands = new TextArea(option.getCommand() == null ? "" : String.join("\n", option.getCommand()));
        this.visibility = new TextBox(option.getVisibilityCommand() == null ? "" : option.getVisibilityCommand());
    }

    static void open(UiHost host, DialogOption option, Runnable onApplied) {
        OptionSheet sheet = new OptionSheet(option, onApplied);
        sheet.build();
        host.open(sheet, true, false);
        host.focus(sheet.commands);
    }

    @Override
    protected void buildBody(Column body) {
        this.commands.placeholder(Theme.tr("option_edit.commands_hint"));
        this.visibility.placeholder(Theme.tr("logic.visibility_hint"));
        body.add(Nodes.caption(Theme.tr("option_edit.commands")));
        body.add(this.commands.prefHeight(74));
        body.add(Nodes.caption(Theme.tr("option_edit.visibility")));
        body.add(this.visibility);
        body.add(Paragraph.of(Theme.tr("option_edit.help")).color(Theme.TEXT_MUTED));
    }

    @Override
    public void build() {
        this.footerButton(Theme.tr("apply"), Button.Tone.PRIMARY, this::apply);
        super.build();
    }

    private void apply() {
        List<String> lines = new ArrayList<>();
        for (String line : this.commands.value().split("\n")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        this.option.setCommand(lines.isEmpty() ? null : lines);
        String condition = this.visibility.value().trim();
        this.option.setVisibilityCommand(condition.isEmpty() ? null : condition);
        this.dismissLayer();
        this.onApplied.run();
    }
}

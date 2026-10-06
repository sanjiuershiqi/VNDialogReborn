package top.yourzi.dialog.editor.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.DialogValidator;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.ItemList;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Validation results as a filterable list; selecting an issue jumps to the node it concerns. */
public final class ValidationPanel extends EditorPanel {
    private enum Filter {
        ALL,
        ERRORS,
        WARNINGS
    }

    private final ItemList<DialogValidator.Issue> list = new ItemList<>();
    private final List<DialogValidator.Issue> issues = new ArrayList<>();
    private final Button all = Button.of(Theme.tr("validation.all"), () -> this.setFilter(Filter.ALL));
    private final Button errors = Button.of(Theme.tr("validation.errors"), () -> this.setFilter(Filter.ERRORS));
    private final Button warnings = Button.of(Theme.tr("validation.warnings"), () -> this.setFilter(Filter.WARNINGS));
    private Filter filter = Filter.ALL;

    public ValidationPanel() {
        super("02", Theme.tr("panel.validation"));
        Row toolbar = this.createToolbar(Theme.ROW);
        toolbar.add(this.all.fit());
        toolbar.add(this.errors.fit());
        toolbar.add(this.warnings.fit());
        toolbar.add(Nodes.fill());
        this.add(this.list);
        this.list.labeler(issue -> Component.empty());
        this.list.decorator((graphics, issue, rowY, x, width, height, selected) -> this.drawIssue(graphics, issue,
                rowY, x, width, height, selected));
        this.list.emptyText(Theme.tr("validation.clean"));
        this.list.activateOnClick(true);
        this.setFilter(Filter.ALL);
    }

    public void setOnIssueSelected(Consumer<DialogValidator.Issue> onSelected) {
        this.list.onActivate(onSelected);
    }

    public void setIssues(List<DialogValidator.Issue> issues) {
        this.issues.clear();
        this.issues.addAll(issues);
        this.apply();
    }

    private void setFilter(Filter filter) {
        this.filter = filter;
        this.all.selected(filter == Filter.ALL);
        this.errors.selected(filter == Filter.ERRORS);
        this.warnings.selected(filter == Filter.WARNINGS);
        this.apply();
    }

    private void apply() {
        List<DialogValidator.Issue> visible = new ArrayList<>();
        for (DialogValidator.Issue issue : this.issues) {
            boolean keep = switch (this.filter) {
                case ALL -> true;
                case ERRORS -> issue.severity() == DialogValidator.Severity.ERROR;
                case WARNINGS -> issue.severity() == DialogValidator.Severity.WARNING;
            };
            if (keep) {
                visible.add(issue);
            }
        }
        this.list.setItems(visible);
        this.errors.setLabel(Theme.tr("validation.errors_count",
                DialogValidator.count(this.issues, DialogValidator.Severity.ERROR)));
        this.warnings.setLabel(Theme.tr("validation.warnings_count",
                DialogValidator.count(this.issues, DialogValidator.Severity.WARNING)));
        this.errors.fit();
        this.warnings.fit();
    }

    @Override
    protected void onLayout() {
        super.onLayout();
        this.list.setBounds(this.content().x() + 3, this.content().y(), this.content().width() - 6,
                this.content().height() - 2);
    }

    private void drawIssue(GuiGraphics graphics, DialogValidator.Issue issue, int rowY, int x, int width, int height,
                           boolean selected) {
        boolean error = issue.severity() == DialogValidator.Severity.ERROR;
        int color = error ? Theme.DANGER : Theme.WARNING;
        graphics.fill(x, rowY + 1, x + 2, rowY + height - 1, color);
        int textY = rowY + (height - 8) / 2;
        String node = issue.nodeId() == null ? "—" : issue.nodeId();
        String head = node + "  ";
        Theme.text(graphics, head, x + 6, textY, Theme.TEXT);
        int messageX = x + 6 + Theme.font().width(head);
        String message = Theme.tr("issue." + issue.code().toLowerCase(java.util.Locale.ROOT)).getString();
        Theme.textIn(graphics, message, messageX, rowY, width - (messageX - x) - 4, height, color);
    }
}

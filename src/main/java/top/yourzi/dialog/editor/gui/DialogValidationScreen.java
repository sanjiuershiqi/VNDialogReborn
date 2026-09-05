package top.yourzi.dialog.editor.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.gui.widget.EditorButton;
import top.yourzi.dialog.editor.util.EditorTheme;
import top.yourzi.dialog.editor.validation.DialogValidator;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 操作型验证面板：把验证结果变成可筛选、可定位的列表，而不是只在状态栏显示数量。
 */
public final class DialogValidationScreen extends Screen {
    private enum Filter { ALL, ERRORS, WARNINGS }

    private final DialogSequence sequence;
    private final List<DialogValidator.Issue> issues;
    private final Consumer<DialogValidator.Issue> onIssueSelected;
    private final Screen parent;
    private Filter filter = Filter.ALL;
    private int scrollOffset;
    private final List<EditorButton> issueButtons = new ArrayList<>();
    private EditorButton allButton;
    private EditorButton errorsButton;
    private EditorButton warningsButton;

    public DialogValidationScreen(DialogSequence sequence, List<DialogValidator.Issue> issues,
                                  Consumer<DialogValidator.Issue> onIssueSelected, Screen parent) {
        super(Component.translatable("gui.vn_edit.validation.title"));
        this.sequence = sequence;
        this.issues = issues == null ? List.of() : List.copyOf(issues);
        this.onIssueSelected = onIssueSelected;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        int panelW = Math.min(560, this.width - 24);
        int panelX = (this.width - panelW) / 2;
        int top = Math.max(12, (this.height - 260) / 2);
        int buttonY = top + 32;
        this.allButton = EditorButton.builder(Component.translatable("gui.vn_edit.validation.all"), b -> setFilter(Filter.ALL))
                .bounds(panelX + 10, buttonY, 86, 18).tone(EditorButton.Tone.LIGHT).build();
        this.errorsButton = EditorButton.builder(Component.translatable("gui.vn_edit.validation.errors"), b -> setFilter(Filter.ERRORS))
                .bounds(panelX + 100, buttonY, 100, 18).tone(EditorButton.Tone.NORMAL).build();
        this.warningsButton = EditorButton.builder(Component.translatable("gui.vn_edit.validation.warnings"), b -> setFilter(Filter.WARNINGS))
                .bounds(panelX + 204, buttonY, 110, 18).tone(EditorButton.Tone.NORMAL).build();
        this.addRenderableWidget(this.allButton);
        this.addRenderableWidget(this.errorsButton);
        this.addRenderableWidget(this.warningsButton);
        rebuildIssueButtons(panelX, buttonY + 24, panelW);
    }

    private void setFilter(Filter filter) {
        this.filter = filter;
        this.scrollOffset = 0;
        int panelW = Math.min(560, this.width - 24);
        int panelX = (this.width - panelW) / 2;
        rebuildIssueButtons(panelX, Math.max(12, (this.height - 260) / 2) + 56, panelW);
    }

    private List<DialogValidator.Issue> filteredIssues() {
        return this.issues.stream().filter(issue -> switch (this.filter) {
            case ALL -> true;
            case ERRORS -> issue.severity() == DialogValidator.Severity.ERROR;
            case WARNINGS -> issue.severity() == DialogValidator.Severity.WARNING;
        }).toList();
    }

    private void rebuildIssueButtons(int panelX, int firstY, int panelW) {
        for (EditorButton button : this.issueButtons) {
            this.removeWidget(button);
        }
        this.issueButtons.clear();
        int y = firstY - this.scrollOffset;
        int maxRows = Math.max(1, (this.height - firstY - 34) / 22);
        int index = 0;
        for (DialogValidator.Issue issue : filteredIssues()) {
            if (y >= firstY - 22 && y < firstY + maxRows * 22) {
                String node = issue.nodeId() == null || issue.nodeId().isBlank() ? "—" : issue.nodeId();
                String label = (issue.severity() == DialogValidator.Severity.ERROR ? "✕ " : "⚠ ")
                        + issue.code() + " · " + node;
                EditorButton row = EditorButton.builder(Component.literal(label), b -> selectIssue(issue))
                        .bounds(panelX + 10, y, panelW - 20, 18).build();
                this.addRenderableWidget(row);
                this.issueButtons.add(row);
            }
            y += 22;
            index++;
        }
    }

    private void selectIssue(DialogValidator.Issue issue) {
        if (this.onIssueSelected != null) {
            this.onIssueSelected.accept(issue);
        } else if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private int panelTop() {
        return Math.max(12, (this.height - 260) / 2);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, EditorTheme.BG_DEEPEST);
        int panelW = Math.min(560, this.width - 24);
        int panelH = Math.min(this.height - 24, 300);
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;
        EditorRenderHelper.fillWithShadow(graphics, panelX, panelY, panelW, panelH,
                EditorTheme.BG_SURFACE, EditorTheme.SHADOW_DROP);
        EditorRenderHelper.drawBorder(graphics, panelX, panelY, panelW, panelH, EditorTheme.BORDER_LIGHT);
        EditorTheme.drawPanelHeader(graphics, this.font, panelX, panelY, panelW, "V", this.title);
        long errors = this.issues.stream().filter(i -> i.severity() == DialogValidator.Severity.ERROR).count();
        long warnings = this.issues.stream().filter(i -> i.severity() == DialogValidator.Severity.WARNING).count();
        String sequenceId = this.sequence == null || this.sequence.getId() == null ? "—" : this.sequence.getId();
        graphics.drawString(this.font, Component.translatable("gui.vn_edit.validation.summary", sequenceId, errors, warnings),
                panelX + 10, panelY + 32, EditorTheme.TEXT_SECONDARY);
        if (this.filteredIssues().isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.vn_edit.validation.clean"),
                    this.width / 2, panelY + 105, EditorTheme.STATUS_SUCCESS);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, Component.translatable("gui.vn_edit.validation.footer"),
                this.width / 2, panelY + panelH - 14, EditorTheme.TEXT_MUTED);
    }

    /** 禁用 Minecraft 菜单默认的背景模糊，避免后处理把验证面板文字一起变糊。 */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, EditorTheme.BG_DEEPEST);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, filteredIssues().size() * 22 - (this.height - panelTop() - 70));
        if (max <= 0) return true;
        this.scrollOffset = Math.max(0, Math.min(max, this.scrollOffset - (int) scrollY * 22));
        int panelW = Math.min(560, this.width - 24);
        rebuildIssueButtons((this.width - panelW) / 2, panelTop() + 56, panelW);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (this.minecraft != null) this.minecraft.setScreen(this.parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }
}

package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Multi-line editor field for dialogue text and command bodies.
 *
 * <p>{@code §} formatting codes stay in the raw value but are painted as real styles, so the writer
 * sees the result rather than the codes. Long lines wrap at the field's width; caret, selection,
 * clicking and scrolling all work on the wrapped rows, while the model keeps counting columns inside
 * the logical line.
 *
 * <p>Wrapping is cached per frame: it is redone only when the text or the width actually changed,
 * which is what keeps typing cheap on a long paragraph.
 */
public class TextArea extends UiNode {
    private static final int LINE_H = 10;

    /** One drawn row: where it came from and the style carried into it. */
    private record Visual(EditableText.RowCtx context, EditableText.Row row, Style style) {
    }

    private final EditableText text;
    private Component placeholder;
    private Consumer<String> onChange;
    private boolean dragSelecting;
    private String lastValue;
    private String lastWrappedValue;
    private int lastWrapWidth = -1;
    private List<Visual> visuals = List.of();

    public TextArea(String value) {
        this.text = new EditableText(true, value);
        this.lastValue = this.text.value();
    }

    public TextArea placeholder(Component placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public TextArea onChange(Consumer<String> onChange) {
        this.onChange = onChange;
        return this;
    }

    public EditableText model() {
        return this.text;
    }

    public String value() {
        return this.text.value();
    }

    public void setValue(String value) {
        String safe = value == null ? "" : value;
        if (!safe.equals(this.text.value())) {
            this.text.setValueSilently(safe);
        }
        this.lastValue = this.text.value();
    }

    public void insertAtCursor(String snippet) {
        this.text.insert(snippet, 0);
        this.changed();
    }

    private void changed() {
        String value = this.text.value();
        if (!value.equals(this.lastValue)) {
            this.lastValue = value;
            if (this.onChange != null) {
                this.onChange.accept(value);
            }
        }
    }

    @Override
    public void setFocused(boolean focused) {
        boolean was = this.focused();
        super.setFocused(focused);
        if (was && !focused) {
            this.dragSelecting = false;
        }
    }

    private int wrapWidth() {
        return Math.max(20, this.width() - Theme.PAD * 2);
    }

    /**
     * Recomputes the drawn rows when the text or the width changed. The check is on the text itself,
     * never on the line list's identity: the model mutates that list in place, so identity says
     * nothing about whether the content is still the same.
     */
    private void rewrap() {
        int width = this.wrapWidth();
        String value = this.text.value();
        if (width == this.lastWrapWidth && value.equals(this.lastWrappedValue)) {
            return;
        }
        this.lastWrapWidth = width;
        this.lastWrappedValue = value;
        List<Visual> built = new ArrayList<>();
        List<String> lines = this.text.lines();
        for (int index = 0; index < lines.size(); index++) {
            EditableText.RowCtx context = EditableText.wrapLine(index, lines.get(index), width);
            List<EditableText.Row> rows = context.rows();
            for (int local = 0; local < rows.size(); local++) {
                built.add(new Visual(context, rows.get(local), context.styleAt(local)));
            }
        }
        if (built.isEmpty()) {
            EditableText.RowCtx empty = EditableText.wrapLine(0, "", width);
            built.add(new Visual(empty, empty.rows().get(0), Style.EMPTY));
        }
        this.visuals = built;
    }

    private int rowCount() {
        return this.visuals.size();
    }

    private Visual visual(int global) {
        return this.visuals.get(Mth.clamp(global, 0, this.visuals.size() - 1));
    }

    /** Global row the caret sits in. */
    private int caretRow() {
        int line = this.text.cursorLine();
        int column = this.text.cursorColumn();
        int found = -1;
        for (int index = 0; index < this.visuals.size(); index++) {
            Visual visual = this.visuals.get(index);
            if (visual.context().line() != line) {
                continue;
            }
            found = index;
            if (column >= visual.row().from() && column <= visual.row().to()) {
                return index;
            }
        }
        return Math.max(0, found);
    }

    private int textLeft() {
        return this.x() + Theme.PAD;
    }

    private int rowTop(int global) {
        return this.y() + 2 + (global - this.text.scrollLine()) * LINE_H;
    }

    private int visibleRows() {
        return Math.max(1, (this.height() - 4) / LINE_H);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.host().focus(this);
        this.host().claimPointer(this);
        this.dragSelecting = true;
        this.placeCaretAt(mouseX, mouseY, net.minecraft.client.gui.screens.Screen.hasShiftDown());
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragSelecting) {
            this.placeCaretAt(mouseX, mouseY, true);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragSelecting = false;
        return true;
    }

    /** Turns a click into a logical line and column using the wrapped row under the cursor. */
    private void placeCaretAt(double mouseX, double mouseY, boolean extend) {
        this.rewrap();
        this.syncScroll();
        int global = Mth.clamp((int) ((mouseY - this.y() - 2) / LINE_H) + this.text.scrollLine(), 0, this.rowCount() - 1);
        Visual visual = this.visual(global);
        int column = EditableText.columnIn(visual.context(), visual.row(), (int) mouseX - this.textLeft());
        this.text.placeCaret(visual.context().line(), column, extend);
        this.changed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.rewrap();
        int max = Math.max(0, this.rowCount() - this.visibleRows());
        if (max == 0) {
            return false;
        }
        this.text.setScroll(Mth.clamp(this.text.scrollLine() - (int) Math.signum(scrollY), 0, max), this.text.scrollX());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_A -> {
                    this.text.selectAll();
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    TextBox.setClipboard(this.text.selectedText());
                    return true;
                }
                case GLFW.GLFW_KEY_X -> {
                    if (this.text.hasSelection()) {
                        TextBox.setClipboard(this.text.selectedText());
                        this.text.backspace();
                        this.changed();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    String clipboard = TextBox.getClipboard();
                    if (!clipboard.isEmpty()) {
                        this.text.insert(clipboard, modifiers);
                        this.changed();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    this.text.backspaceWord();
                    this.changed();
                    return true;
                }
                default -> {
                }
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.text.newLine();
            this.changed();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            this.text.backspace();
            this.changed();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            this.text.delete();
            this.changed();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            this.text.insert("  ", modifiers);
            this.changed();
            return true;
        }
        // Up and down walk the wrapped rows, so a long paragraph behaves like a paragraph.
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            this.moveRow(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1, shift);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END) {
            this.rewrap();
            Visual visual = this.visual(this.caretRow());
            this.text.placeCaret(visual.context().line(),
                    keyCode == GLFW.GLFW_KEY_HOME ? visual.row().from() : visual.row().to(), shift);
            return true;
        }
        if (this.text.handleNavigation(keyCode, shift, ctrl)) {
            this.changed();
            return true;
        }
        return false;
    }

    /** Moves the caret one wrapped row up or down, keeping the horizontal position where possible. */
    private void moveRow(int delta, boolean extend) {
        this.rewrap();
        int current = this.caretRow();
        int target = Mth.clamp(current + delta, 0, this.rowCount() - 1);
        if (target == current) {
            return;
        }
        Visual from = this.visual(current);
        int caretX = EditableText.markerWidth(from.context().lineText(), from.row().from(), this.text.cursorColumn());
        Visual to = this.visual(target);
        this.text.placeCaret(to.context().line(), EditableText.columnIn(to.context(), to.row(), caretX), extend);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint < ' ') {
            return false;
        }
        this.text.insert(String.valueOf(codePoint), modifiers);
        this.changed();
        return true;
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean focused = this.focused();
        graphics.fill(this.x(), this.y(), this.right(), this.bottom(), Theme.FIELD);
        Theme.border(graphics, this.x(), this.y(), this.width(), this.height(), focused ? Theme.ACCENT : Theme.BORDER);

        this.rewrap();
        this.syncScroll();
        int first = this.text.scrollLine();
        int last = Math.min(this.rowCount(), first + this.visibleRows());
        boolean emptyText = this.text.value().isEmpty();

        Theme.clip(graphics, this.x() + 1, this.y() + 1, this.right() - 1, this.bottom() - 1);
        if (emptyText && !focused && this.placeholder != null) {
            Theme.text(graphics, this.placeholder, this.textLeft(), this.y() + 3, Theme.TEXT_MUTED);
        }
        for (int global = first; global < last; global++) {
            Visual visual = this.visual(global);
            EditableText.Row row = visual.row();
            int rowY = this.rowTop(global);
            if (focused) {
                this.drawRowSelection(graphics, visual, rowY);
            }
            if (row.to() > row.from()) {
                Theme.text(graphics, EditableText.styled(
                        visual.context().lineText().substring(row.from(), row.to()), visual.style()),
                        this.textLeft(), rowY, Theme.TEXT);
            }
        }
        if (focused) {
            this.drawCaret(graphics);
        }
        Theme.unclip(graphics);
    }

    /** Keeps the caret's row inside the visible rows. */
    private void syncScroll() {
        int visible = this.visibleRows();
        int max = Math.max(0, this.rowCount() - visible);
        int scroll = Mth.clamp(this.text.scrollLine(), 0, max);
        int caret = this.caretRow();
        if (caret < scroll) {
            scroll = caret;
        } else if (caret >= scroll + visible) {
            scroll = caret - visible + 1;
        }
        this.text.setScroll(Mth.clamp(scroll, 0, max), this.text.scrollX());
    }

    /** Paints the part of the selection that falls inside one wrapped row. */
    private void drawRowSelection(GuiGraphics graphics, Visual visual, int rowY) {
        if (!this.text.hasSelection()) {
            return;
        }
        String line = visual.context().lineText();
        EditableText.Row row = visual.row();
        int startLine = Math.min(this.text.anchorLine(), this.text.cursorLine());
        int endLine = Math.max(this.text.anchorLine(), this.text.cursorLine());
        if (visual.context().line() < startLine || visual.context().line() > endLine) {
            return;
        }
        int startColumn = visual.context().line() == startLine
                ? (this.text.anchorLine() <= this.text.cursorLine() ? this.text.anchorColumn() : this.text.cursorColumn())
                : 0;
        int endColumn = visual.context().line() == endLine
                ? (this.text.anchorLine() <= this.text.cursorLine() ? this.text.cursorColumn() : this.text.anchorColumn())
                : line.length();
        int from = Math.max(row.from(), Math.min(startColumn, endColumn));
        int to = Math.min(row.to(), Math.max(startColumn, endColumn));
        if (to <= from) {
            return;
        }
        int left = this.textLeft() + EditableText.markerWidth(line, row.from(), from);
        int right = this.textLeft() + EditableText.markerWidth(line, row.from(), to);
        graphics.fill(left, rowY - 1, Math.max(right, left + 3), rowY + LINE_H - 1, Theme.withAlpha(Theme.CYAN, 0x66));
    }

    private void drawCaret(GuiGraphics graphics) {
        int global = this.caretRow();
        Visual visual = this.visual(global);
        int caretX = this.textLeft()
                + EditableText.markerWidth(visual.context().lineText(), visual.row().from(), this.text.cursorColumn());
        int rowY = this.rowTop(global);
        graphics.fill(caretX, rowY - 1, caretX + 1, rowY + LINE_H - 1, Theme.TEXT);
    }
}

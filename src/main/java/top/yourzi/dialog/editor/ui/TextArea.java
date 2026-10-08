package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/**
 * Multi-line editor field for dialogue text and command bodies.
 *
 * <p>{@code §} formatting codes stay in the raw value but are painted as real styles, so the writer
 * sees the result rather than the codes. Long lines wrap at the field's width instead of running off
 * the right edge; the caret, selection, clicking and scrolling all work in those wrapped rows, while
 * the model keeps counting columns inside the logical line.
 */
public class TextArea extends UiNode {
    private static final int LINE_H = 10;

    private final EditableText text;
    private Component placeholder;
    private Consumer<String> onChange;
    private boolean dragSelecting;
    private String lastValue;
    private List<String> lastLines = List.of();
    private int lastWidth = -1;
    private List<EditableText.Row> rows = List.of();

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
        if (value.equals(this.lastValue)) {
            return;
        }
        this.lastValue = value;
        if (this.onChange != null) {
            this.onChange.accept(value);
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

    private int textWidth() {
        return Math.max(20, this.width() - Theme.PAD * 2);
    }

    /**
     * The rows the text currently occupies. Recomputing only when the text or the width changed keeps
     * typing cheap, since every keystroke would otherwise re-wrap the whole value.
     */
    private List<EditableText.Row> rows() {
        List<String> lines = this.text.lines();
        int width = this.textWidth();
        if (lines != this.lastLines || width != this.lastWidth) {
            this.lastLines = lines;
            this.lastWidth = width;
            this.rows = EditableText.wrapRows(lines, width);
        }
        return this.rows;
    }

    private int visibleRows() {
        return Math.max(1, (this.height() - 4) / LINE_H);
    }

    private int rowTop(int row) {
        return this.y() + 2 + (row - this.text.scrollLine()) * LINE_H;
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

    /** Turns a click into a logical line and column, following the wrapped row under the cursor. */
    private void placeCaretAt(double mouseX, double mouseY, boolean extend) {
        List<EditableText.Row> rows = this.rows();
        this.syncScroll(rows);
        int rowIndex = Mth.clamp((int) ((mouseY - this.y() - 2) / LINE_H) + this.text.scrollLine(), 0, rows.size() - 1);
        EditableText.Row row = rows.get(rowIndex);
        int column = EditableText.columnInRow(this.text.lines(), row, (int) mouseX - this.x() - Theme.PAD);
        this.text.placeCaret(row.line(), column, extend);
        this.changed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, this.rows().size() - this.visibleRows());
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
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            EditableText.Row row = this.rows().get(EditableText.rowOf(this.rows(), this.text.cursorLine(),
                    this.text.cursorColumn()));
            this.text.placeCaret(row.line(), row.from(), shift);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            EditableText.Row row = this.rows().get(EditableText.rowOf(this.rows(), this.text.cursorLine(),
                    this.text.cursorColumn()));
            this.text.placeCaret(row.line(), row.to(), shift);
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
        List<EditableText.Row> rows = this.rows();
        int current = EditableText.rowOf(rows, this.text.cursorLine(), this.text.cursorColumn());
        int target = Mth.clamp(current + delta, 0, rows.size() - 1);
        if (target == current) {
            return;
        }
        EditableText.Row from = rows.get(current);
        int caretX = EditableText.markerWidth(this.text.lines().get(from.line()), from.from(), this.text.cursorColumn());
        EditableText.Row to = rows.get(target);
        this.text.placeCaret(to.line(), EditableText.columnInRow(this.text.lines(), to, caretX), extend);
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

        List<String> lines = this.text.lines();
        List<EditableText.Row> rows = this.rows();
        int visible = this.visibleRows();
        this.syncScroll(rows);
        int first = this.text.scrollLine();
        int last = Math.min(rows.size(), first + visible);

        Theme.clip(graphics, this.x() + 1, this.y() + 1, this.right() - 1, this.bottom() - 1);
        boolean emptyText = lines.size() == 1 && lines.get(0).isEmpty();
        if (emptyText && !focused && this.placeholder != null) {
            Theme.text(graphics, this.placeholder, this.x() + Theme.PAD, this.y() + 3, Theme.TEXT_MUTED);
        }
        for (int index = first; index < last; index++) {
            EditableText.Row row = rows.get(index);
            int rowY = this.rowTop(index);
            String line = lines.get(row.line());
            if (focused) {
                this.drawRowSelection(graphics, row, line, rowY);
            }
            if (row.to() > row.from()) {
                Theme.text(graphics, EditableText.styled(line.substring(row.from(), row.to())),
                        this.x() + Theme.PAD, rowY, Theme.TEXT);
            }
        }
        if (focused) {
            this.drawCaret(graphics, rows);
        }
        Theme.unclip(graphics);
    }

    /** Keeps the caret's row inside the visible rows. */
    private void syncScroll(List<EditableText.Row> rows) {
        int visible = this.visibleRows();
        int max = Math.max(0, rows.size() - visible);
        int scroll = Mth.clamp(this.text.scrollLine(), 0, max);
        int caret = EditableText.rowOf(rows, this.text.cursorLine(), this.text.cursorColumn());
        if (caret < scroll) {
            scroll = caret;
        } else if (caret >= scroll + visible) {
            scroll = caret - visible + 1;
        }
        this.text.setScroll(Mth.clamp(scroll, 0, max), this.text.scrollX());
    }

    /** Paints the part of the selection that falls inside one wrapped row. */
    private void drawRowSelection(GuiGraphics graphics, EditableText.Row row, String line, int rowY) {
        if (!this.text.hasSelection()) {
            return;
        }
        int startLine = Math.min(this.text.anchorLine(), this.text.cursorLine());
        int endLine = Math.max(this.text.anchorLine(), this.text.cursorLine());
        if (row.line() < startLine || row.line() > endLine) {
            return;
        }
        int startColumn = row.line() == startLine
                ? (this.text.anchorLine() <= this.text.cursorLine() ? this.text.anchorColumn() : this.text.cursorColumn())
                : 0;
        int endColumn = row.line() == endLine
                ? (this.text.anchorLine() <= this.text.cursorLine() ? this.text.cursorColumn() : this.text.anchorColumn())
                : line.length();
        int from = Math.max(row.from(), Math.min(startColumn, endColumn));
        int to = Math.min(row.to(), Math.max(startColumn, endColumn));
        if (to <= from) {
            return;
        }
        int left = this.x() + Theme.PAD + EditableText.markerWidth(line, row.from(), from);
        int right = this.x() + Theme.PAD + EditableText.markerWidth(line, row.from(), to);
        graphics.fill(left, rowY - 1, Math.max(right, left + 3), rowY + LINE_H - 1, Theme.withAlpha(Theme.CYAN, 0x66));
    }

    private void drawCaret(GuiGraphics graphics, List<EditableText.Row> rows) {
        int index = EditableText.rowOf(rows, this.text.cursorLine(), this.text.cursorColumn());
        EditableText.Row row = rows.get(index);
        String line = this.text.lines().get(row.line());
        int caretX = this.x() + Theme.PAD + EditableText.markerWidth(line, row.from(), this.text.cursorColumn());
        int rowY = this.rowTop(index);
        graphics.fill(caretX, rowY - 1, caretX + 1, rowY + LINE_H - 1, Theme.TEXT);
    }
}

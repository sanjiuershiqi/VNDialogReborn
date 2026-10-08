package top.yourzi.dialog.editor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Multi-line editor field for dialogue text and command bodies.
 *
 * <p>{@code §} formatting codes stay in the raw value but are painted as real styles, so the writer
 * sees the result rather than the codes. Long lines wrap at the field's width instead of running off
 * the right edge; caret, selection, clicking and scrolling all work on those rows, while the model
 * keeps counting columns inside the logical line.
 */
public class TextArea extends UiNode {
    private static final int LINE_H = 10;

    private final EditableText text;
    private Component placeholder;
    private Consumer<String> onChange;
    private boolean dragSelecting;
    private String lastValue;
    private int lastWrapWidth = -1;
    private List<String> lastWrappedLines;
    private List<EditableText.RowCtx> wrapped = List.of();
    private int rowCount;

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
     * The wrapped rows, rebuilt only when the text or the width changed: wrapping every line on each
     * keystroke would cost more than drawing it.
     */
    private void rewrap() {
        int width = this.wrapWidth();
        List<String> lines = this.text.lines();
        if (width == this.lastWrapWidth && lines == this.lastWrappedLines
                && this.wrapped.size() == lines.size()) {
            return;
        }
        this.lastWrapWidth = width;
        this.lastWrappedLines = lines;
        List<EditableText.RowCtx> contexts = new ArrayList<>();
        int rows = 0;
        for (int index = 0; index < lines.size(); index++) {
            EditableText.RowCtx context = EditableText.wrapLine(index, lines.get(index), width);
            contexts.add(context);
            rows += context.rows().size();
        }
        this.wrapped = contexts;
        this.rowCount = Math.max(1, rows);
    }

    /** Context of the logical line the global row {@code global} belongs to. */
    private EditableText.RowCtx contextOf(int global) {
        int seen = 0;
        for (EditableText.RowCtx context : this.wrapped) {
            if (global < seen + context.rows().size()) {
                return context;
            }
            seen += context.rows().size();
        }
        return this.wrapped.get(this.wrapped.size() - 1);
    }

    private EditableText.Row rowOf(int global) {
        EditableText.RowCtx context = this.contextOf(global);
        return context.rows().get(this.localOf(global, context));
    }

    private int localOf(int global, EditableText.RowCtx context) {
        int seen = 0;
        for (EditableText.RowCtx candidate : this.wrapped) {
            if (candidate == context) {
                break;
            }
            seen += candidate.rows().size();
        }
        return Mth.clamp(global - seen, 0, context.rows().size() - 1);
    }

    /** Global row of the caret. */
    private int caretRow() {
        return EditableText.locate(this.wrapped, this.text.cursorLine(), this.text.cursorColumn());
    }

    /** Left edge of the text inside the field. */
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
        int global = Mth.clamp((int) ((mouseY - this.y() - 2) / LINE_H) + this.text.scrollLine(), 0, this.rowCount - 1);
        EditableText.RowCtx context = this.contextOf(global);
        EditableText.Row row = this.rowOf(global);
        int column = EditableText.columnIn(context, row, (int) mouseX - this.textLeft());
        this.text.placeCaret(context.line(), column, extend);
        this.changed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.rewrap();
        int max = Math.max(0, this.rowCount - this.visibleRows());
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
            EditableText.RowCtx context = this.contextOf(this.caretRow());
            EditableText.Row row = this.rowOf(this.caretRow());
            this.text.placeCaret(context.line(), keyCode == GLFW.GLFW_KEY_HOME ? row.from() : row.to(), shift);
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
        int target = Mth.clamp(current + delta, 0, this.rowCount - 1);
        if (target == current) {
            return;
        }
        EditableText.RowCtx fromContext = this.contextOf(current);
        EditableText.Row from = this.rowOf(current);
        int caretX = EditableText.markerWidth(fromContext.lineText(), from.from(), this.text.cursorColumn());
        EditableText.RowCtx toContext = this.contextOf(target);
        EditableText.Row to = this.rowOf(target);
        this.text.placeCaret(toContext.line(), EditableText.columnIn(toContext, to, caretX), extend);
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
        int last = Math.min(this.rowCount, first + this.visibleRows());
        boolean emptyText = this.text.value().isEmpty();

        Theme.clip(graphics, this.x() + 1, this.y() + 1, this.right() - 1, this.bottom() - 1);
        if (emptyText && !focused && this.placeholder != null) {
            Theme.text(graphics, this.placeholder, this.textLeft(), this.y() + 3, Theme.TEXT_MUTED);
        }
        for (int global = first; global < last; global++) {
            EditableText.RowCtx context = this.contextOf(global);
            EditableText.Row row = this.rowOf(global);
            int rowY = this.rowTop(global);
            if (focused) {
                this.drawRowSelection(graphics, context, row, rowY);
            }
            if (row.to() > row.from()) {
                Theme.text(graphics, EditableText.styled(context.lineText().substring(row.from(), row.to()),
                        context.styleAt(this.localOf(global, context))), this.textLeft(), rowY, Theme.TEXT);
            }
        }
        if (focused) {
            this.drawCaret(graphics);
        }
        Theme.unclip(graphics);
    }

    /** Keeps the caret's row inside the visible rows. */
    private void syncScroll() {
        int max = Math.max(0, this.rowCount - this.visibleRows());
        int scroll = Mth.clamp(this.text.scrollLine(), 0, max);
        int caret = this.caretRow();
        if (caret < scroll) {
            scroll = caret;
        } else if (caret >= scroll + this.visibleRows()) {
            scroll = caret - this.visibleRows() + 1;
        }
        this.text.setScroll(Mth.clamp(scroll, 0, max), this.text.scrollX());
    }

    /** Paints the part of the selection that falls inside one wrapped row. */
    private void drawRowSelection(GuiGraphics graphics, EditableText.RowCtx context, EditableText.Row row, int rowY) {
        if (!this.text.hasSelection()) {
            return;
        }
        String line = context.lineText();
        int startLine = Math.min(this.text.anchorLine(), this.text.cursorLine());
        int endLine = Math.max(this.text.anchorLine(), this.text.cursorLine());
        if (context.line() < startLine || context.line() > endLine) {
            return;
        }
        int startColumn = context.line() == startLine
                ? (this.text.anchorLine() <= this.text.cursorLine() ? this.text.anchorColumn() : this.text.cursorColumn())
                : 0;
        int endColumn = context.line() == endLine
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
        EditableText.RowCtx context = this.contextOf(global);
        EditableText.Row row = this.rowOf(global);
        int caretX = this.textLeft() + EditableText.markerWidth(context.lineText(), row.from(), this.text.cursorColumn());
        int rowY = this.rowTop(global);
        graphics.fill(caretX, rowY - 1, caretX + 1, rowY + LINE_H - 1, Theme.TEXT);
    }
}

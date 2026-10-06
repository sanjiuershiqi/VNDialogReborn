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
 * sees the result rather than the codes. The caret, selection and vertical scroll follow the same
 * codec, which makes clicking inside a coloured line land on the glyph under the cursor.
 */
public class TextArea extends UiNode {
    private static final int LINE_H = 10;

    private final EditableText text;
    private Component placeholder;
    private Consumer<String> onChange;
    private boolean dragSelecting;
    private String lastValue;

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

    private int visibleLines() {
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

    private void placeCaretAt(double mouseX, double mouseY, boolean extend) {
        int line = Mth.clamp((int) ((mouseY - this.y() - 2) / LINE_H) + this.text.scrollLine(), 0,
                this.text.lineCount() - 1);
        int column = EditableText.columnAt(this.text.lines().get(line), (int) mouseX - this.x() - Theme.PAD);
        this.text.placeCaret(line, column, extend);
        this.changed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, this.text.lineCount() - this.visibleLines());
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
        if (this.text.handleNavigation(keyCode, shift, ctrl)) {
            this.changed();
            return true;
        }
        return false;
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
        int visible = this.visibleLines();
        this.syncScroll(visible);
        int scrollLine = this.text.scrollLine();

        graphics.enableScissor(this.x() + 1, this.y() + 1, this.right() - 1, this.bottom() - 1);
        boolean emptyText = lines.size() == 1 && lines.get(0).isEmpty();
        if (emptyText && !focused && this.placeholder != null) {
            Theme.text(graphics, this.placeholder, this.x() + Theme.PAD, this.y() + 3, Theme.TEXT_MUTED);
        }
        for (int index = scrollLine; index < Math.min(lines.size(), scrollLine + visible); index++) {
            int rowY = this.y() + 2 + (index - scrollLine) * LINE_H;
            String line = lines.get(index);
            if (focused) {
                this.drawLineSelection(graphics, line, index, rowY);
            }
            if (!line.isEmpty()) {
                Theme.text(graphics, EditableText.styled(line), this.x() + Theme.PAD, rowY, Theme.TEXT);
            }
        }
        if (focused) {
            this.drawCaret(graphics);
        }
        graphics.disableScissor();
    }

    private void syncScroll(int visible) {
        int max = Math.max(0, this.text.lineCount() - visible);
        int scroll = this.text.scrollLine();
        if (scroll > max) {
            scroll = max;
        }
        if (this.text.cursorLine() < scroll) {
            scroll = this.text.cursorLine();
        } else if (this.text.cursorLine() >= scroll + visible) {
            scroll = this.text.cursorLine() - visible + 1;
        }
        this.text.setScroll(Mth.clamp(scroll, 0, max), this.text.scrollX());
    }

    private void drawLineSelection(GuiGraphics graphics, String line, int index, int rowY) {
        if (!this.text.hasSelection()) {
            return;
        }
        int[] span = this.selectionSpan(index, line.length());
        if (span == null) {
            return;
        }
        int left = this.x() + Theme.PAD + EditableText.widthTo(line, span[0]);
        int right = this.x() + Theme.PAD + EditableText.widthTo(line, span[1]);
        graphics.fill(left, rowY - 1, Math.max(right, left + 3), rowY + LINE_H - 1, Theme.withAlpha(Theme.CYAN, 0x66));
    }

    /** Visible column range of the selection on one line, or null when the line is untouched. */
    private int[] selectionSpan(int index, int length) {
        int startLine = Math.min(this.text.anchorLine(), this.text.cursorLine());
        int endLine = Math.max(this.text.anchorLine(), this.text.cursorLine());
        if (index < startLine || index > endLine) {
            return null;
        }
        int startColumn = index == startLine
                ? (this.text.anchorLine() <= this.text.cursorLine() ? this.text.anchorColumn() : this.text.cursorColumn())
                : 0;
        int endColumn = index == endLine
                ? (this.text.anchorLine() <= this.text.cursorLine() ? this.text.cursorColumn() : this.text.anchorColumn())
                : length;
        return new int[]{Math.min(startColumn, endColumn), Math.max(startColumn, endColumn)};
    }

    private void drawCaret(GuiGraphics graphics) {
        String line = this.text.lines().get(this.text.cursorLine());
        int rowY = this.y() + 2 + (this.text.cursorLine() - this.text.scrollLine()) * LINE_H;
        int caretX = this.x() + Theme.PAD + EditableText.widthTo(line, this.text.cursorColumn());
        graphics.fill(caretX, rowY - 1, caretX + 1, rowY + LINE_H - 1, Theme.TEXT);
    }

}

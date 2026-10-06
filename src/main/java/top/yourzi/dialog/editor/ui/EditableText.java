package top.yourzi.dialog.editor.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Text editing model shared by single-line and multi-line fields.
 *
 * <p>The stored value is the raw string, which is exactly what the dialog JSON holds: legacy
 * {@code \u00a7} formatting codes included. Rendering translates those codes into styles instead of
 * showing them, and measurement walks the same codec so a coloured run is measured by its visible
 * glyphs. Indices therefore always refer to positions in the raw string.
 */
public final class EditableText {
    private static final int MAX_LENGTH = 32767;

    private final boolean multiline;
    private final List<String> lines = new ArrayList<>();
    private int cursorLine;
    private int cursorColumn;
    private int anchorLine;
    private int anchorColumn;
    private boolean selecting;
    private int scrollLine;
    private int scrollX;
    private Consumer<String> responder = value -> {
    };

    public EditableText(boolean multiline, String value) {
        this.multiline = multiline;
        this.setValue(value == null ? "" : value);
    }

    public void setResponder(Consumer<String> responder) {
        this.responder = responder;
    }

    public boolean multiline() {
        return this.multiline;
    }

    public String value() {
        return String.join("\n", this.lines);
    }

    /** Replaces the content and moves the caret to the end, notifying the responder. */
    public void setValue(String value) {
        this.applyValue(value);
        this.responder.accept(this.value());
    }

    /** Replaces the content without notifying the responder; used when binding a fresh model. */
    public void setValueSilently(String value) {
        this.applyValue(value);
    }

    private void applyValue(String value) {
        this.lines.clear();
        String text = value == null ? "" : value;
        if (this.multiline) {
            for (String line : text.split("\n", -1)) {
                this.lines.add(line);
            }
        } else {
            this.lines.add(text.replace("\n", " "));
        }
        if (this.lines.isEmpty()) {
            this.lines.add("");
        }
        this.cursorLine = this.lines.size() - 1;
        this.cursorColumn = this.lines.get(this.cursorLine).length();
        this.clearSelection();
        this.scrollLine = Integer.MAX_VALUE;
        this.scrollX = Integer.MAX_VALUE;
    }

    public int lineCount() {
        return this.lines.size();
    }

    public List<String> lines() {
        return this.lines;
    }

    public int cursorLine() {
        return this.cursorLine;
    }

    public int cursorColumn() {
        return this.cursorColumn;
    }

    public int anchorLine() {
        return this.anchorLine;
    }

    public int anchorColumn() {
        return this.anchorColumn;
    }

    public int scrollLine() {
        return this.scrollLine;
    }

    public int scrollX() {
        return this.scrollX;
    }

    public void setScroll(int line, int x) {
        this.scrollLine = line;
        this.scrollX = x;
    }

    public boolean hasSelection() {
        return this.selecting && (this.anchorLine != this.cursorLine || this.anchorColumn != this.cursorColumn);
    }

    private void clearSelection() {
        this.selecting = false;
        this.anchorLine = this.cursorLine;
        this.anchorColumn = this.cursorColumn;
    }

    /** True when the anchor sits after the caret in reading order. */
    private boolean selectionReversed() {
        return this.anchorLine > this.cursorLine
                || (this.anchorLine == this.cursorLine && this.anchorColumn > this.cursorColumn);
    }

    public String selectedText() {
        if (!this.hasSelection()) {
            return "";
        }
        int[] start = this.selectionStart();
        int[] end = this.selectionEnd();
        if (start[0] == end[0]) {
            return this.lines.get(start[0]).substring(start[1], end[1]);
        }
        StringBuilder builder = new StringBuilder(this.lines.get(start[0]).substring(start[1]));
        for (int line = start[0] + 1; line < end[0]; line++) {
            builder.append('\n').append(this.lines.get(line));
        }
        builder.append('\n').append(this.lines.get(end[0]).substring(0, end[1]));
        return builder.toString();
    }

    private int[] selectionStart() {
        if (!this.selectionReversed()) {
            return new int[]{this.anchorLine, this.anchorColumn};
        }
        return new int[]{this.cursorLine, this.cursorColumn};
    }

    private int[] selectionEnd() {
        if (!this.selectionReversed()) {
            return new int[]{this.cursorLine, this.cursorColumn};
        }
        return new int[]{this.anchorLine, this.anchorColumn};
    }

    public void selectAll() {
        this.anchorLine = 0;
        this.anchorColumn = 0;
        this.cursorLine = this.lines.size() - 1;
        this.cursorColumn = this.lines.get(this.cursorLine).length();
        this.selecting = true;
    }

    /** Removes the selected range; returns the line/column left behind through the out array. */
    private void deleteSelection() {
        if (!this.hasSelection()) {
            return;
        }
        int[] start = this.selectionStart();
        int[] end = this.selectionEnd();
        String head = this.lines.get(start[0]).substring(0, start[1]);
        String tail = this.lines.get(end[0]).substring(end[1]);
        this.lines.set(start[0], head + tail);
        for (int line = end[0]; line > start[0]; line--) {
            this.lines.remove(line);
        }
        this.cursorLine = start[0];
        this.cursorColumn = start[1];
        this.clearSelection();
    }

    public void insert(String text, int modifiers) {
        if (text.isEmpty()) {
            return;
        }
        this.deleteSelection();
        String current = this.lines.get(this.cursorLine);
        if (!this.multiline || !text.contains("\n")) {
            String merged = this.trim(current.substring(0, this.cursorColumn) + text + current.substring(this.cursorColumn));
            this.lines.set(this.cursorLine, merged);
            this.cursorColumn = Math.min(merged.length(), this.cursorColumn + text.length());
        } else {
            String head = current.substring(0, this.cursorColumn);
            String tail = current.substring(this.cursorColumn);
            String[] parts = (head + text + tail).split("\n", -1);
            this.lines.set(this.cursorLine, parts[0]);
            for (int i = 1; i < parts.length; i++) {
                this.lines.add(this.cursorLine + i, parts[i]);
            }
            this.cursorLine += parts.length - 1;
            this.cursorColumn = parts[parts.length - 1].length();
        }
        this.clearSelection();
        this.notifyChanged();
    }

    public void newLine() {
        this.insert("\n", 0);
    }

    private String trim(String line) {
        return line.length() <= MAX_LENGTH ? line : line.substring(0, MAX_LENGTH);
    }

    private void notifyChanged() {
        this.responder.accept(this.value());
    }

    public void backspace() {
        if (this.hasSelection()) {
            this.deleteSelection();
            this.notifyChanged();
            return;
        }
        String current = this.lines.get(this.cursorLine);
        if (this.cursorColumn > 0) {
            this.lines.set(this.cursorLine, current.substring(0, this.cursorColumn - 1) + current.substring(this.cursorColumn));
            this.cursorColumn--;
        } else if (this.cursorLine > 0) {
            String previous = this.lines.get(this.cursorLine - 1);
            this.lines.set(this.cursorLine - 1, previous + current);
            this.lines.remove(this.cursorLine);
            this.cursorLine--;
            this.cursorColumn = previous.length();
        } else {
            return;
        }
        this.clearSelection();
        this.notifyChanged();
    }

    /** Backspaces a whole word, matching the usual editor behaviour. */
    public void backspaceWord() {
        if (this.hasSelection()) {
            this.backspace();
            return;
        }
        String current = this.lines.get(this.cursorLine);
        if (this.cursorColumn == 0) {
            this.backspace();
            return;
        }
        int target = this.cursorColumn;
        while (target > 0 && Character.isWhitespace(current.charAt(target - 1))) {
            target--;
        }
        while (target > 0 && !Character.isWhitespace(current.charAt(target - 1))) {
            target--;
        }
        this.lines.set(this.cursorLine, current.substring(0, target) + current.substring(this.cursorColumn));
        this.cursorColumn = target;
        this.clearSelection();
        this.notifyChanged();
    }

    public void delete() {
        if (this.hasSelection()) {
            this.deleteSelection();
            this.notifyChanged();
            return;
        }
        String current = this.lines.get(this.cursorLine);
        if (this.cursorColumn < current.length()) {
            this.lines.set(this.cursorLine, current.substring(0, this.cursorColumn) + current.substring(this.cursorColumn + 1));
        } else if (this.cursorLine < this.lines.size() - 1) {
            this.lines.set(this.cursorLine, current + this.lines.get(this.cursorLine + 1));
            this.lines.remove(this.cursorLine + 1);
        } else {
            return;
        }
        this.clearSelection();
        this.notifyChanged();
    }

    public void moveCaret(int deltaLines, int deltaColumns, boolean extend) {
        if (deltaLines != 0 && this.multiline) {
            this.cursorLine = Mth.clamp(this.cursorLine + deltaLines, 0, this.lines.size() - 1);
            this.cursorColumn = Math.min(this.cursorColumn, this.lines.get(this.cursorLine).length());
        }
        if (deltaColumns != 0) {
            if (deltaColumns > 0) {
                this.cursorColumn += deltaColumns;
                while (this.cursorColumn > this.lines.get(this.cursorLine).length() && this.cursorLine < this.lines.size() - 1) {
                    this.cursorColumn -= this.lines.get(this.cursorLine).length() + 1;
                    this.cursorLine++;
                }
            } else {
                this.cursorColumn += deltaColumns;
                while (this.cursorColumn < 0 && this.cursorLine > 0) {
                    this.cursorLine--;
                    this.cursorColumn += this.lines.get(this.cursorLine).length() + 1;
                }
            }
            this.cursorColumn = Mth.clamp(this.cursorColumn, 0, this.lines.get(this.cursorLine).length());
        }
        if (extend) {
            this.selecting = true;
        } else {
            this.clearSelection();
        }
    }

    public void moveToLineBoundary(boolean end, boolean extend) {
        this.cursorColumn = end ? this.lines.get(this.cursorLine).length() : 0;
        if (extend) {
            this.selecting = true;
        } else {
            this.clearSelection();
        }
    }

    public void moveWord(int direction, boolean extend) {
        String line = this.lines.get(this.cursorLine);
        int index = this.cursorColumn;
        if (direction < 0) {
            while (index > 0 && Character.isWhitespace(line.charAt(index - 1))) {
                index--;
            }
            while (index > 0 && !Character.isWhitespace(line.charAt(index - 1))) {
                index--;
            }
        } else {
            while (index < line.length() && !Character.isWhitespace(line.charAt(index))) {
                index++;
            }
            while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
                index++;
            }
        }
        this.cursorColumn = index;
        if (extend) {
            this.selecting = true;
        } else {
            this.clearSelection();
        }
    }

    /** Places the caret at the given line/column, extending the selection when asked. */
    public void placeCaret(int line, int column, boolean extend) {
        this.cursorLine = Mth.clamp(line, 0, this.lines.size() - 1);
        this.cursorColumn = Mth.clamp(column, 0, this.lines.get(this.cursorLine).length());
        if (extend) {
            this.selecting = true;
        } else {
            this.clearSelection();
        }
    }

    /** Handles caret movement keys; @return true when the key was consumed. */
    public boolean handleNavigation(int keyCode, boolean shift, boolean ctrl) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> {
                if (ctrl) {
                    this.moveWord(-1, shift);
                } else {
                    this.moveCaret(0, -1, shift);
                }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (ctrl) {
                    this.moveWord(1, shift);
                } else {
                    this.moveCaret(0, 1, shift);
                }
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                if (!this.multiline) {
                    return false;
                }
                this.moveCaret(-1, 0, shift);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (!this.multiline) {
                    return false;
                }
                this.moveCaret(1, 0, shift);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                this.moveToLineBoundary(false, shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                this.moveToLineBoundary(true, shift);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // ----- measurement -----

    /** Renders {@code line} into a styled component so formatting codes become real styles. */
    public static MutableComponent styled(String line) {
        MutableComponent result = net.minecraft.network.chat.Component.empty();
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\u00a7' && i + 1 < line.length()) {
                ChatFormatting formatting = ChatFormatting.getByCode(line.charAt(i + 1));
                if (run.length() > 0) {
                    result.append(net.minecraft.network.chat.Component.literal(run.toString()).withStyle(style));
                    run.setLength(0);
                }
                if (formatting != null) {
                    style = formatting == ChatFormatting.RESET ? Style.EMPTY : style.applyFormat(formatting);
                } else {
                    run.append(c);
                }
                i += 2;
                continue;
            }
            run.append(c);
            i++;
        }
        if (run.length() > 0) {
            result.append(net.minecraft.network.chat.Component.literal(run.toString()).withStyle(style));
        }
        return result;
    }

    /**
     * Visible width of {@code line} up to (excluding) {@code column}. Formatting codes count as
     * zero width, so the caret tracks glyphs rather than raw indices.
     */
    public static int widthTo(String line, int column) {
        int limit = Mth.clamp(column, 0, line.length());
        if (limit == 0) {
            return 0;
        }
        return Theme.font().width(styled(line.substring(0, limit)));
    }

    /** Character index in {@code line} closest to {@code pixelX}. */
    public static int columnAt(String line, int pixelX) {
        if (pixelX <= 0) {
            return 0;
        }
        int index = 0;
        while (index < line.length()) {
            if (line.charAt(index) == '\u00a7' && index + 1 < line.length()
                    && ChatFormatting.getByCode(line.charAt(index + 1)) != null) {
                index += 2;
                continue;
            }
            index++;
            if (widthTo(line, index) > pixelX) {
                return index - 1;
            }
        }
        return line.length();
    }
}

package top.yourzi.dialog.editor.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

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

    public EditableText(boolean multiline, String value) {
        this.multiline = multiline;
        this.setValueSilently(value);
    }

    public String value() {
        return String.join("\n", this.lines);
    }

    /** Replaces the content and moves the caret to the end. Widgets decide whether to notify. */
    public void setValueSilently(String value) {
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
        this.scrollLine = 0;
        this.scrollX = 0;
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
        // A selected range must not cut a code in half either; step both ends out to a character.
        start[1] = columnStart(this.lines.get(start[0]), start[1]);
        end[1] = columnStart(this.lines.get(end[0]), end[1]);
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
    }

    public void newLine() {
        this.insert("\n", 0);
    }

    private String trim(String line) {
        return line.length() <= MAX_LENGTH ? line : line.substring(0, MAX_LENGTH);
    }

    /**
     * Deletes the character before the caret.
     *
     * <p>A formatting code counts as one thing. With the caret between the two characters of a code,
     * or right after the code, the whole code goes; only otherwise is one character removed. Splitting
     * a code instead would leave a lone section sign, and drawn text swallows the character after a
     * lone section sign, so the next character would disappear as well.
     */
    public void backspace() {
        if (this.hasSelection()) {
            this.deleteSelection();
            return;
        }
        String current = this.lines.get(this.cursorLine);
        if (this.cursorColumn > 0) {
            int[] code = codeSpanBefore(current, this.cursorColumn);
            int from = code != null ? code[0] : this.cursorColumn - 1;
            int to = code != null ? code[1] : this.cursorColumn;
            this.lines.set(this.cursorLine, current.substring(0, from) + current.substring(to));
            this.cursorColumn = from;
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
    }

    /**
     * Backspaces a whole word, matching the usual editor behaviour, except that a formatting code
     * counts as one thing: with the caret in or right after a code, the code goes and the word stays.
     */
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
        if (codeSpanBefore(current, this.cursorColumn) != null) {
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
        if (target == this.cursorColumn) {
            this.backspace();
            return;
        }
        this.lines.set(this.cursorLine, current.substring(0, target) + current.substring(this.cursorColumn));
        this.cursorColumn = target;
        this.clearSelection();
    }

    /**
     * The formatting code the caret is inside or right after, as {start, end}, or null.
     *
     * <p>Removing one character of a code pair is what leaves the stray section sign, so backspacing
     * past a code takes the whole code and leaves the character in front of it alone.
     */
    private static int[] codeSpanBefore(String line, int column) {
        int i = 0;
        while (i < line.length()) {
            int length = FormatCodes.codeLength(line, i);
            if (length == 0) {
                i++;
                continue;
            }
            if (column > i && column <= i + length) {
                return new int[]{i, i + length};
            }
            i += length;
        }
        return null;
    }

    /** The formatting code the caret sits on, as {start, end}, or null when it sits on a character. */
    private static int[] codeSpanAt(String line, int column) {
        int i = 0;
        while (i < line.length()) {
            int length = FormatCodes.codeLength(line, i);
            if (length == 0) {
                i++;
                continue;
            }
            if (column >= i && column < i + length) {
                return new int[]{i, i + length};
            }
            i += length;
        }
        return null;
    }

    /** {@code column}, or the first character of the code it falls inside. */
    private static int columnStart(String line, int column) {
        int i = 0;
        while (i < line.length()) {
            int length = FormatCodes.codeLength(line, i);
            if (length == 0) {
                i++;
                continue;
            }
            if (column > i && column <= i + length) {
                return i;
            }
            i += length;
        }
        return Mth.clamp(column, 0, line.length());
    }

    public void delete() {
        if (this.hasSelection()) {
            this.deleteSelection();
            return;
        }
        String current = this.lines.get(this.cursorLine);
        if (this.cursorColumn < current.length()) {
            int[] code = codeSpanAt(current, this.cursorColumn);
            int from = code != null ? code[0] : this.cursorColumn;
            int to = code != null ? code[1] : this.cursorColumn + 1;
            this.lines.set(this.cursorLine, current.substring(0, from) + current.substring(to));
            this.cursorColumn = from;
        } else if (this.cursorLine < this.lines.size() - 1) {
            this.lines.set(this.cursorLine, current + this.lines.get(this.cursorLine + 1));
            this.lines.remove(this.cursorLine + 1);
        } else {
            return;
        }
        this.clearSelection();
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

    /** Stand-in glyph for {@code §} while editing; Minecraft would otherwise swallow the code. */
    private static final char MARK_GLYPH = '\u00b6';

    /**
     * Renders an editing line: text takes the style its codes select, and every code stays visible
     * as a dim marker. Each raw character maps to exactly one drawn character, so caret and
     * selection maths are plain prefix widths.
     */
    public static MutableComponent styled(String line) {
        return styled(line, Style.EMPTY);
    }

    /**
     * As {@link #styled(String)}, but continuing from the style carried into this part of the line by
     * codes written before it. A wrapped row starts in the middle of a line, so it needs the style in
     * force at its first character, which is what keeps a colour running across a wrap.
     */
    public static MutableComponent styled(String line, Style base) {
        MutableComponent result = Component.empty();
        Style code = Style.EMPTY.withColor(Theme.TEXT_MUTED & 0xFFFFFF);
        StringBuilder run = new StringBuilder();
        int i = 0;
        Style style = base;
        while (i < line.length()) {
            int length = FormatCodes.codeLength(line, i);
            if (length == 0) {
                char c = line.charAt(i++);
                run.append(c == FormatCodes.MARK ? MARK_GLYPH : c);
                continue;
            }
            if (run.length() > 0) {
                result.append(Component.literal(run.toString()).withStyle(style));
                run.setLength(0);
            }
            String token = line.substring(i, i + length);
            result.append(Component.literal(token.replace(FormatCodes.MARK, MARK_GLYPH)).withStyle(code));
            style = FormatCodes.apply(style, line, i, length);
            i += length;
        }
        if (run.length() > 0) {
            result.append(Component.literal(run.toString()).withStyle(style));
        }
        return result;
    }


    /** Drawn width of {@code line} up to (excluding) {@code column}. */
    public static int widthTo(String line, int column) {
        int limit = Mth.clamp(column, 0, line.length());
        return limit == 0 ? 0 : Theme.font().width(styled(line.substring(0, limit)));
    }

    /** Character index in {@code line} closest to {@code pixelX}. */
    public static int columnAt(String line, int pixelX) {
        if (pixelX <= 0) {
            return 0;
        }
        int previous = 0;
        for (int index = 1; index <= line.length(); index++) {
            int width = widthTo(line, index);
            if (width > pixelX) {
                return pixelX - previous < width - pixelX ? index - 1 : index;
            }
            previous = width;
        }
        return line.length();
    }

    // ----- wrapped rows -----

    /**
     * One drawn row of a line.
     *
     * @param from  first character of the row; always at a character, never inside a {@code §} code
     * @param to    character after the last one of the row
     * @param style style carried into the row by the codes written before it
     */
    public record Row(int from, int to, Style style) {
    }

    /**
     * Breaks {@code line} into the rows that fit {@code width}, the way it is drawn.
     *
     * <p>A formatting code takes no room, so it stays with the row it was written in and its effect
     * is carried into the following rows through {@link Row#style}. That is what stops a colour from
     * ending at a wrap and stops half a code from being drawn as literal text.
     */
    public static List<Row> wrap(String line, int width) {
        List<Row> rows = new ArrayList<>();
        int safeWidth = Math.max(8, width);
        if (line.isEmpty()) {
            rows.add(new Row(0, 0, Style.EMPTY));
            return rows;
        }
        int from = 0;
        int runWidth = 0;
        int at = 0;
        Style style = styleBefore(line, 0);
        while (at < line.length()) {
            int length = FormatCodes.codeLength(line, at);
            if (length > 0) {
                at += length;
                continue;
            }
            int glyph = drawnWidth(line, at, at + 1);
            if (runWidth > 0 && runWidth + glyph > safeWidth) {
                rows.add(new Row(from, at, style));
                from = at;
                style = styleBefore(line, at);
                runWidth = 0;
            }
            runWidth += glyph;
            at++;
        }
        rows.add(new Row(from, line.length(), style));
        return rows;
    }

    /** Style in force just before {@code column}, applying every code written earlier in the line. */
    public static Style styleBefore(String line, int column) {
        Style style = Style.EMPTY;
        int limit = Mth.clamp(column, 0, line.length());
        int i = 0;
        while (i < limit) {
            int length = FormatCodes.codeLength(line, i);
            if (length == 0) {
                i++;
            } else {
                style = FormatCodes.apply(style, line, i, length);
                i += length;
            }
        }
        return style;
    }

    /** Row holding {@code column}, which is the later row when a column is exactly a row break. */
    public static int rowOf(List<Row> rows, int column) {
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (column >= row.from() && (column <= row.to() || index == rows.size() - 1)) {
                return index;
            }
        }
        return rows.size() - 1;
    }

    /** Column inside {@code rows[rowIndex]} closest to {@code pixelX}, measured from the row's left edge. */
    public static int columnInRow(String line, List<Row> rows, int rowIndex, int pixelX) {
        Row row = rows.get(Mth.clamp(rowIndex, 0, rows.size() - 1));
        if (pixelX <= 0) {
            return row.from();
        }
        int best = row.from();
        int bestDistance = Math.abs(pixelX);
        for (int at = row.from(); at < row.to(); at++) {
            if (FormatCodes.codeLength(line, at) > 0) {
                // A code draws nothing and takes no room; a click never lands beside one.
                continue;
            }
            int middle = drawnWidth(line, row.from(), at) + drawnWidth(line, at, at + 1) / 2;
            if (Math.abs(pixelX - middle) <= bestDistance) {
                bestDistance = Math.abs(pixelX - middle);
                best = at;
            }
        }
        int end = drawnWidth(line, row.from(), row.to());
        if (Math.abs(pixelX - end) < bestDistance) {
            best = row.to();
        }
        return best;
    }

    /** Drawn width of {@code line[from, to)}: one glyph per character, codes drawn as markers. */
    public static int drawnWidth(String line, int from, int to) {
        int start = Mth.clamp(from, 0, line.length());
        int end = Mth.clamp(to, start, line.length());
        if (start == end) {
            return 0;
        }
        return Theme.font().width(line.substring(start, end).replace(FormatCodes.MARK, MARK_GLYPH));
    }
}
package top.yourzi.dialog.editor.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/**
 * 多行文本编辑框，支持段落符号（§）格式代码显示。融合自 visual_mod_edit_vndialog。
 * 适配 NeoForge 1.21.1 API（mouseScrolled 四参数签名、renderWidget 等）。
 */
public class MultiLineEditBox extends AbstractWidget {
    private final Font font;
    private String value = "";
    private int cursorLine = 0;
    private int cursorColumn = 0;
    private int scrollLine = 0;
    private boolean focused = false;
    private Consumer<String> responder;
    private static final int LINE_HEIGHT = 9;
    private static final int PADDING = 2;
    private static final int TEXT_COLOR = 0xE0E0E0;
    private static final int FORMAT_COLOR = -5592406;
    private static final int SECTION_WIDTH = 5;
    private static final int SECTION_HEIGHT = 7;

    public MultiLineEditBox(Font font, int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.font = font;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String val) {
        this.value = val != null ? val : "";
        this.cursorLine = 0;
        this.cursorColumn = 0;
        this.scrollLine = 0;
        onValueChanged();
    }

    public void setValueSilently(String val) {
        this.value = val != null ? val : "";
        this.cursorLine = 0;
        this.cursorColumn = 0;
        this.scrollLine = 0;
    }

    public void setResponder(Consumer<String> resp) {
        this.responder = resp;
    }

    private void onValueChanged() {
        if (responder != null) {
            responder.accept(value);
        }
    }

    public int getCursorPos() {
        int pos = 0;
        String[] lines = value.split("\n", -1);
        for (int i = 0; i < cursorLine && i < lines.length; i++) {
            pos += lines[i].length() + 1;
        }
        if (cursorLine < lines.length) {
            pos += Math.min(cursorColumn, lines[cursorLine].length());
        }
        return pos;
    }

    public void setCursorPos(int pos) {
        String[] lines = value.split("\n", -1);
        int acc = 0;
        for (int i = 0; i < lines.length; i++) {
            int len = lines[i].length() + 1;
            if (pos <= acc + len) {
                cursorLine = i;
                cursorColumn = Math.max(0, pos - acc);
                if (cursorColumn > lines[i].length()) {
                    cursorColumn = lines[i].length();
                }
                return;
            }
            acc += len;
        }
        cursorLine = lines.length - 1;
        if (lines.length > 0) {
            cursorColumn = lines[lines.length - 1].length();
        }
    }

    public void insertAtCursor(String text) {
        String[] lines = value.split("\n", -1);
        if (cursorLine >= lines.length) {
            return;
        }
        String cur = lines[cursorLine];
        String newLine = cur.substring(0, cursorColumn) + text + cur.substring(cursorColumn);
        lines[cursorLine] = newLine;
        value = String.join("\n", lines);
        cursorColumn += text.length();
        onValueChanged();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (!isMouseOver(mx, my)) {
            setFocused(false);
            return false;
        }
        setFocused(true);
        int relY = (int) my - getY() - PADDING + scrollLine * LINE_HEIGHT;
        int line = relY / LINE_HEIGHT;
        int relX = (int) mx - getX() - PADDING;
        String[] lines = value.split("\n", -1);
        if (line < 0) line = 0;
        if (line >= lines.length) line = lines.length - 1;
        if (line < 0) return true;
        String lineText = lines[line];
        cursorLine = line;
        cursorColumn = getColumnAtX(lineText, relX);
        return true;
    }

    private int getColumnAtX(String line, int targetX) {
        int x = 0;
        int col;
        for (col = 0; col < line.length(); ) {
            int w = getCharWidth(line, col);
            if (x + w > targetX) break;
            x += w;
            if (line.charAt(col) == '\u00a7' && col + 1 < line.length()) {
                col += 2;
            } else {
                col++;
            }
        }
        return col;
    }

    private int getCharWidth(String line, int pos) {
        if (pos >= line.length()) return 0;
        char c = line.charAt(pos);
        if (c == '\u00a7' && pos + 1 < line.length()) {
            return SECTION_WIDTH + font.width(String.valueOf(line.charAt(pos + 1)));
        }
        return font.width(String.valueOf(c));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isMouseOver(mouseX, mouseY)) {
            int maxVis = (getHeight() - 4) / LINE_HEIGHT;
            int maxScroll = Math.max(0, value.split("\n", -1).length - maxVis);
            scrollLine = Mth.clamp(scrollLine - (int) scrollY, 0, maxScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!focused) return false;
        String[] lines = value.split("\n", -1);
        String curLine = cursorLine < lines.length ? lines[cursorLine] : "";
        boolean mod = false;
        switch (key) {
            case 32: // SPACE
                return charTyped(' ', mods);
            case 259: // BACKSPACE
                if (cursorColumn > 0) {
                    lines[cursorLine] = curLine.substring(0, cursorColumn - 1) + curLine.substring(cursorColumn);
                    cursorColumn--;
                    mod = true;
                } else if (cursorLine > 0) {
                    String prev = lines[cursorLine - 1];
                    int prevLen = prev.length();
                    lines[cursorLine - 1] = prev + curLine;
                    String[] n = new String[lines.length - 1];
                    System.arraycopy(lines, 0, n, 0, cursorLine);
                    System.arraycopy(lines, cursorLine + 1, n, cursorLine, lines.length - cursorLine - 1);
                    lines = n;
                    cursorLine--;
                    cursorColumn = prevLen;
                    mod = true;
                }
                break;
            case 261: // DELETE
                if (cursorColumn < curLine.length()) {
                    lines[cursorLine] = curLine.substring(0, cursorColumn) + curLine.substring(cursorColumn + 1);
                    mod = true;
                } else if (cursorLine < lines.length - 1) {
                    String next = lines[cursorLine + 1];
                    lines[cursorLine] = curLine + next;
                    String[] n = new String[lines.length - 1];
                    System.arraycopy(lines, 0, n, 0, cursorLine + 1);
                    System.arraycopy(lines, cursorLine + 2, n, cursorLine + 1, lines.length - cursorLine - 2);
                    lines = n;
                    mod = true;
                }
                break;
            case 257: // ENTER
            case 335: // ENTER (numpad)
                String rest = curLine.substring(cursorColumn);
                lines[cursorLine] = curLine.substring(0, cursorColumn);
                String[] n = new String[lines.length + 1];
                System.arraycopy(lines, 0, n, 0, cursorLine + 1);
                n[cursorLine + 1] = rest;
                System.arraycopy(lines, cursorLine + 1, n, cursorLine + 2, lines.length - cursorLine - 1);
                lines = n;
                cursorLine++;
                cursorColumn = 0;
                mod = true;
                break;
            case 263: // LEFT
                if (cursorColumn > 0) {
                    cursorColumn--;
                } else if (cursorLine > 0) {
                    cursorLine--;
                    cursorColumn = lines[cursorLine].length();
                }
                break;
            case 262: // RIGHT
                if (cursorColumn < curLine.length()) {
                    cursorColumn++;
                } else if (cursorLine < lines.length - 1) {
                    cursorLine++;
                    cursorColumn = 0;
                }
                break;
            case 265: // UP
                if (cursorLine > 0) {
                    cursorLine--;
                    cursorColumn = Math.min(cursorColumn, lines[cursorLine].length());
                }
                break;
            case 264: // DOWN
                if (cursorLine < lines.length - 1) {
                    cursorLine++;
                    cursorColumn = Math.min(cursorColumn, lines[cursorLine].length());
                }
                break;
            case 268: // HOME
                cursorColumn = 0;
                break;
            case 269: // END
                cursorColumn = curLine.length();
                break;
        }
        if (mod) {
            value = String.join("\n", lines);
            onValueChanged();
        }
        return mod;
    }

    @Override
    public boolean charTyped(char cp, int mods) {
        if (!focused || cp < ' ' || cp == '\u007f') {
            return false;
        }
        insertAtCursor(String.valueOf(cp));
        return true;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        g.fill(getX() - 1, getY() - 1, getX() + getWidth() + 1, getY() + getHeight() + 1, focused ? -1 : FORMAT_COLOR);
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), -872415232);
        g.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        String[] lines = value.split("\n", -1);
        int maxVis = (getHeight() - 4) / LINE_HEIGHT;
        if (cursorLine < scrollLine) scrollLine = cursorLine;
        if (cursorLine >= scrollLine + maxVis) scrollLine = cursorLine - maxVis + 1;
        if (scrollLine < 0) scrollLine = 0;
        int y = getY() + PADDING - scrollLine * LINE_HEIGHT;
        for (int i = 0; i < lines.length; i++) {
            if (i < scrollLine || i >= scrollLine + maxVis) continue;
            String line = lines[i];
            int x = getX() + PADDING;
            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                if (c == '\u00a7' && j + 1 < line.length()) {
                    char code = line.charAt(j + 1);
                    drawSectionSign(g, x, y);
                    g.drawString(font, String.valueOf(code), x += SECTION_WIDTH, y, FORMAT_COLOR, false);
                    x += font.width(String.valueOf(code));
                    j++;
                } else {
                    g.drawString(font, String.valueOf(c), x, y, TEXT_COLOR, false);
                    x += font.width(String.valueOf(c));
                }
            }
            y += LINE_HEIGHT;
        }
        if (focused) {
            String curLine = lines.length > cursorLine ? lines[cursorLine] : "";
            int cursorX = getX() + PADDING;
            for (int j = 0; j < cursorColumn && j < curLine.length(); j++) {
                if (curLine.charAt(j) == '\u00a7' && j + 1 < curLine.length()) {
                    cursorX += SECTION_WIDTH + font.width(String.valueOf(curLine.charAt(j + 1)));
                    j++;
                } else {
                    cursorX += font.width(String.valueOf(curLine.charAt(j)));
                }
            }
            int cursorY = getY() + PADDING + (cursorLine - scrollLine) * LINE_HEIGHT;
            g.fill(cursorX, cursorY - 1, cursorX + 1, cursorY + LINE_HEIGHT - 1, -1);
        }
        g.disableScissor();
    }

    private void drawSectionSign(GuiGraphics g, int x, int y) {
        g.fill(x, y + 1, x + 1, y + SECTION_HEIGHT - 1, FORMAT_COLOR);
        g.fill(x + SECTION_WIDTH - 1, y + 1, x + SECTION_WIDTH, y + SECTION_HEIGHT - 1, FORMAT_COLOR);
        g.fill(x + 1, y + 1, x + 3, y + 3, FORMAT_COLOR);
        g.fill(x + 2, y + SECTION_HEIGHT - 3, x + 4, y + SECTION_HEIGHT - 1, FORMAT_COLOR);
    }

    @Override
    public void setFocused(boolean f) {
        this.focused = f;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput n) {
        n.add(NarratedElementType.TITLE, Component.literal(value));
    }
}

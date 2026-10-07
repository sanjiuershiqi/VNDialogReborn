package top.yourzi.dialog.editor.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Single-line text field: caret, selection, horizontal scroll and clipboard keys.
 *
 * <p>{@link #onChange} fires only when the text itself changes, never on caret moves, so callers can
 * write every edit straight through to the model.
 */
public class TextBox extends UiNode {
    private final EditableText text;
    private Component placeholder;
    private Consumer<String> onChange;
    private Runnable onSubmit;
    private String lastValue;
    private boolean dragSelecting;

    public TextBox(String value) {
        this.text = new EditableText(false, value);
        this.lastValue = this.text.value();
    }

    /** Runs when Enter is pressed, e.g. to confirm a prompt. */
    public TextBox onSubmit(Runnable onSubmit) {
        this.onSubmit = onSubmit;
        return this;
    }

    public static TextBox of(String value) {
        return new TextBox(value);
    }

    public TextBox placeholder(Component placeholder) {
        this.placeholder = placeholder;
        return this;
    }


    public TextBox onChange(Consumer<String> onChange) {
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


    /** Notifies listeners only when the text itself changed, not on caret moves. */
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

    @Override
    public int measureHeight(int width) {
        return Theme.ROW;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.host().focus(this);
        this.host().claimPointer(this);
        this.dragSelecting = true;
        this.placeCaretAt(mouseX, hasShiftDown());
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragSelecting) {
            this.placeCaretAt(mouseX, true);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragSelecting = false;
        return true;
    }

    private static boolean hasShiftDown() {
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }

    private void placeCaretAt(double mouseX, boolean extend) {
        int innerX = this.x() + Theme.PAD - this.text.scrollX();
        int column = EditableText.columnAt(this.text.lines().get(0), (int) mouseX - innerX);
        this.text.placeCaret(0, column, extend);
        this.changed();
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
                    setClipboard(this.text.selectedText());
                    return true;
                }
                case GLFW.GLFW_KEY_X -> {
                    if (this.text.hasSelection()) {
                        setClipboard(this.text.selectedText());
                        this.text.backspace();
                        this.changed();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    String clipboard = getClipboard();
                    if (!clipboard.isEmpty()) {
                        this.text.insert(clipboard.replace('\n', ' '), modifiers);
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
            if (this.onSubmit != null) {
                this.onSubmit.run();
            }
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

    static String getClipboard() {
        return Minecraft.getInstance().keyboardHandler.getClipboard();
    }

    static void setClipboard(String value) {
        Minecraft.getInstance().keyboardHandler.setClipboard(value == null ? "" : value);
    }

    @Override
    protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean focused = this.focused();
        graphics.fill(this.x(), this.y(), this.right(), this.bottom(), Theme.FIELD);
        Theme.border(graphics, this.x(), this.y(), this.width(), this.height(), focused ? Theme.ACCENT : Theme.BORDER);

        int innerX = this.x() + Theme.PAD;
        int innerWidth = this.width() - Theme.PAD * 2;
        int textY = this.y() + (this.height() - 8) / 2;
        String line = this.text.lines().get(0);
        this.ensureCaretVisible();
        int scroll = this.text.scrollX();

        if (line.isEmpty() && !focused && this.placeholder != null) {
            Theme.textIn(graphics, this.placeholder.getString(), innerX, this.y(), innerWidth, this.height(), Theme.TEXT_MUTED);
        } else {
            Theme.clip(graphics, innerX, this.y() + 1, innerX + innerWidth, this.bottom() - 1);
            this.drawSelection(graphics, line, innerX, textY, scroll);
            if (!line.isEmpty()) {
                Theme.text(graphics, EditableText.styled(line), innerX - scroll, textY, Theme.TEXT);
            }
            if (focused) {
                int caretX = innerX + EditableText.widthTo(line, this.text.cursorColumn()) - scroll;
                graphics.fill(caretX, this.y() + 3, caretX + 1, this.bottom() - 3, Theme.TEXT);
            }
            Theme.unclip(graphics);
        }
    }

    private void drawSelection(GuiGraphics graphics, String line, int innerX, int textY, int scroll) {
        if (!this.text.hasSelection()) {
            return;
        }
        int[] bounds = this.selectionBounds(line);
        int left = innerX + bounds[0] - scroll;
        int right = innerX + bounds[1] - scroll;
        if (right > left) {
            graphics.fill(left, textY - 1, right, textY + 9, Theme.withAlpha(Theme.CYAN, 0x66));
        }
    }

    private int[] selectionBounds(String line) {
        String selection = this.text.selectedText().replace('\n', ' ');
        int caret = EditableText.widthTo(line, this.text.cursorColumn());
        return new int[]{Math.max(0, caret - Theme.font().width(selection)), caret};
    }

    /**
     * Keeps the caret inside the visible width without ever scrolling past the text: a line that
     * fits always starts at the left edge, and an unfocused field shows its beginning.
     */
    private void ensureCaretVisible() {
        String line = this.text.lines().get(0);
        int innerWidth = Math.max(1, this.width() - Theme.PAD * 2);
        int total = EditableText.widthTo(line, line.length());
        int max = Math.max(0, total - innerWidth + 2);
        if (!this.focused()) {
            this.text.setScroll(0, 0);
            return;
        }
        int caret = EditableText.widthTo(line, this.text.cursorColumn());
        int scroll = Mth.clamp(this.text.scrollX(), 0, max);
        if (caret - scroll > innerWidth - 2) {
            scroll = caret - innerWidth + 2;
        } else if (caret < scroll) {
            scroll = caret;
        }
        this.text.setScroll(0, Mth.clamp(scroll, 0, max));
    }
}

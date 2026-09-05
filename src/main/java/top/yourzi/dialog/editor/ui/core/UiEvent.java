package top.yourzi.dialog.editor.ui.core;

/** Input event passed through capture, target and bubble phases. */
public final class UiEvent {
    public enum Type { POINTER_DOWN, POINTER_UP, POINTER_MOVE, SCROLL, KEY_DOWN, CHAR_TYPED }

    private final Type type;
    private final double mouseX;
    private final double mouseY;
    private final int button;
    private final double scrollX;
    private final double scrollY;
    private final int keyCode;
    private final int modifiers;
    private final char character;
    private boolean consumed;
    private boolean captureRequested;

    private UiEvent(Type type, double mouseX, double mouseY, int button, double scrollX, double scrollY,
                    int keyCode, int modifiers, char character) {
        this.type = type;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.button = button;
        this.scrollX = scrollX;
        this.scrollY = scrollY;
        this.keyCode = keyCode;
        this.modifiers = modifiers;
        this.character = character;
    }

    public static UiEvent pointer(Type type, double x, double y, int button) {
        return new UiEvent(type, x, y, button, 0, 0, 0, 0, '\0');
    }

    public static UiEvent scroll(double x, double y, double scrollX, double scrollY) {
        return new UiEvent(Type.SCROLL, x, y, 0, scrollX, scrollY, 0, 0, '\0');
    }

    public static UiEvent key(Type type, int keyCode, int modifiers) {
        return new UiEvent(type, 0, 0, 0, 0, 0, keyCode, modifiers, '\0');
    }

    public static UiEvent character(char character, int modifiers) {
        return new UiEvent(Type.CHAR_TYPED, 0, 0, 0, 0, 0, 0, modifiers, character);
    }

    public Type type() { return this.type; }
    public double mouseX() { return this.mouseX; }
    public double mouseY() { return this.mouseY; }
    public int button() { return this.button; }
    public double scrollX() { return this.scrollX; }
    public double scrollY() { return this.scrollY; }
    public int keyCode() { return this.keyCode; }
    public int modifiers() { return this.modifiers; }
    public char character() { return this.character; }
    public boolean consumed() { return this.consumed; }
    public void consume() { this.consumed = true; }
    public boolean captureRequested() { return this.captureRequested; }
    public void requestCapture() { this.captureRequested = true; }
}

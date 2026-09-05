package top.yourzi.dialog.editor.ui.core;

/** Shared visual tokens for retained nodes; renderers may map these to Minecraft or Skija. */
public record UiStyle(int background, int foreground, int border, int accent,
                      int padding, int gap, int borderWidth) {
    public static UiStyle defaults() {
        return new UiStyle(0xFF171D1E, 0xFFE7ECE8, 0xFF3B484A, 0xFFD3B936, 6, 4, 1);
    }

    public UiStyle withBackground(int value) {
        return new UiStyle(value, this.foreground, this.border, this.accent, this.padding, this.gap, this.borderWidth);
    }

    public UiStyle withAccent(int value) {
        return new UiStyle(this.background, this.foreground, this.border, value, this.padding, this.gap, this.borderWidth);
    }
}

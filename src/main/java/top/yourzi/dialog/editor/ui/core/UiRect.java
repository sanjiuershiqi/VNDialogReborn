package top.yourzi.dialog.editor.ui.core;

/** Immutable integer rectangle used by every retained editor node. */
public record UiRect(int x, int y, int width, int height) {
    public UiRect {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    public int right() {
        return this.x + this.width;
    }

    public int bottom() {
        return this.y + this.height;
    }

    public boolean contains(double px, double py) {
        return px >= this.x && px < this.right() && py >= this.y && py < this.bottom();
    }

    public UiRect inset(int amount) {
        int a = Math.max(0, amount);
        return new UiRect(this.x + a, this.y + a,
                Math.max(0, this.width - a * 2), Math.max(0, this.height - a * 2));
    }
}

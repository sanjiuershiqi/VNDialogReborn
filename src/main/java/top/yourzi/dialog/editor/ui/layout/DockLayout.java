package top.yourzi.dialog.editor.ui.layout;

import top.yourzi.dialog.editor.ui.core.UiRect;

/**
 * Deterministic three-column layout for the editor workspace.
 * The center column is protected by a minimum width before the inspector grows.
 */
public final class DockLayout {
    private final int sidebarMin;
    private final int sidebarMax;
    private final int inspectorMin;
    private final int inspectorMax;
    private final int centerMin;

    public DockLayout() {
        this(180, 250, 260, 360, 320);
    }

    public DockLayout(int sidebarMin, int sidebarMax, int inspectorMin, int inspectorMax, int centerMin) {
        this.sidebarMin = Math.max(0, sidebarMin);
        this.sidebarMax = Math.max(this.sidebarMin, sidebarMax);
        this.inspectorMin = Math.max(0, inspectorMin);
        this.inspectorMax = Math.max(this.inspectorMin, inspectorMax);
        this.centerMin = Math.max(1, centerMin);
    }

    public Layout calculate(int x, int y, int width, int height, boolean inspectorVisible) {
        int safeWidth = Math.max(0, width);
        int sidebar = clamp(safeWidth * 22 / 100, this.sidebarMin, this.sidebarMax);
        int inspector = inspectorVisible ? clamp(safeWidth * 28 / 100, this.inspectorMin, this.inspectorMax) : 0;
        int available = Math.max(0, safeWidth - sidebar);
        if (inspectorVisible && available - inspector < this.centerMin) {
            inspector = Math.max(0, available - this.centerMin);
        }
        if (inspectorVisible && inspector < this.inspectorMin && available >= this.inspectorMin + this.centerMin) {
            inspector = this.inspectorMin;
        }
        int center = Math.max(0, safeWidth - sidebar - inspector);
        UiRect sidebarRect = new UiRect(x, y, sidebar, height);
        UiRect centerRect = new UiRect(x + sidebar, y, center, height);
        UiRect inspectorRect = new UiRect(x + sidebar + center, y, inspector, height);
        return new Layout(sidebarRect, centerRect, inspectorRect);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Layout(UiRect sidebar, UiRect center, UiRect inspector) {
        public boolean hasInspector() { return inspector.width() > 0; }
    }
}

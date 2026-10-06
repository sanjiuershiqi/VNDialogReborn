package top.yourzi.dialog.editor.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Anti-aliasing-free but arbitrary-angle line drawing for the GUI: thin quads in the GUI render
 * type, so lines follow the current pose (pan and zoom) like any other GUI element.
 *
 * <p>Callers batch any number of segments and then call {@link #flush}; that keeps a graph with
 * hundreds of curves to one draw call.
 */
public final class Lines {
    private Lines() {
    }

    public static void segment(GuiGraphics graphics, float x1, float y1, float x2, float y2, float width, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001f) {
            return;
        }
        float nx = -dy / length * width / 2.0f;
        float ny = dx / length * width / 2.0f;
        quad(graphics, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x2 - nx, y2 - ny, x2 + nx, y2 + ny, color);
    }

    /** Filled triangle, e.g. an arrow head. */
    public static void triangle(GuiGraphics graphics, float ax, float ay, float bx, float by, float cx, float cy, int color) {
        quad(graphics, ax, ay, bx, by, cx, cy, cx, cy, color);
    }

    /**
     * Horizontal-tangent cubic curve from {@code (x1,y1)} to {@code (x2,y2)}, the shape node editors
     * use for links. {@code dashed} skips every other step.
     */
    public static void curve(GuiGraphics graphics, float x1, float y1, float x2, float y2, float bend, float width,
                             int color, boolean dashed) {
        float c1x = x1 + bend;
        float c2x = x2 - bend;
        float approx = Math.abs(x2 - x1) + Math.abs(y2 - y1) + bend;
        int steps = Math.max(12, Math.min(96, (int) (approx / 12.0f)));
        float px = x1;
        float py = y1;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float u = 1.0f - t;
            float x = u * u * u * x1 + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * x2;
            float y = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;
            if (!dashed || i % 2 == 0) {
                segment(graphics, px, py, x, y, width, color);
            }
            px = x;
            py = y;
        }
    }

    /** Emits a quad in both windings so it shows regardless of face culling. */
    private static void quad(GuiGraphics graphics, float ax, float ay, float bx, float by, float cx, float cy,
                             float dx, float dy, int color) {
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        consumer.addVertex(pose, ax, ay, 0.0f).setColor(color);
        consumer.addVertex(pose, bx, by, 0.0f).setColor(color);
        consumer.addVertex(pose, cx, cy, 0.0f).setColor(color);
        consumer.addVertex(pose, dx, dy, 0.0f).setColor(color);
        consumer.addVertex(pose, dx, dy, 0.0f).setColor(color);
        consumer.addVertex(pose, cx, cy, 0.0f).setColor(color);
        consumer.addVertex(pose, bx, by, 0.0f).setColor(color);
        consumer.addVertex(pose, ax, ay, 0.0f).setColor(color);
    }

    public static void flush(GuiGraphics graphics) {
        graphics.flush();
    }
}

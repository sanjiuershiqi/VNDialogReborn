package top.yourzi.dialog.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.editor.ui.Theme;

import java.nio.file.Path;

/**
 * Resolves and draws editor preview textures.
 *
 * <p>Paths in a dialogue refer either to a file the writer dropped into the editor config directory
 * or to a texture shipped inside the mod. Both are presented through one {@link Handle} so every
 * preview in the editor draws with the same call, and missing assets degrade to a placeholder frame
 * instead of a crash.
 */
public final class AssetService {
    private AssetService() {
    }

    /** {@code location} is null when the asset could not be resolved. */
    public record Handle(ResourceLocation location, int width, int height) {
        public boolean present() {
            return this.location != null;
        }

        /** Aspect ratio of the source image, falling back to a square for built-in placeholders. */
        public float aspect() {
            return this.height <= 0 ? 1.0f : (float) this.width / (float) this.height;
        }
    }

    public static final Handle MISSING = new Handle(null, 0, 0);

    public static Handle portrait(String path) {
        return resolve(path, EditorConfig.PORTRAITS_DIR, "textures/portraits/");
    }

    public static Handle background(String path) {
        return resolve(path, EditorConfig.BACKGROUNDS_DIR, "textures/backgrounds/");
    }

    private static Handle resolve(String path, Path directory, String builtinPrefix) {
        if (path == null || path.isBlank()) {
            return MISSING;
        }
        Path file = EditorConfig.resolveInside(directory, path);
        if (file != null && file.toFile().isFile()) {
            TextureCacheService.CachedTexture cached = TextureCacheService.load(file.toFile());
            if (cached != null) {
                return new Handle(cached.location(), cached.width(), cached.height());
            }
        }
        ResourceLocation builtin = builtin(builtinPrefix + path);
        if (builtin != null) {
            return new Handle(builtin, 256, 256);
        }
        return MISSING;
    }

    /** Built-in texture lookup; invalid paths (for example CJK file names) simply do not resolve. */
    public static ResourceLocation builtin(String resourcePath) {
        try {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, resourcePath);
            if (Minecraft.getInstance().getResourceManager().getResource(location).isPresent()) {
                return location;
            }
        } catch (RuntimeException ignored) {
            // A path that is not a legal resource location cannot name a built-in texture.
        }
        return null;
    }

    /** Every texture shipped under the given resource folder, used by the built-in browsers. */
    public static java.util.List<String> listBuiltin(String prefix) {
        String folder = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        java.util.List<String> names = new java.util.ArrayList<>();
        for (ResourceLocation location : Minecraft.getInstance().getResourceManager()
                .listResources(folder, id -> id.getPath().endsWith(".png")).keySet()) {
            names.add(location.getPath().substring(prefix.length()));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /**
     * Draws a texture stretched into the given box, replicating the runtime presentation so a preview
     * matches what the player will see.
     */
    public static void blitStretched(GuiGraphics graphics, Handle handle, int x, int y, int width, int height) {
        if (handle == null || !handle.present() || width <= 0 || height <= 0) {
            return;
        }
        graphics.blit(handle.location(), x, y, width, height, 0.0f, 0.0f, width, height, width, height);
    }

    /** Draws a texture with its own aspect ratio fitted inside the box and centered. */
    public static void blitFitted(GuiGraphics graphics, Handle handle, int x, int y, int width, int height) {
        if (handle == null || !handle.present() || width <= 0 || height <= 0) {
            return;
        }
        float scale = Math.min((float) width / Math.max(1, handle.width()), (float) height / Math.max(1, handle.height()));
        int drawWidth = Math.max(1, (int) (handle.width() * scale));
        int drawHeight = Math.max(1, (int) (handle.height() * scale));
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        graphics.blit(handle.location(), drawX, drawY, drawWidth, drawHeight, 0.0f, 0.0f, drawWidth, drawHeight,
                drawWidth, drawHeight);
    }

    /** Placeholder drawn when an asset is missing: frame plus the unresolved path. */
    public static void placeholder(GuiGraphics graphics, String path, int x, int y, int width, int height,
                                   int fill, int border) {
        graphics.fill(x, y, x + width, y + height, fill);
        Theme.border(graphics, x, y, width, height, border);
        String label = path == null || path.isBlank() ? Theme.tr("stage.no_asset").getString() : path;
        Theme.centered(graphics, Theme.ellipsize(label, width - 8), x + width / 2, y + height / 2 - 4, Theme.TEXT_MUTED);
    }

    /** Releases every cached file texture; called when the editor closes. */
    public static void releaseAll() {
        TextureCacheService.releaseAll();
    }
}

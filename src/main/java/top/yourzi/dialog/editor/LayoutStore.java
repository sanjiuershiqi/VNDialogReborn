package top.yourzi.dialog.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import top.yourzi.dialog.Dialog;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Graph positions the writer arranged by hand, kept beside - never inside - the dialogue files.
 *
 * <p>One small JSON file per dialogue under {@code config/vndialog_editor/layout/}, plus
 * {@link #FILES_KEY} for the all-files overview. Losing these files only loses the arrangement; the
 * graph falls back to its automatic layout.
 */
public final class LayoutStore {
    public static final String FILES_KEY = "_files";
    private static final Path DIRECTORY = EditorConfig.CONFIG_ROOT.resolve("layout");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, int[]>>() {
    }.getType();

    private LayoutStore() {
    }

    private static Path path(String key) {
        if (!FILES_KEY.equals(key) && !EditorStore.isSafeId(key)) {
            return null;
        }
        return DIRECTORY.resolve(key + ".json");
    }

    public static Map<String, int[]> load(String key) {
        Path path = key == null ? null : path(key);
        if (path == null || !Files.isRegularFile(path)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, int[]> positions = GSON.fromJson(Files.readString(path), TYPE);
            return positions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(positions);
        } catch (Exception e) {
            Dialog.LOGGER.warn("Ignoring unreadable graph layout {}", path);
            return new LinkedHashMap<>();
        }
    }

    public static void save(String key, Map<String, int[]> positions) {
        Path path = key == null ? null : path(key);
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(DIRECTORY);
            if (positions.isEmpty()) {
                Files.deleteIfExists(path);
            } else {
                Files.writeString(path, GSON.toJson(positions, TYPE));
            }
        } catch (IOException e) {
            Dialog.LOGGER.warn("Failed to save graph layout {}", path, e);
        }
    }

    /** Follows a dialogue file rename so its arrangement is kept. */
    public static void rename(String oldKey, String newKey) {
        Map<String, int[]> positions = load(oldKey);
        if (!positions.isEmpty()) {
            save(newKey, positions);
            save(oldKey, new LinkedHashMap<>());
        }
    }
}

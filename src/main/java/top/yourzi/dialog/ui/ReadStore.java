package top.yourzi.dialog.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;
import top.yourzi.dialog.Dialog;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Which lines this player has already read, per dialogue.
 *
 * <p>Kept on the client in {@code config/vndialog_read.json} so it survives restarts and works on any
 * server. It drives the things a visual novel reader expects: read text in a different colour,
 * choices that were already explored marked as such, and skipping that stops at new text.
 */
public final class ReadStore {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("vndialog_read.json");
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type TYPE = new TypeToken<HashMap<String, TreeSet<String>>>() {
    }.getType();
    private static Map<String, TreeSet<String>> read;
    private static boolean dirty;

    private ReadStore() {
    }

    private static Map<String, TreeSet<String>> data() {
        if (read == null) {
            read = new HashMap<>();
            if (Files.isRegularFile(FILE)) {
                try {
                    Map<String, TreeSet<String>> loaded = GSON.fromJson(Files.readString(FILE), TYPE);
                    if (loaded != null) {
                        read.putAll(loaded);
                    }
                } catch (Exception e) {
                    Dialog.LOGGER.warn("Ignoring unreadable read-history file {}", FILE);
                }
            }
        }
        return read;
    }

    public static synchronized boolean isRead(String dialogId, String entryId) {
        if (dialogId == null || entryId == null) {
            return false;
        }
        TreeSet<String> entries = data().get(dialogId);
        return entries != null && entries.contains(entryId);
    }

    public static synchronized void markRead(String dialogId, String entryId) {
        if (dialogId == null || entryId == null) {
            return;
        }
        if (data().computeIfAbsent(dialogId, key -> new TreeSet<>()).add(entryId)) {
            dirty = true;
        }
    }

    /** Writes pending changes; cheap to call often. */
    public static synchronized void save() {
        if (!dirty || read == null) {
            return;
        }
        dirty = false;
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(read, TYPE));
        } catch (Exception e) {
            Dialog.LOGGER.warn("Failed to save read history", e);
        }
    }
}

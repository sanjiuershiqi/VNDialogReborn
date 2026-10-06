package top.yourzi.dialog.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;
import top.yourzi.dialog.model.DialogSequence;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * All file access for the editor's dialogue directory.
 *
 * <p>Every path is derived from a validated document id and confined to the editor directory, writes
 * go through a temporary file plus an atomic replace so a crash cannot truncate a script, and
 * rename moves the file before the in-memory id changes, so a failure never leaves half a state.
 */
public final class EditorStore {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_\\-]+(?:\\.[A-Za-z0-9_\\-]+)*");
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private final Path directory;

    public EditorStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public static boolean isSafeId(String id) {
        return id != null && !id.isBlank() && SAFE_ID.matcher(id).matches();
    }

    public Path pathForId(String id) throws IOException {
        if (!isSafeId(id)) {
            throw new IOException("Unsafe dialogue id: " + id);
        }
        return this.confine(this.directory.resolve(id + ".json"));
    }

    public DialogSequence read(String id) throws IOException {
        Path path = this.pathForId(id);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Dialogue file not found: " + path);
        }
        DialogSequence sequence = DialogManager.GSON.fromJson(Files.readString(path), DialogSequence.class);
        if (sequence == null || !isSafeId(sequence.getId())) {
            throw new IOException("Dialogue file has no usable id: " + path);
        }
        return sequence;
    }

    /** Ids of every readable dialogue file, sorted for stable tab order. */
    public List<String> listIds() {
        List<String> ids = new ArrayList<>();
        if (!Files.isDirectory(this.directory)) {
            return ids;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.directory, "*.json")) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                String id = name.substring(0, name.length() - ".json".length());
                if (isSafeId(id)) {
                    ids.add(id);
                }
            }
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to list dialogue files", e);
        }
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    public void write(DialogSequence sequence) throws IOException {
        Path path = this.pathForId(sequence.getId());
        Files.createDirectories(this.directory);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, PRETTY.toJson(sequence));
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    /** Moves the file for {@code oldId} to {@code newId}; returns false when there was no file yet. */
    public boolean moveFile(String oldId, String newId) throws IOException {
        Path target = this.pathForId(newId);
        if (Files.exists(target)) {
            throw new IOException("A dialogue file already exists: " + newId);
        }
        if (!isSafeId(oldId)) {
            return false;
        }
        Path source = this.pathForId(oldId);
        if (!Files.isRegularFile(source)) {
            return false;
        }
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
        return true;
    }

    public boolean delete(String id) throws IOException {
        return Files.deleteIfExists(this.pathForId(id));
    }

    private Path confine(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(this.directory)) {
            throw new IOException("Path escapes the dialogue directory: " + path);
        }
        return normalized;
    }

    /** Session state: which files were open and which one was active. */
    public record Session(List<String> openIds, String activeId) {
    }

    public Session readSession() {
        if (!Files.isRegularFile(EditorConfig.SESSION_FILE)) {
            return new Session(List.of(), null);
        }
        try {
            Session session = DialogManager.GSON.fromJson(Files.readString(EditorConfig.SESSION_FILE), Session.class);
            return session == null || session.openIds() == null ? new Session(List.of(), null) : session;
        } catch (Exception e) {
            Dialog.LOGGER.error("Failed to read editor session", e);
            return new Session(List.of(), null);
        }
    }

    public void writeSession(List<String> openIds, String activeId) {
        try {
            Files.createDirectories(EditorConfig.CONFIG_ROOT);
            Files.writeString(EditorConfig.SESSION_FILE, PRETTY.toJson(new Session(openIds, activeId)));
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to persist editor session", e);
        }
    }
}

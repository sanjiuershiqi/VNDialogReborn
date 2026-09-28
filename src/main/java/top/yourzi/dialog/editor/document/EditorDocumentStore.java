package top.yourzi.dialog.editor.document;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import top.yourzi.dialog.model.DialogSequence;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class EditorDocumentStore {
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path directory;

    public EditorDocumentStore(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    }

    public Path directory() {
        return this.directory;
    }

    public DialogSequence load(Path requestedPath) throws IOException {
        Path path = this.resolveInputPath(requestedPath);
        if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".json")) {
            throw new IOException("Dialog file does not exist or is not a JSON file: " + path);
        }
        DialogSequence sequence = PRETTY_GSON.fromJson(Files.readString(path), DialogSequence.class);
        if (sequence == null || !isSafeDocumentId(sequence.getId())) {
            throw new IOException("Dialog file contains an invalid or missing ID: " + path);
        }
        return sequence;
    }

    public void save(DialogSequence sequence) throws IOException {
        Objects.requireNonNull(sequence, "sequence");
        Path path = this.pathForId(sequence.getId());
        Files.createDirectories(this.directory);
        Path temporaryPath = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.writeString(temporaryPath, PRETTY_GSON.toJson(sequence));
            try {
                Files.move(temporaryPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporaryPath);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    public boolean rename(DialogSequence sequence, String newId) throws IOException {
        Objects.requireNonNull(sequence, "sequence");
        String oldId = sequence.getId();
        if (Objects.equals(oldId, newId)) {
            return false;
        }
        Path newPath = this.pathForId(newId);
        if (Files.exists(newPath)) {
            throw new IOException("A dialog file already exists for ID: " + newId);
        }

        boolean moved = false;
        if (isSafeDocumentId(oldId)) {
            Path oldPath = this.pathForId(oldId);
            if (Files.exists(oldPath)) {
                try {
                    Files.move(oldPath, newPath, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(oldPath, newPath);
                }
                moved = true;
            }
        }
        sequence.setId(newId);
        return moved;
    }

    public boolean delete(DialogSequence sequence) throws IOException {
        Objects.requireNonNull(sequence, "sequence");
        return Files.deleteIfExists(this.pathForId(sequence.getId()));
    }

    public Path pathForId(String id) throws IOException {
        if (!isSafeDocumentId(id)) {
            throw new IOException("Unsafe dialog ID: " + id);
        }
        return this.resolveInside(this.directory.resolve(id + ".json"));
    }

    private Path resolveInputPath(Path requestedPath) throws IOException {
        if (requestedPath == null) {
            throw new IOException("Missing dialog path");
        }
        Path path = requestedPath.isAbsolute() ? requestedPath : this.directory.resolve(requestedPath);
        return this.resolveInside(path);
    }

    private Path resolveInside(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(this.directory)) {
            throw new IOException("Dialog path escapes the editor directory: " + path);
        }
        return normalized;
    }

    public static boolean isSafeDocumentId(String id) {
        return id != null && !id.isBlank() && !id.equals(".") && !id.equals("..")
                && id.matches("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*");
    }
}

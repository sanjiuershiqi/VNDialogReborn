package top.yourzi.dialog.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import top.yourzi.dialog.Dialog;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * How the dialogue behaves for this player, as chosen in the editor's settings sheet.
 *
 * <p>Kept in {@code config/vndialog_play.json} rather than in the mod config file so a pack author can
 * change it in game, see the effect immediately, and hand the default file on with the pack.
 */
public final class PlaySettings {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("vndialog_play.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PlaySettings instance;

    // Input
    public boolean numberKeys = true;
    public boolean spaceAdvance = true;
    public boolean wheelAdvance = true;
    public boolean wheelHistory = true;
    public boolean rightClickHide = true;
    public boolean hideKey = true;

    // Reading
    public boolean trackRead = true;
    public boolean dimReadText = true;
    public boolean dimReadChoices = true;
    public boolean skipUnreadText = true;
    /** Colour of text the player has already read, ARGB. */
    public int readTextColor = 0xFFB9C6DC;

    private PlaySettings() {
    }

    public static synchronized PlaySettings get() {
        if (instance == null) {
            instance = new PlaySettings();
            if (Files.isRegularFile(FILE)) {
                try {
                    PlaySettings loaded = GSON.fromJson(Files.readString(FILE), PlaySettings.class);
                    if (loaded != null) {
                        instance = loaded;
                    }
                } catch (Exception e) {
                    Dialog.LOGGER.warn("Ignoring unreadable dialogue settings file {}", FILE);
                }
            }
        }
        return instance;
    }

    public static synchronized void save() {
        if (instance == null) {
            return;
        }
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (Exception e) {
            Dialog.LOGGER.warn("Failed to save dialogue settings", e);
        }
    }
}

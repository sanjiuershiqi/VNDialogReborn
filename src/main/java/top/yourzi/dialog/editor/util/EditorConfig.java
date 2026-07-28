package top.yourzi.dialog.editor.util;

import net.neoforged.fml.loading.FMLPaths;
import top.yourzi.dialog.Dialog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 编辑器配置目录管理。融合自 visual_mod_edit_vndialog 的 ConfigUtil。
 */
public class EditorConfig {
    public static final Path CONFIG_ROOT = FMLPaths.CONFIGDIR.get().resolve("vndialog_editor");
    public static final Path DIALOG_JSON_DIR = CONFIG_ROOT.resolve("dialog_json");
    public static final Path PORTRAITS_DIR = CONFIG_ROOT.resolve("portraits");
    public static final Path BACKGROUNDS_DIR = CONFIG_ROOT.resolve("backgrounds");
    public static final Path SOUNDS_DIR = CONFIG_ROOT.resolve("sounds");
    public static final Path LANG_DIR = CONFIG_ROOT.resolve("lang");

    public static void createDirectories() {
        try {
            Files.createDirectories(DIALOG_JSON_DIR);
            Files.createDirectories(PORTRAITS_DIR);
            Files.createDirectories(BACKGROUNDS_DIR);
            Files.createDirectories(SOUNDS_DIR);
            Files.createDirectories(LANG_DIR);
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to create editor config directories", e);
        }
    }
}

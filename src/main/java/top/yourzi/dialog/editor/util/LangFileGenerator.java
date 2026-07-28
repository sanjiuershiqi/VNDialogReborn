package top.yourzi.dialog.editor.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import top.yourzi.dialog.Dialog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 语言文件生成器，将翻译键写入配置目录的语言文件。融合自 visual_mod_edit_vndialog。
 */
public class LangFileGenerator {
    private static final Gson GSON = new Gson();

    public static void saveTranslation(String key, String zhCn, String enUs) {
        updateLangFile("zh_cn.json", key, zhCn);
        updateLangFile("en_us.json", key, enUs);
    }

    private static void updateLangFile(String fileName, String key, String value) {
        Path filePath = EditorConfig.LANG_DIR.resolve(fileName);
        Map<String, String> map = loadLangFile(filePath);
        map.put(key, value);
        try {
            Files.createDirectories(EditorConfig.LANG_DIR);
            Files.writeString(filePath, GSON.toJson(map));
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to write lang file: {}", filePath, e);
        }
    }

    private static Map<String, String> loadLangFile(Path file) {
        try {
            if (Files.exists(file)) {
                String json = Files.readString(file);
                Map<String, String> map = GSON.fromJson(json, new TypeToken<Map<String, String>>(){}.getType());
                return map != null ? map : new HashMap<>();
            }
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to read lang file: {}", file, e);
        }
        return new HashMap<>();
    }
}

package top.yourzi.dialog.editor.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import top.yourzi.dialog.Dialog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置目录语言文件缓存。用于编辑器生成的翻译键。
 * 融合自 visual_mod_edit_vndialog，替代了原 MixinClientLanguage 的功能。
 */
public class ConfigLanguageCache {
    private static final Map<String, String> cache = new HashMap<>();
    private static long lastModified = 0L;
    private static String currentLang = "";
    private static final Gson GSON = new Gson();

    public static String get(String key) {
        try {
            if (Minecraft.getInstance().getLanguageManager() == null) {
                return null;
            }
            String lang = Minecraft.getInstance().getLanguageManager().getSelected();
            Path file = EditorConfig.LANG_DIR.resolve(lang + ".json");
            if (!Files.exists(file)) {
                return null;
            }
            long mod = Files.getLastModifiedTime(file).toMillis();
            if (!lang.equals(currentLang) || mod != lastModified) {
                cache.clear();
                String json = Files.readString(file);
                Map<String, String> map = GSON.fromJson(json, new TypeToken<Map<String, String>>(){}.getType());
                if (map != null) {
                    cache.putAll(map);
                }
                currentLang = lang;
                lastModified = mod;
            }
            return cache.get(key);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 尝试从配置缓存翻译键，找不到返回 null。
     * 在 DialogEntry 解析翻译组件时调用。
     */
    public static String translate(String key) {
        return cache.get(key);
    }
}

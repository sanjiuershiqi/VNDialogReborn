package top.yourzi.dialog.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

public final class ComponentJson {
    private ComponentJson() {
    }

    public static Component fromJson(JsonElement json) {
        try {
            Component component = Component.Serializer.fromJson(json, RegistryAccess.EMPTY);
            return component == null ? Component.empty() : component;
        } catch (Exception e) {
            // 解析失败时尝试修复常见的 color 格式问题（如 8 位 ARGB hex）后重试
            JsonElement sanitized = sanitizeColors(json);
            if (sanitized != null && !sanitized.equals(json)) {
                try {
                    Component component = Component.Serializer.fromJson(sanitized, RegistryAccess.EMPTY);
                    return component == null ? Component.empty() : component;
                } catch (Exception ignored) {
                    // 修复后仍失败，返回空组件避免崩溃
                }
            }
            return Component.empty();
        }
    }

    /**
     * 递归遍历 JSON，将非法的 8 位 ARGB hex color 值（#AARRGGBB）截断为
     * Minecraft 接受的 6 位 RGB hex（#RRGGBB，丢弃 alpha 通道）。
     * 返回修复后的副本；若无修改返回原对象。
     */
    private static JsonElement sanitizeColors(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return element;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject().deepCopy();
            if (obj.has("color") && obj.get("color").isJsonPrimitive()) {
                String color = obj.get("color").getAsString();
                String fixed = fixColorString(color);
                if (fixed != null) {
                    obj.addProperty("color", fixed);
                }
            }
            // 递归处理 extra 和子元素
            for (String key : obj.keySet()) {
                JsonElement child = obj.get(key);
                JsonElement fixed = sanitizeColors(child);
                if (fixed != child) {
                    obj.add(key, fixed);
                }
            }
            return obj;
        }
        if (element.isJsonArray()) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            boolean changed = false;
            for (JsonElement e : element.getAsJsonArray()) {
                JsonElement fixed = sanitizeColors(e);
                arr.add(fixed);
                if (fixed != e) {
                    changed = true;
                }
            }
            return changed ? arr : element;
        }
        return element;
    }

    /**
     * 修复 color 字符串：将 #AARRGGBB（8 位 ARGB）截断为 #RRGGBB（6 位 RGB）。
     * 仅处理 # 开头且长度为 9 的字符串，其余原样返回。
     * 返回 null 表示无需修改。
     */
    private static String fixColorString(String color) {
        if (color == null) {
            return null;
        }
        String trimmed = color.trim();
        if (trimmed.length() == 9 && trimmed.startsWith("#")) {
            // #AARRGGBB -> #RRGGBB（去掉前两位 alpha）
            return "#" + trimmed.substring(3);
        }
        return null;
    }

    public static JsonElement toJsonTree(Component component) {
        return JsonParser.parseString(Component.Serializer.toJson(component, RegistryAccess.EMPTY));
    }
}

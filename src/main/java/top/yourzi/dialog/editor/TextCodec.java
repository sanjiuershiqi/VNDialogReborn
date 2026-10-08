package top.yourzi.dialog.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import top.yourzi.dialog.editor.ui.FormatCodes;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.util.ComponentJson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridges between the two shapes dialogue text can take.
 *
 * <p>The model stores text as a JSON component tree, while the editor edits a flat string with legacy
 * {@code §} codes. Both directions live here so the inspector never has to know about components,
 * and translation keys resolve against the editor's own lang directory first so a writer sees the
 * text they actually authored.
 */
public final class TextCodec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TextCodec() {
    }

    /** Flattens a stored text element into editable text. */
    public static String toEditable(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsJsonPrimitive().isString() ? element.getAsString() : element.toString();
        }
        if (element.isJsonArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonElement part : element.getAsJsonArray()) {
                builder.append(toEditable(part));
            }
            return builder.toString();
        }
        if (element.isJsonObject() && element.getAsJsonObject().has("translate")) {
            String key = element.getAsJsonObject().get("translate").getAsString();
            String resolved = ConfigLang.get(key);
            if (resolved != null) {
                return resolved;
            }
            String fallback = fallback(element);
            return fallback != null ? fallback : key;
        }
        return toFormattedCodes(ComponentJson.fromJson(element));
    }

    /** Single-line text as the player reads it, without formatting codes; used by lists. */
    public static String preview(JsonElement element) {
        return FormatCodes.strip(toEditable(element)).replace('\n', ' ').trim();
    }

    /** The text with its formatting applied, for read-only display such as cards and the stage. */
    public static MutableComponent styled(JsonElement element) {
        return FormatCodes.parse(toEditable(element));
    }

    /** Converts editable text back into the stored component shape, or null when empty. */
    public static JsonElement fromEditable(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.indexOf(FormatCodes.MARK) < 0) {
            // Plain text stays a plain JSON string, exactly what hand-written dialogue files use.
            return new com.google.gson.JsonPrimitive(text);
        }
        return ComponentJson.toJsonTree(FormatCodes.parse(text));
    }

    /** Serializes a component back into editable text, re-emitting codes only when they change. */
    public static String toFormattedCodes(Component component) {
        if (component == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        Style[] previous = {Style.EMPTY};
        component.visit((style, part) -> {
            if (!part.isEmpty()) {
                boolean reset = needsReset(previous[0], style);
                if (reset) {
                    builder.append("\u00a7r");
                }
                if (reset || !style.equals(previous[0])) {
                    appendStyle(style, builder);
                }
                builder.append(part);
                previous[0] = style;
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return builder.toString();
    }

    private static boolean needsReset(Style previous, Style current) {
        if (previous.isEmpty()) {
            return false;
        }
        if (previous.isBold() && !current.isBold()
                || previous.isItalic() && !current.isItalic()
                || previous.isUnderlined() && !current.isUnderlined()
                || previous.isStrikethrough() && !current.isStrikethrough()
                || previous.isObfuscated() && !current.isObfuscated()) {
            return true;
        }
        net.minecraft.network.chat.TextColor previousColor = previous.getColor();
        return previousColor != null && !previousColor.equals(current.getColor());
    }

    private static void appendStyle(Style style, StringBuilder builder) {
        net.minecraft.network.chat.TextColor color = style.getColor();
        if (color != null) {
            int rgb = color.getValue();
            boolean named = false;
            for (net.minecraft.ChatFormatting formatting : net.minecraft.ChatFormatting.values()) {
                if (formatting.getColor() != null && formatting.getColor() == rgb) {
                    builder.append('\u00a7').append(formatting.getChar());
                    named = true;
                    break;
                }
            }
            if (!named) {
                builder.append("\u00a7x");
                for (char digit : String.format("%06x", rgb).toCharArray()) {
                    builder.append('\u00a7').append(digit);
                }
            }
        }
        if (style.isBold()) {
            builder.append("\u00a7l");
        }
        if (style.isItalic()) {
            builder.append("\u00a7o");
        }
        if (style.isUnderlined()) {
            builder.append("\u00a7n");
        }
        if (style.isStrikethrough()) {
            builder.append("\u00a7m");
        }
        if (style.isObfuscated()) {
            builder.append("\u00a7k");
        }
    }

    /** True when the stored element is a translation reference rather than literal text. */
    public static boolean isTranslation(JsonElement element) {
        return element != null && element.isJsonObject() && element.getAsJsonObject().has("translate");
    }

    public static String translationKey(JsonElement element) {
        return isTranslation(element) ? element.getAsJsonObject().get("translate").getAsString() : null;
    }

    /** The text a translation element shows while its key has no translation, or null. */
    public static String fallback(JsonElement element) {
        if (!isTranslation(element)) {
            return null;
        }
        JsonElement fallback = element.getAsJsonObject().get("fallback");
        return fallback != null && fallback.isJsonPrimitive() && !fallback.getAsString().isEmpty()
                ? fallback.getAsString() : null;
    }

    /**
     * Sets or clears the fallback of a translation element. The fallback is a standard component
     * field, so the game shows it too whenever the key is missing from the language files; it is
     * what keeps the writer's text when a line is switched to a translation key.
     */
    public static JsonElement withFallback(JsonElement element, String fallback) {
        if (!isTranslation(element)) {
            return element;
        }
        JsonObject object = element.getAsJsonObject().deepCopy();
        if (fallback == null || fallback.isEmpty()) {
            object.remove("fallback");
        } else {
            object.addProperty("fallback", fallback);
        }
        return object;
    }

    /** Builds a translation element, preserving any sibling style fields already present. */
    public static JsonElement translation(String key, JsonElement existing) {
        if (key == null || key.isBlank()) {
            return null;
        }
        JsonObject object = existing != null && existing.isJsonObject()
                ? existing.getAsJsonObject().deepCopy() : new JsonObject();
        object.addProperty("translate", key);
        return object;
    }

    /** Reads one key from the editor lang files for the current game language, English fallback. */
    public static final class ConfigLang {
        private static final Map<String, String> CACHE = new HashMap<>();
        private static String loadedLanguage;
        private static long loadedStamp;

        private ConfigLang() {
        }

        public static synchronized String get(String key) {
            read();
            return CACHE.get(key);
        }

        public static synchronized Map<String, String> entries(String language) {
            Path file = EditorConfig.LANG_DIR.resolve(language + ".json");
            if (!Files.isRegularFile(file)) {
                return new LinkedHashMap<>();
            }
            try {
                Map<String, String> map = GSON.fromJson(Files.readString(file), Map.class);
                return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
            } catch (Exception e) {
                return new LinkedHashMap<>();
            }
        }

        public static synchronized void save(String language, Map<String, String> entries) {
            try {
                Files.createDirectories(EditorConfig.LANG_DIR);
                Files.writeString(EditorConfig.LANG_DIR.resolve(language + ".json"),
                        GSON.toJson(new java.util.TreeMap<>(entries)));
            } catch (Exception e) {
                top.yourzi.dialog.Dialog.LOGGER.error("Failed to write editor lang file {}", language, e);
            }
        }

        /** Merges two translations into the editor lang files, keeping existing keys intact. */
        public static synchronized void merge(String key, String zhCn, String enUs) {
            Map<String, String> zh = entries("zh_cn");
            Map<String, String> en = entries("en_us");
            if (zhCn != null && !zhCn.isBlank()) {
                zh.put(key, zhCn);
            }
            if (enUs != null && !enUs.isBlank()) {
                en.put(key, enUs);
            }
            save("zh_cn", zh);
            save("en_us", en);
        }

        /** Invalidates the cache so the next lookup re-reads from disk. */
        public static synchronized void invalidate() {
            loadedLanguage = null;
        }

        private static void read() {
            String language = currentLanguage();
            long stamp = stamp(language) * 31 + stamp("en_us");
            if (language.equals(loadedLanguage) && stamp == loadedStamp) {
                return;
            }
            loadedStamp = stamp;
            CACHE.clear();
            CACHE.putAll(entries(language));
            if (!"en_us".equals(language)) {
                for (Map.Entry<String, String> entry : entries("en_us").entrySet()) {
                    CACHE.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
            loadedLanguage = language;
        }

        private static long stamp(String language) {
            try {
                Path file = EditorConfig.LANG_DIR.resolve(language + ".json");
                return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : 0L;
            } catch (Exception e) {
                return 0L;
            }
        }

        private static String currentLanguage() {
            try {
                if (net.minecraft.client.Minecraft.getInstance().getLanguageManager() != null) {
                    return net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
                }
            } catch (Throwable ignored) {
                // No client (dedicated server) or the language manager is not ready yet.
            }
            return "en_us";
        }
    }

    /** Every content field of an entry, used by the search index and the validator. */
    public static List<String> searchFields(DialogEntry entry) {
        List<String> fields = new ArrayList<>();
        if (entry == null) {
            return fields;
        }
        addField(fields, entry.getId());
        addField(fields, preview(entry.getSpeaker()));
        addField(fields, preview(entry.getText()));
        addField(fields, entry.getNextId());
        addField(fields, entry.getAudioPath());
        addField(fields, entry.getVisibilityCommand());
        if (entry.getCommands() != null) {
            entry.getCommands().forEach(command -> addField(fields, command));
        }
        if (entry.getOptions() != null) {
            for (DialogOption option : entry.getOptions()) {
                if (option == null) {
                    continue;
                }
                addField(fields, preview(option.getText()));
                addField(fields, option.getTargetId());
            }
        }
        if (entry.getBackgroundImage() != null) {
            addField(fields, entry.getBackgroundImage().getPath());
        }
        if (entry.getPortraits() != null) {
            entry.getPortraits().forEach(portrait -> addField(fields, portrait == null ? null : portrait.getPath()));
        }
        return fields;
    }

    private static void addField(List<String> fields, String value) {
        if (value != null && !value.isBlank()) {
            fields.add(value);
        }
    }
}

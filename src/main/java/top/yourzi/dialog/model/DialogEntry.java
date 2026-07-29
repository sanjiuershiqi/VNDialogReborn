package top.yourzi.dialog.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import top.yourzi.dialog.util.ComponentJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DialogEntry {
    private JsonElement text;
    private JsonElement speaker;
    @SerializedName("portraits")
    private List<PortraitInfo> portraits;
    private String id;
    @SerializedName("next")
    private String nextId;
    private DialogOption[] options;
    private transient String selectedOptionText;
    @SerializedName("command")
    private List<String> commands;
    @SerializedName("allowSkip")
    private Boolean allowSkip;
    @SerializedName("endDialog")
    private Boolean endDialog;
    @SerializedName("visibility_command")
    private String visibilityCommand;
    @SerializedName("display_items")
    private List<DisplayItemInfo> displayItems;
    @SerializedName("background_image")
    private BackgroundImageInfo backgroundImage;
    @SerializedName("audio")
    private String audioPath;

    public static Builder builder() {
        return new Builder();
    }

    public JsonElement getText() {
        return text;
    }

    public void setText(JsonElement text) {
        this.text = text;
    }

    public JsonElement getSpeaker() {
        return speaker;
    }

    public void setSpeaker(JsonElement speaker) {
        this.speaker = speaker;
    }

    public List<PortraitInfo> getPortraits() {
        return portraits;
    }

    public void setPortraits(List<PortraitInfo> portraits) {
        this.portraits = portraits;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNextId() {
        return nextId;
    }

    public void setNextId(String nextId) {
        this.nextId = nextId;
    }

    public DialogOption[] getOptions() {
        return options;
    }

    public void setOptions(DialogOption[] options) {
        this.options = options;
    }

    public String getSelectedOptionText() {
        return selectedOptionText;
    }

    public void setSelectedOptionText(String selectedOptionText) {
        this.selectedOptionText = selectedOptionText;
    }

    public List<String> getCommands() {
        return commands;
    }

    public void setCommands(List<String> commands) {
        this.commands = commands;
    }

    public Boolean getAllowSkip() {
        return allowSkip;
    }

    public void setAllowSkip(Boolean allowSkip) {
        this.allowSkip = allowSkip;
    }

    public Boolean getEndDialog() {
        return endDialog;
    }

    public void setEndDialog(Boolean endDialog) {
        this.endDialog = endDialog;
    }

    public String getVisibilityCommand() {
        return visibilityCommand;
    }

    public void setVisibilityCommand(String visibilityCommand) {
        this.visibilityCommand = visibilityCommand;
    }

    public List<DisplayItemInfo> getDisplayItems() {
        return displayItems;
    }

    public void setDisplayItems(List<DisplayItemInfo> displayItems) {
        this.displayItems = displayItems;
    }

    public BackgroundImageInfo getBackgroundImage() {
        return backgroundImage;
    }

    public void setBackgroundImage(BackgroundImageInfo backgroundImage) {
        this.backgroundImage = backgroundImage;
    }

    public String getAudioPath() {
        return audioPath;
    }

    public void setAudioPath(String audioPath) {
        this.audioPath = audioPath;
    }

    public boolean isSkipAllowed() {
        return allowSkip == null || allowSkip;
    }

    public boolean isEndDialog() {
        return endDialog != null && endDialog;
    }

    public void setCommand(String command) {
        if (this.commands == null) {
            this.commands = new ArrayList<>();
        }
        this.commands.clear();
        this.commands.add(command);
    }

    public String getCommand() {
        if (commands != null && !commands.isEmpty()) {
            return commands.getFirst();
        }
        return null;
    }

    public Component getText(String playerName) {
        return placeHolderReplace("@i", playerName, text);
    }

    public Component getSpeaker(String playerName) {
        return placeHolderReplace("@i", playerName, speaker);
    }

    public boolean hasOptions() {
        return options != null && options.length > 0;
    }

    public Component placeHolderReplace(String fromString, String toString, JsonElement targetElement) {
        if (targetElement == null || targetElement.isJsonNull()) {
            return Component.empty();
        }
        String replacement = toString == null ? "" : toString;

        if (targetElement.isJsonObject()) {
            JsonObject jsonObjectCopy = targetElement.getAsJsonObject().deepCopy();
            performDeepPlaceholderReplace(jsonObjectCopy, fromString, replacement);
            // 如果是翻译组件，先尝试从编辑器配置目录的语言缓存获取翻译
            if (jsonObjectCopy.has("translate")) {
                String key = jsonObjectCopy.get("translate").getAsString();
                String translated = top.yourzi.dialog.editor.util.ConfigLanguageCache.get(key);
                if (translated != null) {
                    // 保留 JSON 中定义的样式 (color/bold/italic 等)，避免丢失
                    Component styled = ComponentJson.fromJson(jsonObjectCopy);
                    MutableComponent result = Component.literal(translated.replace(fromString, replacement));
                    result.setStyle(styled.getStyle());
                    return result;
                }
            }
            try {
                return replaceTextInComponent(ComponentJson.fromJson(jsonObjectCopy), fromString, replacement);
            } catch (JsonSyntaxException e) {
                return replaceTextInComponent(ComponentJson.fromJson(targetElement), fromString, replacement);
            }
        }

        if (targetElement.isJsonArray()) {
            MutableComponent combinedText = Component.empty();
            JsonArray jsonArray = targetElement.getAsJsonArray();
            for (JsonElement element : jsonArray) {
                combinedText.append(placeHolderReplace(fromString, replacement, element));
            }
            return combinedText;
        }

        if (targetElement.isJsonPrimitive() && targetElement.getAsJsonPrimitive().isString()) {
            return Component.literal(targetElement.getAsString().replace(fromString, replacement));
        }

        try {
            return replaceTextInComponent(ComponentJson.fromJson(targetElement), fromString, replacement);
        } catch (JsonSyntaxException e) {
            return Component.empty();
        }
    }

    private Component replaceTextInComponent(Component component, String placeholder, String replacement) {
        if (component == null) {
            return Component.empty();
        }

        MutableComponent newComponent = Component.empty();
        newComponent.setStyle(component.getStyle());
        component.visit((style, textPart) -> {
            newComponent.append(Component.literal(textPart.replace(placeholder, replacement)).setStyle(style));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return newComponent;
    }

    private boolean performDeepPlaceholderReplace(JsonObject jsonObject, String placeholder, String replacement) {
        boolean modified = false;
        for (Map.Entry<String, JsonElement> entry : new ArrayList<>(jsonObject.entrySet())) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String originalString = value.getAsString();
                String replacedString = originalString.replace(placeholder, replacement);
                if (!originalString.equals(replacedString)) {
                    jsonObject.addProperty(entry.getKey(), replacedString);
                    modified = true;
                }
            } else if (value.isJsonObject()) {
                modified |= performDeepPlaceholderReplace(value.getAsJsonObject(), placeholder, replacement);
            } else if (value.isJsonArray()) {
                modified |= performDeepPlaceholderReplaceInArray(value.getAsJsonArray(), placeholder, replacement);
            }
        }
        return modified;
    }

    private boolean performDeepPlaceholderReplaceInArray(JsonArray jsonArray, String placeholder, String replacement) {
        boolean modified = false;
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonElement element = jsonArray.get(i);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String originalString = element.getAsString();
                String replacedString = originalString.replace(placeholder, replacement);
                if (!originalString.equals(replacedString)) {
                    jsonArray.set(i, new JsonPrimitive(replacedString));
                    modified = true;
                }
            } else if (element.isJsonObject()) {
                modified |= performDeepPlaceholderReplace(element.getAsJsonObject(), placeholder, replacement);
            } else if (element.isJsonArray()) {
                modified |= performDeepPlaceholderReplaceInArray(element.getAsJsonArray(), placeholder, replacement);
            }
        }
        return modified;
    }

    public static final class Builder {
        private final DialogEntry entry = new DialogEntry();

        public Builder text(JsonElement text) {
            entry.setText(text);
            return this;
        }

        public Builder speaker(JsonElement speaker) {
            entry.setSpeaker(speaker);
            return this;
        }

        public Builder portraits(List<PortraitInfo> portraits) {
            entry.setPortraits(portraits);
            return this;
        }

        public Builder id(String id) {
            entry.setId(id);
            return this;
        }

        public Builder nextId(String nextId) {
            entry.setNextId(nextId);
            return this;
        }

        public Builder options(DialogOption[] options) {
            entry.setOptions(options);
            return this;
        }

        public Builder selectedOptionText(String selectedOptionText) {
            entry.setSelectedOptionText(selectedOptionText);
            return this;
        }

        public Builder commands(List<String> commands) {
            entry.setCommands(commands);
            return this;
        }

        public Builder allowSkip(Boolean allowSkip) {
            entry.setAllowSkip(allowSkip);
            return this;
        }

        public Builder endDialog(Boolean endDialog) {
            entry.setEndDialog(endDialog);
            return this;
        }

        public Builder visibilityCommand(String visibilityCommand) {
            entry.setVisibilityCommand(visibilityCommand);
            return this;
        }

        public Builder displayItems(List<DisplayItemInfo> displayItems) {
            entry.setDisplayItems(displayItems);
            return this;
        }

        public Builder backgroundImage(BackgroundImageInfo backgroundImage) {
            entry.setBackgroundImage(backgroundImage);
            return this;
        }

        public Builder audioPath(String audioPath) {
            entry.setAudioPath(audioPath);
            return this;
        }

        public DialogEntry build() {
            return entry;
        }
    }
}

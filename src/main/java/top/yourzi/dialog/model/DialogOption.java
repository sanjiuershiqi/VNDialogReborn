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

public class DialogOption {
    private JsonElement text;
    @SerializedName("target")
    private String targetId;
    private transient Component cachedTextComponent;
    private List<String> command;
    @SerializedName("visibility_command")
    private String visibilityCommand;

    public static Builder builder() {
        return new Builder();
    }

    public JsonElement getText() {
        return text;
    }

    public Component getText(String playerName) {
        return placeHolderReplace("@i", playerName, this.text);
    }

    public void setText(JsonElement text) {
        this.text = text;
        this.cachedTextComponent = null;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public List<String> getCommand() {
        return command;
    }

    public void setCommand(List<String> command) {
        this.command = command;
    }

    public String getVisibilityCommand() {
        return visibilityCommand;
    }

    public void setVisibilityCommand(String visibilityCommand) {
        this.visibilityCommand = visibilityCommand;
    }

    private Component placeHolderReplace(String fromString, String toString, JsonElement targetElement) {
        if (targetElement == null || targetElement.isJsonNull()) {
            return Component.empty();
        }
        String replacement = toString == null ? "" : toString;

        if (targetElement.isJsonObject()) {
            JsonObject jsonObjectCopy = targetElement.getAsJsonObject().deepCopy();
            performDeepPlaceholderReplace(jsonObjectCopy, fromString, replacement);
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
        private final DialogOption option = new DialogOption();

        public Builder text(JsonElement text) {
            option.setText(text);
            return this;
        }

        public Builder targetId(String targetId) {
            option.setTargetId(targetId);
            return this;
        }

        public Builder command(List<String> command) {
            option.setCommand(command);
            return this;
        }

        public Builder visibilityCommand(String visibilityCommand) {
            option.setVisibilityCommand(visibilityCommand);
            return this;
        }

        public DialogOption build() {
            return option;
        }
    }
}

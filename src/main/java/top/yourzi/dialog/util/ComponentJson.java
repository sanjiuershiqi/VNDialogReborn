package top.yourzi.dialog.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

public final class ComponentJson {
    private ComponentJson() {
    }

    public static Component fromJson(JsonElement json) {
        Component component = Component.Serializer.fromJson(json, RegistryAccess.EMPTY);
        return component == null ? Component.empty() : component;
    }

    public static JsonElement toJsonTree(Component component) {
        return JsonParser.parseString(Component.Serializer.toJson(component, RegistryAccess.EMPTY));
    }
}

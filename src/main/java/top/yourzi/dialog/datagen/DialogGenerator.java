package top.yourzi.dialog.datagen;

import com.google.gson.JsonElement;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import top.yourzi.dialog.datagen.provider.DialogProvider;
import top.yourzi.dialog.model.*;
import top.yourzi.dialog.util.ComponentJson;

import java.awt.*;
import java.util.List;

public class DialogGenerator extends DialogProvider {
    public DialogGenerator(PackOutput output) {
        super(output);
    }

    private static JsonElement literalText(String text) {
        return ComponentJson.toJsonTree(Component.literal(text));
    }

    private static JsonElement literalText(String text, Style style) {
        return ComponentJson.toJsonTree(Component.literal(text).withStyle(style));
    }

    @Override
    protected void registerDialogs() {
        dialogBuilder.builder().setId("example_dialog")
                .setTitle("Example Dialog")
                .setDescription("This is an example dialog.")
                .addEntry(
                        DialogEntry.builder()
                                .id("1")
                                .nextId("2")
                                .text(literalText("Hello, how are you?", Style.EMPTY.withBold(true).withColor(Color.ORANGE.getRGB() & 0xFFFFFF)))
                                .speaker(literalText("NPC"))
                                .portraits(List.of(new PortraitInfo("leaf.png", PortraitPosition.LEFT, 1.0f, PortraitAnimationType.NONE)))
                                .displayItems(List.of(new DisplayItemInfo("minecraft:acacia_fence", 1, null)))
                                .backgroundImage(new BackgroundImageInfo("background.png", BackgroundRenderOption.FILL))
                                .build())
                .addEntry(
                        DialogEntry.builder()
                                .id("2")
                                .text(literalText("I'm fine, thank you! What should the weather be?"))
                                .speaker(literalText("Player"))
                                .options(new DialogOption[]{
                                        DialogOption.builder()
                                                .targetId("end")
                                                .text(literalText("Clear"))
                                                .command(List.of("weather clear"))
                                                .build(),
                                        DialogOption.builder()
                                                .text(literalText("Rain"))
                                                .targetId("end")
                                                .command(List.of("weather rain"))
                                                .build()})
                                .build())
                .addEntry(
                        DialogEntry.builder()
                                .id("end")
                                .text(literalText("Done. See you!"))
                                .speaker(literalText("NPC"))
                                .build())
                .build("1");
    }
}

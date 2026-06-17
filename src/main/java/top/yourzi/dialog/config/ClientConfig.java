package top.yourzi.dialog.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue DIALOG_BOX_WIDTH;
    public static final ModConfigSpec.IntValue DIALOG_BOX_HEIGHT;
    public static final ModConfigSpec.IntValue DIALOG_BOX_PADDING;
    public static final ModConfigSpec.IntValue DIALOG_TEXT_COLOR;
    public static final ModConfigSpec.IntValue DIALOG_BACKGROUND_COLOR;
    public static final ModConfigSpec.IntValue DIALOG_BACKGROUND_OPACITY;
    public static final ModConfigSpec.BooleanValue ENABLE_PORTRAIT_ANIMATIONS;
    public static final ModConfigSpec.BooleanValue IS_PAUSE_SCREEN;
    public static final ModConfigSpec.IntValue AUTO_ADVANCE_DELAY;
    public static final ModConfigSpec.BooleanValue SHOW_SPEAKER_NAME;
    public static final ModConfigSpec.IntValue TEXT_ANIMATION_SPEED;

    static {
        BUILDER.comment("Client settings for the visual novel dialog UI.").push("dialog_client");

        BUILDER.comment("Dialog box layout.").push("ui");
        DIALOG_BOX_WIDTH = BUILDER.comment("Dialog box width.").defineInRange("dialogBoxWidth", 320, 1, 4096);
        DIALOG_BOX_HEIGHT = BUILDER.comment("Dialog box height.").defineInRange("dialogBoxHeight", 100, 1, 4096);
        DIALOG_BOX_PADDING = BUILDER.comment("Dialog box padding.").defineInRange("dialogBoxPadding", 10, 0, 512);
        DIALOG_TEXT_COLOR = BUILDER.comment("Dialog text color, ARGB.").defineInRange("dialogTextColor", 0xFFFFFFFF, Integer.MIN_VALUE, Integer.MAX_VALUE);
        DIALOG_BACKGROUND_COLOR = BUILDER.comment("Dialog background color, RGB.").defineInRange("dialogBackgroundColor", 0x000000, 0, 0xFFFFFF);
        DIALOG_BACKGROUND_OPACITY = BUILDER.comment("Dialog background opacity, 0-255.").defineInRange("dialogBackgroundOpacity", 200, 0, 255);
        BUILDER.pop();

        BUILDER.comment("Portrait settings.").push("portrait");
        ENABLE_PORTRAIT_ANIMATIONS = BUILDER.comment("Enable portrait entrance animations.").define("enablePortraitAnimations", true);
        BUILDER.pop();

        BUILDER.comment("Dialog behavior.").push("system");
        IS_PAUSE_SCREEN = BUILDER.comment("Whether the dialog screen pauses the game.").define("isPauseScreen", false);
        AUTO_ADVANCE_DELAY = BUILDER.comment("Auto-play delay in milliseconds.").defineInRange("autoAdvanceDelay", 700, 0, 60000);
        SHOW_SPEAKER_NAME = BUILDER.comment("Show speaker names in the dialog box.").define("showSpeakerName", true);
        TEXT_ANIMATION_SPEED = BUILDER.comment("Text animation speed in milliseconds per character. 0 shows text instantly.").defineInRange("textAnimationSpeed", 20, 0, 1000);
        BUILDER.pop();

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}

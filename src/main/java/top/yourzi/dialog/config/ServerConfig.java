package top.yourzi.dialog.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ALLOW_SKIP_DIALOG;

    static {
        BUILDER.comment("Server settings for dialog control.").push("dialog_server");
        BUILDER.comment("Dialog control settings.").push("control");
        ALLOW_SKIP_DIALOG = BUILDER.comment("Allow players to fast-forward dialog with the Ctrl key.").define("allowSkipDialog", true);
        BUILDER.pop();
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}

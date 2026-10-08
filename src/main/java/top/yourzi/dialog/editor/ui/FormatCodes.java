package top.yourzi.dialog.editor.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Legacy {@code §} formatting codes, the one place that understands them.
 *
 * <p>Supports the sixteen colours, the k-o styles, {@code §r}, and the {@code §x§r§r§g§g§b§b} hex
 * colour form Minecraft uses for RGB. Parsing, stripping and caret measurement all go through
 * {@link #codeLength}, so the editor can never disagree with itself about what is a code.
 */
public final class FormatCodes {
    public static final char MARK = '\u00a7';

    private FormatCodes() {
    }

    /** Length of the formatting code starting at {@code index}, or 0 when there is none. */
    public static int codeLength(String text, int index) {
        if (index + 1 >= text.length() || text.charAt(index) != MARK) {
            return 0;
        }
        char code = Character.toLowerCase(text.charAt(index + 1));
        if (code == 'x' && index + 14 <= text.length()) {
            for (int i = 0; i < 6; i++) {
                int at = index + 2 + i * 2;
                if (text.charAt(at) != MARK || Character.digit(text.charAt(at + 1), 16) < 0) {
                    return 2;
                }
            }
            return 14;
        }
        return 2;
    }

    /** Text without any formatting codes, as the player reads it. */
    public static String strip(String text) {
        if (text == null || text.indexOf(MARK) < 0) {
            return text == null ? "" : text;
        }
        StringBuilder builder = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int length = codeLength(text, i);
            if (length > 0) {
                i += length;
            } else {
                builder.append(text.charAt(i++));
            }
        }
        return builder.toString();
    }

    /** Converts coded text into a styled component; unknown codes are dropped, never shown. */
    public static MutableComponent parse(String text) {
        MutableComponent result = Component.empty();
        if (text == null || text.isEmpty()) {
            return result;
        }
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int length = codeLength(text, i);
            if (length == 0) {
                run.append(text.charAt(i++));
                continue;
            }
            if (run.length() > 0) {
                result.append(Component.literal(run.toString()).withStyle(style));
                run.setLength(0);
            }
            style = apply(style, text, i, length);
            i += length;
        }
        if (run.length() > 0) {
            result.append(Component.literal(run.toString()).withStyle(style));
        }
        return result;
    }

    /** Applies the code of {@code length} chars at {@code index} on top of {@code style}. */
    public static Style apply(Style style, String text, int index, int length) {
        if (length == 14) {
            StringBuilder hex = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                hex.append(text.charAt(index + 3 + i * 2));
            }
            return style.withColor(TextColor.fromRgb(Integer.parseInt(hex.toString(), 16)));
        }
        ChatFormatting formatting = ChatFormatting.getByCode(Character.toLowerCase(text.charAt(index + 1)));
        if (formatting == null) {
            return style;
        }
        return formatting == ChatFormatting.RESET ? Style.EMPTY : style.applyFormat(formatting);
    }
}

package top.yourzi.dialog.editor.ui;

import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.TextCodec;

import java.util.ArrayList;
import java.util.List;

/** Word wrapping shared by paragraphs, flow cards and inspector hints. */
public final class Wrap {
    private Wrap() {
    }

    public static List<String> text(Component text, int width) {
        return text(text.getString(), width);
    }

    public static List<String> text(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (width <= 0) {
            lines.add(text);
            return lines;
        }
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = current.length() == 0 ? word : current + " " + word;
                if (Theme.font().width(candidate) > width && current.length() > 0) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            lines.add(current.toString());
        }
        return lines;
    }

    /** Wraps dialogue text after flattening formatting codes into plain glyphs. */
    public static List<String> dialogue(String rawText, int width, int maxLines) {
        String plain = rawText == null ? "" : rawText.replace('\u00a7', ' ').replace('\n', ' ');
        List<String> lines = text(plain.trim(), width);
        if (lines.size() <= maxLines) {
            return lines;
        }
        List<String> trimmed = new ArrayList<>(lines.subList(0, maxLines));
        String last = trimmed.get(maxLines - 1);
        trimmed.set(maxLines - 1, Theme.ellipsize(last + " …", width));
        return trimmed;
    }

    /** Convenience for components that hold dialogue text. */
    public static List<String> dialogue(com.google.gson.JsonElement element, int width, int maxLines) {
        return dialogue(TextCodec.preview(element), width, maxLines);
    }
}

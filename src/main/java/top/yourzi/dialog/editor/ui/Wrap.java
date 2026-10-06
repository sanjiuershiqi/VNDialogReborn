package top.yourzi.dialog.editor.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Line wrapping through Minecraft's own splitter: it breaks CJK text between characters, Latin text
 * between words, and carries styles across line breaks.
 */
public final class Wrap {
    private Wrap() {
    }

    public static List<FormattedCharSequence> lines(Component text, int width) {
        List<FormattedCharSequence> lines = Theme.font().split(text, Math.max(10, width));
        return lines.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : lines;
    }

    /** Wraps and keeps at most {@code maxLines}, ending the last kept line with an ellipsis. */
    public static List<FormattedCharSequence> lines(Component text, int width, int maxLines) {
        List<FormattedCharSequence> lines = lines(text, width);
        if (lines.size() <= maxLines) {
            return lines;
        }
        List<FormattedCharSequence> kept = new ArrayList<>(lines.subList(0, maxLines));
        kept.set(maxLines - 1, FormattedCharSequence.composite(kept.get(maxLines - 1),
                FormattedCharSequence.forward(" …", Style.EMPTY)));
        return kept;
    }
}

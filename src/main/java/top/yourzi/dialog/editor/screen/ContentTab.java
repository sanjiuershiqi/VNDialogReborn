package top.yourzi.dialog.editor.screen;

import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.TextCodec;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.Column;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Paragraph;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.TextArea;
import top.yourzi.dialog.editor.ui.TextBox;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiNode;
import top.yourzi.dialog.model.DialogEntry;

/**
 * Content tab: who speaks and what they say, either as literal text or as a translation key.
 *
 * <p>The writer edits a flat string carrying legacy {@code §} codes; {@link TextCodec} converts it to
 * and from the stored component JSON, so files keep their structure while the editing surface stays
 * a text box with a format palette.
 */
final class ContentTab extends Column {
    private static final int[] COLOR_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };
    private static final String COLOR_CHARS = "0123456789abcdef";

    private final EditorContext context;
    private final TextBox speaker = new TextBox("");
    private final TextArea body = new TextArea("");
    private final Button modeSwitch = new Button(Theme.tr("content.mode_plain"), button -> {
    });
    private final Row palette = new Row().gap(1);
    private final Row formats = new Row().gap(2);
    private final UiNode plainHint = Paragraph.of(Theme.tr("content.format_hint")).color(Theme.TEXT_MUTED);
    private final UiNode keyCaption = Nodes.caption(Theme.tr("content.translation_key"));
    private final TextBox translationKey = new TextBox("");
    private final UiNode zhCaption = Nodes.caption(Theme.tr("content.translation_zh"));
    private final TextBox translationZh = new TextBox("");
    private final UiNode enCaption = Nodes.caption(Theme.tr("content.translation_en"));
    private final TextBox translationEn = new TextBox("");
    private final Button writeLang = new Button(Theme.tr("content.generate_lang"), button -> {
    });
    private DialogEntry entry;
    private boolean translationMode;
    private boolean binding;

    ContentTab(EditorContext context) {
        this.context = context;
        this.gap(3).padding(2);

        for (int i = 0; i < COLOR_RGB.length; i++) {
            String code = "\u00a7" + COLOR_CHARS.charAt(i);
            Button swatch = new Button(Component.empty(), button -> this.insert(code)).swatchColor(COLOR_RGB[i]);
            swatch.flex(1);
            swatch.prefHeight(12);
            this.palette.add(swatch);
        }
        this.palette.prefHeight(12);
        this.formats.add(this.formatButton("B", "\u00a7l", "content.bold"));
        this.formats.add(this.formatButton("I", "\u00a7o", "content.italic"));
        this.formats.add(this.formatButton("U", "\u00a7n", "content.underline"));
        this.formats.add(this.formatButton("S", "\u00a7m", "content.strike"));
        this.formats.add(this.formatButton("R", "\u00a7r", "content.reset"));
        this.formats.add(Nodes.fill());
        this.formats.add(this.modeSwitch.fit());

        this.add(Nodes.section(Theme.tr("section.speaker")));
        this.add(this.speaker);
        this.add(Nodes.section(Theme.tr("section.content")));
        this.add(this.formats);
        this.add(this.palette);
        this.add(this.body.prefHeight(110));
        this.add(this.plainHint);
        this.add(this.keyCaption);
        this.add(this.translationKey);
        this.add(this.zhCaption);
        this.add(this.translationZh);
        this.add(this.enCaption);
        this.add(this.translationEn);
        this.add(this.writeLang.fit());

        this.speaker.placeholder(Theme.tr("content.speaker_hint"));
        this.speaker.onChange(this::applySpeaker);
        this.body.placeholder(Theme.tr("content.text_hint"));
        this.body.onChange(this::applyText);
        this.translationKey.placeholder(Theme.tr("content.key_hint"));
        this.translationKey.onChange(this::applyTranslationKey);
        this.translationZh.placeholder(Theme.tr("content.zh_hint"));
        this.translationEn.placeholder(Theme.tr("content.en_hint"));
        this.modeSwitch.setAction(this::toggleTranslationMode);
        this.writeLang.setAction(this::writeLangFiles);
        this.applyMode();
    }

    private Button formatButton(String label, String code, String tooltipKey) {
        Button button = new Button(Component.literal(label), b -> this.insert(code)).prefWidth(16);
        button.withTooltip(Theme.tr(tooltipKey));
        return button;
    }

    void bind(DialogEntry entry) {
        this.binding = true;
        try {
            this.entry = entry;
            if (entry == null) {
                return;
            }
            this.speaker.setValue(TextCodec.toEditable(entry.getSpeaker()));
            JsonElement text = entry.getText();
            this.translationMode = TextCodec.isTranslation(text);
            if (this.translationMode) {
                String key = TextCodec.translationKey(text);
                this.translationKey.setValue(key == null ? "" : key);
                this.loadTranslations(key);
                this.body.setValue("");
            } else {
                this.body.setValue(TextCodec.toEditable(text));
            }
            this.applyMode();
        } finally {
            this.binding = false;
        }
    }

    private void applyMode() {
        boolean plain = !this.translationMode;
        this.palette.setVisible(plain);
        this.body.setVisible(plain);
        this.plainHint.setVisible(plain);
        this.keyCaption.setVisible(!plain);
        this.translationKey.setVisible(!plain);
        this.zhCaption.setVisible(!plain);
        this.translationZh.setVisible(!plain);
        this.enCaption.setVisible(!plain);
        this.translationEn.setVisible(!plain);
        this.writeLang.setVisible(!plain);
        for (UiNode child : this.formats.children()) {
            if (child != this.modeSwitch) {
                child.setVisible(plain);
            }
        }
        this.modeSwitch.setLabel(Theme.tr(plain ? "content.mode_plain" : "content.mode_translation"));
        this.modeSwitch.fit();
    }

    private void insert(String code) {
        if (!this.translationMode) {
            this.body.insertAtCursor(code);
        }
    }

    private void applySpeaker(String value) {
        if (this.entry == null || this.binding) {
            return;
        }
        this.entry.setSpeaker(TextCodec.fromEditable(value));
        this.context.touch(true);
    }

    private void applyText(String value) {
        if (this.entry == null || this.binding || this.translationMode) {
            return;
        }
        this.entry.setText(TextCodec.fromEditable(value));
        this.context.touch(true);
    }

    private void applyTranslationKey(String key) {
        if (this.entry == null || this.binding || !this.translationMode) {
            return;
        }
        this.entry.setText(TextCodec.translation(key.trim(), this.entry.getText()));
        this.context.touch(true);
        this.loadTranslations(key.trim());
    }

    private void toggleTranslationMode() {
        if (this.entry == null) {
            return;
        }
        this.translationMode = !this.translationMode;
        if (this.translationMode) {
            String key = this.translationKey.value().isBlank() ? this.suggestKey() : this.translationKey.value();
            String literal = this.body.value();
            this.entry.setText(TextCodec.translation(key, null));
            this.translationKey.setValue(key);
            this.loadTranslations(key);
            if (this.translationZh.value().isBlank() && !literal.isBlank()) {
                this.translationZh.setValue(literal);
            }
        } else {
            // Leaving translation mode keeps whatever text the key currently resolves to.
            String resolved = TextCodec.toEditable(this.entry.getText());
            this.entry.setText(TextCodec.fromEditable(resolved));
            this.body.setValue(resolved);
        }
        this.context.touch(false);
        this.applyMode();
    }

    private String suggestKey() {
        String sequence = this.context.sequence() == null ? "dialog" : this.context.sequence().getId();
        return "dialog." + sequence + "." + this.entry.getId();
    }

    private void loadTranslations(String key) {
        if (key == null || key.isBlank()) {
            this.translationZh.setValue("");
            this.translationEn.setValue("");
            return;
        }
        this.translationZh.setValue(TextCodec.ConfigLang.entries("zh_cn").getOrDefault(key, ""));
        this.translationEn.setValue(TextCodec.ConfigLang.entries("en_us").getOrDefault(key, ""));
    }

    private void writeLangFiles() {
        String key = this.translationKey.value().trim();
        if (key.isEmpty()) {
            this.context.status(Theme.tr("content.no_key"), EditorContext.StatusKind.WARNING);
            return;
        }
        TextCodec.ConfigLang.merge(key, this.translationZh.value(), this.translationEn.value());
        TextCodec.ConfigLang.invalidate();
        this.context.status(Theme.tr("content.lang_written", key), EditorContext.StatusKind.SUCCESS);
        this.context.touch(false);
    }
}

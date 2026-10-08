package top.yourzi.dialog.editor.screen;

import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.Column;
import top.yourzi.dialog.editor.ui.Modal;
import top.yourzi.dialog.editor.ui.Nodes;
import top.yourzi.dialog.editor.ui.Paragraph;
import top.yourzi.dialog.editor.ui.Row;
import top.yourzi.dialog.editor.ui.Sheets;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.Toggle;
import top.yourzi.dialog.editor.ui.UiHost;
import top.yourzi.dialog.ui.PlaySettings;
import top.yourzi.dialog.ui.ReadStore;

import java.util.function.Consumer;

/**
 * The switches the player has in the dialogue itself, collected in one place.
 *
 * <p>These are not dialogue data - they describe how one player likes to read - so they are saved to
 * their own file the moment they change and take effect the next time the dialogue opens. The read
 * history can be cleared from here too.
 */
final class SettingsSheet extends Modal {
    /** Colours offered for already-read text, from plain white to the dim blue default. */
    private static final int[] READ_COLORS = {0xFFE4E5E9, 0xFFFFFFFF, 0xFFB9C6DC, 0xFF9AA3B2, 0xFFC9B06A, 0xFF8BC77A, 0xFFEC7A8C, 0xFF6E9BFF};

    private final PlaySettings settings = PlaySettings.get();

    private SettingsSheet() {
        super(Theme.tr("settings.title"));
        this.setCardWidth(380);
    }

    static void open(UiHost host) {
        SettingsSheet sheet = new SettingsSheet();
        sheet.build();
        host.open(sheet, true, false);
    }

    private Toggle toggle(String key, boolean value, Consumer<Boolean> apply) {
        return new Toggle(Theme.tr(key), value, chosen -> {
            apply.accept(chosen);
            PlaySettings.save();
        });
    }

    @Override
    protected void buildBody(Column body) {
        body.add(Nodes.section(Theme.tr("settings.section_input")));
        body.add(this.toggle("settings.number_keys", this.settings.numberKeys, value -> this.settings.numberKeys = value));
        body.add(this.toggle("settings.space_advance", this.settings.spaceAdvance, value -> this.settings.spaceAdvance = value));
        body.add(this.toggle("settings.wheel_advance", this.settings.wheelAdvance, value -> this.settings.wheelAdvance = value));
        body.add(this.toggle("settings.wheel_history", this.settings.wheelHistory, value -> this.settings.wheelHistory = value));
        body.add(this.toggle("settings.right_hide", this.settings.rightClickHide, value -> this.settings.rightClickHide = value));
        body.add(this.toggle("settings.hide_key", this.settings.hideKey, value -> this.settings.hideKey = value));

        body.add(Nodes.section(Theme.tr("settings.section_read")));
        body.add(this.toggle("settings.track_read", this.settings.trackRead, value -> this.settings.trackRead = value));
        body.add(this.toggle("settings.dim_read", this.settings.dimReadText, value -> this.settings.dimReadText = value));
        body.add(this.toggle("settings.dim_choices", this.settings.dimReadChoices, value -> this.settings.dimReadChoices = value));
        body.add(this.toggle("settings.skip_unread", this.settings.skipUnreadText, value -> this.settings.skipUnreadText = value));
        body.add(Nodes.caption(Theme.tr("settings.read_color")));

        Row palette = new Row().gap(2);
        for (int color : READ_COLORS) {
            Button swatch = Button.of(Component.empty(), () -> {
                this.settings.readTextColor = color;
                PlaySettings.save();
            }).swatchColor(color & 0xFFFFFF);
            swatch.flex(1);
            swatch.prefHeight(14);
            palette.add(swatch);
        }
        body.add(palette);
        body.add(Paragraph.of(Theme.tr("settings.read_color_hint")).color(Theme.TEXT_MUTED));

        Row actions = new Row().gap(2);
        actions.add(Button.of(Theme.tr("settings.clear_read"), () -> Sheets.confirm(this.host(),
                Theme.tr("settings.clear_read"), Theme.tr("settings.clear_read_confirm"),
                Theme.tr("settings.clear_read"), true, ReadStore::clear)).tone(Button.Tone.DANGER).fit());
        actions.add(Nodes.fill());
        body.add(actions);
        body.add(Paragraph.of(Theme.tr("settings.saved")).color(Theme.TEXT_MUTED));
    }
}

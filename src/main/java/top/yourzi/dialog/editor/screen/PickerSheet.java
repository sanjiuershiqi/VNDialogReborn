package top.yourzi.dialog.editor.screen;

import net.minecraft.network.chat.Component;
import top.yourzi.dialog.editor.ui.Button;
import top.yourzi.dialog.editor.ui.Column;
import top.yourzi.dialog.editor.ui.ItemList;
import top.yourzi.dialog.editor.ui.Modal;
import top.yourzi.dialog.editor.ui.TextBox;
import top.yourzi.dialog.editor.ui.Theme;
import top.yourzi.dialog.editor.ui.UiHost;

import top.yourzi.dialog.editor.EditorConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A modal list of values - nodes, files, textures, dialogue ids.
 *
 * <p>Every picker shares this one shape: a filter field, a list and single-click choice. They all get
 * the same search, keyboard and dismissal behaviour instead of each inventing its own screen.
 */
public final class PickerSheet extends Modal {
    private final List<String> values;
    private final Function<String, String> detail;
    private final Consumer<String> onChosen;
    private final Component clearLabel;
    private Path folder;
    private final ItemList<String> list = new ItemList<>();
    private final TextBox filter = new TextBox("");

    private PickerSheet(Component title, List<String> values, Function<String, String> detail,
                        Consumer<String> onChosen, Component clearLabel) {
        super(title);
        this.values = values;
        this.detail = detail;
        this.onChosen = onChosen;
        this.clearLabel = clearLabel;
        this.setCardWidth(380);
    }

    public static void open(UiHost host, Component title, List<String> values, Consumer<String> onChosen) {
        open(host, title, values, value -> "", onChosen, null);
    }

    /**
     * @param clearLabel when not null, adds a footer button that chooses the empty value
     */
    public static void open(UiHost host, Component title, List<String> values, Function<String, String> detail,
                            Consumer<String> onChosen, Component clearLabel) {
        PickerSheet sheet = new PickerSheet(title, values, detail, onChosen, clearLabel);
        sheet.build();
        host.open(sheet, true, false);
        host.focus(sheet.filter);
    }

    /** File picker: lists the folder's files and offers to open the folder in the system browser. */
    public static void openFiles(UiHost host, Component title, Path folder, List<String> files,
                                 Consumer<String> onChosen) {
        PickerSheet sheet = new PickerSheet(title, files, value -> "", onChosen, null);
        sheet.folder = folder;
        sheet.build();
        host.open(sheet, true, false);
        host.focus(sheet.filter);
    }

    @Override
    protected void buildBody(Column body) {
        this.filter.placeholder(Theme.tr("picker.filter"));
        this.filter.onChange(text -> this.refresh());
        this.filter.onSubmit(() -> {
            if (this.list.items().size() == 1) {
                this.choose(this.list.items().get(0));
            }
        });
        this.list.labeler(Component::literal);
        this.list.decorator((graphics, value, rowY, x, width, height, selected) -> {
            String text = this.detail.apply(value);
            if (text != null && !text.isEmpty()) {
                int textWidth = Theme.font().width(text);
                Theme.text(graphics, text, x + width - textWidth - 8, rowY + (height - 8) / 2, Theme.TEXT_MUTED);
            }
        });
        this.list.emptyText(Theme.tr(this.folder != null ? "picker.empty_folder" : "picker.empty"));
        this.list.activateOnClick(true);
        this.list.onActivate(this::choose);
        this.list.prefHeight(220);
        body.add(this.filter);
        body.add(this.list);
        this.refresh();
    }

    @Override
    public void build() {
        if (this.clearLabel != null) {
            this.footerButton(this.clearLabel, Button.Tone.GHOST, () -> this.choose(""));
        }
        if (this.folder != null) {
            Path target = this.folder;
            this.footerButton(Theme.tr("picker.open_folder"), Button.Tone.GHOST, () -> EditorConfig.openFolder(target));
        }
        super.build();
    }

    private void choose(String value) {
        this.dismissLayer();
        this.onChosen.accept(value);
    }

    private void refresh() {
        String needle = this.filter.value().trim().toLowerCase(Locale.ROOT);
        List<String> visible = new ArrayList<>();
        for (String value : this.values) {
            if (needle.isEmpty() || value.toLowerCase(Locale.ROOT).contains(needle)) {
                visible.add(value);
            }
        }
        this.list.setItems(visible);
    }
}
